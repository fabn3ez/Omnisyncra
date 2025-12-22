package com.omnisyncra.core.ui.accessibility

import androidx.compose.runtime.Composable

actual class AccessibilityManager {
    actual fun announce(message: String) {
        // Web Speech API announcement implementation
        try {
            js("window.speechSynthesis.speak(new SpeechSynthesisUtterance(message))")
        } catch (e: Exception) {
            console.log("Accessibility Announcement: $message")
        }
    }
    
    actual fun isScreenReaderEnabled(): Boolean {
        // Check for screen reader indicators in browser
        return js("window.navigator.userAgent.includes('NVDA') || window.navigator.userAgent.includes('JAWS')") as Boolean
    }
    
    actual fun isHighContrastEnabled(): Boolean {
        // Check browser/OS high contrast settings
        return js("window.matchMedia('(prefers-contrast: high)').matches") as Boolean
    }
    
    actual fun isLargeTextEnabled(): Boolean {
        // Check browser font size settings
        return false // Simplified implementation
    }
    
    actual fun isReducedMotionEnabled(): Boolean {
        // Check browser reduced motion preference
        return js("window.matchMedia('(prefers-reduced-motion: reduce)').matches") as Boolean
    }
}

@Composable
actual fun LocalAccessibilityManager.Companion.current: AccessibilityManager? {
    return AccessibilityManager()
}