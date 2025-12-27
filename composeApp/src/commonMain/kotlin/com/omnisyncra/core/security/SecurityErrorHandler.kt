package com.omnisyncra.core.security

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Comprehensive error handling system for security operations
 * Provides consistent error handling across all platforms with retry logic
 */
class SecurityErrorHandler {
    
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30000L
        private const val JITTER_FACTOR = 0.1
    }
    
    /**
     * Execute an operation with retry logic and exponential backoff
     */
    suspend fun <T> executeWithRetry(
        operation: String,
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
        block: suspend () -> T
    ): SecurityResult<T> {
        var lastException: Exception? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                val result = block()
                if (attempt > 0) {
                    // Log successful retry
                    SecurityEventLogger.logEvent(
                        SecurityEvent(
                            type = SecurityEventType.RETRY_SUCCESS,
                            deviceId = null,
                            timestamp = System.currentTimeMillis(),
                            details = "Operation '$operation' succeeded after ${attempt + 1} attempts"
                        )
                    )
                }
                return SecurityResult.Success(result)
            } catch (e: Exception) {
                lastException = e
                
                // Log the failure
                SecurityEventLogger.logEvent(
                    SecurityEvent(
                        type = SecurityEventType.OPERATION_FAILURE,
                        deviceId = null,
                        timestamp = System.currentTimeMillis(),
                        details = "Operation '$operation' failed on attempt ${attempt + 1}: ${e.message}"
                    )
                )
                
                // Don't delay on the last attempt
                if (attempt < maxAttempts - 1) {
                    val delayMs = calculateBackoffDelay(attempt)
                    delay(delayMs)
                }
            }
        }
        
        // All attempts failed
        val finalError = SecurityError.OperationFailed(operation, maxAttempts, lastException)
        SecurityEventLogger.logEvent(
            SecurityEvent(
                type = SecurityEventType.OPERATION_EXHAUSTED,
                deviceId = null,
                timestamp = System.currentTimeMillis(),
                details = "Operation '$operation' failed after $maxAttempts attempts: ${lastException?.message}"
            )
        )
        
        return SecurityResult.Error(finalError)
    }
    
    /**
     * Handle authentication errors with appropriate retry logic
     */
    suspend fun handleAuthenticationError(
        deviceId: String,
        error: Exception,
        attempt: Int
    ): SecurityResult<Unit> {
        return when (error) {
            is SecurityError.AuthenticationFailed -> {
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    SecurityEventLogger.logEvent(
                        SecurityEvent(
                            type = SecurityEventType.AUTH_FAILURE,
                            deviceId = deviceId,
                            timestamp = System.currentTimeMillis(),
                            details = "Authentication failed after $attempt attempts: ${error.reason}"
                        )
                    )
                    SecurityResult.Error(error)
                } else {
                    // Retry with exponential backoff
                    val delayMs = calculateBackoffDelay(attempt - 1)
                    delay(delayMs)
                    SecurityResult.Retry
                }
            }
            is SecurityError.CertificateInvalid -> {
                // Certificate errors are not retryable
                SecurityEventLogger.logEvent(
                    SecurityEvent(
                        type = SecurityEventType.CERTIFICATE_ERROR,
                        deviceId = deviceId,
                        timestamp = System.currentTimeMillis(),
                        details = "Certificate invalid: ${error.reason}"
                    )
                )
                SecurityResult.Error(error)
            }
            else -> {
                // Generic error handling
                SecurityResult.Error(SecurityError.UnknownError("Authentication error", error))
            }
        }
    }
    
    /**
     * Handle encryption errors with appropriate fallback strategies
     */
    fun handleEncryptionError(
        algorithm: String,
        error: Exception
    ): SecurityResult<Unit> {
        return when (error) {
            is SecurityError.EncryptionFailed -> {
                SecurityEventLogger.logEvent(
                    SecurityEvent(
                        type = SecurityEventType.ENCRYPTION_ERROR,
                        deviceId = null,
                        timestamp = System.currentTimeMillis(),
                        details = "Encryption failed with algorithm $algorithm: ${error.cause?.message}"
                    )
                )
                SecurityResult.Error(error)
            }
            else -> {
                val wrappedError = SecurityError.EncryptionFailed(algorithm, error)
                SecurityEventLogger.logEvent(
                    SecurityEvent(
                        type = SecurityEventType.ENCRYPTION_ERROR,
                        deviceId = null,
                        timestamp = System.currentTimeMillis(),
                        details = "Unexpected encryption error: ${error.message}"
                    )
                )
                SecurityResult.Error(wrappedError)
            }
        }
    }
    
    /**
     * Handle key exchange errors with session cleanup
     */
    suspend fun handleKeyExchangeError(
        sessionId: String,
        phase: String,
        error: Exception
    ): SecurityResult<Unit> {
        return when (error) {
            is SecurityError.KeyExchangeFailed -> {
                SecurityEventLogger.logEvent(
                    SecurityEvent(
                        type = SecurityEventType.KEY_EXCHANGE_FAILURE,
                        deviceId = null,
                        timestamp = System.currentTimeMillis(),
                        details = "Key exchange failed in phase '$phase' for session $sessionId: ${error.message}"
                    )
                )
                
                // Clean up the failed session
                cleanupFailedKeyExchangeSession(sessionId)
                
                SecurityResult.Error(error)
            }
            else -> {
                val wrappedError = SecurityError.KeyExchangeFailed(sessionId, phase)
                SecurityEventLogger.logEvent(
                    SecurityEvent(
                        type = SecurityEventType.KEY_EXCHANGE_FAILURE,
                        deviceId = null,
                        timestamp = System.currentTimeMillis(),
                        details = "Unexpected key exchange error in phase '$phase': ${error.message}"
                    )
                )
                
                cleanupFailedKeyExchangeSession(sessionId)
                SecurityResult.Error(wrappedError)
            }
        }
    }
    
    /**
     * Handle trust violations with immediate security response
     */
    fun handleTrustViolation(
        deviceId: String,
        action: String,
        error: Exception
    ): SecurityResult<Unit> {
        val trustError = SecurityError.TrustViolation(deviceId, action)
        
        SecurityEventLogger.logEvent(
            SecurityEvent(
                type = SecurityEventType.SECURITY_VIOLATION,
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                details = "Trust violation during action '$action': ${error.message}"
            )
        )
        
        // Immediate security response - revoke trust and terminate connections
        // This should be handled by the calling code
        
        return SecurityResult.Error(trustError)
    }
    
    /**
     * Handle storage errors with fallback to memory-only mode
     */
    fun handleStorageError(
        operation: String,
        error: Exception
    ): SecurityResult<StorageFallback> {
        val storageError = SecurityError.StorageError(operation, error)
        
        SecurityEventLogger.logEvent(
            SecurityEvent(
                type = SecurityEventType.STORAGE_ERROR,
                deviceId = null,
                timestamp = System.currentTimeMillis(),
                details = "Storage operation '$operation' failed: ${error.message}"
            )
        )
        
        // Determine fallback strategy
        val fallback = when (operation) {
            "store_certificate", "store_key" -> StorageFallback.MemoryOnly
            "load_certificate", "load_key" -> StorageFallback.UseDefaults
            else -> StorageFallback.Fail
        }
        
        return SecurityResult.Success(fallback)
    }
    
    /**
     * Calculate exponential backoff delay with jitter
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = BASE_DELAY_MS * (2.0.pow(attempt)).toLong()
        val cappedDelay = min(exponentialDelay, MAX_DELAY_MS)
        
        // Add jitter to prevent thundering herd
        val jitter = (cappedDelay * JITTER_FACTOR * Random.nextDouble()).toLong()
        return cappedDelay + jitter
    }
    
    /**
     * Clean up failed key exchange session resources
     */
    private suspend fun cleanupFailedKeyExchangeSession(sessionId: String) {
        try {
            // This would typically involve:
            // 1. Removing session from active sessions
            // 2. Clearing any temporary keys
            // 3. Notifying other components of the failure
            
            SecurityEventLogger.logEvent(
                SecurityEvent(
                    type = SecurityEventType.SESSION_CLEANUP,
                    deviceId = null,
                    timestamp = System.currentTimeMillis(),
                    details = "Cleaned up failed key exchange session: $sessionId"
                )
            )
        } catch (e: Exception) {
            // Log cleanup failure but don't propagate
            SecurityEventLogger.logEvent(
                SecurityEvent(
                    type = SecurityEventType.CLEANUP_FAILURE,
                    deviceId = null,
                    timestamp = System.currentTimeMillis(),
                    details = "Failed to cleanup session $sessionId: ${e.message}"
                )
            )
        }
    }
}

/**
 * Result type for security operations
 */
sealed class SecurityResult<out T> {
    data class Success<T>(val value: T) : SecurityResult<T>()
    data class Error(val error: SecurityError) : SecurityResult<Nothing>()
    object Retry : SecurityResult<Nothing>()
}

/**
 * Storage fallback strategies
 */
enum class StorageFallback {
    MemoryOnly,    // Use memory-only storage
    UseDefaults,   // Use default/hardcoded values
    Fail           // Fail the operation
}

/**
 * Enhanced security error types with detailed context
 */
sealed class SecurityError : Exception() {
    data class AuthenticationFailed(val deviceId: String, val reason: String) : SecurityError() {
        override val message: String = "Authentication failed for device $deviceId: $reason"
    }
    
    data class EncryptionFailed(val algorithm: String, val cause: Throwable) : SecurityError() {
        override val message: String = "Encryption failed with algorithm $algorithm: ${cause.message}"
    }
    
    data class KeyExchangeFailed(val sessionId: String, val phase: String) : SecurityError() {
        override val message: String = "Key exchange failed in phase $phase for session $sessionId"
    }
    
    data class CertificateInvalid(val deviceId: String, val reason: String) : SecurityError() {
        override val message: String = "Certificate invalid for device $deviceId: $reason"
    }
    
    data class TrustViolation(val deviceId: String, val action: String) : SecurityError() {
        override val message: String = "Trust violation by device $deviceId during action: $action"
    }
    
    data class StorageError(val operation: String, val cause: Throwable) : SecurityError() {
        override val message: String = "Storage operation '$operation' failed: ${cause.message}"
    }
    
    data class OperationFailed(val operation: String, val attempts: Int, val lastError: Exception?) : SecurityError() {
        override val message: String = "Operation '$operation' failed after $attempts attempts: ${lastError?.message}"
    }
    
    data class UnknownError(val context: String, val cause: Throwable) : SecurityError() {
        override val message: String = "Unknown error in $context: ${cause.message}"
    }
}

/**
 * Simple static logger for error handling
 */
object SecurityEventLogger {
    private val events = mutableListOf<SecurityEvent>()
    
    fun logEvent(event: SecurityEvent) {
        events.add(event)
        // In a real implementation, this would write to persistent storage
        println("[SECURITY] ${event.type}: ${event.details}")
    }
    
    fun getRecentEvents(limit: Int = 10): List<SecurityEvent> {
        return events.takeLast(limit)
    }
}