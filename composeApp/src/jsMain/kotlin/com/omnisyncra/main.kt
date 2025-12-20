package com.omnisyncra

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.omnisyncra.di.commonModule
import com.omnisyncra.core.platform.JsPlatform
import org.koin.core.context.startKoin
import org.koin.dsl.module

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Initialize Koin DI
    startKoin {
        modules(
            commonModule,
            module {
                single { JsPlatform() }
            }
        )
    }
    
    ComposeViewport {
        App()
    }
}