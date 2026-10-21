package com.hiro.mistakebook.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.hiro.mistakebook.data.QuestionDraft
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.ui.theme.Muted
import kotlinx.coroutines.launch
import java.util.Calendar

private data class BatchSource(
    val sourcePath: String,
    val crops: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sources = remember { mutableStateListOf<BatchSource>() }
    var subject by remember { mutableStateOf("数学") }
    var captureTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var autoAnalyze by remember { mutableStateOf(true) }
    var cropSourceIndex by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var splittingIndex by remember { mutableStateOf<Int?>(null) }
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
                        SubjectChips(selected = subject, includeAll = false, onSelected = { subject = it })
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
                    enabled = !loading && sources.size < 50
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null)
                    Text(if (loading) " 正在导入…" else " 从相册批量选择（最多 50 张）")
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
                            IconButton(onClick = { sources.removeAt(index) }) {
                                Icon(Icons.Outlined.DeleteOutline, "移除原图")
                            }
                        }
                        ImagePanel(item.sourcePath, Modifier.fillMaxWidth().height(180.dp))
                        if (item.crops.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item.crops.take(4).forEach { crop ->
                                    ImagePanel(crop, Modifier.height(70.dp).fillMaxWidth(0.22f))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { cropSourceIndex = index },
                                modifier = Modifier.weight(1f),
                                enabled = item.crops.size < 10 && cropCount < 50
                            ) {
                                Icon(Icons.Outlined.ContentCut, null)
                                Text(" 手动框选")
                            }
                            OutlinedButton(
                                onClick = {
                                    splittingIndex = index
                                    viewModel.autoSplit(
                                        item.sourcePath,
                                        onDone = { paths ->
                                            splittingIndex = null
                                            val remaining = (50 - cropCount).coerceAtLeast(0)
                                            if (paths.isNotEmpty() && remaining > 0) {
                                                sources[index] = sources[index].copy(
                                                    crops = sources[index].crops + paths.take(remaining)
                                                )
                                            } else {
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
                                enabled = apiConfigured && cropCount < 50 && splittingIndex == null
                            ) {
                                Text(if (splittingIndex == index) "分题中…" else "AI 自动分题")
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
                                    subject = subject,
                                    errorCauseState = ErrorCauseState.AI_ACCEPTED,
                                    questionImage = crop,
                                    sourceImage = source.sourcePath,
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
                    enabled = cropCount > 0 && !saving
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
                            sources[index] = source.copy(crops = source.crops + crop)
                        }
                    }
                }
            )
        }
    }
}
