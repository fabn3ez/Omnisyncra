package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.NetworkCapability

class AndroidPlatform : Platform {
    override val name = "Android"
    override val capabilities = DeviceCapabilities(
        computePower = ComputePower.MEDIUM,
        networkCapability = NetworkCapability.FULL,
        maxConcurrentTasks = 4,
        availableMemoryMB = 4096
    )
    
    override fun getDeviceId(): String = "android-device"
    override fun isNetworkAvailable(): Boolean = true
}

actual fun getPlatform(): Platform = AndroidPlatform()