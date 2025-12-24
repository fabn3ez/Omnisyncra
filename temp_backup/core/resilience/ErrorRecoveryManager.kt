package com.omnisyncra.core.resilience

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

data class ErrorEvent(
    val id: Uuid = com.benasher44.uuid.uuid4(),
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val component: String,
    val errorType: String,
    val message: String,
    val isRetryable: Boolean,
    val severity: ErrorSeverity,
    val context: Map<String, String> = emptyMap()
)

enum class ErrorSeverity {
    LOW,      // Minor issues, system continues normally
    MEDIUM,   // Some functionality affected, but core features work
    HIGH,     // Major functionality affected, degraded experience
    CRITICAL  // System-wide failure, immediate attention required
}

data class RecoveryAction(
    val type: RecoveryActionType,
    val description: String,
    val execute: suspend () -> Boolean
)

enum class RecoveryActionType {
    RETRY_OPERATION,
    RECONNECT_DEVICE,
    RESET_COMPONENT,
    FALLBACK_MODE,
    RESTART_SERVICE,
    NOTIFY_USER
}

class ErrorRecoveryManager {
    private val _errorEvents = MutableSharedFlow<ErrorEvent>()
    val errorEvents: SharedFlow<ErrorEvent> = _errorEvents.asSharedFlow()
    
    private val circuitBreakers = mutableMapOf<String, CircuitBreaker>()
    private val recoveryActions = mutableMapOf<String, List<RecoveryAction>>()
    
    init {
        setupDefaultRecoveryActions()
    }
    
    suspend fun reportError(
        component: String,
        exception: Throwable,
        context: Map<String, String> = emptyMap()
    ) {
        val severity = determineSeverity(exception)
        val errorEvent = ErrorEvent(
            component = component,
            errorType = exception::class.simpleName ?: "Unknown",
            message = exception.message ?: "No message",
            isRetryable = (exception as? OmnisyncraException)?.isRetryable ?: false,
            severity = severity,
            context = context
        )
        
        _errorEvents.emit(errorEvent)
        
        // Attempt automatic recovery for retryable errors
        if (errorEvent.isRetryable && severity != ErrorSeverity.CRITICAL) {
            attemptRecovery(errorEvent)
        }
    }
    
    fun getCircuitBreaker(name: String): CircuitBreaker {
        return circuitBreakers.getOrPut(name) {
            CircuitBreaker(name)
        }
    }
    
    private suspend fun attemptRecovery(errorEvent: ErrorEvent) {
        val actions = recoveryActions[errorEvent.component] ?: return
        
        for (action in actions) {
            try {
                val success = action.execute()
                if (success) {
                    reportRecoverySuccess(errorEvent, action)
                    break
                }
            } catch (e: Exception) {
                // Recovery action failed, try next one
                continue
            }
        }
    }
    
    private suspend fun reportRecoverySuccess(errorEvent: ErrorEvent, action: RecoveryAction) {
        val recoveryEvent = ErrorEvent(
            component = errorEvent.component,
            errorType = "RECOVERY_SUCCESS",
            message = "Recovered from ${errorEvent.errorType} using ${action.type}",
            isRetryable = false,
            severity = ErrorSeverity.LOW,
            context = mapOf(
                "original_error_id" to errorEvent.id.toString(),
                "recovery_action" to action.type.name
            )
        )
        _errorEvents.emit(recoveryEvent)
    }
    
    private fun determineSeverity(exception: Throwable): ErrorSeverity {
        return when (exception) {
            is StateCorruptionException -> ErrorSeverity.CRITICAL
            is ConfigurationException -> ErrorSeverity.CRITICAL
            is ComputeTaskException -> ErrorSeverity.MEDIUM
            is DeviceConnectionException -> ErrorSeverity.MEDIUM
            is NetworkException -> ErrorSeverity.MEDIUM
            is UITransitionException -> ErrorSeverity.LOW
            is OperationTimeoutException -> ErrorSeverity.MEDIUM
            else -> ErrorSeverity.MEDIUM
        }
    }
    
    private fun setupDefaultRecoveryActions() {
        // Device Discovery Recovery
        recoveryActions["DeviceDiscovery"] = listOf(
            RecoveryAction(
                type = RecoveryActionType.RETRY_OPERATION,
                description = "Retry device discovery with exponential backoff"
            ) {
                delay(2000)
                true // Simulate recovery
            },
            RecoveryAction(
                type = RecoveryActionType.RESET_COMPONENT,
                description = "Reset discovery service"
            ) {
                delay(1000)
                true
            }
        )
        
        // Network Communication Recovery
        recoveryActions["NetworkCommunication"] = listOf(
            RecoveryAction(
                type = RecoveryActionType.RETRY_OPERATION,
                description = "Retry network operation"
            ) {
                delay(1000)
                true
            },
            RecoveryAction(
                type = RecoveryActionType.FALLBACK_MODE,
                description = "Switch to offline mode"
            ) {
                true
            }
        )
        
        // Compute Task Recovery
        recoveryActions["ComputeScheduler"] = listOf(
            RecoveryAction(
                type = RecoveryActionType.RETRY_OPERATION,
                description = "Retry task on different node"
            ) {
                delay(500)
                true
            },
            RecoveryAction(
                type = RecoveryActionType.FALLBACK_MODE,
                description = "Execute task locally"
            ) {
                true
            }
        )
        
        // State Management Recovery
        recoveryActions["StateManager"] = listOf(
            RecoveryAction(
                type = RecoveryActionType.RETRY_OPERATION,
                description = "Retry state synchronization"
            ) {
                delay(1000)
                true
            },
            RecoveryAction(
                type = RecoveryActionType.RESET_COMPONENT,
                description = "Reset to last known good state"
            ) {
                true
            }
        )
    }
}

// Global error recovery manager instance
object GlobalErrorRecovery {
    val manager = ErrorRecoveryManager()
}