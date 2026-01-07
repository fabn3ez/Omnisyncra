package com.omnisyncra.core.monitoring

import kotlinx.coroutines.flow.StateFlow
import com.omnisyncra.core.platform.TimeUtils

/**
 * System Monitoring Interface
 * Provides comprehensive system health and performance monitoring
 */
interface SystemMonitor {
    /**
     * Overall system health status
     */
    val systemHealth: StateFlow<SystemHealth>
    
    /**
     * Performance metrics
     */
    val performanceMetrics: StateFlow<PerformanceMetrics>
    
    /**
     * Security status
     */
    val securityStatus: StateFlow<SecurityStatus>
    
    /**
     * Network status
     */
    val networkStatus: StateFlow<NetworkStatus>
    
    /**
     * Initialize monitoring system
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * Start monitoring
     */
    suspend fun startMonitoring(): Result<Unit>
    
    /**
     * Stop monitoring
     */
    suspend fun stopMonitoring(): Result<Unit>
    
    /**
     * Get system alerts
     */
    suspend fun getSystemAlerts(): Result<List<SystemAlert>>
    
    /**
     * Get performance history
     */
    suspend fun getPerformanceHistory(timeRange: TimeRange): Result<List<PerformanceSnapshot>>
    
    /**
     * Get system logs
     */
    suspend fun getSystemLogs(level: LogLevel, limit: Int): Result<List<SystemLog>>
    
    /**
     * Generate system report
     */
    suspend fun generateSystemReport(): Result<SystemReport>
    
    /**
     * Shutdown monitoring
     */
    suspend fun shutdown()
}

/**
 * System Health Status
 */
data class SystemHealth(
    val overallStatus: HealthStatus,
    val cpuUsage: Float, // 0.0 to 1.0
    val memoryUsage: Float, // 0.0 to 1.0
    val diskUsage: Float, // 0.0 to 1.0
    val networkLatency: Long, // milliseconds
    val activeConnections: Int,
    val errorRate: Float, // errors per minute
    val uptime: Long, // milliseconds
    val lastUpdated: Long = TimeUtils.currentTimeMillis()
)

/**
 * Health Status Levels
 */
enum class HealthStatus {
    EXCELLENT,
    GOOD,
    WARNING,
    CRITICAL,
    UNKNOWN
}

/**
 * Performance Metrics
 */
data class PerformanceMetrics(
    val throughput: Long, // operations per second
    val responseTime: Long, // average response time in ms
    val concurrentTasks: Int,
    val queuedTasks: Int,
    val completedTasks: Long,
    val failedTasks: Long,
    val cacheHitRate: Float, // 0.0 to 1.0
    val gcPressure: Float, // 0.0 to 1.0 (garbage collection pressure)
    val timestamp: Long = TimeUtils.currentTimeMillis()
)

/**
 * Security Status
 */
data class SecurityStatus(
    val threatLevel: ThreatLevel,
    val activeThreats: Int,
    val blockedAttempts: Long,
    val encryptionStatus: EncryptionStatus,
    val certificateStatus: CertificateStatus,
    val lastSecurityScan: Long,
    val vulnerabilities: List<SecurityVulnerability>,
    val timestamp: Long = TimeUtils.currentTimeMillis()
)

/**
 * Threat Levels
 */
enum class ThreatLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Encryption Status
 */
enum class EncryptionStatus {
    ACTIVE,
    DEGRADED,
    INACTIVE,
    ERROR
}

/**
 * Certificate Status
 */
enum class CertificateStatus {
    VALID,
    EXPIRING_SOON,
    EXPIRED,
    INVALID
}

/**
 * Security Vulnerability
 */
data class SecurityVulnerability(
    val id: String,
    val severity: VulnerabilitySeverity,
    val description: String,
    val component: String,
    val discoveredAt: Long,
    val mitigated: Boolean
)

/**
 * Vulnerability Severity
 */
enum class VulnerabilitySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Network Status
 */
data class NetworkStatus(
    val connectionState: NetworkConnectionState,
    val bandwidth: Long, // bytes per second
    val packetLoss: Float, // 0.0 to 1.0
    val jitter: Long, // milliseconds
    val connectedDevices: Int,
    val dataTransferred: Long, // total bytes
    val networkErrors: Long,
    val timestamp: Long = TimeUtils.currentTimeMillis()
)

/**
 * Network Connection State
 */
enum class NetworkConnectionState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    LIMITED,
    ERROR
}

/**
 * System Alert
 */
data class SystemAlert(
    val id: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val title: String,
    val description: String,
    val component: String,
    val createdAt: Long,
    val acknowledged: Boolean = false,
    val resolved: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Alert Types
 */
enum class AlertType {
    PERFORMANCE,
    SECURITY,
    NETWORK,
    STORAGE,
    MEMORY,
    CPU,
    APPLICATION,
    SYSTEM
}

/**
 * Alert Severity
 */
enum class AlertSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Performance Snapshot
 */
data class PerformanceSnapshot(
    val timestamp: Long,
    val cpuUsage: Float,
    val memoryUsage: Float,
    val networkLatency: Long,
    val throughput: Long,
    val errorRate: Float
)

/**
 * Time Range for queries
 */
data class TimeRange(
    val startTime: Long,
    val endTime: Long
)

/**
 * System Log Entry
 */
data class SystemLog(
    val timestamp: Long,
    val level: LogLevel,
    val component: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
    val exception: String? = null
)

/**
 * Log Levels
 */
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}

/**
 * System Report
 */
data class SystemReport(
    val generatedAt: Long,
    val reportPeriod: TimeRange,
    val systemHealth: SystemHealth,
    val performanceSummary: PerformanceSummary,
    val securitySummary: SecuritySummary,
    val networkSummary: NetworkSummary,
    val alerts: List<SystemAlert>,
    val recommendations: List<String>
)

/**
 * Performance Summary
 */
data class PerformanceSummary(
    val averageResponseTime: Long,
    val peakThroughput: Long,
    val totalTasksCompleted: Long,
    val successRate: Float,
    val bottlenecks: List<String>
)

/**
 * Security Summary
 */
data class SecuritySummary(
    val threatsDetected: Int,
    val threatsBlocked: Int,
    val vulnerabilitiesFound: Int,
    val securityScore: Float, // 0.0 to 1.0
    val recommendations: List<String>
)

/**
 * Network Summary
 */
data class NetworkSummary(
    val averageBandwidth: Long,
    val totalDataTransferred: Long,
    val connectionUptime: Float, // 0.0 to 1.0
    val networkErrors: Long,
    val peakConnections: Int
)