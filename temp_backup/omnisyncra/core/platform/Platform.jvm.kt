package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceType
import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.ScreenSize
import java.net.NetworkInterface as JavaNetworkInterface
import java.awt.Toolkit
import java.lang.management.ManagementFactory

class JvmPlatform : Platform {
    override val name: String = "Desktop ${System.getProperty("os.name")}"
    override val deviceType: DeviceType = DeviceType.DESKTOP
    
    override val capabilities: DeviceCapabilities = DeviceCapabilities(
        computePower = detectComputePower(),
        screenSize = detectScreenSize(),
        hasBluetoothLE = false, // Would need native integration
        hasWiFi = true,
        canOffloadCompute = true, // Desktop can handle compute tasks
        maxConcurrentTasks = getMaxConcurrentTasks(),
        supportedProtocols = listOf("wifi", "websocket", "mdns")
    )
    
    override fun getDeviceName(): String = System.getProperty("user.name") + "'s Desktop"
    
    override fun getNetworkInterfaces(): List<NetworkInterface> {
        return JavaNetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.toList()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { inetAddress ->
                        NetworkInterface(
                            name = networkInterface.name,
                            ipAddress = inetAddress.hostAddress,
                            isActive = true,
                            type = when {
                                networkInterface.name.contains("wifi", ignoreCase = true) -> NetworkType.WIFI
                                networkInterface.name.contains("eth", ignoreCase = true) -> NetworkType.ETHERNET
                                else -> NetworkType.UNKNOWN
                            }
                        )
                    }
            }
    }
    
    override fun supportsBluetoothLE(): Boolean = false // Would need native integration
    
    override fun supportsWebBluetooth(): Boolean = false
    
    override fun getScreenDimensions(): Pair<Int, Int> {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        return Pair(screenSize.width, screenSize.height)
    }
    
    override fun getAvailableMemory(): Long {
        val memoryBean = ManagementFactory.getMemoryMXBean()
        return memoryBean.heapMemoryUsage.max - memoryBean.heapMemoryUsage.used
    }
    
    override fun getCpuCores(): Int = Runtime.getRuntime().availableProcessors()
}

actual fun getPlatform(): Platform = JvmPlatform()

actual fun detectComputePower(): ComputePower {
    val cores = Runtime.getRuntime().availableProcessors()
    val maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024) // GB
    
    return when {
        cores >= 16 && maxMemory >= 16 -> ComputePower.EXTREME
        cores >= 8 && maxMemory >= 8 -> ComputePower.HIGH
        cores >= 4 && maxMemory >= 4 -> ComputePower.MEDIUM
        else -> ComputePower.LOW
    }
}

actual fun detectScreenSize(): ScreenSize {
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    val width = screenSize.width
    
    return when {
        width >= 3440 -> ScreenSize.EXTRA_LARGE // Ultra-wide
        width >= 1920 -> ScreenSize.LARGE
        width >= 1366 -> ScreenSize.MEDIUM
        else -> ScreenSize.SMALL
    }
}

actual fun getMaxConcurrentTasks(): Int {
    val cores = Runtime.getRuntime().availableProcessors()
    return cores * 2 // Desktop can handle more concurrent tasks
}