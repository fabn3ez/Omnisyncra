package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.NetworkCapability

class WebPlatform : Platform {
    override val name = "Web"
    override val capabilities = DeviceCapabilities(
        computePower = ComputePower.MEDIUM,
        networkCapability = NetworkCapability.FULL,
        maxConcurrentTasks = 2,
        availableMemoryMB = 2048
    )
    
    override fun getDeviceId(): String = "web-browser"
    override fun isNetworkAvailable(): Boolean = true
}

actual fun getPlatform(): Platform = WebPlatform()