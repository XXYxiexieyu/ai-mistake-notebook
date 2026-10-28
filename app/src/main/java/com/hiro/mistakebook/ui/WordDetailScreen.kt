package com.hiro.mistakebook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单词详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
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
                            ImagePanel(item.sourceImagePath, Modifier.fillMaxSize())
                        }
                        Text(
                            "点击图片可全屏放大查看",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                }
            }
        }
    }

    if (showSourceImage && word?.sourceImagePath?.isNotBlank() == true) {
        FullScreenWordImage(
            path = word!!.sourceImagePath,
            onDismiss = { showSourceImage = false }
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

@Composable
private fun FullScreenWordImage(path: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                }
        ) {
            ImagePanel(
                path,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                ContentScale.Fit
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(
                        Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(50)
                    )
                ) {
                    Icon(Icons.Outlined.Close, "关闭图片", tint = Color.White)
                }
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.background(
                        Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(20.dp)
                    )
                ) {
                    Text("重置缩放", color = Color.White)
                }
            }
            Text(
                "双指缩放 · 拖动查看",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
