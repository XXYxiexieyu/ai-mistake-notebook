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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.QuestionEntity
import com.hiro.mistakebook.data.QuestionStatus
import com.hiro.mistakebook.data.RetakeGrade
import com.hiro.mistakebook.data.RetakeItemEntity
import com.hiro.mistakebook.data.RetakeSessionStatus
import com.hiro.mistakebook.data.Subjects
import com.hiro.mistakebook.ui.theme.Amber
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import com.hiro.mistakebook.ui.theme.Red
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.io.File

@Composable
fun RetakeHomeScreen(
    viewModel: MainViewModel,
    onBuild: () -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val sessions by viewModel.retakeSessions.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("重考", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("按科目和拍摄日期使用原错题组卷", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onBuild) {
                    Icon(Icons.Outlined.Add, null)
                    Text(" 新建")
                }
            }
        }
        if (sessions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Refresh, null, tint = Green)
                        Spacer(Modifier.height(8.dp))
                        Text("还没有重考记录")
                        Text(
                            "先把错题状态标记为“已复习”，再选择题目组卷。",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = { onOpenSession(session.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "拍摄日期 ${formatDate(session.startCaptureTime)} — ${formatDate(session.endCaptureTime)}",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "状态：${runCatching { RetakeSessionStatus.valueOf(session.status).label }.getOrDefault(session.status)}" +
                                    if (session.totalElapsedMs > 0) " · 用时 ${formatDuration(session.totalElapsedMs)}" else "",
                                color = if (session.status == RetakeSessionStatus.COMPLETED.name) Green else Amber,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Text("查看", color = Green)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetakeBuilderScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val allQuestions by viewModel.questions.collectAsStateWithLifecycle()
    var subject by remember { mutableStateOf("数学") }
    var startTime by remember {
        mutableLongStateOf(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
    }
    var endTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val eligible = remember(allQuestions, subject, startTime, endTime) {
        allQuestions
            .filter {
                it.subject == subject &&
                    it.status == QuestionStatus.REVIEWED.name &&
                    it.captureTime in startTime..endTime
            }
            .sortedBy { it.captureTime }
    }
    var selected by remember(eligible) { mutableStateOf(eligible.map { it.id }.toSet()) }
    var error by remember { mutableStateOf("") }

    fun pickDate(initial: Long, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { timeInMillis = initial }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建重考") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SubjectChips(selected = subject, includeAll = false, onSelected = {
                    subject = it
                })
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Text("拍摄日期范围", fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { pickDate(startTime) { startTime = it } }) {
                                Text("开始：${formatDate(startTime)}")
                            }
                            TextButton(onClick = {
                                pickDate(endTime) {
                                    endTime = it + TimeUnit.DAYS.toMillis(1) - 1
                                }
                            }) {
                                Text("结束：${formatDate(endTime)}")
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "可用已复习错题 ${eligible.size} 道",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = {
                        selected = if (selected.size == eligible.size) emptySet() else eligible.map { it.id }.toSet()
                    }) {
                        Text(if (selected.size == eligible.size) "取消全选" else "全选")
                    }
                }
            }
            if (eligible.isEmpty()) {
                item {
                    Text(
                        "该科目和日期范围内没有“已复习”的错题。请先在错题详情中更新状态。",
                        color = Muted
                    )
                }
            }
            items(eligible, key = { it.id }) { question ->
                SelectQuestionRow(
                    question = question,
                    checked = question.id in selected,
                    onChecked = { checked ->
                        selected = if (checked) selected + question.id else selected - question.id
                    }
                )
            }
            if (error.isNotBlank()) {
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Button(
                    onClick = {
                        error = ""
                        viewModel.createRetake(
                            subject,
                            startTime,
                            endTime,
                            selected.toList(),
                            onCreated = onCreated,
                            onError = { error = it }
                        )
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("生成测试（${selected.size} 题）")
                }
            }
        }
    }
}

@Composable
private fun SelectQuestionRow(
    question: QuestionEntity,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { onChecked(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = checked, onCheckedChange = onChecked)
            ImagePanel(question.imagePath, Modifier.height(64.dp).fillMaxWidth(0.22f))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${question.subject} · ${question.questionType.ifBlank { "未分类" }}",
                    color = Green,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    question.questionText.ifBlank { "${question.book} / ${question.unit}" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(formatDate(question.captureTime), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetakeRunScreen(
    viewModel: MainViewModel,
    sessionId: Long,
    onBack: () -> Unit
) {
    val sessionFlow = remember(sessionId) { viewModel.repository.observeRetakeSession(sessionId) }
    val itemsFlow = remember(sessionId) { viewModel.repository.observeRetakeItems(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)
    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val allQuestions by viewModel.questions.collectAsStateWithLifecycle()
    var currentIndex by rememberSaveable(sessionId) { mutableIntStateOf(0) }
    var indexInitialized by rememberSaveable(sessionId) { mutableStateOf(false) }
    var answerText by remember(sessionId) { mutableStateOf("") }
    var answerImage by remember(sessionId) { mutableStateOf("") }
    var itemStartedAt by remember(sessionId) { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsed by remember(sessionId) { mutableLongStateOf(0) }
    var error by remember { mutableStateOf("") }
    var showGradeOptions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importImage(uri, "retake", onResult = { answerImage = it }, onError = { error = it })
        }
    }
    var answerCameraUri by remember { mutableStateOf<Uri?>(null) }
    var answerCameraFile by remember { mutableStateOf<File?>(null) }
    val answerCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = answerCameraUri
        val capturedFile = answerCameraFile
        if (success && uri != null) {
            viewModel.importImage(uri, "retake", onResult = {
                answerImage = it
                capturedFile?.delete()
            }, onError = {
                error = it
                capturedFile?.delete()
            })
        } else {
            capturedFile?.delete()
        }
        answerCameraFile = null
        answerCameraUri = null
    }

    val item = items.getOrNull(currentIndex)
    val question = item?.let { retakeItem -> allQuestions.firstOrNull { it.id == retakeItem.questionId } }

    LaunchedEffect(item?.id) {
        if (item != null) {
            answerText = item.answerText
            answerImage = item.answerImagePath
            itemStartedAt = System.currentTimeMillis()
        }
    }
    LaunchedEffect(items) {
        if (!indexInitialized && items.isNotEmpty()) {
            val next = items.indexOfFirst { it.answeredAt == null }
            currentIndex = if (next >= 0) next else items.lastIndex
            indexInitialized = true
        }
    }
    LaunchedEffect(item?.id) {
        while (true) {
            elapsed = System.currentTimeMillis() - itemStartedAt
            delay(1_000)
        }
    }

    fun persistCurrent(onDone: () -> Unit = {}) {
        if (item == null) {
            onDone()
            return
        }
        viewModel.saveRetakeAnswer(
            item,
            answerText,
            answerImage,
            System.currentTimeMillis() - itemStartedAt,
            onSaved = onDone
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.name ?: "重考")
                        Text(
                            "第 ${currentIndex + 1}/${items.size.coerceAtLeast(1)} 题 · ${formatDuration(elapsed)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        persistCurrent()
                        onBack()
                    }) { Icon(Icons.Outlined.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        if (session == null || item == null || question == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text(if (items.isEmpty()) "正在准备测试…" else "测试内容已加载完成。")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ImagePanel(question.imagePath, Modifier.fillMaxWidth().height(280.dp))
            }
            item {
                Text(
                    question.questionText.ifBlank { "以题目图片为准" },
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    label = { Text("重考答案") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }
            item {
                if (answerImage.isNotBlank()) ImagePanel(answerImage, Modifier.fillMaxWidth().height(180.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null)
                        Text(" 相册")
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val file = ImageStore(context).newCaptureFile()
                                answerCameraFile = file
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.files",
                                    file
                                )
                                answerCameraUri = uri
                                answerCamera.launch(uri)
                            }.onFailure { error = "无法打开相机：${it.message}" }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.CameraAlt, null)
                        Text(" 拍照作答")
                    }
                }
            }
            if (item.aiFeedback.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("AI 判分：${runCatching { RetakeGrade.valueOf(item.aiGrade).label }.getOrDefault(item.aiGrade)}")
                            Text(item.aiFeedback, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { showGradeOptions = !showGradeOptions }) {
                    Text(if (showGradeOptions) "收起评分选项" else "评分选项")
                }
                if (showGradeOptions) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradeChip("通过", RetakeGrade.CORRECT, item) {
                            persistCurrent { viewModel.applyManualGrade(item, it) }
                        }
                        GradeChip("部分正确", RetakeGrade.PARTIAL, item) {
                            persistCurrent { viewModel.applyManualGrade(item, it) }
                        }
                        GradeChip("未通过", RetakeGrade.WRONG, item) {
                            persistCurrent { viewModel.applyManualGrade(item, it) }
                        }
                    }
                }
            }
            if (error.isNotBlank()) {
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            persistCurrent {
                                viewModel.enqueueAiGrade(item.id)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存并 AI 判分")
                    }
                    Button(
                        onClick = {
                            persistCurrent {
                                if (currentIndex < items.lastIndex) {
                                    currentIndex++
                                } else {
                                    viewModel.finishRetake(sessionId)
                                    onBack()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (currentIndex == items.lastIndex) "完成测试" else "保存并下一题")
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun GradeChip(
    label: String,
    grade: RetakeGrade,
    item: RetakeItemEntity,
    onClick: ((RetakeGrade) -> Unit)
) {
    FilterChip(
        selected = item.finalGrade == grade.name,
        onClick = { onClick(grade) },
        label = { Text(label) },
        leadingIcon = if (item.finalGrade == grade.name) {
            { Icon(Icons.Outlined.Check, null) }
        } else {
            null
        }
    )
}
