package com.omnisyncra.di

actual fun getProperty(key: String, defaultValue: String): String {
    return try {
        // For WASM, try to get from JavaScript environment
        val jsValue = js("(typeof globalThis !== 'undefined' && globalThis[key]) || (typeof process !== 'undefined' && process.env && process.env[key]) || undefined")
        if (jsValue != null && jsValue != js("undefined")) {
            jsValue.toString()
        } else {
            defaultValue
        }
    } catch (e: Exception) {
        defaultValue
    }
}