package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

// Key exchange protocol
enum class KeyExchangeProtocol {
    ECDH,
    RSA
}

// Key exchange request
data class KeyExchangeRequest(
    val requestId: String = com.benasher44.uuid.uuid4().toString(),
    val fromDevice: Uuid,
    val toDevice: Uuid,
    val protocol: KeyExchangeProtocol,
    val publicKeyData: ByteArray,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as KeyExchangeRequest
        return requestId == other.requestId
    }

    override fun hashCode(): Int = requestId.hashCode()
}

// Key exchange response
data class KeyExchangeResponse(
    val requestId: String,
    val fromDevice: Uuid,
    val toDevice: Uuid,
    val publicKeyData: ByteArray,
    val encryptedSharedSecret: ByteArray,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as KeyExchangeResponse
        return requestId == other.requestId
    }

    override fun hashCode(): Int = requestId.hashCode()
}

// Shared session key
data class SessionKey(
    val deviceId: Uuid,
    val sharedSecret: ByteArray,
    val symmetricKey: SymmetricKey,
    val establishedAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    val expiresAt: Long = establishedAt + 86400_000L // 24 hours
) {
    fun isExpired(currentTime: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()): Boolean {
        return currentTime > expiresAt
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SessionKey
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()
}

// Platform-specific key exchange
expect class PlatformKeyExchange() {
    fun generateECDHKeyPair(): KeyPair
    fun deriveSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray
    fun encryptWithPublicKey(data: ByteArray, publicKey: ByteArray): ByteArray
    fun decryptWithPrivateKey(data: ByteArray, privateKey: ByteArray): ByteArray
}

// Key exchange service
class KeyExchangeService(
    private val nodeId: Uuid,
    private val authService: AuthenticationService,
    private val encryptionService: EncryptionService,
    private val keyExchange: PlatformKeyExchange = PlatformKeyExchange()
) {
    private val sessionKeys = mutableMapOf<Uuid, SessionKey>()
    private val pendingRequests = mutableMapOf<String, KeyExchangeRequest>()
    private val mutex = Mutex()
    
    private var localECDHKeyPair: KeyPair? = null
    
    suspend fun initialize() {
        mutex.withLock {
            if (localECDHKeyPair == null) {
                localECDHKeyPair = keyExchange.generateECDHKeyPair()
            }
        }
    }
    
    suspend fun initiateKeyExchange(
        targetDevice: Uuid,
        protocol: KeyExchangeProtocol = KeyExchangeProtocol.ECDH
    ): KeyExchangeRequest? {
        // Check if device is authenticated
        val trustLevel = authService.getTrustLevel(targetDevice)
        if (trustLevel == TrustLevel.UNTRUSTED) return null
        
        val keyPair = mutex.withLock { localECDHKeyPair } ?: return null
        
        val request = KeyExchangeRequest(
            fromDevice = nodeId,
            toDevice = targetDevice,
            protocol = protocol,
            publicKeyData = keyPair.publicKey,
            signature = authService.signData(keyPair.publicKey)
        )
        
        mutex.withLock {
            pendingRequests[request.requestId] = request
        }
        
        return request
    }
    
    suspend fun handleKeyExchangeRequest(request: KeyExchangeRequest): KeyExchangeResponse? {
        // Verify request signature
        if (!authService.verifySignature(
                request.publicKeyData,
                request.signature,
                request.fromDevice
            )) {
            return null
        }
        
        // Check trust level
        val trustLevel = authService.getTrustLevel(request.fromDevice)
        if (trustLevel == TrustLevel.UNTRUSTED) return null
        
        val keyPair = mutex.withLock { localECDHKeyPair } ?: return null
        
        // Derive shared secret
        val sharedSecret = keyExchange.deriveSharedSecret(
            keyPair.privateKey,
            request.publicKeyData
        )
        
        // Create symmetric key from shared secret
        val symmetricKey = encryptionService.generateKey(
            keyId = "session_${request.fromDevice}",
            algorithm = EncryptionAlgorithm.AES_256_GCM
        )
        
        // Store session key
        val sessionKey = SessionKey(
            deviceId = request.fromDevice,
            sharedSecret = sharedSecret,
            symmetricKey = symmetricKey
        )
        
        mutex.withLock {
            sessionKeys[request.fromDevice] = sessionKey
        }
        
        // Encrypt shared secret with requester's public key
        val encryptedSecret = keyExchange.encryptWithPublicKey(
            sharedSecret,
            request.publicKeyData
        )
        
        val response = KeyExchangeResponse(
            requestId = request.requestId,
            fromDevice = nodeId,
            toDevice = request.fromDevice,
            publicKeyData = keyPair.publicKey,
            encryptedSharedSecret = encryptedSecret,
            signature = authService.signData(encryptedSecret)
        )
        
        return response
    }
    
    suspend fun handleKeyExchangeResponse(response: KeyExchangeResponse): Boolean {
        // Verify response signature
        if (!authService.verifySignature(
                response.encryptedSharedSecret,
                response.signature,
                response.fromDevice
            )) {
            return false
        }
        
        // Get pending request
        val request = mutex.withLock {
            pendingRequests.remove(response.requestId)
        } ?: return false
        
        val keyPair = mutex.withLock { localECDHKeyPair } ?: return false
        
        // Decrypt shared secret
        val sharedSecret = try {
            keyExchange.decryptWithPrivateKey(
                response.encryptedSharedSecret,
                keyPair.privateKey
            )
        } catch (e: Exception) {
            return false
        }
        
        // Create symmetric key
        val symmetricKey = encryptionService.generateKey(
            keyId = "session_${response.fromDevice}",
            algorithm = EncryptionAlgorithm.AES_256_GCM
        )
        
        // Store session key
        val sessionKey = SessionKey(
            deviceId = response.fromDevice,
            sharedSecret = sharedSecret,
            symmetricKey = symmetricKey
        )
        
        mutex.withLock {
            sessionKeys[response.fromDevice] = sessionKey
        }
        
        return true
    }
    
    suspend fun getSessionKey(deviceId: Uuid): SessionKey? {
        return mutex.withLock {
            val key = sessionKeys[deviceId]
            if (key?.isExpired() == true) {
                sessionKeys.remove(deviceId)
                null
            } else {
                key
            }
        }
    }
    
    suspend fun hasActiveSession(deviceId: Uuid): Boolean {
        return getSessionKey(deviceId) != null
    }
    
    suspend fun revokeSession(deviceId: Uuid) {
        mutex.withLock {
            val sessionKey = sessionKeys.remove(deviceId)
            sessionKey?.let {
                encryptionService.removeKey(it.symmetricKey.id)
            }
        }
    }
    
    suspend fun renewSession(deviceId: Uuid): KeyExchangeRequest? {
        revokeSession(deviceId)
        return initiateKeyExchange(deviceId)
    }
    
    suspend fun cleanupExpiredSessions() {
        mutex.withLock {
            val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val expiredDevices = sessionKeys.filter { (_, key) ->
                key.isExpired(currentTime)
            }.keys
            
            expiredDevices.forEach { deviceId ->
                val sessionKey = sessionKeys.remove(deviceId)
                sessionKey?.let {
                    encryptionService.removeKey(it.symmetricKey.id)
                }
            }
        }
    }
    
    suspend fun getAllActiveSessions(): List<Uuid> {
        return mutex.withLock {
            sessionKeys.keys.toList()
        }
    }
}
