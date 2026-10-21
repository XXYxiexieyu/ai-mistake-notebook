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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.AnalysisState
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.PageScanEntity
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
    onViewResults: () -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val apiConfigured by viewModel.apiConfigured.collectAsStateWithLifecycle()
    var sourcePath by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("数学") }
    var captureTime by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var scanId by rememberSaveable { mutableStateOf<Long?>(null) }
    var importing by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf("") }

    var scan by remember { mutableStateOf<PageScanEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(scanId) {
        scan = null
        val id = scanId ?: return@LaunchedEffect
        viewModel.repository.observePageScan(id).collect { scan = it }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importing = true
            viewModel.importImage(uri, "page", onResult = {
                sourcePath = it
                scanId = null
                importing = false
            }, onError = {
                importing = false
                scope.launch { snackbar.showSnackbar(it) }
            })
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
                sourcePath = it
                scanId = null
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
                        if (sourcePath.isNotBlank()) {
                            ImagePanel(sourcePath, Modifier.fillMaxWidth().height(330.dp))
                        } else {
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.CameraAlt, null)
                                Text(" 拍摄整页")
                            }
                            OutlinedButton(
                                onClick = { gallery.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, null)
                                Text(" 从相册选择")
                            }
                        }
                        if (importing) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.height(20.dp))
                                Text(" 正在保存整页图片…")
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
                        SubjectChips(selected = subject, includeAll = false, onSelected = { subject = it })
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column {
                                Text("拍摄日期", fontWeight = FontWeight.SemiBold)
                                Text(formatDate(captureTime), color = Muted)
                            }
                            TextButton(onClick = { pickDate() }) { Text("选择日期") }
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
                        onViewResults = onViewResults
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
                        viewModel.startPageScan(
                            sourcePath,
                            subject,
                            captureTime,
                            onCreated = { scanId = it },
                            onError = { launchError = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sourcePath.isNotBlank() && !importing &&
                        scan?.status != AnalysisState.PROCESSING.name &&
                        scan?.status != AnalysisState.DONE.name
                ) {
                    Text("开始自动识别错题")
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun ScanStatusCard(
    scan: PageScanEntity,
    onRetry: () -> Unit,
    onViewResults: () -> Unit
) {
    val status = runCatching { AnalysisState.valueOf(scan.status) }.getOrDefault(AnalysisState.PENDING)
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
                    when (status) {
                        AnalysisState.PENDING -> " 已保存，等待联网分析"
                        AnalysisState.PROCESSING -> " AI 正在检查整页批改痕迹"
                        AnalysisState.DONE -> " 识别完成，已保存 ${scan.detectedCount} 道错题"
                        AnalysisState.FAILED -> " 识别失败"
                        AnalysisState.MANUAL -> " 等待处理"
                    },
                    fontWeight = FontWeight.SemiBold
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
                Button(onClick = onViewResults) { Text("查看错题本") }
            }
        }
    }
}
