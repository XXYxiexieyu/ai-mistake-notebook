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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.ImportResult
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    val apiConfigured by viewModel.apiConfigured.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var yearReminder by remember { mutableStateOf(viewModel.yearReminderEnabled()) }
    var pdfSubject by remember { mutableStateOf("全部") }
    var pdfStart by remember { mutableLongStateOf(0L) }
    var pdfEnd by remember { mutableLongStateOf(0L) }
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pendingZipUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var importPreview by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var storageSize by remember { mutableLongStateOf(0L) }

    LaunchedEffect(questions.size) {
        storageSize = withContext(Dispatchers.IO) {
            File(context.filesDir, "images").walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPdf(
                uri,
                pdfSubject.takeUnless { it == "全部" },
                pdfStart.takeIf { it > 0 },
                pdfEnd.takeIf { it > 0 },
                onDone = {
                    scope.launch { snackbar.showSnackbar("PDF 已导出") }
                },
                onError = {
                    scope.launch { snackbar.showSnackbar(it) }
                }
            )
        }
    }
    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(
                uri,
                onDone = { count ->
                    scope.launch { snackbar.showSnackbar("备份已导出，共 $count 张图片") }
                },
                onError = { scope.launch { snackbar.showSnackbar(it) } }
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.previewImport(
                uri,
                onDone = { total, new, duplicate ->
                    importPreview = Triple(total, new, duplicate)
                    pendingImportUri = uri
                },
                onError = { scope.launch { snackbar.showSnackbar(it) } }
            )
        }
    }

    fun pickDate(initial: Long, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { timeInMillis = initial.takeIf { it > 0 } ?: System.currentTimeMillis() }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onPicked(
                    Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("模型、报告、备份和本地数据", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            item {
                SettingCard("DeepSeek 官方 API") {
                    Text(
                        if (apiConfigured) "已配置密钥。请求仅用于识题、错因分析和重考判分。" else "尚未配置。未配置时本地分类功能仍可正常使用。",
                        color = if (apiConfigured) Green else Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(if (apiConfigured) "输入新密钥以替换" else "API 密钥") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (apiKey.isBlank()) {
                                    scope.launch { snackbar.showSnackbar("请输入 API 密钥") }
                                } else {
                                    viewModel.saveApiKey(apiKey)
                                    apiKey = ""
                                    scope.launch { snackbar.showSnackbar("密钥已加密保存") }
                                }
                            }
                        ) { Text("保存密钥") }
                        OutlinedButton(onClick = {
                            viewModel.saveApiKey("")
                            scope.launch { snackbar.showSnackbar("密钥已删除") }
                        }) { Text("删除密钥") }
                    }
                    Text("模型", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("deepseek-flash", "deepseek-v4-pro").forEach { value ->
                            FilterChip(
                                selected = model == value,
                                onClick = { viewModel.setModel(value) },
                                label = { Text(if (value == "deepseek-flash") "Flash（默认）" else "V4 Pro") }
                            )
                        }
                    }
                }
            }
            item {
                SettingCard("PDF 错误知识点报告") {
                    Text(
                        "报告不包含具体题目、题目图片或答案。可选择科目和拍摄日期范围。",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    SubjectChips(selected = pdfSubject, onSelected = { pdfSubject = it })
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { pickDate(pdfStart) { pdfStart = it } }) {
                            Text(if (pdfStart == 0L) "开始日期：不限" else "开始：${formatDate(pdfStart)}")
                        }
                        TextButton(onClick = {
                            pickDate(pdfEnd) {
                                pdfEnd = it + 24L * 60 * 60 * 1000 - 1
                            }
                        }) {
                            Text(if (pdfEnd == 0L) "结束日期：不限" else "结束：${formatDate(pdfEnd)}")
                        }
                    }
                    OutlinedButton(
                        onClick = { pdfLauncher.launch("错题知识点报告.pdf") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Icon(Icons.Outlined.FileDownload, null)
                        Text(" 导出 PDF")
                    }
                }
            }
            item {
                SettingCard("换机备份") {
                    Text(
                        "普通 ZIP 包含题目图片、原作答、分析结果、重考记录和自定义内容。导入时按合并方式处理重复题。",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { zipLauncher.launch("AI错题本备份.zip") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Icon(Icons.Outlined.Backup, null)
                        Text(" 导出完整 ZIP")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Icon(Icons.Outlined.FileUpload, null)
                        Text(" 从 ZIP 合并导入")
                    }
                }
            }
            item {
                SettingCard("一年提醒") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("提醒已重考通过满一年的错题", fontWeight = FontWeight.SemiBold)
                            Text(
                                "只提示，不自动删除；可在首页选择删除当前题或全部符合项。",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = yearReminder,
                            onCheckedChange = {
                                yearReminder = it
                                viewModel.setYearReminder(it)
                            }
                        )
                    }
                }
            }
            item {
                SettingCard("本地存储") {
                    Text("已收录 ${questions.size} 道错题", fontWeight = FontWeight.SemiBold)
                    Text(
                        "图片约 ${"%.1f".format(storageSize / 1024f / 1024f)} MB",
                        color = Muted
                    )
                    Text(
                        "应用使用本地 Room 数据库。没有登录、云端同步或系统自动云备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }
            item {
                Text(
                    "AI错题本 0.1.0 预览版",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
        }
    }

    importPreview?.let { preview ->
        val (total, new, duplicate) = preview
        AlertDialog(
            onDismissRequest = {
                importPreview = null
                pendingImportUri = null
            },
            title = { Text("确认合并导入") },
            text = {
                Text("备份含 $total 道错题：预计新增 $new 道，重复合并 $duplicate 道。本机已有数据不会被覆盖。")
            },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingImportUri ?: return@Button
                    viewModel.importBackup(
                        uri,
                        onDone = { result: ImportResult ->
                            scope.launch {
                                snackbar.showSnackbar(
                                    "导入完成：新增 ${result.added}，合并 ${result.merged}，恢复图片 ${result.imagesRestored}"
                                )
                            }
                        },
                        onError = { scope.launch { snackbar.showSnackbar(it) } }
                    )
                    importPreview = null
                    pendingImportUri = null
                }) { Text("合并导入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    importPreview = null
                    pendingImportUri = null
                }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
