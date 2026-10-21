package com.hiro.mistakebook.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiro.mistakebook.MistakeBookApplication
import com.hiro.mistakebook.data.AnalysisState
import com.hiro.mistakebook.data.QuestionDraft
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

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
                scan.copy(status = AnalysisState.PENDING.name, error = "等待配置 DeepSeek API 密钥")
            )
            return Result.failure()
        }
        repository.updatePageScan(scan.copy(status = AnalysisState.PROCESSING.name, error = ""))
        val sourceFile = repository.images.resolve(scan.sourceImagePath)
        if (!sourceFile.isFile) {
            repository.updatePageScan(
                scan.copy(status = AnalysisState.FAILED.name, error = "整页图片不存在")
            )
            return Result.failure()
        }

        return try {
            val (payload, rawJson) = app.container.aiClient.scanWrongPage(sourceFile, scan.subject)
            val source = BitmapFactory.decodeFile(sourceFile.absolutePath)
                ?: error("无法读取整页图片")
            var saved = 0
            payload.questions
                .asSequence()
                .filter { it.isWrong && it.evidenceType != "无明确批改" }
                .filter { (it.confidence ?: 0.6) >= 0.35 }
                .take(10)
                .forEach { question ->
                    val box = question.box
                    if (box.size < 4) return@forEach
                    val marginX = source.width * 0.015f
                    val marginY = source.height * 0.015f
                    val left = (box[0].coerceIn(0f, 1000f) / 1000f * source.width - marginX)
                        .toInt().coerceIn(0, source.width - 1)
                    val top = (box[1].coerceIn(0f, 1000f) / 1000f * source.height - marginY)
                        .toInt().coerceIn(0, source.height - 1)
                    val right = (box[2].coerceIn(0f, 1000f) / 1000f * source.width + marginX)
                        .toInt().coerceIn(left + 1, source.width)
                    val bottom = (box[3].coerceIn(0f, 1000f) / 1000f * source.height + marginY)
                        .toInt().coerceIn(top + 1, source.height)
                    val width = max(1, right - left)
                    val height = max(1, bottom - top)
                    if (width < 50 || height < 50) return@forEach
                    val crop = Bitmap.createBitmap(source, left, top, width, height)
                    val cropPath = repository.images.saveBitmap(crop, "page_wrong")
                    if (crop !== source) crop.recycle()
                    repository.addScannedQuestion(
                        draft = QuestionDraft(
                            subject = scan.subject,
                            questionImage = cropPath,
                            sourceImage = scan.sourceImagePath,
                            captureTime = scan.captureTime,
                            wrongEvidence = question.wrongEvidence,
                            evidenceType = question.evidenceType
                        ),
                        payload = question,
                        rawJson = rawJson,
                        model = repository.settings.model
                    )
                    saved++
                }
            source.recycle()
            repository.updatePageScan(
                scan.copy(
                    status = AnalysisState.DONE.name,
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
                    error = error.message.orEmpty().take(1000)
                )
            )
            if (error is IOException && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val SCAN_ID = "page_scan_id"
    }
}
