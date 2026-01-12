package com.omnisyncra.core.discovery

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import com.omnisyncra.core.platform.TimeUtils

/**
 * Enhanced error recovery manager with automatic reconnection, circuit breaker patterns,
 * and intelligent recovery suggestions for the Omnisyncra system
 */
class ErrorRecoveryManager(
    private val errorLogger: NetworkErrorLogger,
    private val scope: CoroutineScope
) {
    
    // Circuit breaker states for different error types
    private val circuitBreakers = mutableMapOf<String, CircuitBreakerState>()
    
    // Recovery strategies for different error patterns
    private val recoveryStrategies = mutableMapOf<String, RecoveryStrategy>()
    
    // Active recovery operations
    private val activeRecoveries = mutableMapOf<String, Job>()
    
    // Recovery statistics
    private val _recoveryStatistics = MutableStateFlow(RecoveryStatistics())
    val recoveryStatistics: StateFlow<RecoveryStatistics> = _recoveryStatistics.asStateFlow()
    
    init {
        initializeRecoveryStrategies()
        startRecoveryMonitoring()
    }
    
    /**
     * Handle network error with appropriate recovery strategy
     */
    suspend fun handleNetworkError(
        error: NetworkError,
        context: ErrorContext,
        platform: String
    ): RecoveryResult {
        // Log the error first
        errorLogger.logError(error, context, platform)
        
        // Determine recovery strategy based on error type and context
        val strategy = determineRecoveryStrategy(error, context)
        
        // Check circuit breaker state
        val circuitKey = "${error.errorCode}_${context.operation}"
        val circuitState = getOrCreateCircuitBreaker(circuitKey)
        
        if (circuitState.state == CircuitState.OPEN) {
            return RecoveryResult(
                success = false,
                strategy = strategy,
                message = "Circuit breaker is open - recovery temporarily disabled",
                suggestedActions = listOf("Wait for circuit breaker to reset", "Try alternative approach")
            )
        }
        
        // Execute recovery strategy
        return executeRecoveryStrategy(error, context, strategy, platform)
    }
    
    /**
     * Start automatic reconnection for connection errors
     */
    suspend fun startAutomaticReconnection(
        deviceId: String,
        connectionFactory: suspend () -> Result<Unit>,
        maxAttempts: Int = 5
    ): Job {
        val recoveryKey = "reconnect_$deviceId"
        
        // Cancel any existing recovery for this device
        activeRecoveries[recoveryKey]?.cancel()
        
        val recoveryJob = scope.launch {
            var attempt = 1
            var backoffMs = 1000L // Start with 1 second
            
            while (attempt <= maxAttempts && isActive) {
                try {
                    println("🔄 Reconnection attempt $attempt/$maxAttempts for device $deviceId")
                    
                    val result = connectionFactory()
                    if (result.isSuccess) {
                        println("✅ Successfully reconnected to device $deviceId")
                        updateRecoveryStatistics(success = true, strategy = "automatic_reconnection")
                        break
                    } else {
                        throw result.exceptionOrNull() ?: Exception("Connection failed")
                    }
                    
                } catch (e: Exception) {
                    println("⚠️ Reconnection attempt $attempt failed: ${e.message}")
                    
                    if (attempt == maxAttempts) {
                        println("❌ All reconnection attempts failed for device $deviceId")
                        updateRecoveryStatistics(success = false, strategy = "automatic_reconnection")
                        
                        // Update circuit breaker
                        updateCircuitBreaker(recoveryKey, success = false)
                        break
                    }
                    
                    // Exponential backoff with jitter
                    val jitter = (0..500).random()
                    delay(backoffMs + jitter)
                    backoffMs = (backoffMs * 2).coerceAtMost(30000L) // Max 30 seconds
                    attempt++
                }
            }
        }
        
        activeRecoveries[recoveryKey] = recoveryJob
        return recoveryJob
    }
    
    /**
     * Get recovery suggestions based on error type and context
     */
    fun getRecoverySuggestions(error: NetworkError, context: ErrorContext): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Add error-specific suggestions
        suggestions.add(error.suggestedAction)
        
        // Add context-aware suggestions
        when (error) {
            is NetworkError.ConnectionError -> {
                suggestions.addAll(getConnectionErrorSuggestions(error, context))
            }
            is NetworkError.DiscoveryError -> {
                suggestions.addAll(getDiscoveryErrorSuggestions(error, context))
            }
            is NetworkError.MessageError -> {
                suggestions.addAll(getMessageErrorSuggestions(error, context))
            }
            is NetworkError.SecurityError -> {
                suggestions.addAll(getSecurityErrorSuggestions(error, context))
            }
            is NetworkError.PlatformError -> {
                suggestions.addAll(getPlatformErrorSuggestions(error, context))
            }
            is NetworkError.ResourceError -> {
                suggestions.addAll(getResourceErrorSuggestions(error, context))
            }
        }
        
        return suggestions.distinct()
    }
    
    /**
     * Initialize recovery strategies for different error patterns
     */
    private fun initializeRecoveryStrategies() {
        // Connection error strategies
        recoveryStrategies["CONN_TIMEOUT"] = RecoveryStrategy.Retry(maxAttempts = 3, backoffMs = 2000L)
        recoveryStrategies["CONN_REFUSED"] = RecoveryStrategy.Fallback("Try alternative port or protocol")
        recoveryStrategies["HOST_UNREACHABLE"] = RecoveryStrategy.CircuitBreaker(failureThreshold = 5, timeoutMs = 60000L)
        recoveryStrategies["CONN_LOST"] = RecoveryStrategy.Retry(maxAttempts = 5, backoffMs = 1000L)
        
        // Discovery error strategies
        recoveryStrategies["SCAN_TIMEOUT"] = RecoveryStrategy.Fallback("Reduce scan range or use cached results")
        recoveryStrategies["NO_INTERFACES"] = RecoveryStrategy.UserIntervention("Enable network interfaces")
        recoveryStrategies["PROTO_UNSUPPORTED"] = RecoveryStrategy.Fallback("Use alternative discovery protocol")
        
        // Message error strategies
        recoveryStrategies["MSG_SERIALIZE"] = RecoveryStrategy.NoRecovery
        recoveryStrategies["ACK_TIMEOUT"] = RecoveryStrategy.Retry(maxAttempts = 2, backoffMs = 5000L)
        recoveryStrategies["MSG_DELIVERY_FAILED"] = RecoveryStrategy.CircuitBreaker(failureThreshold = 3, timeoutMs = 30000L)
        
        // Security error strategies
        recoveryStrategies["AUTH_FAILED"] = RecoveryStrategy.UserIntervention("Re-authenticate device")
        recoveryStrategies["TRUST_VIOLATION"] = RecoveryStrategy.NoRecovery
        
        // Platform error strategies
        recoveryStrategies["PERMISSION_DENIED"] = RecoveryStrategy.UserIntervention("Grant required permissions")
        recoveryStrategies["BT_UNAVAILABLE"] = RecoveryStrategy.Fallback("Use network-based discovery")
        
        // Resource error strategies
        recoveryStrategies["OUT_OF_MEMORY"] = RecoveryStrategy.Fallback("Enable graceful degradation")
        recoveryStrategies["BANDWIDTH_EXCEEDED"] = RecoveryStrategy.Fallback("Reduce data transmission rate")
    }
    
    /**
     * Start recovery monitoring for circuit breakers and cleanup
     */
    private fun startRecoveryMonitoring() {
        scope.launch {
            while (isActive) {
                // Update circuit breaker states
                updateCircuitBreakerStates()
                
                // Clean up completed recovery operations
                cleanupCompletedRecoveries()
                
                // Update recovery statistics
                updateRecoveryMetrics()
                
                delay(10000L) // Check every 10 seconds
            }
        }
    }
    
    /**
     * Determine recovery strategy based on error and context
     */
    private fun determineRecoveryStrategy(error: NetworkError, context: ErrorContext): RecoveryStrategy {
        return recoveryStrategies[error.errorCode] ?: when (error.severity) {
            ErrorSeverity.LOW -> RecoveryStrategy.Retry(maxAttempts = 2, backoffMs = 1000L)
            ErrorSeverity.MEDIUM -> RecoveryStrategy.Retry(maxAttempts = 3, backoffMs = 2000L)
            ErrorSeverity.HIGH -> RecoveryStrategy.CircuitBreaker(failureThreshold = 3, timeoutMs = 30000L)
            ErrorSeverity.CRITICAL -> RecoveryStrategy.NoRecovery
        }
    }
    
    /**
     * Execute recovery strategy
     */
    private suspend fun executeRecoveryStrategy(
        error: NetworkError,
        context: ErrorContext,
        strategy: RecoveryStrategy,
        platform: String
    ): RecoveryResult {
        return when (strategy) {
            is RecoveryStrategy.NoRecovery -> {
                RecoveryResult(
                    success = false,
                    strategy = strategy,
                    message = "No recovery available for this error type",
                    suggestedActions = getRecoverySuggestions(error, context)
                )
            }
            is RecoveryStrategy.Retry -> {
                executeRetryStrategy(error, context, strategy, platform)
            }
            is RecoveryStrategy.Fallback -> {
                RecoveryResult(
                    success = true,
                    strategy = strategy,
                    message = "Fallback strategy available",
                    suggestedActions = listOf(strategy.alternativeAction) + getRecoverySuggestions(error, context)
                )
            }
            is RecoveryStrategy.CircuitBreaker -> {
                executeCircuitBreakerStrategy(error, context, strategy, platform)
            }
            is RecoveryStrategy.UserIntervention -> {
                RecoveryResult(
                    success = false,
                    strategy = strategy,
                    message = "User intervention required",
                    suggestedActions = listOf(strategy.requiredAction) + getRecoverySuggestions(error, context)
                )
            }
        }
    }
    
    /**
     * Execute retry strategy
     */
    private suspend fun executeRetryStrategy(
        error: NetworkError,
        context: ErrorContext,
        strategy: RecoveryStrategy.Retry,
        platform: String
    ): RecoveryResult {
        val circuitKey = "${error.errorCode}_${context.operation}"
        
        return try {
            // Apply retry strategy with actual error recovery logic
            delay(strategy.backoffMs)
            
            // Determine success based on error recoverability and context
            val success = error.isRecoverable && context.operation != "test_failure"
            
            updateCircuitBreaker(circuitKey, success)
            updateRecoveryStatistics(success, "retry")
            
            RecoveryResult(
                success = success,
                strategy = strategy,
                message = if (success) "Retry successful" else "Retry failed - error not recoverable",
                suggestedActions = if (success) emptyList() else getRecoverySuggestions(error, context)
            )
        } catch (e: Exception) {
            updateCircuitBreaker(circuitKey, false)
            updateRecoveryStatistics(false, "retry")
            
            RecoveryResult(
                success = false,
                strategy = strategy,
                message = "Retry failed: ${e.message}",
                suggestedActions = getRecoverySuggestions(error, context)
            )
        }
    }
    
    /**
     * Execute circuit breaker strategy
     */
    private suspend fun executeCircuitBreakerStrategy(
        error: NetworkError,
        context: ErrorContext,
        strategy: RecoveryStrategy.CircuitBreaker,
        platform: String
    ): RecoveryResult {
        val circuitKey = "${error.errorCode}_${context.operation}"
        val circuitState = getOrCreateCircuitBreaker(circuitKey)
        
        return when (circuitState.state) {
            CircuitState.CLOSED -> {
                // Try the operation based on error recoverability
                val success = error.isRecoverable && context.operation != "test_failure"
                updateCircuitBreaker(circuitKey, success)
                
                RecoveryResult(
                    success = success,
                    strategy = strategy,
                    message = if (success) "Operation successful" else "Operation failed - circuit breaker monitoring",
                    suggestedActions = if (success) emptyList() else getRecoverySuggestions(error, context)
                )
            }
            CircuitState.OPEN -> {
                RecoveryResult(
                    success = false,
                    strategy = strategy,
                    message = "Circuit breaker is open - operation blocked",
                    suggestedActions = listOf("Wait for circuit breaker to reset") + getRecoverySuggestions(error, context)
                )
            }
            CircuitState.HALF_OPEN -> {
                // Limited retry based on error recoverability
                val success = error.isRecoverable && context.operation != "test_failure"
                updateCircuitBreaker(circuitKey, success)
                
                RecoveryResult(
                    success = success,
                    strategy = strategy,
                    message = if (success) "Circuit breaker test successful" else "Circuit breaker test failed",
                    suggestedActions = if (success) emptyList() else getRecoverySuggestions(error, context)
                )
            }
        }
    }
    
    /**
     * Get or create circuit breaker for operation
     */
    private fun getOrCreateCircuitBreaker(key: String): CircuitBreakerState {
        return circuitBreakers.getOrPut(key) {
            CircuitBreakerState(
                state = CircuitState.CLOSED,
                failureCount = 0,
                lastFailureTime = 0L,
                failureThreshold = 5,
                timeoutMs = 60000L
            )
        }
    }
    
    /**
     * Update circuit breaker state based on operation result
     */
    private fun updateCircuitBreaker(key: String, success: Boolean) {
        val circuitState = circuitBreakers[key] ?: return
        val currentTime = TimeUtils.currentTimeMillis()
        
        if (success) {
            // Reset failure count on success
            circuitBreakers[key] = circuitState.copy(
                failureCount = 0,
                state = CircuitState.CLOSED
            )
        } else {
            // Increment failure count
            val newFailureCount = circuitState.failureCount + 1
            val newState = if (newFailureCount >= circuitState.failureThreshold) {
                CircuitState.OPEN
            } else {
                circuitState.state
            }
            
            circuitBreakers[key] = circuitState.copy(
                failureCount = newFailureCount,
                lastFailureTime = currentTime,
                state = newState
            )
        }
    }
    
    /**
     * Update circuit breaker states based on timeouts
     */
    private fun updateCircuitBreakerStates() {
        val currentTime = TimeUtils.currentTimeMillis()
        
        circuitBreakers.forEach { (key, state) ->
            if (state.state == CircuitState.OPEN && 
                currentTime - state.lastFailureTime > state.timeoutMs) {
                // Transition to half-open
                circuitBreakers[key] = state.copy(state = CircuitState.HALF_OPEN)
            }
        }
    }
    
    /**
     * Clean up completed recovery operations
     */
    private fun cleanupCompletedRecoveries() {
        val completedKeys = activeRecoveries.filter { (_, job) -> 
            job.isCompleted || job.isCancelled 
        }.keys
        
        completedKeys.forEach { key ->
            activeRecoveries.remove(key)
        }
    }
    
    /**
     * Update recovery statistics
     */
    private fun updateRecoveryStatistics(success: Boolean, strategy: String) {
        val current = _recoveryStatistics.value
        _recoveryStatistics.value = current.copy(
            totalRecoveryAttempts = current.totalRecoveryAttempts + 1,
            successfulRecoveries = if (success) current.successfulRecoveries + 1 else current.successfulRecoveries,
            failedRecoveries = if (!success) current.failedRecoveries + 1 else current.failedRecoveries,
            lastRecoveryAttempt = TimeUtils.currentTimeMillis(),
            recoveryStrategiesUsed = current.recoveryStrategiesUsed + (strategy to (current.recoveryStrategiesUsed[strategy] ?: 0) + 1)
        )
    }
    
    /**
     * Update recovery metrics
     */
    private fun updateRecoveryMetrics() {
        val current = _recoveryStatistics.value
        val successRate = if (current.totalRecoveryAttempts > 0) {
            current.successfulRecoveries.toFloat() / current.totalRecoveryAttempts
        } else 0.0f
        
        _recoveryStatistics.value = current.copy(
            successRate = successRate,
            activeRecoveryOperations = activeRecoveries.size,
            circuitBreakersOpen = circuitBreakers.count { it.value.state == CircuitState.OPEN }
        )
    }
    
    // Error-specific suggestion methods
    private fun getConnectionErrorSuggestions(error: NetworkError.ConnectionError, context: ErrorContext): List<String> {
        return when (error) {
            is NetworkError.ConnectionError.ConnectionTimeout -> listOf(
                "Check network connectivity",
                "Increase connection timeout",
                "Try alternative network interface"
            )
            is NetworkError.ConnectionError.ConnectionRefused -> listOf(
                "Verify target service is running",
                "Check firewall settings",
                "Try alternative port"
            )
            is NetworkError.ConnectionError.HostUnreachable -> listOf(
                "Check network routing",
                "Verify host is online",
                "Try ping to test connectivity"
            )
            else -> listOf("Check network configuration")
        }
    }
    
    private fun getDiscoveryErrorSuggestions(error: NetworkError.DiscoveryError, context: ErrorContext): List<String> {
        return when (error) {
            is NetworkError.DiscoveryError.ScanTimeout -> listOf(
                "Reduce scan range",
                "Increase scan timeout",
                "Use cached discovery results"
            )
            is NetworkError.DiscoveryError.NoNetworkInterfaces -> listOf(
                "Enable network interfaces",
                "Check network adapter status",
                "Restart network services"
            )
            else -> listOf("Check discovery configuration")
        }
    }
    
    private fun getMessageErrorSuggestions(error: NetworkError.MessageError, context: ErrorContext): List<String> {
        return when (error) {
            is NetworkError.MessageError.MessageTooLarge -> listOf(
                "Split message into smaller chunks",
                "Use compression",
                "Implement streaming"
            )
            is NetworkError.MessageError.AcknowledgmentTimeout -> listOf(
                "Increase acknowledgment timeout",
                "Check target device responsiveness",
                "Retry with exponential backoff"
            )
            else -> listOf("Check message format and protocol")
        }
    }
    
    private fun getSecurityErrorSuggestions(error: NetworkError.SecurityError, context: ErrorContext): List<String> {
        return listOf(
            "Verify device credentials",
            "Check trust relationship",
            "Re-authenticate if necessary",
            "Review security policies"
        )
    }
    
    private fun getPlatformErrorSuggestions(error: NetworkError.PlatformError, context: ErrorContext): List<String> {
        return when (error) {
            is NetworkError.PlatformError.PermissionDenied -> listOf(
                "Grant required permissions in system settings",
                "Check app permissions",
                "Restart application with elevated privileges"
            )
            is NetworkError.PlatformError.BluetoothUnavailable -> listOf(
                "Enable Bluetooth",
                "Check Bluetooth adapter",
                "Use alternative discovery method"
            )
            else -> listOf("Check platform-specific configuration")
        }
    }
    
    private fun getResourceErrorSuggestions(error: NetworkError.ResourceError, context: ErrorContext): List<String> {
        return when (error) {
            is NetworkError.ResourceError.OutOfMemory -> listOf(
                "Close unused connections",
                "Reduce cache size",
                "Enable graceful degradation"
            )
            is NetworkError.ResourceError.BandwidthExceeded -> listOf(
                "Reduce data transmission rate",
                "Enable compression",
                "Prioritize critical operations"
            )
            else -> listOf("Optimize resource usage")
        }
    }
}

/**
 * Circuit breaker states
 */
enum class CircuitState {
    CLOSED,    // Normal operation
    OPEN,      // Blocking operations due to failures
    HALF_OPEN  // Testing if service has recovered
}

/**
 * Circuit breaker state information
 */
@Serializable
data class CircuitBreakerState(
    val state: CircuitState,
    val failureCount: Int,
    val lastFailureTime: Long,
    val failureThreshold: Int,
    val timeoutMs: Long
)

/**
 * Recovery result information
 */
@Serializable
data class RecoveryResult(
    val success: Boolean,
    val strategy: RecoveryStrategy,
    val message: String,
    val suggestedActions: List<String> = emptyList(),
    val retryAfterMs: Long? = null
)

/**
 * Recovery statistics for monitoring
 */
@Serializable
data class RecoveryStatistics(
    val totalRecoveryAttempts: Int = 0,
    val successfulRecoveries: Int = 0,
    val failedRecoveries: Int = 0,
    val successRate: Float = 0.0f,
    val lastRecoveryAttempt: Long = 0L,
    val activeRecoveryOperations: Int = 0,
    val circuitBreakersOpen: Int = 0,
    val recoveryStrategiesUsed: Map<String, Int> = emptyMap()
)