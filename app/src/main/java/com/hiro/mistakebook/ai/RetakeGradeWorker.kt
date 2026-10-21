package com.hiro.mistakebook.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiro.mistakebook.MistakeBookApplication
import com.hiro.mistakebook.data.AnalysisState
import com.hiro.mistakebook.data.QuestionStatus
import com.hiro.mistakebook.data.RetakeGrade
import java.io.IOException

class RetakeGradeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val itemId = inputData.getLong(ITEM_ID, -1L)
        if (itemId <= 0) return Result.failure()
        val app = applicationContext as MistakeBookApplication
        val repository = app.container.repository
        val item = app.container.database.retakeDao().getItem(itemId) ?: return Result.failure()
        val question = repository.getQuestion(item.questionId) ?: return Result.failure()
        if (repository.settings.apiKey().isBlank()) return Result.failure()
        val answerImage = item.answerImagePath.takeIf { it.isNotBlank() }?.let(repository.images::resolve)
        return try {
            val (payload, _) = app.container.aiClient.gradeRetake(
                question,
                repository.images.resolve(question.imagePath),
                answerImage,
                item.answerText
            )
            repository.saveAiRetakeGrade(item.id, payload.grade, payload.feedback)
            val latest = repository.getRetakeItem(item.id) ?: return Result.failure()
            val manualDecisionExists = latest.finalGrade != RetakeGrade.UNGRADED.name
            if (!manualDecisionExists) {
                repository.saveFinalRetakeGrade(item.id, payload.grade, payload.feedback)
            }
            if (!manualDecisionExists) {
                if (payload.grade == RetakeGrade.CORRECT.name) {
                    repository.setStatus(question.id, QuestionStatus.PASSED)
                } else {
                    repository.markRetakeFailed(question.id)
                }
            }
            Result.success()
        } catch (error: Exception) {
            app.container.database.retakeDao().saveAiGrade(
                item.id,
                item.aiGrade,
                error.message.orEmpty().take(500),
                AnalysisState.FAILED.name
            )
            if (error is IOException && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val ITEM_ID = "retake_item_id"
    }
}
