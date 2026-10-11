package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.editor.ImageEditorScreen
import com.example.ui.home.HomeScreen
import com.example.ui.pdfsettings.PdfSettingsScreen
import com.example.ui.recent.RecentPdfsScreen
import com.example.ui.result.PdfResultScreen
import com.example.ui.scanner.DocumentScannerScreen
import com.example.ui.tools.PdfToolsScreen
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToEditor = { navController.navigate(Screen.Editor.route) },
                    onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
                    onNavigateToTools = { navController.navigate(Screen.Tools.route) },
                    onNavigateToRecent = { navController.navigate(Screen.Recent.route) }
                )
            }

            composable(Screen.Editor.route) {
                ImageEditorScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.Settings.route) {
                PdfSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onGenerationSuccess = {
                        navController.navigate(Screen.Result.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(Screen.Result.route) {
                PdfResultScreen(
                    viewModel = viewModel,
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Scanner.route) {
                DocumentScannerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEditor = {
                        if (!navController.popBackStack(Screen.Editor.route, false)) {
                            navController.navigate(Screen.Editor.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    }
                )
            }

            composable(Screen.Tools.route) {
                PdfToolsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Recent.route) {
                RecentPdfsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
