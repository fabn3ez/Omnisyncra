package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Simplified network error logger for demo
 */
class NetworkErrorLogger {
    
    private val _errorHistory = MutableStateFlow<List<ErrorDetails>>(emptyList())
    val errorHistory: StateFlow<List<ErrorDetails>> = _errorHistory.asStateFlow()
    
    private val _errorStatistics = MutableStateFlow(ErrorStatistics())
    val errorStatistics: StateFlow<ErrorStatistics> = _errorStatistics.asStateFlow()
    
    /**
     * Log a network error with detailed context
     */
    fun logError(
        error: NetworkError,
        context: ErrorContext,
        platform: String? = null
    ) {
        println("⚠️ Network Error: ${error.message} (Platform: $platform)")
    }
    
    /**
     * Log error with retry information
     */
    fun logRetryError(
        error: NetworkError,
        context: ErrorContext,
        retryCount: Int,
        lastRetryAt: Long,
        platform: String? = null
    ) {
        println("🔄 Retry $retryCount: ${error.message}")
    }
    
    /**
     * Get recent errors within time window
     */
    fun getRecentErrors(timeWindowMs: Long): List<ErrorDetails> {
        return emptyList()
    }
    
    /**
     * Clear error history
     */
    fun clearHistory() {
        _errorHistory.value = emptyList()
        _errorStatistics.value = ErrorStatistics()
    }
    
    /**
     * Clear old errors beyond retention period
     */
    fun cleanupOldErrors(retentionMs: Long = 24 * 60 * 60 * 1000L) {
        // Simplified for demo
    }
}

/**
 * Error statistics for monitoring and analysis
 */
@Serializable
data class ErrorStatistics(
    val totalErrors: Int = 0,
    val lastErrorTimestamp: Long = 0L
)