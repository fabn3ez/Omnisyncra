package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

// Audit event types
enum class AuditEventType {
    DEVICE_CONNECTED,
    DEVICE_DISCONNECTED,
    AUTHENTICATION_SUCCESS,
    AUTHENTICATION_FAILURE,
    KEY_EXCHANGE_INITIATED,
    KEY_EXCHANGE_COMPLETED,
    DATA_ENCRYPTED,
    DATA_DECRYPTED,
    COMPUTE_TASK_SUBMITTED,
    COMPUTE_TASK_COMPLETED,
    PERMISSION_GRANTED,
    PERMISSION_DENIED,
    SECURITY_VIOLATION,
    SESSION_CREATED,
    SESSION_EXPIRED
}

// Audit severity levels
enum class AuditSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

// Audit log entry
@Serializable
data class AuditLogEntry(
    val id: String = com.benasher44.uuid.uuid4().toString(),
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val eventType: AuditEventType,
    val severity: AuditSeverity,
    val sourceDevice: String, // UUID as string for serialization
    val targetDevice: String? = null,
    val userId: String? = null,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val ipAddress: String? = null,
    val userAgent: String? = null
)
// Audit log storage interface
interface AuditLogStorage {
    suspend fun store(entry: AuditLogEntry)
    suspend fun query(
        startTime: Long? = null,
        endTime: Long? = null,
        eventTypes: Set<AuditEventType>? = null,
        severity: AuditSeverity? = null,
        sourceDevice: Uuid? = null,
        limit: Int = 100
    ): List<AuditLogEntry>
    suspend fun count(
        startTime: Long? = null,
        endTime: Long? = null,
        eventTypes: Set<AuditEventType>? = null,
        severity: AuditSeverity? = null
    ): Long
    suspend fun cleanup(olderThan: Long)
}

// In-memory audit log storage (for demo/testing)
class InMemoryAuditLogStorage : AuditLogStorage {
    private val entries = mutableListOf<AuditLogEntry>()
    private val mutex = Mutex()
    
    override suspend fun store(entry: AuditLogEntry) {
        mutex.withLock {
            entries.add(entry)
            // Keep only last 10000 entries to prevent memory issues
            if (entries.size > 10000) {
                entries.removeFirst()
            }
        }
    }
    
    override suspend fun query(
        startTime: Long?,
        endTime: Long?,
        eventTypes: Set<AuditEventType>?,
        severity: AuditSeverity?,
        sourceDevice: Uuid?,
        limit: Int
    ): List<AuditLogEntry> {
        return mutex.withLock {
            entries.asSequence()
                .filter { entry ->
                    (startTime == null || entry.timestamp >= startTime) &&
                    (endTime == null || entry.timestamp <= endTime) &&
                    (eventTypes == null || entry.eventType in eventTypes) &&
                    (severity == null || entry.severity == severity) &&
                    (sourceDevice == null || entry.sourceDevice == sourceDevice.toString())
                }
                .sortedByDescending { it.timestamp }
                .take(limit)
                .toList()
        }
    }
    
    override suspend fun count(
        startTime: Long?,
        endTime: Long?,
        eventTypes: Set<AuditEventType>?,
        severity: AuditSeverity?
    ): Long {
        return mutex.withLock {
            entries.count { entry ->
                (startTime == null || entry.timestamp >= startTime) &&
                (endTime == null || entry.timestamp <= endTime) &&
                (eventTypes == null || entry.eventType in eventTypes) &&
                (severity == null || entry.severity == severity)
            }.toLong()
        }
    }
    
    override suspend fun cleanup(olderThan: Long) {
        mutex.withLock {
            entries.removeAll { it.timestamp < olderThan }
        }
    }
}
// Audit logging service
class AuditLogService(
    private val nodeId: Uuid,
    private val storage: AuditLogStorage = InMemoryAuditLogStorage()
) {
    suspend fun logDeviceConnection(deviceId: Uuid, success: Boolean) {
        val entry = AuditLogEntry(
            eventType = if (success) AuditEventType.DEVICE_CONNECTED else AuditEventType.DEVICE_DISCONNECTED,
            severity = AuditSeverity.INFO,
            sourceDevice = nodeId.toString(),
            targetDevice = deviceId.toString(),
            message = if (success) "Device connected successfully" else "Device disconnected",
            details = mapOf("deviceId" to deviceId.toString())
        )
        storage.store(entry)
    }
    
    suspend fun logAuthentication(deviceId: Uuid, success: Boolean, reason: String? = null) {
        val entry = AuditLogEntry(
            eventType = if (success) AuditEventType.AUTHENTICATION_SUCCESS else AuditEventType.AUTHENTICATION_FAILURE,
            severity = if (success) AuditSeverity.INFO else AuditSeverity.WARNING,
            sourceDevice = nodeId.toString(),
            targetDevice = deviceId.toString(),
            message = if (success) "Authentication successful" else "Authentication failed: ${reason ?: "Unknown"}",
            details = buildMap {
                put("deviceId", deviceId.toString())
                if (reason != null) put("reason", reason)
            }
        )
        storage.store(entry)
    }
    
    suspend fun logKeyExchange(deviceId: Uuid, initiated: Boolean, success: Boolean) {
        val entry = AuditLogEntry(
            eventType = if (initiated) AuditEventType.KEY_EXCHANGE_INITIATED else AuditEventType.KEY_EXCHANGE_COMPLETED,
            severity = if (success) AuditSeverity.INFO else AuditSeverity.WARNING,
            sourceDevice = nodeId.toString(),
            targetDevice = deviceId.toString(),
            message = when {
                initiated && success -> "Key exchange initiated"
                initiated && !success -> "Key exchange initiation failed"
                !initiated && success -> "Key exchange completed successfully"
                else -> "Key exchange failed"
            },
            details = mapOf(
                "deviceId" to deviceId.toString(),
                "success" to success.toString()
            )
        )
        storage.store(entry)
    }
    
    suspend fun logDataOperation(operation: String, deviceId: Uuid?, dataSize: Int) {
        val eventType = when (operation.lowercase()) {
            "encrypt" -> AuditEventType.DATA_ENCRYPTED
            "decrypt" -> AuditEventType.DATA_DECRYPTED
            else -> AuditEventType.DATA_ENCRYPTED
        }
        
        val entry = AuditLogEntry(
            eventType = eventType,
            severity = AuditSeverity.INFO,
            sourceDevice = nodeId.toString(),
            targetDevice = deviceId?.toString(),
            message = "Data $operation operation completed",
            details = mapOf(
                "operation" to operation,
                "dataSize" to dataSize.toString()
            )
        )
        storage.store(entry)
    }
    
    suspend fun logComputeTask(taskId: String, deviceId: Uuid?, completed: Boolean) {
        val entry = AuditLogEntry(
            eventType = if (completed) AuditEventType.COMPUTE_TASK_COMPLETED else AuditEventType.COMPUTE_TASK_SUBMITTED,
            severity = AuditSeverity.INFO,
            sourceDevice = nodeId.toString(),
            targetDevice = deviceId?.toString(),
            message = if (completed) "Compute task completed" else "Compute task submitted",
            details = mapOf(
                "taskId" to taskId,
                "completed" to completed.toString()
            )
        )
        storage.store(entry)
    }
    
    suspend fun logPermission(deviceId: Uuid, permission: String, granted: Boolean, reason: String? = null) {
        val entry = AuditLogEntry(
            eventType = if (granted) AuditEventType.PERMISSION_GRANTED else AuditEventType.PERMISSION_DENIED,
            severity = if (granted) AuditSeverity.INFO else AuditSeverity.WARNING,
            sourceDevice = nodeId.toString(),
            targetDevice = deviceId.toString(),
            message = if (granted) "Permission granted: $permission" else "Permission denied: $permission",
            details = buildMap {
                put("permission", permission)
                put("granted", granted.toString())
                if (reason != null) put("reason", reason)
            }
        )
        storage.store(entry)
    }
    
    suspend fun logSecurityViolation(deviceId: Uuid?, violation: String, severity: AuditSeverity = AuditSeverity.ERROR) {
        val entry = AuditLogEntry(
            eventType = AuditEventType.SECURITY_VIOLATION,
            severity = severity,
            sourceDevice = nodeId.toString(),
            targetDevice = deviceId?.toString(),
            message = "Security violation detected: $violation",
            details = mapOf("violation" to violation)
        )
        storage.store(entry)
    }
    
    suspend fun logSessionEvent(deviceId: Uuid, created: Boolean) {
        val entry = AuditLogEntry(
            eventType = if (created) AuditEventType.SESSION_CREATED else AuditEventType.SESSION_EXPIRED,
            severity = AuditSeverity.INFO,
            sourceDevice = nodeId.toString(),
            targetDevice = deviceId.toString(),
            message = if (created) "Session created" else "Session expired",
            details = mapOf("deviceId" to deviceId.toString())
        )
        storage.store(entry)
    }
    
    suspend fun getRecentEvents(limit: Int = 50): List<AuditLogEntry> {
        return storage.query(limit = limit)
    }
    
    suspend fun getEventsByType(eventType: AuditEventType, limit: Int = 50): List<AuditLogEntry> {
        return storage.query(eventTypes = setOf(eventType), limit = limit)
    }
    
    suspend fun getSecurityEvents(limit: Int = 50): List<AuditLogEntry> {
        val securityEventTypes = setOf(
            AuditEventType.AUTHENTICATION_FAILURE,
            AuditEventType.PERMISSION_DENIED,
            AuditEventType.SECURITY_VIOLATION
        )
        return storage.query(eventTypes = securityEventTypes, limit = limit)
    }
    
    suspend fun getEventStats(timeRangeMs: Long = 86400_000L): AuditStats {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val startTime = currentTime - timeRangeMs
        
        val allEvents = storage.query(startTime = startTime)
        
        return AuditStats(
            totalEvents = allEvents.size,
            eventsByType = allEvents.groupBy { it.eventType }.mapValues { it.value.size },
            eventsBySeverity = allEvents.groupBy { it.severity }.mapValues { it.value.size },
            securityViolations = allEvents.count { it.eventType == AuditEventType.SECURITY_VIOLATION },
            authenticationFailures = allEvents.count { it.eventType == AuditEventType.AUTHENTICATION_FAILURE },
            timeRangeMs = timeRangeMs
        )
    }
    
    suspend fun cleanup(retentionDays: Int = 30) {
        val cutoffTime = Clock.System.now().toEpochMilliseconds() - (retentionDays * 24 * 60 * 60 * 1000L)
        storage.cleanup(cutoffTime)
    }
}

data class AuditStats(
    val totalEvents: Int,
    val eventsByType: Map<AuditEventType, Int>,
    val eventsBySeverity: Map<AuditSeverity, Int>,
    val securityViolations: Int,
    val authenticationFailures: Int,
    val timeRangeMs: Long
)