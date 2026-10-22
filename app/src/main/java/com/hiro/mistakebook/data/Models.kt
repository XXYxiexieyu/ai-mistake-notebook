package com.hiro.mistakebook.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Subjects {
    val all = listOf("语文", "数学", "英语", "物理", "化学", "生物")

    val typesBySubject = mapOf(
        "语文" to listOf("陌生文言文阅读", "现代文阅读", "课内语基", "课内背诵", "名著阅读", "作文"),
        "数学" to listOf("选择题", "填空题", "解答题", "三角函数与解三角形", "立体几何", "概率与统计", "解析几何", "导数与函数", "新定义创新题", "其他"),
        "英语" to listOf("完形填空", "语法填空", "阅读选择", "七选五", "阅读表达", "应用文写作"),
        "物理" to listOf("单项选择题", "实验题", "计算与综合题"),
        "化学" to listOf("单项选择题", "无机物性质与结构", "化学反应原理", "有机合成与推断", "工业流程", "实验探究"),
        "生物" to listOf("单项选择题", "实验探究", "遗传推理", "代谢与调节", "生态与工程", "材料阅读综合")
    )

    fun types(subject: String): List<String> = typesBySubject[subject].orEmpty()

    fun questionTypes(subject: String): List<String> =
        if (subject == "数学") types(subject).take(3) else types(subject)

    fun mathTopics(): List<String> = types("数学").drop(3)
}

enum class QuestionStatus(val label: String) {
    UNREVIEWED("未复习"),
    REVIEWED("已复习"),
    PASSED("重考通过")
}

enum class AnalysisState(val label: String) {
    MANUAL("手动录入"),
    PENDING("等待分析"),
    PROCESSING("正在分析"),
    DONE("分析完成"),
    FAILED("分析失败")
}

enum class ErrorCauseState(val label: String) {
    UNSUPPLEMENTED("未补充错因"),
    AI_CANDIDATE("AI 识别"),
    AI_ACCEPTED("AI 识别"),
    CONFIRMED("已确认")
}

enum class RetakeGrade(val label: String) {
    UNGRADED("待判分"),
    CORRECT("正确"),
    PARTIAL("部分正确"),
    WRONG("错误")
}

enum class RetakeSessionStatus(val label: String) {
    ACTIVE("进行中"),
    COMPLETED("已完成")
}

enum class ChatRole(val value: String) {
    USER("USER"),
    ASSISTANT("ASSISTANT")
}

enum class ChatMessageStatus(val label: String) {
    PENDING("等待回答"),
    DONE("已回答"),
    FAILED("回答失败")
}

@Entity(
    tableName = "questions",
    indices = [
        Index("subject"),
        Index("book"),
        Index("status"),
        Index("captureTime"),
        Index("analysisState"),
        Index("imageHash")
    ]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String = "数学",
    val book: String = "",
    val unit: String = "",
    val chapter: String = "",
    val knowledgePointsJson: String = "[]",
    val questionType: String = "",
    val mathTopic: String = "",
    val questionText: String = "",
    val answerText: String = "",
    val explanation: String = "",
    val errorCause: String = "",
    val errorCauseState: String = ErrorCauseState.UNSUPPLEMENTED.name,
    val errorSubpart: String = "",
    val difficulty: String = "",
    val status: String = QuestionStatus.UNREVIEWED.name,
    val captureTime: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val imagePath: String = "",
    val sourceImagePath: String = "",
    val originalAnswerImagePath: String = "",
    val analysisState: String = AnalysisState.PENDING.name,
    val analysisError: String = "",
    val aiConfidence: Float? = null,
    val aiRawJson: String = "",
    val aiModel: String = "",
    val promptVersion: String = "analysis-v1",
    val errorCount: Int = 1,
    val retakeFailures: Int = 0,
    val lastRetakeAt: Long? = null,
    val passedAt: Long? = null,
    val duplicateKey: String = "",
    val imageHash: String = "",
    val originalAnswerText: String = "",
    val sourceImagePathsJson: String = "[]",
    val wrongEvidence: String = "",
    val evidenceType: String = "",
    val knowledgeMappingJson: String = "[]"
)

@Entity(tableName = "retake_sessions")
data class RetakeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subject: String,
    val startCaptureTime: Long,
    val endCaptureTime: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = RetakeSessionStatus.ACTIVE.name,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val totalElapsedMs: Long = 0
)

@Entity(
    tableName = "retake_items",
    indices = [Index("sessionId"), Index("questionId")]
)
data class RetakeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val questionId: Long,
    val orderIndex: Int,
    val answerText: String = "",
    val answerImagePath: String = "",
    val aiGrade: String = RetakeGrade.UNGRADED.name,
    val aiFeedback: String = "",
    val finalGrade: String = RetakeGrade.UNGRADED.name,
    val finalFeedback: String = "",
    val elapsedMs: Long = 0,
    val answeredAt: Long? = null,
    val analysisState: String = AnalysisState.MANUAL.name
)

@Entity(
    tableName = "page_scans",
    indices = [Index("status"), Index("createdAt")]
)
data class PageScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceImagePath: String,
    val subject: String,
    val captureTime: Long,
    val status: String = AnalysisState.PENDING.name,
    val stage: String = "QUEUED",
    val progress: Int = 0,
    val detectedCount: Int = 0,
    val sourceImagePathsJson: String = "[]",
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "question_chat_messages",
    indices = [Index("questionId"), Index("createdAt")]
)
data class QuestionChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val role: String,
    val content: String,
    val imagePath: String = "",
    val status: String = ChatMessageStatus.DONE.name,
    val error: String = "",
    val model: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class KnowledgeBook(
    val subject: String,
    val publisher: String,
    val title: String,
    val units: List<KnowledgeUnit>
)

data class KnowledgeUnit(
    val title: String,
    val sections: List<String>
)

data class QuestionDraft(
    val subject: String = "数学",
    val book: String = "",
    val unit: String = "",
    val chapter: String = "",
    val knowledgePoints: List<String> = emptyList(),
    val questionType: String = "",
    val mathTopic: String = "",
    val questionText: String = "",
    val answerText: String = "",
    val explanation: String = "",
    val errorCause: String = "",
    val errorCauseState: ErrorCauseState = ErrorCauseState.AI_ACCEPTED,
    val errorSubpart: String = "",
    val questionImage: String = "",
    val sourceImage: String = "",
    val sourceImages: List<String> = emptyList(),
    val originalAnswerImage: String = "",
    val originalAnswerText: String = "",
    val wrongEvidence: String = "",
    val evidenceType: String = "",
    val knowledgeMappingJson: String = "[]",
    val captureTime: Long = System.currentTimeMillis(),
    val autoAnalyze: Boolean = true
)

data class AnalysisPayload(
    val questionText: String = "",
    val originalAnswerText: String = "",
    val answerText: String = "",
    val explanation: String = "",
    val subject: String = "",
    val book: String = "",
    val unit: String = "",
    val chapter: String = "",
    val knowledgePoints: List<String> = emptyList(),
    val questionType: String = "",
    val mathTopic: String = "",
    val errorCause: String = "",
    val errorCauseIsCandidate: Boolean = true,
    val difficulty: String = "",
    val confidence: Double? = null
)

data class RetakeGradePayload(
    val grade: String = "WRONG",
    val feedback: String = "",
    val confidence: Double? = null
)

data class FullPageScanPayload(
    val questions: List<ScannedQuestionPayload> = emptyList()
)

data class ScannedQuestionPayload(
    val box: List<Float> = emptyList(),
    val segments: List<ScannedQuestionSegment> = emptyList(),
    val isWrong: Boolean = false,
    val evidenceType: String = "",
    val wrongEvidence: String = "",
    val questionText: String = "",
    val originalAnswerText: String = "",
    val answerText: String = "",
    val explanation: String = "",
    val subject: String = "",
    val book: String = "",
    val unit: String = "",
    val chapter: String = "",
    val knowledgePoints: List<String> = emptyList(),
    val questionType: String = "",
    val mathTopic: String = "",
    val errorCause: String = "",
    val difficulty: String = "",
    val confidence: Double? = null
)

data class ScannedQuestionSegment(
    val pageIndex: Int = 0,
    val box: List<Float> = emptyList()
)

data class KnowledgeNodeRef(
    val knowledgePoint: String,
    val book: String = "",
    val unit: String = "",
    val chapter: String = "",
    val matched: Boolean = false,
    val matchMethod: String = ""
)
