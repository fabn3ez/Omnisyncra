package com.omnisyncra

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.omnisyncra.di.commonModule
import com.omnisyncra.di.wasmModule
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Initialize Koin DI
    startKoin {
        modules(commonModule, wasmModule)
    }
    
    ComposeViewport {
        App()
    }
}