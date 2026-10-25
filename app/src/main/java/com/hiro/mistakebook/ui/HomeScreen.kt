package com.hiro.mistakebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.QuestionEntity
import com.hiro.mistakebook.data.QuestionStatus
import com.hiro.mistakebook.ui.theme.Amber
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import java.util.concurrent.TimeUnit

private enum class GroupMode(val label: String) {
    TIME("拍摄时间"),
    SUBJECT("科目"),
    BOOK("书目"),
    UNIT("单元"),
    TYPE("题型")
}

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenQuestion: (Long) -> Unit,
    onOpenRetake: () -> Unit
) {
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    val apiConfigured by viewModel.apiConfigured.collectAsStateWithLifecycle()
    var subject by rememberSaveable { mutableStateOf(viewModel.repository.settings.lastSubjectFilter) }
    var statusFilter by rememberSaveable { mutableStateOf("全部状态") }
    var bookFilter by rememberSaveable { mutableStateOf("全部书目") }
    var unitFilter by rememberSaveable { mutableStateOf("全部单元") }
    var knowledgeFilter by rememberSaveable { mutableStateOf("全部知识点") }
    var query by rememberSaveable { mutableStateOf("") }
    var groupMode by rememberSaveable { mutableStateOf(GroupMode.TIME) }
    var ascending by rememberSaveable { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }

    val filtered = remember(
        questions,
        subject,
        statusFilter,
        bookFilter,
        unitFilter,
        knowledgeFilter,
        query
    ) {
        questions.filter { question ->
            (subject == "全部" || question.subject == subject) &&
                (statusFilter == "全部状态" || statusLabel(question.status) == statusFilter) &&
                (bookFilter == "全部书目" || question.book == bookFilter) &&
                (unitFilter == "全部单元" || question.unit == unitFilter) &&
                (
                    knowledgeFilter == "全部知识点" ||
                        knowledgePoints(question.knowledgePointsJson).contains(knowledgeFilter)
                    ) &&
                (
                    query.isBlank() ||
                        question.questionText.contains(query, ignoreCase = true) ||
                        question.book.contains(query, ignoreCase = true) ||
                        question.unit.contains(query, ignoreCase = true) ||
                        question.chapter.contains(query, ignoreCase = true) ||
                        question.errorCause.contains(query, ignoreCase = true)
                    )
        }
    }
    val groups = remember(filtered, groupMode, ascending) {
        groupQuestions(filtered, groupMode, ascending)
    }
    val availableBooks = remember(questions, subject) {
        if (subject == "全部") {
            questions.map { it.book }.filter { it.isNotBlank() }.distinct().sorted()
        } else {
            viewModel.catalog.booksFor(subject).map { it.title }
        }
    }
    val availableUnits = remember(questions, subject, bookFilter) {
        if (bookFilter != "全部书目") {
            viewModel.catalog.units(subject, bookFilter).map { it.title }
        } else {
            questions
                .filter { subject == "全部" || it.subject == subject }
                .map { it.unit }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
    }
    val availableKnowledgePoints = remember(questions, subject, bookFilter, unitFilter) {
        questions
            .filter {
                (subject == "全部" || it.subject == subject) &&
                    (bookFilter == "全部书目" || it.book == bookFilter) &&
                    (unitFilter == "全部单元" || it.unit == unitFilter)
            }
            .flatMap { knowledgePoints(it.knowledgePointsJson) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val reminderCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365)
    val oldPassed = questions.filter {
        it.status == QuestionStatus.PASSED.name && (it.passedAt ?: Long.MAX_VALUE) <= reminderCutoff
    }
    val reminderEnabled = viewModel.yearReminderEnabled()
    val pendingCount = questions.count { it.analysisState == "PENDING" || it.analysisState == "PROCESSING" }
    val reviewCount = questions.count { it.status == QuestionStatus.UNREVIEWED.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "高三错题本",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "本地分类随时可用，AI 分析联网后自动继续",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = onOpenRetake) {
                    Icon(Icons.Outlined.Refresh, null)
                    Text(" 重考")
                }
            }
        }

        if (!apiConfigured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CloudOff, null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("尚未配置 DeepSeek API", fontWeight = FontWeight.SemiBold)
                            Text(
                                "拍照、手动分类、查看和统计仍可离线使用。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OverviewNumber("全部", questions.size, Modifier.weight(1f))
                OverviewNumber("待复习", reviewCount, Modifier.weight(1f))
                OverviewNumber("待分析", pendingCount, Modifier.weight(1f))
            }
        }

        if (reminderEnabled && oldPassed.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "有 ${oldPassed.size} 道已通过满一年的错题",
                            fontWeight = FontWeight.SemiBold,
                            color = Amber
                        )
                        Text(
                            "不会自动删除。你可以逐题查看或批量清理已通过的旧数据。",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showReminder = true }) {
                                Text("查看")
                            }
                            Button(onClick = { showReminder = true }) {
                                Text("处理")
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("搜索题目、书目、单元或错因") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        item {
            SubjectChips(
                selected = subject,
                onSelected = {
                    subject = it
                    bookFilter = "全部书目"
                    unitFilter = "全部单元"
                    knowledgeFilter = "全部知识点"
                    viewModel.saveLastSubjectFilter(it)
                }
            )
        }

        item {
            SelectionChipRow(
                prefix = "书目",
                allLabel = "全部书目",
                options = availableBooks,
                selected = bookFilter,
                onSelected = {
                    bookFilter = it
                    unitFilter = "全部单元"
                    knowledgeFilter = "全部知识点"
                }
            )
        }

        if (availableUnits.isNotEmpty()) {
            item {
                SelectionChipRow(
                    prefix = "单元",
                    allLabel = "全部单元",
                    options = availableUnits,
                    selected = unitFilter,
                    onSelected = {
                        unitFilter = it
                        knowledgeFilter = "全部知识点"
                    }
                )
            }
        }

        if (availableKnowledgePoints.isNotEmpty()) {
            item {
                SelectionChipRow(
                    prefix = "知识点",
                    allLabel = "全部知识点",
                    options = availableKnowledgePoints,
                    selected = knowledgeFilter,
                    onSelected = { knowledgeFilter = it }
                )
            }
        }

        item {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statuses = listOf("全部状态", "未复习", "已复习", "重考通过")
                items(statuses) { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = { statusFilter = status },
                        label = { Text(status) }
                    )
                }
            }
        }

        item {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GroupMode.entries) { mode ->
                    FilterChip(
                        selected = groupMode == mode,
                        onClick = { groupMode = mode },
                        label = { Text("按${mode.label}") }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyNotebook()
            }
        } else {
            groups.forEach { (groupName, groupItems) ->
                item(key = "header-$groupName") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("${groupItems.size} 题", color = Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                items(groupItems, key = { it.id }) { question ->
                    QuestionCard(question, onClick = { onOpenQuestion(question.id) })
                }
            }
                item {
                    FilterChip(
                        selected = ascending,
                        onClick = { ascending = !ascending },
                        label = { Text(if (ascending) "时间正序" else "时间倒序") }
                    )
                }
        }
    }

    if (showReminder) {
        AlertDialog(
            onDismissRequest = { showReminder = false },
            title = { Text("一年前的已通过错题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    oldPassed.take(8).forEach { question ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${question.subject} · ${formatDate(question.passedAt ?: question.captureTime)}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = {
                                viewModel.deleteQuestion(question.id)
                            }) {
                                Text("删除本题")
                            }
                        }
                    }
                    if (oldPassed.size > 8) {
                        Text("另有 ${oldPassed.size - 8} 道未展开", color = Muted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOldPassed(oldPassed.map { it.id })
                    showReminder = false
                }) {
                    Icon(Icons.Outlined.DeleteOutline, null)
                    Text("删除全部符合项")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminder = false }) { Text("取消") }
            }
        )
    }
}

private fun statusLabel(state: String): String = runCatching {
    QuestionStatus.valueOf(state).label
}.getOrDefault(state)

private fun groupQuestions(
    questions: List<QuestionEntity>,
    mode: GroupMode,
    ascending: Boolean
): List<Pair<String, List<QuestionEntity>>> {
    val grouped = when (mode) {
        GroupMode.TIME -> questions.groupBy { formatDate(it.captureTime) }
        GroupMode.SUBJECT -> questions.groupBy { it.subject }
        GroupMode.BOOK -> questions.groupBy { it.book.ifBlank { "未归类书目" } }
        GroupMode.UNIT -> questions.groupBy { it.unit.ifBlank { "未归类单元" } }
        GroupMode.TYPE -> questions.groupBy { it.questionType.ifBlank { "未分类题型" } }
    }
    val entries = grouped.entries.sortedBy { entry ->
        entry.value.maxOfOrNull { it.captureTime } ?: 0L
    }
    val orderedEntries = if (ascending) entries else entries.reversed()
    return orderedEntries.map { entry ->
        val items = entry.value.sortedBy { question -> question.captureTime }
        entry.key to (if (ascending) items else items.reversed())
    }
}

@Composable
private fun SelectionChipRow(
    prefix: String,
    allLabel: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == allLabel,
                onClick = { onSelected(allLabel) },
                label = { Text(allLabel) }
            )
        }
        items(options) { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
private fun OverviewNumber(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = Green,
                fontWeight = FontWeight.Bold
            )
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyNotebook() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("还没有符合条件的错题", fontWeight = FontWeight.SemiBold)
            Text(
                "即使在断网状态，也可以拍照、手动分类并保存。",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "点击底部中央的加号开始添加。",
                style = MaterialTheme.typography.bodySmall,
                color = Green
            )
        }
    }
}
