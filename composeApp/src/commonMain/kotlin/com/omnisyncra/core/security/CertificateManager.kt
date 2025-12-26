package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Certificate management system for device authentication and trust
 */
interface CertificateManager {
    suspend fun generateSelfSignedCertificate(
        deviceId: Uuid,
        keyPair: KeyPair,
        validityDays: Int = 365
    ): DeviceCertificate
    
    suspend fun validateCertificate(certificate: DeviceCertificate): CertificateValidationResult
    suspend fun storeCertificate(certificate: DeviceCertificate): Boolean
    suspend fun getCertificate(deviceId: Uuid): DeviceCertificate?
    suspend fun revokeCertificate(deviceId: Uuid, reason: RevocationReason): Boolean
    suspend fun renewCertificate(deviceId: Uuid): DeviceCertificate?
    suspend fun getExpiringCertificates(thresholdDays: Int = 7): List<DeviceCertificate>
    suspend fun cleanupExpiredCertificates(): Int
}

/**
 * Certificate validation result
 */
sealed class CertificateValidationResult {
    object Valid : CertificateValidationResult()
    data class Invalid(val reason: String) : CertificateValidationResult()
    data class Expired(val expiredAt: Long) : CertificateValidationResult()
    data class NotYetValid(val validFrom: Long) : CertificateValidationResult()
    data class Revoked(val revokedAt: Long, val reason: RevocationReason) : CertificateValidationResult()
}

/**
 * Certificate revocation reasons
 */
enum class RevocationReason {
    KEY_COMPROMISE,
    DEVICE_COMPROMISE,
    SUPERSEDED,
    CESSATION_OF_OPERATION,
    PRIVILEGE_WITHDRAWN,
    UNSPECIFIED
}

/**
 * Certificate revocation entry
 */
data class CertificateRevocation(
    val serialNumber: String,
    val deviceId: Uuid,
    val revokedAt: Long,
    val reason: RevocationReason,
    val revokedBy: Uuid
)

/**
 * Platform-specific certificate operations
 */
expect class PlatformCertificateManager() {
    suspend fun generateKeyPair(): KeyPair
    suspend fun signCertificate(certificateData: ByteArray, privateKey: ByteArray): ByteArray
    suspend fun verifyCertificateSignature(
        certificateData: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Boolean
    suspend fun securelyStoreCertificate(certificate: DeviceCertificate): Boolean
    suspend fun securelyRetrieveCertificate(deviceId: Uuid): DeviceCertificate?
    suspend fun securelyDeleteCertificate(deviceId: Uuid): Boolean
}

/**
 * Implementation of certificate management system
 */
class OmnisyncraeCertificateManager(
    private val nodeId: Uuid,
    private val platformManager: PlatformCertificateManager = PlatformCertificateManager()
) : CertificateManager {
    
    private val certificates = mutableMapOf<Uuid, DeviceCertificate>()
    private val revocations = mutableMapOf<String, CertificateRevocation>()
    private val mutex = Mutex()
    
    private var localKeyPair: KeyPair? = null
    private var localCertificate: DeviceCertificate? = null
    
    suspend fun initialize(): Boolean {
        return mutex.withLock {
            try {
                // Generate or load local key pair
                localKeyPair = platformManager.generateKeyPair()
                
                // Try to load existing certificate
                localCertificate = platformManager.securelyRetrieveCertificate(nodeId)
                
                // Generate new certificate if none exists or if expired
                if (localCertificate?.isValid() != true) {
                    localCertificate = generateSelfSignedCertificate(
                        deviceId = nodeId,
                        keyPair = localKeyPair!!
                    )
                    platformManager.securelyStoreCertificate(localCertificate!!)
                }
                
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun getLocalCertificate(): DeviceCertificate? {
        return mutex.withLock { localCertificate }
    }
    
    override suspend fun generateSelfSignedCertificate(
        deviceId: Uuid,
        keyPair: KeyPair,
        validityDays: Int
    ): DeviceCertificate {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val validityMs = validityDays * 24L * 60 * 60 * 1000
        
        val serialNumber = com.benasher44.uuid.uuid4().toString()
        
        // Create certificate data for signing
        val certData = buildString {
            append("deviceId:$deviceId")
            append("|serialNumber:$serialNumber")
            append("|validFrom:$currentTime")
            append("|validUntil:${currentTime + validityMs}")
            append("|publicKey:${keyPair.publicKey.contentHashCode()}")
        }.encodeToByteArray()
        
        val signature = platformManager.signCertificate(certData, keyPair.privateKey)
        
        return DeviceCertificate(
            deviceId = deviceId,
            publicKey = keyPair.publicKey,
            issuer = "self-signed",
            subject = "device:$deviceId",
            validFrom = currentTime,
            validUntil = currentTime + validityMs,
            signature = signature,
            serialNumber = serialNumber,
            keyUsage = setOf(KeyUsage.DIGITAL_SIGNATURE, KeyUsage.KEY_AGREEMENT)
        )
    }
    
    override suspend fun validateCertificate(certificate: DeviceCertificate): CertificateValidationResult {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        // Check if revoked
        val revocation = mutex.withLock { revocations[certificate.serialNumber] }
        if (revocation != null) {
            return CertificateValidationResult.Revoked(revocation.revokedAt, revocation.reason)
        }
        
        // Check validity period
        if (currentTime < certificate.validFrom) {
            return CertificateValidationResult.NotYetValid(certificate.validFrom)
        }
        
        if (currentTime > certificate.validUntil) {
            return CertificateValidationResult.Expired(certificate.validUntil)
        }
        
        // Verify signature
        val certData = buildString {
            append("deviceId:${certificate.deviceId}")
            append("|serialNumber:${certificate.serialNumber}")
            append("|validFrom:${certificate.validFrom}")
            append("|validUntil:${certificate.validUntil}")
            append("|publicKey:${certificate.publicKey.contentHashCode()}")
        }.encodeToByteArray()
        
        val isSignatureValid = platformManager.verifyCertificateSignature(
            certData,
            certificate.signature,
            certificate.publicKey
        )
        
        return if (isSignatureValid) {
            CertificateValidationResult.Valid
        } else {
            CertificateValidationResult.Invalid("Invalid signature")
        }
    }
    
    override suspend fun storeCertificate(certificate: DeviceCertificate): Boolean {
        val validationResult = validateCertificate(certificate)
        if (validationResult !is CertificateValidationResult.Valid) {
            return false
        }
        
        return mutex.withLock {
            certificates[certificate.deviceId] = certificate
            platformManager.securelyStoreCertificate(certificate)
        }
    }
    
    override suspend fun getCertificate(deviceId: Uuid): DeviceCertificate? {
        return mutex.withLock {
            certificates[deviceId] ?: platformManager.securelyRetrieveCertificate(deviceId)
        }
    }
    
    override suspend fun revokeCertificate(deviceId: Uuid, reason: RevocationReason): Boolean {
        val certificate = getCertificate(deviceId) ?: return false
        
        val revocation = CertificateRevocation(
            serialNumber = certificate.serialNumber,
            deviceId = deviceId,
            revokedAt = Clock.System.now().toEpochMilliseconds(),
            reason = reason,
            revokedBy = nodeId
        )
        
        return mutex.withLock {
            revocations[certificate.serialNumber] = revocation
            certificates.remove(deviceId)
            platformManager.securelyDeleteCertificate(deviceId)
        }
    }
    
    override suspend fun renewCertificate(deviceId: Uuid): DeviceCertificate? {
        // Only allow renewal of local certificate
        if (deviceId != nodeId) return null
        
        val keyPair = mutex.withLock { localKeyPair } ?: return null
        
        val newCertificate = generateSelfSignedCertificate(
            deviceId = deviceId,
            keyPair = keyPair
        )
        
        return if (storeCertificate(newCertificate)) {
            mutex.withLock {
                localCertificate = newCertificate
            }
            newCertificate
        } else {
            null
        }
    }
    
    override suspend fun getExpiringCertificates(thresholdDays: Int): List<DeviceCertificate> {
        val thresholdMs = thresholdDays * 24L * 60 * 60 * 1000
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        return mutex.withLock {
            certificates.values.filter { cert ->
                (cert.validUntil - currentTime) <= thresholdMs && cert.isValid(currentTime)
            }
        }
    }
    
    override suspend fun cleanupExpiredCertificates(): Int {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        return mutex.withLock {
            val expiredDevices = certificates.filter { (_, cert) ->
                !cert.isValid(currentTime)
            }.keys
            
            expiredDevices.forEach { deviceId ->
                certificates.remove(deviceId)
                platformManager.securelyDeleteCertificate(deviceId)
            }
            
            expiredDevices.size
        }
    }
    
    suspend fun getAllCertificates(): List<DeviceCertificate> {
        return mutex.withLock {
            certificates.values.toList()
        }
    }
    
    suspend fun getCertificateRevocations(): List<CertificateRevocation> {
        return mutex.withLock {
            revocations.values.toList()
        }
    }
    
    suspend fun signData(data: ByteArray): ByteArray? {
        val keyPair = mutex.withLock { localKeyPair } ?: return null
        return platformManager.signCertificate(data, keyPair.privateKey)
    }
    
    suspend fun verifySignature(data: ByteArray, signature: ByteArray, deviceId: Uuid): Boolean {
        val certificate = getCertificate(deviceId) ?: return false
        return platformManager.verifyCertificateSignature(data, signature, certificate.publicKey)
    }
}