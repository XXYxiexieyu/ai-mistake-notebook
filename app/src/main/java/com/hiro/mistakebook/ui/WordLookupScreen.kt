package com.hiro.mistakebook.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.ai.MarkedWordPayload
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.WordLookupCodec
import com.hiro.mistakebook.data.WordLookupStatus
import com.hiro.mistakebook.data.WordLookupTaskEntity
import com.hiro.mistakebook.ui.theme.Amber
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import com.hiro.mistakebook.ui.theme.Red
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordLookupScreen(
    viewModel: MainViewModel,
    taskId: Long?,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var resolvedTaskId by remember(taskId) { mutableLongStateOf(taskId ?: 0L) }
    var completing by remember { mutableStateOf(false) }
    val tasks by viewModel.wordLookupTasks.collectAsStateWithLifecycle()
    val task = tasks.firstOrNull { it.id == resolvedTaskId }
    val pagePaths = remember(task?.pagePathsJson) {
        WordLookupCodec.pagePaths(task?.pagePathsJson.orEmpty())
    }
    val recognizedWords = remember(task?.recognizedWordsJson) {
        WordLookupCodec.recognizedWords(task?.recognizedWordsJson.orEmpty())
    }
    val excludedIndices = remember(task?.excludedIndicesJson) {
        WordLookupCodec.excludedIndices(task?.excludedIndicesJson.orEmpty())
    }
    val indexedWords = remember(recognizedWords) {
        recognizedWords.mapIndexed { index, word -> IndexedMarkedWord(index, word) }
    }
    val status = remember(task?.status) {
        runCatching { WordLookupStatus.valueOf(task?.status.orEmpty()) }
            .getOrDefault(WordLookupStatus.READY)
    }
    val includedCount = recognizedWords.indices.count { it !in excludedIndices }
    val canAddImages = status in setOf(WordLookupStatus.READY, WordLookupStatus.FAILED)
    val canReview = status == WordLookupStatus.REVIEW

    LaunchedEffect(taskId) {
        viewModel.ensureWordLookupTask(
            requestedId = taskId,
            onReady = { id -> resolvedTaskId = id },
            onError = { scope.launch { snackbar.showSnackbar(it) } }
        )
    }

    fun importUris(uris: List<Uri>, onComplete: () -> Unit = {}) {
        if (resolvedTaskId <= 0 || uris.isEmpty()) {
            onComplete()
            return
        }
        viewModel.importWordLookupImages(
            taskId = resolvedTaskId,
            uris = uris,
            onImported = {},
            onDone = onComplete,
            onError = {
                onComplete()
                scope.launch { snackbar.showSnackbar(it) }
            }
        )
    }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        importUris(uris.take((5 - pagePaths.size).coerceAtLeast(0)))
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        val captured = cameraFile
        if (success && uri != null) {
            importUris(listOf(uri)) { captured?.delete() }
        } else {
            captured?.delete()
        }
        cameraUri = null
        cameraFile = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("英语阅读查词")
                        Text(
                            status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor(status)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LookupTaskSummary(
                    task = task,
                    status = status,
                    pageCount = pagePaths.size,
                    recognizedCount = recognizedWords.size,
                    includedCount = includedCount
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "只识别手写圈画、划线单词；识别结果会直接在原图上标号，确认前可逐词排除。",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val file = ImageStore(context).newCaptureFile()
                                        cameraFile = file
                                        cameraUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.files",
                                            file
                                        )
                                        camera.launch(cameraUri!!)
                                    }.onFailure {
                                        scope.launch { snackbar.showSnackbar("无法打开相机：${it.message}") }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = canAddImages && pagePaths.size < 5 && resolvedTaskId > 0
                            ) {
                                Icon(Icons.Outlined.CameraAlt, null)
                                Text(" 拍摄")
                            }
                            OutlinedButton(
                                onClick = {
                                    gallery.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = canAddImages && pagePaths.size < 5 && resolvedTaskId > 0
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, null)
                                Text(" 相册")
                            }
                        }
                        if (status == WordLookupStatus.IMPORTING) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(" 正在保存上传图片，可退出后继续查看")
                            }
                        }
                    }
                }
            }
            if (pagePaths.isNotEmpty()) {
                item {
                    Text(
                        "上传图片记录（${pagePaths.size}/5）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                itemsIndexed(pagePaths, key = { _, path -> path }) { pageIndex, path ->
                    WordLookupImageCard(
                        pageIndex = pageIndex,
                        path = path,
                        words = indexedWords.filter { it.word.pageIndex == pageIndex },
                        excludedIndices = excludedIndices,
                        reviewable = canReview,
                        removable = canAddImages,
                        onToggleWord = { index, accepted ->
                            viewModel.setWordLookupWordExcluded(
                                taskId = resolvedTaskId,
                                index = index,
                                excluded = !accepted
                            )
                        },
                        onDelete = { viewModel.removeWordLookupPage(resolvedTaskId, path) }
                    )
                }
            }
            task?.error?.takeIf { it.isNotBlank() }?.let { message ->
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            item {
                when (status) {
                    WordLookupStatus.REVIEW -> {
                        Button(
                            onClick = {
                                if (includedCount == 0) {
                                    scope.launch { snackbar.showSnackbar("请至少保留一个单词") }
                                    return@Button
                                }
                                completing = true
                                viewModel.completeWordLookupReview(
                                    taskId = resolvedTaskId,
                                    onDone = { count ->
                                        completing = false
                                        scope.launch {
                                            snackbar.showSnackbar("已加入或更新 $count 个单词")
                                            delay(450)
                                            onFinished()
                                        }
                                    },
                                    onError = {
                                        completing = false
                                        scope.launch { snackbar.showSnackbar(it) }
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !completing && includedCount > 0
                        ) {
                            if (completing) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(" 正在加入单词本…")
                            } else {
                                Text("确认并加入单词本（保留 $includedCount / ${recognizedWords.size}）")
                            }
                        }
                    }
                    WordLookupStatus.COMPLETED -> {
                        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                            Text("查看单词本（已加入 ${task?.wordCount ?: 0} 个）")
                        }
                    }
                    WordLookupStatus.ANALYZING, WordLookupStatus.IMPORTING -> {
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                if (status == WordLookupStatus.ANALYZING) {
                                    " AI 正在识别标记单词…"
                                } else {
                                    " 正在保存上传图片…"
                                }
                            )
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                viewModel.lookupEnglishWords(
                                    taskId = resolvedTaskId,
                                    onDone = { count ->
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                "识别到 $count 个标记单词，请在图片记录中复核"
                                            )
                                        }
                                    },
                                    onError = { scope.launch { snackbar.showSnackbar(it) } }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pagePaths.isNotEmpty() && resolvedTaskId > 0
                        ) {
                            Text(
                                if (status == WordLookupStatus.FAILED) {
                                    "重新识别圈画/划线单词"
                                } else {
                                    "识别圈画/划线单词"
                                }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

private data class IndexedMarkedWord(
    val index: Int,
    val word: MarkedWordPayload
)

private data class ImageDimension(
    val width: Int,
    val height: Int
) {
    val aspectRatio: Float
        get() = if (width <= 0 || height <= 0) 0.707f else width.toFloat() / height
}

@Composable
private fun LookupTaskSummary(
    task: WordLookupTaskEntity?,
    status: WordLookupStatus,
    pageCount: Int,
    recognizedCount: Int,
    includedCount: Int
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("查词任务进度", fontWeight = FontWeight.SemiBold)
                Text(status.label, color = statusColor(status), fontWeight = FontWeight.SemiBold)
            }
            Text(
                "已上传 $pageCount 页 · 识别 $recognizedCount 个单词" +
                    if (status == WordLookupStatus.REVIEW) " · 当前保留 $includedCount 个" else "",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { (task?.progress ?: 0).coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            if (task == null) {
                Text("正在读取本地查词任务…", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordLookupImageCard(
    pageIndex: Int,
    path: String,
    words: List<IndexedMarkedWord>,
    excludedIndices: Set<Int>,
    reviewable: Boolean,
    removable: Boolean,
    onToggleWord: (index: Int, accepted: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dimensions = remember(path) {
        val file = ImageStore(context).resolve(path)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        ImageDimension(options.outWidth, options.outHeight)
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("上传记录 ${pageIndex + 1}", fontWeight = FontWeight.SemiBold)
                    Text(
                        words.size.toString() + " 个标记单词",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (removable) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, "删除上传图片")
                    }
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(dimensions.aspectRatio)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                ImagePanel(
                    path,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                words.forEach { match ->
                    val box = match.word.box
                    if (box.size < 4) return@forEach
                    val leftRatio = box[0].coerceIn(0f, 1000f) / 1000f
                    val topRatio = box[1].coerceIn(0f, 1000f) / 1000f
                    val rightRatio = box[2].coerceIn(0f, 1000f) / 1000f
                    val bottomRatio = box[3].coerceIn(0f, 1000f) / 1000f
                    if (rightRatio <= leftRatio || bottomRatio <= topRatio) return@forEach
                    val color = if (match.index in excludedIndices) Muted else Amber
                    val left = maxWidth * leftRatio
                    val top = maxHeight * topRatio
                    val markWidth = (maxWidth * (rightRatio - leftRatio)).coerceAtLeast(18.dp)
                    val markHeight = (maxHeight * (bottomRatio - topRatio)).coerceAtLeast(18.dp)
                    Box(
                        modifier = Modifier
                            .offset(x = left, y = top)
                            .size(markWidth, markHeight)
                            .background(color.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                            .border(2.dp, color, RoundedCornerShape(3.dp))
                    )
                    Text(
                        "${match.index + 1}. ${match.word.word}",
                        modifier = Modifier
                            .offset(x = left, y = if (top > 24.dp) top - 22.dp else 0.dp)
                            .background(color, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            if (words.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    words.forEach { match ->
                        FilterChip(
                            selected = match.index !in excludedIndices,
                            onClick = {
                                if (reviewable) {
                                    onToggleWord(match.index, match.index in excludedIndices)
                                }
                            },
                            enabled = reviewable,
                            label = {
                                Text(
                                    "${match.index + 1}. ${match.word.word}" +
                                        if (match.word.markType.isNotBlank()) " · ${match.word.markType}" else ""
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: WordLookupStatus): Color = when (status) {
    WordLookupStatus.COMPLETED -> Green
    WordLookupStatus.FAILED -> Red
    WordLookupStatus.REVIEW -> Amber
    WordLookupStatus.ANALYZING, WordLookupStatus.IMPORTING -> MaterialTheme.colorScheme.primary
    WordLookupStatus.READY -> Muted
}
