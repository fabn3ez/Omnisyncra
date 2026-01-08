package com.omnisyncra.core.monitoring

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.omnisyncra.core.security.SecuritySystem
import com.omnisyncra.core.network.NetworkCommunicator
import com.omnisyncra.core.ai.AISystem
import com.omnisyncra.core.platform.TimeUtils
import com.omnisyncra.core.performance.*
import com.benasher44.uuid.uuid4

/**
 * Enhanced Real System Monitor Implementation
 * Integrates PerformanceMonitor with existing RealSystemMonitor
 * Provides advanced performance analytics while preserving all existing functionality
 */
class EnhancedRealSystemMonitor(
    private val baseMonitor: RealSystemMonitor,
    private val performanceMonitor: PerformanceMonitor,
    private val securitySystem: SecuritySystem,
    private val networkCommunicator: NetworkCommunicator,
    private val aiSystem: AISystem,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : SystemMonitor, EnhancedPerformanceMonitor {
    
    // Delegate base functionality to existing RealSystemMonitor
    override val systemHealth: StateFlow<SystemHealth> = baseMonitor.systemHealth
    override val performanceMetrics: StateFlow<PerformanceMetrics> = baseMonitor.performanceMetrics
    override val securityStatus: StateFlow<SecurityStatus> = baseMonitor.securityStatus
    override val networkStatus: StateFlow<NetworkStatus> = baseMonitor.networkStatus
    
    // Enhanced performance tracking
    private val _enhancedMetrics = MutableStateFlow(createInitialEnhancedMetrics())
    val enhancedMetrics: StateFlow<EnhancedPerformanceMetrics> = _enhancedMetrics.asStateFlow()
    
    private var isEnhancedMonitoring = false
    
    override suspend fun initialize(): Result<Unit> {
        return try {
            // Initialize base monitor
            val baseResult = baseMonitor.initialize()
            if (baseResult.isFailure) {
                return baseResult
            }
            
            // Start enhanced monitoring
            startEnhancedMonitoring()
            
            logEnhancedEvent("Enhanced system monitor initialized with performance analytics")
            Result.success(Unit)
        } catch (e: Exception) {
            logEnhancedEvent("Failed to initialize enhanced monitor: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun startMonitoring(): Result<Unit> {
        return try {
            // Start base monitoring
            val baseResult = baseMonitor.startMonitoring()
            if (baseResult.isFailure) {
                return baseResult
            }
            
            isEnhancedMonitoring = true
            startEnhancedPerformanceTracking()
            
            logEnhancedEvent("Enhanced monitoring started with advanced analytics")
            Result.success(Unit)
        } catch (e: Exception) {
            logEnhancedEvent("Failed to start enhanced monitoring: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun stopMonitoring(): Result<Unit> {
        isEnhancedMonitoring = false
        val baseResult = baseMonitor.stopMonitoring()
        logEnhancedEvent("Enhanced monitoring stopped")
        return baseResult
    }
    
    // Delegate base methods to RealSystemMonitor
    override suspend fun getSystemAlerts(): Result<List<SystemAlert>> = baseMonitor.getSystemAlerts()
    override suspend fun getPerformanceHistory(timeRange: TimeRange): Result<List<PerformanceSnapshot>> = 
        baseMonitor.getPerformanceHistory(timeRange)
    override suspend fun getSystemLogs(level: LogLevel, limit: Int): Result<List<SystemLog>> = 
        baseMonitor.getSystemLogs(level, limit)
    override suspend fun generateSystemReport(): Result<SystemReport> = baseMonitor.generateSystemReport()
    override suspend fun shutdown() {
        isEnhancedMonitoring = false
        performanceMonitor.cleanup()
        baseMonitor.shutdown()
    }
    
    // Enhanced Performance Monitoring Interface Implementation
    override suspend fun startOperation(operationId: String): String {
        val trackingId = performanceMonitor.startOperation(operationId)
        logEnhancedEvent("Started tracking operation: $operationId")
        return trackingId
    }
    
    override suspend fun endOperation(trackingId: String, success: Boolean, tags: Map<String, String>) {
        performanceMonitor.endOperation(trackingId, success, tags)
        
        // Also record in base monitor for integration
        val duration = TimeUtils.currentTimeMillis() - (tags["startTime"]?.toLongOrNull() ?: 0L)
        baseMonitor.recordOperation(success, duration)
        
        logEnhancedEvent("Completed operation tracking: $trackingId (success: $success)")
    }
    
    override suspend fun <T> measureOperation(
        operation: String,
        tags: Map<String, String>,
        block: suspend () -> T
    ): T {
        return performanceMonitor.measureOperation(operation, tags) {
            val startTime = TimeUtils.currentTimeMillis()
            try {
                val result = block()
                val duration = TimeUtils.currentTimeMillis() - startTime
                baseMonitor.recordOperation(true, duration)
                result
            } catch (e: Exception) {
                val duration = TimeUtils.currentTimeMillis() - startTime
                baseMonitor.recordOperation(false, duration)
                throw e
            }
        }
    }
    
    override suspend fun recordThroughput(operation: String) {
        performanceMonitor.recordThroughput(operation)
        logEnhancedEvent("Recorded throughput for operation: $operation")
    }
    
    override suspend fun getLatencyStats(operation: String): LatencyStats? {
        return performanceMonitor.getLatencyStats(operation)
    }
    
    override suspend fun getThroughputStats(operation: String): ThroughputStats? {
        return performanceMonitor.getThroughputStats(operation)
    }
    
    override suspend fun getAllStats(): PerformanceStats {
        return performanceMonitor.getAllStats()
    }
    
    override fun cleanup() {
        performanceMonitor.cleanup()
    }
    
    // Enhanced monitoring functions
    private fun startEnhancedMonitoring() {
        scope.launch {
            performanceMonitor.metrics.collect { metric ->
                processPerformanceMetric(metric)
            }
        }
    }
    
    private fun startEnhancedPerformanceTracking() {
        scope.launch {
            while (isEnhancedMonitoring) {
                updateEnhancedMetrics()
                delay(5000) // Update every 5 seconds
            }
        }
    }
    
    private suspend fun updateEnhancedMetrics() {
        try {
            val allStats = performanceMonitor.getAllStats()
            val currentTime = TimeUtils.currentTimeMillis()
            
            val enhanced = EnhancedPerformanceMetrics(
                latencyStats = allStats.latencyStats,
                throughputStats = allStats.throughputStats,
                activeOperations = allStats.activeOperations,
                memoryUsageMB = allStats.memoryUsageMB,
                performanceScore = calculatePerformanceScore(allStats),
                bottlenecks = identifyPerformanceBottlenecks(allStats),
                recommendations = generatePerformanceRecommendations(allStats),
                timestamp = currentTime
            )
            
            _enhancedMetrics.value = enhanced
        } catch (e: Exception) {
            logEnhancedEvent("Failed to update enhanced metrics: ${e.message}")
        }
    }
    
    private suspend fun processPerformanceMetric(metric: PerformanceMetric) {
        // Process individual metrics for real-time analysis
        when (metric.name) {
            "latency" -> {
                val operation = metric.tags["operation"] ?: "unknown"
                if (metric.value > 1000.0) { // > 1 second
                    logEnhancedEvent("High latency detected for $operation: ${metric.value}ms")
                }
            }
            "throughput" -> {
                val operation = metric.tags["operation"] ?: "unknown"
                if (metric.value < 10.0) { // < 10 ops/min
                    logEnhancedEvent("Low throughput detected for $operation: ${metric.value} ops/min")
                }
            }
            "memory_usage" -> {
                if (metric.value > 100.0) { // > 100 MB
                    logEnhancedEvent("High memory usage detected: ${metric.value} MB")
                }
            }
        }
    }
    
    private fun calculatePerformanceScore(stats: PerformanceStats): Float {
        var score = 1.0f
        
        // Latency impact
        stats.latencyStats.forEach { latency ->
            when {
                latency.p95LatencyMs > 1000 -> score -= 0.3f
                latency.p95LatencyMs > 500 -> score -= 0.2f
                latency.p95LatencyMs > 200 -> score -= 0.1f
            }
            
            if (latency.successRate < 0.95) {
                score -= (1.0f - latency.successRate.toFloat()) * 0.5f
            }
        }
        
        // Throughput impact
        stats.throughputStats.forEach { throughput ->
            if (throughput.opsPerMinute < 10) {
                score -= 0.1f
            }
        }
        
        // Memory impact
        when {
            stats.memoryUsageMB > 200 -> score -= 0.2f
            stats.memoryUsageMB > 100 -> score -= 0.1f
        }
        
        // Active operations impact
        if (stats.activeOperations > 50) {
            score -= 0.1f
        }
        
        return maxOf(0.0f, score)
    }
    
    private fun identifyPerformanceBottlenecks(stats: PerformanceStats): List<String> {
        val bottlenecks = mutableListOf<String>()
        
        // Latency bottlenecks
        stats.latencyStats.forEach { latency ->
            if (latency.p95LatencyMs > 1000) {
                bottlenecks.add("High latency in ${latency.operation} (P95: ${latency.p95LatencyMs.toInt()}ms)")
            }
            if (latency.successRate < 0.9) {
                bottlenecks.add("Low success rate in ${latency.operation} (${(latency.successRate * 100).toInt()}%)")
            }
        }
        
        // Throughput bottlenecks
        stats.throughputStats.forEach { throughput ->
            if (throughput.opsPerMinute < 5) {
                bottlenecks.add("Low throughput in ${throughput.operation} (${throughput.opsPerMinute.toInt()} ops/min)")
            }
        }
        
        // Memory bottlenecks
        if (stats.memoryUsageMB > 150) {
            bottlenecks.add("High memory usage (${stats.memoryUsageMB.toInt()} MB)")
        }
        
        // Active operations bottlenecks
        if (stats.activeOperations > 30) {
            bottlenecks.add("High number of active operations (${stats.activeOperations})")
        }
        
        return bottlenecks
    }
    
    private fun generatePerformanceRecommendations(stats: PerformanceStats): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Latency recommendations
        stats.latencyStats.forEach { latency ->
            if (latency.p95LatencyMs > 500) {
                recommendations.add("Optimize ${latency.operation} - consider caching or async processing")
            }
            if (latency.successRate < 0.95) {
                recommendations.add("Improve error handling for ${latency.operation} - success rate is ${(latency.successRate * 100).toInt()}%")
            }
        }
        
        // Throughput recommendations
        stats.throughputStats.forEach { throughput ->
            if (throughput.opsPerMinute < 10) {
                recommendations.add("Increase throughput for ${throughput.operation} - consider batch processing")
            }
        }
        
        // Memory recommendations
        if (stats.memoryUsageMB > 100) {
            recommendations.add("Reduce memory usage - consider implementing data cleanup or compression")
        }
        
        // General recommendations
        if (stats.activeOperations > 20) {
            recommendations.add("Consider implementing operation queuing to limit concurrent operations")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Performance is optimal - no immediate optimizations needed")
        }
        
        return recommendations
    }
    
    private fun createInitialEnhancedMetrics(): EnhancedPerformanceMetrics {
        return EnhancedPerformanceMetrics(
            latencyStats = emptyList(),
            throughputStats = emptyList(),
            activeOperations = 0,
            memoryUsageMB = 0.0,
            performanceScore = 1.0f,
            bottlenecks = emptyList(),
            recommendations = listOf("Enhanced monitoring initialized - collecting baseline metrics"),
            timestamp = TimeUtils.currentTimeMillis()
        )
    }
    
    private fun logEnhancedEvent(message: String) {
        println("📊 EnhancedMonitor: $message")
    }
    
    // Public methods for recording operations (enhanced interface)
    fun recordEnhancedOperation(operation: String, success: Boolean, duration: Long, tags: Map<String, String> = emptyMap()) {
        scope.launch {
            // Record in performance monitor
            val trackingId = performanceMonitor.startOperation(operation)
            performanceMonitor.endOperation(trackingId, success, tags + mapOf("duration" to duration.toString()))
            
            // Record in base monitor
            baseMonitor.recordOperation(success, duration)
        }
    }
    
    fun recordEnhancedTask(operation: String, completed: Boolean) {
        scope.launch {
            recordThroughput(operation)
            baseMonitor.recordTask(completed)
        }
    }
    
    fun recordEnhancedError(component: String, operation: String? = null) {
        scope.launch {
            baseMonitor.recordError(component)
            operation?.let { 
                val trackingId = performanceMonitor.startOperation(it)
                performanceMonitor.endOperation(trackingId, false, mapOf("error_component" to component))
            }
        }
    }
}

/**
 * Enhanced Performance Monitor Interface
 */
interface EnhancedPerformanceMonitor : SystemMonitor {
    suspend fun startOperation(operationId: String): String
    suspend fun endOperation(trackingId: String, success: Boolean = true, tags: Map<String, String> = emptyMap())
    suspend fun <T> measureOperation(operation: String, tags: Map<String, String> = emptyMap(), block: suspend () -> T): T
    suspend fun recordThroughput(operation: String)
    suspend fun getLatencyStats(operation: String): LatencyStats?
    suspend fun getThroughputStats(operation: String): ThroughputStats?
    suspend fun getAllStats(): PerformanceStats
    fun cleanup()
}

/**
 * Enhanced Performance Metrics
 */
data class EnhancedPerformanceMetrics(
    val latencyStats: List<LatencyStats>,
    val throughputStats: List<ThroughputStats>,
    val activeOperations: Int,
    val memoryUsageMB: Double,
    val performanceScore: Float,
    val bottlenecks: List<String>,
    val recommendations: List<String>,
    val timestamp: Long
)