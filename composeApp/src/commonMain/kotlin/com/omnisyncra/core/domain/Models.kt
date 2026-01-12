package com.omnisyncra.core.domain

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import com.omnisyncra.core.platform.TimeUtils

/**
 * Core domain models for Omnisyncra
 */

@Serializable
enum class ComputePower {
    LOW, MEDIUM, HIGH, EXTREME
}

@Serializable
enum class NetworkCapability {
    OFFLINE, LIMITED, FULL
}

@Serializable
data class DeviceCapabilities(
    val computePower: ComputePower = ComputePower.MEDIUM,
    val networkCapability: NetworkCapability = NetworkCapability.FULL,
    val maxConcurrentTasks: Int = 4,
    val availableMemoryMB: Int = 4096,
    // Extended capabilities for enhanced device discovery
    val canCompute: Boolean = true,
    val canStore: Boolean = true,
    val canDisplay: Boolean = true,
    val canInput: Boolean = true, // Added missing field
    val canNetwork: Boolean = true,
    val processingPower: ProcessingPower = ProcessingPower.MEDIUM, // Changed type
    val memoryCapacity: MemoryCapacity = MemoryCapacity.MEDIUM, // Changed type
    val storageCapacity: StorageCapacity = StorageCapacity.MEDIUM, // Changed type
    val batteryCapacity: BatteryCapacity = BatteryCapacity.UNLIMITED, // Added missing field
    val networkBandwidth: Long = 100L * 1024 * 1024, // bytes per second
    val batteryLevel: Float? = null // 0.0 to 1.0, null if not applicable
)

// Enhanced presence information for real-time device status
@Serializable
data class PresenceInfo(
    val status: PresenceStatus = PresenceStatus.ONLINE,
    val lastSeen: Long = TimeUtils.currentTimeMillis(),
    val batteryLevel: Float? = null, // 0.0 to 1.0, null if not applicable
    val currentLoad: Float = 0.0f, // 0.0 to 1.0
    val availableForTasks: Boolean = true,
    val cpuUsage: Float = 0.0f, // 0.0 to 1.0
    val memoryUsage: Float = 0.0f, // 0.0 to 1.0
    val networkBandwidth: Long = 0L, // bytes per second
    val storageInfo: StorageInfo? = null,
    val capabilities: Map<String, String> = emptyMap()
)

@Serializable
enum class PresenceStatus {
    ONLINE, BUSY, IDLE, OFFLINE, AWAY
}

@Serializable
data class StorageInfo(
    val totalSpace: Long, // bytes
    val availableSpace: Long, // bytes
    val usedSpace: Long // bytes
)

// Connection session history for tracking device interactions
@Serializable
data class ConnectionSession(
    val sessionId: String,
    val deviceId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Long = 0L, // milliseconds
    val messagesExchanged: Int = 0,
    val averageLatency: Float = 0.0f, // milliseconds
    val connectionQuality: ConnectionQuality = ConnectionQuality.UNKNOWN
)

@Serializable
enum class ConnectionQuality {
    EXCELLENT, GOOD, FAIR, POOR, UNKNOWN
}

// Device status lifecycle management
@Serializable
data class DeviceStatusHistory(
    val deviceId: String,
    val statusChanges: List<StatusChange> = emptyList(),
    val connectionSessions: List<ConnectionSession> = emptyList(),
    val lastPresenceUpdate: Long = 0L
)

@Serializable
data class StatusChange(
    val timestamp: Long,
    val fromStatus: PresenceStatus,
    val toStatus: PresenceStatus,
    val reason: String? = null
)

// Connection session analytics for comprehensive tracking
@Serializable
data class ConnectionAnalytics(
    val deviceId: String,
    val totalSessions: Int = 0,
    val totalConnectionTime: Long = 0L, // milliseconds
    val totalMessagesExchanged: Int = 0,
    val averageLatency: Float = 0.0f, // milliseconds
    val lastConnectionTime: Long? = null,
    val connectionQualityDistribution: Map<ConnectionQuality, Int> = emptyMap(),
    val averageSessionDuration: Long = 0L, // milliseconds
    val reliabilityScore: Float = 0.0f // 0.0 to 1.0
) {
    val averageSessionDurationCalculated: Long
        get() = if (totalSessions > 0) totalConnectionTime / totalSessions else 0L
    
    val messagesPerSession: Float
        get() = if (totalSessions > 0) totalMessagesExchanged.toFloat() / totalSessions else 0.0f
    
    val mostCommonQuality: ConnectionQuality?
        get() = connectionQualityDistribution.maxByOrNull { it.value }?.key
}
@Serializable
data class DeviceLifecycleInfo(
    val device: NetworkDevice,
    val totalConnections: Int,
    val totalConnectionTime: Long, // milliseconds
    val averageConnectionDuration: Long, // milliseconds
    val lastConnectionTime: Long?, // timestamp
    val statusChangeCount: Int,
    val mostCommonStatus: PresenceStatus?,
    val reliabilityScore: Float // 0.0 to 1.0
)

@Serializable
data class Device(
    @Contextual val id: Uuid,
    val name: String,
    val capabilities: DeviceCapabilities,
    val isOnline: Boolean = true,
    val lastSeen: Long = TimeUtils.currentTimeMillis()
)

@Serializable
enum class TaskType {
    COMPUTATION, DATA_PROCESSING, AI_INFERENCE, NETWORK_OPERATION
}

@Serializable
enum class TaskStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

@Serializable
data class ComputeTask(
    @Contextual val id: Uuid,
    val type: TaskType,
    val priority: Int = 0,
    val status: TaskStatus = TaskStatus.PENDING,
    val estimatedDurationMs: Long = 1000,
    val requiredMemoryMB: Int = 512,
    val data: Map<String, String> = emptyMap()
)

@Serializable
enum class TrustLevel {
    UNKNOWN, LOW, MEDIUM, HIGH, VERIFIED
}

@Serializable
data class DeviceSecurityContext(
    @Contextual val deviceId: Uuid,
    val trustLevel: TrustLevel = TrustLevel.UNKNOWN,
    val isEncrypted: Boolean = false,
    val lastVerified: Long = 0
)

@Serializable
enum class DeviceType {
    DESKTOP, MOBILE, BROWSER, UNKNOWN
}

// Additional enums for enhanced device capabilities
@Serializable
enum class ProcessingPower {
    LOW, MEDIUM, HIGH, EXTREME
}

@Serializable
enum class MemoryCapacity {
    SMALL, MEDIUM, LARGE, UNLIMITED
}

@Serializable
enum class StorageCapacity {
    SMALL, MEDIUM, LARGE, UNLIMITED
}

@Serializable
enum class BatteryCapacity {
    LOW, MEDIUM, HIGH, UNLIMITED
}

@Serializable
enum class NetworkType {
    WIFI, CELLULAR, ETHERNET, BLUETOOTH, UNKNOWN
}

@Serializable
data class NetworkDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val capabilities: DeviceCapabilities,
    val ipAddress: String,
    val port: Int,
    val lastSeen: Long,
    val signalStrength: Float, // 0.0 to 1.0
    val isConnected: Boolean = false,
    val batteryLevel: Int = 100, // Added missing field
    val networkType: NetworkType = NetworkType.UNKNOWN, // Added missing field
    val metadata: Map<String, String> = emptyMap(),
    // Enhanced presence information
    val presenceInfo: PresenceInfo = PresenceInfo(),
    val statusHistory: DeviceStatusHistory? = null
)

// Enhanced device lifecycle state for comprehensive status management
@Serializable
data class DeviceLifecycleState(
    val deviceId: String,
    val currentStatus: PresenceStatus,
    val statusHistory: List<StatusChange>,
    val connectionSessions: List<ConnectionSession>,
    val connectionAnalytics: ConnectionAnalytics?,
    val lastPresenceUpdate: Long,
    val immediateMetadata: Map<String, String>,
    val lifecycleMetrics: DeviceLifecycleMetrics
)

// Comprehensive lifecycle metrics for device monitoring
@Serializable
data class DeviceLifecycleMetrics(
    val totalObservationTime: Long, // milliseconds
    val statusChangeFrequency: Float, // changes per hour
    val statusDistribution: Map<PresenceStatus, Int>,
    val statusDurations: Map<PresenceStatus, Long>, // milliseconds spent in each status
    val availabilityPercentage: Float, // percentage of time available (online + idle)
    val connectionReliability: Float, // percentage of good/excellent connections
    val averageConnectionDuration: Long, // milliseconds
    val totalDataExchanged: Int // total messages exchanged
)

// Device status summary for system-wide monitoring
@Serializable
data class DeviceStatusSummary(
    val totalDevices: Int,
    val activeDevices: Int,
    val statusDistribution: Map<PresenceStatus, Int>,
    val totalConnectionSessions: Int,
    val totalConnectionTime: Long,
    val devicesWithMetadata: Int
)