package com.omnisyncra.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Theme configuration
data class OmnisyncraThemeConfig(
    val isDarkMode: Boolean = true,
    val useSystemTheme: Boolean = true,
    val accentColor: Color = Color(0xFF6366F1),
    val highContrast: Boolean = false,
    val fontSize: FontSize = FontSize.MEDIUM,
    val animationsEnabled: Boolean = true
)

enum class FontSize(val scale: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f)
}

// Color schemes
object OmnisyncraColors {
    // Dark theme colors
    val DarkPrimary = Color(0xFF6366F1)
    val DarkSecondary = Color(0xFF8B5CF6)
    val DarkTertiary = Color(0xFF10B981)
    val DarkBackground = Color(0xFF0F0F23)
    val DarkSurface = Color(0xFF1E1E3F)
    val DarkOnPrimary = Color.White
    val DarkOnSecondary = Color.White
    val DarkOnBackground = Color(0xFFE5E7EB)
    val DarkOnSurface = Color(0xFFE5E7EB)
    val DarkError = Color(0xFFEF4444)
    
    // Light theme colors
    val LightPrimary = Color(0xFF4F46E5)
    val LightSecondary = Color(0xFF7C3AED)
    val LightTertiary = Color(0xFF059669)
    val LightBackground = Color(0xFFFAFAFA)
    val LightSurface = Color.White
    val LightOnPrimary = Color.White
    val LightOnSecondary = Color.White
    val LightOnBackground = Color(0xFF1F2937)
    val LightOnSurface = Color(0xFF1F2937)
    val LightError = Color(0xFFDC2626)
    
    // High contrast colors
    val HighContrastPrimary = Color(0xFF0000FF)
    val HighContrastSecondary = Color(0xFF8000FF)
    val HighContrastBackground = Color.Black
    val HighContrastSurface = Color(0xFF1A1A1A)
    val HighContrastOnBackground = Color.White
    val HighContrastOnSurface = Color.White
    
    // Proximity indicator colors
    val ProximityVeryClose = Color(0xFF10B981)
    val ProximityClose = Color(0xFFF59E0B)
    val ProximityMedium = Color(0xFFEF4444)
    val ProximityFar = Color(0xFF6B7280)
    
    // Task status colors
    val TaskPending = Color(0xFF6B7280)
    val TaskRunning = Color(0xFF3B82F6)
    val TaskCompleted = Color(0xFF10B981)
    val TaskFailed = Color(0xFFEF4444)
    
    // Security level colors
    val SecurityTrusted = Color(0xFF10B981)
    val SecurityPending = Color(0xFFF59E0B)
    val SecurityUntrusted = Color(0xFFEF4444)
    val SecurityVerified = Color(0xFF8B5CF6)
}

// Typography system
object OmnisyncraTypography {
    fun getTypography(fontSize: FontSize): Typography {
        val scale = fontSize.scale
        
        return Typography(
            displayLarge = TextStyle(
                fontSize = (57 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (64 * scale).sp
            ),
            displayMedium = TextStyle(
                fontSize = (45 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (52 * scale).sp
            ),
            displaySmall = TextStyle(
                fontSize = (36 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (44 * scale).sp
            ),
            headlineLarge = TextStyle(
                fontSize = (32 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (40 * scale).sp
            ),
            headlineMedium = TextStyle(
                fontSize = (28 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (36 * scale).sp
            ),
            headlineSmall = TextStyle(
                fontSize = (24 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (32 * scale).sp
            ),
            titleLarge = TextStyle(
                fontSize = (22 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (28 * scale).sp
            ),
            titleMedium = TextStyle(
                fontSize = (16 * scale).sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (24 * scale).sp
            ),
            titleSmall = TextStyle(
                fontSize = (14 * scale).sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (20 * scale).sp
            ),
            bodyLarge = TextStyle(
                fontSize = (16 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (24 * scale).sp
            ),
            bodyMedium = TextStyle(
                fontSize = (14 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (20 * scale).sp
            ),
            bodySmall = TextStyle(
                fontSize = (12 * scale).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (16 * scale).sp
            ),
            labelLarge = TextStyle(
                fontSize = (14 * scale).sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (20 * scale).sp
            ),
            labelMedium = TextStyle(
                fontSize = (12 * scale).sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (16 * scale).sp
            ),
            labelSmall = TextStyle(
                fontSize = (11 * scale).sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (16 * scale).sp
            )
        )
    }
}

// Theme provider
@Composable
fun OmnisyncraTheme(
    config: OmnisyncraThemeConfig = OmnisyncraThemeConfig(),
    content: @Composable () -> Unit
) {
    val systemInDarkTheme = isSystemInDarkTheme()
    val isDarkMode = if (config.useSystemTheme) systemInDarkTheme else config.isDarkMode
    
    val colorScheme = when {
        config.highContrast && isDarkMode -> darkColorScheme(
            primary = OmnisyncraColors.HighContrastPrimary,
            secondary = OmnisyncraColors.HighContrastSecondary,
            background = OmnisyncraColors.HighContrastBackground,
            surface = OmnisyncraColors.HighContrastSurface,
            onBackground = OmnisyncraColors.HighContrastOnBackground,
            onSurface = OmnisyncraColors.HighContrastOnSurface
        )
        config.highContrast && !isDarkMode -> lightColorScheme(
            primary = OmnisyncraColors.HighContrastPrimary,
            secondary = OmnisyncraColors.HighContrastSecondary,
            background = Color.White,
            surface = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
        isDarkMode -> darkColorScheme(
            primary = config.accentColor,
            secondary = OmnisyncraColors.DarkSecondary,
            tertiary = OmnisyncraColors.DarkTertiary,
            background = OmnisyncraColors.DarkBackground,
            surface = OmnisyncraColors.DarkSurface,
            onPrimary = OmnisyncraColors.DarkOnPrimary,
            onSecondary = OmnisyncraColors.DarkOnSecondary,
            onBackground = OmnisyncraColors.DarkOnBackground,
            onSurface = OmnisyncraColors.DarkOnSurface,
            error = OmnisyncraColors.DarkError
        )
        else -> lightColorScheme(
            primary = config.accentColor,
            secondary = OmnisyncraColors.LightSecondary,
            tertiary = OmnisyncraColors.LightTertiary,
            background = OmnisyncraColors.LightBackground,
            surface = OmnisyncraColors.LightSurface,
            onPrimary = OmnisyncraColors.LightOnPrimary,
            onSecondary = OmnisyncraColors.LightOnSecondary,
            onBackground = OmnisyncraColors.LightOnBackground,
            onSurface = OmnisyncraColors.LightOnSurface,
            error = OmnisyncraColors.LightError
        )
    }
    
    val typography = OmnisyncraTypography.getTypography(config.fontSize)
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

// Theme state management
@Composable
fun rememberThemeState(
    initialConfig: OmnisyncraThemeConfig = OmnisyncraThemeConfig()
): ThemeState {
    return remember { ThemeState(initialConfig) }
}

class ThemeState(initialConfig: OmnisyncraThemeConfig) {
    var config by mutableStateOf(initialConfig)
        private set
    
    fun toggleDarkMode() {
        config = config.copy(isDarkMode = !config.isDarkMode)
    }
    
    fun setAccentColor(color: Color) {
        config = config.copy(accentColor = color)
    }
    
    fun toggleHighContrast() {
        config = config.copy(highContrast = !config.highContrast)
    }
    
    fun setFontSize(fontSize: FontSize) {
        config = config.copy(fontSize = fontSize)
    }
    
    fun toggleSystemTheme() {
        config = config.copy(useSystemTheme = !config.useSystemTheme)
    }
    
    fun toggleAnimations() {
        config = config.copy(animationsEnabled = !config.animationsEnabled)
    }
}

// Semantic color helpers
@Composable
fun getProximityColor(distance: com.omnisyncra.core.domain.ProximityDistance): Color {
    return when (distance) {
        com.omnisyncra.core.domain.ProximityDistance.VERY_CLOSE -> OmnisyncraColors.ProximityVeryClose
        com.omnisyncra.core.domain.ProximityDistance.CLOSE -> OmnisyncraColors.ProximityClose
        com.omnisyncra.core.domain.ProximityDistance.MEDIUM -> OmnisyncraColors.ProximityMedium
        com.omnisyncra.core.domain.ProximityDistance.FAR -> OmnisyncraColors.ProximityFar
    }
}

@Composable
fun getTaskStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "pending" -> OmnisyncraColors.TaskPending
        "running" -> OmnisyncraColors.TaskRunning
        "completed" -> OmnisyncraColors.TaskCompleted
        "failed" -> OmnisyncraColors.TaskFailed
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun getSecurityLevelColor(trustLevel: com.omnisyncra.core.security.TrustLevel): Color {
    return when (trustLevel) {
        com.omnisyncra.core.security.TrustLevel.TRUSTED -> OmnisyncraColors.SecurityTrusted
        com.omnisyncra.core.security.TrustLevel.PENDING -> OmnisyncraColors.SecurityPending
        com.omnisyncra.core.security.TrustLevel.UNTRUSTED -> OmnisyncraColors.SecurityUntrusted
        com.omnisyncra.core.security.TrustLevel.VERIFIED -> OmnisyncraColors.SecurityVerified
    }
}