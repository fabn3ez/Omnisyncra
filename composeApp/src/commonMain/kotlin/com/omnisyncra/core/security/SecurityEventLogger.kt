package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Security event types
 */
enum class SecurityEventType {
    AUTHENTICATION_SUCCESS,
    AUTHENTICATION_FAILURE,
    CERTIFICATE_GENERATED,
    CERTIFICATE_VALIDATED,
    CERTIFICATE_EXPIRED,
    CERTIFICATE_REVOKED,
    CERTIFICATE_RENEWED,
    CERTIFICATE_EXPIRING_SOON,
    CERTIFICATE_VALIDATION_FAILED,
    KEY_EXCHANGE_INITIATED,
    KEY_EXCHANGE_COMPLETED,
    KEY_EXCHANGE_FAILED,
    TRUST_ESTABLISHED,
    TRUST_REVOKED,
    BEACON_STARTED,
    BEACON_STOPPED,
    BEACON_DETECTED,
    IDENTITY_REVEALED,
    IDENTITY_REVELATION_FAILED,
    ENCRYPTION_PERFORMED,
    DECRYPTION_PERFORMED,
    ENCRYPTION_FAILED,
    DECRYPTION_FAILED,
    SECURITY_VIOLATION,
    SYSTEM_COMPROMISE_DETECTED,
    ANTI_TRACKING_ACTIVATED,
    SESSION_ESTABLISHED,
    SESSION_TERMINATED,
    CLEANUP_PERFORMED
}

/**
 * Security event severity levels
 */
enum class SecurityEventSeverity {
    INFO,       // Informational events
    WARNING,    // Warning events that may need attention
    ERROR,      // Error events that indicate problems
    CRITICAL    // Critical security events requiring immediate attention
}

/**
 * Security event data
 */
@Serializable
data class SecurityEvent(
    val id: String,
    val type: SecurityEventType,
    val severity: SecurityEventSeverity,
    val timestamp: Long,
    val deviceId: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val relatedDeviceId: String? = null,
    val sessionId: String? = null,
    val errorCode: String? = null,
    val stackTrace: String? = null
)

/**
 * Log rotation configuration
 */
data class LogRotationConfig(
    val maxLogSize: Long = 10 * 1024 * 1024, // 10MB
    val maxLogFiles: Int = 5,
    val rotationInterval: Long = 24 * 60 * 60 * 1000L, // 24 hours
    val compressionEnabled: Boolean = true
)

/**
 * Security event logger interface
 */
interface SecurityEventLogger {
    suspend fun logEvent(
        type: SecurityEventType,
        severity: SecurityEventSeverity,
        message: String,
        details: Map<String, String> = emptyMap(),
        relatedDeviceId: Uuid? = null,
        sessionId: Uuid? = null,
        error: Throwable? = null
    )
    
    suspend fun getEvents(
        startTime: Long? = null,
        endTime: Long? = null,
        types: Set<SecurityEventType>? = null,
        severities: Set<SecurityEventSeverity>? = null,
        limit: Int = 100
    ): List<SecurityEvent>
    
    suspend fun getEventCount(
        startTime: Long? = null,
        endTime: Long? = null,
        types: Set<SecurityEventType>? = null,
        severities: Set<SecurityEventSeverity>? = null
    ): Int
    
    suspend fun clearEvents(olderThan: Long? = null): Int
    suspend fun rotateLog(): Boolean
    suspend fun exportLogs(format: String = "json"): String
    suspend fun setLogLevel(severity: SecurityEventSeverity)
    suspend fun getLogLevel(): SecurityEventSeverity
}

/**
 * Platform-specific log storage
 */
expect class PlatformSecurityLogger() {
    suspend fun writeEvent(event: SecurityEvent): Boolean
    suspend fun readEvents(
        startTime: Long?,
        endTime: Long?,
        types: Set<SecurityEventType>?,
        severities: Set<SecurityEventSeverity>?,
        limit: Int
    ): List<SecurityEvent>
    suspend fun deleteEvents(olderThan: Long?): Int
    suspend fun getLogSize(): Long
    suspend fun rotateLogFiles(): Boolean
    suspend fun compressOldLogs(): Boolean
}

/**
 * Implementation of security event logger with rotation and monitoring
 */
class OmnisyncraSecurityEventLogger(
    private val deviceId: Uuid,
    private val rotationConfig: LogRotationConfig = LogRotationConfig(),
    private val platformLogger: PlatformSecurityLogger = PlatformSecurityLogger()
) : SecurityEventLogger {
    
    private val _logLevel = MutableStateFlow(SecurityEventSeverity.INFO)
    val logLevel: StateFlow<SecurityEventSeverity> = _logLevel.asStateFlow()
    
    private val _recentEvents = MutableStateFlow<List<SecurityEvent>>(emptyList())
    val recentEvents: StateFlow<List<SecurityEvent>> = _recentEvents.asStateFlow()
    
    private var lastRotationTime = Clock.System.now().toEpochMilliseconds()
    
    override suspend fun logEvent(
        type: SecurityEventType,
        severity: SecurityEventSeverity,
        message: String,
        details: Map<String, String>,
        relatedDeviceId: Uuid?,
        sessionId: Uuid?,
        error: Throwable?
    ) {
        // Check if event should be logged based on log level
        if (!shouldLogEvent(severity)) {
            return
        }
        
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val eventId = com.benasher44.uuid.uuid4().toString()
        
        val event = SecurityEvent(
            id = eventId,
            type = type,
            severity = severity,
            timestamp = currentTime,
            deviceId = deviceId.toString(),
            message = message,
            details = details,
            relatedDeviceId = relatedDeviceId?.toString(),
            sessionId = sessionId?.toString(),
            errorCode = error?.let { it::class.simpleName },
            stackTrace = error?.stackTraceToString()
        )
        
        // Write to platform storage
        try {
            platformLogger.writeEvent(event)
            
            // Update recent events in memory
            updateRecentEvents(event)
            
            // Check if log rotation is needed
            checkAndRotateIfNeeded()
            
        } catch (e: Exception) {
            // If logging fails, at least keep in memory
            updateRecentEvents(event.copy(
                details = event.details + ("logging_error" to (e.message ?: "Unknown error"))
            ))
        }
    }
    
    override suspend fun getEvents(
        startTime: Long?,
        endTime: Long?,
        types: Set<SecurityEventType>?,
        severities: Set<SecurityEventSeverity>?,
        limit: Int
    ): List<SecurityEvent> {
        return try {
            platformLogger.readEvents(startTime, endTime, types, severities, limit)
        } catch (e: Exception) {
            // Fallback to recent events in memory
            _recentEvents.value.filter { event ->
                (startTime == null || event.timestamp >= startTime) &&
                (endTime == null || event.timestamp <= endTime) &&
                (types == null || event.type in types) &&
                (severities == null || event.severity in severities)
            }.take(limit)
        }
    }
    
    override suspend fun getEventCount(
        startTime: Long?,
        endTime: Long?,
        types: Set<SecurityEventType>?,
        severities: Set<SecurityEventSeverity>?
    ): Int {
        return getEvents(startTime, endTime, types, severities, Int.MAX_VALUE).size
    }
    
    override suspend fun clearEvents(olderThan: Long?): Int {
        return try {
            val deletedCount = platformLogger.deleteEvents(olderThan)
            
            // Also clear from memory
            if (olderThan != null) {
                val currentEvents = _recentEvents.value
                val filteredEvents = currentEvents.filter { it.timestamp >= olderThan }
                _recentEvents.value = filteredEvents
            } else {
                _recentEvents.value = emptyList()
            }
            
            deletedCount
        } catch (e: Exception) {
            0
        }
    }
    
    override suspend fun rotateLog(): Boolean {
        return try {
            val success = platformLogger.rotateLogFiles()
            if (success) {
                lastRotationTime = Clock.System.now().toEpochMilliseconds()
                
                // Log the rotation event
                logEvent(
                    type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
                    severity = SecurityEventSeverity.INFO,
                    message = "Security log rotation completed",
                    details = mapOf(
                        "rotation_time" to lastRotationTime.toString(),
                        "max_size" to rotationConfig.maxLogSize.toString(),
                        "max_files" to rotationConfig.maxLogFiles.toString()
                    )
                )
            }
            success
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun exportLogs(format: String): String {
        return try {
            val events = getEvents(limit = Int.MAX_VALUE)
            when (format.lowercase()) {
                "json" -> exportAsJson(events)
                "csv" -> exportAsCsv(events)
                "text" -> exportAsText(events)
                else -> exportAsJson(events)
            }
        } catch (e: Exception) {
            "Export failed: ${e.message}"
        }
    }
    
    override suspend fun setLogLevel(severity: SecurityEventSeverity) {
        _logLevel.value = severity
        
        logEvent(
            type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
            severity = SecurityEventSeverity.INFO,
            message = "Security log level changed",
            details = mapOf(
                "new_level" to severity.name,
                "changed_by" to deviceId.toString()
            )
        )
    }
    
    override suspend fun getLogLevel(): SecurityEventSeverity {
        return _logLevel.value
    }
    
    private fun shouldLogEvent(severity: SecurityEventSeverity): Boolean {
        val currentLevel = _logLevel.value
        return when (currentLevel) {
            SecurityEventSeverity.INFO -> true
            SecurityEventSeverity.WARNING -> severity != SecurityEventSeverity.INFO
            SecurityEventSeverity.ERROR -> severity == SecurityEventSeverity.ERROR || severity == SecurityEventSeverity.CRITICAL
            SecurityEventSeverity.CRITICAL -> severity == SecurityEventSeverity.CRITICAL
        }
    }
    
    private fun updateRecentEvents(event: SecurityEvent) {
        val currentEvents = _recentEvents.value.toMutableList()
        currentEvents.add(0, event) // Add to beginning
        
        // Keep only last 50 events in memory
        if (currentEvents.size > 50) {
            _recentEvents.value = currentEvents.take(50)
        } else {
            _recentEvents.value = currentEvents
        }
    }
    
    private suspend fun checkAndRotateIfNeeded() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val timeSinceRotation = currentTime - lastRotationTime
        
        // Check if rotation is needed based on time or size
        val needsRotation = timeSinceRotation >= rotationConfig.rotationInterval ||
                           platformLogger.getLogSize() >= rotationConfig.maxLogSize
        
        if (needsRotation) {
            rotateLog()
            
            // Compress old logs if enabled
            if (rotationConfig.compressionEnabled) {
                platformLogger.compressOldLogs()
            }
        }
    }
    
    private fun exportAsJson(events: List<SecurityEvent>): String {
        // Simple JSON export (in production, use proper JSON serialization)
        val jsonEvents = events.joinToString(",\n") { event ->
            """
            {
                "id": "${event.id}",
                "type": "${event.type}",
                "severity": "${event.severity}",
                "timestamp": ${event.timestamp},
                "deviceId": "${event.deviceId}",
                "message": "${event.message.replace("\"", "\\\"")}"
            }
            """.trimIndent()
        }
        return "[\n$jsonEvents\n]"
    }
    
    private fun exportAsCsv(events: List<SecurityEvent>): String {
        val header = "ID,Type,Severity,Timestamp,DeviceId,Message\n"
        val rows = events.joinToString("\n") { event ->
            "${event.id},${event.type},${event.severity},${event.timestamp},${event.deviceId},\"${event.message.replace("\"", "\\\"")}\""
        }
        return header + rows
    }
    
    private fun exportAsText(events: List<SecurityEvent>): String {
        return events.joinToString("\n\n") { event ->
            """
            Event ID: ${event.id}
            Type: ${event.type}
            Severity: ${event.severity}
            Timestamp: ${event.timestamp}
            Device: ${event.deviceId}
            Message: ${event.message}
            """.trimIndent()
        }
    }
    
    // Convenience methods for common security events
    suspend fun logAuthenticationSuccess(deviceId: Uuid, method: String) {
        logEvent(
            type = SecurityEventType.AUTHENTICATION_SUCCESS,
            severity = SecurityEventSeverity.INFO,
            message = "Authentication successful",
            details = mapOf("method" to method),
            relatedDeviceId = deviceId
        )
    }
    
    suspend fun logAuthenticationFailure(deviceId: Uuid?, reason: String, error: Throwable? = null) {
        logEvent(
            type = SecurityEventType.AUTHENTICATION_FAILURE,
            severity = SecurityEventSeverity.WARNING,
            message = "Authentication failed: $reason",
            details = mapOf("failure_reason" to reason),
            relatedDeviceId = deviceId,
            error = error
        )
    }
    
    suspend fun logSecurityViolation(violation: String, severity: SecurityEventSeverity = SecurityEventSeverity.ERROR) {
        logEvent(
            type = SecurityEventType.SECURITY_VIOLATION,
            severity = severity,
            message = "Security violation detected: $violation",
            details = mapOf("violation_type" to violation)
        )
    }
    
    suspend fun logKeyExchange(sessionId: Uuid, success: Boolean, error: Throwable? = null) {
        logEvent(
            type = if (success) SecurityEventType.KEY_EXCHANGE_COMPLETED else SecurityEventType.KEY_EXCHANGE_FAILED,
            severity = if (success) SecurityEventSeverity.INFO else SecurityEventSeverity.WARNING,
            message = if (success) "Key exchange completed successfully" else "Key exchange failed",
            sessionId = sessionId,
            error = error
        )
    }
}