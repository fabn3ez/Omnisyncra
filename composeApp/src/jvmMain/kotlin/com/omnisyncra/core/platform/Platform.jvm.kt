package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.NetworkCapability

class DesktopPlatform : Platform {
    override val name = "Desktop"
    override val capabilities = DeviceCapabilities(
        computePower = ComputePower.HIGH,
        networkCapability = NetworkCapability.FULL,
        maxConcurrentTasks = 8,
        availableMemoryMB = 8192
    )
    
    override fun getDeviceId(): String = "desktop-${System.getProperty("user.name", "unknown")}"
    override fun isNetworkAvailable(): Boolean = true
}

actual fun getPlatform(): Platform = DesktopPlatform()