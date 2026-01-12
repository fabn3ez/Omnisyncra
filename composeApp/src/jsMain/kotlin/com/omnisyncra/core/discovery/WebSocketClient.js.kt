package com.omnisyncra.core.discovery

import com.omnisyncra.core.domain.*
import com.omnisyncra.core.platform.TimeUtils

actual class WebSocketClient actual constructor(
    private val host: String,
    private val port: Int
) {
    actual suspend fun connect(onMessage: (message: SyncMessage) -> Unit) {
        println("JS client connecting to $host:$port")
    }
    
    actual suspend fun disconnect() {
        // Disconnect
    }
    
    actual suspend fun sendMessage(message: SyncMessage) {
        println("JS client sending: ${message.type}")
    }
    
    actual suspend fun getDeviceInfo(): NetworkDevice? {
        return NetworkDevice(
            id = "js_device_${host}_${port}",
            name = "JavaScript Omnisyncra Device",
            type = DeviceType.BROWSER,
            capabilities = DeviceCapabilities(
                canCompute = true,
                canStore = false,
                canDisplay = true,
                canInput = true,
                processingPower = ProcessingPower.MEDIUM,
                memoryCapacity = MemoryCapacity.SMALL,
                storageCapacity = StorageCapacity.SMALL,
                networkCapability = NetworkCapability.FULL,
                batteryCapacity = BatteryCapacity.UNLIMITED
            ),
            ipAddress = host,
            port = port,
            lastSeen = TimeUtils.currentTimeMillis(),
            signalStrength = 0.7f,
            isConnected = true,
            batteryLevel = 100,
            networkType = NetworkType.WIFI
        )
    }
}