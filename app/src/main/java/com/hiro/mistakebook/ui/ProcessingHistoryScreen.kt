package com.hiro.mistakebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.BatchSplitProgress
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.PageScanEntity
import com.hiro.mistakebook.data.QuestionEntity
import com.hiro.mistakebook.data.WordLookupCodec
import com.hiro.mistakebook.data.WordLookupStatus
import com.hiro.mistakebook.data.WordLookupTaskEntity
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import com.hiro.mistakebook.ui.theme.Red

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenQuestion: (Long) -> Unit,
    onReviewScan: (Long) -> Unit,
    onOpenWordLookup: (Long) -> Unit
) {
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    val pageScan by viewModel.activePageScan.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchSplitProgress.collectAsStateWithLifecycle()
    val pendingReviewCount by viewModel.pendingReviewCount.collectAsStateWithLifecycle()
    val latestPendingReviewScanId by viewModel.latestPendingReviewScanId.collectAsStateWithLifecycle()
    val wordLookupTasks by viewModel.wordLookupTasks.collectAsStateWithLifecycle()
    val visibleWordLookupTasks = wordLookupTasks.filter {
        WordLookupCodec.pagePaths(it.pagePathsJson).isNotEmpty()
    }
    val activeWordLookupCount = visibleWordLookupTasks.count {
        runCatching { WordLookupStatus.valueOf(it.status) }
            .getOrDefault(WordLookupStatus.FAILED).isActive
    }
    val completedWordLookupCount = visibleWordLookupTasks.count {
        it.status == WordLookupStatus.COMPLETED.name
    }
    val pendingAnalysis = questions.count {
        it.analysisState == "PENDING" || it.analysisState == "PROCESSING"
    }
    val completedQuestions = questions.count {
        it.analysisState == "DONE" || it.analysisState == "MANUAL"
    }
    val batchRemaining = batchProgress?.let { (it.total - it.current).coerceAtLeast(0) } ?: 0
    val scanRemaining = if (pageScan != null) 1 else 0
    val completed = completedQuestions + (batchProgress?.generated ?: 0) +
        completedWordLookupCount
    val remaining = pendingAnalysis + pendingReviewCount + batchRemaining + scanRemaining +
        activeWordLookupCount
    val history = remember(questions) {
        questions
            .sortedByDescending { it.createdAt }
            .groupBy { formatDate(it.createdAt) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加历史与处理进度") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProcessingCountCard("已完成", completed, Green, Modifier.weight(1f))
                    ProcessingCountCard("剩余", remaining, Red, Modifier.weight(1f))
                }
            }
            item {
                Text(
                    "当前导入进度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (pageScan == null && batchProgress == null && pendingAnalysis == 0 &&
                pendingReviewCount == 0 && activeWordLookupCount == 0
            ) {
                item {
                    StatusCard("当前没有正在处理的任务", null)
                }
            }
            pageScan?.let { scan ->
                item {
                    PageScanProgressCard(scan)
                }
            }
            batchProgress?.let { progress ->
                item {
                    ProgressCard(
                        title = "批量 AI 分题",
                        detail = "已处理 ${progress.current}/${progress.total} 张图片，新增 ${progress.generated} 道候选错题",
                        progress = progress.current.toFloat() / progress.total.coerceAtLeast(1)
                    )
                }
            }
            if (pendingAnalysis > 0) {
                item {
                    StatusCard("AI 分析队列：剩余 $pendingAnalysis 道题", "等待联网或后台处理")
                }
            }
            if (pendingReviewCount > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "AI 错题待审查：$pendingReviewCount 道",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("确认后才会进入正式错题本。", color = Muted)
                            latestPendingReviewScanId?.let { scanId ->
                                Button(onClick = { onReviewScan(scanId) }) {
                                    Text("进入错题审查")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "英语阅读查词任务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (visibleWordLookupTasks.isEmpty()) {
                item { Text("尚未上传英语查词图片。", color = Muted) }
            } else {
                items(visibleWordLookupTasks.take(12), key = { "word-lookup-${it.id}" }) { task ->
                    WordLookupProgressCard(
                        task = task,
                        onOpen = { onOpenWordLookup(task.id) }
                    )
                }
            }
            item {
                Text(
                    "错题添加历史",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (history.isEmpty()) {
                item { Text("尚未添加错题。", color = Muted) }
            } else {
                history.forEach { (date, items) ->
                    item(key = "history-$date") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(date, fontWeight = FontWeight.SemiBold, color = Green)
                            Text("${items.size} 题", color = Muted)
                        }
                    }
                    items(items, key = { "question-${it.id}" }) { question ->
                        QuestionCard(
                            question = question,
                            onClick = { onOpenQuestion(question.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordLookupProgressCard(
    task: WordLookupTaskEntity,
    onOpen: () -> Unit
) {
    val status = runCatching { WordLookupStatus.valueOf(task.status) }
        .getOrDefault(WordLookupStatus.FAILED)
    val pageCount = WordLookupCodec.pagePaths(task.pagePathsJson).size
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(status.label, fontWeight = FontWeight.SemiBold)
                Text(
                    formatDate(task.createdAt, includeTime = true),
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                "$pageCount 页 · 识别 ${task.recognizedCount} 个 · 已加入 ${task.wordCount} 个",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            if (status.isActive) {
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            task.error.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Red, style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onOpen) {
                Text(
                    when (status) {
                        WordLookupStatus.REVIEW -> "查看自动入库进度"
                        WordLookupStatus.COMPLETED -> "查看上传记录"
                        WordLookupStatus.FAILED -> "继续处理"
                        else -> "查看查词进度"
                    }
                )
            }
        }
    }
}

@Composable
private fun ProcessingCountCard(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun PageScanProgressCard(scan: PageScanEntity) {
    val status = runCatching {
        com.hiro.mistakebook.data.AnalysisState.valueOf(scan.status)
    }.getOrDefault(com.hiro.mistakebook.data.AnalysisState.PENDING)
    ProgressCard(
        title = "整页扫描",
        detail = if (status == com.hiro.mistakebook.data.AnalysisState.DONE) {
            "识别完成，发现 ${scan.detectedCount} 道候选题"
        } else {
            processingStageLabel(scan.stage)
        },
        progress = scan.progress.coerceIn(0, 100) / 100f
    )
}

@Composable
private fun ProgressCard(title: String, detail: String, progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                CircularProgressIndicator(
                    modifier = Modifier.padding(2.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    if (detail == null) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    null,
                    tint = if (detail == null) Green else Muted
                )
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun processingStageLabel(stage: String): String = when (stage) {
    "QUEUED" -> "页面已保存，等待处理"
    "WAITING_KEY" -> "等待配置 API 密钥"
    "RETRY_WAIT" -> "等待网络后重试"
    "ENHANCING" -> "正在生成彩色笔迹增强图"
    "AI_ANALYZING" -> "AI 正在检查多页题目和批改痕迹"
    "SAVING" -> "正在裁题并生成待审查结果"
    "COMPLETE" -> "处理完成"
    "FAILED" -> "处理失败"
    else -> "正在处理"
}
