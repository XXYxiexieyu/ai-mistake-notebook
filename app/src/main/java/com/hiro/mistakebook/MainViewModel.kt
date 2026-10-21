package com.hiro.mistakebook

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hiro.mistakebook.ai.PageScanWorker
import com.hiro.mistakebook.ai.QuestionChatWorker
import com.hiro.mistakebook.ai.RetakeGradeWorker
import com.hiro.mistakebook.data.QuestionDraft
import com.hiro.mistakebook.data.QuestionEntity
import com.hiro.mistakebook.data.QuestionRepository
import com.hiro.mistakebook.data.QuestionStatus
import com.hiro.mistakebook.data.AnalysisState
import com.hiro.mistakebook.data.RetakeGrade
import com.hiro.mistakebook.data.RetakeItemEntity
import com.hiro.mistakebook.data.RetakeSessionEntity
import com.hiro.mistakebook.data.ImportResult
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.PageScanEntity
import com.hiro.mistakebook.data.QuestionChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MistakeBookApplication
    val repository: QuestionRepository = app.container.repository
    val catalog = app.container.catalog

    val questions: StateFlow<List<QuestionEntity>> = repository.observeQuestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val retakeSessions: StateFlow<List<RetakeSessionEntity>> = repository.observeRetakeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _apiConfigured = MutableStateFlow(repository.settings.hasApiKey())
    val apiConfigured: StateFlow<Boolean> = _apiConfigured

    private val _model = MutableStateFlow(repository.settings.model)
    val model: StateFlow<String> = _model

    fun importImage(uri: Uri, prefix: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.copyImage(uri, prefix) }
                .onSuccess { path -> withContext(Dispatchers.Main) { onResult(path) } }
                .onFailure { error ->
                    withContext(Dispatchers.Main) { onError(error.message ?: "图片导入失败") }
                }
        }
    }

    fun importImages(
        uris: List<Uri>,
        prefix: String,
        onImported: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                uris.forEach { uri ->
                    val path = repository.copyImage(uri, prefix)
                    withContext(Dispatchers.Main) { onImported(path) }
                }
                withContext(Dispatchers.Main) { onDone() }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) { onError(error.message ?: "批量图片导入失败") }
            }
        }
    }

    fun saveBatch(
        drafts: List<QuestionDraft>,
        onDone: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                drafts.forEach { repository.addQuestion(it) }
                onDone(drafts.size)
            } catch (error: Exception) {
                onError(error.message ?: "批量保存失败")
            }
        }
    }

    fun autoSplit(
        sourcePath: String,
        onDone: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!repository.settings.hasApiKey()) {
            onError("请先在设置中配置 DeepSeek API 密钥")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = repository.images.resolve(sourcePath)
                val (boxes, _) = app.container.aiClient.detectQuestionBoxes(file)
                val source = BitmapFactory.decodeFile(file.absolutePath)
                    ?: error("无法读取待分题图片")
                val crops = boxes.mapNotNull { box ->
                    val left = (box.left.coerceIn(0f, 1f) * source.width).toInt()
                    val top = (box.top.coerceIn(0f, 1f) * source.height).toInt()
                    val right = (box.right.coerceIn(0f, 1f) * source.width).toInt()
                    val bottom = (box.bottom.coerceIn(0f, 1f) * source.height).toInt()
                    val width = right - left
                    val height = bottom - top
                    if (width < 40 || height < 40) return@mapNotNull null
                    val crop = Bitmap.createBitmap(
                        source,
                        left.coerceIn(0, source.width - 1),
                        top.coerceIn(0, source.height - 1),
                        width.coerceAtMost(source.width - left),
                        height.coerceAtMost(source.height - top)
                    )
                    val path = repository.images.saveBitmap(crop, "auto_question")
                    if (crop !== source) crop.recycle()
                    path
                }
                source.recycle()
                crops
            }.onSuccess { paths ->
                withContext(Dispatchers.Main) { onDone(paths) }
            }.onFailure { error ->
                withContext(Dispatchers.Main) { onError(error.message ?: "AI 自动分题失败") }
            }
        }
    }

    fun startPageScan(
        sourcePath: String,
        subject: String,
        captureTime: Long,
        onCreated: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val id = repository.createPageScan(sourcePath, subject, captureTime)
                if (repository.settings.hasApiKey()) enqueuePageScan(id)
                id
            }.onSuccess(onCreated).onFailure { onError(it.message ?: "无法创建整页扫描任务") }
        }
    }

    fun observePageScan(id: Long): StateFlow<PageScanEntity?> =
        repository.observePageScan(id).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

    fun observeChatMessages(questionId: Long): StateFlow<List<QuestionChatMessageEntity>> =
        repository.observeChatMessages(questionId).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun askQuestion(
        questionId: Long,
        text: String,
        imagePath: String,
        onSent: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addUserChatAndPendingReply(
                    questionId,
                    text,
                    imagePath,
                    repository.settings.model
                )
            }.onSuccess { assistantId ->
                enqueueChatReply(assistantId)
                onSent(assistantId)
            }.onFailure { onError(it.message ?: "发送问题失败") }
        }
    }

    fun retryChatReply(messageId: Long) {
        enqueueChatReply(messageId)
    }

    private fun enqueueChatReply(messageId: Long) {
        val request = OneTimeWorkRequestBuilder<QuestionChatWorker>()
            .setInputData(Data.Builder().putLong(QuestionChatWorker.MESSAGE_ID, messageId).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            "question-chat-$messageId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueuePageScan(id: Long) {
        val request = OneTimeWorkRequestBuilder<PageScanWorker>()
            .setInputData(Data.Builder().putLong(PageScanWorker.SCAN_ID, id).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            "page-scan-$id",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun saveCrop(bitmap: Bitmap, prefix: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.cropBitmap(bitmap, prefix) }
                .onSuccess { path ->
                    bitmap.recycle()
                    withContext(Dispatchers.Main) { onResult(path) }
                }
                .onFailure { bitmap.recycle() }
        }
    }

    fun saveQuestion(
        draft: QuestionDraft,
        forceNew: Boolean,
        mergeTargetId: Long? = null,
        onSaved: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (!forceNew && mergeTargetId == null) {
                    val candidates = repository.findDuplicates(draft)
                    if (candidates.isNotEmpty()) {
                        onError("DUPLICATE:${candidates.first().id}")
                        return@launch
                    }
                }
                val id = if (mergeTargetId != null) {
                    val target = repository.getQuestion(mergeTargetId)
                    if (target != null) {
                        repository.updateQuestion(
                            target.copy(
                                errorCount = target.errorCount + 1,
                                updatedAt = System.currentTimeMillis(),
                                sourceImagePath = target.sourceImagePath.ifBlank { draft.sourceImage }
                            )
                        )
                        if (draft.autoAnalyze && repository.settings.hasApiKey()) {
                            repository.enqueueAnalysis(mergeTargetId)
                        }
                        mergeTargetId
                    } else {
                        repository.addQuestion(draft)
                    }
                } else {
                    repository.addQuestion(draft)
                }
                onSaved(id)
            } catch (error: Exception) {
                onError(error.message ?: "保存失败")
            }
        }
    }

    fun updateQuestion(question: QuestionEntity, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateQuestion(question)
            onSaved()
        }
    }

    fun setStatus(questionId: Long, status: QuestionStatus) {
        viewModelScope.launch { repository.setStatus(questionId, status) }
    }

    fun deleteQuestion(id: Long) {
        viewModelScope.launch { repository.deleteQuestion(id) }
    }

    fun deleteOldPassed(ids: List<Long>) {
        viewModelScope.launch { repository.deleteOldPassed(ids) }
    }

    fun rerunAnalysis(id: Long) {
        repository.enqueueAnalysis(id)
    }

    fun saveApiKey(value: String) {
        repository.settings.saveApiKey(value)
        _apiConfigured.value = repository.settings.hasApiKey()
        if (_apiConfigured.value) {
            viewModelScope.launch {
                questions.value.filter {
                    it.analysisState in setOf(
                        AnalysisState.PENDING.name,
                        AnalysisState.FAILED.name
                    ) && it.imagePath.isNotBlank()
                }.forEach { repository.enqueueAnalysis(it.id) }
                repository.getPendingPageScans().forEach { enqueuePageScan(it.id) }
                repository.getPendingChatReplies().forEach { enqueueChatReply(it.id) }
            }
        }
    }

    fun setModel(value: String) {
        repository.settings.model = value
        _model.value = value
    }

    fun setYearReminder(enabled: Boolean) {
        repository.settings.yearReminderEnabled = enabled
    }

    fun yearReminderEnabled(): Boolean = repository.settings.yearReminderEnabled

    fun saveLastSubjectFilter(subject: String) {
        repository.settings.lastSubjectFilter = subject
    }

    fun saveLastSort(sort: String) {
        repository.settings.lastSort = sort
    }

    fun createRetake(
        subject: String,
        start: Long,
        end: Long,
        ids: List<Long>,
        onCreated: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { repository.createRetakeSession(subject, start, end, ids) }
                .onSuccess(onCreated)
                .onFailure { onError(it.message ?: "组卷失败") }
        }
    }

    fun saveRetakeAnswer(
        item: RetakeItemEntity,
        answerText: String,
        answerImagePath: String,
        elapsedMs: Long,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            repository.saveRetakeAnswer(
                item.id,
                answerText,
                answerImagePath,
                elapsedMs
            )
            onSaved()
        }
    }

    fun enqueueAiGrade(itemId: Long) {
        val request = OneTimeWorkRequestBuilder<RetakeGradeWorker>()
            .setInputData(Data.Builder().putLong(RetakeGradeWorker.ITEM_ID, itemId).build())
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            "retake-grade-$itemId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun applyManualGrade(item: RetakeItemEntity, grade: RetakeGrade, feedback: String = "") {
        viewModelScope.launch {
            val question = repository.getQuestion(item.questionId) ?: return@launch
            repository.saveFinalRetakeGrade(
                item.id,
                grade.name,
                feedback.ifBlank {
                    if (grade == RetakeGrade.CORRECT) "人工判定通过" else "人工判定未通过"
                }
            )
            if (grade == RetakeGrade.CORRECT) {
                repository.setStatus(question.id, QuestionStatus.PASSED)
            } else {
                repository.markRetakeFailed(question.id)
            }
        }
    }

    fun finishRetake(sessionId: Long) {
        viewModelScope.launch { repository.finishRetakeSession(sessionId) }
    }

    fun exportPdf(
        uri: Uri,
        subject: String?,
        start: Long?,
        end: Long?,
        onDone: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                app.container.pdfExporter.export(uri, questions.value, subject, start, end)
            }.onSuccess(onDone).onFailure { onError(it.message ?: "PDF 导出失败") }
        }
    }

    fun exportBackup(
        uri: Uri,
        onDone: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { app.container.backupManager.export(uri) }
                .onSuccess(onDone)
                .onFailure { onError(it.message ?: "备份失败") }
        }
    }

    fun previewImport(
        uri: Uri,
        onDone: (Int, Int, Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { app.container.backupManager.previewImport(uri) }
                .onSuccess { onDone(it.questionCount, it.newCount, it.duplicateCount) }
                .onFailure { onError(it.message ?: "备份检查失败") }
        }
    }

    fun importBackup(
        uri: Uri,
        onDone: (ImportResult) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { app.container.backupManager.import(uri) }
                .onSuccess(onDone)
                .onFailure { onError(it.message ?: "备份导入失败") }
        }
    }

    fun duplicateKey(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), "")

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application) as T
        }
    }
}
