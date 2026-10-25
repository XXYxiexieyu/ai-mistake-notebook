package com.hiro.mistakebook.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordLookupScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var analyzing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            viewModel.importImages(
                uris.take(5 - pagePaths.size),
                "words",
                onImported = { pagePaths = pagePaths + it },
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
            viewModel.importImage(
                uri,
                "words",
                onResult = {
                    pagePaths = pagePaths + it
                    importing = false
                    captured?.delete()
                },
                onError = {
                    importing = false
                    captured?.delete()
                    scope.launch { snackbar.showSnackbar(it) }
                }
            )
        } else {
            captured?.delete()
        }
        cameraUri = null
        cameraFile = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("英语阅读查词") },
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
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "只识别你在英语阅读或完形篇目中手写圈画、划线的单词。印刷体强调线不会被当作标记。",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "已添加 ${pagePaths.size}/5 页",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (pagePaths.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pagePaths.forEachIndexed { index, path ->
                                    Card(shape = RoundedCornerShape(14.dp)) {
                                        Column(
                                            modifier = Modifier.width(128.dp).padding(7.dp),
                                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                        ) {
                                            ImagePanel(path, Modifier.fillMaxWidth().height(120.dp))
                                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Text("第 ${index + 1} 页", style = MaterialTheme.typography.labelSmall)
                                                IconButton(
                                                    onClick = {
                                                        pagePaths = pagePaths.filterIndexed { i, _ -> i != index }
                                                        viewModel.deleteStoredImage(path)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Outlined.DeleteOutline, "删除页面")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
                                enabled = pagePaths.size < 5 && !importing && !analyzing
                            ) {
                                Icon(Icons.Outlined.CameraAlt, null)
                                Text(" 拍摄")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (pagePaths.size < 5) {
                                        gallery.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = pagePaths.size < 5 && !importing && !analyzing
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, null)
                                Text(" 相册")
                            }
                        }
                        if (importing) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(" 正在保存页面…")
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (pagePaths.isEmpty()) {
                            scope.launch { snackbar.showSnackbar("请先添加英语阅读页面") }
                            return@Button
                        }
                        analyzing = true
                        viewModel.lookupEnglishWords(
                            pagePaths,
                            onDone = { count ->
                                analyzing = false
                                scope.launch {
                                    snackbar.showSnackbar("已识别并加入单词本 $count 个单词")
                                    delay(650)
                                    onFinished()
                                }
                            },
                            onError = {
                                analyzing = false
                                scope.launch { snackbar.showSnackbar(it) }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pagePaths.isNotEmpty() && !analyzing && !importing
                ) {
                    if (analyzing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(" AI 正在识别标记单词…")
                    } else {
                        Text("识别圈画/划线单词并加入单词本")
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}
