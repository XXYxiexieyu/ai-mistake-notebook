package com.hiro.mistakebook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.QuestionEntity
import com.hiro.mistakebook.data.QuestionStatus
import com.hiro.mistakebook.data.Subjects
import com.hiro.mistakebook.ui.theme.Amber
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import com.hiro.mistakebook.ui.theme.Red
import java.util.concurrent.TimeUnit

private val chartColors = listOf(
    Color(0xFF176B57),
    Color(0xFF4E9F86),
    Color(0xFFE0A23B),
    Color(0xFF7A8FC7),
    Color(0xFFB66666),
    Color(0xFF6B927B)
)

@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    val subjectCounts = remember(questions) {
        Subjects.all.associateWith { subject -> questions.count { it.subject == subject } }
            .filterValues { it > 0 }
    }
    val statusCounts = remember(questions) {
        QuestionStatus.entries.associateWith { status -> questions.count { it.status == status.name } }
    }
    val points = remember(questions) { aggregateKnowledgePoints(questions) }
    val causes = remember(questions) {
        questions
            .filter { it.errorCauseState != ErrorCauseState.UNSUPPLEMENTED.name }
            .map { it.errorCause.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
    }
    val recentCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
    val recentCount = questions.count { it.captureTime >= recentCutoff }
    val unsupplied = questions.count { it.errorCauseState == ErrorCauseState.UNSUPPLEMENTED.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "总结",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "只分析已收录错题，不把错题占比解释为知识点掌握率。",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBox("错题总数", questions.size.toString(), Modifier.weight(1f))
                StatBox("近30天", recentCount.toString(), Modifier.weight(1f))
                StatBox("已通过", (statusCounts[QuestionStatus.PASSED] ?: 0).toString(), Modifier.weight(1f))
            }
        }
        item {
            ChartCard("各科错题分布") {
                if (subjectCounts.isEmpty()) {
                    Text("暂无数据", color = Muted)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DonutChart(
                            values = subjectCounts.values.toList(),
                            modifier = Modifier.size(130.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            subjectCounts.entries.forEachIndexed { index, entry ->
                                LegendRow(
                                    chartColors[index % chartColors.size],
                                    "${entry.key}  ${entry.value}"
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            ChartCard("复习状态") {
                statusCounts.forEach { (status, count) ->
                    BarRow(status.label, count, questions.size, chartColor(status))
                }
            }
        }
        item {
            ChartCard("易错知识点") {
                if (points.isEmpty()) {
                    Text("还没有可用于统计的知识点。", color = Muted)
                } else {
                    points.take(8).forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}. ${item.name}",
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${item.count} 题",
                                color = if (index < 3) Red else Muted,
                                fontWeight = if (index < 3) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        Text(
                            "重考失败 ${item.failures} 次 · 最近 ${formatDate(item.latest)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                }
            }
        }
        item {
            ChartCard("错误原因") {
                if (causes.isEmpty()) {
                    Text("没有已确认或 AI 给出的错因。", color = Muted)
                } else {
                    val max = causes.maxOf { it.value }
                    causes.forEach { entry ->
                        BarRow(entry.key, entry.value, max, Amber)
                    }
                }
                if (unsupplied > 0) {
                    Text(
                        "另有 $unsupplied 道题选择不补充错因，未计入上方分布。",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private data class PointStat(
    val name: String,
    val count: Int,
    val failures: Int,
    val latest: Long
)

private fun aggregateKnowledgePoints(questions: List<QuestionEntity>): List<PointStat> {
    val stats = mutableMapOf<String, PointStat>()
    for (question in questions) {
        knowledgePoints(question.knowledgePointsJson).forEach { raw ->
            val name = raw.trim()
            if (name.isBlank()) return@forEach
            val current = stats[name]
            stats[name] = if (current == null) {
                PointStat(name, 1, question.retakeFailures, question.captureTime)
            } else {
                current.copy(
                    count = current.count + 1,
                    failures = current.failures + question.retakeFailures,
                    latest = maxOf(current.latest, question.captureTime)
                )
            }
        }
    }
    return stats.values.sortedWith(
        compareByDescending<PointStat> { it.count }
            .thenByDescending { it.failures }
            .thenByDescending { it.latest }
    )
}

private fun chartColor(status: QuestionStatus): Color = when (status) {
    QuestionStatus.UNREVIEWED -> Red
    QuestionStatus.REVIEWED -> Amber
    QuestionStatus.PASSED -> Green
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = Green, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DonutChart(values: List<Int>, modifier: Modifier = Modifier) {
    val total = values.sum().coerceAtLeast(1)
    Canvas(modifier = modifier) {
        val stroke = 24.dp.toPx()
        val inset = stroke / 2
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value.toFloat() / total * 360f
            drawArc(
                color = chartColors[index % chartColors.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke)
            )
            start += sweep
        }
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(3.dp)))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BarRow(label: String, value: Int, max: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value.toString(), color = Muted)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value.toFloat() / max.coerceAtLeast(1)).coerceIn(0.02f, 1f))
                    .height(7.dp)
                    .background(color, RoundedCornerShape(10.dp))
            )
        }
    }
}
