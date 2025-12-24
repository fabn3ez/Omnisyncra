package com.omnisyncra.core.performance

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.math.max
import kotlin.math.min

data class PerformanceMetric(
    val name: String,
    val value: Double,
    val unit: String,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val tags: Map<String, String> = emptyMap()
)

data class LatencyMeasurement(
    val operation: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long = endTime - startTime,
    val success: Boolean = true,
    val tags: Map<String, String> = emptyMap()
)

data class ThroughputMeasurement(
    val operation: String,
    val count: Int,
    val windowMs: Long,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

class PerformanceMonitor {
    private val _metrics = MutableSharedFlow<PerformanceMetric>()
    val metrics: SharedFlow<PerformanceMetric> = _metrics.asSharedFlow()
    
    private val latencyHistory = mutableMapOf<String, MutableList<LatencyMeasurement>>()
    private val throughputCounters = mutableMapOf<String, MutableList<Long>>()
    private val activeOperations = mutableMapOf<String, Long>()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // Start periodic metric collection
        scope.launch {
            while (isActive) {
                delay(10_000L) // Every 10 seconds
                collectSystemMetrics()
            }
        }
        
        // Start throughput calculation
        scope.launch {
            while (isActive) {
                delay(5_000L) // Every 5 seconds
                calculateThroughputMetrics()
            }
        }
    }
    
    // Latency Tracking
    fun startOperation(operationId: String): String {
        val trackingId = "${operationId}_${com.benasher44.uuid.uuid4()}"
        activeOperations[trackingId] = Clock.System.now().toEpochMilliseconds()
        return trackingId
    }
    
    suspend fun endOperation(
        trackingId: String,
        success: Boolean = true,
        tags: Map<String, String> = emptyMap()
    ) {
        val startTime = activeOperations.remove(trackingId) ?: return
        val endTime = Clock.System.now().toEpochMilliseconds()
        
        val operation = trackingId.substringBefore("_")
        val measurement = LatencyMeasurement(
            operation = operation,
            startTime = startTime,
            endTime = endTime,
            success = success,
            tags = tags
        )
        
        recordLatency(measurement)
    }
    
    suspend fun <T> measureOperation(
        operation: String,
        tags: Map<String, String> = emptyMap(),
        block: suspend () -> T
    ): T {
        val startTime = Clock.System.now().toEpochMilliseconds()
        var success = true
        
        return try {
            block()
        } catch (e: Exception) {
            success = false
            throw e
        } finally {
            val endTime = Clock.System.now().toEpochMilliseconds()
            val measurement = LatencyMeasurement(
                operation = operation,
                startTime = startTime,
                endTime = endTime,
                success = success,
                tags = tags
            )
            recordLatency(measurement)
        }
    }
    
    private suspend fun recordLatency(measurement: LatencyMeasurement) {
        // Store in history (keep last 1000 measurements per operation)
        val history = latencyHistory.getOrPut(measurement.operation) { mutableListOf() }
        history.add(measurement)
        if (history.size > 1000) {
            history.removeFirst()
        }
        
        // Emit metric
        _metrics.emit(
            PerformanceMetric(
                name = "latency",
                value = measurement.durationMs.toDouble(),
                unit = "ms",
                tags = mapOf(
                    "operation" to measurement.operation,
                    "success" to measurement.success.toString()
                ) + measurement.tags
            )
        )
    }
    
    // Throughput Tracking
    suspend fun recordThroughput(operation: String) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val counters = throughputCounters.getOrPut(operation) { mutableListOf() }
        counters.add(currentTime)
        
        // Keep only last 5 minutes of data
        val fiveMinutesAgo = currentTime - 300_000L
        counters.removeAll { it < fiveMinutesAgo }
    }
    
    private suspend fun calculateThroughputMetrics() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        throughputCounters.forEach { (operation, timestamps) ->
            // Calculate operations per minute
            val oneMinuteAgo = currentTime - 60_000L
            val recentOperations = timestamps.count { it >= oneMinuteAgo }
            
            _metrics.emit(
                PerformanceMetric(
                    name = "throughput",
                    value = recentOperations.toDouble(),
                    unit = "ops/min",
                    tags = mapOf("operation" to operation)
                )
            )
        }
    }
    
    // System Metrics
    private suspend fun collectSystemMetrics() {
        // Memory usage (simplified estimation)
        val memoryUsage = estimateMemoryUsage()
        _metrics.emit(
            PerformanceMetric(
                name = "memory_usage",
                value = memoryUsage,
                unit = "MB"
            )
        )
        
        // Active operations count
        _metrics.emit(
            PerformanceMetric(
                name = "active_operations",
                value = activeOperations.size.toDouble(),
                unit = "count"
            )
        )
    }
    
    private fun estimateMemoryUsage(): Double {
        // Simplified memory estimation
        val historySize = latencyHistory.values.sumOf { it.size } * 100 // ~100 bytes per measurement
        val throughputSize = throughputCounters.values.sumOf { it.size } * 8 // 8 bytes per timestamp
        val activeOpsSize = activeOperations.size * 50 // ~50 bytes per active operation
        
        return (historySize + throughputSize + activeOpsSize) / (1024.0 * 1024.0) // Convert to MB
    }
    
    // Analytics
    suspend fun getLatencyStats(operation: String): LatencyStats? {
        val measurements = latencyHistory[operation] ?: return null
        if (measurements.isEmpty()) return null
        
        val durations = measurements.map { it.durationMs.toDouble() }
        val successfulMeasurements = measurements.filter { it.success }
        
        return LatencyStats(
            operation = operation,
            count = measurements.size,
            successCount = successfulMeasurements.size,
            successRate = successfulMeasurements.size.toDouble() / measurements.size,
            avgLatencyMs = durations.average(),
            minLatencyMs = durations.minOrNull() ?: 0.0,
            maxLatencyMs = durations.maxOrNull() ?: 0.0,
            p50LatencyMs = durations.sorted().let { sorted ->
                if (sorted.isEmpty()) 0.0 else sorted[sorted.size / 2]
            },
            p95LatencyMs = durations.sorted().let { sorted ->
                if (sorted.isEmpty()) 0.0 else sorted[(sorted.size * 0.95).toInt()]
            },
            p99LatencyMs = durations.sorted().let { sorted ->
                if (sorted.isEmpty()) 0.0 else sorted[(sorted.size * 0.99).toInt()]
            }
        )
    }
    
    suspend fun getThroughputStats(operation: String): ThroughputStats? {
        val timestamps = throughputCounters[operation] ?: return null
        if (timestamps.isEmpty()) return null
        
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val oneMinuteAgo = currentTime - 60_000L
        val fiveMinutesAgo = currentTime - 300_000L
        
        val opsLastMinute = timestamps.count { it >= oneMinuteAgo }
        val opsLastFiveMinutes = timestamps.count { it >= fiveMinutesAgo }
        
        return ThroughputStats(
            operation = operation,
            opsPerMinute = opsLastMinute.toDouble(),
            opsPerFiveMinutes = opsLastFiveMinutes.toDouble(),
            totalOperations = timestamps.size
        )
    }
    
    suspend fun getAllStats(): PerformanceStats {
        val latencyStats = latencyHistory.keys.mapNotNull { getLatencyStats(it) }
        val throughputStats = throughputCounters.keys.mapNotNull { getThroughputStats(it) }
        
        return PerformanceStats(
            latencyStats = latencyStats,
            throughputStats = throughputStats,
            activeOperations = activeOperations.size,
            memoryUsageMB = estimateMemoryUsage()
        )
    }
    
    fun cleanup() {
        scope.cancel()
        latencyHistory.clear()
        throughputCounters.clear()
        activeOperations.clear()
    }
}

data class LatencyStats(
    val operation: String,
    val count: Int,
    val successCount: Int,
    val successRate: Double,
    val avgLatencyMs: Double,
    val minLatencyMs: Double,
    val maxLatencyMs: Double,
    val p50LatencyMs: Double,
    val p95LatencyMs: Double,
    val p99LatencyMs: Double
)

data class ThroughputStats(
    val operation: String,
    val opsPerMinute: Double,
    val opsPerFiveMinutes: Double,
    val totalOperations: Int
)

data class PerformanceStats(
    val latencyStats: List<LatencyStats>,
    val throughputStats: List<ThroughputStats>,
    val activeOperations: Int,
    val memoryUsageMB: Double
)