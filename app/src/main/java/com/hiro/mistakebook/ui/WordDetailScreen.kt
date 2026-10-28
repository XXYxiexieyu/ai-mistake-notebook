package com.hiro.mistakebook.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.WordLookupCodec
import com.hiro.mistakebook.data.WordEntryEntity
import com.hiro.mistakebook.ui.theme.Amber
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
                            WordMarkedSourceImage(
                                path = item.sourceImagePath,
                                box = initialBox,
                                word = item.word,
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
        FullScreenWordImage(
            path = word!!.sourceImagePath,
            box = initialBox,
            word = word!!.word,
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

@Composable
private fun WordMarkedSourceImage(
    path: String,
    box: List<Float>,
    word: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageSize = remember(path) {
        val file = ImageStore(context).resolve(path)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth to options.outHeight
    }
    val imageAspect = if (imageSize.first > 0 && imageSize.second > 0) {
        imageSize.first.toFloat() / imageSize.second
    } else {
        0.707f
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(14.dp))) {
        ImagePanel(path, Modifier.fillMaxSize(), ContentScale.Fit)
        if (box.size < 4) return@BoxWithConstraints
        val leftRatio = box[0].coerceIn(0f, 1000f) / 1000f
        val topRatio = box[1].coerceIn(0f, 1000f) / 1000f
        val rightRatio = box[2].coerceIn(0f, 1000f) / 1000f
        val bottomRatio = box[3].coerceIn(0f, 1000f) / 1000f
        if (rightRatio <= leftRatio || bottomRatio <= topRatio) return@BoxWithConstraints

        val containerAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val drawWidth = if (containerAspect > imageAspect) maxHeight * imageAspect else maxWidth
        val drawHeight = if (containerAspect > imageAspect) maxHeight else maxWidth / imageAspect
        val imageLeft = (maxWidth - drawWidth) / 2
        val imageTop = (maxHeight - drawHeight) / 2
        val markLeft = imageLeft + drawWidth * leftRatio
        val markTop = imageTop + drawHeight * topRatio
        val markWidth = (drawWidth * (rightRatio - leftRatio)).coerceAtLeast(18.dp)
        val markHeight = (drawHeight * (bottomRatio - topRatio)).coerceAtLeast(18.dp)

        Box(
            modifier = Modifier
                .offset(x = markLeft, y = markTop)
                .size(markWidth, markHeight)
                .background(Amber.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                .border(2.dp, Amber, RoundedCornerShape(3.dp))
        )
        Text(
            "初次识别 · $word",
            modifier = Modifier
                .offset(x = markLeft, y = if (markTop > 24.dp) markTop - 22.dp else 0.dp)
                .background(Amber, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun FullScreenWordImage(
    path: String,
    box: List<Float>,
    word: String,
    onDismiss: () -> Unit
) {
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
            WordMarkedSourceImage(
                path = path,
                box = box,
                word = word,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
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
