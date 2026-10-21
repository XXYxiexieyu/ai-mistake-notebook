package com.hiro.mistakebook.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hiro.mistakebook.MainViewModel

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomItems = listOf(
    BottomItem("home", "错题本", Icons.Outlined.Home),
    BottomItem("stats", "总结", Icons.Outlined.Assessment),
    BottomItem("retake", "重考", Icons.Outlined.Refresh),
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = backStack?.destination?.hierarchy?.any {
                            it.route == item.route
                        } == true
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
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == "home") {
                FloatingActionButton(
                    onClick = { navController.navigate("add") }
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加错题")
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
                    onAdd = { navController.navigate("add") },
                    onBatch = { navController.navigate("batch") },
                    onScan = { navController.navigate("page-scan") },
                    onOpenRetake = { navController.navigate("retake") }
                )
            }
            composable("stats") {
                StatsScreen(viewModel)
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
                    onViewResults = {
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
                    onChat = { navController.navigate("chat/$id") }
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
        }
    }
}
