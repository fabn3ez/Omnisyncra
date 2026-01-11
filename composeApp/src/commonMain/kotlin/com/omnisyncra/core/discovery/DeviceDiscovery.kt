package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.StateFlow
import com.omnisyncra.core.domain.NetworkDevice

/**
 * Enhanced device discovery service for cross-platform connectivity
 * Supports JVM Desktop, Android, JavaScript Browser, and WASM platforms
 */
interface DeviceDiscovery {
    val discoveredDevices: StateFlow<List<NetworkDevice>>
    val connectedDevices: StateFlow<List<NetworkDevice>>
    val discoveryStatus: StateFlow<DiscoveryStatus>
    val sharedContext: StateFlow<SharedContext>
    val currentDevice: StateFlow<NetworkDevice>
    
    suspend fun startDiscovery(): Result<Unit>
    suspend fun stopDiscovery(): Result<Unit>
    suspend fun connectToDevice(deviceId: String): Result<Unit>
    suspend fun disconnectFromDevice(deviceId: String): Result<Unit>
    suspend fun shareContext(context: Map<String, Any>): Result<Unit>
    suspend fun broadcastMessage(message: String): Result<Unit>
    suspend fun getPlatformDevices(): Result<Map<String, List<NetworkDevice>>>
}