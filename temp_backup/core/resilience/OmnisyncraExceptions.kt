package com.omnisyncra.core.resilience

import com.benasher44.uuid.Uuid

// Base exception for all Omnisyncra errors
sealed class OmnisyncraException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String,
    val isRetryable: Boolean = false
) : Exception(message, cause)

// Network-related exceptions
class NetworkException(
    message: String,
    cause: Throwable? = null,
    errorCode: String = "NETWORK_ERROR",
    isRetryable: Boolean = true
) : OmnisyncraException(message, cause, errorCode, isRetryable)

class DeviceConnectionException(
    val deviceId: Uuid,
    message: String,
    cause: Throwable? = null,
    isRetryable: Boolean = true
) : OmnisyncraException(message, cause, "DEVICE_CONNECTION_ERROR", isRetryable)

class DeviceDiscoveryException(
    message: String,
    cause: Throwable? = null,
    isRetryable: Boolean = true
) : OmnisyncraException(message, cause, "DEVICE_DISCOVERY_ERROR", isRetryable)

// State management exceptions
class StateCorruptionException(
    message: String,
    cause: Throwable? = null
) : OmnisyncraException(message, cause, "STATE_CORRUPTION", false)

class StateSyncException(
    message: String,
    cause: Throwable? = null,
    isRetryable: Boolean = true
) : OmnisyncraException(message, cause, "STATE_SYNC_ERROR", isRetryable)

// Compute exceptions
class ComputeTaskException(
    val taskId: Uuid,
    message: String,
    cause: Throwable? = null,
    isRetryable: Boolean = false
) : OmnisyncraException(message, cause, "COMPUTE_TASK_ERROR", isRetryable)

class ComputeNodeUnavailableException(
    val nodeId: Uuid,
    message: String = "Compute node is unavailable"
) : OmnisyncraException(message, null, "COMPUTE_NODE_UNAVAILABLE", true)

// UI exceptions
class UITransitionException(
    message: String,
    cause: Throwable? = null
) : OmnisyncraException(message, cause, "UI_TRANSITION_ERROR", false)

// Storage exceptions
class StorageException(
    message: String,
    cause: Throwable? = null,
    isRetryable: Boolean = false
) : OmnisyncraException(message, cause, "STORAGE_ERROR", isRetryable)

// Timeout exceptions
class OperationTimeoutException(
    operation: String,
    timeoutMs: Long
) : OmnisyncraException(
    "Operation '$operation' timed out after ${timeoutMs}ms", 
    null, 
    "OPERATION_TIMEOUT", 
    true
)

// Configuration exceptions
class ConfigurationException(
    message: String,
    cause: Throwable? = null
) : OmnisyncraException(message, cause, "CONFIGURATION_ERROR", false)