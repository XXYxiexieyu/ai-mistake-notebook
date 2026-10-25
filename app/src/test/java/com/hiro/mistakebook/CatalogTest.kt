package com.hiro.mistakebook

import com.google.gson.Gson
import com.hiro.mistakebook.data.KnowledgeTreeData
import com.hiro.mistakebook.data.FullPageScanPayload
import com.hiro.mistakebook.data.KnowledgeMatcher
import com.hiro.mistakebook.ai.InkColorAnalyzer
import com.hiro.mistakebook.ai.AutoSplitPayload
import com.hiro.mistakebook.ai.MarkedWordsPayload
import com.hiro.mistakebook.data.AnalysisPayload
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.QuestionDraft
import com.hiro.mistakebook.data.Subjects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {
    private val catalog: KnowledgeTreeData by lazy {
        val file = File("src/main/assets/knowledge_tree.json")
        Gson().fromJson(file.readText(Charsets.UTF_8), KnowledgeTreeData::class.java)
    }

    @Test
    fun catalogContainsAllRequiredBooks() {
        assertEquals(35, catalog.books.size)
        assertEquals(
            mapOf("语文" to 5, "数学" to 7, "英语" to 7, "物理" to 6, "化学" to 5, "生物" to 5),
            catalog.books.groupingBy { it.subject }.eachCount()
        )
        assertTrue(catalog.books.all { it.units.isNotEmpty() })
        assertTrue(catalog.books.all { book -> book.units.any { it.sections.isNotEmpty() } })
    }

    @Test
    fun catalogEntriesAreUniqueWithinTheirParent() {
        catalog.books.forEach { book ->
            assertEquals(book.units.size, book.units.map { it.title }.distinct().size)
            book.units.forEach { unit ->
                assertEquals(unit.sections.size, unit.sections.distinct().size)
            }
        }
    }

    @Test
    fun subjectTypesAreOfflineConstants() {
        assertEquals("AI 自动识别", Subjects.AUTO)
        assertFalse(Subjects.types("数学").isEmpty())
        assertEquals(listOf("选择题", "填空题", "解答题"), Subjects.questionTypes("数学"))
        assertTrue(Subjects.mathTopics().contains("导数与函数"))
        assertTrue(Subjects.mathTopics().contains("新定义创新题"))
        Subjects.all.forEach { assertTrue(Subjects.questionTypes(it).isNotEmpty()) }
    }

    @Test
    fun fullPageWrongQuestionEvidenceParses() {
        val json = """
            {
              "questions": [{
                "segments": [
                  {"pageIndex": 0, "box": [100, 700, 900, 980]},
                  {"pageIndex": 1, "box": [80, 40, 920, 420]}
                ],
                "isWrong": true,
                "subject": "数学",
                "evidenceType": "红笔订正",
                "wrongEvidence": "答案右侧有红笔叉号，并在下方补写正确步骤",
                "questionText": "测试题",
                "knowledgePoints": ["函数定义域"],
                "confidence": 0.92
              }]
            }
        """.trimIndent()
        val payload = Gson().fromJson(json, FullPageScanPayload::class.java)
        assertEquals(1, payload.questions.size)
        assertTrue(payload.questions.first().isWrong)
        assertEquals("红笔订正", payload.questions.first().evidenceType)
        assertEquals(2, payload.questions.first().segments.size)
        assertEquals(1, payload.questions.first().segments[1].pageIndex)
        assertEquals(4, payload.questions.first().segments[1].box.size)
    }

    @Test
    fun knowledgePointMapsIntoTextbookNetwork() {
        val mathBooks = catalog.books.filter { it.subject == "数学" }
        val matches = KnowledgeMatcher.match(
            subjectBooks = mathBooks,
            bookHint = "必修第一册",
            unitHint = "第三章 函数",
            chapterHint = "",
            points = listOf("函数的单调性")
        )
        assertEquals(1, matches.size)
        assertTrue(matches.first().matched)
        assertEquals("必修第一册", matches.first().book)
        assertEquals("第三章 函数", matches.first().unit)
        assertTrue(matches.first().chapter.contains("单调性"))
    }

    @Test
    fun blueCorrectionInkIsDetected() {
        val blue = InkColorAnalyzer.analyze(42, 88, 205)
        assertTrue(blue.coloredInk)
        assertTrue(blue.blueFamily)
        assertTrue(blue.hue in 165f..325f)

        val pastelBlue = InkColorAnalyzer.analyze(95, 125, 190)
        assertTrue(pastelBlue.blueFamily)

        val grayPrint = InkColorAnalyzer.analyze(72, 72, 72)
        assertFalse(grayPrint.coloredInk)
        assertFalse(grayPrint.blueFamily)
    }

    @Test
    fun aiRecognitionDefaultsAreAcceptedAndOriginalAnswerParses() {
        assertEquals(ErrorCauseState.AI_ACCEPTED, QuestionDraft().errorCauseState)
        val payload = Gson().fromJson(
            """
            {
              "questionText": "题目",
              "originalAnswerText": "学生在答题区写下的原始步骤",
              "answerText": "标准答案",
              "errorCause": "在定义域判断处出错"
            }
            """.trimIndent(),
            AnalysisPayload::class.java
        )
        assertEquals("学生在答题区写下的原始步骤", payload.originalAnswerText)
        assertEquals("在定义域判断处出错", payload.errorCause)
    }

    @Test
    fun aiSplitReviewUsesAnswerDifferenceAndCorrectionEvidence() {
        val payload = Gson().fromJson(
            """
            {
              "questions": [{
                "box": [80, 200, 920, 620],
                "isWrong": true,
                "subject": "数学",
                "evidenceType": "选项错误",
                "wrongEvidence": "作答 B，但正确选项为 C",
                "selectedAnswer": "B",
                "correctAnswer": "C",
                "questionText": "完整选择题及配图",
                "confidence": 0.93
              }]
            }
            """.trimIndent(),
            AutoSplitPayload::class.java
        )
        val item = payload.questions.single()
        assertTrue(item.isWrong)
        assertEquals("数学", item.subject)
        assertEquals("B", item.selectedAnswer)
        assertEquals("C", item.correctAnswer)
        assertTrue(item.wrongEvidence.contains("正确选项"))
    }

    @Test
    fun markedEnglishWordLookupParses() {
        val payload = Gson().fromJson(
            """
            {
              "words": [{
                "word": "resilience",
                "lemma": "resilience",
                "phonetic": "/rɪˈzɪliəns/",
                "partOfSpeech": "n.",
                "meaningChinese": "韧性；恢复力",
                "contextSentence": "Resilience helps students recover from setbacks.",
                "markType": "圈画",
                "pageIndex": 0,
                "confidence": 0.96
              }]
            }
            """.trimIndent(),
            MarkedWordsPayload::class.java
        )
        val word = payload.words.single()
        assertEquals("resilience", word.word)
        assertEquals("韧性；恢复力", word.meaningChinese)
        assertEquals("圈画", word.markType)
    }
}
