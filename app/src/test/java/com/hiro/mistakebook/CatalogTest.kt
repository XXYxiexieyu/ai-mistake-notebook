package com.hiro.mistakebook

import com.google.gson.Gson
import com.hiro.mistakebook.data.KnowledgeTreeData
import com.hiro.mistakebook.data.FullPageScanPayload
import com.hiro.mistakebook.data.KnowledgeMatcher
import com.hiro.mistakebook.ai.InkColorAnalyzer
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
                "box": [100, 200, 800, 500],
                "isWrong": true,
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
        assertEquals(4, payload.questions.first().box.size)
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
}
