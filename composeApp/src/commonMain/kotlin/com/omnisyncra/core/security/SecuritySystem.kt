package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.StateFlow

/**
 * Core security system interface for Omnisyncra Phase 13
 * Provides comprehensive security, encryption, and trust management
 */
interface SecuritySystem {
    suspend fun initialize(): Boolean
    suspend fun createSecureChannel(deviceId: Uuid): SecureChannel?
    suspend fun authenticateDevice(deviceId: Uuid, certificate: DeviceCertificate): AuthResult
    suspend fun establishTrust(deviceId: Uuid, method: TrustMethod): TrustResult
    suspend fun revokeTrust(deviceId: Uuid): Boolean
    fun getSecurityStatus(): SecurityStatus
    suspend fun shutdown()
}

/**
 * Secure communication channel between devices
 */
interface SecureChannel {
    val deviceId: Uuid
    val isActive: Boolean
    val encryptionAlgorithm: EncryptionAlgorithm
    
    suspend fun send(data: ByteArray): Boolean
    suspend fun receive(): ByteArray?
    suspend fun close()
}

/**
 * Authentication result
 */
sealed class AuthResult {
    object Success : AuthResult()
    data class Failed(val reason: String) : AuthResult()
    data class Pending(val challengeData: ByteArray) : AuthResult()
}

/**
 * Trust establishment methods
 */
enum class TrustMethod {
    QR_CODE,
    PIN_VERIFICATION,
    CERTIFICATE_CHAIN,
    MANUAL_APPROVAL
}

/**
 * Trust establishment result
 */
sealed class TrustResult {
    object Success : TrustResult()
    data class Failed(val reason: String) : TrustResult()
    data class RequiresUserAction(val action: String, val data: ByteArray) : TrustResult()
}

/**
 * Overall security system status
 */
data class SecurityStatus(
    val isInitialized: Boolean,
    val activeChannels: Int,
    val trustedDevices: Int,
    val pendingAuthentications: Int,
    val lastSecurityEvent: com.omnisyncra.core.security.SecurityEvent?
)

// Note: SecurityEvent, SecurityEventType, and SecurityEventSeverity are defined in SecurityEventLogger.kt

/**
 * Enhanced device certificate with additional security features
 */
data class DeviceCertificate(
    val deviceId: Uuid,
    val publicKey: ByteArray,
    val issuer: String,
    val subject: String,
    val validFrom: Long,
    val validUntil: Long,
    val signature: ByteArray,
    val serialNumber: String,
    val version: Int = 1,
    val keyUsage: Set<KeyUsage> = setOf(KeyUsage.DIGITAL_SIGNATURE, KeyUsage.KEY_AGREEMENT),
    val extensions: Map<String, ByteArray> = emptyMap()
) {
    fun isValid(currentTime: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()): Boolean {
        return currentTime in validFrom..validUntil
    }
    
    fun isExpiringSoon(thresholdMs: Long = 7 * 24 * 60 * 60 * 1000L): Boolean {
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return (validUntil - currentTime) <= thresholdMs
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as DeviceCertificate
        return deviceId == other.deviceId && serialNumber == other.serialNumber
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + serialNumber.hashCode()
        return result
    }
}

enum class KeyUsage {
    DIGITAL_SIGNATURE,
    KEY_AGREEMENT,
    KEY_ENCIPHERMENT,
    DATA_ENCIPHERMENT,
    CERTIFICATE_SIGNING
}

/**
 * Trust levels with enhanced granularity
 */
enum class TrustLevel {
    UNKNOWN,
    PENDING,
    TRUSTED,
    VERIFIED,
    REVOKED
}

/**
 * Device identity with comprehensive trust information
 */
data class DeviceIdentity(
    val deviceId: Uuid,
    val certificate: DeviceCertificate,
    val trustLevel: TrustLevel = TrustLevel.PENDING,
    val trustedAt: Long? = null,
    val trustedBy: Uuid? = null,
    val trustMethod: TrustMethod? = null,
    val lastSeen: Long? = null,
    val capabilities: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap()
)