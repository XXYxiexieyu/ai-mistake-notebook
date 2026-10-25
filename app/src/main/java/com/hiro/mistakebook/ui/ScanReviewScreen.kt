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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.QuestionEntity
import com.hiro.mistakebook.ui.theme.Amber
import com.hiro.mistakebook.ui.theme.Muted
import com.hiro.mistakebook.ui.theme.Red

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    viewModel: MainViewModel,
    scanId: Long,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val pendingFlow = remember(scanId) { viewModel.observePendingScanQuestions(scanId) }
    val pending by pendingFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 错题审查") },
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
                Text(
                    if (pending.isEmpty()) {
                        "本轮候选错题已审查完成。"
                    } else {
                        "还有 ${pending.size} 道候选题。请根据作答与答案差异、异色笔订正或扣分依据确认后再加入错题本。"
                    },
                    color = if (pending.isEmpty()) Muted else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            items(pending, key = { it.id }) { question ->
                ScanReviewCard(
                    question = question,
                    onApprove = { viewModel.approveScanCandidate(question.id) },
                    onReject = { viewModel.deleteQuestion(question.id) }
                )
            }
            item {
                if (pending.isNotEmpty()) {
                    Button(
                        onClick = {
                            viewModel.approveAllScanCandidates(scanId)
                            onFinished()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Check, null)
                        Text(" 全部确认加入错题本")
                    }
                } else {
                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                        Text("返回错题本")
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ScanReviewCard(
    question: QuestionEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            ImagePanel(question.imagePath, Modifier.fillMaxWidth().height(230.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${question.subject} · ${question.questionType.ifBlank { "未分类" }}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(question.evidenceType.ifBlank { "AI 判定错题" }, color = Amber)
            }
            question.questionText.takeIf { it.isNotBlank() }?.let {
                MathText(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            }
            if (question.originalAnswerText.isNotBlank() || question.answerText.isNotBlank()) {
                MathText(
                    "作答：${question.originalAnswerText.ifBlank { "未识别" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                MathText(
                    "正确答案：${question.answerText.ifBlank { "未识别" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Red
                )
            }
            if (question.wrongEvidence.isNotBlank()) {
                MathText(
                    "判错依据：${question.wrongEvidence}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Check, null)
                    Text(" 确认错题")
                }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.DeleteOutline, null)
                    Text(" 不是错题")
                }
            }
        }
    }
}
