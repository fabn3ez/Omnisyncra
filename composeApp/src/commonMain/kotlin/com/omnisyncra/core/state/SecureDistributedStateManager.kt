package com.omnisyncra.core.state

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.security.*
import com.omnisyncra.core.crdt.CrdtOperation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Secure CRDT operation with encryption and authentication
 */
@Serializable
data class SecureCrdtOperation(
    val operationId: Uuid,
    val sourceDeviceId: Uuid,
    val encryptedPayload: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray,
    val signature: ByteArray,
    val timestamp: Long,
    val vectorClock: Map<String, Long>,
    val operationType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SecureCrdtOperation
        return operationId == other.operationId &&
                sourceDeviceId == other.sourceDeviceId &&
                encryptedPayload.contentEquals(other.encryptedPayload) &&
                nonce.contentEquals(other.nonce) &&
                authTag.contentEquals(other.authTag) &&
                signature.contentEquals(other.signature) &&
                timestamp == other.timestamp &&
                vectorClock == other.vectorClock &&
                operationType == other.operationType
    }

    override fun hashCode(): Int {
        var result = operationId.hashCode()
        result = 31 * result + sourceDeviceId.hashCode()
        result = 31 * result + encryptedPayload.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + vectorClock.hashCode()
        result = 31 * result + operationType.hashCode()
        return result
    }
}

/**
 * Secure state synchronization result
 */
data class SecureSyncResult(
    val success: Boolean,
    val operationsReceived: Int,
    val operationsSent: Int,
    val conflictsResolved: Int,
    val securityViolations: Int,
    val errorMessage: String? = null
)

/**
 * State synchronization security policy
 */
data class StateSyncSecurityPolicy(
    val requireTrustedDevices: Boolean = true,
    val requireEncryption: Boolean = true,
    val requireAuthentication: Boolean = true,
    val maxOperationAge: Long = 3600_000L, // 1 hour
    val allowedOperationTypes: Set<String> = setOf("DeviceUpdate", "ContextUpdate", "StateSync"),
    val maxOperationsPerSync: Int = 100
)

/**
 * Secure distributed state manager interface
 */
interface SecureDistributedStateManager {
    val secureState: StateFlow<SecureCrdtState?>
    val syncPolicy: StateFlow<StateSyncSecurityPolicy>
    val securityEvents: StateFlow<List<SecurityEvent>>
    
    suspend fun initialize(): Boolean
    suspend fun applySecureLocalOperation(operation: CrdtOperation): Boolean
    suspend fun syncWithSecurePeer(peerDeviceId: Uuid): SecureSyncResult
    suspend fun receiveSecureOperations(operations: List<SecureCrdtOperation>): SecureSyncResult
    suspend fun updateSyncPolicy(policy: StateSyncSecurityPolicy)
    suspend fun validateOperationSecurity(operation: SecureCrdtOperation): Boolean
}

/**
 * Secure CRDT state with encryption metadata
 */
@Serializable
data class SecureCrdtState(
    val nodeId: Uuid,
    val vectorClock: Map<String, Long>,
    val encryptedOperations: List<SecureCrdtOperation>,
    val lastSyncTime: Long,
    val securityVersion: Int = 1
)

/**
 * Implementation of secure distributed state manager
 */
class OmnisyncraSecureDistributedStateManager(
    private val deviceId: Uuid,
    private val baseStateManager: DistributedStateManager,
    private val sessionManager: SessionManager,
    private val trustManager: TrustManager,
    private val certificateManager: CertificateManager,
    private val securityLogger: SecurityEventLogger,
    private val initialPolicy: StateSyncSecurityPolicy = StateSyncSecurityPolicy()
) : SecureDistributedStateManager {
    
    private val _secureState = MutableStateFlow<SecureCrdtState?>(null)
    override val secureState: StateFlow<SecureCrdtState?> = _secureState.asStateFlow()
    
    private val _syncPolicy = MutableStateFlow(initialPolicy)
    override val syncPolicy: StateFlow<StateSyncSecurityPolicy> = _syncPolicy.asStateFlow()
    
    private val _securityEvents = MutableStateFlow<List<SecurityEvent>>(emptyList())
    override val securityEvents: StateFlow<List<SecurityEvent>> = _securityEvents.asStateFlow()
    
    private val operationCache = mutableMapOf<Uuid, CrdtOperation>()
    
    override suspend fun initialize(): Boolean {
        return try {
            // Initialize base state manager
            baseStateManager.initialize()
            
            // Initialize secure state
            val currentTime = Clock.System.now().toEpochMilliseconds()
            _secureState.value = SecureCrdtState(
                nodeId = deviceId,
                vectorClock = mapOf(deviceId.toString() to 0L),
                encryptedOperations = emptyList(),
                lastSyncTime = currentTime
            )
            
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
                severity = SecurityEventSeverity.INFO,
                message = "Secure distributed state manager initialized",
                details = mapOf(
                    "device_id" to deviceId.toString(),
                    "security_version" to "1"
                )
            )
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to initialize secure state manager",
                error = e
            )
            false
        }
    }
    
    override suspend fun applySecureLocalOperation(operation: CrdtOperation): Boolean {
        return try {
            val policy = _syncPolicy.value
            
            // Validate operation type
            if (!policy.allowedOperationTypes.contains(operation::class.simpleName)) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Operation type not allowed by security policy",
                    details = mapOf("operation_type" to (operation::class.simpleName ?: "unknown"))
                )
                return false
            }
            
            // Apply to base state manager
            baseStateManager.applyLocalOperation(operation)
            
            // Cache operation for secure synchronization
            operationCache[operation.id] = operation
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Local operation applied securely",
                details = mapOf(
                    "operation_id" to operation.id.toString(),
                    "operation_type" to (operation::class.simpleName ?: "unknown")
                )
            )
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to apply secure local operation",
                error = e
            )
            false
        }
    }
    
    override suspend fun syncWithSecurePeer(peerDeviceId: Uuid): SecureSyncResult {
        return try {
            val policy = _syncPolicy.value
            
            // Check if peer is trusted
            if (policy.requireTrustedDevices && !trustManager.isTrusted(peerDeviceId)) {
                securityLogger.logEvent(
                    type = SecurityEventType.AUTHENTICATION_FAILURE,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Sync rejected - peer device not trusted",
                    relatedDeviceId = peerDeviceId
                )
                
                return SecureSyncResult(
                    success = false,
                    operationsReceived = 0,
                    operationsSent = 0,
                    conflictsResolved = 0,
                    securityViolations = 1,
                    errorMessage = "Peer device not trusted"
                )
            }
            
            // Get active session with peer
            val session = sessionManager.getActiveSessions().find { it.remoteDeviceId == peerDeviceId }
                ?: return SecureSyncResult(
                    success = false,
                    operationsReceived = 0,
                    operationsSent = 0,
                    conflictsResolved = 0,
                    securityViolations = 1,
                    errorMessage = "No secure session with peer"
                )
            
            // Get operations to send
            val operationsToSend = getOperationsForPeer(peerDeviceId)
            val secureOperationsToSend = operationsToSend.mapNotNull { operation ->
                encryptOperation(operation, session.sessionId)
            }
            
            // Send encrypted operations (simulated - in real implementation would use network)
            val operationsSent = secureOperationsToSend.size
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure sync completed with peer",
                relatedDeviceId = peerDeviceId,
                sessionId = session.sessionId,
                details = mapOf(
                    "operations_sent" to operationsSent.toString(),
                    "operations_received" to "0" // Simulated
                )
            )
            
            SecureSyncResult(
                success = true,
                operationsReceived = 0, // Simulated
                operationsSent = operationsSent,
                conflictsResolved = 0,
                securityViolations = 0
            )
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Secure sync failed with peer",
                relatedDeviceId = peerDeviceId,
                error = e
            )
            
            SecureSyncResult(
                success = false,
                operationsReceived = 0,
                operationsSent = 0,
                conflictsResolved = 0,
                securityViolations = 1,
                errorMessage = e.message
            )
        }
    }
    
    override suspend fun receiveSecureOperations(operations: List<SecureCrdtOperation>): SecureSyncResult {
        return try {
            val policy = _syncPolicy.value
            var operationsReceived = 0
            var conflictsResolved = 0
            var securityViolations = 0
            
            // Validate and decrypt operations
            val validOperations = mutableListOf<CrdtOperation>()
            
            for (secureOperation in operations.take(policy.maxOperationsPerSync)) {
                // Validate security
                if (!validateOperationSecurity(secureOperation)) {
                    securityViolations++
                    continue
                }
                
                // Decrypt operation
                val decryptedOperation = decryptOperation(secureOperation)
                if (decryptedOperation != null) {
                    validOperations.add(decryptedOperation)
                    operationsReceived++
                } else {
                    securityViolations++
                }
            }
            
            // Apply valid operations to base state manager
            if (validOperations.isNotEmpty()) {
                baseStateManager.applyRemoteOperations(validOperations)
            }
            
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure operations received and processed",
                details = mapOf(
                    "operations_received" to operationsReceived.toString(),
                    "security_violations" to securityViolations.toString(),
                    "conflicts_resolved" to conflictsResolved.toString()
                )
            )
            
            SecureSyncResult(
                success = true,
                operationsReceived = operationsReceived,
                operationsSent = 0,
                conflictsResolved = conflictsResolved,
                securityViolations = securityViolations
            )
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to receive secure operations",
                error = e
            )
            
            SecureSyncResult(
                success = false,
                operationsReceived = 0,
                operationsSent = 0,
                conflictsResolved = 0,
                securityViolations = 1,
                errorMessage = e.message
            )
        }
    }
    
    override suspend fun updateSyncPolicy(policy: StateSyncSecurityPolicy) {
        _syncPolicy.value = policy
        
        securityLogger.logEvent(
            type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
            severity = SecurityEventSeverity.INFO,
            message = "State sync security policy updated",
            details = mapOf(
                "require_trusted_devices" to policy.requireTrustedDevices.toString(),
                "require_encryption" to policy.requireEncryption.toString(),
                "max_operation_age" to policy.maxOperationAge.toString()
            )
        )
    }
    
    override suspend fun validateOperationSecurity(operation: SecureCrdtOperation): Boolean {
        return try {
            val policy = _syncPolicy.value
            val currentTime = Clock.System.now().toEpochMilliseconds()
            
            // Check operation age
            if (currentTime - operation.timestamp > policy.maxOperationAge) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Operation too old",
                    relatedDeviceId = operation.sourceDeviceId,
                    details = mapOf(
                        "operation_age" to (currentTime - operation.timestamp).toString(),
                        "max_age" to policy.maxOperationAge.toString()
                    )
                )
                return false
            }
            
            // Check if source device is trusted
            if (policy.requireTrustedDevices && !trustManager.isTrusted(operation.sourceDeviceId)) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Operation from untrusted device",
                    relatedDeviceId = operation.sourceDeviceId
                )
                return false
            }
            
            // Check operation type
            if (!policy.allowedOperationTypes.contains(operation.operationType)) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Operation type not allowed",
                    relatedDeviceId = operation.sourceDeviceId,
                    details = mapOf("operation_type" to operation.operationType)
                )
                return false
            }
            
            // Verify signature
            val signatureData = buildString {
                append("operation_id:${operation.operationId}")
                append("|source:${operation.sourceDeviceId}")
                append("|timestamp:${operation.timestamp}")
                append("|type:${operation.operationType}")
            }.encodeToByteArray()
            
            val signatureValid = certificateManager.verifySignature(
                signatureData,
                operation.signature,
                operation.sourceDeviceId
            )
            
            if (!signatureValid) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.ERROR,
                    message = "Operation signature verification failed",
                    relatedDeviceId = operation.sourceDeviceId
                )
                return false
            }
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SECURITY_VIOLATION,
                severity = SecurityEventSeverity.ERROR,
                message = "Operation security validation error",
                relatedDeviceId = operation.sourceDeviceId,
                error = e
            )
            false
        }
    }
    
    private suspend fun encryptOperation(operation: CrdtOperation, sessionId: Uuid): SecureCrdtOperation? {
        return try {
            // Serialize operation
            val operationJson = Json.encodeToString(operation)
            val plaintextData = operationJson.encodeToByteArray()
            
            // Encrypt operation
            val encryptedMessage = sessionManager.encryptMessage(sessionId, plaintextData, "crdt_operation")
                .getOrNull() ?: return null
            
            // Create signature
            val signatureData = buildString {
                append("operation_id:${operation.id}")
                append("|source:$deviceId")
                append("|timestamp:${operation.timestamp}")
                append("|type:${operation::class.simpleName}")
            }.encodeToByteArray()
            
            val signature = certificateManager.signData(signatureData) ?: return null
            
            SecureCrdtOperation(
                operationId = operation.id,
                sourceDeviceId = deviceId,
                encryptedPayload = encryptedMessage.encryptedData,
                nonce = encryptedMessage.nonce,
                authTag = encryptedMessage.authTag,
                signature = signature,
                timestamp = operation.timestamp,
                vectorClock = operation.vectorClock.clocks,
                operationType = operation::class.simpleName ?: "unknown"
            )
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to encrypt CRDT operation",
                error = e
            )
            null
        }
    }
    
    private suspend fun decryptOperation(secureOperation: SecureCrdtOperation): CrdtOperation? {
        return try {
            // Get session with source device
            val session = sessionManager.getActiveSessions()
                .find { it.remoteDeviceId == secureOperation.sourceDeviceId }
                ?: return null
            
            // Create encrypted message
            val encryptedMessage = EncryptedMessage(
                sessionId = session.sessionId,
                messageId = com.benasher44.uuid.uuid4(),
                encryptedData = secureOperation.encryptedPayload,
                nonce = secureOperation.nonce,
                authTag = secureOperation.authTag,
                timestamp = secureOperation.timestamp,
                messageType = "crdt_operation"
            )
            
            // Decrypt operation
            val decryptedData = sessionManager.decryptMessage(encryptedMessage)
                .getOrNull() ?: return null
            
            // Deserialize operation
            val operationJson = decryptedData.decodeToString()
            Json.decodeFromString<CrdtOperation>(operationJson)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to decrypt CRDT operation",
                relatedDeviceId = secureOperation.sourceDeviceId,
                error = e
            )
            null
        }
    }
    
    private fun getOperationsForPeer(peerDeviceId: Uuid): List<CrdtOperation> {
        // Get operations from cache that the peer hasn't seen
        // In a real implementation, this would use vector clocks to determine
        // which operations to send
        return operationCache.values.toList()
    }
    
    // Cleanup old operations from cache
    suspend fun cleanupOperationCache(): Int {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val maxAge = _syncPolicy.value.maxOperationAge
        
        val expiredOperations = operationCache.filter { (_, operation) ->
            (currentTime - operation.timestamp) > maxAge
        }
        
        expiredOperations.keys.forEach { operationId ->
            operationCache.remove(operationId)
        }
        
        return expiredOperations.size
    }
}