package com.hiro.mistakebook.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.hiro.mistakebook.MainViewModel
import com.hiro.mistakebook.data.WordLookupCodec
import com.hiro.mistakebook.data.WordLookupStatus

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isAdd: Boolean = false
)

private val bottomItems = listOf(
    BottomItem("home", "错题本", Icons.Outlined.Home),
    BottomItem("words", "单词本", Icons.Outlined.Translate),
    BottomItem("add-method", "添加", Icons.Outlined.Add, isAdd = true),
    BottomItem("stats", "总结", Icons.Outlined.Assessment),
    BottomItem("settings", "设置", Icons.Outlined.Settings)
)

@Composable
fun MistakeBookApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottom = bottomItems.any { item ->
        backStack?.destination?.hierarchy?.any { it.route == item.route } == true
    }
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    val activePageScan by viewModel.activePageScan.collectAsStateWithLifecycle()
    val batchSplitProgress by viewModel.batchSplitProgress.collectAsStateWithLifecycle()
    val pendingReviewCount by viewModel.pendingReviewCount.collectAsStateWithLifecycle()
    val latestPendingReviewScanId by viewModel.latestPendingReviewScanId.collectAsStateWithLifecycle()
    val wordLookupTasks by viewModel.wordLookupTasks.collectAsStateWithLifecycle()
    val pendingAnalysis = questions.count {
        it.analysisState == "PENDING" || it.analysisState == "PROCESSING"
    }
    val completedQuestions = questions.count {
        it.analysisState == "DONE" || it.analysisState == "MANUAL"
    }
    val batchRemaining = batchSplitProgress?.let {
        (it.total - it.current).coerceAtLeast(0)
    } ?: 0
    val scanRemaining = if (activePageScan != null) 1 else 0
    val visibleWordLookupTasks = wordLookupTasks.filter {
        WordLookupCodec.pagePaths(it.pagePathsJson).isNotEmpty()
    }
    val activeWordLookupCount = visibleWordLookupTasks.count {
        runCatching { WordLookupStatus.valueOf(it.status) }
            .getOrDefault(WordLookupStatus.FAILED).isActive
    }
    val completedWordLookupCount = visibleWordLookupTasks.count {
        it.status == WordLookupStatus.COMPLETED.name
    }
    val completedCount = completedQuestions + (batchSplitProgress?.generated ?: 0) +
        completedWordLookupCount
    val remainingCount = pendingAnalysis + pendingReviewCount + batchRemaining +
        scanRemaining + activeWordLookupCount

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = if (item.isAdd) {
                            route in setOf("add-method", "add", "batch", "page-scan")
                        } else {
                            backStack?.destination?.hierarchy?.any {
                                it.route == item.route
                            } == true
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (item.isAdd) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        shadowElevation = 5.dp,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        androidx.compose.foundation.layout.Box(
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                item.icon,
                                                contentDescription = item.label,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = if (item.isAdd) null else {
                                { Text(item.label) }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBottom) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ProcessingBubble(
                        completed = completedCount,
                        remaining = remainingCount,
                        onClick = { navController.navigate("processing") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenQuestion = { navController.navigate("question/$it") },
                    onOpenRetake = { navController.navigate("retake") }
                )
            }
            composable("add-method") {
                AddMethodScreen(
                    onSingleImport = { navController.navigate("add") },
                    onBatchImport = { navController.navigate("batch") },
                    onWordLookup = { navController.navigate("word-lookup") }
                )
            }
            composable("stats") {
                StatsScreen(viewModel)
            }
            composable("processing") {
                ProcessingHistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenQuestion = { navController.navigate("question/$it") },
                    onReviewScan = { scanId ->
                        navController.navigate("scan-review/$scanId")
                    },
                    onOpenWordLookup = { id ->
                        navController.navigate("word-lookup?taskId=$id")
                    }
                )
            }
            composable("words") {
                WordBookScreen(viewModel)
            }
            composable(
                "word-lookup?taskId={taskId}",
                arguments = listOf(navArgument("taskId") {
                    type = NavType.LongType
                    defaultValue = 0L
                })
            ) { entry ->
                WordLookupScreen(
                    viewModel = viewModel,
                    taskId = entry.arguments?.getLong("taskId")?.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    onFinished = {
                        navController.navigate("words") {
                            popUpTo("add-method") { inclusive = true }
                        }
                    }
                )
            }
            composable("retake") {
                RetakeHomeScreen(
                    viewModel = viewModel,
                    onBuild = { navController.navigate("retake-build") },
                    onOpenSession = { navController.navigate("retake-run/$it") }
                )
            }
            composable("retake-build") {
                RetakeBuilderScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.navigate("retake-run/$id") {
                            popUpTo("retake-build") { inclusive = true }
                        }
                    }
                )
            }
            composable(
                "retake-run/{sessionId}",
                arguments = listOf(navArgument("sessionId") { defaultValue = 0L })
            ) { entry ->
                val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                RetakeRunScreen(
                    viewModel = viewModel,
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(viewModel)
            }
            composable("add") {
                QuestionEditorScreen(
                    viewModel = viewModel,
                    questionId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate("question/$id") {
                            popUpTo("add") { inclusive = true }
                        }
                    }
                )
            }
            composable("batch") {
                BatchImportScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPageScan = { navController.navigate("page-scan") },
                    onFinished = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
            composable("page-scan") {
                FullPageScanScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onViewResults = { scanId ->
                        navController.navigate("scan-review/$scanId")
                    }
                )
            }
            composable(
                "scan-review/{scanId}",
                arguments = listOf(navArgument("scanId") { defaultValue = 0L })
            ) { entry ->
                val scanId = entry.arguments?.getLong("scanId") ?: 0L
                ScanReviewScreen(
                    viewModel = viewModel,
                    scanId = scanId,
                    onBack = { navController.popBackStack() },
                    onFinished = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
            composable(
                "question/{questionId}",
                arguments = listOf(navArgument("questionId") { defaultValue = 0L })
            ) { entry ->
                val id = entry.arguments?.getLong("questionId") ?: 0L
                QuestionDetailScreen(
                    viewModel = viewModel,
                    questionId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("edit/$id") },
                    onChat = { navController.navigate("chat/$id") },
                    onKnowledgePoint = { target ->
                        navController.navigate(
                            "knowledge?subject=${Uri.encode(target.subject)}" +
                                "&book=${Uri.encode(target.book)}" +
                                "&unit=${Uri.encode(target.unit)}" +
                                "&chapter=${Uri.encode(target.chapter)}" +
                                "&kp=${Uri.encode(target.knowledgePoint)}"
                        )
                    }
                )
            }
            composable(
                "edit/{questionId}",
                arguments = listOf(navArgument("questionId") { defaultValue = 0L })
            ) { entry ->
                val id = entry.arguments?.getLong("questionId") ?: 0L
                QuestionEditorScreen(
                    viewModel = viewModel,
                    questionId = id,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.popBackStack()
                    }
                )
            }
            composable(
                "chat/{questionId}",
                arguments = listOf(navArgument("questionId") { defaultValue = 0L })
            ) { entry ->
                val id = entry.arguments?.getLong("questionId") ?: 0L
                QuestionChatScreen(
                    viewModel = viewModel,
                    questionId = id,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "knowledge?subject={subject}&book={book}&unit={unit}&chapter={chapter}&kp={kp}",
                arguments = listOf(
                    navArgument("subject") { defaultValue = "" },
                    navArgument("book") { defaultValue = "" },
                    navArgument("unit") { defaultValue = "" },
                    navArgument("chapter") { defaultValue = "" },
                    navArgument("kp") { defaultValue = "" }
                )
            ) { entry ->
                val target = KnowledgePointTarget(
                    subject = entry.arguments?.getString("subject").orEmpty(),
                    book = entry.arguments?.getString("book").orEmpty(),
                    unit = entry.arguments?.getString("unit").orEmpty(),
                    chapter = entry.arguments?.getString("chapter").orEmpty(),
                    knowledgePoint = entry.arguments?.getString("kp").orEmpty()
                )
                KnowledgePointDirectoryScreen(
                    viewModel = viewModel,
                    target = target,
                    onBack = { navController.popBackStack() },
                    onOpenQuestion = { navController.navigate("question/$it") }
                )
            }
        }
    }
}

@Composable
private fun ProcessingBubble(
    completed: Int,
    remaining: Int,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "完成 $completed",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp
            )
            Text(
                "剩余 $remaining",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp
            )
        }
    }
}
