package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceType
import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.ScreenSize

interface Platform {
    val name: String
    val deviceType: DeviceType
    val capabilities: DeviceCapabilities
    
    fun getDeviceName(): String
    fun getNetworkInterfaces(): List<NetworkInterface>
    fun supportsBluetoothLE(): Boolean
    fun supportsWebBluetooth(): Boolean
    fun getScreenDimensions(): Pair<Int, Int>
    fun getAvailableMemory(): Long
    fun getCpuCores(): Int
}

data class NetworkInterface(
    val name: String,
    val ipAddress: String,
    val isActive: Boolean,
    val type: NetworkType
)

enum class NetworkType {
    WIFI,
    ETHERNET,
    CELLULAR,
    BLUETOOTH,
    UNKNOWN
}

expect fun getPlatform(): Platform

// Platform-specific capabilities detection
expect fun detectComputePower(): ComputePower
expect fun detectScreenSize(): ScreenSize
expect fun getMaxConcurrentTasks(): Int