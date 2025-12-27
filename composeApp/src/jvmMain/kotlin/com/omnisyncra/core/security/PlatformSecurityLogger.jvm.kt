package com.omnisyncra.core.security

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.io.BufferedReader
import java.io.FileReader
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream
import java.io.FileOutputStream
import java.io.FileInputStream

/**
 * JVM implementation using file-based logging with rotation and compression
 */
actual class PlatformSecurityLogger actual constructor() {
    
    private val logDir = File(System.getProperty("user.home"), ".omnisyncra/security-logs").apply { mkdirs() }
    private val currentLogFile = File(logDir, "security.log")
    private val json = Json { ignoreUnknownKeys = true }
    
    actual suspend fun writeEvent(event: SecurityEvent): Boolean {
        return try {
            val eventJson = json.encodeToString(event)
            
            // Append to current log file
            FileWriter(currentLogFile, true).use { writer ->
                writer.appendLine(eventJson)
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun readEvents(
        startTime: Long?,
        endTime: Long?,
        types: Set<SecurityEventType>?,
        severities: Set<SecurityEventSeverity>?,
        limit: Int
    ): List<SecurityEvent> {
        val events = mutableListOf<SecurityEvent>()
        
        try {
            // Read from current log file
            if (currentLogFile.exists()) {
                events.addAll(readEventsFromFile(currentLogFile, startTime, endTime, types, severities))
            }
            
            // Read from rotated log files
            val rotatedFiles = logDir.listFiles { _, name -> 
                name.startsWith("security.log.") && (name.endsWith(".gz") || name.matches(Regex("security\\.log\\.\\d+")))
            }?.sortedByDescending { it.name } ?: emptyArray()
            
            for (file in rotatedFiles) {
                if (events.size >= limit) break
                
                val fileEvents = if (file.name.endsWith(".gz")) {
                    readEventsFromCompressedFile(file, startTime, endTime, types, severities)
                } else {
                    readEventsFromFile(file, startTime, endTime, types, severities)
                }
                
                events.addAll(fileEvents)
            }
            
            // Sort by timestamp (newest first) and apply limit
            return events.sortedByDescending { it.timestamp }.take(limit)
            
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    actual suspend fun deleteEvents(olderThan: Long?): Int {
        var deletedCount = 0
        
        try {
            if (olderThan == null) {
                // Delete all events
                if (currentLogFile.exists()) {
                    val lineCount = currentLogFile.readLines().size
                    currentLogFile.delete()
                    deletedCount += lineCount
                }
                
                // Delete rotated files
                val rotatedFiles = logDir.listFiles { _, name -> 
                    name.startsWith("security.log.") 
                } ?: emptyArray()
                
                for (file in rotatedFiles) {
                    if (file.name.endsWith(".gz")) {
                        deletedCount += countEventsInCompressedFile(file)
                    } else {
                        deletedCount += file.readLines().size
                    }
                    file.delete()
                }
            } else {
                // Delete events older than specified time
                deletedCount += filterLogFile(currentLogFile, olderThan)
                
                // Handle rotated files
                val rotatedFiles = logDir.listFiles { _, name -> 
                    name.startsWith("security.log.") 
                } ?: emptyArray()
                
                for (file in rotatedFiles) {
                    deletedCount += filterLogFile(file, olderThan)
                }
            }
            
        } catch (e: Exception) {
            // Ignore errors in cleanup
        }
        
        return deletedCount
    }
    
    actual suspend fun getLogSize(): Long {
        return try {
            var totalSize = if (currentLogFile.exists()) currentLogFile.length() else 0L
            
            val rotatedFiles = logDir.listFiles { _, name -> 
                name.startsWith("security.log.") 
            } ?: emptyArray()
            
            for (file in rotatedFiles) {
                totalSize += file.length()
            }
            
            totalSize
        } catch (e: Exception) {
            0L
        }
    }
    
    actual suspend fun rotateLogFiles(): Boolean {
        return try {
            if (!currentLogFile.exists() || currentLogFile.length() == 0L) {
                return true
            }
            
            val timestamp = System.currentTimeMillis()
            val rotatedFile = File(logDir, "security.log.$timestamp")
            
            // Move current log to rotated file
            val success = currentLogFile.renameTo(rotatedFile)
            
            if (success) {
                // Clean up old rotated files (keep only last 5)
                val rotatedFiles = logDir.listFiles { _, name -> 
                    name.startsWith("security.log.") && name != rotatedFile.name
                }?.sortedByDescending { it.name } ?: emptyArray()
                
                if (rotatedFiles.size > 4) {
                    for (i in 4 until rotatedFiles.size) {
                        rotatedFiles[i].delete()
                    }
                }
            }
            
            success
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun compressOldLogs(): Boolean {
        return try {
            val rotatedFiles = logDir.listFiles { _, name -> 
                name.startsWith("security.log.") && !name.endsWith(".gz")
            } ?: emptyArray()
            
            for (file in rotatedFiles) {
                val compressedFile = File(logDir, "${file.name}.gz")
                
                GZIPOutputStream(FileOutputStream(compressedFile)).use { gzipOut ->
                    FileInputStream(file).use { fileIn ->
                        fileIn.copyTo(gzipOut)
                    }
                }
                
                // Delete original file after successful compression
                if (compressedFile.exists() && compressedFile.length() > 0) {
                    file.delete()
                }
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun readEventsFromFile(
        file: File,
        startTime: Long?,
        endTime: Long?,
        types: Set<SecurityEventType>?,
        severities: Set<SecurityEventSeverity>?
    ): List<SecurityEvent> {
        val events = mutableListOf<SecurityEvent>()
        
        try {
            BufferedReader(FileReader(file)).use { reader ->
                reader.lineSequence().forEach { line ->
                    try {
                        val event = json.decodeFromString<SecurityEvent>(line)
                        if (matchesFilters(event, startTime, endTime, types, severities)) {
                            events.add(event)
                        }
                    } catch (e: Exception) {
                        // Skip malformed lines
                    }
                }
            }
        } catch (e: Exception) {
            // Return what we have so far
        }
        
        return events
    }
    
    private fun readEventsFromCompressedFile(
        file: File,
        startTime: Long?,
        endTime: Long?,
        types: Set<SecurityEventType>?,
        severities: Set<SecurityEventSeverity>?
    ): List<SecurityEvent> {
        val events = mutableListOf<SecurityEvent>()
        
        try {
            GZIPInputStream(FileInputStream(file)).use { gzipIn ->
                gzipIn.bufferedReader().lineSequence().forEach { line ->
                    try {
                        val event = json.decodeFromString<SecurityEvent>(line)
                        if (matchesFilters(event, startTime, endTime, types, severities)) {
                            events.add(event)
                        }
                    } catch (e: Exception) {
                        // Skip malformed lines
                    }
                }
            }
        } catch (e: Exception) {
            // Return what we have so far
        }
        
        return events
    }
    
    private fun matchesFilters(
        event: SecurityEvent,
        startTime: Long?,
        endTime: Long?,
        types: Set<SecurityEventType>?,
        severities: Set<SecurityEventSeverity>?
    ): Boolean {
        return (startTime == null || event.timestamp >= startTime) &&
               (endTime == null || event.timestamp <= endTime) &&
               (types == null || event.type in types) &&
               (severities == null || event.severity in severities)
    }
    
    private fun filterLogFile(file: File, olderThan: Long): Int {
        if (!file.exists()) return 0
        
        var deletedCount = 0
        val tempFile = File(file.parent, "${file.name}.tmp")
        
        try {
            FileWriter(tempFile).use { writer ->
                BufferedReader(FileReader(file)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        try {
                            val event = json.decodeFromString<SecurityEvent>(line)
                            if (event.timestamp >= olderThan) {
                                writer.appendLine(line)
                            } else {
                                deletedCount++
                            }
                        } catch (e: Exception) {
                            // Keep malformed lines
                            writer.appendLine(line)
                        }
                    }
                }
            }
            
            // Replace original file with filtered file
            file.delete()
            tempFile.renameTo(file)
            
        } catch (e: Exception) {
            tempFile.delete()
        }
        
        return deletedCount
    }
    
    private fun countEventsInCompressedFile(file: File): Int {
        return try {
            var count = 0
            GZIPInputStream(FileInputStream(file)).use { gzipIn ->
                gzipIn.bufferedReader().lineSequence().forEach { _ ->
                    count++
                }
            }
            count
        } catch (e: Exception) {
            0
        }
    }
}