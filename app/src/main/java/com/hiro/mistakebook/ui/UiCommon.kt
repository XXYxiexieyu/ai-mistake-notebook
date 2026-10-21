package com.hiro.mistakebook.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.hiro.mistakebook.data.AnalysisState
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.KnowledgeNodeRef
import com.hiro.mistakebook.data.QuestionEntity
import com.hiro.mistakebook.data.QuestionStatus
import com.hiro.mistakebook.ui.theme.Amber
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import com.hiro.mistakebook.ui.theme.Red
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(millis: Long, includeTime: Boolean = false): String {
    val pattern = if (includeTime) "yyyy年M月d日 HH:mm" else "yyyy年M月d日"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(millis))
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
}

fun knowledgePoints(json: String): List<String> = runCatching {
    Gson().fromJson(json, Array<String>::class.java).toList()
}.getOrDefault(emptyList())

fun knowledgeNodeRefs(json: String): List<KnowledgeNodeRef> = runCatching {
    Gson().fromJson(json, Array<KnowledgeNodeRef>::class.java).toList()
}.getOrDefault(emptyList())

@Composable
fun ImagePanel(
    relativePath: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val file = remember(relativePath) {
        if (relativePath.isBlank()) null else ImageStore(context).resolve(relativePath)
    }
    if (file?.isFile == true) {
        AsyncImage(
            model = file,
            contentDescription = "题目图片",
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Image, null, tint = Muted)
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val label = runCatching { QuestionStatus.valueOf(status).label }.getOrDefault(status)
    val color = when (status) {
        QuestionStatus.PASSED.name -> Green
        QuestionStatus.REVIEWED.name -> Amber
        else -> Muted
    }
    Surface(
        color = color.copy(alpha = 0.13f),
        contentColor = color,
        shape = RoundedCornerShape(100)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun AnalysisPill(state: String) {
    val value = runCatching { AnalysisState.valueOf(state) }.getOrDefault(AnalysisState.MANUAL)
    val color = when (value) {
        AnalysisState.DONE, AnalysisState.MANUAL -> Green
        AnalysisState.FAILED -> Red
        AnalysisState.PROCESSING -> MaterialTheme.colorScheme.primary
        else -> Amber
    }
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(100)) {
        Text(
            value.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun SubjectChips(
    selected: String,
    includeAll: Boolean = true,
    onSelected: (String) -> Unit
) {
    val subjects = if (includeAll) listOf("全部", "语文", "数学", "英语", "物理", "化学", "生物") else listOf("语文", "数学", "英语", "物理", "化学", "生物")
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
    ) {
        items(subjects.size) { index ->
            val subject = subjects[index]
            FilterChip(
                selected = selected == subject,
                onClick = { onSelected(subject) },
                label = { Text(subject) }
            )
        }
    }
}

@Composable
fun QuestionCard(
    question: QuestionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ImagePanel(
                question.imagePath,
                modifier = Modifier.size(86.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${question.subject} · ${question.questionType.ifBlank { "未分类" }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Green,
                        fontWeight = FontWeight.SemiBold
                    )
                    StatusPill(question.status)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    question.questionText.ifBlank {
                        listOf(question.book, question.unit).filter { it.isNotBlank() }
                            .joinToString(" / ")
                            .ifBlank { "等待识别或手动填写题目内容" }
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        listOf(question.book, question.unit).filter { it.isNotBlank() }
                            .joinToString(" / ")
                            .ifBlank { "未归类" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                    if (question.errorCount > 1) {
                        Text(
                            "错 ${question.errorCount} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = Red
                        )
                    }
                }
                Text(
                    formatDate(question.captureTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
        }
    }
}

fun errorCauseLabel(question: QuestionEntity): String = when (
    runCatching { ErrorCauseState.valueOf(question.errorCauseState) }
        .getOrDefault(ErrorCauseState.UNSUPPLEMENTED)
) {
    ErrorCauseState.UNSUPPLEMENTED -> "未补充错因"
    ErrorCauseState.AI_CANDIDATE, ErrorCauseState.AI_ACCEPTED -> question.errorCause
    ErrorCauseState.CONFIRMED -> question.errorCause
}
