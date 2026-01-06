package com.omnisyncra.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.platform.TimeUtils

/**
 * Main Security System Implementation
 * Provides comprehensive security services with cross-platform support
 */
class OmnisyncraSecuritySystem(
    private val deviceId: String
) : SecuritySystem {
    
    private val _status = MutableStateFlow(SecurityStatus.INITIALIZING)
    override val status: StateFlow<SecurityStatus> = _status.asStateFlow()
    
    private lateinit var cryptoEngine: CryptoEngine
    private var deviceKeyPair: KeyPair? = null
    private var deviceCertificate: DeviceCertificate? = null
    private val trustStore = mutableMapOf<String, TrustLevel>()
    private val sharedSecrets = mutableMapOf<String, SharedSecret>()
    private val securityEvents = mutableListOf<SecurityEvent>()
    
    override suspend fun initialize(): Result<Unit> {
        return try {
            _status.value = SecurityStatus.INITIALIZING
            
            // Initialize crypto engine
            cryptoEngine = CryptoEngine()
            
            // Generate device key pair if not exists
            if (deviceKeyPair == null) {
                deviceKeyPair = cryptoEngine.generateEd25519KeyPair()
            }
            
            // Generate device certificate
            deviceCertificate = generateDeviceCertificate()
            
            logSecurityEvent(SecurityEventType.CERTIFICATE_GENERATED, "Device certificate generated")
            
            _status.value = SecurityStatus.READY
            Result.success(Unit)
        } catch (e: Exception) {
            _status.value = SecurityStatus.ERROR
            logSecurityEvent(SecurityEventType.SECURITY_ERROR, "Failed to initialize security system: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun encrypt(data: ByteArray, key: ByteArray): Result<EncryptedData> {
        if (_status.value != SecurityStatus.READY) {
            return Result.failure(IllegalStateException("Security system not ready"))
        }
        
        return try {
            _status.value = SecurityStatus.PROCESSING
            
            val nonce = cryptoEngine.generateRandomBytes(12) // 96-bit nonce for GCM
            val encryptedData = cryptoEngine.encryptAES256GCM(data, key, nonce)
            
            logSecurityEvent(SecurityEventType.ENCRYPTION, "Data encrypted successfully")
            
            _status.value = SecurityStatus.READY
            Result.success(encryptedData)
        } catch (e: Exception) {
            _status.value = SecurityStatus.ERROR
            logSecurityEvent(SecurityEventType.SECURITY_ERROR, "Encryption failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun decrypt(encryptedData: EncryptedData, key: ByteArray): Result<ByteArray> {
        if (_status.value != SecurityStatus.READY) {
            return Result.failure(IllegalStateException("Security system not ready"))
        }
        
        return try {
            _status.value = SecurityStatus.PROCESSING
            
            val decryptedData = cryptoEngine.decryptAES256GCM(encryptedData, key)
            
            logSecurityEvent(SecurityEventType.DECRYPTION, "Data decrypted successfully")
            
            _status.value = SecurityStatus.READY
            Result.success(decryptedData)
        } catch (e: Exception) {
            _status.value = SecurityStatus.ERROR
            logSecurityEvent(SecurityEventType.SECURITY_ERROR, "Decryption failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun generateKey(): Result<ByteArray> {
        return try {
            val key = cryptoEngine.generateRandomBytes(32) // 256-bit key
            Result.success(key)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun performKeyExchange(deviceId: String): Result<SharedSecret> {
        if (_status.value != SecurityStatus.READY) {
            return Result.failure(IllegalStateException("Security system not ready"))
        }
        
        return try {
            _status.value = SecurityStatus.PROCESSING
            
            // Generate ephemeral key pair for this exchange
            val ephemeralKeyPair = cryptoEngine.generateX25519KeyPair()
            
            // In a real implementation, this would involve network communication
            // For now, we'll simulate the key exchange
            val otherPublicKey = cryptoEngine.generateRandomBytes(32) // Simulated other device's public key
            
            val sharedSecret = cryptoEngine.performX25519KeyExchange(
                ephemeralKeyPair.privateKey,
                otherPublicKey
            )
            
            val secret = SharedSecret(sharedSecret, deviceId)
            sharedSecrets[deviceId] = secret
            
            logSecurityEvent(SecurityEventType.KEY_EXCHANGE, "Key exchange completed", deviceId)
            
            _status.value = SecurityStatus.READY
            Result.success(secret)
        } catch (e: Exception) {
            _status.value = SecurityStatus.ERROR
            logSecurityEvent(SecurityEventType.SECURITY_ERROR, "Key exchange failed: ${e.message}", deviceId)
            Result.failure(e)
        }
    }
    
    override suspend fun signData(data: ByteArray): Result<Signature> {
        if (_status.value != SecurityStatus.READY) {
            return Result.failure(IllegalStateException("Security system not ready"))
        }
        
        val keyPair = deviceKeyPair ?: return Result.failure(IllegalStateException("Device key pair not available"))
        
        return try {
            _status.value = SecurityStatus.PROCESSING
            
            val signatureBytes = cryptoEngine.signEd25519(data, keyPair.privateKey)
            val signature = Signature(signatureBytes)
            
            logSecurityEvent(SecurityEventType.SIGNATURE_CREATED, "Data signed successfully")
            
            _status.value = SecurityStatus.READY
            Result.success(signature)
        } catch (e: Exception) {
            _status.value = SecurityStatus.ERROR
            logSecurityEvent(SecurityEventType.SECURITY_ERROR, "Signing failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun verifySignature(data: ByteArray, signature: Signature, publicKey: ByteArray): Result<Boolean> {
        if (_status.value != SecurityStatus.READY) {
            return Result.failure(IllegalStateException("Security system not ready"))
        }
        
        return try {
            _status.value = SecurityStatus.PROCESSING
            
            val isValid = cryptoEngine.verifyEd25519(data, signature.signature, publicKey)
            
            logSecurityEvent(
                if (isValid) SecurityEventType.SIGNATURE_VERIFIED else SecurityEventType.SECURITY_ERROR,
                "Signature verification: ${if (isValid) "valid" else "invalid"}"
            )
            
            _status.value = SecurityStatus.READY
            Result.success(isValid)
        } catch (e: Exception) {
            _status.value = SecurityStatus.ERROR
            logSecurityEvent(SecurityEventType.SECURITY_ERROR, "Signature verification failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun getDeviceCertificate(): Result<DeviceCertificate> {
        val certificate = deviceCertificate ?: return Result.failure(IllegalStateException("Device certificate not available"))
        return Result.success(certificate)
    }
    
    override suspend fun establishTrust(deviceId: String, certificate: DeviceCertificate): Result<TrustLevel> {
        return try {
            // Validate certificate
            val isValid = validateCertificate(certificate)
            
            val trustLevel = if (isValid) {
                TrustLevel.TRUSTED
            } else {
                TrustLevel.PENDING
            }
            
            trustStore[deviceId] = trustLevel
            
            logSecurityEvent(
                SecurityEventType.TRUST_ESTABLISHED,
                "Trust established with level: $trustLevel",
                deviceId
            )
            
            Result.success(trustLevel)
        } catch (e: Exception) {
            logSecurityEvent(SecurityEventType.SECURITY_ERROR, "Trust establishment failed: ${e.message}", deviceId)
            Result.failure(e)
        }
    }
    
    override suspend fun getTrustLevel(deviceId: String): TrustLevel {
        return trustStore[deviceId] ?: TrustLevel.UNKNOWN
    }
    
    override suspend fun shutdown() {
        _status.value = SecurityStatus.SHUTDOWN
        
        // Clear sensitive data
        sharedSecrets.clear()
        deviceKeyPair = null
        
        logSecurityEvent(SecurityEventType.SECURITY_ERROR, "Security system shutdown")
    }
    
    /**
     * Get security events for monitoring
     */
    fun getSecurityEvents(): List<SecurityEvent> {
        return securityEvents.toList()
    }
    
    /**
     * Clear security events
     */
    fun clearSecurityEvents() {
        securityEvents.clear()
    }
    
    private suspend fun generateDeviceCertificate(): DeviceCertificate {
        val keyPair = deviceKeyPair ?: throw IllegalStateException("Device key pair not available")
        
        // Create certificate data
        val certificateData = "$deviceId:${keyPair.publicKey.contentHashCode()}:${TimeUtils.currentTimeMillis()}"
        
        // Sign the certificate with device private key (self-signed)
        val signature = cryptoEngine.signEd25519(certificateData.encodeToByteArray(), keyPair.privateKey)
        
        return DeviceCertificate(
            deviceId = deviceId,
            publicKey = keyPair.publicKey,
            signature = signature,
            metadata = mapOf(
                "algorithm" to "Ed25519",
                "version" to "1.0"
            )
        )
    }
    
    private suspend fun validateCertificate(certificate: DeviceCertificate): Boolean {
        return try {
            // Check expiration
            val currentTime = TimeUtils.currentTimeMillis()
            if (currentTime < certificate.validFrom || currentTime > certificate.validUntil) {
                return false
            }
            
            // Verify self-signature
            val certificateData = "${certificate.deviceId}:${certificate.publicKey.contentHashCode()}:${certificate.validFrom}"
            cryptoEngine.verifyEd25519(certificateData.encodeToByteArray(), certificate.signature, certificate.publicKey)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun logSecurityEvent(type: SecurityEventType, message: String, deviceId: String? = null) {
        val event = SecurityEvent(
            id = uuid4().toString(),
            type = type.name,
            deviceId = deviceId,
            message = message,
            timestamp = TimeUtils.currentTimeMillis(),
            severity = if (type == SecurityEventType.SECURITY_ERROR) "ERROR" else "INFO"
        )
        securityEvents.add(event)
        
        // Keep only last 1000 events
        if (securityEvents.size > 1000) {
            securityEvents.removeAt(0)
        }
    }
}