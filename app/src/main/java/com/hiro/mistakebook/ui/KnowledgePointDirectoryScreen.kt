package com.hiro.mistakebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgePointDirectoryScreen(
    viewModel: MainViewModel,
    target: KnowledgePointTarget,
    onBack: () -> Unit,
    onOpenQuestion: (Long) -> Unit
) {
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    val matches = remember(questions, target) {
        questions.filter { question ->
            knowledgePoints(question.knowledgePointsJson).contains(target.knowledgePoint) &&
                (target.book.isBlank() || question.book == target.book) &&
                (target.unit.isBlank() || question.unit == target.unit) &&
                (target.chapter.isBlank() || question.chapter == target.chapter)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识点错题目录") },
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
                    target.knowledgePoint,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Green
                )
                Text(
                    listOf(target.subject, target.book, target.unit, target.chapter)
                        .filter { it.isNotBlank() }
                        .joinToString(" → "),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "该知识点共收录 ${matches.size} 道错题",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (matches.isEmpty()) {
                item {
                    Text("该知识点目录暂无错题。", color = Muted)
                }
            } else {
                items(matches, key = { it.id }) { question ->
                    QuestionCard(question, onClick = { onOpenQuestion(question.id) })
                }
            }
        }
    }
}
