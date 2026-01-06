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
    val availableMemoryMB: Int = 4096
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
data class SecurityContext(
    @Contextual val deviceId: Uuid,
    val trustLevel: TrustLevel = TrustLevel.UNKNOWN,
    val isEncrypted: Boolean = false,
    val lastVerified: Long = 0
)