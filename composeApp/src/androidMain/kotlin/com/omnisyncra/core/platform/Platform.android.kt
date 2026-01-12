package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.NetworkCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

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
    
    override suspend fun getCurrentCpuUsage(): Float = withContext(Dispatchers.IO) {
        try {
            // Read CPU usage from /proc/stat on Android
            val statFile = File("/proc/stat")
            if (statFile.exists()) {
                val reader = RandomAccessFile(statFile, "r")
                val line = reader.readLine()
                reader.close()
                
                if (line != null && line.startsWith("cpu ")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val user = parts[1].toLongOrNull() ?: 0L
                        val nice = parts[2].toLongOrNull() ?: 0L
                        val system = parts[3].toLongOrNull() ?: 0L
                        val idle = parts[4].toLongOrNull() ?: 0L
                        
                        val total = user + nice + system + idle
                        val used = user + nice + system
                        
                        if (total > 0) {
                            (used.toFloat() / total.toFloat()).coerceIn(0.0f, 1.0f)
                        } else {
                            0.0f
                        }
                    } else {
                        0.0f
                    }
                } else {
                    0.0f
                }
            } else {
                0.0f
            }
        } catch (e: Exception) {
            0.0f
        }
    }
    
    override suspend fun getAvailableMemory(): Long = withContext(Dispatchers.IO) {
        try {
            // Read memory info from /proc/meminfo on Android
            val meminfoFile = File("/proc/meminfo")
            if (meminfoFile.exists()) {
                val lines = meminfoFile.readLines()
                var memAvailable = 0L
                var memFree = 0L
                var buffers = 0L
                var cached = 0L
                
                for (line in lines) {
                    when {
                        line.startsWith("MemAvailable:") -> {
                            memAvailable = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0L
                            memAvailable *= 1024 // Convert KB to bytes
                        }
                        line.startsWith("MemFree:") -> {
                            memFree = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0L
                            memFree *= 1024 // Convert KB to bytes
                        }
                        line.startsWith("Buffers:") -> {
                            buffers = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0L
                            buffers *= 1024 // Convert KB to bytes
                        }
                        line.startsWith("Cached:") -> {
                            cached = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0L
                            cached *= 1024 // Convert KB to bytes
                        }
                    }
                }
                
                // Use MemAvailable if available, otherwise estimate
                if (memAvailable > 0) memAvailable else (memFree + buffers + cached)
            } else {
                4L * 1024 * 1024 * 1024 // 4GB fallback
            }
        } catch (e: Exception) {
            4L * 1024 * 1024 * 1024 // 4GB fallback
        }
    }
    
    override suspend fun getTotalMemory(): Long = withContext(Dispatchers.IO) {
        try {
            val meminfoFile = File("/proc/meminfo")
            if (meminfoFile.exists()) {
                val lines = meminfoFile.readLines()
                for (line in lines) {
                    if (line.startsWith("MemTotal:")) {
                        val memTotal = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0L
                        return@withContext memTotal * 1024 // Convert KB to bytes
                    }
                }
            }
            6L * 1024 * 1024 * 1024 // 6GB fallback
        } catch (e: Exception) {
            6L * 1024 * 1024 * 1024 // 6GB fallback
        }
    }
    
    override suspend fun getBatteryLevel(): Float? = withContext(Dispatchers.IO) {
        try {
            // Try to read battery level from /sys/class/power_supply/battery/capacity
            val batteryFile = File("/sys/class/power_supply/battery/capacity")
            if (batteryFile.exists()) {
                val capacity = batteryFile.readText().trim().toIntOrNull()
                capacity?.let { it / 100.0f }
            } else {
                // Fallback: simulate battery level for demo
                0.60f + (kotlin.random.Random.nextFloat() * 0.4f)
            }
        } catch (e: Exception) {
            // Fallback: simulate battery level for demo
            0.60f + (kotlin.random.Random.nextFloat() * 0.4f)
        }
    }
    
    override suspend fun getNetworkBandwidth(): Long = withContext(Dispatchers.IO) {
        // Estimate mobile network bandwidth
        100L * 1024 * 1024 // 100 Mbps estimate for mobile
    }
    
    override suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            // Use internal storage directory
            val internalDir = File("/data")
            val totalSpace = internalDir.totalSpace
            val freeSpace = internalDir.freeSpace
            val usedSpace = totalSpace - freeSpace
            
            StorageInfo(
                totalSpace = totalSpace,
                availableSpace = freeSpace,
                usedSpace = usedSpace
            )
        } catch (e: Exception) {
            StorageInfo(
                totalSpace = 128L * 1024 * 1024 * 1024, // 128GB
                availableSpace = 64L * 1024 * 1024 * 1024, // 64GB
                usedSpace = 64L * 1024 * 1024 * 1024 // 64GB
            )
        }
    }
    
    override suspend fun getSystemLoad(): Float = withContext(Dispatchers.IO) {
        try {
            // Read load average from /proc/loadavg
            val loadavgFile = File("/proc/loadavg")
            if (loadavgFile.exists()) {
                val line = loadavgFile.readText().trim()
                val parts = line.split(" ")
                if (parts.isNotEmpty()) {
                    val load1min = parts[0].toFloatOrNull() ?: 0.0f
                    // Normalize by number of CPU cores (assume 4 cores for mobile)
                    (load1min / 4.0f).coerceAtMost(1.0f)
                } else {
                    0.0f
                }
            } else {
                0.0f
            }
        } catch (e: Exception) {
            0.0f
        }
    }
    
    override suspend fun isAvailableForTasks(): Boolean {
        val batteryLevel = getBatteryLevel() ?: 1.0f
        val systemLoad = getSystemLoad()
        
        // Available if battery > 20% and system load < 0.7
        return batteryLevel > 0.2f && systemLoad < 0.7f
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()