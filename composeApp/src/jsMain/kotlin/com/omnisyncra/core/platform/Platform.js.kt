package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.NetworkCapability
import kotlinx.coroutines.delay

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
    
    override suspend fun getCurrentCpuUsage(): Float {
        // Browser doesn't provide direct CPU usage access
        // Estimate based on performance timing
        return try {
            val start = js("performance.now()")
            // Perform a small computation to estimate CPU load
            var sum = 0
            for (i in 0..10000) {
                sum += i
            }
            val end = js("performance.now()")
            val duration = (end as Double) - (start as Double)
            
            // Normalize duration to CPU usage estimate (higher duration = higher load)
            (duration / 100.0).coerceAtMost(1.0).toFloat()
        } catch (e: Exception) {
            0.3f // Default moderate usage
        }
    }
    
    override suspend fun getAvailableMemory(): Long {
        return try {
            // Try to get memory info from performance API
            val memoryInfo = js("performance.memory")
            if (memoryInfo != null) {
                val usedJSHeapSize = js("memoryInfo.usedJSHeapSize") as? Double ?: 0.0
                val totalJSHeapSize = js("memoryInfo.totalJSHeapSize") as? Double ?: 0.0
                ((totalJSHeapSize - usedJSHeapSize).toLong()).coerceAtLeast(0L)
            } else {
                2L * 1024 * 1024 * 1024 // 2GB fallback
            }
        } catch (e: Exception) {
            2L * 1024 * 1024 * 1024 // 2GB fallback
        }
    }
    
    override suspend fun getTotalMemory(): Long {
        return try {
            val memoryInfo = js("performance.memory")
            if (memoryInfo != null) {
                val jsHeapSizeLimit = js("memoryInfo.jsHeapSizeLimit") as? Double ?: 0.0
                jsHeapSizeLimit.toLong()
            } else {
                4L * 1024 * 1024 * 1024 // 4GB fallback
            }
        } catch (e: Exception) {
            4L * 1024 * 1024 * 1024 // 4GB fallback
        }
    }
    
    override suspend fun getBatteryLevel(): Float? {
        return try {
            // Try to access Battery API (deprecated but may still work)
            val battery = js("navigator.getBattery && navigator.getBattery()")
            if (battery != null) {
                val level = js("battery.level") as? Double
                level?.toFloat()
            } else {
                null // No battery info available in browser
            }
        } catch (e: Exception) {
            null // No battery info available
        }
    }
    
    override suspend fun getNetworkBandwidth(): Long {
        return try {
            // Try to get connection info
            val connection = js("navigator.connection || navigator.mozConnection || navigator.webkitConnection")
            if (connection != null) {
                val downlink = js("connection.downlink") as? Double ?: 10.0
                (downlink * 1024 * 1024).toLong() // Convert Mbps to bytes per second
            } else {
                200L * 1024 * 1024 // 200 Mbps fallback
            }
        } catch (e: Exception) {
            200L * 1024 * 1024 // 200 Mbps fallback
        }
    }
    
    override suspend fun getStorageInfo(): StorageInfo {
        return try {
            // Try to estimate storage using Storage API
            val estimate = js("navigator.storage && navigator.storage.estimate && navigator.storage.estimate()")
            if (estimate != null) {
                val quota = js("estimate.quota") as? Double ?: 0.0
                val usage = js("estimate.usage") as? Double ?: 0.0
                
                StorageInfo(
                    totalSpace = quota.toLong(),
                    availableSpace = (quota - usage).toLong(),
                    usedSpace = usage.toLong()
                )
            } else {
                // Fallback values for browser storage
                StorageInfo(
                    totalSpace = 1L * 1024 * 1024 * 1024, // 1GB
                    availableSpace = 512L * 1024 * 1024, // 512MB
                    usedSpace = 512L * 1024 * 1024 // 512MB
                )
            }
        } catch (e: Exception) {
            StorageInfo(
                totalSpace = 1L * 1024 * 1024 * 1024, // 1GB
                availableSpace = 512L * 1024 * 1024, // 512MB
                usedSpace = 512L * 1024 * 1024 // 512MB
            )
        }
    }
    
    override suspend fun getSystemLoad(): Float {
        // Browser doesn't provide system load info
        // Estimate based on CPU usage
        return getCurrentCpuUsage()
    }
    
    override suspend fun isAvailableForTasks(): Boolean {
        val cpuUsage = getCurrentCpuUsage()
        val availableMemory = getAvailableMemory()
        val totalMemory = getTotalMemory()
        
        val memoryUsage = if (totalMemory > 0) {
            1.0f - (availableMemory.toFloat() / totalMemory.toFloat())
        } else {
            0.5f
        }
        
        // Available if CPU usage < 70% and memory usage < 80%
        return cpuUsage < 0.7f && memoryUsage < 0.8f
    }
}

actual fun getPlatform(): Platform = WebPlatform()