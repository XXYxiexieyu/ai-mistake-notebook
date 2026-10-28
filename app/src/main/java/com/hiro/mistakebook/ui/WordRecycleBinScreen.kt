package com.hiro.mistakebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.hiro.mistakebook.ui.theme.Red

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordRecycleBinScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val deletedWords by viewModel.deletedWords.collectAsStateWithLifecycle()
    var clearConfirm by remember { mutableStateOf(false) }
    var permanentDeleteTarget by remember { mutableStateOf<WordEntryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("单词回收站")
                        Text(
                            "${deletedWords.size} 个已删除单词",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (deletedWords.isNotEmpty()) {
                        TextButton(onClick = { clearConfirm = true }) {
                            Text("清空", color = Red)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Text(
                    "删除的单词会暂存在这里。恢复后重新出现在单词本，所有本地释义和来源图片都会保留。",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (deletedWords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text("回收站为空", modifier = Modifier.padding(20.dp), color = Muted)
                    }
                }
            } else {
                items(deletedWords, key = { it.id }) { word ->
                    DeletedWordCard(
                        word = word,
                        onRestore = { viewModel.restoreWord(word.id) },
                        onDeleteForever = { permanentDeleteTarget = word }
                    )
                }
            }
        }
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("清空单词回收站") },
            text = { Text("将永久删除回收站内全部单词记录，无法恢复。来源图片仍可保留在查词记录中。") },
            confirmButton = {
                Button(onClick = {
                    clearConfirm = false
                    viewModel.clearWordRecycleBin()
                }) {
                    Text("永久清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) { Text("取消") }
            }
        )
    }
    permanentDeleteTarget?.let { word ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text("永久删除 ${word.word}") },
            text = { Text("该单词记录将无法恢复。") },
            confirmButton = {
                Button(onClick = {
                    permanentDeleteTarget = null
                    viewModel.permanentlyDeleteWord(word.id)
                }) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DeletedWordCard(
    word: WordEntryEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    word.word,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Green
                )
                Text(
                    word.meaningChinese.ifBlank { "暂无释义" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                word.deletedAt?.let {
                    Text(
                        "删除于 ${formatDate(it, includeTime = true)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                }
            }
            IconButton(onClick = onRestore, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Restore, "恢复单词", tint = Green)
            }
            IconButton(onClick = onDeleteForever, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.DeleteForever, "永久删除", tint = Red)
            }
        }
    }
}
