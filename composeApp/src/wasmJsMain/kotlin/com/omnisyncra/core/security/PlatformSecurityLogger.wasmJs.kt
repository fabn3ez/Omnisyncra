package com.omnisyncra.core.security

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * WASM implementation using in-memory storage with console logging
 * Limited by WASM runtime constraints
 */
actual class PlatformSecurityLogger actual constructor() {
    
    private val events = mutableListOf<SecurityEvent>()
    private val maxMemoryEvents = 200 // Limit for memory storage
    private val json = Json { ignoreUnknownKeys = true }
    
    actual suspend fun writeEvent(event: SecurityEvent): Boolean {
        return try {
            // Add to memory storage
            events.add(0, event) // Add to beginning
            
            // Keep only recent events to avoid memory issues
            if (events.size > maxMemoryEvents) {
                events.removeAt(events.size - 1)
            }
            
            // Log to console for debugging
            println("Security Event [${event.severity.name}]: ${event.type.name} - ${event.message}")
            
            true
        } catch (e: Exception) {
            println("Failed to write security event: ${e.message}")
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
        return try {
            events.filter { event ->
                (startTime == null || event.timestamp >= startTime) &&
                (endTime == null || event.timestamp <= endTime) &&
                (types == null || event.type in types) &&
                (severities == null || event.severity in severities)
            }.take(limit)
        } catch (e: Exception) {
            println("Failed to read security events: ${e.message}")
            emptyList()
        }
    }
    
    actual suspend fun deleteEvents(olderThan: Long?): Int {
        return try {
            val originalSize = events.size
            
            if (olderThan == null) {
                events.clear()
            } else {
                events.removeAll { it.timestamp < olderThan }
            }
            
            val deletedCount = originalSize - events.size
            println("Deleted $deletedCount security events")
            deletedCount
        } catch (e: Exception) {
            println("Failed to delete security events: ${e.message}")
            0
        }
    }
    
    actual suspend fun getLogSize(): Long {
        return try {
            // Estimate size based on JSON serialization
            val eventsJson = json.encodeToString(events)
            eventsJson.length.toLong()
        } catch (e: Exception) {
            0L
        }
    }
    
    actual suspend fun rotateLogFiles(): Boolean {
        return try {
            // Keep only recent events for rotation
            val eventsToKeep = events.take(maxMemoryEvents / 2)
            events.clear()
            events.addAll(eventsToKeep)
            
            println("Security log rotation completed - kept ${eventsToKeep.size} events")
            true
        } catch (e: Exception) {
            println("Failed to rotate security logs: ${e.message}")
            false
        }
    }
    
    actual suspend fun compressOldLogs(): Boolean {
        // WASM in-memory storage doesn't support compression
        // This is a no-op for WASM implementation
        println("Log compression completed (not supported in WASM)")
        return true
    }
}