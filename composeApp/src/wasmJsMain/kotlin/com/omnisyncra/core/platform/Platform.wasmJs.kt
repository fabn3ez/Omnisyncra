package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.NetworkCapability

class WasmPlatform : Platform {
    override val name = "WebAssembly"
    override val capabilities = DeviceCapabilities(
        computePower = ComputePower.HIGH, // WASM is fast
        networkCapability = NetworkCapability.FULL,
        maxConcurrentTasks = 4,
        availableMemoryMB = 2048
    )
    
    override fun getDeviceId(): String = "wasm-client"
    override fun isNetworkAvailable(): Boolean = true
    
    override suspend fun getCurrentCpuUsage(): Float {
        // WASM doesn't provide direct CPU usage access
        // Estimate based on performance timing with more intensive computation
        return try {
            val start = js("performance.now()")
            // Perform computation to estimate CPU load
            var sum = 0.0
            for (i in 0..50000) {
                sum += kotlin.math.sqrt(i.toDouble())
            }
            val end = js("performance.now()")
            val duration = (end as Double) - (start as Double)
            
            // Normalize duration to CPU usage estimate
            (duration / 200.0).coerceAtMost(1.0).toFloat()
        } catch (e: Exception) {
            0.2f // Default low usage for WASM efficiency
        }
    }
    
    override suspend fun getAvailableMemory(): Long {
        return try {
            // WASM has limited memory access
            // Try to get memory info from performance API
            val memoryInfo = js("performance.memory")
            if (memoryInfo != null) {
                val usedJSHeapSize = js("memoryInfo.usedJSHeapSize") as? Double ?: 0.0
                val totalJSHeapSize = js("memoryInfo.totalJSHeapSize") as? Double ?: 0.0
                ((totalJSHeapSize - usedJSHeapSize).toLong()).coerceAtLeast(0L)
            } else {
                1L * 1024 * 1024 * 1024 // 1GB fallback
            }
        } catch (e: Exception) {
            1L * 1024 * 1024 * 1024 // 1GB fallback
        }
    }
    
    override suspend fun getTotalMemory(): Long {
        return try {
            val memoryInfo = js("performance.memory")
            if (memoryInfo != null) {
                val jsHeapSizeLimit = js("memoryInfo.jsHeapSizeLimit") as? Double ?: 0.0
                jsHeapSizeLimit.toLong()
            } else {
                2L * 1024 * 1024 * 1024 // 2GB fallback
            }
        } catch (e: Exception) {
            2L * 1024 * 1024 * 1024 // 2GB fallback
        }
    }
    
    override suspend fun getBatteryLevel(): Float? {
        // WASM typically runs in browser context, no battery access
        return null
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
            // WASM has very limited storage
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
                // Fallback values for WASM storage
                StorageInfo(
                    totalSpace = 512L * 1024 * 1024, // 512MB
                    availableSpace = 256L * 1024 * 1024, // 256MB
                    usedSpace = 256L * 1024 * 1024 // 256MB
                )
            }
        } catch (e: Exception) {
            StorageInfo(
                totalSpace = 512L * 1024 * 1024, // 512MB
                availableSpace = 256L * 1024 * 1024, // 256MB
                usedSpace = 256L * 1024 * 1024 // 256MB
            )
        }
    }
    
    override suspend fun getSystemLoad(): Float {
        // WASM doesn't provide system load info
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
            0.3f
        }
        
        // WASM is efficient, so more lenient thresholds
        // Available if CPU usage < 80% and memory usage < 90%
        return cpuUsage < 0.8f && memoryUsage < 0.9f
    }
}

actual fun getPlatform(): Platform = WasmPlatform()