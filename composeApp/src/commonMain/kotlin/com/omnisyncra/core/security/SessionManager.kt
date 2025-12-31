package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Secure session state
 */
enum class SessionState {
    INITIALIZING,
    ESTABLISHING,
    ACTIVE,
    ROTATING_KEYS,
    TERMINATING,
    TERMINATED,
    ERROR
}

/**
 * Session configuration
 */
data class SessionConfig(
    val keyRotationInterval: Long = 3600_000L, // 1 hour
    val sessionTimeout: Long = 86400_000L, // 24 hours
    val maxConcurrentSessions: Int = 10,
    val enableForwardSecrecy: Boolean = true,
    val compressionEnabled: Boolean = true,
    val encryptionAlgorithm: String = "AES-256-GCM"
)

/**
 * Secure session for encrypted communication
 */
data class SecureSession(
    val sessionId: Uuid,
    val localDeviceId: Uuid,
    val remoteDeviceId: Uuid,
    val state: SessionState,
    val sessionKey: ByteArray,
    val createdAt: Long,
    val lastActivity: Long,
    val keyRotationCount: Int = 0,
    val config: SessionConfig,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SecureSession
        return sessionId == other.sessionId &&
                localDeviceId == other.localDeviceId &&
                remoteDeviceId == other.remoteDeviceId &&
                state == other.state &&
                sessionKey.contentEquals(other.sessionKey) &&
                createdAt == other.createdAt &&
                lastActivity == other.lastActivity &&
                keyRotationCount == other.keyRotationCount
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + localDeviceId.hashCode()
        result = 31 * result + remoteDeviceId.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + sessionKey.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastActivity.hashCode()
        result = 31 * result + keyRotationCount.hashCode()
        return result
    }
    
    fun isExpired(currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return (currentTime - lastActivity) > config.sessionTimeout
    }
    
    fun needsKeyRotation(currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return config.enableForwardSecrecy && 
               (currentTime - createdAt) > (config.keyRotationInterval * (keyRotationCount + 1))
    }
}

/**
 * Encrypted message for secure communication
 */
data class EncryptedMessage(
    val sessionId: Uuid,
    val messageId: Uuid,
    val encryptedData: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray,
    val timestamp: Long,
    val messageType: String = "data",
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as EncryptedMessage
        return sessionId == other.sessionId &&
                messageId == other.messageId &&
                encryptedData.contentEquals(other.encryptedData) &&
                nonce.contentEquals(other.nonce) &&
                authTag.contentEquals(other.authTag) &&
                timestamp == other.timestamp &&
                messageType == other.messageType
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + encryptedData.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + messageType.hashCode()
        return result
    }
}

/**
 * Session management interface
 */
interface SessionManager {
    suspend fun createSession(
        remoteDeviceId: Uuid,
        config: SessionConfig = SessionConfig()
    ): Result<SecureSession>
    
    suspend fun acceptSession(
        sessionId: Uuid,
        remoteDeviceId: Uuid,
        sessionKey: ByteArray,
        config: SessionConfig = SessionConfig()
    ): Result<SecureSession>
    
    suspend fun getSession(sessionId: Uuid): SecureSession?
    suspend fun getActiveSessions(): List<SecureSession>
    suspend fun terminateSession(sessionId: Uuid): Boolean
    suspend fun terminateAllSessions(): Int
    
    suspend fun encryptMessage(sessionId: Uuid, data: ByteArray, messageType: String = "data"): Result<EncryptedMessage>
    suspend fun decryptMessage(message: EncryptedMessage): Result<ByteArray>
    
    suspend fun rotateSessionKey(sessionId: Uuid): Result<SecureSession>
    suspend fun updateSessionActivity(sessionId: Uuid): Boolean
    suspend fun cleanupExpiredSessions(): Int
}

/**
 * Implementation of secure session manager
 */
class OmnisyncraSessionManager(
    private val deviceId: Uuid,
    private val keyExchangeManager: KeyExchangeManager,
    private val trustManager: TrustManager,
    private val securityLogger: SecurityEventLogger
) : SessionManager {
    
    private val _activeSessions = MutableStateFlow<Map<Uuid, SecureSession>>(emptyMap())
    val activeSessions: StateFlow<Map<Uuid, SecureSession>> = _activeSessions.asStateFlow()
    
    private val sessions = mutableMapOf<Uuid, SecureSession>()
    
    override suspend fun createSession(
        remoteDeviceId: Uuid,
        config: SessionConfig
    ): Result<SecureSession> {
        return try {
            // Check if device is trusted
            if (!trustManager.isTrusted(remoteDeviceId)) {
                securityLogger.logEvent(
                    type = SecurityEventType.SESSION_TERMINATED,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Session creation rejected - device not trusted",
                    relatedDeviceId = remoteDeviceId
                )
                return Result.failure(SecurityException("Device $remoteDeviceId is not trusted"))
            }
            
            // Check session limits
            if (sessions.size >= config.maxConcurrentSessions) {
                return Result.failure(IllegalStateException("Maximum concurrent sessions reached"))
            }
            
            val sessionId = com.benasher44.uuid.uuid4()
            val currentTime = Clock.System.now().toEpochMilliseconds()
            
            // Generate session key using key exchange
            val keyExchangeRequest = keyExchangeManager.initiateKeyExchange(remoteDeviceId)
            if (keyExchangeRequest == null) {
                securityLogger.logEvent(
                    SecurityEvent(
                        id = com.benasher44.uuid.uuid4().toString(),
                        type = SecurityEventType.KEY_EXCHANGE_FAILED,
                        severity = SecurityEventSeverity.ERROR,
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        deviceId = nodeId.toString(),
                        message = "Failed to initiate key exchange",
                        details = mapOf("remote_device" to remoteDeviceId.toString())
                    )
                )
                return null
            }
            
            // For now, generate a temporary session key
            // In a real implementation, this would complete the key exchange protocol
            val sessionKey = generateTemporarySessionKey()
            
            val session = SecureSession(
                sessionId = sessionId,
                localDeviceId = deviceId,
                remoteDeviceId = remoteDeviceId,
                state = SessionState.ESTABLISHING,
                sessionKey = sessionKey,
                createdAt = currentTime,
                lastActivity = currentTime,
                config = config,
                metadata = mapOf(
                    "created_by" to "local",
                    "protocol_version" to "1.0"
                )
            )
            
            sessions[sessionId] = session
            updateActiveSessionsFlow()
            
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_ESTABLISHED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure session created",
                relatedDeviceId = remoteDeviceId,
                sessionId = sessionId
            )
            
            Result.success(session.copy(state = SessionState.ACTIVE))
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to create session",
                relatedDeviceId = remoteDeviceId,
                error = e
            )
            Result.failure(e)
        }
    }
    
    override suspend fun acceptSession(
        sessionId: Uuid,
        remoteDeviceId: Uuid,
        sessionKey: ByteArray,
        config: SessionConfig
    ): Result<SecureSession> {
        return try {
            // Check if device is trusted
            if (!trustManager.isTrusted(remoteDeviceId)) {
                return Result.failure(SecurityException("Device $remoteDeviceId is not trusted"))
            }
            
            val currentTime = Clock.System.now().toEpochMilliseconds()
            
            val session = SecureSession(
                sessionId = sessionId,
                localDeviceId = deviceId,
                remoteDeviceId = remoteDeviceId,
                state = SessionState.ACTIVE,
                sessionKey = sessionKey,
                createdAt = currentTime,
                lastActivity = currentTime,
                config = config,
                metadata = mapOf(
                    "created_by" to "remote",
                    "protocol_version" to "1.0"
                )
            )
            
            sessions[sessionId] = session
            updateActiveSessionsFlow()
            
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_ESTABLISHED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure session accepted",
                relatedDeviceId = remoteDeviceId,
                sessionId = sessionId
            )
            
            Result.success(session)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to accept session",
                relatedDeviceId = remoteDeviceId,
                sessionId = sessionId,
                error = e
            )
            Result.failure(e)
        }
    }
    
    override suspend fun getSession(sessionId: Uuid): SecureSession? {
        return sessions[sessionId]
    }
    
    override suspend fun getActiveSessions(): List<SecureSession> {
        return sessions.values.filter { it.state == SessionState.ACTIVE }
    }
    
    override suspend fun terminateSession(sessionId: Uuid): Boolean {
        return try {
            val session = sessions[sessionId] ?: return false
            
            val terminatedSession = session.copy(
                state = SessionState.TERMINATED,
                lastActivity = Clock.System.now().toEpochMilliseconds()
            )
            
            sessions[sessionId] = terminatedSession
            updateActiveSessionsFlow()
            
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.INFO,
                message = "Session terminated",
                relatedDeviceId = session.remoteDeviceId,
                sessionId = sessionId
            )
            
            // Remove from memory after a delay (for cleanup)
            sessions.remove(sessionId)
            updateActiveSessionsFlow()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun terminateAllSessions(): Int {
        val sessionCount = sessions.size
        val sessionIds = sessions.keys.toList()
        
        for (sessionId in sessionIds) {
            terminateSession(sessionId)
        }
        
        return sessionCount
    }
    
    override suspend fun encryptMessage(
        sessionId: Uuid,
        data: ByteArray,
        messageType: String
    ): Result<EncryptedMessage> {
        return try {
            val session = sessions[sessionId] 
                ?: return Result.failure(IllegalArgumentException("Session not found"))
            
            if (session.state != SessionState.ACTIVE) {
                return Result.failure(IllegalStateException("Session is not active"))
            }
            
            // Check if key rotation is needed
            if (session.needsKeyRotation()) {
                rotateSessionKey(sessionId)
            }
            
            val messageId = com.benasher44.uuid.uuid4()
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val nonce = generateNonce()
            
            // Encrypt using AES-256-GCM (simplified implementation)
            val encryptedData = encryptWithAESGCM(data, session.sessionKey, nonce)
            val authTag = generateAuthTag(encryptedData, session.sessionKey)
            
            val encryptedMessage = EncryptedMessage(
                sessionId = sessionId,
                messageId = messageId,
                encryptedData = encryptedData,
                nonce = nonce,
                authTag = authTag,
                timestamp = currentTime,
                messageType = messageType
            )
            
            // Update session activity
            updateSessionActivity(sessionId)
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Message encrypted",
                sessionId = sessionId,
                details = mapOf(
                    "message_type" to messageType,
                    "data_size" to data.size.toString()
                )
            )
            
            Result.success(encryptedMessage)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Message encryption failed",
                sessionId = sessionId,
                error = e
            )
            Result.failure(e)
        }
    }
    
    override suspend fun decryptMessage(message: EncryptedMessage): Result<ByteArray> {
        return try {
            val session = sessions[message.sessionId] 
                ?: return Result.failure(IllegalArgumentException("Session not found"))
            
            if (session.state != SessionState.ACTIVE) {
                return Result.failure(IllegalStateException("Session is not active"))
            }
            
            // Verify auth tag
            val expectedAuthTag = generateAuthTag(message.encryptedData, session.sessionKey)
            if (!message.authTag.contentEquals(expectedAuthTag)) {
                securityLogger.logEvent(
                    type = SecurityEventType.SECURITY_VIOLATION,
                    severity = SecurityEventSeverity.ERROR,
                    message = "Message authentication failed",
                    sessionId = message.sessionId
                )
                return Result.failure(SecurityException("Message authentication failed"))
            }
            
            // Decrypt using AES-256-GCM (simplified implementation)
            val decryptedData = decryptWithAESGCM(message.encryptedData, session.sessionKey, message.nonce)
            
            // Update session activity
            updateSessionActivity(message.sessionId)
            
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Message decrypted",
                sessionId = message.sessionId,
                details = mapOf(
                    "message_type" to message.messageType,
                    "data_size" to decryptedData.size.toString()
                )
            )
            
            Result.success(decryptedData)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Message decryption failed",
                sessionId = message.sessionId,
                error = e
            )
            Result.failure(e)
        }
    }
    
    override suspend fun rotateSessionKey(sessionId: Uuid): Result<SecureSession> {
        return try {
            val session = sessions[sessionId] 
                ?: return Result.failure(IllegalArgumentException("Session not found"))
            
            val updatedSession = session.copy(
                state = SessionState.ROTATING_KEYS
            )
            sessions[sessionId] = updatedSession
            
            // Generate new session key
            val keyExchangeRequest = keyExchangeManager.initiateKeyExchange(session.remoteDeviceId)
            if (keyExchangeRequest == null) {
                securityLogger.logEvent(
                    SecurityEvent(
                        id = com.benasher44.uuid.uuid4().toString(),
                        type = SecurityEventType.KEY_EXCHANGE_FAILED,
                        severity = SecurityEventSeverity.ERROR,
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        deviceId = nodeId.toString(),
                        message = "Failed to initiate key exchange for rotation",
                        details = mapOf("remote_device" to session.remoteDeviceId.toString())
                    )
                )
                return null
            }
            
            // For now, generate a temporary session key
            val newSessionKey = generateTemporarySessionKey()
            
            val rotatedSession = updatedSession.copy(
                state = SessionState.ACTIVE,
                sessionKey = newSessionKey,
                keyRotationCount = session.keyRotationCount + 1,
                lastActivity = Clock.System.now().toEpochMilliseconds()
            )
            
            sessions[sessionId] = rotatedSession
            updateActiveSessionsFlow()
            
            securityLogger.logEvent(
                type = SecurityEventType.KEY_EXCHANGE_COMPLETED,
                severity = SecurityEventSeverity.INFO,
                message = "Session key rotated",
                sessionId = sessionId,
                details = mapOf(
                    "rotation_count" to rotatedSession.keyRotationCount.toString()
                )
            )
            
            Result.success(rotatedSession)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.KEY_EXCHANGE_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Session key rotation failed",
                sessionId = sessionId,
                error = e
            )
            Result.failure(e)
        }
    }
    
    override suspend fun updateSessionActivity(sessionId: Uuid): Boolean {
        return try {
            val session = sessions[sessionId] ?: return false
            val updatedSession = session.copy(
                lastActivity = Clock.System.now().toEpochMilliseconds()
            )
            sessions[sessionId] = updatedSession
            updateActiveSessionsFlow()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun cleanupExpiredSessions(): Int {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val expiredSessions = sessions.values.filter { it.isExpired(currentTime) }
        
        for (session in expiredSessions) {
            terminateSession(session.sessionId)
        }
        
        return expiredSessions.size
    }
    
    private fun updateActiveSessionsFlow() {
        _activeSessions.value = sessions.toMap()
    }
    
    private fun generateNonce(): ByteArray {
        return ByteArray(12) { Random.nextInt(256).toByte() }
    }
    
    private fun encryptWithAESGCM(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        // Simplified AES-GCM encryption (not cryptographically secure)
        // In production, use proper AES-GCM implementation
        val encrypted = ByteArray(data.size)
        for (i in data.indices) {
            encrypted[i] = (data[i].toInt() xor key[i % key.size].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        return encrypted
    }
    
    private fun decryptWithAESGCM(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        // Simplified AES-GCM decryption (not cryptographically secure)
        // In production, use proper AES-GCM implementation
        val decrypted = ByteArray(encryptedData.size)
        for (i in encryptedData.indices) {
            decrypted[i] = (encryptedData[i].toInt() xor key[i % key.size].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        return decrypted
    }
    
    private fun generateAuthTag(data: ByteArray, key: ByteArray): ByteArray {
        // Simplified authentication tag generation (not cryptographically secure)
        // In production, use proper HMAC or GCM authentication
        val tag = ByteArray(16)
        var hash = 0x811c9dc5.toInt()
        
        for (byte in data + key) {
            hash = hash xor byte.toInt()
            hash = hash * 0x01000193
        }
        
        for (i in tag.indices) {
            tag[i] = ((hash shr (i % 32)) and 0xFF).toByte()
            hash = hash * 0x01000193
        }
        
        return tag
    }
}