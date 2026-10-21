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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.AnalysisState
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.QuestionStatus
import com.hiro.mistakebook.ui.theme.Amber
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailScreen(
    viewModel: MainViewModel,
    questionId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onChat: () -> Unit
) {
    val question by remember(questionId) {
        viewModel.repository.observeQuestion(questionId)
    }.collectAsStateWithLifecycle(initialValue = null)
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("错题详情")
                        question?.let {
                            Text(
                                "${it.subject} · ${it.questionType.ifBlank { "未分类" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, "编辑")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Outlined.DeleteOutline, "删除")
                    }
                }
            )
        }
    ) { padding ->
        val item = question
        if (item == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("错题不存在或已经删除。")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ImagePanel(item.imagePath, Modifier.fillMaxWidth().height(270.dp))
            }
            item {
                Button(
                    onClick = onChat,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.QuestionAnswer, null)
                    Text(" 针对本题向 AI 提问")
                }
            }
            item {
                DetailCard("分类信息") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${item.book.ifBlank { "未归类书目" }} · ${item.unit.ifBlank { "未归类单元" }}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                item.chapter.ifBlank { "未填写章节" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted
                            )
                        }
                        StatusPill(item.status)
                    }
                    if (item.knowledgePointsJson != "[]") {
                        val points = knowledgePoints(item.knowledgePointsJson)
                        if (points.isNotEmpty()) {
                            Text("知识点", style = MaterialTheme.typography.labelMedium, color = Muted)
                            points.forEach { point ->
                                AssistChip(onClick = {}, label = { Text(point) })
                            }
                        }
                    }
                    if (item.mathTopic.isNotBlank()) {
                        Text("数学专题：${item.mathTopic}", color = Green)
                    }
                    Text(
                        "拍摄时间：${formatDate(item.captureTime, true)}  错误次数：${item.errorCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }
            if (item.wrongEvidence.isNotBlank() || item.evidenceType.isNotBlank()) {
                item {
                    DetailCard("整页扫描的错题依据") {
                        if (item.evidenceType.isNotBlank()) {
                            Text("批改类型：${item.evidenceType}", color = Amber)
                        }
                        Text(item.wrongEvidence.ifBlank { "未记录具体批改痕迹" })
                    }
                }
            }
            if (item.knowledgeMappingJson != "[]") {
                item {
                    val mappings = knowledgeNodeRefs(item.knowledgeMappingJson)
                    DetailCard("知识网定位") {
                        if (mappings.isEmpty()) {
                            Text("尚未匹配知识网节点。")
                        } else {
                            mappings.forEach { node ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        node.knowledgePoint,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (node.matched) Green else Muted
                                    )
                                    Text(
                                        listOf(node.book, node.unit, node.chapter)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" → ")
                                            .ifBlank { "待指定教材位置" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Muted
                                    )
                                    Text(
                                        node.matchMethod,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (node.matched) Green else Muted
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                DetailCard("题目") {
                    Text(item.questionText.ifBlank { "尚未识别题目文字，请编辑补充。" })
                }
            }
            item {
                DetailCard("答案与解析") {
                    Text(
                        item.answerText.ifBlank { "尚未生成答案。" },
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.explanation.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(item.explanation)
                    }
                }
            }
            if (
                item.originalAnswerText.isNotBlank() ||
                item.originalAnswerImagePath.isNotBlank() ||
                item.errorCause.isNotBlank() ||
                item.errorCauseState == ErrorCauseState.UNSUPPLEMENTED.name
            ) {
                item {
                    DetailCard("原作答与错因") {
                        if (item.originalAnswerText.isNotBlank()) {
                            Text("原始作答：${item.originalAnswerText}")
                        }
                        if (item.originalAnswerImagePath.isNotBlank()) {
                            ImagePanel(
                                item.originalAnswerImagePath,
                                Modifier.fillMaxWidth().height(180.dp)
                            )
                        }
                        if (item.errorSubpart.isNotBlank()) {
                            Text("错误位置：${item.errorSubpart}", color = Amber)
                        }
                        Text(
                            errorCauseLabel(item),
                            color = if (item.errorCauseState == ErrorCauseState.UNSUPPLEMENTED.name) Muted else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            item {
                DetailCard("复习状态") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuestionStatus.entries.forEach { status ->
                            FilterChip(
                                selected = item.status == status.name,
                                onClick = { viewModel.setStatus(item.id, status) },
                                label = { Text(status.label) }
                            )
                        }
                    }
                    if (item.retakeFailures > 0) {
                        Text("累计重考未通过 ${item.retakeFailures} 次", color = MaterialTheme.colorScheme.error)
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        AnalysisPill(item.analysisState)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { viewModel.rerunAnalysis(item.id) }) {
                            Icon(Icons.Outlined.AutoAwesome, null)
                            Text(" AI 重新分析")
                        }
                    }
                    if (item.analysisState == AnalysisState.FAILED.name && item.analysisError.isNotBlank()) {
                        Text(item.analysisError, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Spacer(Modifier.height(26.dp)) }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除错题") },
            text = { Text("将删除题目图片、原作答证据、分析结果和关联记录，无法恢复。") },
            confirmButton = {
                Button(onClick = {
                    showDelete = false
                    viewModel.deleteQuestion(questionId)
                    onBack()
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
