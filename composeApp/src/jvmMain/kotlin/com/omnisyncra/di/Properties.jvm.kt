package com.omnisyncra.di

import java.util.Properties
import java.io.FileInputStream
import java.io.File

actual fun getProperty(key: String, defaultValue: String): String {
    return try {
        // Try to load from local.properties file
        val localPropsFile = File("local.properties")
        if (localPropsFile.exists()) {
            val props = Properties()
            props.load(FileInputStream(localPropsFile))
            props.getProperty(key, defaultValue)
        } else {
            // Fallback to system properties
            System.getProperty(key, defaultValue)
        }
    } catch (e: Exception) {
        defaultValue
    }
}