package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Editor : Screen("editor")
    object Settings : Screen("pdf_settings")
    object Result : Screen("result")
    object Scanner : Screen("scanner")
    object Tools : Screen("tools")
    object Recent : Screen("recent")
}
