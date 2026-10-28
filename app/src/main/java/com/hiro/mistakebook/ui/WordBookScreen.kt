package com.hiro.mistakebook.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.WordEntryEntity
import com.hiro.mistakebook.data.WordCsvExporter
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WordBookScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val csv = "\uFEFF" + WordCsvExporter.toCsv(words)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("无法打开导出文件")
                }
            }.onSuccess {
                Toast.makeText(context, "已导出 ${words.size} 个单词", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "单词导出失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("单词本", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "共 ${words.size} 个单词，来自英语阅读和完形标记",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (words.isEmpty()) {
                            Toast.makeText(context, "单词本为空，暂无可导出内容", Toast.LENGTH_SHORT).show()
                        } else {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())
                            exportLauncher.launch("AI错题本_单词_$stamp.csv")
                        }
                    }
                ) {
                    Icon(Icons.Outlined.FileDownload, null)
                    Text(" 导出")
                }
            }
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    word.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Green
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, "删除单词")
                }
            }
            val subtitle = listOf(word.phonetic, word.partOfSpeech).filter { it.isNotBlank() }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle.joinToString("  "),
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                word.meaningChinese.ifBlank { "暂无释义" },
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (word.contextSentence.isNotBlank()) {
                Text(
                    word.contextSentence,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
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
