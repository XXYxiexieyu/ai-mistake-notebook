package com.hiro.mistakebook.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WordCsvExporter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun toCsv(words: List<WordEntryEntity>): String = buildString {
        appendCsvRow(
            listOf(
                "单词",
                "原形",
                "音标",
                "词性",
                "文中意义释义",
                "额外其他释义",
                "固定搭配",
                "词性转换",
                "造句",
                "原文语境",
                "标记方式",
                "出现次数",
                "首次加入时间",
                "最近更新时间"
            )
        )
        words.forEach { word ->
            appendCsvRow(
                listOf(
                    word.word,
                    word.lemma,
                    word.phonetic,
                    word.partOfSpeech,
                    word.meaningChinese,
                    word.otherMeanings,
                    word.collocations,
                    word.wordForms,
                    word.exampleSentence,
                    word.contextSentence,
                    word.markType,
                    word.occurrences.toString(),
                    formatTime(word.createdAt),
                    formatTime(word.updatedAt)
                )
            )
        }
    }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        append(values.joinToString(",") { value -> csvField(value) })
        append("\r\n")
    }

    private fun csvField(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    private fun formatTime(millis: Long): String =
        synchronized(dateFormat) { dateFormat.format(Date(millis)) }
}
