package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.NetworkCapability

/**
 * Platform abstraction for cross-platform functionality
 */
interface Platform {
    val name: String
    val capabilities: DeviceCapabilities
    
    fun getDeviceId(): String
    fun isNetworkAvailable(): Boolean
    
    // Enhanced capabilities for real presence broadcasting
    suspend fun getCurrentCpuUsage(): Float // 0.0 to 1.0
    suspend fun getAvailableMemory(): Long // bytes
    suspend fun getTotalMemory(): Long // bytes
    suspend fun getBatteryLevel(): Float? // 0.0 to 1.0, null if not applicable
    suspend fun getNetworkBandwidth(): Long // bytes per second estimate
    suspend fun getStorageInfo(): StorageInfo
    suspend fun getSystemLoad(): Float // 0.0 to 1.0
    suspend fun isAvailableForTasks(): Boolean
}

data class StorageInfo(
    val totalSpace: Long, // bytes
    val availableSpace: Long, // bytes
    val usedSpace: Long // bytes
)

expect fun getPlatform(): Platform