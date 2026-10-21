package com.hiro.mistakebook.ai

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hiro.mistakebook.data.AnalysisPayload
import com.hiro.mistakebook.data.ChatRole
import com.hiro.mistakebook.data.FullPageScanPayload
import com.hiro.mistakebook.data.QuestionChatMessageEntity
import com.hiro.mistakebook.data.QuestionEntity
import com.hiro.mistakebook.data.RetakeGrade
import com.hiro.mistakebook.data.RetakeGradePayload
import com.hiro.mistakebook.data.Subjects
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class DeepSeekClient(
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun analyze(
        question: QuestionEntity,
        questionImage: File,
        answerImage: File?
    ): Pair<AnalysisPayload, String> {
        val schema = """
            {
              "questionText": "完整题干，含公式和选项",
              "answerText": "正确答案",
              "explanation": "完整解题过程",
              "subject": "语文|数学|英语|物理|化学|生物",
              "book": "教材册次，不确定则留空",
              "unit": "教材单元，不确定则留空",
              "chapter": "课节或章节，不确定则留空",
              "knowledgePoints": ["具体知识点"],
              "questionType": "必须从该科目提供的题型中选择",
              "mathTopic": "仅数学解答题使用：三角函数与解三角形|立体几何|概率与统计|解析几何|导数与函数|新定义创新题|其他",
              "errorCause": "有原作答证据时给出有证据的错因；没有原作答时只能给出谨慎候选；用户选择不补充错因时留空",
              "errorCauseIsCandidate": true,
              "difficulty": "基础|中等|较难",
              "confidence": 0.0
            }
        """.trimIndent()

        val prompt = buildString {
            appendLine("你是高三北京高考错题分析助手。请识别图片中的完整题目并独立求解。")
            appendLine("科目提示：${question.subject}。")
            appendLine("该科目允许的题型：${Subjects.questionTypes(question.subject).joinToString("、")}。")
            if (question.subject == "数学") {
                appendLine("若题型为解答题，mathTopic 必须从：${Subjects.mathTopics().joinToString("、")} 中选择。")
            }
            appendLine("若用户已填写书目/单元，请优先使用；无法确认时留空，不要编造教材位置。")
            appendLine("知识树：${question.book} / ${question.unit} / ${question.chapter}")
            appendLine("用户已填写知识点：${question.knowledgePointsJson}")
            appendLine("原作答文字：${question.originalAnswerText.ifBlank { "未填写" }}")
            if (question.errorCauseState == "UNSUPPLEMENTED") {
                appendLine("用户选择不补充错因，因此 errorCause 必须为空字符串。")
            } else if (answerImage == null) {
                appendLine("没有提供原作答图片，errorCause 只能作为候选，errorCauseIsCandidate 必须为 true。")
            } else {
                appendLine("第二张图片是用户原作答或草稿，应基于实际答案和步骤分析错误。")
            }
            appendLine("只返回一个合法 JSON 对象，不要 Markdown 代码块，不要添加 JSON 之外的文字。")
            appendLine("严格使用以下字段：")
            append(schema)
        }

        val content = ArrayList<JsonObject>()
        content += textPart(prompt)
        content += imagePart(questionImage)
        if (answerImage != null) content += imagePart(answerImage)
        val raw = request(content)
        val json = extractJson(raw)
        val payload = gson.fromJson(json, AnalysisPayload::class.java)
        return payload to json
    }

    fun gradeRetake(
        question: QuestionEntity,
        questionImage: File,
        answerImage: File?,
        answerText: String
    ): Pair<RetakeGradePayload, String> {
        val prompt = """
            你是高三北京高考阅卷助手。请根据题目、标准答案和学生的重考答案判定。
            科目：${question.subject}
            标准答案：${question.answerText}
            解析：${question.explanation}
            学生文字答案：${answerText.ifBlank { "未提供" }}
            ${if (answerImage != null) "第二张图片是学生本次重考的手写作答。" else ""}
            判定规则：核心结论和关键过程都正确才为 CORRECT；部分关键步骤正确为 PARTIAL；主要思路错误为 WRONG。
            不因书写风格扣分，但必须指出缺失的条件、计算或论证。只返回合法 JSON：
            {"grade":"CORRECT|PARTIAL|WRONG","feedback":"具体判分说明","confidence":0.0}
        """.trimIndent()
        val content = arrayListOf(textPart(prompt), imagePart(questionImage))
        if (answerImage != null) content += imagePart(answerImage)
        val raw = request(content)
        val json = extractJson(raw)
        val payload = gson.fromJson(json, RetakeGradePayload::class.java)
        val normalized = when (payload.grade.uppercase()) {
            RetakeGrade.CORRECT.name -> payload.copy(grade = RetakeGrade.CORRECT.name)
            RetakeGrade.PARTIAL.name -> payload.copy(grade = RetakeGrade.PARTIAL.name)
            else -> payload.copy(grade = RetakeGrade.WRONG.name)
        }
        return normalized to json
    }

    fun detectQuestionBoxes(imageFile: File): Pair<List<NormalizedBox>, String> {
        val prompt = """
            识别图片中所有独立的试题区域。不要把同一大题的小问拆成多题，不要包含页码、答案、解析或大面积空白。
            最多返回 10 个框。坐标采用 0 到 1000 的归一化值，原点在左上角。
            只返回合法 JSON，不要 Markdown：
            {"questions":[{"box":[左,上,右,下],"type":"选择题|主观题|未知"}]}
        """.trimIndent()
        val raw = request(arrayListOf(textPart(prompt), imagePart(imageFile)))
        val json = extractJson(raw)
        val root = JsonParser.parseString(json).asJsonObject
        val array = root.getAsJsonArray("questions") ?: throw IOException("AI 未返回题目区域")
        val boxes = array.mapNotNull { element ->
            runCatching {
                val values = element.asJsonObject.getAsJsonArray("box")
                NormalizedBox(
                    left = values[0].asFloat / 1000f,
                    top = values[1].asFloat / 1000f,
                    right = values[2].asFloat / 1000f,
                    bottom = values[3].asFloat / 1000f
                )
            }.getOrNull()
        }.take(10)
        if (boxes.isEmpty()) throw IOException("没有识别到完整题目区域")
        return boxes to json
    }

    fun scanWrongPage(
        imageFile: File,
        subjectHint: String
    ): Pair<FullPageScanPayload, String> {
        val typeList = Subjects.questionTypes(subjectHint).joinToString("、")
        val prompt = """
            你是高三北京高考整页错题识别助手。请检查整页作业或试卷，只找出有明确批改证据的错题。

            判断错题时必须重点观察题目、作答区域和答案附近是否有：
            - 红色或其他颜色的叉、圈划、波浪线、删除线、订正答案；
            - 老师批注、扣分标记、失分分值、未得分标记；
            - 原先答案被另一颜色笔改写，或旁边出现正确步骤。

            注意：
            - 单独的红勾通常表示正确，不能仅凭红勾判为错题；
            - 普通高亮、下划线、印刷粗体或题干颜色本身不等于错题；
            - 只有作答完成但没有批改痕迹时，不要擅自判错；
            - 同一大题的小问归入同一道题，不拆分；
            - 最多返回 10 道错题，坐标使用 0 到 1000 的归一化值，原点在左上角；
            - wrongEvidence 必须描述实际看到的批改位置和颜色，不能编造。

            科目提示：$subjectHint。
            题型必须从：$typeList 中选择。
            数学解答题必须额外从：${Subjects.mathTopics().joinToString("、")} 中选择 mathTopic。
            书目、单元无法确认时留空。

            只返回合法 JSON，不要 Markdown，不要 JSON 之外的文字：
            {
              "questions": [
                {
                  "box": [左, 上, 右, 下],
                  "isWrong": true,
                  "evidenceType": "叉号|红笔订正|扣分标记|其他颜色批注|错误圈划|无明确批改",
                  "wrongEvidence": "描述批改痕迹和位置",
                  "questionText": "完整题干，含公式和选项",
                  "answerText": "正确答案",
                  "explanation": "完整解题过程",
                  "subject": "语文|数学|英语|物理|化学|生物",
                  "book": "",
                  "unit": "",
                  "chapter": "",
                  "knowledgePoints": ["具体知识点"],
                  "questionType": "从本题型列表选择",
                  "mathTopic": "仅数学解答题使用",
                  "errorCause": "结合错题和批改痕迹给出的具体错因",
                  "difficulty": "基础|中等|较难",
                  "confidence": 0.0
                }
              ]
            }
        """.trimIndent()
        val raw = request(arrayListOf(textPart(prompt), imagePart(imageFile)))
        val json = extractJson(raw)
        val payload = gson.fromJson(json, FullPageScanPayload::class.java)
        return payload to json
    }

    fun answerQuestion(
        question: QuestionEntity,
        questionImage: File?,
        history: List<QuestionChatMessageEntity>,
        extraImage: File?
    ): String {
        val historyText = history
            .filter { it.content.isNotBlank() }
            .takeLast(20)
            .joinToString("\n") { message ->
                val role = if (message.role == ChatRole.USER.value) "用户" else "AI"
                "$role：${message.content}"
            }
        val prompt = """
            你正在回答用户针对一道具体错题的追问。不要脱离题目泛泛讲解。

            科目：${question.subject}
            书目/单元/章节：${question.book} / ${question.unit} / ${question.chapter}
            已识别知识点：${question.knowledgePointsJson}
            知识网定位：${question.knowledgeMappingJson}
            题目：${question.questionText}
            标准答案：${question.answerText}
            解析：${question.explanation}
            已知错因：${question.errorCause}
            批改依据：${question.wrongEvidence}

            规则：
            - 优先解释用户追问的具体步骤、知识点或错因；
            - 如题目信息不足要明确指出，不要编造；
            - 可给必要公式和步骤，但保持回答直接；
            - 用户附加了图片时，结合图片中的追问内容；
            - 使用中文回答。

            最近对话：
            $historyText
        """.trimIndent()
        val content = arrayListOf(textPart(prompt))
        if (questionImage?.isFile == true) content += imagePart(questionImage)
        if (extraImage != null) content += imagePart(extraImage)
        return request(content, jsonMode = false)
    }

    private fun request(content: List<JsonObject>, jsonMode: Boolean = true): String {
        val key = apiKeyProvider()
        if (key.isBlank()) throw IllegalStateException("未配置 DeepSeek API 密钥")

        val body = JsonObject().apply {
            addProperty("model", modelProvider())
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "user", "content" to content))))
            addProperty("temperature", 0.1)
            if (jsonMode) {
                add("response_format", JsonObject().apply { addProperty("type", "json_object") })
            }
        }
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("DeepSeek HTTP ${response.code}: ${text.take(500)}")
            }
            val root = JsonParser.parseString(text).asJsonObject
            return root.getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.asString
                ?: throw IOException("DeepSeek 返回内容为空")
        }
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        throw IOException("AI 未返回合法 JSON")
    }

    private fun textPart(text: String): JsonObject = JsonObject().apply {
        addProperty("type", "text")
        addProperty("text", text)
    }

    private fun imagePart(file: File): JsonObject {
        require(file.isFile) { "图片不存在：${file.name}" }
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        return JsonObject().apply {
            addProperty("type", "image_url")
            add(
                "image_url",
                JsonObject().apply {
                    addProperty("url", "data:image/jpeg;base64,$encoded")
                    addProperty("detail", "high")
                }
            )
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
