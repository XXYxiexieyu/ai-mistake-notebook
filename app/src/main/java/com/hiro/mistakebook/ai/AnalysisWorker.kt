package com.hiro.mistakebook.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiro.mistakebook.MistakeBookApplication
import com.hiro.mistakebook.data.AnalysisState
import java.io.IOException

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val questionId = inputData.getLong(QUESTION_ID, -1L)
        if (questionId <= 0) return Result.failure()
        val app = applicationContext as MistakeBookApplication
        val repository = app.container.repository
        val question = repository.getQuestion(questionId) ?: return Result.failure()
        if (repository.settings.apiKey().isBlank()) {
            repository.markAnalysisState(questionId, AnalysisState.PENDING, "等待配置 DeepSeek API 密钥")
            return Result.failure()
        }
        repository.markAnalysisState(questionId, AnalysisState.PROCESSING)
        return try {
            val questionImage = repository.images.resolve(question.imagePath)
            val answerImage = question.originalAnswerImagePath
                .takeIf { it.isNotBlank() }
                ?.let(repository.images::resolve)
            val (payload, rawJson) = app.container.aiClient.analyze(question, questionImage, answerImage)
            repository.saveAnalysis(questionId, payload, rawJson, repository.settings.model)
            Result.success()
        } catch (error: Exception) {
            repository.markAnalysisState(
                questionId,
                AnalysisState.FAILED,
                error.message.orEmpty().take(1000)
            )
            if (error is IOException && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val QUESTION_ID = "question_id"
    }
}
