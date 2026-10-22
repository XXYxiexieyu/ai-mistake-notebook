package com.hiro.mistakebook.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.room.withTransaction
import com.google.gson.Gson
import com.hiro.mistakebook.ai.AnalysisWorker
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer

class QuestionRepository(
    private val context: Context,
    private val database: AppDatabase,
    val settings: SettingsStore,
    val images: ImageStore,
    val catalog: KnowledgeCatalog
) {
    private val gson = Gson()

    fun observeQuestions(): Flow<List<QuestionEntity>> = database.questionDao().observeAll()
    fun observeQuestion(id: Long): Flow<QuestionEntity?> = database.questionDao().observeById(id)
    fun observeRetakeSessions(): Flow<List<RetakeSessionEntity>> = database.retakeDao().observeSessions()
    fun observeRetakeSession(id: Long): Flow<RetakeSessionEntity?> = database.retakeDao().observeSession(id)
    fun observeRetakeItems(sessionId: Long): Flow<List<RetakeItemEntity>> = database.retakeDao().observeItems(sessionId)
    fun observeChatMessages(questionId: Long): Flow<List<QuestionChatMessageEntity>> =
        database.questionChatDao().observeForQuestion(questionId)

    suspend fun getQuestion(id: Long): QuestionEntity? = database.questionDao().getById(id)
    suspend fun getRetakeSession(id: Long): RetakeSessionEntity? = database.retakeDao().getSession(id)
    suspend fun getRetakeItems(id: Long): List<RetakeItemEntity> = database.retakeDao().getItems(id)
    suspend fun getRetakeItem(id: Long): RetakeItemEntity? = database.retakeDao().getItem(id)
    suspend fun getChatMessage(id: Long): QuestionChatMessageEntity? =
        database.questionChatDao().getById(id)
    suspend fun getCompletedChat(questionId: Long): List<QuestionChatMessageEntity> =
        database.questionChatDao().getCompletedForQuestion(questionId)
    suspend fun getPendingChatReplies(): List<QuestionChatMessageEntity> =
        database.questionChatDao().getPendingOrFailedReplies()

    suspend fun addQuestion(draft: QuestionDraft, autoAnalyze: Boolean? = null): Long {
        val now = System.currentTimeMillis()
        val normalized = normalizeQuestionText(draft.questionText)
        val entity = QuestionEntity(
            subject = draft.subject,
            book = draft.book,
            unit = draft.unit,
            chapter = draft.chapter,
            knowledgePointsJson = gson.toJson(draft.knowledgePoints),
            questionType = draft.questionType,
            mathTopic = draft.mathTopic,
            questionText = draft.questionText.trim(),
            answerText = draft.answerText,
            explanation = draft.explanation,
            errorCause = draft.errorCause,
            errorCauseState = draft.errorCauseState.name,
            errorSubpart = draft.errorSubpart,
            captureTime = draft.captureTime,
            createdAt = now,
            updatedAt = now,
            imagePath = draft.questionImage,
            sourceImagePath = draft.sourceImage,
            originalAnswerImagePath = draft.originalAnswerImage,
            originalAnswerText = draft.originalAnswerText,
            sourceImagePathsJson = gson.toJson(
                draft.sourceImages.ifEmpty {
                    listOfNotNull(draft.sourceImage.takeIf { it.isNotBlank() })
                }
            ),
            analysisState = if (draft.autoAnalyze || autoAnalyze == true) {
                AnalysisState.PENDING.name
            } else {
                AnalysisState.MANUAL.name
            },
            duplicateKey = normalized,
            imageHash = images.sha256(draft.questionImage),
            wrongEvidence = draft.wrongEvidence,
            evidenceType = draft.evidenceType,
            knowledgeMappingJson = gson.toJson(
                buildKnowledgeMapping(
                    draft.subject,
                    draft.book,
                    draft.unit,
                    draft.chapter,
                    draft.knowledgePoints
                )
            )
        )
        val id = database.questionDao().insert(entity)
        if ((draft.autoAnalyze || autoAnalyze == true) && settings.hasApiKey()) {
            enqueueAnalysis(id)
        }
        return id
    }

    suspend fun updateQuestion(question: QuestionEntity) {
        val points = runCatching {
            gson.fromJson(question.knowledgePointsJson, Array<String>::class.java).toList()
        }.getOrDefault(emptyList())
        database.questionDao().update(
            question.copy(
                duplicateKey = normalizeQuestionText(question.questionText),
                knowledgeMappingJson = gson.toJson(
                    buildKnowledgeMapping(
                        question.subject,
                        question.book,
                        question.unit,
                        question.chapter,
                        points
                    )
                ),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveAnalysis(
        questionId: Long,
        payload: AnalysisPayload,
        rawJson: String,
        model: String
    ) {
        val current = getQuestion(questionId) ?: return
        val confirmedCause = if (current.errorCauseState == ErrorCauseState.UNSUPPLEMENTED.name) {
            ""
        } else if (current.errorCauseState == ErrorCauseState.CONFIRMED.name) {
            current.errorCause
        } else {
            payload.errorCause
        }
        val causeState = if (current.errorCauseState == ErrorCauseState.UNSUPPLEMENTED.name) {
            ErrorCauseState.UNSUPPLEMENTED.name
        } else if (current.errorCauseState == ErrorCauseState.CONFIRMED.name) {
            ErrorCauseState.CONFIRMED.name
        } else if (confirmedCause.isBlank()) {
            ErrorCauseState.UNSUPPLEMENTED.name
        } else {
            ErrorCauseState.AI_ACCEPTED.name
        }
        val finalQuestionText = current.questionText.ifBlank { payload.questionText }
        val finalBook = current.book.ifBlank { payload.book }
        val finalUnit = current.unit.ifBlank { payload.unit }
        val finalChapter = current.chapter.ifBlank { payload.chapter }
        database.questionDao().update(
            current.copy(
                questionText = finalQuestionText,
                duplicateKey = normalizeQuestionText(finalQuestionText),
                answerText = payload.answerText,
                explanation = payload.explanation,
                subject = current.subject.ifBlank { payload.subject },
                book = finalBook,
                unit = finalUnit,
                chapter = finalChapter,
                knowledgePointsJson = if (current.knowledgePointsJson == "[]") {
                    gson.toJson(payload.knowledgePoints)
                } else {
                    current.knowledgePointsJson
                },
                knowledgeMappingJson = gson.toJson(
                    buildKnowledgeMapping(
                        current.subject,
                        finalBook,
                        finalUnit,
                        finalChapter,
                        if (current.knowledgePointsJson == "[]") {
                            payload.knowledgePoints
                        } else {
                            runCatching {
                                gson.fromJson(
                                    current.knowledgePointsJson,
                                    Array<String>::class.java
                                ).toList()
                            }.getOrDefault(payload.knowledgePoints)
                        }
                    )
                ),
                questionType = current.questionType.ifBlank { payload.questionType },
                mathTopic = current.mathTopic.ifBlank { payload.mathTopic },
                errorCause = confirmedCause,
                errorCauseState = causeState,
                originalAnswerText = current.originalAnswerText.ifBlank {
                    payload.originalAnswerText
                },
                difficulty = payload.difficulty,
                aiConfidence = payload.confidence?.toFloat(),
                aiRawJson = rawJson,
                aiModel = model,
                analysisState = AnalysisState.DONE.name,
                analysisError = "",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addScannedQuestion(
        draft: QuestionDraft,
        payload: ScannedQuestionPayload,
        rawJson: String,
        model: String
    ): Boolean = database.withTransaction {
        val normalized = normalizeQuestionText(payload.questionText)
        val hash = images.sha256(draft.questionImage)
        val duplicate = database.questionDao().findDuplicateCandidates(
            hash,
            draft.subject,
            normalized
        ).firstOrNull()
        if (duplicate != null) {
            database.questionDao().update(
                duplicate.copy(
                    errorCount = duplicate.errorCount + 1,
                    sourceImagePath = duplicate.sourceImagePath.ifBlank { draft.sourceImage },
                    wrongEvidence = payload.wrongEvidence.ifBlank { duplicate.wrongEvidence },
                    evidenceType = payload.evidenceType.ifBlank { duplicate.evidenceType },
                    originalAnswerText = duplicate.originalAnswerText.ifBlank {
                        payload.originalAnswerText
                    },
                    updatedAt = System.currentTimeMillis()
                )
            )
            false
        } else {
            val allowedTypes = Subjects.questionTypes(draft.subject)
            val exactBook = catalog.findBook(draft.subject, payload.book)
            val resolvedBook = exactBook?.title ?: draft.book.takeIf {
                catalog.findBook(draft.subject, it) != null
            }.orEmpty()
            val resolvedUnit = payload.unit.takeIf { unit ->
                catalog.units(draft.subject, resolvedBook).any { it.title == unit }
            } ?: draft.unit
            val now = System.currentTimeMillis()
            val mapping = buildKnowledgeMapping(
                draft.subject,
                resolvedBook,
                resolvedUnit,
                payload.chapter.ifBlank { draft.chapter },
                payload.knowledgePoints
            )
            database.questionDao().insert(
                QuestionEntity(
                    subject = draft.subject,
                    book = resolvedBook,
                    unit = resolvedUnit,
                    chapter = payload.chapter.ifBlank { draft.chapter },
                    knowledgePointsJson = gson.toJson(payload.knowledgePoints),
                    questionType = payload.questionType.takeIf { it in allowedTypes }.orEmpty(),
                    mathTopic = if (draft.subject == "数学") {
                        payload.mathTopic.takeIf { it in Subjects.mathTopics() }.orEmpty()
                    } else {
                        ""
                    },
                    questionText = payload.questionText,
                    answerText = payload.answerText,
                    explanation = payload.explanation,
                    errorCause = payload.errorCause,
                    errorCauseState = if (payload.errorCause.isBlank()) {
                        ErrorCauseState.UNSUPPLEMENTED.name
                    } else {
                        ErrorCauseState.AI_ACCEPTED.name
                    },
                    originalAnswerText = payload.originalAnswerText,
                    difficulty = payload.difficulty,
                    status = QuestionStatus.UNREVIEWED.name,
                    captureTime = draft.captureTime,
                    createdAt = now,
                    updatedAt = now,
                    imagePath = draft.questionImage,
                    sourceImagePath = draft.sourceImage,
                    analysisState = AnalysisState.DONE.name,
                    aiConfidence = payload.confidence?.toFloat(),
                    aiRawJson = rawJson,
                    aiModel = model,
                    promptVersion = "page-scan-v1",
                    duplicateKey = normalized,
                    imageHash = hash,
                    wrongEvidence = payload.wrongEvidence,
                    evidenceType = payload.evidenceType,
                    sourceImagePathsJson = gson.toJson(
                        draft.sourceImages.ifEmpty {
                            listOfNotNull(draft.sourceImage.takeIf { it.isNotBlank() })
                        }
                    ),
                    knowledgeMappingJson = gson.toJson(mapping)
                )
            )
            true
        }
    }

    suspend fun markAnalysisState(id: Long, state: AnalysisState, error: String = "") {
        val current = getQuestion(id) ?: return
        database.questionDao().update(
            current.copy(
                analysisState = state.name,
                analysisError = error,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setStatus(questionId: Long, status: QuestionStatus) {
        val now = System.currentTimeMillis()
        database.questionDao().setStatus(
            questionId,
            status.name,
            if (status == QuestionStatus.PASSED) now else null,
            now
        )
    }

    suspend fun markRetakeFailed(questionId: Long) {
        database.questionDao().markRetakeFailed(questionId, System.currentTimeMillis())
    }

    suspend fun createPageScan(
        sourceImagePaths: List<String>,
        subject: String,
        captureTime: Long
    ): Long = database.pageScanDao().insert(
        PageScanEntity(
            sourceImagePath = sourceImagePaths.first(),
            sourceImagePathsJson = gson.toJson(sourceImagePaths),
            subject = subject,
            captureTime = captureTime
        )
    )

    suspend fun getPageScan(id: Long): PageScanEntity? = database.pageScanDao().getById(id)

    fun observePageScan(id: Long): Flow<PageScanEntity?> = database.pageScanDao().observeById(id)

    suspend fun updatePageScan(scan: PageScanEntity) {
        database.pageScanDao().update(scan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun getPendingPageScans(): List<PageScanEntity> =
        database.pageScanDao().getPendingOrFailed()

    suspend fun addUserChatAndPendingReply(
        questionId: Long,
        text: String,
        imagePath: String,
        model: String
    ): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        database.questionChatDao().insert(
            QuestionChatMessageEntity(
                questionId = questionId,
                role = ChatRole.USER.value,
                content = text.trim(),
                imagePath = imagePath,
                status = ChatMessageStatus.DONE.name,
                createdAt = now
            )
        )
        database.questionChatDao().insert(
            QuestionChatMessageEntity(
                questionId = questionId,
                role = ChatRole.ASSISTANT.value,
                content = "",
                status = ChatMessageStatus.PENDING.name,
                model = model,
                createdAt = now + 1
            )
        )
    }

    suspend fun completeChatMessage(messageId: Long, content: String, model: String) {
        val message = database.questionChatDao().getById(messageId) ?: return
        database.questionChatDao().update(
            message.copy(
                content = content,
                status = ChatMessageStatus.DONE.name,
                error = "",
                model = model
            )
        )
    }

    suspend fun failChatMessage(messageId: Long, error: String) {
        val message = database.questionChatDao().getById(messageId) ?: return
        database.questionChatDao().update(
            message.copy(
                status = ChatMessageStatus.FAILED.name,
                error = error.take(1000)
            )
        )
    }

    suspend fun deleteQuestion(id: Long) {
        val question = getQuestion(id) ?: return
        val chatImages = database.questionChatDao().getForQuestion(id).map { it.imagePath }
        val imageReferenced = question.imagePath.isNotBlank() &&
            database.questionDao().countOtherImageReferences(id, question.imagePath) > 0
        val sourceReferenced = question.sourceImagePath.isNotBlank() && (
            database.questionDao().countOtherImageReferences(id, question.sourceImagePath) > 0 ||
                database.pageScanDao().countBySourcePath(question.sourceImagePath) > 0
            )
        val multiPageSources = runCatching {
            gson.fromJson(question.sourceImagePathsJson, Array<String>::class.java).toList()
        }.getOrDefault(emptyList()).filter { it.isNotBlank() }
        val answerReferenced = question.originalAnswerImagePath.isNotBlank() &&
            database.questionDao().countOtherImageReferences(
                id,
                question.originalAnswerImagePath
            ) > 0
        database.retakeDao().deleteItemsForQuestion(id)
        database.retakeDao().deleteEmptySessions()
        database.questionChatDao().deleteForQuestion(id)
        database.questionDao().deleteById(id)
        if (!imageReferenced && question.imagePath != question.sourceImagePath) {
            images.delete(question.imagePath)
        }
        if (!sourceReferenced && multiPageSources.size <= 1 && question.sourceImagePath != question.imagePath) {
            images.delete(question.sourceImagePath)
        }
        if (
            question.imagePath.isNotBlank() &&
            question.imagePath == question.sourceImagePath &&
            !imageReferenced &&
            !sourceReferenced &&
            multiPageSources.size <= 1
        ) {
            images.delete(question.imagePath)
        }
        if (!answerReferenced) images.delete(question.originalAnswerImagePath)
        chatImages.filter { it.isNotBlank() }.distinct().forEach(images::delete)
    }

    suspend fun deleteOldPassed(ids: List<Long>) {
        ids.forEach { deleteQuestion(it) }
    }

    suspend fun findDuplicates(draft: QuestionDraft): List<QuestionEntity> {
        return database.questionDao().findDuplicateCandidates(
            hash = images.sha256(draft.questionImage),
            subject = draft.subject,
            normalizedText = normalizeQuestionText(draft.questionText)
        )
    }

    suspend fun mergeIntoTarget(targetId: Long, sourceId: Long) {
        val target = getQuestion(targetId) ?: return
        val source = getQuestion(sourceId) ?: return
        database.questionDao().update(
            target.copy(
                errorCount = target.errorCount + source.errorCount,
                retakeFailures = target.retakeFailures + source.retakeFailures,
                lastRetakeAt = maxOf(target.lastRetakeAt ?: 0, source.lastRetakeAt ?: 0)
                    .takeIf { it > 0 },
                updatedAt = System.currentTimeMillis()
            )
        )
        database.questionDao().deleteById(sourceId)
    }

    suspend fun createRetakeSession(
        subject: String,
        start: Long,
        end: Long,
        selectedIds: List<Long>
    ): Long {
        val questions = database.questionDao().getByIds(selectedIds)
            .filter { it.subject == subject && it.status == QuestionStatus.REVIEWED.name }
        require(questions.isNotEmpty()) { "没有可用的已复习错题" }
        val now = System.currentTimeMillis()
        val sessionId = database.retakeDao().insertSession(
            RetakeSessionEntity(
                name = "$subject 重考 ${questions.size} 题",
                subject = subject,
                startCaptureTime = start,
                endCaptureTime = end,
                createdAt = now,
                startedAt = now
            )
        )
        database.retakeDao().insertItems(
            questions.mapIndexed { index, question ->
                RetakeItemEntity(
                    sessionId = sessionId,
                    questionId = question.id,
                    orderIndex = index
                )
            }
        )
        return sessionId
    }

    suspend fun updateRetakeItem(item: RetakeItemEntity) {
        database.retakeDao().updateItem(item)
    }

    suspend fun saveRetakeAnswer(
        itemId: Long,
        answerText: String,
        answerImagePath: String,
        elapsedMs: Long
    ) {
        database.retakeDao().saveAnswer(
            itemId,
            answerText,
            answerImagePath,
            elapsedMs,
            System.currentTimeMillis()
        )
    }

    suspend fun saveAiRetakeGrade(itemId: Long, grade: String, feedback: String) {
        database.retakeDao().saveAiGrade(
            itemId,
            grade,
            feedback,
            AnalysisState.DONE.name
        )
    }

    suspend fun saveFinalRetakeGrade(itemId: Long, grade: String, feedback: String) {
        database.retakeDao().saveFinalGrade(itemId, grade, feedback)
    }

    suspend fun finishRetakeSession(sessionId: Long) {
        val session = getRetakeSession(sessionId) ?: return
        val elapsed = System.currentTimeMillis() - session.startedAt
        database.retakeDao().updateSession(
            session.copy(
                status = RetakeSessionStatus.COMPLETED.name,
                completedAt = System.currentTimeMillis(),
                totalElapsedMs = elapsed
            )
        )
    }

    fun enqueueAnalysis(questionId: Long) {
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(Data.Builder().putLong(AnalysisWorker.QUESTION_ID, questionId).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "analysis-$questionId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cropBitmap(bitmap: Bitmap, prefix: String): String = images.saveBitmap(bitmap, prefix)

    fun copyImage(uri: Uri, prefix: String): String = images.copyUri(uri, prefix)

    fun buildKnowledgeMapping(
        subject: String,
        book: String,
        unit: String,
        chapter: String,
        points: List<String>
    ): List<KnowledgeNodeRef> = KnowledgeMatcher.match(
        catalog,
        subject,
        book,
        unit,
        chapter,
        points
    )

    private fun normalizeQuestionText(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), "")
            .trim()
}
