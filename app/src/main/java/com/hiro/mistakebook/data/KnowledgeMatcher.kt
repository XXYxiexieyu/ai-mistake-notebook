package com.hiro.mistakebook.data

import java.text.Normalizer

object KnowledgeMatcher {
    fun match(
        catalog: KnowledgeCatalog,
        subject: String,
        bookHint: String,
        unitHint: String,
        chapterHint: String,
        points: List<String>
    ): List<KnowledgeNodeRef> =
        match(catalog.booksFor(subject), bookHint, unitHint, chapterHint, points)

    fun match(
        subjectBooks: List<KnowledgeBook>,
        bookHint: String,
        unitHint: String,
        chapterHint: String,
        points: List<String>
    ): List<KnowledgeNodeRef> {
        val books = subjectBooks
        if (books.isEmpty()) return points.distinct().map { KnowledgeNodeRef(it) }
        val exactBook = books.firstOrNull {
            normalize(it.title) == normalize(bookHint) || normalize(bookHint).contains(normalize(it.title))
        }
        val exactUnit = exactBook?.units?.firstOrNull {
            normalize(it.title) == normalize(unitHint) || normalize(unitHint).contains(normalize(it.title))
        }

        return points.filter { it.isNotBlank() }.distinct().map { point ->
            val pointKey = normalize(point)
            val directSection = exactUnit?.sections?.firstOrNull {
                val sectionKey = normalize(it)
                sectionKey == pointKey || sectionKey.contains(pointKey) || pointKey.contains(sectionKey)
            }
            if (exactBook != null && exactUnit != null && directSection != null) {
                return@map KnowledgeNodeRef(
                    knowledgePoint = point,
                    book = exactBook.title,
                    unit = exactUnit.title,
                    chapter = directSection,
                    matched = true,
                    matchMethod = "章节定位"
                )
            }

            val best = books.asSequence()
                .flatMap { book -> book.units.asSequence().map { book to it } }
                .mapNotNull { (book, unit) ->
                    val bestSection = unit.sections.maxByOrNull { section ->
                        similarity(pointKey, normalize(section))
                    }
                    val score = bestSection?.let { similarity(pointKey, normalize(it)) } ?: 0.0
                    val unitScore = similarity(pointKey, normalize(unit.title))
                    if (score < 0.3 && unitScore < 0.45) null
                    else Candidate(book.title, unit.title, bestSection.orEmpty(), maxOf(score, unitScore))
                }
                .maxByOrNull { it.score }

            if (best != null) {
                KnowledgeNodeRef(
                    knowledgePoint = point,
                    book = best.book,
                    unit = best.unit,
                    chapter = best.chapter,
                    matched = true,
                    matchMethod = if (best.score >= 0.75) "知识网匹配" else "知识网近似匹配"
                )
            } else {
                KnowledgeNodeRef(
                    knowledgePoint = point,
                    book = exactBook?.title ?: bookHint,
                    unit = exactUnit?.title ?: unitHint,
                    chapter = chapterHint,
                    matched = false,
                    matchMethod = "待人工确认"
                )
            }
        }
    }

    private data class Candidate(
        val book: String,
        val unit: String,
        val chapter: String,
        val score: Double
    )

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.82
        val setA = bigrams(a)
        val setB = bigrams(b)
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val overlap = setA.intersect(setB).size.toDouble()
        return overlap / setA.union(setB).size
    }

    private fun bigrams(value: String): Set<String> {
        if (value.length < 2) return setOf(value)
        return value.windowed(2).toSet()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(Regex("^[第0-9一二三四五六七八九十百零〇\\s\\.、（）()]+"), "")
            .replace(Regex("[\\s:：，,。；;、·（）()\\-—]"), "")
            .lowercase()
}
