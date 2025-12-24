package com.omnisyncra.ui.gestures

actual class HapticFeedback {
    actual fun lightImpact() {
        // WASM haptic feedback - similar to JS but with WASM-specific APIs
        println("Light haptic feedback (WASM)")
    }
    
    actual fun mediumImpact() {
        println("Medium haptic feedback (WASM)")
    }
    
    actual fun heavyImpact() {
        println("Heavy haptic feedback (WASM)")
    }
    
    actual fun selectionChanged() {
        println("Selection changed haptic feedback (WASM)")
    }
}