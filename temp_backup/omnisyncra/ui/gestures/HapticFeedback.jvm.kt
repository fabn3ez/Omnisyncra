package com.omnisyncra.ui.gestures

actual class HapticFeedback {
    actual fun lightImpact() {
        // Desktop haptic feedback could be implemented with:
        // - System beep
        // - External haptic devices
        // - Audio feedback
        println("Light haptic feedback")
    }
    
    actual fun mediumImpact() {
        println("Medium haptic feedback")
    }
    
    actual fun heavyImpact() {
        println("Heavy haptic feedback")
    }
    
    actual fun selectionChanged() {
        println("Selection changed haptic feedback")
    }
}