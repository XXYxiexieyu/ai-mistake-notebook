package com.hiro.mistakebook.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.ChatMessageStatus
import com.hiro.mistakebook.data.ChatRole
import com.hiro.mistakebook.data.QuestionChatMessageEntity
import com.hiro.mistakebook.ui.theme.Green
import com.hiro.mistakebook.ui.theme.Muted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionChatScreen(
    viewModel: MainViewModel,
    questionId: Long,
    onBack: () -> Unit
) {
    val question by remember(questionId) {
        viewModel.repository.observeQuestion(questionId)
    }.collectAsStateWithLifecycle(initialValue = null)
    val messages by remember(questionId) {
        viewModel.observeChatMessages(questionId)
    }.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var attachedImage by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importImage(uri, "chat", onResult = { attachedImage = it }, onError = {
                scope.launch { snackbar.showSnackbar(it) }
            })
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        if (sending || (text.isBlank() && attachedImage.isBlank())) return
        sending = true
        viewModel.askQuestion(
            questionId,
            text,
            attachedImage,
            onSent = {
                text = ""
                attachedImage = ""
                sending = false
            },
            onError = {
                sending = false
                scope.launch { snackbar.showSnackbar(it) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("针对本题提问")
                        Text(
                            question?.let { "${it.subject} · ${it.questionType.ifBlank { "未分类" }}" }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            question?.let { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImagePanel(item.imagePath, Modifier.size(72.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        MathText(
                            item.questionText.ifBlank { "${item.book} / ${item.unit}" },
                            maxLines = 2,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "题目照片会自动加入每次提问",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("可以这样问", fontWeight = FontWeight.SemiBold)
                                listOf(
                                    "这道题最关键的知识点是什么？",
                                    "请按我的错因讲解关键步骤",
                                    "为什么这里不能直接套公式？",
                                    "换一个条件后解题方法会怎样变化？"
                                ).forEach { suggestion ->
                                    OutlinedButton(
                                        onClick = { text = suggestion },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(suggestion) }
                                }
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onRetry = { viewModel.retryChatReply(message.id) }
                    )
                }
            }
            if (attachedImage.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImagePanel(attachedImage, Modifier.size(64.dp))
                    OutlinedButton(onClick = { attachedImage = "" }) {
                        Text("移除附加图片")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Outlined.AttachFile, "附加图片")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("针对这道题提问…") },
                    maxLines = 4
                )
                IconButton(
                    onClick = { send() },
                    enabled = !sending && (text.isNotBlank() || attachedImage.isNotBlank())
                ) {
                    if (sending) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Outlined.Send, "发送", tint = Green)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: QuestionChatMessageEntity,
    onRetry: () -> Unit
) {
    val isUser = message.role == ChatRole.USER.value
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (message.imagePath.isNotBlank()) {
                    ImagePanel(message.imagePath, Modifier.fillMaxWidth().height(150.dp))
                }
                when (message.status) {
                    ChatMessageStatus.PENDING.name -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(" AI 正在结合题目分析…")
                        }
                    }
                    ChatMessageStatus.FAILED.name -> {
                        Text(
                            message.error.ifBlank { "回答失败" },
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(onClick = onRetry) { Text("重试") }
                    }
                    else -> MathText(message.content)
                }
            }
        }
    }
}
