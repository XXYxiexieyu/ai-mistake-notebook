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

data class AutoSplitQuestionPayload(
    val box: List<Float> = emptyList(),
    val isWrong: Boolean = false,
    val subject: String = "",
    val evidenceType: String = "",
    val wrongEvidence: String = "",
    val selectedAnswer: String = "",
    val correctAnswer: String = "",
    val questionText: String = "",
    val confidence: Double? = null
)

data class AutoSplitPayload(
    val questions: List<AutoSplitQuestionPayload> = emptyList()
)

data class MarkedWordPayload(
    val word: String = "",
    val lemma: String = "",
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val meaningChinese: String = "",
    val otherMeanings: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    val wordForms: List<String> = emptyList(),
    val exampleSentence: String = "",
    val contextSentence: String = "",
    val markType: String = "",
    val pageIndex: Int = 0,
    val box: List<Float> = emptyList(),
    val confidence: Double? = null
)

data class MarkedWordsPayload(
    val words: List<MarkedWordPayload> = emptyList()
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
              "originalAnswerText": "图片中学生原始作答、草稿或错误步骤；没有则填空字符串，绝不能填标准答案",
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
              "errorCauseIsCandidate": false,
              "difficulty": "基础|中等|较难",
              "confidence": 0.0
            }
        """.trimIndent()

        val knownSubject = question.subject.takeIf { it in Subjects.all }
        val prompt = buildString {
            appendLine("你是高三北京高考错题分析助手。请识别图片中的完整题目并独立求解。")
            appendLine("科目提示：${knownSubject ?: "自动识别"}。")
            if (knownSubject == null) {
                appendLine("请先识别科目，并在 subject 中只填写：${Subjects.all.joinToString("、")}。")
                appendLine("题型根据识别后的科目选择，不要混用其他科目的题型。")
            } else {
                appendLine("该科目允许的题型：${Subjects.questionTypes(knownSubject).joinToString("、")}。")
            }
            if (knownSubject == "数学") {
                appendLine("若题型为解答题，mathTopic 必须从：${Subjects.mathTopics().joinToString("、")} 中选择。")
            }
            appendLine(MATH_FORMAT_RULE)
            if (knownSubject == "化学") appendLine(CHEMISTRY_FORMAT_RULE)
            appendLine("若用户已填写书目/单元，请优先使用；无法确认时留空，不要编造教材位置。")
            appendLine("知识树：${question.book} / ${question.unit} / ${question.chapter}")
            appendLine("用户已填写知识点：${question.knowledgePointsJson}")
            appendLine("原作答文字：${question.originalAnswerText.ifBlank { "未填写" }}")
            appendLine("请自动识别图片中的学生原始作答并填入 originalAnswerText；印刷标准答案或解析不能充当原作答。")
            if (question.errorCauseState == "UNSUPPLEMENTED") {
                appendLine("用户选择不补充错因，因此 errorCause 必须为空字符串。")
            } else if (answerImage == null) {
                appendLine("如果图片中能识别学生原始作答，应据此分析错因；不能把标准答案当原作答。")
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
            $MATH_FORMAT_RULE
            ${if (question.subject == "化学") CHEMISTRY_FORMAT_RULE else ""}
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

    fun detectWrongQuestionCandidates(
        imageFile: File,
        subjectHint: String
    ): Pair<List<AutoSplitQuestionPayload>, String> {
        val prompt = """
            你是高三北京高考错题审查助手。科目提示：${subjectHint.ifBlank { "自动识别" }}。
            如果科目提示为“自动识别”或“AI 自动识别”，必须判断每题科目并填入 subject。
            识别图片中确实做错的独立题目，并返回足够精确的裁剪区域。

            判为错题至少需要以下一种证据：
            - 选择题中可见的作答选项与正确答案不同；
            - 答案错误、空缺后被补写，或正确答案与作答明显不符；
            - 蓝、红、紫、绿等异色笔存在叉号、圈划、删除线、订正或补充步骤；
            - 有老师扣分、失分或其他明确错误批注。

            重要规则：
            - 正确题、无法确认的题一律不要返回；
            - 普通下划线、高亮、蓝笔作答本身不是判错依据；
            - 每个题目区域必须包含完整题干、图形、表格、选项和对应作答，不能夹带相邻题目；
            - 同一大题的小问和图表属于一道题，不拆开；
            - 最多返回 10 道错题；坐标采用 0 到 1000 的归一化值，原点在左上角；
            - 错题依据必须描述实际看到的内容，不能编造。

            只返回合法 JSON，不要 Markdown：
            {
              "questions": [{
                "box": [左, 上, 右, 下],
                "isWrong": true,
                "subject": "语文|数学|英语|物理|化学|生物",
                "evidenceType": "选项错误|答案解析错误|蓝色笔订正|红笔订正|扣分标记|其他颜色批注",
                "wrongEvidence": "具体判错依据",
                "selectedAnswer": "选择题实际选项；非选择题留空",
                "correctAnswer": "选择题正确选项；非选择题留空",
                "questionText": "简短题目摘要",
                "confidence": 0.0
              }]
            }
        """.trimIndent()
        val raw = request(arrayListOf(textPart(prompt), imagePart(imageFile)))
        val json = extractJson(raw)
        val payload = gson.fromJson(json, AutoSplitPayload::class.java)
        val candidates = payload.questions
            .filter { it.isWrong && it.box.size >= 4 }
            .filter { it.evidenceType != "无明确批改" }
            .filter { (it.confidence ?: 0.6) >= 0.35 }
            .take(10)
        if (candidates.isEmpty()) throw IOException("没有识别到有明确依据的错题")
        return candidates to json
    }

    fun scanWrongPage(
        imageFile: File,
        subjectHint: String,
        enhancedInkImage: File? = null
    ): Pair<FullPageScanPayload, String> = scanWrongPages(
        originalPages = listOf(imageFile),
        enhancedPages = listOfNotNull(enhancedInkImage?.takeIf { it.isFile }),
        subjectHint = subjectHint
    )

    fun scanWrongPages(
        originalPages: List<File>,
        enhancedPages: List<File>,
        subjectHint: String
    ): Pair<FullPageScanPayload, String> {
        require(originalPages.isNotEmpty()) { "缺少页面图片" }
        require(originalPages.size <= 10) { "单次最多上传 10 页" }
        val knownSubject = subjectHint.takeIf { it in Subjects.all }
        val typeList = Subjects.questionTypes(subjectHint).joinToString("、")
        val prompt = """
            你是高三北京高考跨页错题识别助手。本次共有 ${originalPages.size} 页，图片顺序与页面顺序一致。
            前 ${originalPages.size} 张是原始页面；随后 ${enhancedPages.size} 张是对应的彩色笔迹增强图。
            请跨页综合分析，只找出有明确批改证据的错题。

            判断错题时必须重点观察题目、作答区域和答案附近是否有：
            - 蓝色笔的叉号、圈划、波浪线、删除线、订正或补写答案；
            - 红色、紫色、绿色或其他颜色的叉、圈划、波浪线、删除线、订正答案；
            - 老师批注、扣分标记、失分分值、未得分标记；
            - 原先答案被另一颜色笔改写，或旁边出现正确步骤。

            注意：
            - 彩色笔迹增强图中，浅灰代表原纸张和印刷内容，蓝色、红色、绿色和紫色高饱和笔画代表检测到的彩笔痕迹；
            - 必须同时对照原图和彩色笔迹增强图，特别留意蓝色圆珠笔、蓝色中性笔和蓝紫色钢笔；
            - 蓝色叉号、蓝笔下划线加订正、蓝色圈出错误、蓝笔补充过程都可能表示错题；
            - 蓝色勾号、普通蓝笔作答或没有订正意义的蓝色线条不能直接判错；
            - 单独的红勾通常表示正确，不能仅凭红勾判为错题；
            - 普通高亮、下划线、印刷粗体或题干颜色本身不等于错题；
            - 只有作答完成但没有批改痕迹时，不要擅自判错；
            - 同一大题的小问归入同一道题，不拆分；
            - 大题可能从上一页底部延续到下一页顶部，必须合并为一个错题，不能拆成两题；
            - 每道题用一个或多个 segments 表示各页中的区域，pageIndex 从 0 开始；
            - 最多返回 10 道错题，坐标使用 0 到 1000 的归一化值，原点在左上角；
            - wrongEvidence 必须描述实际看到的批改位置和颜色，不能编造。

            科目提示：${knownSubject ?: "自动识别"}。
            ${if (knownSubject == null) {
                "请判断每题科目并填入 subject；题型必须符合对应科目。"
            } else {
                "题型必须从：$typeList 中选择。"
            }}
            数学解答题必须额外从：${Subjects.mathTopics().joinToString("、")} 中选择 mathTopic。
            书目、单元无法确认时留空。
            $MATH_FORMAT_RULE
            $CHEMISTRY_FORMAT_RULE

            只返回合法 JSON，不要 Markdown，不要 JSON 之外的文字：
            {
              "questions": [
                {
                  "segments": [
                    {"pageIndex": 0, "box": [左, 上, 右, 下]},
                    {"pageIndex": 1, "box": [左, 上, 右, 下]}
                  ],
                  "isWrong": true,
                  "evidenceType": "蓝色笔订正|红笔订正|其他颜色批注|叉号|错误圈划|扣分标记|无明确批改",
                  "wrongEvidence": "描述批改痕迹和位置",
                  "questionText": "完整题干，含公式和选项",
                  "originalAnswerText": "学生在本题中的原始作答或草稿；没有则留空，不能填标准答案",
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
        val content = arrayListOf(textPart(prompt))
        originalPages.forEach { content += imagePart(it) }
        enhancedPages.forEach { content += imagePart(it) }
        val raw = request(content)
        val json = extractJson(raw)
        val payload = gson.fromJson(json, FullPageScanPayload::class.java)
        return payload to json
    }

    fun extractMarkedEnglishWords(
        pageFiles: List<File>
    ): Pair<List<MarkedWordPayload>, String> {
        require(pageFiles.isNotEmpty()) { "缺少英语阅读页面" }
        require(pageFiles.size <= 5) { "英语查词单次最多 5 页" }
        val prompt = """
            你是高中英语阅读查词助手。图片按英语阅读或完形填空的页面顺序提供。
            只提取用户在图片中通过手写圈画或手写划线标记的英语单词。

            规则：
            - 只处理手写圈、椭圆圈、手写下划线等明显用户标记；
            - 印刷体链接、固定加粗、印刷强调线不是用户标记，不要提取；
            - 组合词和短语可以按一个词条返回；
            - meaningChinese 必须是该单词在当前英文原句中的准确中文释义；
            - otherMeanings 只列出与文中义不同的常见高中释义，使用简短中文；
            - collocations 给出 2 到 4 个固定搭配，格式为英文搭配 — 中文；
            - wordForms 给出常见词性转换或词形变化，例如名词、形容词、过去式；没有则留空；
            - exampleSentence 造一个适合中国高三学生的英文例句，并附中文翻译；
            - contextSentence 必须是单词所在的英文原句；
            - pageIndex 从 0 开始，对应图片顺序；
            - box 是手写标记单词在原图中的外接框 [左, 上, 右, 下]，使用 0 到 1000 的归一化坐标；
            - 看不到精确位置时可以返回空数组，不能编造；
            - 同一标记单词只返回一次；
            - 最多返回 50 个单词。

            只返回合法 JSON，不要 Markdown：
            {
              "words": [{
                "word": "原文单词",
                "lemma": "原形",
                "phonetic": "音标，可留空",
                "partOfSpeech": "词性",
                "meaningChinese": "该词在当前句中的中文释义",
                "otherMeanings": ["其他常见中文释义"],
                "collocations": ["固定搭配 — 中文"],
                "wordForms": ["词性转换或词形变化"],
                "exampleSentence": "新造的英文例句。中文翻译。",
                "contextSentence": "所在英文原句",
                "markType": "圈画|划线",
                "pageIndex": 0,
                "box": [左, 上, 右, 下],
                "confidence": 0.0
              }]
            }
        """.trimIndent()
        val content = arrayListOf(textPart(prompt))
        pageFiles.forEach { content += imagePart(it) }
        val raw = request(content)
        val json = extractJson(raw)
        val payload = gson.fromJson(json, MarkedWordsPayload::class.java)
        return payload.words
            .filter { it.word.isNotBlank() && (it.confidence ?: 0.6) >= 0.3 }
            .take(50) to json
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
            学生原作答：${question.originalAnswerText}
            标准答案：${question.answerText}
            解析：${question.explanation}
            已知错因：${question.errorCause}
            批改依据：${question.wrongEvidence}

            规则：
            - 优先解释用户追问的具体步骤、知识点或错因；
            - 如题目信息不足要明确指出，不要编造；
            - 可给必要公式和步骤，但保持回答直接；
            - $MATH_FORMAT_RULE
            ${if (question.subject == "化学") "- $CHEMISTRY_FORMAT_RULE" else ""}
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
        private const val MATH_FORMAT_RULE =
            "数学内容优先使用标准 Unicode 符号，例如 x²、x³、√(x)、∠ABC、∥、⊥、≤、∈；如需 LaTeX，只使用 x^{2}、\\sqrt{x}、\\frac{a}{b}、\\angle ABC、\\vec{AB} 等简单写法，不要输出公式代码块。"
        private const val CHEMISTRY_FORMAT_RULE =
            "化学式和离子使用标准上下标与电荷，例如 H₂O、SO₄²⁻、Fe³⁺；化学方程式用 →，可逆反应用 ⇌，气体用 ↑，沉淀用 ↓，反应条件写明；离子方程式必须同时满足原子守恒和电荷守恒。"
    }
}
