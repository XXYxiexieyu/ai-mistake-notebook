package com.hiro.mistakebook

import com.google.gson.Gson
import com.hiro.mistakebook.data.KnowledgeTreeData
import com.hiro.mistakebook.data.FullPageScanPayload
import com.hiro.mistakebook.data.KnowledgeMatcher
import com.hiro.mistakebook.ai.InkColorAnalyzer
import com.hiro.mistakebook.ai.AutoSplitPayload
import com.hiro.mistakebook.ai.MarkedWordsPayload
import com.hiro.mistakebook.chemistry.ChemistrySymbolDatabase
import com.hiro.mistakebook.chemistry.createChemistryNormalizer
import com.hiro.mistakebook.data.AnalysisPayload
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.QuestionDraft
import com.hiro.mistakebook.data.Subjects
import com.hiro.mistakebook.data.WordCsvExporter
import com.hiro.mistakebook.data.WordEntryEntity
import com.hiro.mistakebook.data.WordLookupCodec
import com.hiro.mistakebook.math.MathSymbolDatabase
import com.hiro.mistakebook.math.MathTextNormalizer
import com.hiro.mistakebook.math.createNormalizer
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
    private val mathSymbols by lazy {
        MathSymbolDatabase.parse(File("src/main/assets/math_symbols.json").readText(Charsets.UTF_8))
    }
    private val chemistrySymbols by lazy {
        ChemistrySymbolDatabase.parse(File("src/main/assets/chemistry_symbols.json").readText(Charsets.UTF_8))
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
                "otherMeanings": ["弹性", "适应力"],
                "collocations": ["resilience training — 韧性训练"],
                "wordForms": ["resilient adj. 有韧性的"],
                "exampleSentence": "Her resilience helped her recover. 她的韧性帮助她恢复。",
                "contextSentence": "Resilience helps students recover from setbacks.",
                "markType": "圈画",
                "pageIndex": 0,
                "box": [120, 240, 360, 310],
                "confidence": 0.96
              }]
            }
            """.trimIndent(),
            MarkedWordsPayload::class.java
        )
        val word = payload.words.single()
        assertEquals("resilience", word.word)
        assertEquals("韧性；恢复力", word.meaningChinese)
        assertEquals(listOf("弹性", "适应力"), word.otherMeanings)
        assertEquals(listOf("resilience training — 韧性训练"), word.collocations)
        assertEquals(listOf("resilient adj. 有韧性的"), word.wordForms)
        assertTrue(word.exampleSentence.contains("她的韧性"))
        assertEquals("圈画", word.markType)
        assertEquals(listOf(120f, 240f, 360f, 310f), word.box)
    }

    @Test
    fun wordLookupCodecAndCsvExportPreserveReviewData() {
        val payload = Gson().fromJson(
            """
            {
              "words": [{
                "word": "resilience",
                "contextSentence": "A sentence, with \"quotes\".",
                "pageIndex": 1,
                "box": [10, 20, 300, 80]
              }]
            }
            """.trimIndent(),
            MarkedWordsPayload::class.java
        )
        val encoded = WordLookupCodec.encodeRecognizedWords(payload.words)
        val decoded = WordLookupCodec.recognizedWords(encoded)
        assertEquals(1, decoded.size)
        assertEquals(listOf(10f, 20f, 300f, 80f), decoded.first().box)

        val csv = WordCsvExporter.toCsv(
            listOf(
                WordEntryEntity(
                    word = "resilience",
                    normalizedWord = "resilience",
                    meaningChinese = "韧性, 恢复力",
                    otherMeanings = "弹性\n适应力",
                    collocations = "resilience training — 韧性训练",
                    wordForms = "resilient adj. 有韧性的",
                    exampleSentence = "Her resilience helped her recover. 她的韧性帮助她恢复。",
                    contextSentence = "He said \"never give up\".",
                    occurrences = 3
                )
            )
        )
        assertTrue(csv.contains("\"韧性, 恢复力\""))
        assertTrue(csv.contains("\"resilience training — 韧性训练\""))
        assertTrue(csv.contains("\"He said \"\"never give up\"\".\""))
        assertTrue(csv.contains("\"3\""))
    }

    @Test
    fun mathSymbolDatabaseCoversGaokaoCategories() {
        assertTrue(mathSymbols.categories.size >= 8)
        assertTrue(mathSymbols.entries.size >= 220)
        val symbols = mathSymbols.entries.map { it.symbol }.toSet()
        listOf("²", "³", "√", "∠", "△", "⊥", "∥", "∈", "∉", "⊆", "π", "∞", "∑", "∫", "Φ").forEach {
            assertTrue("缺少数学符号：$it", it in symbols)
        }
        assertTrue(mathSymbols.latexSymbolMap.containsKey("\\angle"))
        assertTrue(mathSymbols.latexSymbolMap.containsKey("\\mathbb{R}"))
    }

    @Test
    fun latexAndPowerNotationBecomeReadableMathSymbols() {
        val normalizer = mathSymbols.createNormalizer()
        assertEquals("x²+y³", normalizer.normalize("x^2+y^3"))
        assertEquals("x¹⁰", normalizer.normalize("x^10"))
        assertEquals("x⁻¹", normalizer.normalize("x^-1"))
        assertEquals("xⁿ⁺¹", normalizer.normalize("x^{n+1}"))
        assertEquals("aₙ+aₙ₊₁", normalizer.normalize("a_n+a_{n+1}"))
        assertEquals("∠ABC=90°", normalizer.normalize("\\angle ABC=90^\\circ"))
        assertEquals("√(x²+y²)", normalizer.normalize("\\sqrt{x^2+y^2}"))
        assertEquals("(a+b)⁄(2)", normalizer.normalize("\\frac{a+b}{2}"))
        assertEquals("AB⃗", normalizer.normalize("\\vec{AB}"))
        assertEquals("x∈ℝ", normalizer.normalize("x\\in\\mathbb{R}"))
        assertEquals("{x, x≥0；-x, x<0}", normalizer.normalize("\\begin{cases}x,&x\\ge0\\\\-x,&x<0\\end{cases}"))
        assertEquals("答案", normalizer.normalize("\\boxed{答案}"))
    }

    @Test
    fun normalizerLeavesOrdinaryTextAndUnicodeIntact() {
        val normalizer = MathTextNormalizer()
        assertEquals("函数 y=x² 的定义域是 ℝ。", normalizer.normalize("函数 y=x² 的定义域是 ℝ。"))
        assertEquals("答案为 B。", normalizer.normalize("答案为 B。"))
    }

    @Test
    fun chemistrySymbolDatabaseCoversEquationsAndIons() {
        assertTrue(chemistrySymbols.categories.size >= 8)
        assertTrue(chemistrySymbols.entries.size >= 180)
        val symbols = chemistrySymbols.entries.map { it.symbol }.toSet()
        listOf("→", "⇌", "↑", "↓", "H⁺", "OH⁻", "SO₄²⁻", "Fe³⁺", "BaSO₄", "MnO₄⁻").forEach {
            assertTrue("缺少化学符号：$it", it in symbols)
        }
        assertTrue(chemistrySymbols.entries.any { it.name.contains("离子方程式") })
    }

    @Test
    fun chemistryEquationsAndIonicEquationsBecomeReadable() {
        assertEquals(
            "Ba²⁺ + SO₄²⁻ → BaSO₄↓",
            normalizeScience("Ba2+ + SO42- -> BaSO4↓")
        )
        assertEquals(
            "Fe³⁺ + 3OH⁻ → Fe(OH)₃↓",
            normalizeScience("Fe^{3+} + 3OH^- -> Fe(OH)_3↓")
        )
        assertEquals(
            "N₂ + 3H₂ ⇌ 2NH₃",
            normalizeScience("N2 + 3H2 <=> 2NH3")
        )
        assertEquals(
            "CO₂ + H₂O → H₂CO₃",
            normalizeScience("\\ce{CO2 + H2O -> H2CO3}")
        )
        assertEquals("→(催化剂)", normalizeScience("\\xrightarrow{催化剂}"))
        assertEquals("3Fe³⁺ + 4H₂O", normalizeScience("3Fe3+ + 4H2O"))
        assertEquals("the-end", normalizeScience("the-end"))
    }

    private fun normalizeScience(input: String): String {
        val chemistry = chemistrySymbols.createChemistryNormalizer()
        val math = mathSymbols.createNormalizer()
        return math.normalize(chemistry.normalize(input))
    }
}
