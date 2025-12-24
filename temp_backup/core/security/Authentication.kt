package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

// Device certificate for authentication
data class DeviceCertificate(
    val deviceId: Uuid,
    val publicKey: ByteArray,
    val issuer: String,
    val subject: String,
    val validFrom: Long,
    val validUntil: Long,
    val signature: ByteArray,
    val serialNumber: String = com.benasher44.uuid.uuid4().toString()
) {
    fun isValid(currentTime: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()): Boolean {
        return currentTime in validFrom..validUntil
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

// Authentication token
data class AuthToken(
    val token: String,
    val deviceId: Uuid,
    val issuedAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    val expiresAt: Long = issuedAt + 3600_000L, // 1 hour default
    val scope: Set<String> = emptySet()
) {
    fun isExpired(currentTime: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()): Boolean {
        return currentTime > expiresAt
    }
    
    fun hasScope(requiredScope: String): Boolean {
        return scope.contains(requiredScope) || scope.contains("*")
    }
}

// Trust level for devices
enum class TrustLevel {
    UNTRUSTED,
    PENDING,
    TRUSTED,
    VERIFIED
}

// Device identity with trust information
data class DeviceIdentity(
    val deviceId: Uuid,
    val certificate: DeviceCertificate,
    val trustLevel: TrustLevel = TrustLevel.PENDING,
    val trustedAt: Long? = null,
    val trustedBy: Uuid? = null,
    val metadata: Map<String, String> = emptyMap()
)

// Platform-specific authentication
expect class PlatformAuthenticator() {
    fun generateKeyPair(): KeyPair
    fun sign(data: ByteArray, privateKey: ByteArray): ByteArray
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}

data class KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as KeyPair
        return publicKey.contentEquals(other.publicKey) && 
               privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

// Authentication service
class AuthenticationService(
    private val nodeId: Uuid,
    private val authenticator: PlatformAuthenticator = PlatformAuthenticator()
) {
    private val identities = mutableMapOf<Uuid, DeviceIdentity>()
    private val tokens = mutableMapOf<String, AuthToken>()
    private val mutex = Mutex()
    
    private var localKeyPair: KeyPair? = null
    private var localCertificate: DeviceCertificate? = null
    
    suspend fun initialize() {
        mutex.withLock {
            if (localKeyPair == null) {
                localKeyPair = authenticator.generateKeyPair()
                localCertificate = createSelfSignedCertificate()
            }
        }
    }
    
    private fun createSelfSignedCertificate(): DeviceCertificate {
        val keyPair = localKeyPair ?: throw IllegalStateException("Key pair not initialized")
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val validityPeriod = 365L * 24 * 60 * 60 * 1000 // 1 year
        
        val certData = "$nodeId:$currentTime".encodeToByteArray()
        val signature = authenticator.sign(certData, keyPair.privateKey)
        
        return DeviceCertificate(
            deviceId = nodeId,
            publicKey = keyPair.publicKey,
            issuer = "self",
            subject = nodeId.toString(),
            validFrom = currentTime,
            validUntil = currentTime + validityPeriod,
            signature = signature
        )
    }
    
    suspend fun getLocalCertificate(): DeviceCertificate? {
        return mutex.withLock {
            localCertificate
        }
    }
    
    suspend fun authenticateDevice(certificate: DeviceCertificate): Boolean {
        if (!certificate.isValid()) return false
        
        // Verify certificate signature
        val certData = "${certificate.deviceId}:${certificate.validFrom}".encodeToByteArray()
        val isValid = authenticator.verify(certData, certificate.signature, certificate.publicKey)
        
        if (isValid) {
            mutex.withLock {
                val identity = DeviceIdentity(
                    deviceId = certificate.deviceId,
                    certificate = certificate,
                    trustLevel = TrustLevel.PENDING
                )
                identities[certificate.deviceId] = identity
            }
        }
        
        return isValid
    }
    
    suspend fun trustDevice(deviceId: Uuid, trustLevel: TrustLevel = TrustLevel.TRUSTED) {
        mutex.withLock {
            val identity = identities[deviceId] ?: return@withLock
            identities[deviceId] = identity.copy(
                trustLevel = trustLevel,
                trustedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                trustedBy = nodeId
            )
        }
    }
    
    suspend fun getTrustLevel(deviceId: Uuid): TrustLevel {
        return mutex.withLock {
            identities[deviceId]?.trustLevel ?: TrustLevel.UNTRUSTED
        }
    }
    
    suspend fun issueToken(
        deviceId: Uuid,
        scope: Set<String> = setOf("read", "write"),
        validityMs: Long = 3600_000L
    ): AuthToken? {
        val identity = mutex.withLock { identities[deviceId] } ?: return null
        
        if (identity.trustLevel == TrustLevel.UNTRUSTED) return null
        
        val token = AuthToken(
            token = com.benasher44.uuid.uuid4().toString(),
            deviceId = deviceId,
            expiresAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + validityMs,
            scope = scope
        )
        
        mutex.withLock {
            tokens[token.token] = token
        }
        
        return token
    }
    
    suspend fun validateToken(tokenString: String): AuthToken? {
        return mutex.withLock {
            val token = tokens[tokenString] ?: return@withLock null
            if (token.isExpired()) {
                tokens.remove(tokenString)
                return@withLock null
            }
            token
        }
    }
    
    suspend fun revokeToken(tokenString: String) {
        mutex.withLock {
            tokens.remove(tokenString)
        }
    }
    
    suspend fun revokeAllTokensForDevice(deviceId: Uuid) {
        mutex.withLock {
            tokens.entries.removeAll { it.value.deviceId == deviceId }
        }
    }
    
    suspend fun getDeviceIdentity(deviceId: Uuid): DeviceIdentity? {
        return mutex.withLock {
            identities[deviceId]
        }
    }
    
    suspend fun getAllTrustedDevices(): List<DeviceIdentity> {
        return mutex.withLock {
            identities.values.filter { 
                it.trustLevel == TrustLevel.TRUSTED || it.trustLevel == TrustLevel.VERIFIED 
            }
        }
    }
    
    suspend fun removeDevice(deviceId: Uuid) {
        mutex.withLock {
            identities.remove(deviceId)
            tokens.entries.removeAll { it.value.deviceId == deviceId }
        }
    }
    
    fun signData(data: ByteArray): ByteArray {
        val keyPair = localKeyPair ?: throw IllegalStateException("Key pair not initialized")
        return authenticator.sign(data, keyPair.privateKey)
    }
    
    fun verifySignature(data: ByteArray, signature: ByteArray, deviceId: Uuid): Boolean {
        val identity = identities[deviceId] ?: return false
        return authenticator.verify(data, signature, identity.certificate.publicKey)
    }
}
