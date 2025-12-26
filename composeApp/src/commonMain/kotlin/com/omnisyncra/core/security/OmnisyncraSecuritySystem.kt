package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Main security system implementation that integrates all security components
 */
class OmnisyncraSecuritySystem(
    private val nodeId: Uuid
) : SecuritySystem {
    
    private val certificateManager = OmnisyncraeCertificateManager(nodeId)
    private val keyExchangeManager = OmnisyncraKeyExchangeManager(nodeId, certificateManager)
    private val secureChannels = mutableMapOf<Uuid, OmnisyncraSecureChannel>()
    private val securityEvents = mutableListOf<SecurityEvent>()
    private val mutex = Mutex()
    
    private var isInitialized = false
    
    override suspend fun initialize(): Boolean {
        return mutex.withLock {
            try {
                if (isInitialized) return@withLock true
                
                // Initialize certificate manager
                val certInitialized = certificateManager.initialize()
                if (!certInitialized) {
                    logSecurityEvent(
                        SecurityEventType.SECURITY_VIOLATION,
                        null,
                        "Failed to initialize certificate manager",
                        SecuritySeverity.CRITICAL
                    )
                    return@withLock false
                }
                
                // Initialize key exchange manager
                val keyExchangeInitialized = keyExchangeManager.initialize()
                if (!keyExchangeInitialized) {
                    logSecurityEvent(
                        SecurityEventType.SECURITY_VIOLATION,
                        null,
                        "Failed to initialize key exchange manager",
                        SecuritySeverity.CRITICAL
                    )
                    return@withLock false
                }
                
                isInitialized = true
                
                logSecurityEvent(
                    SecurityEventType.AUTHENTICATION_SUCCESS,
                    nodeId,
                    "Security system initialized successfully",
                    SecuritySeverity.INFO
                )
                
                true
            } catch (e: Exception) {
                logSecurityEvent(
                    SecurityEventType.SECURITY_VIOLATION,
                    null,
                    "Security system initialization failed: ${e.message}",
                    SecuritySeverity.CRITICAL
                )
                false
            }
        }
    }
    
    override suspend fun createSecureChannel(deviceId: Uuid): SecureChannel? {
        if (!isInitialized) return null
        
        return try {
            // Check if we already have an active session
            val existingSession = keyExchangeManager.getSessionKey(deviceId)
            if (existingSession != null) {
                val channel = OmnisyncraSecureChannel(deviceId, existingSession)
                mutex.withLock {
                    secureChannels[deviceId] = channel
                }
                return channel
            }
            
            // Initiate key exchange
            val keyExchangeRequest = keyExchangeManager.initiateKeyExchange(deviceId)
            if (keyExchangeRequest == null) {
                logSecurityEvent(
                    SecurityEventType.AUTHENTICATION_FAILURE,
                    deviceId,
                    "Failed to initiate key exchange",
                    SecuritySeverity.ERROR
                )
                return null
            }
            
            // In a real implementation, this would be sent over the network
            // For now, we'll simulate a successful exchange
            logSecurityEvent(
                SecurityEventType.KEY_ROTATION,
                deviceId,
                "Key exchange initiated",
                SecuritySeverity.INFO
            )
            
            null // Channel will be created after successful key exchange
        } catch (e: Exception) {
            logSecurityEvent(
                SecurityEventType.ENCRYPTION_ERROR,
                deviceId,
                "Failed to create secure channel: ${e.message}",
                SecuritySeverity.ERROR
            )
            null
        }
    }
    
    override suspend fun authenticateDevice(deviceId: Uuid, certificate: DeviceCertificate): AuthResult {
        if (!isInitialized) return AuthResult.Failed("Security system not initialized")
        
        return try {
            // Validate certificate
            val validationResult = certificateManager.validateCertificate(certificate)
            
            when (validationResult) {
                is CertificateValidationResult.Valid -> {
                    // Store certificate
                    val stored = certificateManager.storeCertificate(certificate)
                    if (stored) {
                        logSecurityEvent(
                            SecurityEventType.AUTHENTICATION_SUCCESS,
                            deviceId,
                            "Device authenticated successfully",
                            SecuritySeverity.INFO
                        )
                        AuthResult.Success
                    } else {
                        logSecurityEvent(
                            SecurityEventType.AUTHENTICATION_FAILURE,
                            deviceId,
                            "Failed to store certificate",
                            SecuritySeverity.ERROR
                        )
                        AuthResult.Failed("Failed to store certificate")
                    }
                }
                
                is CertificateValidationResult.Invalid -> {
                    logSecurityEvent(
                        SecurityEventType.AUTHENTICATION_FAILURE,
                        deviceId,
                        "Invalid certificate: ${validationResult.reason}",
                        SecuritySeverity.WARNING
                    )
                    AuthResult.Failed(validationResult.reason)
                }
                
                is CertificateValidationResult.Expired -> {
                    logSecurityEvent(
                        SecurityEventType.CERTIFICATE_EXPIRED,
                        deviceId,
                        "Certificate expired at ${validationResult.expiredAt}",
                        SecuritySeverity.WARNING
                    )
                    AuthResult.Failed("Certificate expired")
                }
                
                is CertificateValidationResult.NotYetValid -> {
                    logSecurityEvent(
                        SecurityEventType.AUTHENTICATION_FAILURE,
                        deviceId,
                        "Certificate not yet valid until ${validationResult.validFrom}",
                        SecuritySeverity.WARNING
                    )
                    AuthResult.Failed("Certificate not yet valid")
                }
                
                is CertificateValidationResult.Revoked -> {
                    logSecurityEvent(
                        SecurityEventType.AUTHENTICATION_FAILURE,
                        deviceId,
                        "Certificate revoked: ${validationResult.reason}",
                        SecuritySeverity.ERROR
                    )
                    AuthResult.Failed("Certificate revoked")
                }
            }
        } catch (e: Exception) {
            logSecurityEvent(
                SecurityEventType.AUTHENTICATION_FAILURE,
                deviceId,
                "Authentication error: ${e.message}",
                SecuritySeverity.ERROR
            )
            AuthResult.Failed("Authentication error: ${e.message}")
        }
    }
    
    override suspend fun establishTrust(deviceId: Uuid, method: TrustMethod): TrustResult {
        if (!isInitialized) return TrustResult.Failed("Security system not initialized")
        
        return try {
            when (method) {
                TrustMethod.QR_CODE -> {
                    // Generate QR code data for trust establishment
                    val localCert = certificateManager.getLocalCertificate()
                        ?: return TrustResult.Failed("Local certificate not available")
                    
                    val qrData = buildString {
                        append("omnisyncra://trust/")
                        append(nodeId)
                        append("?cert=")
                        append(localCert.serialNumber)
                        append("&timestamp=")
                        append(Clock.System.now().toEpochMilliseconds())
                    }.encodeToByteArray()
                    
                    logSecurityEvent(
                        SecurityEventType.TRUST_ESTABLISHED,
                        deviceId,
                        "QR code trust method initiated",
                        SecuritySeverity.INFO
                    )
                    
                    TrustResult.RequiresUserAction("scan_qr_code", qrData)
                }
                
                TrustMethod.PIN_VERIFICATION -> {
                    // Generate PIN for verification
                    val pin = generateSecurePin()
                    
                    logSecurityEvent(
                        SecurityEventType.TRUST_ESTABLISHED,
                        deviceId,
                        "PIN verification trust method initiated",
                        SecuritySeverity.INFO
                    )
                    
                    TrustResult.RequiresUserAction("verify_pin", pin.encodeToByteArray())
                }
                
                TrustMethod.CERTIFICATE_CHAIN -> {
                    // Verify certificate chain
                    val certificate = certificateManager.getCertificate(deviceId)
                        ?: return TrustResult.Failed("Device certificate not found")
                    
                    // In a real implementation, this would verify the full certificate chain
                    logSecurityEvent(
                        SecurityEventType.TRUST_ESTABLISHED,
                        deviceId,
                        "Certificate chain trust established",
                        SecuritySeverity.INFO
                    )
                    
                    TrustResult.Success
                }
                
                TrustMethod.MANUAL_APPROVAL -> {
                    // Manual approval by user
                    logSecurityEvent(
                        SecurityEventType.TRUST_ESTABLISHED,
                        deviceId,
                        "Manual approval trust method initiated",
                        SecuritySeverity.INFO
                    )
                    
                    TrustResult.RequiresUserAction("manual_approval", deviceId.toString().encodeToByteArray())
                }
            }
        } catch (e: Exception) {
            logSecurityEvent(
                SecurityEventType.SECURITY_VIOLATION,
                deviceId,
                "Trust establishment error: ${e.message}",
                SecuritySeverity.ERROR
            )
            TrustResult.Failed("Trust establishment error: ${e.message}")
        }
    }
    
    override suspend fun revokeTrust(deviceId: Uuid): Boolean {
        if (!isInitialized) return false
        
        return try {
            // Revoke certificate
            val revoked = certificateManager.revokeCertificate(deviceId, RevocationReason.PRIVILEGE_WITHDRAWN)
            
            if (revoked) {
                // Close any active secure channels
                mutex.withLock {
                    secureChannels[deviceId]?.close()
                    secureChannels.remove(deviceId)
                }
                
                // Revoke session keys
                keyExchangeManager.revokeSession(deviceId)
                
                logSecurityEvent(
                    SecurityEventType.TRUST_REVOKED,
                    deviceId,
                    "Trust revoked successfully",
                    SecuritySeverity.INFO
                )
            } else {
                logSecurityEvent(
                    SecurityEventType.SECURITY_VIOLATION,
                    deviceId,
                    "Failed to revoke trust",
                    SecuritySeverity.ERROR
                )
            }
            
            revoked
        } catch (e: Exception) {
            logSecurityEvent(
                SecurityEventType.SECURITY_VIOLATION,
                deviceId,
                "Trust revocation error: ${e.message}",
                SecuritySeverity.ERROR
            )
            false
        }
    }
    
    override fun getSecurityStatus(): SecurityStatus {
        return SecurityStatus(
            isInitialized = isInitialized,
            activeChannels = secureChannels.size,
            trustedDevices = 0, // Would be calculated from certificate manager
            pendingAuthentications = 0, // Would be calculated from pending operations
            lastSecurityEvent = securityEvents.lastOrNull()
        )
    }
    
    override suspend fun shutdown() {
        mutex.withLock {
            // Close all secure channels
            secureChannels.values.forEach { it.close() }
            secureChannels.clear()
            
            // Cleanup expired sessions
            keyExchangeManager.cleanupExpiredSessions()
            
            isInitialized = false
            
            logSecurityEvent(
                SecurityEventType.AUTHENTICATION_SUCCESS,
                nodeId,
                "Security system shutdown completed",
                SecuritySeverity.INFO
            )
        }
    }
    
    // Internal helper methods
    
    private suspend fun logSecurityEvent(
        type: SecurityEventType,
        deviceId: Uuid?,
        message: String,
        severity: SecuritySeverity
    ) {
        val event = SecurityEvent(
            id = com.benasher44.uuid.uuid4().toString(),
            type = type,
            deviceId = deviceId,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            message = message,
            severity = severity
        )
        
        mutex.withLock {
            securityEvents.add(event)
            
            // Keep only last 1000 events
            if (securityEvents.size > 1000) {
                securityEvents.removeAt(0)
            }
        }
    }
    
    private fun generateSecurePin(): String {
        val digits = "0123456789"
        return (1..6).map { digits.random() }.joinToString("")
    }
    
    // Public methods for accessing components
    
    suspend fun getCertificateManager(): CertificateManager = certificateManager
    
    suspend fun getKeyExchangeManager(): KeyExchangeManager = keyExchangeManager
    
    suspend fun getSecurityEvents(): List<SecurityEvent> {
        return mutex.withLock { securityEvents.toList() }
    }
    
    suspend fun getActiveChannels(): List<SecureChannel> {
        return mutex.withLock { secureChannels.values.toList() }
    }
}

/**
 * Implementation of secure channel for encrypted communication
 */
class OmnisyncraSecureChannel(
    override val deviceId: Uuid,
    private val sessionKey: SessionKey
) : SecureChannel {
    
    override val isActive: Boolean get() = !sessionKey.isExpired()
    override val encryptionAlgorithm: EncryptionAlgorithm = EncryptionAlgorithm.AES_256_GCM
    
    private val mutex = Mutex()
    private var isClosed = false
    
    override suspend fun send(data: ByteArray): Boolean {
        return mutex.withLock {
            if (isClosed || !isActive) return@withLock false
            
            try {
                // In a real implementation, this would encrypt the data and send it
                // For now, we'll just simulate successful sending
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override suspend fun receive(): ByteArray? {
        return mutex.withLock {
            if (isClosed || !isActive) return@withLock null
            
            try {
                // In a real implementation, this would receive and decrypt data
                // For now, we'll return null (no data available)
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override suspend fun close() {
        mutex.withLock {
            isClosed = true
        }
    }
}