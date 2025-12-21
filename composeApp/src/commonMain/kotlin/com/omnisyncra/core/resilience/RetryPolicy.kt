package com.omnisyncra.core.resilience

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30_000L,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1
)

class RetryPolicy(private val config: RetryConfig = RetryConfig()) {
    
    suspend fun <T> execute(
        operation: suspend (attempt: Int) -> T,
        shouldRetry: (Exception) -> Boolean = { true }
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(config.maxAttempts) { attempt ->
            try {
                val result = operation(attempt + 1)
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                
                if (!shouldRetry(e) || attempt == config.maxAttempts - 1) {
                    return Result.failure(e)
                }
                
                val delayMs = calculateDelay(attempt)
                delay(delayMs)
            }
        }
        
        return Result.failure(lastException ?: Exception("Retry failed"))
    }
    
    private fun calculateDelay(attempt: Int): Long {
        val exponentialDelay = config.baseDelayMs * config.backoffMultiplier.pow(attempt).toLong()
        val cappedDelay = min(exponentialDelay, config.maxDelayMs)
        
        // Add jitter to prevent thundering herd
        val jitter = cappedDelay * config.jitterFactor * Random.nextDouble()
        return (cappedDelay + jitter).toLong()
    }
}

// Predefined retry policies for common scenarios
object RetryPolicies {
    val networkOperation = RetryPolicy(
        RetryConfig(
            maxAttempts = 3,
            baseDelayMs = 1000L,
            maxDelayMs = 10_000L,
            backoffMultiplier = 2.0
        )
    )
    
    val deviceDiscovery = RetryPolicy(
        RetryConfig(
            maxAttempts = 5,
            baseDelayMs = 2000L,
            maxDelayMs = 30_000L,
            backoffMultiplier = 1.5
        )
    )
    
    val computeTask = RetryPolicy(
        RetryConfig(
            maxAttempts = 2,
            baseDelayMs = 500L,
            maxDelayMs = 5_000L,
            backoffMultiplier = 2.0
        )
    )
}