package com.omnisyncra.core.security

import kotlinx.coroutines.flow.StateFlow
import com.omnisyncra.core.platform.TimeUtils

/**
 * Core Security System Interface
 * Provides comprehensive security services for Omnisyncra
 */
interface SecuritySystem {
    /**
     * Current security system status
     */
    val status: StateFlow<SecurityStatus>
    
    /**
     * Initialize the security system
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * Encrypt data using AES-256-GCM
     */
    suspend fun encrypt(data: ByteArray, key: ByteArray): Result<EncryptedData>
    
    /**
     * Decrypt data using AES-256-GCM
     */
    suspend fun decrypt(encryptedData: EncryptedData, key: ByteArray): Result<ByteArray>
    
    /**
     * Generate a new encryption key
     */
    suspend fun generateKey(): Result<ByteArray>
    
    /**
     * Perform key exchange with another device
     */
    suspend fun performKeyExchange(deviceId: String): Result<SharedSecret>
    
    /**
     * Sign data with device private key
     */
    suspend fun signData(data: ByteArray): Result<Signature>
    
    /**
     * Verify signature with device public key
     */
    suspend fun verifySignature(data: ByteArray, signature: Signature, publicKey: ByteArray): Result<Boolean>
    
    /**
     * Get device certificate
     */
    suspend fun getDeviceCertificate(): Result<DeviceCertificate>
    
    /**
     * Establish trust with another device
     */
    suspend fun establishTrust(deviceId: String, certificate: DeviceCertificate): Result<TrustLevel>
    
    /**
     * Get trust level for a device
     */
    suspend fun getTrustLevel(deviceId: String): TrustLevel
    
    /**
     * Shutdown the security system
     */
    suspend fun shutdown()
}

/**
 * Security System Status
 */
enum class SecurityStatus {
    INITIALIZING,
    READY,
    PROCESSING,
    ERROR,
    SHUTDOWN
}

/**
 * Encrypted data container
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val tag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as EncryptedData
        
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!tag.contentEquals(other.tag)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}

/**
 * Shared secret from key exchange
 */
data class SharedSecret(
    val secret: ByteArray,
    val deviceId: String,
    val timestamp: Long = TimeUtils.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as SharedSecret
        
        if (!secret.contentEquals(other.secret)) return false
        if (deviceId != other.deviceId) return false
        if (timestamp != other.timestamp) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = secret.contentHashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Digital signature
 */
data class Signature(
    val signature: ByteArray,
    val algorithm: String = "Ed25519",
    val timestamp: Long = TimeUtils.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as Signature
        
        if (!signature.contentEquals(other.signature)) return false
        if (algorithm != other.algorithm) return false
        if (timestamp != other.timestamp) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = signature.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Device certificate for authentication
 */
data class DeviceCertificate(
    val deviceId: String,
    val publicKey: ByteArray,
    val signature: ByteArray,
    val issuer: String = "self-signed",
    val validFrom: Long = TimeUtils.currentTimeMillis(),
    val validUntil: Long = TimeUtils.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000L), // 1 year
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as DeviceCertificate
        
        if (deviceId != other.deviceId) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!signature.contentEquals(other.signature)) return false
        if (issuer != other.issuer) return false
        if (validFrom != other.validFrom) return false
        if (validUntil != other.validUntil) return false
        if (metadata != other.metadata) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + issuer.hashCode()
        result = 31 * result + validFrom.hashCode()
        result = 31 * result + validUntil.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Trust levels for devices
 */
enum class TrustLevel {
    UNKNOWN,
    PENDING,
    TRUSTED,
    REVOKED
}

/**
 * Types of security events
 */
enum class SecurityEventType {
    ENCRYPTION,
    DECRYPTION,
    KEY_EXCHANGE,
    SIGNATURE_CREATED,
    SIGNATURE_VERIFIED,
    TRUST_ESTABLISHED,
    TRUST_REVOKED,
    CERTIFICATE_GENERATED,
    CERTIFICATE_VALIDATED,
    SECURITY_ERROR,
    AUTHENTICATION_SUCCESS,
    AUTHENTICATION_FAILURE
}