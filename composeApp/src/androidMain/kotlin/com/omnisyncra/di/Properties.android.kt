package com.omnisyncra.di

import android.content.Context
import java.util.Properties
import java.io.IOException

actual fun getProperty(key: String, defaultValue: String): String {
    return try {
        // Try to load from assets/local.properties
        val context = getApplicationContext()
        val inputStream = context.assets.open("local.properties")
        val props = Properties()
        props.load(inputStream)
        props.getProperty(key, defaultValue)
    } catch (e: IOException) {
        // Fallback to default value
        defaultValue
    }
}

// This would need to be provided by the Android application
private fun getApplicationContext(): Context {
    // In a real implementation, this would be injected or obtained from Application
    throw NotImplementedError("Application context not available")
}