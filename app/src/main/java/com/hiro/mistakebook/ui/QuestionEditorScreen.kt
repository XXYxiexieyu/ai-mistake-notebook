package com.hiro.mistakebook.ui

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.ErrorCauseState
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.QuestionDraft
import com.hiro.mistakebook.data.QuestionStatus
import com.hiro.mistakebook.data.Subjects
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditorScreen(
    viewModel: MainViewModel,
    questionId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isEdit = questionId != null
    var loaded by remember(questionId) { mutableStateOf(!isEdit) }
    var existing by remember(questionId) { mutableStateOf<com.hiro.mistakebook.data.QuestionEntity?>(null) }

    var subject by remember { mutableStateOf("数学") }
    var book by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("") }
    var questionType by remember { mutableStateOf("") }
    var mathTopic by remember { mutableStateOf("") }
    var questionText by remember { mutableStateOf("") }
    var answerText by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    var knowledgeText by remember { mutableStateOf("") }
    var errorCause by remember { mutableStateOf("") }
    var originalErrorCause by remember { mutableStateOf("") }
    var originalErrorCauseState by remember { mutableStateOf(ErrorCauseState.UNSUPPLEMENTED.name) }
    var noErrorCause by remember { mutableStateOf(false) }
    var errorSubpart by remember { mutableStateOf("") }
    var originalAnswerText by remember { mutableStateOf("") }
    var captureTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var imagePath by remember { mutableStateOf("") }
    var sourcePath by remember { mutableStateOf("") }
    var answerImagePath by remember { mutableStateOf("") }
    var autoAnalyze by remember { mutableStateOf(true) }
    var cropPath by remember { mutableStateOf<String?>(null) }
    var duplicateId by remember { mutableStateOf<Long?>(null) }
    var pendingDraft by remember { mutableStateOf<QuestionDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(QuestionStatus.UNREVIEWED) }
    var mathInputTarget by remember { mutableStateOf<MathInputTarget?>(null) }

    LaunchedEffect(questionId) {
        if (questionId != null) {
            val question = viewModel.repository.getQuestion(questionId)
            if (question != null) {
                existing = question
                subject = question.subject
                book = question.book
                unit = question.unit
                chapter = question.chapter
                knowledgeText = knowledgePoints(question.knowledgePointsJson).joinToString("，")
                questionType = question.questionType
                mathTopic = question.mathTopic
                questionText = question.questionText
                answerText = question.answerText
                explanation = question.explanation
                errorCause = question.errorCause
                originalErrorCause = question.errorCause
                originalErrorCauseState = question.errorCauseState
                noErrorCause = question.errorCauseState == ErrorCauseState.UNSUPPLEMENTED.name
                errorSubpart = question.errorSubpart
                originalAnswerText = question.originalAnswerText
                captureTime = question.captureTime
                imagePath = question.imagePath
                sourcePath = question.sourceImagePath
                answerImagePath = question.originalAnswerImagePath
                status = runCatching { QuestionStatus.valueOf(question.status) }
                    .getOrDefault(QuestionStatus.UNREVIEWED)
                autoAnalyze = false
            }
            loaded = true
        }
    }

    val books = remember(subject) { viewModel.catalog.booksFor(subject) }
    val units = remember(subject, book) { viewModel.catalog.units(subject, book) }
    val sections = remember(subject, book, unit) {
        viewModel.catalog.sections(subject, book, unit)
    }
    LaunchedEffect(subject) {
        if (loaded && books.none { it.title == book }) {
            book = ""
            unit = ""
            chapter = ""
            questionType = ""
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.importImage(uri, "source", onResult = { path ->
                sourcePath = path
                imagePath = path
                cropPath = path
            }, onError = { message ->
                scope.launch { snackbar.showSnackbar(message) }
            })
        }
    }
    val answerGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.importImage(uri, "answer", onResult = { path ->
                answerImagePath = path
            }, onError = { message ->
                scope.launch { snackbar.showSnackbar(message) }
            })
        }
    }
    var answerCameraUri by remember { mutableStateOf<Uri?>(null) }
    var answerCameraFile by remember { mutableStateOf<File?>(null) }
    val answerCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = answerCameraUri
        val capturedFile = answerCameraFile
        if (success && uri != null) {
            viewModel.importImage(uri, "answer", onResult = { path ->
                answerImagePath = path
                capturedFile?.delete()
            }, onError = { message ->
                capturedFile?.delete()
                scope.launch { snackbar.showSnackbar(message) }
            })
        } else {
            capturedFile?.delete()
        }
        answerCameraFile = null
        answerCameraUri = null
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        val capturedFile = cameraFile
        if (success && uri != null) {
            viewModel.importImage(uri, "source", onResult = { path ->
                sourcePath = path
                imagePath = path
                cropPath = path
                capturedFile?.delete()
            }, onError = { message ->
                capturedFile?.delete()
                scope.launch { snackbar.showSnackbar(message) }
            })
        } else {
            capturedFile?.delete()
        }
        cameraFile = null
        cameraUri = null
    }
    fun launchCamera() {
        runCatching {
            val file = ImageStore(context).newCaptureFile()
            cameraFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }.onFailure {
            scope.launch { snackbar.showSnackbar("无法打开相机：${it.message}") }
        }
    }

    fun currentDraft(): QuestionDraft {
        val points = knowledgeText.split(Regex("[,，、;；\\n]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return QuestionDraft(
            subject = subject,
            book = book,
            unit = unit,
            chapter = chapter,
            knowledgePoints = points,
            questionType = questionType,
            mathTopic = if (subject == "数学" && questionType == "解答题") mathTopic else "",
            questionText = questionText,
            answerText = answerText,
            explanation = explanation,
            errorCause = if (noErrorCause) "" else errorCause,
            errorCauseState = when {
                noErrorCause -> ErrorCauseState.UNSUPPLEMENTED
                errorCause.isBlank() -> ErrorCauseState.AI_ACCEPTED
                errorCause == originalErrorCause &&
                    originalErrorCauseState in setOf(
                        ErrorCauseState.AI_CANDIDATE.name,
                        ErrorCauseState.AI_ACCEPTED.name
                    ) -> ErrorCauseState.AI_ACCEPTED
                else -> ErrorCauseState.CONFIRMED
            },
            errorSubpart = errorSubpart,
            questionImage = imagePath,
            sourceImage = sourcePath,
            originalAnswerImage = answerImagePath,
            originalAnswerText = originalAnswerText,
            captureTime = captureTime,
            autoAnalyze = autoAnalyze && imagePath.isNotBlank()
        )
    }

    fun save(forceNew: Boolean = false, mergeTarget: Long? = null) {
        if (saving) return
        if (imagePath.isBlank() && questionText.isBlank()) {
            scope.launch { snackbar.showSnackbar("请先添加题目图片或输入题目文字") }
            return
        }
        saving = true
        if (isEdit && existing != null) {
            val original = existing!!
            val points = currentDraft().knowledgePoints
            viewModel.updateQuestion(
                original.copy(
                    subject = subject,
                    book = book,
                    unit = unit,
                    chapter = chapter,
                    knowledgePointsJson = Gson().toJson(points),
                    questionType = questionType,
                    mathTopic = if (subject == "数学" && questionType == "解答题") mathTopic else "",
                    questionText = questionText,
                    answerText = answerText,
                    explanation = explanation,
                    errorCause = if (noErrorCause) "" else errorCause,
                    errorCauseState = when {
                        noErrorCause -> ErrorCauseState.UNSUPPLEMENTED.name
                        errorCause.isBlank() -> ErrorCauseState.AI_ACCEPTED.name
                        errorCause == originalErrorCause &&
                            originalErrorCauseState in setOf(
                                ErrorCauseState.AI_CANDIDATE.name,
                                ErrorCauseState.AI_ACCEPTED.name
                            ) ->
                            ErrorCauseState.AI_ACCEPTED.name
                        else -> ErrorCauseState.CONFIRMED.name
                    },
                    errorSubpart = errorSubpart,
                    originalAnswerText = originalAnswerText,
                    captureTime = captureTime,
                    imagePath = imagePath,
                    sourceImagePath = sourcePath,
                    originalAnswerImagePath = answerImagePath,
                    status = status.name,
                    passedAt = if (status == QuestionStatus.PASSED) {
                        original.passedAt ?: System.currentTimeMillis()
                    } else {
                        null
                    }
                )
            ) {
                if (autoAnalyze && imagePath.isNotBlank()) viewModel.rerunAnalysis(original.id)
                onSaved(original.id)
            }
            return
        }
        val draft = currentDraft()
        viewModel.saveQuestion(
            draft = draft,
            forceNew = forceNew,
            mergeTargetId = mergeTarget,
            onSaved = { id ->
                saving = false
                onSaved(id)
            },
            onError = { message ->
                saving = false
                if (message.startsWith("DUPLICATE:")) {
                    duplicateId = message.substringAfter("DUPLICATE:").toLongOrNull()
                    pendingDraft = draft
                } else {
                    scope.launch { snackbar.showSnackbar(message) }
                }
            }
        )
    }

    fun insertMathSymbol(value: String) {
        when (mathInputTarget) {
            MathInputTarget.QUESTION -> questionText += value
            MathInputTarget.ANSWER -> answerText += value
            MathInputTarget.EXPLANATION -> explanation += value
            MathInputTarget.ORIGINAL_ANSWER -> originalAnswerText += value
            MathInputTarget.ERROR_CAUSE -> errorCause += value
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑错题" else "添加错题") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = loaded && !saving) {
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding)) { Text("正在读取…") }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                EditorSection("题目图片") {
                    if (imagePath.isNotBlank()) {
                        ImagePanel(imagePath, Modifier.fillMaxWidth().height(220.dp))
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text("尚未添加图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.CameraAlt, null)
                            Text(" 拍照")
                        }
                        OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.PhotoLibrary, null)
                            Text(" 相册")
                        }
                    }
                    if (sourcePath.isNotBlank() || imagePath.isNotBlank()) {
                        OutlinedButton(
                            onClick = { cropPath = sourcePath.ifBlank { imagePath } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.ContentCut, null)
                            Text(" 重新框选题目")
                        }
                    }
                }
            }

            item {
                EditorSection("分类与题型") {
                    SubjectChips(
                        selected = subject,
                        includeAll = false,
                        onSelected = {
                            subject = it
                            book = ""
                            unit = ""
                            chapter = ""
                            questionType = ""
                        }
                    )
                    DropdownField(
                        label = "教材书目",
                        options = listOf(AUTO_OPTION) + books.map { "${it.publisher} · ${it.title}" },
                        selected = books.firstOrNull { it.title == book }
                            ?.let { "${it.publisher} · ${it.title}" }
                            ?: AUTO_OPTION,
                        onSelected = { value ->
                            book = if (value == AUTO_OPTION) {
                                ""
                            } else {
                                books.firstOrNull { "${it.publisher} · ${it.title}" == value }
                                    ?.title.orEmpty()
                            }
                            unit = ""
                            chapter = ""
                        }
                    )
                    DropdownField(
                        label = "单元 / 章",
                        options = listOf(AUTO_OPTION) + units.map { it.title },
                        selected = unit.ifBlank { AUTO_OPTION },
                        onSelected = {
                            unit = it.takeUnless { value -> value == AUTO_OPTION }.orEmpty()
                            chapter = ""
                        }
                    )
                    DropdownField(
                        label = "章节 / 课",
                        options = listOf(AUTO_OPTION) + sections,
                        selected = chapter.ifBlank { AUTO_OPTION },
                        onSelected = {
                            chapter = it.takeUnless { value -> value == AUTO_OPTION }.orEmpty()
                        }
                    )
                    DropdownField(
                        label = "题型",
                        options = listOf(AUTO_OPTION) + Subjects.questionTypes(subject),
                        selected = questionType.ifBlank { AUTO_OPTION },
                        onSelected = {
                            questionType = it.takeUnless { value -> value == AUTO_OPTION }.orEmpty()
                        }
                    )
                    if (subject == "数学" && questionType == "解答题") {
                        DropdownField(
                            label = "数学大题专题",
                            options = listOf(AUTO_OPTION) + Subjects.mathTopics(),
                            selected = mathTopic.ifBlank { AUTO_OPTION },
                            onSelected = {
                                mathTopic = it.takeUnless { value -> value == AUTO_OPTION }.orEmpty()
                            }
                        )
                    }
                    OutlinedTextField(
                        value = knowledgeText,
                        onValueChange = { knowledgeText = it },
                        label = { Text("知识点，用逗号分隔") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Text(
                        "知识点默认采用 AI 识别结果，并自动匹配教材书目、单元和章节。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                EditorSection("题目与解答") {
                    Text(
                        "数学公式可粘贴 LaTeX 或使用右侧 ∑ 符号库；显示时会自动转换为 √、²、³、∠、∥ 等标准符号。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        label = { Text("题目文字（可由 AI 自动填写）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        trailingIcon = {
                            MathSymbolTrailingButton {
                                mathInputTarget = MathInputTarget.QUESTION
                            }
                        }
                    )
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it },
                        label = { Text("答案") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        trailingIcon = {
                            MathSymbolTrailingButton {
                                mathInputTarget = MathInputTarget.ANSWER
                            }
                        }
                    )
                    OutlinedTextField(
                        value = explanation,
                        onValueChange = { explanation = it },
                        label = { Text("解题过程与解析") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        trailingIcon = {
                            MathSymbolTrailingButton {
                                mathInputTarget = MathInputTarget.EXPLANATION
                            }
                        }
                    )
                }
            }

            item {
                EditorSection("原作答与错因（可选）") {
                    OutlinedTextField(
                        value = originalAnswerText,
                        onValueChange = { originalAnswerText = it },
                        label = { Text("原错误答案或关键步骤") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        trailingIcon = {
                            MathSymbolTrailingButton {
                                mathInputTarget = MathInputTarget.ORIGINAL_ANSWER
                            }
                        }
                    )
                    OutlinedTextField(
                        value = errorSubpart,
                        onValueChange = { errorSubpart = it },
                        label = { Text("错误所在小问 / 步骤") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (answerImagePath.isNotBlank()) {
                        ImagePanel(answerImagePath, Modifier.fillMaxWidth().height(160.dp))
                    }
                    Text(
                        if (answerImagePath.isBlank()) "添加原作答/草稿照片" else "更换原作答/草稿照片",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { answerGalleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null)
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
                                    answerCameraLauncher.launch(uri)
                                }.onFailure {
                                    scope.launch { snackbar.showSnackbar("无法打开相机：${it.message}") }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.CameraAlt, null)
                            Text(" 拍照")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("不补充错因", fontWeight = FontWeight.SemiBold)
                            Text(
                                "选择后不显示推测错因，也不计入错因统计。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = noErrorCause, onCheckedChange = { noErrorCause = it })
                    }
                    if (!noErrorCause) {
                        OutlinedTextField(
                            value = errorCause,
                            onValueChange = { errorCause = it },
                            label = { Text("错因（AI 识别后自动填入）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            trailingIcon = {
                                MathSymbolTrailingButton {
                                    mathInputTarget = MathInputTarget.ERROR_CAUSE
                                }
                            }
                        )
                    }
                }
            }

            item {
                EditorSection("记录信息") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("拍摄日期", fontWeight = FontWeight.SemiBold)
                            Text(formatDate(captureTime), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (isEdit) {
                        Text("复习状态", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuestionStatus.entries.forEach { value ->
                                FilterChip(
                                    selected = status == value,
                                    onClick = { status = value },
                                    label = { Text(value.label) }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("保存后 AI 分析与知识网匹配", fontWeight = FontWeight.SemiBold)
                            Text(
                                "自动识别知识点并定位到教材书目、单元和章节；断网时保留任务。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoAnalyze, onCheckedChange = { autoAnalyze = it })
                    }
                }
            }

            item {
                Button(
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                ) {
                    Text(if (saving) "正在保存…" else "保存错题")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    cropPath?.let { path ->
        CropDialog(
            imagePath = path,
            onDismiss = { cropPath = null },
            onCropped = { bitmap ->
                cropPath = null
                viewModel.saveCrop(bitmap, "question") { cropped ->
                    imagePath = cropped
                }
            }
        )
    }

    duplicateId?.let { id ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties()
        ) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("发现疑似重复题", style = MaterialTheme.typography.titleLarge)
                    Text("应用根据图片或题目文字找到了已有错题。是否合并并累计错误次数？")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            duplicateId = null
                            save(forceNew = true)
                        }) { Text("保留为新题") }
                        Button(onClick = {
                            duplicateId = null
                            save(forceNew = true, mergeTarget = id)
                        }) { Text("合并旧题") }
                    }
                }
            }
        }
    }

    mathInputTarget?.let {
        MathSymbolPickerDialog(
            onDismiss = { mathInputTarget = null },
            onInsert = { symbol -> insertMathSymbol(symbol) }
        )
    }
}

private enum class MathInputTarget {
    QUESTION,
    ANSWER,
    EXPLANATION,
    ORIGINAL_ANSWER,
    ERROR_CAUSE
}

@Composable
private fun MathSymbolTrailingButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Text(
            "∑",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private const val AUTO_OPTION = "AI 自动识别（默认）"
