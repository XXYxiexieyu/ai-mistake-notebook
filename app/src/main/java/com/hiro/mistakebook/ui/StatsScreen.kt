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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private enum class SummaryTab(val label: String) {
    ALL("全部总结"),
    SUBJECT("各科目总结"),
    BOOK("各书目总结")
}

private data class SummarySnapshot(
    val questions: List<QuestionEntity>,
    val recentCount: Int,
    val statusCounts: Map<QuestionStatus, Int>,
    val knowledgePoints: List<PointStat>,
    val causes: List<Map.Entry<String, Int>>,
    val unsupplied: Int
)

@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(SummaryTab.ALL.name) }
    val tab = runCatching { SummaryTab.valueOf(tabName) }.getOrDefault(SummaryTab.ALL)
    var bookSubject by rememberSaveable { mutableStateOf("全部") }
    val overall = remember(questions) { buildSnapshot(questions) }

    val subjectSummaries = remember(questions) {
        Subjects.all.map { subject ->
            subject to buildSnapshot(questions.filter { it.subject == subject })
        }
    }
    val bookSummaries = remember(questions, bookSubject) {
        questions
            .filter { bookSubject == "全部" || it.subject == bookSubject }
            .groupBy { it.subject to it.book.ifBlank { "未归类书目" } }
            .map { (key, scoped) -> Triple(key.first, key.second, buildSnapshot(scoped)) }
            .sortedWith(
                compareBy<Triple<String, String, SummarySnapshot>> {
                    Subjects.all.indexOf(it.first).takeIf { index -> index >= 0 } ?: 999
                }.thenBy { it.second }
            )
    }

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
                "内容实时读取本地错题。只收错题时，错题占比不等同于完整掌握率。",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SummaryTab.entries) { item ->
                    FilterChip(
                        selected = tab == item,
                        onClick = { tabName = item.name },
                        label = { Text(item.label) }
                    )
                }
            }
        }

        when (tab) {
            SummaryTab.ALL -> allSummaryItems(overall)
            SummaryTab.SUBJECT -> {
                item {
                    SectionHint("按科目查看错题数量、复习状态、易错知识点和错误原因。")
                }
                items(subjectSummaries, key = { it.first }) { (subject, snapshot) ->
                    ScopeSummaryCard(
                        title = subject,
                        subtitle = "科目总结",
                        accent = chartColors[Subjects.all.indexOf(subject).mod(chartColors.size)],
                        snapshot = snapshot
                    )
                }
            }
            SummaryTab.BOOK -> {
                item {
                    SectionHint("选择科目后查看每本教材的错题总结。")
                    SubjectChips(
                        selected = bookSubject,
                        onSelected = { bookSubject = it }
                    )
                }
                if (bookSummaries.isEmpty()) {
                    item {
                        Text("当前筛选范围没有可统计的错题。", color = Muted)
                    }
                } else {
                    items(bookSummaries, key = { "${it.first}-${it.second}" }) { item ->
                        ScopeSummaryCard(
                            title = item.second,
                            subtitle = "${item.first} · 书目总结",
                            accent = chartColors[
                                Subjects.all.indexOf(item.first).takeIf { it >= 0 }?.mod(chartColors.size) ?: 0
                            ],
                            snapshot = item.third
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.allSummaryItems(
    snapshot: SummarySnapshot
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatBox("错题总数", snapshot.questions.size.toString(), Modifier.weight(1f))
            StatBox("近30天", snapshot.recentCount.toString(), Modifier.weight(1f))
            StatBox(
                "已通过",
                (snapshot.statusCounts[QuestionStatus.PASSED] ?: 0).toString(),
                Modifier.weight(1f)
            )
        }
    }
    item {
        val subjectCounts = snapshot.questions
            .groupingBy { it.subject }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
        ChartCard("各科目错题分布") {
            if (subjectCounts.isEmpty()) {
                Text("暂无数据", color = Muted)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DonutChart(
                        values = subjectCounts.map { it.second },
                        modifier = Modifier.size(130.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        subjectCounts.forEachIndexed { index, entry ->
                            LegendRow(
                                chartColors[index % chartColors.size],
                                "${entry.first}  ${entry.second}"
                            )
                        }
                    }
                }
            }
        }
    }
    item {
        ChartCard("全部总结 · 复习状态") {
            StatusBars(snapshot)
        }
    }
    item {
        ChartCard("全部总结 · 易错知识点") {
            KnowledgePointList(snapshot.knowledgePoints)
        }
    }
    item {
        ChartCard("全部总结 · 错误原因") {
            CauseList(snapshot.causes, snapshot.unsupplied)
        }
    }
}

@Composable
private fun ScopeSummaryCard(
    title: String,
    subtitle: String,
    accent: Color,
    snapshot: SummarySnapshot
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${snapshot.questions.size} 题",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "近30天 ${snapshot.recentCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                }
            }
            if (snapshot.questions.isEmpty()) {
                Text("暂无错题", color = Muted, style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            StatusBars(snapshot)
            HorizontalDividerLine()
            Text("易错知识点", style = MaterialTheme.typography.labelLarge, color = Muted)
            KnowledgePointList(snapshot.knowledgePoints.take(3), compact = true)
            if (snapshot.causes.isNotEmpty() || snapshot.unsupplied > 0) {
                HorizontalDividerLine()
                Text("常见错误原因", style = MaterialTheme.typography.labelLarge, color = Muted)
                CauseList(snapshot.causes.take(3), snapshot.unsupplied, compact = true)
            }
        }
    }
}

@Composable
private fun StatusBars(snapshot: SummarySnapshot) {
    QuestionStatus.entries.forEach { status ->
        BarRow(
            status.label,
            snapshot.statusCounts[status] ?: 0,
            snapshot.questions.size,
            chartColor(status)
        )
    }
}

@Composable
private fun KnowledgePointList(
    points: List<PointStat>,
    compact: Boolean = false
) {
    if (points.isEmpty()) {
        Text("还没有可用于统计的知识点。", color = Muted)
        return
    }
    points.forEachIndexed { index, item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${index + 1}. ${item.name}",
                modifier = Modifier.weight(1f),
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${item.count} 题",
                color = if (index < 3) Red else Muted,
                fontWeight = if (index < 3) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        if (!compact) {
            Text(
                "重考失败 ${item.failures} 次 · 最近 ${formatDate(item.latest)}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun CauseList(
    causes: List<Map.Entry<String, Int>>,
    unsupplied: Int,
    compact: Boolean = false
) {
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
            "另有 $unsupplied 道题未补充错因，未计入上方分布。",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HorizontalDividerLine() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

private fun buildSnapshot(all: List<QuestionEntity>): SummarySnapshot {
    val recentCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
    val causes = all
        .filter { it.errorCauseState != ErrorCauseState.UNSUPPLEMENTED.name }
        .map { it.errorCause.trim() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
    return SummarySnapshot(
        questions = all,
        recentCount = all.count { it.captureTime >= recentCutoff },
        statusCounts = QuestionStatus.entries.associateWith { status ->
            all.count { it.status == status.name }
        },
        knowledgePoints = aggregateKnowledgePoints(all),
        causes = causes,
        unsupplied = all.count {
            it.errorCauseState == ErrorCauseState.UNSUPPLEMENTED.name || it.errorCause.isBlank()
        }
    )
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
