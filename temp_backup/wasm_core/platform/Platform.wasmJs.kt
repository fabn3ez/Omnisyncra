package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceType
import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.ScreenSize

class WasmPlatform : Platform {
    override val name: String = "WebAssembly"
    override val deviceType: DeviceType = DeviceType.WEB_BROWSER
    
    override val capabilities: DeviceCapabilities = DeviceCapabilities(
        computePower = detectComputePower(),
        screenSize = detectScreenSize(),
        hasBluetoothLE = supportsBluetoothLE(),
        hasWiFi = true,
        canOffloadCompute = false, // Wasm typically offloads compute
        maxConcurrentTasks = getMaxConcurrentTasks(),
        supportedProtocols = listOf("websocket", "webrtc", "web-bluetooth")
    )
    
    override fun getDeviceName(): String = "WebAssembly Runtime"
    
    override fun getNetworkInterfaces(): List<NetworkInterface> {
        return listOf(
            NetworkInterface(
                name = "wasm",
                ipAddress = "unknown",
                isActive = true,
                type = NetworkType.UNKNOWN
            )
        )
    }
    
    override fun supportsBluetoothLE(): Boolean {
        // Check if Web Bluetooth is available in Wasm context
        return try {
            js("typeof navigator !== 'undefined' && typeof navigator.bluetooth !== 'undefined'") as Boolean
        } catch (e: Exception) {
            false
        }
    }
    
    override fun supportsWebBluetooth(): Boolean = supportsBluetoothLE()
    
    override fun getScreenDimensions(): Pair<Int, Int> {
        return try {
            val width = js("window.screen.width") as Int
            val height = js("window.screen.height") as Int
            Pair(width, height)
        } catch (e: Exception) {
            Pair(1920, 1080) // Default fallback
        }
    }
    
    override fun getAvailableMemory(): Long = -1L // Not available in Wasm
    
    override fun getCpuCores(): Int {
        return try {
            js("navigator.hardwareConcurrency || 4") as Int
        } catch (e: Exception) {
            4 // Default fallback
        }
    }
}

actual fun getPlatform(): Platform = WasmPlatform()

actual fun detectComputePower(): ComputePower {
    // Wasm has good performance but is still constrained by browser
    val cores = try {
        js("navigator.hardwareConcurrency || 4") as Int
    } catch (e: Exception) {
        4
    }
    
    return when {
        cores >= 8 -> ComputePower.MEDIUM
        cores >= 4 -> ComputePower.LOW
        else -> ComputePower.LOW
    }
}

actual fun detectScreenSize(): ScreenSize {
    val width = try {
        js("window.screen.width") as Int
    } catch (e: Exception) {
        1920
    }
    
    return when {
        width >= 1920 -> ScreenSize.LARGE
        width >= 1366 -> ScreenSize.MEDIUM
        else -> ScreenSize.SMALL
    }
}

actual fun getMaxConcurrentTasks(): Int {
    val cores = try {
        js("navigator.hardwareConcurrency || 4") as Int
    } catch (e: Exception) {
        4
    }
    return maxOf(2, cores / 2) // Conservative for Wasm
}