package com.hiro.mistakebook.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.hiro.mistakebook.MistakeBookApplication
import com.hiro.mistakebook.data.AnalysisState
import com.hiro.mistakebook.data.QuestionDraft
import com.hiro.mistakebook.data.ScannedQuestionPayload
import com.hiro.mistakebook.data.ScannedQuestionSegment
import java.io.File
import java.io.IOException
import kotlin.math.max

class PageScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val scanId = inputData.getLong(SCAN_ID, -1L)
        if (scanId <= 0) return Result.failure()
        val app = applicationContext as MistakeBookApplication
        val repository = app.container.repository
        val scan = repository.getPageScan(scanId) ?: return Result.failure()
        if (repository.settings.apiKey().isBlank()) {
            repository.updatePageScan(
                scan.copy(
                    status = AnalysisState.PENDING.name,
                    stage = "WAITING_KEY",
                    progress = 0,
                    error = "等待配置 DeepSeek API 密钥"
                )
            )
            return Result.failure()
        }

        val pagePaths = runCatching {
            Gson().fromJson(scan.sourceImagePathsJson, Array<String>::class.java).toList()
        }.getOrDefault(emptyList()).filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(scan.sourceImagePath.takeIf { it.isNotBlank() }) }
            .distinct()
            .take(10)
        val pageFiles = pagePaths.map(repository.images::resolve)
        if (pageFiles.isEmpty() || pageFiles.any { !it.isFile }) {
            repository.updatePageScan(
                scan.copy(
                    status = AnalysisState.FAILED.name,
                    stage = "FAILED",
                    progress = 0,
                    error = "有页面图片不存在，请重新添加页面"
                )
            )
            return Result.failure()
        }

        repository.updatePageScan(
            scan.copy(
                status = AnalysisState.PROCESSING.name,
                stage = "ENHANCING",
                progress = 10,
                error = ""
            )
        )

        val enhancedFiles = mutableListOf<File>()
        return try {
            pageFiles.forEachIndexed { index, pageFile ->
                val enhancedFile = File(applicationContext.cacheDir, "ink_${scan.id}_${index}.jpg")
                if (InkImageEnhancer.createEnhancedImage(pageFile, enhancedFile)) {
                    enhancedFiles += enhancedFile
                }
                repository.updatePageScan(
                    scan.copy(
                        status = AnalysisState.PROCESSING.name,
                        stage = "ENHANCING",
                        progress = 10 + ((index + 1) * 20 / pageFiles.size),
                        error = ""
                    )
                )
            }
            val enhancedForRequest = if (enhancedFiles.size == pageFiles.size) {
                enhancedFiles.toList()
            } else {
                emptyList()
            }
            repository.updatePageScan(
                scan.copy(
                    status = AnalysisState.PROCESSING.name,
                    stage = "AI_ANALYZING",
                    progress = 40,
                    error = ""
                )
            )
            val (payload, rawJson) = app.container.aiClient.scanWrongPages(
                originalPages = pageFiles,
                enhancedPages = enhancedForRequest,
                subjectHint = scan.subject
            )
            repository.updatePageScan(
                scan.copy(
                    status = AnalysisState.PROCESSING.name,
                    stage = "SAVING",
                    progress = 75,
                    error = ""
                )
            )

            val normalizedQuestions = payload.questions
                .asSequence()
                .filter { it.isWrong && it.evidenceType != "无明确批改" }
                .filter { (it.confidence ?: 0.6) >= 0.35 }
                .take(10)
                .mapNotNull { normalizeSegments(it, pageFiles.size) }
                .toList()

            val tempDirectory = File(applicationContext.cacheDir, "scan_crops_${scan.id}")
                .apply { mkdirs() }
            val questionCropFiles = Array(normalizedQuestions.size) { mutableListOf<File>() }

            pageFiles.forEachIndexed { pageIndex, pageFile ->
                val questionsForPage = normalizedQuestions.withIndex()
                    .filter { (_, item) -> item.segments.any { it.pageIndex == pageIndex } }
                if (questionsForPage.isEmpty()) return@forEachIndexed
                val source = BitmapFactory.decodeFile(pageFile.absolutePath) ?: return@forEachIndexed
                questionsForPage.forEach { (questionIndex, item) ->
                    item.segments
                        .filter { it.pageIndex == pageIndex }
                        .forEachIndexed { segmentIndex, segment ->
                            val crop = cropSegment(source, segment.box) ?: return@forEachIndexed
                            val temp = File(
                                tempDirectory,
                                "q${questionIndex}_p${pageIndex}_${segmentIndex}.jpg"
                            )
                            temp.outputStream().use {
                                crop.compress(Bitmap.CompressFormat.JPEG, 94, it)
                            }
                            if (crop !== source) crop.recycle()
                            questionCropFiles[questionIndex] += temp
                        }
                }
                source.recycle()
            }

            var saved = 0
            normalizedQuestions.forEachIndexed { index, item ->
                val imagePath = composeQuestionImage(
                    questionCropFiles[index],
                    repository.images,
                    "page_wrong"
                ) ?: return@forEachIndexed
                repository.addScannedQuestion(
                    draft = QuestionDraft(
                        subject = scan.subject,
                        questionImage = imagePath,
                        sourceImage = pagePaths.first(),
                        sourceImages = pagePaths,
                        captureTime = scan.captureTime,
                        wrongEvidence = item.payload.wrongEvidence,
                        evidenceType = item.payload.evidenceType
                    ),
                    payload = item.payload,
                    rawJson = rawJson,
                    model = repository.settings.model
                )
                saved++
            }

            val cacheRoot = applicationContext.cacheDir.canonicalFile
            if (tempDirectory.canonicalFile.toPath().startsWith(cacheRoot.toPath())) {
                tempDirectory.deleteRecursively()
            }
            repository.updatePageScan(
                scan.copy(
                    status = AnalysisState.DONE.name,
                    stage = "COMPLETE",
                    progress = 100,
                    detectedCount = saved,
                    error = ""
                )
            )
            Result.success()
        } catch (error: Exception) {
            repository.updatePageScan(
                scan.copy(
                    status = if (error is IOException && runAttemptCount < 2) {
                        AnalysisState.PENDING.name
                    } else {
                        AnalysisState.FAILED.name
                    },
                    stage = if (error is IOException && runAttemptCount < 2) "RETRY_WAIT" else "FAILED",
                    progress = 0,
                    error = error.message.orEmpty().take(1000)
                )
            )
            if (error is IOException && runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            enhancedFiles.forEach { if (it.exists()) it.delete() }
        }
    }

    private fun normalizeSegments(
        payload: ScannedQuestionPayload,
        pageCount: Int
    ): NormalizedQuestion? {
        val segments = if (payload.segments.isNotEmpty()) {
            payload.segments.mapNotNull { segment ->
                val index = when {
                    segment.pageIndex in 0 until pageCount -> segment.pageIndex
                    segment.pageIndex - 1 in 0 until pageCount -> segment.pageIndex - 1
                    else -> return@mapNotNull null
                }
                segment.copy(pageIndex = index)
            }
        } else if (payload.box.size >= 4) {
            listOf(ScannedQuestionSegment(pageIndex = 0, box = payload.box))
        } else {
            emptyList()
        }.filter { it.box.size >= 4 }
        return segments.sortedBy { it.pageIndex }
            .takeIf { it.isNotEmpty() }
            ?.let { NormalizedQuestion(payload, it) }
    }

    private fun cropSegment(source: Bitmap, box: List<Float>): Bitmap? {
        val marginX = source.width * 0.012f
        val marginY = source.height * 0.012f
        val left = (box[0].coerceIn(0f, 1000f) / 1000f * source.width - marginX)
            .toInt().coerceIn(0, source.width - 1)
        val top = (box[1].coerceIn(0f, 1000f) / 1000f * source.height - marginY)
            .toInt().coerceIn(0, source.height - 1)
        val right = (box[2].coerceIn(0f, 1000f) / 1000f * source.width + marginX)
            .toInt().coerceIn(left + 1, source.width)
        val bottom = (box[3].coerceIn(0f, 1000f) / 1000f * source.height + marginY)
            .toInt().coerceIn(top + 1, source.height)
        val width = right - left
        val height = bottom - top
        if (width < 50 || height < 50) return null
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    private fun composeQuestionImage(
        crops: List<File>,
        imageStore: com.hiro.mistakebook.data.ImageStore,
        prefix: String
    ): String? {
        if (crops.isEmpty()) return null
        val bitmaps = crops.mapNotNull { BitmapFactory.decodeFile(it.absolutePath) }
        if (bitmaps.isEmpty()) return null
        val gap = 12
        val width = bitmaps.maxOf { it.width }
        val height = bitmaps.sumOf { it.height } + gap * (bitmaps.size - 1)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        var y = 0
        bitmaps.forEachIndexed { index, bitmap ->
            canvas.drawBitmap(bitmap, ((width - bitmap.width) / 2f), y.toFloat(), null)
            y += bitmap.height
            if (index < bitmaps.lastIndex) {
                canvas.drawRect(0f, y.toFloat(), width.toFloat(), (y + gap).toFloat(), android.graphics.Paint().apply {
                    color = Color.rgb(210, 220, 216)
                })
                y += gap
            }
            bitmap.recycle()
        }
        val path = imageStore.saveBitmap(output, prefix)
        output.recycle()
        return path
    }

    private data class NormalizedQuestion(
        val payload: ScannedQuestionPayload,
        val segments: List<ScannedQuestionSegment>
    )

    companion object {
        const val SCAN_ID = "page_scan_id"
    }
}
