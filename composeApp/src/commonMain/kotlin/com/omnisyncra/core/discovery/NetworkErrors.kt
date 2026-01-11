package com.omnisyncra.core.discovery

import kotlinx.serialization.Serializable

/**
 * Comprehensive network error types for enhanced error handling and recovery
 */
sealed class NetworkError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    abstract val errorCode: String
    abstract val severity: ErrorSeverity
    abstract val isRecoverable: Boolean
    abstract val suggestedAction: String
    
    /**
     * Connection-related errors
     */
    sealed class ConnectionError(
        message: String,
        cause: Throwable? = null
    ) : NetworkError(message, cause) {
        
        data class ConnectionTimeout(
            val host: String,
            val port: Int,
            val timeoutMs: Long,
            override val cause: Throwable? = null
        ) : ConnectionError("Connection timeout to $host:$port after ${timeoutMs}ms", cause) {
            override val errorCode = "CONN_TIMEOUT"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Retry with longer timeout or check network connectivity"
        }
        
        data class ConnectionRefused(
            val host: String,
            val port: Int,
            override val cause: Throwable? = null
        ) : ConnectionError("Connection refused by $host:$port", cause) {
            override val errorCode = "CONN_REFUSED"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = true
            override val suggestedAction = "Check if service is running on target device or try different port"
        }
        
        data class HostUnreachable(
            val host: String,
            override val cause: Throwable? = null
        ) : ConnectionError("Host unreachable: $host", cause) {
            override val errorCode = "HOST_UNREACHABLE"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = true
            override val suggestedAction = "Check network connectivity and host availability"
        }
        
        data class NetworkUnreachable(
            val networkRange: String,
            override val cause: Throwable? = null
        ) : ConnectionError("Network unreachable: $networkRange", cause) {
            override val errorCode = "NET_UNREACHABLE"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = true
            override val suggestedAction = "Check routing configuration and network interfaces"
        }
        
        data class ConnectionLost(
            val deviceId: String,
            val lastSeenMs: Long,
            override val cause: Throwable? = null
        ) : ConnectionError("Connection lost to device $deviceId (last seen ${lastSeenMs}ms ago)", cause) {
            override val errorCode = "CONN_LOST"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Attempt automatic reconnection with exponential backoff"
        }
        
        data class TooManyConnections(
            val currentCount: Int,
            val maxAllowed: Int,
            override val cause: Throwable? = null
        ) : ConnectionError("Too many connections: $currentCount/$maxAllowed", cause) {
            override val errorCode = "CONN_LIMIT"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Close unused connections or increase connection limit"
        }
    }
    
    /**
     * Discovery-related errors
     */
    sealed class DiscoveryError(
        message: String,
        cause: Throwable? = null
    ) : NetworkError(message, cause) {
        
        data class NetworkInterfaceError(
            val interfaceName: String,
            override val cause: Throwable? = null
        ) : DiscoveryError("Failed to access network interface: $interfaceName", cause) {
            override val errorCode = "IFACE_ERROR"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Check network interface status and permissions"
        }
        
        data class ScanTimeout(
            val networkRange: String,
            val timeoutMs: Long,
            override val cause: Throwable? = null
        ) : DiscoveryError("Network scan timeout for $networkRange after ${timeoutMs}ms", cause) {
            override val errorCode = "SCAN_TIMEOUT"
            override val severity = ErrorSeverity.LOW
            override val isRecoverable = true
            override val suggestedAction = "Reduce scan range or increase timeout"
        }
        
        data class NoNetworkInterfaces(
            override val cause: Throwable? = null
        ) : DiscoveryError("No available network interfaces found", cause) {
            override val errorCode = "NO_INTERFACES"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = false
            override val suggestedAction = "Check network configuration and enable network interfaces"
        }
        
        data class ProtocolNotSupported(
            val protocol: String,
            val platform: String,
            override val cause: Throwable? = null
        ) : DiscoveryError("Protocol $protocol not supported on platform $platform", cause) {
            override val errorCode = "PROTO_UNSUPPORTED"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Use alternative discovery protocol for this platform"
        }
    }
    
    /**
     * Message delivery errors
     */
    sealed class MessageError(
        message: String,
        cause: Throwable? = null
    ) : NetworkError(message, cause) {
        
        data class SerializationError(
            val messageType: String,
            override val cause: Throwable? = null
        ) : MessageError("Failed to serialize message of type $messageType", cause) {
            override val errorCode = "MSG_SERIALIZE"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = false
            override val suggestedAction = "Check message format and serialization compatibility"
        }
        
        data class DeserializationError(
            val rawData: String,
            override val cause: Throwable? = null
        ) : MessageError("Failed to deserialize message: $rawData", cause) {
            override val errorCode = "MSG_DESERIALIZE"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = false
            override val suggestedAction = "Check message format and protocol version compatibility"
        }
        
        data class MessageTooLarge(
            val messageSize: Long,
            val maxSize: Long,
            override val cause: Throwable? = null
        ) : MessageError("Message too large: ${messageSize}B > ${maxSize}B", cause) {
            override val errorCode = "MSG_TOO_LARGE"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Split message into smaller chunks or use streaming"
        }
        
        data class AcknowledgmentTimeout(
            val messageId: String,
            val timeoutMs: Long,
            override val cause: Throwable? = null
        ) : MessageError("Acknowledgment timeout for message $messageId after ${timeoutMs}ms", cause) {
            override val errorCode = "ACK_TIMEOUT"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Retry message delivery or increase acknowledgment timeout"
        }
        
        data class DeliveryFailure(
            val messageId: String,
            val targetDeviceId: String,
            val retryCount: Int,
            override val cause: Throwable? = null
        ) : MessageError("Failed to deliver message $messageId to $targetDeviceId after $retryCount retries", cause) {
            override val errorCode = "MSG_DELIVERY_FAILED"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = true
            override val suggestedAction = "Check target device connectivity and retry with exponential backoff"
        }
    }
    
    /**
     * Security-related errors
     */
    sealed class SecurityError(
        message: String,
        cause: Throwable? = null
    ) : NetworkError(message, cause) {
        
        data class AuthenticationFailure(
            val deviceId: String,
            val reason: String,
            override val cause: Throwable? = null
        ) : SecurityError("Authentication failed for device $deviceId: $reason", cause) {
            override val errorCode = "AUTH_FAILED"
            override val severity = ErrorSeverity.CRITICAL
            override val isRecoverable = false
            override val suggestedAction = "Verify device credentials and trust relationship"
        }
        
        data class EncryptionError(
            val operation: String,
            override val cause: Throwable? = null
        ) : SecurityError("Encryption error during $operation", cause) {
            override val errorCode = "ENCRYPT_ERROR"
            override val severity = ErrorSeverity.CRITICAL
            override val isRecoverable = false
            override val suggestedAction = "Check encryption keys and algorithm compatibility"
        }
        
        data class TrustViolation(
            val deviceId: String,
            val violation: String,
            override val cause: Throwable? = null
        ) : SecurityError("Trust violation by device $deviceId: $violation", cause) {
            override val errorCode = "TRUST_VIOLATION"
            override val severity = ErrorSeverity.CRITICAL
            override val isRecoverable = false
            override val suggestedAction = "Revoke device trust and investigate security breach"
        }
    }
    
    /**
     * Platform-specific errors
     */
    sealed class PlatformError(
        message: String,
        cause: Throwable? = null
    ) : NetworkError(message, cause) {
        
        data class PermissionDenied(
            val permission: String,
            val platform: String,
            override val cause: Throwable? = null
        ) : PlatformError("Permission denied: $permission on $platform", cause) {
            override val errorCode = "PERMISSION_DENIED"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = true
            override val suggestedAction = "Grant required permissions in system settings"
        }
        
        data class BluetoothUnavailable(
            val reason: String,
            override val cause: Throwable? = null
        ) : PlatformError("Bluetooth unavailable: $reason", cause) {
            override val errorCode = "BT_UNAVAILABLE"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Enable Bluetooth and check device compatibility"
        }
        
        data class WebSocketError(
            val url: String,
            val statusCode: Int?,
            override val cause: Throwable? = null
        ) : PlatformError("WebSocket error for $url: ${statusCode ?: "unknown"}", cause) {
            override val errorCode = "WS_ERROR"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Check WebSocket server availability and CORS configuration"
        }
        
        data class BrowserSecurityRestriction(
            val restriction: String,
            override val cause: Throwable? = null
        ) : PlatformError("Browser security restriction: $restriction", cause) {
            override val errorCode = "BROWSER_SECURITY"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = false
            override val suggestedAction = "Use HTTPS or adjust browser security settings"
        }
    }
    
    /**
     * Resource-related errors
     */
    sealed class ResourceError(
        message: String,
        cause: Throwable? = null
    ) : NetworkError(message, cause) {
        
        data class OutOfMemory(
            val requestedBytes: Long,
            val availableBytes: Long,
            override val cause: Throwable? = null
        ) : ResourceError("Out of memory: requested ${requestedBytes}B, available ${availableBytes}B", cause) {
            override val errorCode = "OUT_OF_MEMORY"
            override val severity = ErrorSeverity.CRITICAL
            override val isRecoverable = true
            override val suggestedAction = "Reduce memory usage or increase available memory"
        }
        
        data class BandwidthExceeded(
            val currentUsage: Long,
            val limit: Long,
            override val cause: Throwable? = null
        ) : ResourceError("Bandwidth exceeded: ${currentUsage}bps > ${limit}bps", cause) {
            override val errorCode = "BANDWIDTH_EXCEEDED"
            override val severity = ErrorSeverity.MEDIUM
            override val isRecoverable = true
            override val suggestedAction = "Reduce data transmission rate or increase bandwidth limit"
        }
        
        data class StorageExhausted(
            val requiredSpace: Long,
            val availableSpace: Long,
            override val cause: Throwable? = null
        ) : ResourceError("Storage exhausted: need ${requiredSpace}B, available ${availableSpace}B", cause) {
            override val errorCode = "STORAGE_EXHAUSTED"
            override val severity = ErrorSeverity.HIGH
            override val isRecoverable = true
            override val suggestedAction = "Free up storage space or use external storage"
        }
    }
}

/**
 * Error severity levels for prioritizing error handling
 */
enum class ErrorSeverity {
    LOW,        // Minor issues that don't affect core functionality
    MEDIUM,     // Issues that may degrade performance or features
    HIGH,       // Serious issues that affect major functionality
    CRITICAL    // Critical issues that may compromise security or cause system failure
}

/**
 * Detailed error information for logging and debugging
 */
@Serializable
data class ErrorDetails(
    val errorCode: String,
    val message: String,
    val severity: ErrorSeverity,
    val timestamp: Long,
    val deviceId: String? = null,
    val platform: String? = null,
    val networkInterface: String? = null,
    val stackTrace: String? = null,
    val context: Map<String, String> = emptyMap(),
    val suggestedAction: String,
    val isRecoverable: Boolean,
    val retryCount: Int = 0,
    val lastRetryAt: Long? = null
)

/**
 * Error recovery strategy
 */
sealed class RecoveryStrategy {
    object NoRecovery : RecoveryStrategy()
    data class Retry(val maxAttempts: Int, val backoffMs: Long) : RecoveryStrategy()
    data class Fallback(val alternativeAction: String) : RecoveryStrategy()
    data class CircuitBreaker(val failureThreshold: Int, val timeoutMs: Long) : RecoveryStrategy()
    data class UserIntervention(val requiredAction: String) : RecoveryStrategy()
}

/**
 * Error context for enhanced debugging
 */
@Serializable
data class ErrorContext(
    val operation: String,
    val deviceId: String? = null,
    val networkInterface: String? = null,
    val protocol: String? = null,
    val messageId: String? = null,
    val connectionId: String? = null,
    val additionalInfo: Map<String, String> = emptyMap()
)