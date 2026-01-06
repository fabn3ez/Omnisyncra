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
}

expect fun getPlatform(): Platform