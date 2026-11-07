package com.hiro.mistakebook.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.WordLookupCodec
import com.hiro.mistakebook.data.WordEntryEntity
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    viewModel: MainViewModel,
    wordId: Long,
    onBack: () -> Unit
) {
    val word by remember(wordId) {
        viewModel.repository.observeWord(wordId)
    }.collectAsStateWithLifecycle(initialValue = null)
    var showSourceImage by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var savingEdit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val initialBox = remember(word?.sourceBoxJson) {
        WordLookupCodec.box(word?.sourceBoxJson.orEmpty())
    }
    val sourceMarks = remember(initialBox, word?.word) {
        if (initialBox.size >= 4) {
            listOf(PageWordMark(initialBox, "初次识别 · ${word?.word.orEmpty()}"))
        } else {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单词详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                },
                actions = {
                    word?.let { item ->
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Outlined.Edit, "编辑本地词条")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val item = word
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("单词不存在或已被永久删除。", color = Muted)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                WordTitleCard(item)
            }
            item {
                WordInfoCard("文中意义释义") {
                    Text(
                        item.meaningChinese.ifBlank { "暂无释义" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.contextSentence.isNotBlank()) {
                        Text(
                            "原文语境",
                            style = MaterialTheme.typography.labelSmall,
                            color = Green
                        )
                        Text(
                            item.contextSentence,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }
            }
            item {
                WordInfoCard("额外其他释义") {
                    WordFieldText(item.otherMeanings)
                }
            }
            item {
                WordInfoCard("固定搭配") {
                    WordFieldText(item.collocations)
                }
            }
            item {
                WordInfoCard("词性转换") {
                    WordFieldText(item.wordForms)
                }
            }
            item {
                WordInfoCard("造句") {
                    WordFieldText(item.exampleSentence)
                }
            }
            item {
                WordInfoCard("上传来源图片") {
                    if (item.sourceImagePath.isBlank()) {
                        Text("暂无来源图片", color = Muted)
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clickable { showSourceImage = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            MarkedPageImage(
                                path = item.sourceImagePath,
                                marks = sourceMarks,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            if (initialBox.size >= 4) {
                                "初次识别于第 ${item.sourcePageIndex + 1} 页，黄色框为单词位置；点击图片可全屏缩放"
                            } else {
                                "历史词条暂无初次识别坐标；点击图片可全屏查看"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                }
            }
        }
    }

    if (showSourceImage && word?.sourceImagePath?.isNotBlank() == true) {
        MarkedPageFullscreenDialog(
            path = word!!.sourceImagePath,
            marks = sourceMarks,
            onDismiss = { showSourceImage = false }
        )
    }

    if (showEdit && word != null) {
        WordEditDialog(
            word = word!!,
            saving = savingEdit,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                savingEdit = true
                viewModel.updateWordDetails(
                    word = updated,
                    onDone = {
                        savingEdit = false
                        showEdit = false
                        Toast.makeText(context, "本地词条已更新", Toast.LENGTH_SHORT).show()
                    },
                    onError = {
                        savingEdit = false
                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}

@Composable
private fun WordTitleCard(word: WordEntryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                word.word,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Green
            )
            Text(
                word.phonetic.ifBlank { "暂无音标" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                word.partOfSpeech.ifBlank { "暂无词性" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "累计出现 ${word.occurrences} 次",
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun WordInfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun WordFieldText(value: String) {
    val display = value.trim().ifBlank { "暂无" }
    Text(
        display,
        style = MaterialTheme.typography.bodyMedium,
        color = if (value.isBlank()) Muted else Color.Unspecified
    )
}
