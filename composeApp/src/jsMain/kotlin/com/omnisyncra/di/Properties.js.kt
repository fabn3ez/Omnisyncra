package com.omnisyncra.di

actual fun getProperty(key: String, defaultValue: String): String {
    return try {
        // Try to get from environment variables or localStorage
        val envValue = js("(typeof process !== 'undefined' && process.env) ? process.env[key] : undefined") as? String
        if (envValue != null && envValue != "undefined") {
            envValue
        } else {
            // Try localStorage for browser environment
            val localStorage = js("(typeof localStorage !== 'undefined') ? localStorage : null")
            if (localStorage != null) {
                val storedValue = js("localStorage.getItem(key)") as? String
                storedValue ?: defaultValue
            } else {
                defaultValue
            }
        }
    } catch (e: Exception) {
        defaultValue
    }
}