package com.omnisyncra

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.omnisyncra.di.commonModule
import com.omnisyncra.core.platform.JvmPlatform
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() = application {
    // Initialize Koin DI
    startKoin {
        modules(
            commonModule,
            module {
                single { JvmPlatform() }
            }
        )
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Omnisyncra - Desktop Node",
    ) {
        App()
    }
}