package com.hiro.mistakebook.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.AutoSplitCropCandidate
import com.hiro.mistakebook.data.QuestionDraft
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.Subjects
import com.hiro.mistakebook.ui.theme.Muted
import com.hiro.mistakebook.ui.theme.Red
import kotlinx.coroutines.launch
import java.util.Calendar

private data class BatchSource(
    val sourcePath: String,
    val crops: List<AutoSplitCropCandidate> = emptyList(),
    val aiSplitDone: Boolean = false
)

private const val AI_SUBJECT = Subjects.AUTO

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPageScan: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sources = remember { mutableStateListOf<BatchSource>() }
    var subject by remember { mutableStateOf(AI_SUBJECT) }
    var captureTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var autoAnalyze by remember { mutableStateOf(true) }
    var cropSourceIndex by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var splittingIndex by remember { mutableStateOf<Int?>(null) }
    var bulkSplitting by remember { mutableStateOf(false) }
    var bulkProgress by remember { mutableStateOf(0 to 0) }
    val apiConfigured by viewModel.apiConfigured.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            loading = true
            viewModel.importImages(
                uris.take(50),
                "batch",
                onImported = { sources.add(BatchSource(it)) },
                onDone = { loading = false },
                onError = {
                    loading = false
                    scope.launch { snackbar.showSnackbar(it) }
                }
            )
        }
    }

    val cropCount = sources.sumOf { it.crops.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("批量导入与多题框选") },
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("导入设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        RecognitionSubjectChips(
                            selected = subject,
                            onSelected = { subject = it }
                        )
                        OutlinedButton(
                            onClick = onOpenPageScan,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null)
                            Text(" 整页扫描 / 跨页大题识别")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("拍摄日期", fontWeight = FontWeight.SemiBold)
                                Text(formatDate(captureTime), color = Muted)
                            }
                            TextButton(onClick = {
                                val calendar = Calendar.getInstance().apply { timeInMillis = captureTime }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        captureTime = Calendar.getInstance().apply {
                                            set(year, month, day, 12, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) { Text("选择日期") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("保存后 AI 分析与知识网匹配", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "自动识别知识点并对应教材知识网；无网络时先保存等待。",
                                    color = Muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = autoAnalyze, onCheckedChange = { autoAnalyze = it })
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && !bulkSplitting && sources.size < 50
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null)
                    Text(if (loading) " 正在导入…" else " 从相册批量选择（最多 50 张）")
                }
            }
            item {
                val pendingIndexes = sources.indices.filter { !sources[it].aiSplitDone }
                OutlinedButton(
                    onClick = {
                        if (pendingIndexes.isEmpty()) return@OutlinedButton
                        bulkSplitting = true
                        bulkProgress = 0 to pendingIndexes.size
                        val remaining = (50 - cropCount).coerceAtLeast(0)
                        viewModel.autoSplitAll(
                            sourcePaths = pendingIndexes.map { sources[it].sourcePath },
                            subject = subject,
                            maxTotal = remaining,
                            onPageDone = { relativeIndex, candidates ->
                                pendingIndexes.getOrNull(relativeIndex)?.let { sourceIndex ->
                                    sources.getOrNull(sourceIndex)?.let { current ->
                                        val stillAvailable = (50 - cropCount).coerceAtLeast(0)
                                        sources[sourceIndex] = current.copy(
                                            crops = current.crops + candidates.take(stillAvailable),
                                            aiSplitDone = true
                                        )
                                    }
                                }
                            },
                            onProgress = { done, total ->
                                bulkProgress = done to total
                            },
                            onDone = { generated, failures ->
                                bulkSplitting = false
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (failures > 0) {
                                            "AI 分题完成：新增 $generated 题，$failures 张图片失败"
                                        } else {
                                            "AI 分题完成：新增 $generated 题"
                                        }
                                    )
                                }
                            },
                            onError = {
                                bulkSplitting = false
                                scope.launch { snackbar.showSnackbar(it) }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = apiConfigured && pendingIndexes.isNotEmpty() &&
                        cropCount < 50 && !bulkSplitting && splittingIndex == null && !loading
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Text(
                        when {
                            bulkSplitting -> " 正在分题 ${bulkProgress.first}/${bulkProgress.second}"
                            pendingIndexes.isEmpty() && sources.isNotEmpty() -> " 全部图片已完成 AI 分题"
                            else -> " 一键全部 AI 分题"
                        }
                    )
                }
                if (bulkSplitting) {
                    LinearProgressIndicator(
                        progress = {
                            bulkProgress.first.toFloat() / bulkProgress.second.coerceAtLeast(1)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (sources.isEmpty()) {
                item {
                    Text(
                        "每张照片必须至少框选一道题。同一张照片可以反复点击“框选一题”，最多生成 10 道错题。",
                        color = Muted
                    )
                }
            }
            itemsIndexed(sources, key = { _, item -> item.sourcePath }) { index, item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("原图 ${index + 1}", fontWeight = FontWeight.SemiBold)
                            IconButton(
                                onClick = {
                                    val removed = sources.removeAt(index)
                                    viewModel.deleteStoredImage(removed.sourcePath)
                                    removed.crops.forEach {
                                        viewModel.deleteStoredImage(it.imagePath)
                                    }
                                },
                                enabled = !bulkSplitting && splittingIndex == null
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, "移除原图")
                            }
                        }
                        ImagePanel(item.sourcePath, Modifier.fillMaxWidth().height(180.dp))
                        if (item.crops.isNotEmpty()) {
                            Text(
                                "分题结果预览（${item.crops.size} 题）",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item.crops.forEachIndexed { cropIndex, crop ->
                                    Column(
                                        modifier = Modifier.width(112.dp),
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                    ) {
                                        Box {
                                            ImagePanel(crop.imagePath, Modifier.width(112.dp).height(92.dp))
                                            IconButton(
                                                onClick = {
                                                    val current = sources.getOrNull(index)
                                                    if (current != null) {
                                                        val remainingCrops = current.crops.filterIndexed { i, _ ->
                                                            i != cropIndex
                                                        }
                                                        sources[index] = current.copy(
                                                            crops = remainingCrops,
                                                            aiSplitDone = current.aiSplitDone &&
                                                                remainingCrops.isNotEmpty()
                                                        )
                                                    }
                                                    viewModel.deleteStoredImage(crop.imagePath)
                                                },
                                                enabled = !bulkSplitting && splittingIndex == null,
                                                modifier = Modifier
                                                    .align(androidx.compose.ui.Alignment.TopEnd)
                                                    .size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.DeleteOutline,
                                                    "删除第 ${cropIndex + 1} 题",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        Text(
                                            "第 ${cropIndex + 1} 题",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            crop.evidenceType,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Text(
                                "AI 错题审查：以下题目均检测到明确错误依据。请删除误识别项后再保存。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted
                            )
                            item.crops.forEachIndexed { cropIndex, crop ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "第 ${cropIndex + 1} 题 · ${crop.evidenceType}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (crop.selectedAnswer.isNotBlank() || crop.correctAnswer.isNotBlank()) {
                                        Text(
                                            "作答 ${crop.selectedAnswer.ifBlank { "未识别" }} / 正确答案 ${crop.correctAnswer.ifBlank { "未识别" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (
                                                crop.selectedAnswer.isNotBlank() &&
                                                crop.correctAnswer.isNotBlank() &&
                                                crop.selectedAnswer.trim() != crop.correctAnswer.trim()
                                            ) {
                                                Red
                                            } else {
                                                Muted
                                            }
                                        )
                                    }
                                    if (crop.wrongEvidence.isNotBlank()) {
                                        Text(
                                            crop.wrongEvidence,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Muted
                                        )
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { cropSourceIndex = index },
                                modifier = Modifier.weight(1f),
                                enabled = item.crops.size < 10 && cropCount < 50 &&
                                    !bulkSplitting && splittingIndex == null
                            ) {
                                Icon(Icons.Outlined.ContentCut, null)
                                Text(" 手动框选")
                            }
                            OutlinedButton(
                                onClick = {
                                    splittingIndex = index
                                    viewModel.autoSplit(
                                        item.sourcePath,
                                        subject,
                                        onDone = { candidates ->
                                            splittingIndex = null
                                            val remaining = (50 - cropCount).coerceAtLeast(0)
                                            if (candidates.isNotEmpty() && remaining > 0) {
                                                sources[index] = sources[index].copy(
                                                    crops = sources[index].crops + candidates.take(remaining),
                                                    aiSplitDone = true
                                                )
                                            } else {
                                                sources[index] = sources[index].copy(aiSplitDone = true)
                                                scope.launch { snackbar.showSnackbar("未生成可用的题目区域") }
                                            }
                                        },
                                        onError = {
                                            splittingIndex = null
                                            scope.launch { snackbar.showSnackbar(it) }
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = apiConfigured && cropCount < 50 && splittingIndex == null &&
                                    !bulkSplitting && !item.aiSplitDone
                            ) {
                                Text(
                                    when {
                                        splittingIndex == index -> "分题中…"
                                        item.aiSplitDone -> "已完成 AI 分题"
                                        else -> "AI 自动分题"
                                    }
                                )
                            }
                        }
                        Text("已框 ${item.crops.size}/10；整批最多 50 题", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Text(
                    "已选原图 ${sources.size} 张，已框选 $cropCount 道题",
                    color = if (cropCount > 0) MaterialTheme.colorScheme.primary else Muted
                )
            }
            item {
                Button(
                    onClick = {
                        val drafts = sources.flatMap { source ->
                            source.crops.map { crop ->
                                QuestionDraft(
                                    subject = when {
                                        subject != AI_SUBJECT -> subject
                                        crop.subject in Subjects.all -> crop.subject
                                        else -> "未识别"
                                    },
                                    errorCauseState = ErrorCauseState.AI_ACCEPTED,
                                    questionImage = crop.imagePath,
                                    sourceImage = source.sourcePath,
                                    wrongEvidence = crop.wrongEvidence,
                                    evidenceType = crop.evidenceType,
                                    captureTime = captureTime,
                                    autoAnalyze = autoAnalyze
                                )
                            }
                        }
                        if (drafts.isEmpty()) {
                            scope.launch { snackbar.showSnackbar("请先框选至少一道题") }
                        } else {
                            saving = true
                            viewModel.saveBatch(
                                drafts,
                                onDone = {
                                    saving = false
                                    onFinished()
                                },
                                onError = {
                                    saving = false
                                    scope.launch { snackbar.showSnackbar(it) }
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = cropCount > 0 && !saving && !bulkSplitting && splittingIndex == null
                ) {
                    Text(if (saving) "正在保存…" else "保存全部 $cropCount 道题")
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    cropSourceIndex?.let { index ->
        val source = sources.getOrNull(index)
        if (source != null) {
            CropDialog(
                imagePath = source.sourcePath,
                onDismiss = { cropSourceIndex = null },
                onCropped = { bitmap ->
                    cropSourceIndex = null
                    viewModel.saveCrop(bitmap, "batch_question") { crop ->
                        if (cropCount < 50) {
                            sources[index] = source.copy(
                                crops = source.crops + AutoSplitCropCandidate(
                                    imagePath = crop,
                                    sourcePath = source.sourcePath,
                                    isWrong = true,
                                    subject = if (subject == AI_SUBJECT) "" else subject,
                                    evidenceType = "手动框选",
                                    wrongEvidence = "用户手动指定为错题",
                                    selectedAnswer = "",
                                    correctAnswer = "",
                                    questionText = "",
                                    confidence = null
                                )
                            )
                        }
                    }
                }
            )
        }
    }
}

