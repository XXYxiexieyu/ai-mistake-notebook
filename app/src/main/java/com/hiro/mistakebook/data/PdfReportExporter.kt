package com.hiro.mistakebook.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportExporter(private val context: Context) {
    private val gson = Gson()

    suspend fun export(
        uri: Uri,
        allQuestions: List<QuestionEntity>,
        subject: String?,
        startTime: Long?,
        endTime: Long?
    ): Int = withContext(Dispatchers.IO) {
        val filtered = allQuestions.filter {
            (subject == null || it.subject == subject) &&
                (startTime == null || it.captureTime >= startTime) &&
                (endTime == null || it.captureTime <= endTime)
        }
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42f
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 95, 77)
            textSize = 22f
            isFakeBoldText = true
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 95, 77)
            textSize = 15f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 55, 52)
            textSize = 11f
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(105, 115, 111)
            textSize = 9.5f
        }

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun line(text: String, paint: Paint = bodyPaint, gap: Float = 18f) {
            val lines = wrapText(text, paint, pageWidth - margin * 2)
            for (part in lines) {
                if (y + gap > pageHeight - margin) newPage()
                canvas.drawText(part, margin, y, paint)
                y += gap
            }
        }

        canvas.drawText("高三错题知识点报告", margin, y, titlePaint)
        y += 30f
        line(
            "生成时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())}",
            mutedPaint
        )
        line(
            "统计范围：${subject ?: "全部科目"}，共 ${filtered.size} 道已收录错题",
            mutedPaint,
            26f
        )

        val grouped = filtered
            .groupBy { it.subject }
            .toSortedMap(compareBy { Subjects.all.indexOf(it).takeIf { index -> index >= 0 } ?: 999 })
        if (grouped.isEmpty()) {
            line("当前范围内没有错题数据。", bodyPaint)
        }

        for ((subjectName, questions) in grouped) {
            if (y > pageHeight - 100) newPage()
            y += 8f
            line(subjectName, sectionPaint, 24f)
            val books = questions.groupBy { it.book.ifBlank { "未归类书目" } }
            for ((book, bookQuestions) in books) {
                if (y > pageHeight - 80) newPage()
                line("书目：$book（${bookQuestions.size} 题）", bodyPaint)
                val units = bookQuestions.groupBy { it.unit.ifBlank { "未归类单元" } }
                for ((unit, unitQuestions) in units) {
                    if (y > pageHeight - 65) newPage()
                    line("单元：$unit", bodyPaint)
                    val points = unitQuestions
                        .flatMap { question ->
                            runCatching {
                                gson.fromJson(
                                    question.knowledgePointsJson,
                                    Array<String>::class.java
                                ).toList()
                            }.getOrDefault(emptyList())
                        }
                        .filter { it.isNotBlank() }
                        .groupingBy { it }
                        .eachCount()
                    if (points.isEmpty()) {
                        line("  错误知识点：未归类", mutedPaint)
                    } else {
                        points.entries.sortedByDescending { it.value }.forEach { (point, count) ->
                            line("  错误知识点：$point（$count 题）", mutedPaint)
                        }
                    }
                    val causes = unitQuestions
                        .filter { it.errorCauseState != ErrorCauseState.UNSUPPLEMENTED.name }
                        .map { it.errorCause.trim() }
                        .filter { it.isNotEmpty() }
                        .groupingBy { it }
                        .eachCount()
                    causes.entries.sortedByDescending { it.value }.forEach { (cause, count) ->
                        line("  错因：$cause（$count 题）", mutedPaint)
                    }
                    val unsupplied = unitQuestions.count {
                        it.errorCauseState == ErrorCauseState.UNSUPPLEMENTED.name || it.errorCause.isBlank()
                    }
                    if (unsupplied > 0) line("  未补充错因：$unsupplied 题", mutedPaint)
                }
            }
        }
        document.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
            ?: error("无法打开 PDF 输出文件")
        document.close()
        pageNumber
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        var current = ""
        for (char in text) {
            val candidate = current + char
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                result += current
                current = char.toString()
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) result += current
        return result
    }
}
