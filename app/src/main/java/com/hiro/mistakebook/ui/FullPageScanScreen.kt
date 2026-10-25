package com.hiro.mistakebook.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.AnalysisState
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.PageScanEntity
import com.hiro.mistakebook.data.Subjects
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import com.hiro.mistakebook.ui.theme.Red
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPageScanScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onViewResults: (Long) -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val apiConfigured by viewModel.apiConfigured.collectAsStateWithLifecycle()
    val sourcePaths: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    var subject by rememberSaveable { mutableStateOf(Subjects.AUTO) }
    var captureTime by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var scanId by rememberSaveable { mutableStateOf<Long?>(null) }
    var importing by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf("") }
    var showProgress by rememberSaveable { mutableStateOf(false) }

    var scan by remember { mutableStateOf<PageScanEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(scanId) {
        scan = null
        val id = scanId ?: return@LaunchedEffect
        viewModel.repository.observePageScan(id).collect { scan = it }
    }
    val scanBusy = scan?.status == AnalysisState.PENDING.name ||
        scan?.status == AnalysisState.PROCESSING.name

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            viewModel.importImages(
                uris.take((10 - sourcePaths.size).coerceAtLeast(0)),
                "page",
                onImported = {
                    sourcePaths += it
                    scanId = null
                    showProgress = false
                },
                onDone = { importing = false },
                onError = {
                    importing = false
                    scope.launch { snackbar.showSnackbar(it) }
                }
            )
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        val captured = cameraFile
        if (success && uri != null) {
            importing = true
            viewModel.importImage(uri, "page", onResult = {
                if (sourcePaths.size < 10) sourcePaths += it
                scanId = null
                showProgress = false
                importing = false
                captured?.delete()
            }, onError = {
                importing = false
                captured?.delete()
                scope.launch { snackbar.showSnackbar(it) }
            })
        } else {
            captured?.delete()
        }
        cameraUri = null
        cameraFile = null
    }

    fun pickDate() {
        val calendar = Calendar.getInstance().apply { timeInMillis = captureTime }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                captureTime = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                scanId = null
                showProgress = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("整页扫描错题") },
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
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text(
                            "AI 会重点检查蓝色、红色、紫色等彩笔在题目和答案附近留下的叉号、圈划、订正、扣分和补充步骤。普通下划线和高亮不会单独被判为错题。",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (sourcePaths.isEmpty()) {
                            Text("请拍下完整一页，并确保批改痕迹清晰可见。", color = Muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val file = ImageStore(context).newCaptureFile()
                                        cameraFile = file
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.files",
                                            file
                                        )
                                        cameraUri = uri
                                        camera.launch(uri)
                                    }.onFailure {
                                        scope.launch { snackbar.showSnackbar("无法打开相机：${it.message}") }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = sourcePaths.size < 10 && !importing && !scanBusy
                            ) {
                                Icon(Icons.Outlined.CameraAlt, null)
                                Text(if (sourcePaths.isEmpty()) " 拍摄第一页" else " 继续拍下一页")
                            }
                            OutlinedButton(
                                onClick = {
                                    val remaining = (10 - sourcePaths.size).coerceAtLeast(0)
                                    if (remaining > 0) {
                                        gallery.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = sourcePaths.size < 10 && !importing && !scanBusy
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, null)
                                Text(" 多页选择")
                            }
                        }
                        if (importing) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.height(20.dp))
                                Text(" 正在保存整页图片…")
                            }
                        }
                        Text(
                            "已添加 ${sourcePaths.size}/10 页。跨页大题会合并为一道错题。",
                            color = if (sourcePaths.isEmpty()) Muted else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (sourcePaths.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                sourcePaths.forEachIndexed { index, path ->
                                    PagePreviewEditor(
                                        path = path,
                                        index = index,
                                        pageCount = sourcePaths.size,
                                        onMoveUp = {
                                            if (index > 0) {
                                                val previous = sourcePaths[index - 1]
                                                sourcePaths[index - 1] = path
                                                sourcePaths[index] = previous
                                                scanId = null
                                            }
                                        },
                                        onMoveDown = {
                                            if (index < sourcePaths.lastIndex) {
                                                val next = sourcePaths[index + 1]
                                                sourcePaths[index + 1] = path
                                                sourcePaths[index] = next
                                                scanId = null
                                            }
                                        },
                                        onRemove = {
                                            sourcePaths.removeAt(index)
                                            scanId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text("识别设置", fontWeight = FontWeight.SemiBold)
                        RecognitionSubjectChips(
                            selected = subject,
                            onSelected = {
                                subject = it
                                scanId = null
                                showProgress = false
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column {
                                Text("拍摄日期", fontWeight = FontWeight.SemiBold)
                                Text(formatDate(captureTime), color = Muted)
                            }
                            TextButton(
                                onClick = { pickDate() },
                                enabled = !scanBusy
                            ) { Text("选择日期") }
                        }
                        if (!apiConfigured) {
                            Text(
                                "整页自动识别需要先在设置中配置 DeepSeek API 密钥。",
                                color = Red
                            )
                        }
                    }
                }
            }
            scan?.let { current ->
                item {
                    ScanStatusCard(
                        scan = current,
                        onRetry = { viewModel.enqueuePageScan(current.id) },
                        onViewResults = { onViewResults(current.id) }
                    )
                }
            }
            if (launchError.isNotBlank()) {
                item { Text(launchError, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Button(
                    onClick = {
                        launchError = ""
                        showProgress = true
                        viewModel.startPageScan(
                            sourcePaths.toList(),
                            subject,
                            captureTime,
                            onCreated = { scanId = it },
                            onError = {
                                showProgress = false
                                launchError = it
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sourcePaths.isNotEmpty() && !importing &&
                        scan?.status != AnalysisState.PROCESSING.name &&
                        scan?.status != AnalysisState.DONE.name
                ) {
                    Text("开始自动识别错题")
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    if (showProgress) {
        ScanProgressDialog(
            sourcePaths = sourcePaths,
            scan = scan,
            onBackground = { showProgress = false },
            onRetry = {
                scan?.let { viewModel.enqueuePageScan(it.id) }
            },
            onViewResults = {
                showProgress = false
                scan?.let { onViewResults(it.id) }
            }
        )
    }
}

@Composable
private fun ScanStatusCard(
    scan: PageScanEntity,
    onRetry: () -> Unit,
    onViewResults: () -> Unit
) {
    val status = runCatching { AnalysisState.valueOf(scan.status) }.getOrDefault(AnalysisState.PENDING)
    val pageCount = scanPagePaths(scan).size.coerceAtLeast(1)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                AnalysisState.FAILED -> MaterialTheme.colorScheme.errorContainer
                AnalysisState.DONE -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (status == AnalysisState.PENDING || status == AnalysisState.PROCESSING) {
                    CircularProgressIndicator(Modifier.height(22.dp))
                }
                Text(
                    if (status == AnalysisState.DONE) {
                        " 识别完成，发现 ${scan.detectedCount} 道候选题"
                    } else {
                        " ${scanStageLabel(scan.stage)} · $pageCount 页"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (status == AnalysisState.PENDING || status == AnalysisState.PROCESSING) {
                LinearProgressIndicator(
                    progress = { scan.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (scan.error.isNotBlank()) {
                Text(scan.error, style = MaterialTheme.typography.bodySmall)
            }
            if (status == AnalysisState.FAILED || status == AnalysisState.PENDING) {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, null)
                    Text(" 重试")
                }
            }
            if (status == AnalysisState.DONE) {
                Button(onClick = onViewResults) { Text("审查识别结果") }
            }
        }
    }
}

@Composable
private fun ScanProgressDialog(
    sourcePaths: List<String>,
    scan: PageScanEntity?,
    onBackground: () -> Unit,
    onRetry: () -> Unit,
    onViewResults: () -> Unit
) {
    val status = scan?.let {
        runCatching { AnalysisState.valueOf(it.status) }.getOrDefault(AnalysisState.PENDING)
    } ?: AnalysisState.PENDING
    Dialog(
        onDismissRequest = onBackground,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "整页识别进度",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "当前任务 · ${sourcePaths.size} 页 · ${scan?.let { "任务 ${it.id}" } ?: "正在创建任务"}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (sourcePaths.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sourcePaths.forEachIndexed { index, path ->
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                ImagePanel(path, Modifier.width(145.dp).height(210.dp))
                                Text("第 ${index + 1} 页", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text(
                    when {
                        scan == null -> "正在保存当前页"
                        status == AnalysisState.DONE -> "本页识别完成，发现 ${scan.detectedCount} 道候选题"
                        status == AnalysisState.FAILED -> "本页识别失败"
                        else -> scanStageLabel(scan.stage)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when (status) {
                        AnalysisState.DONE -> Green
                        AnalysisState.FAILED -> Red
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                LinearProgressIndicator(
                    progress = {
                        when {
                            scan == null -> 0.05f
                            status == AnalysisState.DONE -> 1f
                            status == AnalysisState.FAILED -> 0f
                            else -> scan.progress.coerceIn(0, 100) / 100f
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                val activeStage = scan?.stage.orEmpty()
                ScanStageRow("页面已保存", scan != null)
                ScanStageRow(
                    "彩色笔迹增强",
                    activeStage in setOf("AI_ANALYZING", "SAVING", "COMPLETE")
                )
                ScanStageRow(
                    "AI 检查批改痕迹",
                    activeStage in setOf("SAVING", "COMPLETE")
                )
                ScanStageRow("裁题并保存到错题本", activeStage == "COMPLETE")
                if (scan?.error?.isNotBlank() == true) {
                    Text(scan.error, color = Red, style = MaterialTheme.typography.bodySmall)
                }
                if (status != AnalysisState.DONE && status != AnalysisState.FAILED) {
                    Text(
                        "可以转到后台；关闭本页面后 WorkManager 仍会继续处理当前页。",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.weight(1f))
                when (status) {
                    AnalysisState.DONE -> {
                        Button(onClick = onViewResults, modifier = Modifier.fillMaxWidth()) {
                            Text("审查识别结果")
                        }
                        TextButton(onClick = onBackground, modifier = Modifier.fillMaxWidth()) {
                            Text("留在此页")
                        }
                    }
                    AnalysisState.FAILED -> {
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Refresh, null)
                            Text(" 重新识别本页")
                        }
                        TextButton(onClick = onBackground, modifier = Modifier.fillMaxWidth()) {
                            Text("关闭")
                        }
                    }
                    else -> {
                        OutlinedButton(onClick = onBackground, modifier = Modifier.fillMaxWidth()) {
                            Text("转到后台运行")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagePreviewEditor(
    path: String,
    index: Int,
    pageCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.width(126.dp).padding(7.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ImagePanel(path, Modifier.fillMaxWidth().height(132.dp))
            Text("第 ${index + 1} 页", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Outlined.ArrowUpward, "上移")
                }
                IconButton(onClick = onMoveDown, enabled = index < pageCount - 1) {
                    Icon(Icons.Outlined.ArrowDownward, "下移")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.DeleteOutline, "移除")
                }
            }
        }
    }
}

private fun scanPagePaths(scan: PageScanEntity): List<String> = runCatching {
    com.google.gson.Gson().fromJson(scan.sourceImagePathsJson, Array<String>::class.java).toList()
}.getOrDefault(emptyList()).filter { it.isNotBlank() }.ifEmpty {
    listOfNotNull(scan.sourceImagePath.takeIf { it.isNotBlank() })
}

@Composable
private fun ScanStageRow(label: String, complete: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            if (complete) "✓" else "○",
            color = if (complete) Green else Muted,
            fontWeight = FontWeight.Bold
        )
        Text(label, color = if (complete) MaterialTheme.colorScheme.onSurface else Muted)
    }
}

private fun scanStageLabel(stage: String): String = when (stage) {
    "QUEUED" -> "当前页已保存，等待处理"
    "WAITING_KEY" -> "等待配置 API 密钥"
    "RETRY_WAIT" -> "等待网络后重试"
    "ENHANCING" -> "正在生成彩色笔迹增强图"
    "AI_ANALYZING" -> "AI 正在检查当前页的题目和批改痕迹"
    "SAVING" -> "正在切分题目并保存到本地"
    "COMPLETE" -> "当前页识别完成"
    "FAILED" -> "当前页识别失败"
    else -> "正在处理当前页"
}
