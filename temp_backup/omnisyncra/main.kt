package com.omnisyncra

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.omnisyncra.di.commonModule
import com.omnisyncra.di.jvmModule
import org.koin.core.context.startKoin

fun main() = application {
    // Initialize Koin DI
    startKoin {
        modules(commonModule, jvmModule)
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Omnisyncra - Desktop Node",
    ) {
        App()
    }
}