package com.omnisyncra.core.resilience

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

enum class CircuitBreakerState {
    CLOSED,    // Normal operation
    OPEN,      // Failing fast
    HALF_OPEN  // Testing if service recovered
}

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val recoveryTimeoutMs: Long = 30_000L, // 30 seconds
    val successThreshold: Int = 3 // For half-open state
)

class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private var state = CircuitBreakerState.CLOSED
    private var failureCount = 0
    private var successCount = 0
    private var lastFailureTime = 0L
    private val mutex = Mutex()
    
    suspend fun <T> execute(operation: suspend () -> T): Result<T> {
        return mutex.withLock {
            when (state) {
                CircuitBreakerState.OPEN -> {
                    if (shouldAttemptReset()) {
                        state = CircuitBreakerState.HALF_OPEN
                        successCount = 0
                        executeOperation(operation)
                    } else {
                        Result.failure(CircuitBreakerOpenException(name))
                    }
                }
                CircuitBreakerState.HALF_OPEN -> {
                    executeOperation(operation)
                }
                CircuitBreakerState.CLOSED -> {
                    executeOperation(operation)
                }
            }
        }
    }
    
    private suspend fun <T> executeOperation(operation: suspend () -> T): Result<T> {
        return try {
            val result = operation()
            onSuccess()
            Result.success(result)
        } catch (e: Exception) {
            onFailure()
            Result.failure(e)
        }
    }
    
    private fun onSuccess() {
        when (state) {
            CircuitBreakerState.HALF_OPEN -> {
                successCount++
                if (successCount >= config.successThreshold) {
                    state = CircuitBreakerState.CLOSED
                    failureCount = 0
                }
            }
            CircuitBreakerState.CLOSED -> {
                failureCount = 0
            }
            CircuitBreakerState.OPEN -> {
                // Should not happen
            }
        }
    }
    
    private fun onFailure() {
        failureCount++
        lastFailureTime = Clock.System.now().toEpochMilliseconds()
        
        when (state) {
            CircuitBreakerState.CLOSED -> {
                if (failureCount >= config.failureThreshold) {
                    state = CircuitBreakerState.OPEN
                }
            }
            CircuitBreakerState.HALF_OPEN -> {
                state = CircuitBreakerState.OPEN
            }
            CircuitBreakerState.OPEN -> {
                // Already open
            }
        }
    }
    
    private fun shouldAttemptReset(): Boolean {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        return (currentTime - lastFailureTime) >= config.recoveryTimeoutMs
    }
    
    fun getState(): CircuitBreakerState = state
    fun getFailureCount(): Int = failureCount
}

class CircuitBreakerOpenException(circuitName: String) : 
    Exception("Circuit breaker '$circuitName' is OPEN - failing fast")