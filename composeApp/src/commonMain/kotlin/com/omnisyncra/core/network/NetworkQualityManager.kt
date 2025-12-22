package com.omnisyncra.core.network

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.math.max
import kotlin.math.min

// Network quality metrics
data class NetworkQuality(
    val bandwidth: Long, // bytes per second
    val latency: Long, // milliseconds
    val packetLoss: Double, // percentage (0.0 to 1.0)
    val jitter: Long, // milliseconds
    val quality: QualityLevel,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

enum class QualityLevel {
    EXCELLENT, // > 10 Mbps, < 50ms latency, < 0.1% loss
    GOOD,      // > 5 Mbps, < 100ms latency, < 0.5% loss
    FAIR,      // > 1 Mbps, < 200ms latency, < 2% loss
    POOR       // < 1 Mbps, > 200ms latency, > 2% loss
}

// Adaptive configuration based on network quality
data class AdaptiveConfig(
    val maxConcurrentConnections: Int,
    val compressionLevel: Int, // 0-9
    val batchSize: Int,
    val retryAttempts: Int,
    val timeoutMs: Long,
    val useCompression: Boolean,
    val prioritizeLatency: Boolean
)

// Network measurement result
data class NetworkMeasurement(
    val deviceId: Uuid,
    val testType: TestType,
    val startTime: Long,
    val endTime: Long,
    val bytesTransferred: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

enum class TestType {
    BANDWIDTH_UPLOAD,
    BANDWIDTH_DOWNLOAD,
    LATENCY_PING,
    PACKET_LOSS,
    JITTER
}

// Network Quality Manager
class NetworkQualityManager(
    private val nodeId: Uuid
) {
    private val measurements = mutableMapOf<Uuid, MutableList<NetworkMeasurement>>()
    private val qualityHistory = mutableMapOf<Uuid, MutableList<NetworkQuality>>()
    
    private val _qualityUpdates = MutableSharedFlow<NetworkQualityUpdate>()
    val qualityUpdates: SharedFlow<NetworkQualityUpdate> = _qualityUpdates.asSharedFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Default configurations for different quality levels
    private val qualityConfigs = mapOf(
        QualityLevel.EXCELLENT to AdaptiveConfig(
            maxConcurrentConnections = 10,
            compressionLevel = 1,
            batchSize = 100,
            retryAttempts = 2,
            timeoutMs = 5000,
            useCompression = false,
            prioritizeLatency = true
        ),
        QualityLevel.GOOD to AdaptiveConfig(
            maxConcurrentConnections = 6,
            compressionLevel = 3,
            batchSize = 50,
            retryAttempts = 3,
            timeoutMs = 10000,
            useCompression = true,
            prioritizeLatency = true
        ),
        QualityLevel.FAIR to AdaptiveConfig(
            maxConcurrentConnections = 3,
            compressionLevel = 6,
            batchSize = 20,
            retryAttempts = 5,
            timeoutMs = 20000,
            useCompression = true,
            prioritizeLatency = false
        ),
        QualityLevel.POOR to AdaptiveConfig(
            maxConcurrentConnections = 1,
            compressionLevel = 9,
            batchSize = 5,
            retryAttempts = 8,
            timeoutMs = 30000,
            useCompression = true,
            prioritizeLatency = false
        )
    )
    
    init {
        // Start periodic quality monitoring
        scope.launch {
            while (isActive) {
                delay(30_000) // Every 30 seconds
                performQualityCheck()
            }
        }
    }
    
    suspend fun measureBandwidth(
        deviceId: Uuid,
        testDataSize: Long = 1024 * 1024 // 1MB
    ): NetworkMeasurement {
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        return try {
            // Simulate bandwidth test
            delay(1000) // Simulate network transfer
            
            val endTime = Clock.System.now().toEpochMilliseconds()
            val measurement = NetworkMeasurement(
                deviceId = deviceId,
                testType = TestType.BANDWIDTH_DOWNLOAD,
                startTime = startTime,
                endTime = endTime,
                bytesTransferred = testDataSize,
                success = true
            )
            
            recordMeasurement(deviceId, measurement)
            measurement
        } catch (e: Exception) {
            NetworkMeasurement(
                deviceId = deviceId,
                testType = TestType.BANDWIDTH_DOWNLOAD,
                startTime = startTime,
                endTime = Clock.System.now().toEpochMilliseconds(),
                bytesTransferred = 0,
                success = false,
                errorMessage = e.message
            )
        }
    }
    
    suspend fun measureLatency(deviceId: Uuid): NetworkMeasurement {
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        return try {
            // Simulate ping test
            delay(50) // Simulate network round trip
            
            val endTime = Clock.System.now().toEpochMilliseconds()
            val measurement = NetworkMeasurement(
                deviceId = deviceId,
                testType = TestType.LATENCY_PING,
                startTime = startTime,
                endTime = endTime,
                bytesTransferred = 64, // Typical ping packet size
                success = true
            )
            
            recordMeasurement(deviceId, measurement)
            measurement
        } catch (e: Exception) {
            NetworkMeasurement(
                deviceId = deviceId,
                testType = TestType.LATENCY_PING,
                startTime = startTime,
                endTime = Clock.System.now().toEpochMilliseconds(),
                bytesTransferred = 0,
                success = false,
                errorMessage = e.message
            )
        }
    }
    
    suspend fun measurePacketLoss(
        deviceId: Uuid,
        packetCount: Int = 10
    ): NetworkMeasurement {
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        return try {
            // Simulate packet loss test
            delay(2000) // Simulate multiple ping attempts
            
            val endTime = Clock.System.now().toEpochMilliseconds()
            val measurement = NetworkMeasurement(
                deviceId = deviceId,
                testType = TestType.PACKET_LOSS,
                startTime = startTime,
                endTime = endTime,
                bytesTransferred = packetCount * 64L,
                success = true
            )
            
            recordMeasurement(deviceId, measurement)
            measurement
        } catch (e: Exception) {
            NetworkMeasurement(
                deviceId = deviceId,
                testType = TestType.PACKET_LOSS,
                startTime = startTime,
                endTime = Clock.System.now().toEpochMilliseconds(),
                bytesTransferred = 0,
                success = false,
                errorMessage = e.message
            )
        }
    }
    
    private fun recordMeasurement(deviceId: Uuid, measurement: NetworkMeasurement) {
        val deviceMeasurements = measurements.getOrPut(deviceId) { mutableListOf() }
        deviceMeasurements.add(measurement)
        
        // Keep only last 100 measurements per device
        if (deviceMeasurements.size > 100) {
            deviceMeasurements.removeFirst()
        }
    }
    
    suspend fun calculateNetworkQuality(deviceId: Uuid): NetworkQuality? {
        val deviceMeasurements = measurements[deviceId] ?: return null
        if (deviceMeasurements.isEmpty()) return null
        
        // Get recent measurements (last 10)
        val recentMeasurements = deviceMeasurements.takeLast(10)
        
        // Calculate bandwidth (from download tests)
        val bandwidthTests = recentMeasurements.filter { 
            it.testType == TestType.BANDWIDTH_DOWNLOAD && it.success 
        }
        val avgBandwidth = if (bandwidthTests.isNotEmpty()) {
            bandwidthTests.map { measurement ->
                val durationSeconds = (measurement.endTime - measurement.startTime) / 1000.0
                if (durationSeconds > 0) measurement.bytesTransferred / durationSeconds else 0.0
            }.average().toLong()
        } else 0L
        
        // Calculate latency (from ping tests)
        val latencyTests = recentMeasurements.filter { 
            it.testType == TestType.LATENCY_PING && it.success 
        }
        val avgLatency = if (latencyTests.isNotEmpty()) {
            latencyTests.map { it.endTime - it.startTime }.average().toLong()
        } else 0L
        
        // Calculate packet loss
        val packetLossTests = recentMeasurements.filter { 
            it.testType == TestType.PACKET_LOSS 
        }
        val packetLoss = if (packetLossTests.isNotEmpty()) {
            val totalTests = packetLossTests.size
            val failedTests = packetLossTests.count { !it.success }
            failedTests.toDouble() / totalTests
        } else 0.0
        
        // Estimate jitter (simplified)
        val jitter = if (latencyTests.size > 1) {
            val latencies = latencyTests.map { it.endTime - it.startTime }
            val avgLat = latencies.average()
            latencies.map { kotlin.math.abs(it - avgLat) }.average().toLong()
        } else 0L
        
        // Determine quality level
        val quality = determineQualityLevel(avgBandwidth, avgLatency, packetLoss)
        
        val networkQuality = NetworkQuality(
            bandwidth = avgBandwidth,
            latency = avgLatency,
            packetLoss = packetLoss,
            jitter = jitter,
            quality = quality
        )
        
        // Store quality history
        val qualityList = qualityHistory.getOrPut(deviceId) { mutableListOf() }
        qualityList.add(networkQuality)
        if (qualityList.size > 50) {
            qualityList.removeFirst()
        }
        
        // Emit quality update
        _qualityUpdates.emit(
            NetworkQualityUpdate(deviceId, networkQuality)
        )
        
        return networkQuality
    }
    
    private fun determineQualityLevel(
        bandwidth: Long,
        latency: Long,
        packetLoss: Double
    ): QualityLevel {
        val bandwidthMbps = bandwidth * 8 / (1024 * 1024) // Convert to Mbps
        
        return when {
            bandwidthMbps > 10 && latency < 50 && packetLoss < 0.001 -> QualityLevel.EXCELLENT
            bandwidthMbps > 5 && latency < 100 && packetLoss < 0.005 -> QualityLevel.GOOD
            bandwidthMbps > 1 && latency < 200 && packetLoss < 0.02 -> QualityLevel.FAIR
            else -> QualityLevel.POOR
        }
    }
    
    fun getAdaptiveConfig(deviceId: Uuid): AdaptiveConfig {
        val recentQuality = qualityHistory[deviceId]?.lastOrNull()
        val qualityLevel = recentQuality?.quality ?: QualityLevel.FAIR
        
        return qualityConfigs[qualityLevel] ?: qualityConfigs[QualityLevel.FAIR]!!
    }
    
    fun getAdaptiveConfigForQuality(quality: QualityLevel): AdaptiveConfig {
        return qualityConfigs[quality] ?: qualityConfigs[QualityLevel.FAIR]!!
    }
    
    suspend fun performQualityCheck() {
        // Get all devices we have measurements for
        val deviceIds = measurements.keys.toList()
        
        deviceIds.forEach { deviceId ->
            try {
                // Perform lightweight quality check
                measureLatency(deviceId)
                calculateNetworkQuality(deviceId)
            } catch (e: Exception) {
                // Log error but continue with other devices
            }
        }
    }
    
    fun getQualityHistory(deviceId: Uuid): List<NetworkQuality> {
        return qualityHistory[deviceId]?.toList() ?: emptyList()
    }
    
    fun getCurrentQuality(deviceId: Uuid): NetworkQuality? {
        return qualityHistory[deviceId]?.lastOrNull()
    }
    
    fun getAllCurrentQualities(): Map<Uuid, NetworkQuality> {
        return qualityHistory.mapValues { (_, history) ->
            history.lastOrNull()
        }.filterValues { it != null }.mapValues { it.value!! }
    }
    
    suspend fun optimizeForDevice(deviceId: Uuid): NetworkOptimization {
        val quality = getCurrentQuality(deviceId)
        val config = getAdaptiveConfig(deviceId)
        
        val recommendations = mutableListOf<String>()
        
        quality?.let { q ->
            when (q.quality) {
                QualityLevel.POOR -> {
                    recommendations.add("Enable maximum compression")
                    recommendations.add("Reduce concurrent connections")
                    recommendations.add("Increase retry attempts")
                    recommendations.add("Use larger timeouts")
                }
                QualityLevel.FAIR -> {
                    recommendations.add("Enable moderate compression")
                    recommendations.add("Limit concurrent connections")
                    recommendations.add("Use standard timeouts")
                }
                QualityLevel.GOOD -> {
                    recommendations.add("Use light compression")
                    recommendations.add("Allow more concurrent connections")
                    recommendations.add("Optimize for throughput")
                }
                QualityLevel.EXCELLENT -> {
                    recommendations.add("Disable compression for speed")
                    recommendations.add("Maximize concurrent connections")
                    recommendations.add("Optimize for latency")
                }
            }
        }
        
        return NetworkOptimization(
            deviceId = deviceId,
            currentQuality = quality,
            recommendedConfig = config,
            recommendations = recommendations
        )
    }
    
    fun cleanup() {
        scope.cancel()
        measurements.clear()
        qualityHistory.clear()
    }
}

// Network quality update event
data class NetworkQualityUpdate(
    val deviceId: Uuid,
    val quality: NetworkQuality
)

// Network optimization recommendations
data class NetworkOptimization(
    val deviceId: Uuid,
    val currentQuality: NetworkQuality?,
    val recommendedConfig: AdaptiveConfig,
    val recommendations: List<String>
)

// Network quality statistics
data class NetworkQualityStats(
    val totalDevices: Int,
    val excellentQuality: Int,
    val goodQuality: Int,
    val fairQuality: Int,
    val poorQuality: Int,
    val averageBandwidth: Long,
    val averageLatency: Long,
    val averagePacketLoss: Double
)