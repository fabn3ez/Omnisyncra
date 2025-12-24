package com.omnisyncra

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Omnisyncra KMP - Phase 7: Dynamic UI Morphing & Transitions",
    ) {
        MainApp()
    }
}