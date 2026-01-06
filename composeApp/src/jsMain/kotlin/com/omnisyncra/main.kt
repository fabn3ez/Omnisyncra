package com.omnisyncra

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.omnisyncra.di.allModules
import kotlinx.browser.document
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Initialize Koin DI
    startKoin {
        modules(allModules)
    }
    
    ComposeViewport(document.body!!) {
        MainApp()
    }
}