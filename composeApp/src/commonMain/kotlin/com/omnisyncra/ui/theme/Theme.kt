package com.omnisyncra.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium Dark Theme Colors
object OmnisyncraColors {
    val PrimaryGlow = Color(0xFF6366F1)
    val SecondaryGlow = Color(0xFF8B5CF6)
    val AccentGlow = Color(0xFF10B981)
    val SuccessGlow = Color(0xFF10B981)
    val WarningGlow = Color(0xFFF59E0B)
    val ErrorGlow = Color(0xFFEF4444)
    val SurfaceGlass = Color(0x1A1E1E3F)
    val BackgroundDark = Color(0xFF0A0A1A)
    val BackgroundDeep = Color(0xFF0F0F23)
    val BackgroundMid = Color(0xFF1A1A2E)
}

@Composable
fun OmnisyncraTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = OmnisyncraColors.PrimaryGlow,
        secondary = OmnisyncraColors.SecondaryGlow,
        tertiary = OmnisyncraColors.AccentGlow,
        background = OmnisyncraColors.BackgroundDeep,
        surface = OmnisyncraColors.SurfaceGlass,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}