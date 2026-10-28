package com.hiro.mistakebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiro.mistakebook.data.WordEntryEntity

@Composable
fun WordEditDialog(
    word: WordEntryEntity,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (WordEntryEntity) -> Unit
) {
    var wordText by remember(word.id) { mutableStateOf(word.word) }
    var phonetic by remember(word.id) { mutableStateOf(word.phonetic) }
    var partOfSpeech by remember(word.id) { mutableStateOf(word.partOfSpeech) }
    var meaning by remember(word.id) { mutableStateOf(word.meaningChinese) }
    var contextSentence by remember(word.id) { mutableStateOf(word.contextSentence) }
    var otherMeanings by remember(word.id) { mutableStateOf(word.otherMeanings) }
    var collocations by remember(word.id) { mutableStateOf(word.collocations) }
    var wordForms by remember(word.id) { mutableStateOf(word.wordForms) }
    var exampleSentence by remember(word.id) { mutableStateOf(word.exampleSentence) }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(14.dp).imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 520.dp, max = 760.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("编辑本地词条", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "AI 识别结果已直接保存。这里的修改会覆盖当前本地数据库字段。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = wordText,
                            onValueChange = { wordText = it },
                            label = { Text("单词") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = phonetic,
                            onValueChange = { phonetic = it },
                            label = { Text("音标") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = partOfSpeech,
                            onValueChange = { partOfSpeech = it },
                            label = { Text("词性") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = meaning,
                            onValueChange = { meaning = it },
                            label = { Text("文中意义释义") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = otherMeanings,
                            onValueChange = { otherMeanings = it },
                            label = { Text("额外其他释义（每行一条）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = collocations,
                            onValueChange = { collocations = it },
                            label = { Text("固定搭配（每行一条）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = wordForms,
                            onValueChange = { wordForms = it },
                            label = { Text("词性转换（每行一条）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = exampleSentence,
                            onValueChange = { exampleSentence = it },
                            label = { Text("造句") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = contextSentence,
                            onValueChange = { contextSentence = it },
                            label = { Text("原文语境") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !saving) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            onSave(
                                word.copy(
                                    word = wordText,
                                    phonetic = phonetic,
                                    partOfSpeech = partOfSpeech,
                                    meaningChinese = meaning,
                                    otherMeanings = otherMeanings,
                                    collocations = collocations,
                                    wordForms = wordForms,
                                    exampleSentence = exampleSentence,
                                    contextSentence = contextSentence
                                )
                            )
                        },
                        enabled = !saving && wordText.isNotBlank()
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 6.dp).size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(if (saving) "保存中…" else "保存本地修改")
                    }
                }
                }
            }
        }
    }
}
