package com.omnisyncra

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.omnisyncra.di.commonModule
import org.koin.core.context.startKoin

fun main() = application {
    // Initialize Koin DI
    startKoin {
        modules(commonModule)
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Omnisyncra KMP - Phase 11: Privacy-First AI Integration",
    ) {
        MainApp()
    }
}