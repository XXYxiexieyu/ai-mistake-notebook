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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.hiro.mistakebook.data.WordEntryEntity
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted

@Composable
fun WordBookScreen(viewModel: MainViewModel) {
    val words by viewModel.words.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(words, query) {
        if (query.isBlank()) {
            words
        } else {
            val key = query.trim().lowercase()
            words.filter {
                it.word.lowercase().contains(key) ||
                    it.lemma.lowercase().contains(key) ||
                    it.meaningChinese.contains(query.trim())
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("单词本", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "来自英语阅读和完形篇目中的圈画、划线单词",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("搜索单词或中文释义") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (filtered.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (words.isEmpty()) "单词本还是空的" else "没有匹配的单词",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (words.isEmpty()) {
                                "点击底部中央加号，选择英语阅读查词。"
                            } else {
                                "尝试更换搜索关键词。"
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { word ->
                WordCard(word, onDelete = { viewModel.deleteWord(word.id) })
            }
        }
    }
}

@Composable
private fun WordCard(word: WordEntryEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (word.sourceImagePath.isNotBlank()) {
                ImagePanel(word.sourceImagePath, Modifier.size(82.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        word.word,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Green
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, "删除单词")
                    }
                }
                val subtitle = listOf(word.phonetic, word.partOfSpeech).filter { it.isNotBlank() }
                if (subtitle.isNotEmpty()) {
                    Text(subtitle.joinToString("  "), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text(word.meaningChinese.ifBlank { "暂无释义" }, fontWeight = FontWeight.Medium)
                if (word.contextSentence.isNotBlank()) {
                    Text(
                        word.contextSentence,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
                Text(
                    "${word.markType.ifBlank { "阅读标记" }} · 累计 ${word.occurrences} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
        }
    }
}
