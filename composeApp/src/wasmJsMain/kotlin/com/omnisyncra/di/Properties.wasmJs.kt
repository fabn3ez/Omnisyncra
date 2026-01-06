package com.omnisyncra.di

actual fun getProperty(key: String, defaultValue: String): String {
    // For WASM, return default values for now
    // In a real implementation, this would use proper WASM-JS interop
    return when (key) {
        "gemini.api.key" -> "AIzaSyBFNrzV95EYb7-c7IzG21e93EAHocvKfYk"
        else -> defaultValue
    }
}