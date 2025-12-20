package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceType
import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.ScreenSize
import kotlinx.browser.window

class JsPlatform : Platform {
    override val name: String = "Web ${window.navigator.userAgent}"
    override val deviceType: DeviceType = DeviceType.WEB_BROWSER
    
    override val capabilities: DeviceCapabilities = DeviceCapabilities(
        computePower = detectComputePower(),
        screenSize = detectScreenSize(),
        hasBluetoothLE = supportsBluetoothLE(),
        hasWiFi = true, // Assume web has network
        canOffloadCompute = false, // Web typically offloads to desktop
        maxConcurrentTasks = getMaxConcurrentTasks(),
        supportedProtocols = listOf("websocket", "webrtc", "web-bluetooth")
    )
    
    override fun getDeviceName(): String = "Web Browser"
    
    override fun getNetworkInterfaces(): List<NetworkInterface> {
        // Web browsers don't expose network interface details for security
        return listOf(
            NetworkInterface(
                name = "browser",
                ipAddress = "unknown",
                isActive = true,
                type = NetworkType.UNKNOWN
            )
        )
    }
    
    override fun supportsBluetoothLE(): Boolean {
        return js("typeof navigator.bluetooth !== 'undefined'") as Boolean
    }
    
    override fun supportsWebBluetooth(): Boolean = supportsBluetoothLE()
    
    override fun getScreenDimensions(): Pair<Int, Int> {
        return Pair(window.screen.width, window.screen.height)
    }
    
    override fun getAvailableMemory(): Long {
        // Web browsers don't expose memory info for security
        return -1L
    }
    
    override fun getCpuCores(): Int {
        return js("navigator.hardwareConcurrency || 4") as Int
    }
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun detectComputePower(): ComputePower {
    val cores = js("navigator.hardwareConcurrency || 4") as Int
    // Web/JS is generally more constrained
    return when {
        cores >= 8 -> ComputePower.MEDIUM
        cores >= 4 -> ComputePower.LOW
        else -> ComputePower.LOW
    }
}

actual fun detectScreenSize(): ScreenSize {
    val width = window.screen.width
    return when {
        width >= 1920 -> ScreenSize.LARGE
        width >= 1366 -> ScreenSize.MEDIUM
        width >= 768 -> ScreenSize.SMALL
        else -> ScreenSize.SMALL
    }
}

actual fun getMaxConcurrentTasks(): Int {
    val cores = js("navigator.hardwareConcurrency || 4") as Int
    return maxOf(2, cores / 2) // Conservative for web
}