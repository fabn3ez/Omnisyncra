package com.omnisyncra.core.handoff

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.security.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * Secure handoff package with encryption and authentication
 */
@Serializable
data class SecureHandoffPackage(
    val id: String,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val encryptedPayload: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray,
    val deviceCertificate: DeviceCertificate,
    val signature: ByteArray,
    val priority: HandoffPriority,
    val expirationTime: Long,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SecureHandoffPackage
        return id == other.id &&
                sourceDeviceId == other.sourceDeviceId &&
                targetDeviceId == other.targetDeviceId &&
                encryptedPayload.contentEquals(other.encryptedPayload) &&
                nonce.contentEquals(other.nonce) &&
                authTag.contentEquals(other.authTag) &&
                deviceCertificate == other.deviceCertificate &&
                signature.contentEquals(other.signature) &&
                priority == other.priority &&
                expirationTime == other.expirationTime &&
                timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sourceDeviceId.hashCode()
        result = 31 * result + targetDeviceId.hashCode()
        result = 31 * result + encryptedPayload.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        result = 31 * result + deviceCertificate.hashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + expirationTime.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Secure handoff result with security information
 */
data class SecureHandoffResult(
    val success: Boolean,
    val transferredApps: List<String>,
    val failedApps: List<String>,
    val contextPreservationScore: Float,
    val transferTime: Duration,
    val securityVerified: Boolean,
    val encryptionUsed: Boolean,
    val trustLevel: TrustLevel,
    val errorMessage: String? = null
)

/**
 * Secure Ghost Handoff System with end-to-end encryption and device authentication
 */
interface SecureGhostHandoffSystem {
    val isActive: StateFlow<Boolean>
    val availableTargets: StateFlow<List<String>>
    val currentSecureHandoffs: StateFlow<List<SecureHandoffPackage>>
    val securityStatus: StateFlow<SecurityStatus>
    
    suspend fun startSecureHandoffCapture(): Boolean
    suspend fun stopSecureHandoffCapture()
    suspend fun initiateSecureHandoff(
        targetDeviceId: Uuid, 
        priority: HandoffPriority = HandoffPriority.NORMAL
    ): SecureHandoffResult
    suspend fun acceptSecureHandoff(handoffId: String): SecureHandoffResult
    suspend fun rejectSecureHandoff(handoffId: String, reason: String): Boolean
    suspend fun cancelSecureHandoff(handoffId: String): Boolean
    
    suspend fun verifyHandoffSecurity(handoffPackage: SecureHandoffPackage): Boolean
    suspend fun encryptHandoffData(handoffPackage: HandoffPackage, targetDeviceId: Uuid): SecureHandoffPackage?
    suspend fun decryptHandoffData(securePackage: SecureHandoffPackage): HandoffPackage?
}

/**
 * Implementation of secure Ghost Handoff system
 */
class OmnisyncraSecureGhostHandoffSystem(
    private val deviceId: Uuid,
    private val baseHandoffSystem: GhostHandoffSystem,
    private val sessionManager: SessionManager,
    private val trustManager: TrustManager,
    private val certificateManager: CertificateManager,
    private val securityLogger: SecurityEventLogger
) : SecureGhostHandoffSystem {
    
    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _availableTargets = MutableStateFlow<List<String>>(emptyList())
    override val availableTargets: StateFlow<List<String>> = _availableTargets.asStateFlow()
    
    private val _currentSecureHandoffs = MutableStateFlow<List<SecureHandoffPackage>>(emptyList())
    override val currentSecureHandoffs: StateFlow<List<SecureHandoffPackage>> = _currentSecureHandoffs.asStateFlow()
    
    private val _securityStatus = MutableStateFlow(SecurityStatus(
        isInitialized = false,
        activeChannels = 0,
        trustedDevices = 0,
        lastKeyRotation = 0L,
        securityEvents = emptyList()
    ))
    override val securityStatus: StateFlow<SecurityStatus> = _securityStatus.asStateFlow()
    
    override suspend fun startSecureHandoffCapture(): Boolean {
        return try {
            // Start base handoff system
            val baseStarted = baseHandoffSystem.startHandoffCapture()
            if (!baseStarted) {
                return false
            }
            
            _isActive.value = true
            
            // Update available targets with only trusted devices
            updateAvailableTargets()
            
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_ESTABLISHED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure handoff capture started",
                details = mapOf("device_id" to deviceId.toString())
            )
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to start secure handoff capture",
                error = e
            )
            false
        }
    }
    
    override suspend fun stopSecureHandoffCapture() {
        try {
            baseHandoffSystem.stopHandoffCapture()
            _isActive.value = false
            _availableTargets.value = emptyList()
            
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure handoff capture stopped"
            )
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.ERROR,
                message = "Error stopping secure handoff capture",
                error = e
            )
        }
    }
    
    override suspend fun initiateSecureHandoff(
        targetDeviceId: Uuid,
        priority: HandoffPriority
    ): SecureHandoffResult {
        return try {
            // Verify target device is trusted
            if (!trustManager.isTrusted(targetDeviceId)) {
                securityLogger.logEvent(
                    type = SecurityEventType.AUTHENTICATION_FAILURE,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Handoff rejected - target device not trusted",
                    relatedDeviceId = targetDeviceId
                )
                
                return SecureHandoffResult(
                    success = false,
                    transferredApps = emptyList(),
                    failedApps = emptyList(),
                    contextPreservationScore = 0.0f,
                    transferTime = Duration.ZERO,
                    securityVerified = false,
                    encryptionUsed = false,
                    trustLevel = TrustLevel.UNKNOWN,
                    errorMessage = "Target device not trusted"
                )
            }
            
            // Create handoff package
            val mentalContext = baseHandoffSystem.getMentalContext()
            val applicationStates = baseHandoffSystem.getApplicationStates()
            val deviceContext = DeviceContext(
                deviceId = deviceId.toString(),
                deviceType = "mobile", // This would be determined dynamically
                screenSize = Pair(1080f, 1920f),
                inputMethods = listOf("touch", "voice"),
                capabilities = listOf("handoff", "sync", "encryption"),
                batteryLevel = 0.8f,
                networkQuality = 0.9f
            )
            
            val handoffPackage = HandoffPackage(
                id = com.benasher44.uuid.uuid4().toString(),
                sourceDeviceId = deviceId.toString(),
                targetDeviceId = targetDeviceId.toString(),
                mentalContext = mentalContext,
                applicationStates = applicationStates,
                deviceContext = deviceContext,
                priority = priority,
                expirationTime = Clock.System.now().toEpochMilliseconds() + 300_000L // 5 minutes
            )
            
            // Encrypt handoff package
            val securePackage = encryptHandoffData(handoffPackage, targetDeviceId)
            if (securePackage == null) {
                return SecureHandoffResult(
                    success = false,
                    transferredApps = emptyList(),
                    failedApps = applicationStates.map { it.appId },
                    contextPreservationScore = 0.0f,
                    transferTime = Duration.ZERO,
                    securityVerified = false,
                    encryptionUsed = false,
                    trustLevel = trustManager.getTrustLevel(targetDeviceId),
                    errorMessage = "Failed to encrypt handoff data"
                )
            }
            
            // Add to current handoffs
            val currentHandoffs = _currentSecureHandoffs.value.toMutableList()
            currentHandoffs.add(securePackage)
            _currentSecureHandoffs.value = currentHandoffs
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure handoff initiated",
                relatedDeviceId = targetDeviceId,
                details = mapOf(
                    "handoff_id" to securePackage.id,
                    "priority" to priority.name,
                    "app_count" to applicationStates.size.toString()
                )
            )
            
            // Simulate successful handoff (in real implementation, this would involve network transfer)
            SecureHandoffResult(
                success = true,
                transferredApps = applicationStates.map { it.appId },
                failedApps = emptyList(),
                contextPreservationScore = 0.95f,
                transferTime = Duration.parse("2s"),
                securityVerified = true,
                encryptionUsed = true,
                trustLevel = trustManager.getTrustLevel(targetDeviceId),
                errorMessage = null
            )
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Secure handoff initiation failed",
                relatedDeviceId = targetDeviceId,
                error = e
            )
            
            SecureHandoffResult(
                success = false,
                transferredApps = emptyList(),
                failedApps = emptyList(),
                contextPreservationScore = 0.0f,
                transferTime = Duration.ZERO,
                securityVerified = false,
                encryptionUsed = false,
                trustLevel = TrustLevel.UNKNOWN,
                errorMessage = e.message
            )
        }
    }
    
    override suspend fun acceptSecureHandoff(handoffId: String): SecureHandoffResult {
        return try {
            val securePackage = _currentSecureHandoffs.value.find { it.id == handoffId }
                ?: return SecureHandoffResult(
                    success = false,
                    transferredApps = emptyList(),
                    failedApps = emptyList(),
                    contextPreservationScore = 0.0f,
                    transferTime = Duration.ZERO,
                    securityVerified = false,
                    encryptionUsed = false,
                    trustLevel = TrustLevel.UNKNOWN,
                    errorMessage = "Handoff not found"
                )
            
            // Verify security
            if (!verifyHandoffSecurity(securePackage)) {
                return SecureHandoffResult(
                    success = false,
                    transferredApps = emptyList(),
                    failedApps = emptyList(),
                    contextPreservationScore = 0.0f,
                    transferTime = Duration.ZERO,
                    securityVerified = false,
                    encryptionUsed = true,
                    trustLevel = TrustLevel.UNKNOWN,
                    errorMessage = "Security verification failed"
                )
            }
            
            // Decrypt handoff data
            val handoffPackage = decryptHandoffData(securePackage)
                ?: return SecureHandoffResult(
                    success = false,
                    transferredApps = emptyList(),
                    failedApps = emptyList(),
                    contextPreservationScore = 0.0f,
                    transferTime = Duration.ZERO,
                    securityVerified = true,
                    encryptionUsed = true,
                    trustLevel = TrustLevel.TRUSTED,
                    errorMessage = "Failed to decrypt handoff data"
                )
            
            // Reconstruct context using base system
            val reconstructed = baseHandoffSystem.reconstructContext(handoffPackage)
            
            // Remove from current handoffs
            val currentHandoffs = _currentSecureHandoffs.value.toMutableList()
            currentHandoffs.removeAll { it.id == handoffId }
            _currentSecureHandoffs.value = currentHandoffs
            
            val sourceDeviceId = Uuid.fromString(securePackage.sourceDeviceId)
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure handoff accepted and decrypted",
                relatedDeviceId = sourceDeviceId,
                details = mapOf(
                    "handoff_id" to handoffId,
                    "reconstructed" to reconstructed.toString(),
                    "app_count" to handoffPackage.applicationStates.size.toString()
                )
            )
            
            SecureHandoffResult(
                success = reconstructed,
                transferredApps = if (reconstructed) handoffPackage.applicationStates.map { it.appId } else emptyList(),
                failedApps = if (!reconstructed) handoffPackage.applicationStates.map { it.appId } else emptyList(),
                contextPreservationScore = if (reconstructed) 0.95f else 0.0f,
                transferTime = Duration.parse("1s"),
                securityVerified = true,
                encryptionUsed = true,
                trustLevel = trustManager.getTrustLevel(sourceDeviceId),
                errorMessage = if (!reconstructed) "Context reconstruction failed" else null
            )
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Secure handoff acceptance failed",
                error = e
            )
            
            SecureHandoffResult(
                success = false,
                transferredApps = emptyList(),
                failedApps = emptyList(),
                contextPreservationScore = 0.0f,
                transferTime = Duration.ZERO,
                securityVerified = false,
                encryptionUsed = false,
                trustLevel = TrustLevel.UNKNOWN,
                errorMessage = e.message
            )
        }
    }
    
    override suspend fun rejectSecureHandoff(handoffId: String, reason: String): Boolean {
        return try {
            val securePackage = _currentSecureHandoffs.value.find { it.id == handoffId }
            if (securePackage != null) {
                // Remove from current handoffs
                val currentHandoffs = _currentSecureHandoffs.value.toMutableList()
                currentHandoffs.removeAll { it.id == handoffId }
                _currentSecureHandoffs.value = currentHandoffs
                
                val sourceDeviceId = Uuid.fromString(securePackage.sourceDeviceId)
                securityLogger.logEvent(
                    type = SecurityEventType.AUTHENTICATION_FAILURE,
                    severity = SecurityEventSeverity.INFO,
                    message = "Secure handoff rejected",
                    relatedDeviceId = sourceDeviceId,
                    details = mapOf(
                        "handoff_id" to handoffId,
                        "reason" to reason
                    )
                )
                
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun cancelSecureHandoff(handoffId: String): Boolean {
        return rejectSecureHandoff(handoffId, "Cancelled by user")
    }
    
    override suspend fun verifyHandoffSecurity(handoffPackage: SecureHandoffPackage): Boolean {
        return try {
            val sourceDeviceId = Uuid.fromString(handoffPackage.sourceDeviceId)
            
            // Check if source device is trusted
            if (!trustManager.isTrusted(sourceDeviceId)) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Handoff security verification failed - source device not trusted",
                    relatedDeviceId = sourceDeviceId
                )
                return false
            }
            
            // Verify certificate
            val validationResult = certificateManager.validateCertificate(handoffPackage.deviceCertificate)
            if (validationResult !is CertificateValidationResult.Valid) {
                securityLogger.logEvent(
                    type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Handoff certificate validation failed",
                    relatedDeviceId = sourceDeviceId
                )
                return false
            }
            
            // Verify signature
            val signatureData = buildString {
                append("handoff_id:${handoffPackage.id}")
                append("|source:${handoffPackage.sourceDeviceId}")
                append("|target:${handoffPackage.targetDeviceId}")
                append("|timestamp:${handoffPackage.timestamp}")
            }.encodeToByteArray()
            
            val signatureValid = certificateManager.verifySignature(
                signatureData,
                handoffPackage.signature,
                sourceDeviceId
            )
            
            if (!signatureValid) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.ERROR,
                    message = "Handoff signature verification failed",
                    relatedDeviceId = sourceDeviceId
                )
                return false
            }
            
            // Check expiration
            val currentTime = Clock.System.now().toEpochMilliseconds()
            if (currentTime > handoffPackage.expirationTime) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Handoff package expired",
                    relatedDeviceId = sourceDeviceId
                )
                return false
            }
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SECURITY_VIOLATION,
                severity = SecurityEventSeverity.ERROR,
                message = "Handoff security verification error",
                error = e
            )
            false
        }
    }
    
    override suspend fun encryptHandoffData(handoffPackage: HandoffPackage, targetDeviceId: Uuid): SecureHandoffPackage? {
        return try {
            // Get or create session with target device
            val session = sessionManager.getActiveSessions().find { it.remoteDeviceId == targetDeviceId }
                ?: sessionManager.createSession(targetDeviceId).getOrNull()
                ?: return null
            
            // Serialize handoff package
            val jsonData = Json.encodeToString(handoffPackage)
            val plaintextData = jsonData.encodeToByteArray()
            
            // Encrypt data
            val encryptedMessage = sessionManager.encryptMessage(session.sessionId, plaintextData, "handoff")
                .getOrNull() ?: return null
            
            // Get device certificate
            val certificate = certificateManager.getCertificate(deviceId) ?: return null
            
            // Create signature
            val signatureData = buildString {
                append("handoff_id:${handoffPackage.id}")
                append("|source:${handoffPackage.sourceDeviceId}")
                append("|target:${handoffPackage.targetDeviceId}")
                append("|timestamp:${Clock.System.now().toEpochMilliseconds()}")
            }.encodeToByteArray()
            
            val signature = certificateManager.signData(signatureData) ?: return null
            
            SecureHandoffPackage(
                id = handoffPackage.id,
                sourceDeviceId = handoffPackage.sourceDeviceId,
                targetDeviceId = handoffPackage.targetDeviceId,
                encryptedPayload = encryptedMessage.encryptedData,
                nonce = encryptedMessage.nonce,
                authTag = encryptedMessage.authTag,
                deviceCertificate = certificate,
                signature = signature,
                priority = handoffPackage.priority,
                expirationTime = handoffPackage.expirationTime
            )
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to encrypt handoff data",
                relatedDeviceId = targetDeviceId,
                error = e
            )
            null
        }
    }
    
    override suspend fun decryptHandoffData(securePackage: SecureHandoffPackage): HandoffPackage? {
        return try {
            val sourceDeviceId = Uuid.fromString(securePackage.sourceDeviceId)
            
            // Get session with source device
            val session = sessionManager.getActiveSessions().find { it.remoteDeviceId == sourceDeviceId }
                ?: return null
            
            // Create encrypted message
            val encryptedMessage = EncryptedMessage(
                sessionId = session.sessionId,
                messageId = com.benasher44.uuid.uuid4(),
                encryptedData = securePackage.encryptedPayload,
                nonce = securePackage.nonce,
                authTag = securePackage.authTag,
                timestamp = securePackage.timestamp,
                messageType = "handoff"
            )
            
            // Decrypt data
            val decryptedData = sessionManager.decryptMessage(encryptedMessage)
                .getOrNull() ?: return null
            
            // Deserialize handoff package
            val jsonData = decryptedData.decodeToString()
            Json.decodeFromString<HandoffPackage>(jsonData)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to decrypt handoff data",
                error = e
            )
            null
        }
    }
    
    private suspend fun updateAvailableTargets() {
        try {
            val trustedDevices = trustManager.getTrustedDevices()
            _availableTargets.value = trustedDevices.map { it.toString() }
        } catch (e: Exception) {
            _availableTargets.value = emptyList()
        }
    }
}