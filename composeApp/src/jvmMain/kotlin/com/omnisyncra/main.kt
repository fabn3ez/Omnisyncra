package com.omnisyncra

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.omnisyncra.di.allModules
import org.koin.core.context.startKoin

fun main() = application {
    // Initialize Koin DI
    startKoin {
        modules(allModules)
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Omnisyncra - Privacy-First Distributed Computing",
    ) {
        MainApp()
    }
}