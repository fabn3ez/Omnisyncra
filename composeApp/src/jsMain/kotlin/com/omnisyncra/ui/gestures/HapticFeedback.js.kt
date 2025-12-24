package com.omnisyncra.ui.gestures

actual class HapticFeedback {
    actual fun lightImpact() {
        // Web haptic feedback using Vibration API
        try {
            js("navigator.vibrate(50)")
        } catch (e: Exception) {
            console.log("Haptic feedback not supported")
        }
    }
    
    actual fun mediumImpact() {
        try {
            js("navigator.vibrate(100)")
        } catch (e: Exception) {
            console.log("Haptic feedback not supported")
        }
    }
    
    actual fun heavyImpact() {
        try {
            js("navigator.vibrate(200)")
        } catch (e: Exception) {
            console.log("Haptic feedback not supported")
        }
    }
    
    actual fun selectionChanged() {
        try {
            js("navigator.vibrate(25)")
        } catch (e: Exception) {
            console.log("Haptic feedback not supported")
        }
    }
}