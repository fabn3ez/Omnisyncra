package com.omnisyncra.core.security

import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * JavaScript implementation using localStorage
 * Limited by browser storage constraints
 */
actual class PlatformSecurityLogger actual constructor() {
    
    private val storageKey = "omnisyncra_security_logs"
    private val maxStorageEvents = 500 // Limit for localStorage
    private val json = Json { ignoreUnknownKeys = true }
    
    actual suspend fun writeEvent(event: SecurityEvent): Boolean {
        return try {
            val events = getStoredEvents().toMutableList()
            events.add(0, event) // Add to beginning
            
            // Keep only recent events to avoid localStorage limits
            if (events.size > maxStorageEvents) {
                events.removeAt(events.size - 1)
            }
            
            saveEvents(events)
            
            // Also log to browser console
            console.log("Security Event [${event.severity.name}]: ${event.type.name} - ${event.message}")
            
            true
        } catch (e: Exception) {
            console.error("Failed to write security event: ${e.message}")
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
            getStoredEvents().filter { event ->
                (startTime == null || event.timestamp >= startTime) &&
                (endTime == null || event.timestamp <= endTime) &&
                (types == null || event.type in types) &&
                (severities == null || event.severity in severities)
            }.take(limit)
        } catch (e: Exception) {
            console.error("Failed to read security events: ${e.message}")
            emptyList()
        }
    }
    
    actual suspend fun deleteEvents(olderThan: Long?): Int {
        return try {
            val events = getStoredEvents()
            val filteredEvents = if (olderThan == null) {
                emptyList()
            } else {
                events.filter { it.timestamp >= olderThan }
            }
            
            val deletedCount = events.size - filteredEvents.size
            saveEvents(filteredEvents)
            
            console.log("Deleted $deletedCount security events")
            deletedCount
        } catch (e: Exception) {
            console.error("Failed to delete security events: ${e.message}")
            0
        }
    }
    
    actual suspend fun getLogSize(): Long {
        return try {
            val eventsJson = localStorage.getItem(storageKey) ?: "[]"
            eventsJson.length.toLong()
        } catch (e: Exception) {
            0L
        }
    }
    
    actual suspend fun rotateLogFiles(): Boolean {
        return try {
            val events = getStoredEvents()
            
            // Keep only recent events for rotation
            val eventsToKeep = events.take(maxStorageEvents / 2)
            saveEvents(eventsToKeep)
            
            console.log("Security log rotation completed - kept ${eventsToKeep.size} events")
            true
        } catch (e: Exception) {
            console.error("Failed to rotate security logs: ${e.message}")
            false
        }
    }
    
    actual suspend fun compressOldLogs(): Boolean {
        // Browser localStorage doesn't support compression
        // This is a no-op for JS implementation
        console.log("Log compression completed (not supported in browser)")
        return true
    }
    
    private fun getStoredEvents(): List<SecurityEvent> {
        return try {
            val eventsJson = localStorage.getItem(storageKey) ?: "[]"
            json.decodeFromString<List<SecurityEvent>>(eventsJson)
        } catch (e: Exception) {
            console.error("Failed to parse stored security events: ${e.message}")
            emptyList()
        }
    }
    
    private fun saveEvents(events: List<SecurityEvent>) {
        try {
            val eventsJson = json.encodeToString(events)
            localStorage.setItem(storageKey, eventsJson)
        } catch (e: Exception) {
            console.error("Failed to save security events: ${e.message}")
            
            // If storage is full, try to make space by removing old events
            if (e.message?.contains("quota", ignoreCase = true) == true) {
                try {
                    val reducedEvents = events.take(maxStorageEvents / 4)
                    val reducedJson = json.encodeToString(reducedEvents)
                    localStorage.setItem(storageKey, reducedJson)
                    console.log("Reduced security events to ${reducedEvents.size} due to storage quota")
                } catch (e2: Exception) {
                    console.error("Failed to save reduced security events: ${e2.message}")
                }
            }
        }
    }
}