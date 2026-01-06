package com.omnisyncra

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.omnisyncra.ui.screens.ComprehensiveUIScreen
import com.omnisyncra.ui.theme.OmnisyncraTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun MainApp() {
    OmnisyncraTheme {
        ComprehensiveUIScreen()
    }
}