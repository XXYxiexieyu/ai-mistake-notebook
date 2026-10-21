package com.hiro.mistakebook.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiro.mistakebook.MistakeBookApplication
import com.hiro.mistakebook.data.ChatRole
import java.io.IOException

class QuestionChatWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val assistantMessageId = inputData.getLong(MESSAGE_ID, -1L)
        if (assistantMessageId <= 0) return Result.failure()
        val repository = (applicationContext as MistakeBookApplication).container.repository
        val assistantMessage = repository.getChatMessage(assistantMessageId) ?: return Result.failure()
        if (repository.settings.apiKey().isBlank()) {
            repository.failChatMessage(assistantMessageId, "未配置 DeepSeek API 密钥")
            return Result.failure()
        }
        val question = repository.getQuestion(assistantMessage.questionId) ?: return Result.failure()
        val history = repository.getCompletedChat(question.id)
        val extraImagePath = history
            .lastOrNull { it.role == ChatRole.USER.value && it.imagePath.isNotBlank() }
            ?.imagePath
            ?.let(repository.images::resolve)
        return try {
            val answer = (applicationContext as MistakeBookApplication).container.aiClient.answerQuestion(
                question = question,
                questionImage = question.imagePath
                    .takeIf { it.isNotBlank() }
                    ?.let(repository.images::resolve),
                history = history,
                extraImage = extraImagePath
            )
            repository.completeChatMessage(
                assistantMessageId,
                answer,
                repository.settings.model
            )
            Result.success()
        } catch (error: Exception) {
            val willRetry = error is IOException && runAttemptCount < 2
            if (!willRetry) {
                repository.failChatMessage(
                    assistantMessageId,
                    error.message.orEmpty().take(1000)
                )
            }
            if (willRetry) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val MESSAGE_ID = "assistant_message_id"
    }
}
