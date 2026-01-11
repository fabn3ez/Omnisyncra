package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.NetworkCapability
import java.lang.management.ManagementFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    
    override suspend fun getCurrentCpuUsage(): Float = withContext(Dispatchers.IO) {
        try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            if (osBean is com.sun.management.OperatingSystemMXBean) {
                val cpuUsage = osBean.processCpuLoad
                if (cpuUsage >= 0.0) cpuUsage.toFloat() else 0.0f
            } else {
                // Fallback: estimate based on system load
                val systemLoad = osBean.systemLoadAverage
                if (systemLoad >= 0.0) {
                    (systemLoad / Runtime.getRuntime().availableProcessors()).coerceAtMost(1.0).toFloat()
                } else {
                    0.0f
                }
            }
        } catch (e: Exception) {
            0.0f
        }
    }
    
    override suspend fun getAvailableMemory(): Long = withContext(Dispatchers.IO) {
        try {
            val memoryBean = ManagementFactory.getMemoryMXBean()
            val heapMemory = memoryBean.heapMemoryUsage
            val nonHeapMemory = memoryBean.nonHeapMemoryUsage
            
            // Return available heap memory
            heapMemory.max - heapMemory.used
        } catch (e: Exception) {
            Runtime.getRuntime().freeMemory()
        }
    }
    
    override suspend fun getTotalMemory(): Long = withContext(Dispatchers.IO) {
        try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            if (osBean is com.sun.management.OperatingSystemMXBean) {
                osBean.totalPhysicalMemorySize
            } else {
                Runtime.getRuntime().totalMemory()
            }
        } catch (e: Exception) {
            Runtime.getRuntime().totalMemory()
        }
    }
    
    override suspend fun getBatteryLevel(): Float? = null // Desktop typically doesn't have battery
    
    override suspend fun getNetworkBandwidth(): Long = withContext(Dispatchers.IO) {
        // Estimate network bandwidth - in real implementation this could use network monitoring
        1000L * 1024 * 1024 // 1 Gbps estimate for desktop
    }
    
    override suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val rootFile = File("/")
            val totalSpace = rootFile.totalSpace
            val freeSpace = rootFile.freeSpace
            val usedSpace = totalSpace - freeSpace
            
            StorageInfo(
                totalSpace = totalSpace,
                availableSpace = freeSpace,
                usedSpace = usedSpace
            )
        } catch (e: Exception) {
            StorageInfo(0L, 0L, 0L)
        }
    }
    
    override suspend fun getSystemLoad(): Float = withContext(Dispatchers.IO) {
        try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            val systemLoad = osBean.systemLoadAverage
            if (systemLoad >= 0.0) {
                (systemLoad / Runtime.getRuntime().availableProcessors()).coerceAtMost(1.0).toFloat()
            } else {
                0.0f
            }
        } catch (e: Exception) {
            0.0f
        }
    }
    
    override suspend fun isAvailableForTasks(): Boolean {
        val cpuUsage = getCurrentCpuUsage()
        val systemLoad = getSystemLoad()
        
        // Available if CPU usage < 80% and system load < 0.8
        return cpuUsage < 0.8f && systemLoad < 0.8f
    }
}

actual fun getPlatform(): Platform = DesktopPlatform()