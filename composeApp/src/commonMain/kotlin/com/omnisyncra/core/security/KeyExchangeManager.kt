package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Enhanced key exchange manager supporting X25519 ECDH
 */
interface KeyExchangeManager {
    suspend fun initialize(): Boolean
    suspend fun initiateKeyExchange(targetDevice: Uuid): KeyExchangeRequest?
    suspend fun handleKeyExchangeRequest(request: KeyExchangeRequest): KeyExchangeResponse?
    suspend fun handleKeyExchangeResponse(response: KeyExchangeResponse): Boolean
    suspend fun getSessionKey(deviceId: Uuid): SessionKey?
    suspend fun hasActiveSession(deviceId: Uuid): Boolean
    suspend fun revokeSession(deviceId: Uuid): Boolean
    suspend fun renewSession(deviceId: Uuid): KeyExchangeRequest?
    suspend fun cleanupExpiredSessions(): Int
    suspend fun getAllActiveSessions(): List<SessionInfo>
}

/**
 * Enhanced key exchange request with X25519 support
 */
data class KeyExchangeRequest(
    val requestId: String = com.benasher44.uuid.uuid4().toString(),
    val fromDevice: Uuid,
    val toDevice: Uuid,
    val protocol: KeyExchangeProtocol,
    val publicKeyData: ByteArray,
    val ephemeralPublicKey: ByteArray, // X25519 ephemeral key
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val nonce: ByteArray,
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

/**
 * Enhanced key exchange response with X25519 support
 */
data class KeyExchangeResponse(
    val requestId: String,
    val fromDevice: Uuid,
    val toDevice: Uuid,
    val publicKeyData: ByteArray,
    val ephemeralPublicKey: ByteArray, // X25519 ephemeral key
    val encryptedSessionKey: ByteArray, // Encrypted with derived shared secret
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val nonce: ByteArray,
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

/**
 * Enhanced session key with forward secrecy
 */
data class SessionKey(
    val deviceId: Uuid,
    val sessionId: String,
    val sharedSecret: ByteArray,
    val encryptionKey: ByteArray,
    val macKey: ByteArray,
    val establishedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val expiresAt: Long = establishedAt + 86400_000L, // 24 hours
    val rotationThreshold: Long = establishedAt + 3600_000L // Rotate after 1 hour
) {
    fun isExpired(currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return currentTime > expiresAt
    }
    
    fun needsRotation(currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return currentTime > rotationThreshold
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SessionKey
        return sessionId == other.sessionId
    }

    override fun hashCode(): Int = sessionId.hashCode()
}

/**
 * Session information for monitoring
 */
data class SessionInfo(
    val deviceId: Uuid,
    val sessionId: String,
    val establishedAt: Long,
    val expiresAt: Long,
    val needsRotation: Boolean,
    val bytesTransferred: Long = 0L
)

/**
 * Key exchange protocols
 */
enum class KeyExchangeProtocol {
    X25519_ECDH,
    ECDH_P256
}

/**
 * Platform-specific X25519 key exchange operations
 */
expect class PlatformKeyExchange() {
    suspend fun generateX25519KeyPair(): KeyPair
    suspend fun deriveSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray
    suspend fun generateNonce(size: Int = 32): ByteArray
    suspend fun hkdfExpand(
        sharedSecret: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int = 64
    ): ByteArray
}

/**
 * Implementation of enhanced key exchange manager
 */
class OmnisyncraKeyExchangeManager(
    private val nodeId: Uuid,
    private val certificateManager: CertificateManager,
    private val platformKeyExchange: PlatformKeyExchange = PlatformKeyExchange()
) : KeyExchangeManager {
    
    private val sessionKeys = mutableMapOf<Uuid, SessionKey>()
    private val pendingRequests = mutableMapOf<String, KeyExchangeRequest>()
    private val ephemeralKeys = mutableMapOf<String, KeyPair>()
    private val mutex = Mutex()
    
    override suspend fun initialize(): Boolean {
        return try {
            // Cleanup any expired sessions on startup
            cleanupExpiredSessions()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun initiateKeyExchange(targetDevice: Uuid): KeyExchangeRequest? {
        return try {
            // Generate ephemeral X25519 key pair for this exchange
            val ephemeralKeyPair = platformKeyExchange.generateX25519KeyPair()
            val nonce = platformKeyExchange.generateNonce()
            
            val localCert = (certificateManager as? OmnisyncraeCertificateManager)?.getLocalCertificate()
                ?: return null
            
            val request = KeyExchangeRequest(
                fromDevice = nodeId,
                toDevice = targetDevice,
                protocol = KeyExchangeProtocol.X25519_ECDH,
                publicKeyData = localCert.publicKey,
                ephemeralPublicKey = ephemeralKeyPair.publicKey,
                nonce = nonce,
                signature = ByteArray(0) // Will be filled after signing
            )
            
            // Sign the request
            val dataToSign = buildRequestSignatureData(request)
            val signature = (certificateManager as? OmnisyncraeCertificateManager)?.signData(dataToSign)
                ?: return null
            
            val signedRequest = request.copy(signature = signature)
            
            mutex.withLock {
                pendingRequests[signedRequest.requestId] = signedRequest
                ephemeralKeys[signedRequest.requestId] = ephemeralKeyPair
            }
            
            signedRequest
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun handleKeyExchangeRequest(request: KeyExchangeRequest): KeyExchangeResponse? {
        return try {
            // Verify request signature
            val dataToSign = buildRequestSignatureData(request)
            val isValidSignature = (certificateManager as? OmnisyncraeCertificateManager)
                ?.verifySignature(dataToSign, request.signature, request.fromDevice) ?: false
            
            if (!isValidSignature) return null
            
            // Generate our ephemeral key pair
            val ourEphemeralKeyPair = platformKeyExchange.generateX25519KeyPair()
            val nonce = platformKeyExchange.generateNonce()
            
            // Derive shared secret using X25519
            val sharedSecret = platformKeyExchange.deriveSharedSecret(
                ourEphemeralKeyPair.privateKey,
                request.ephemeralPublicKey
            )
            
            // Derive session keys using HKDF
            val salt = request.nonce + nonce
            val info = "omnisyncra-session-${request.fromDevice}-$nodeId".encodeToByteArray()
            val keyMaterial = platformKeyExchange.hkdfExpand(sharedSecret, salt, info, 64)
            
            val encryptionKey = keyMaterial.sliceArray(0..31)
            val macKey = keyMaterial.sliceArray(32..63)
            
            // Create session key
            val sessionKey = SessionKey(
                deviceId = request.fromDevice,
                sessionId = com.benasher44.uuid.uuid4().toString(),
                sharedSecret = sharedSecret,
                encryptionKey = encryptionKey,
                macKey = macKey
            )
            
            // Store session key
            mutex.withLock {
                sessionKeys[request.fromDevice] = sessionKey
            }
            
            val localCert = (certificateManager as? OmnisyncraeCertificateManager)?.getLocalCertificate()
                ?: return null
            
            val response = KeyExchangeResponse(
                requestId = request.requestId,
                fromDevice = nodeId,
                toDevice = request.fromDevice,
                publicKeyData = localCert.publicKey,
                ephemeralPublicKey = ourEphemeralKeyPair.publicKey,
                encryptedSessionKey = encryptionKey, // In real implementation, encrypt this
                nonce = nonce,
                signature = ByteArray(0) // Will be filled after signing
            )
            
            // Sign the response
            val responseDataToSign = buildResponseSignatureData(response)
            val signature = (certificateManager as? OmnisyncraeCertificateManager)?.signData(responseDataToSign)
                ?: return null
            
            response.copy(signature = signature)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun handleKeyExchangeResponse(response: KeyExchangeResponse): Boolean {
        return try {
            // Verify response signature
            val dataToSign = buildResponseSignatureData(response)
            val isValidSignature = (certificateManager as? OmnisyncraeCertificateManager)
                ?.verifySignature(dataToSign, response.signature, response.fromDevice) ?: false
            
            if (!isValidSignature) return false
            
            // Get our ephemeral key pair
            val ephemeralKeyPair = mutex.withLock {
                ephemeralKeys.remove(response.requestId)
            } ?: return false
            
            // Derive shared secret
            val sharedSecret = platformKeyExchange.deriveSharedSecret(
                ephemeralKeyPair.privateKey,
                response.ephemeralPublicKey
            )
            
            // Get original request for nonce
            val originalRequest = mutex.withLock {
                pendingRequests.remove(response.requestId)
            } ?: return false
            
            // Derive session keys using HKDF
            val salt = originalRequest.nonce + response.nonce
            val info = "omnisyncra-session-$nodeId-${response.fromDevice}".encodeToByteArray()
            val keyMaterial = platformKeyExchange.hkdfExpand(sharedSecret, salt, info, 64)
            
            val encryptionKey = keyMaterial.sliceArray(0..31)
            val macKey = keyMaterial.sliceArray(32..63)
            
            // Create session key
            val sessionKey = SessionKey(
                deviceId = response.fromDevice,
                sessionId = com.benasher44.uuid.uuid4().toString(),
                sharedSecret = sharedSecret,
                encryptionKey = encryptionKey,
                macKey = macKey
            )
            
            // Store session key
            mutex.withLock {
                sessionKeys[response.fromDevice] = sessionKey
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getSessionKey(deviceId: Uuid): SessionKey? {
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
    
    override suspend fun hasActiveSession(deviceId: Uuid): Boolean {
        return getSessionKey(deviceId) != null
    }
    
    override suspend fun revokeSession(deviceId: Uuid): Boolean {
        return mutex.withLock {
            sessionKeys.remove(deviceId) != null
        }
    }
    
    override suspend fun renewSession(deviceId: Uuid): KeyExchangeRequest? {
        revokeSession(deviceId)
        return initiateKeyExchange(deviceId)
    }
    
    override suspend fun cleanupExpiredSessions(): Int {
        return mutex.withLock {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val expiredDevices = sessionKeys.filter { (_, key) ->
                key.isExpired(currentTime)
            }.keys
            
            expiredDevices.forEach { deviceId ->
                sessionKeys.remove(deviceId)
            }
            
            expiredDevices.size
        }
    }
    
    override suspend fun getAllActiveSessions(): List<SessionInfo> {
        return mutex.withLock {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            sessionKeys.values.map { key ->
                SessionInfo(
                    deviceId = key.deviceId,
                    sessionId = key.sessionId,
                    establishedAt = key.establishedAt,
                    expiresAt = key.expiresAt,
                    needsRotation = key.needsRotation(currentTime)
                )
            }
        }
    }
    
    private fun buildRequestSignatureData(request: KeyExchangeRequest): ByteArray {
        return buildString {
            append("request:")
            append(request.requestId)
            append("|from:")
            append(request.fromDevice)
            append("|to:")
            append(request.toDevice)
            append("|protocol:")
            append(request.protocol.name)
            append("|timestamp:")
            append(request.timestamp)
            append("|nonce:")
            append(request.nonce.contentHashCode())
            append("|ephemeral:")
            append(request.ephemeralPublicKey.contentHashCode())
        }.encodeToByteArray()
    }
    
    private fun buildResponseSignatureData(response: KeyExchangeResponse): ByteArray {
        return buildString {
            append("response:")
            append(response.requestId)
            append("|from:")
            append(response.fromDevice)
            append("|to:")
            append(response.toDevice)
            append("|timestamp:")
            append(response.timestamp)
            append("|nonce:")
            append(response.nonce.contentHashCode())
            append("|ephemeral:")
            append(response.ephemeralPublicKey.contentHashCode())
        }.encodeToByteArray()
    }
}