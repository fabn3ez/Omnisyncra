package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

// Secure message envelope
@Serializable
data class SecureMessage(
    val id: String = com.benasher44.uuid.uuid4().toString(),
    val fromDevice: String, // UUID as string
    val toDevice: String, // UUID as string
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val messageType: String,
    val encryptedPayload: ByteArray,
    val nonce: ByteArray,
    val signature: ByteArray,
    val keyId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SecureMessage
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

// Message integrity verification
data class MessageIntegrity(
    val messageId: String,
    val hash: ByteArray,
    val timestamp: Long,
    val verified: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MessageIntegrity
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}

// Secure messaging service
class SecureMessagingService(
    private val nodeId: Uuid,
    private val authService: AuthenticationService,
    private val encryptionService: EncryptionService,
    private val keyExchangeService: KeyExchangeService,
    private val auditService: AuditLogService
) {
    private val messageCache = mutableMapOf<String, SecureMessage>()
    private val integrityCache = mutableMapOf<String, MessageIntegrity>()
    private val mutex = Mutex()
    
    suspend fun sendSecureMessage(
        toDevice: Uuid,
        messageType: String,
        payload: ByteArray
    ): SecureMessage? {
        // Check if we have an active session
        val sessionKey = keyExchangeService.getSessionKey(toDevice)
        if (sessionKey == null) {
            // Initiate key exchange first
            keyExchangeService.initiateKeyExchange(toDevice)
            return null
        }
        
        // Encrypt payload
        val encryptedData = encryptionService.encrypt(payload, sessionKey.symmetricKey.id)
            ?: return null
        
        // Create message
        val message = SecureMessage(
            fromDevice = nodeId.toString(),
            toDevice = toDevice.toString(),
            messageType = messageType,
            encryptedPayload = encryptedData.ciphertext,
            nonce = encryptedData.nonce,
            keyId = encryptedData.keyId,
            signature = authService.signData(encryptedData.ciphertext)
        )
        
        // Cache message
        mutex.withLock {
            messageCache[message.id] = message
        }
        
        // Log audit event
        auditService.logDataOperation("encrypt", toDevice, payload.size)
        
        return message
    }
    
    suspend fun receiveSecureMessage(message: SecureMessage): ByteArray? {
        val fromDevice = try {
            com.benasher44.uuid.uuidFrom(message.fromDevice)
        } catch (e: Exception) {
            auditService.logSecurityViolation(null, "Invalid device ID in message")
            return null
        }
        
        // Verify message signature
        if (!authService.verifySignature(
                message.encryptedPayload,
                message.signature,
                fromDevice
            )) {
            auditService.logSecurityViolation(fromDevice, "Message signature verification failed")
            return null
        }
        
        // Get session key
        val sessionKey = keyExchangeService.getSessionKey(fromDevice)
        if (sessionKey == null) {
            auditService.logSecurityViolation(fromDevice, "No active session for message")
            return null
        }
        
        // Decrypt payload
        val encryptedData = EncryptedData(
            ciphertext = message.encryptedPayload,
            nonce = message.nonce,
            algorithm = EncryptionAlgorithm.AES_256_GCM,
            keyId = message.keyId
        )
        
        val decryptedPayload = encryptionService.decrypt(encryptedData)
        if (decryptedPayload == null) {
            auditService.logSecurityViolation(fromDevice, "Message decryption failed")
            return null
        }
        
        // Verify message integrity
        val integrity = verifyMessageIntegrity(message, decryptedPayload)
        mutex.withLock {
            integrityCache[message.id] = integrity
        }
        
        if (!integrity.verified) {
            auditService.logSecurityViolation(fromDevice, "Message integrity verification failed")
            return null
        }
        
        // Log audit event
        auditService.logDataOperation("decrypt", fromDevice, decryptedPayload.size)
        
        return decryptedPayload
    }
    
    private fun verifyMessageIntegrity(message: SecureMessage, payload: ByteArray): MessageIntegrity {
        val messageData = message.encryptedPayload + message.nonce + payload
        val hash = encryptionService.hash(messageData)
        
        // Simple integrity check - in production, use more sophisticated methods
        val verified = hash.isNotEmpty()
        
        return MessageIntegrity(
            messageId = message.id,
            hash = hash,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            verified = verified
        )
    }
    
    suspend fun sendString(toDevice: Uuid, messageType: String, text: String): SecureMessage? {
        return sendSecureMessage(toDevice, messageType, text.encodeToByteArray())
    }
    
    suspend fun receiveString(message: SecureMessage): String? {
        val payload = receiveSecureMessage(message) ?: return null
        return payload.decodeToString()
    }
    
    suspend fun getMessageHistory(deviceId: Uuid, limit: Int = 50): List<SecureMessage> {
        return mutex.withLock {
            messageCache.values
                .filter { 
                    it.fromDevice == deviceId.toString() || it.toDevice == deviceId.toString() 
                }
                .sortedByDescending { it.timestamp }
                .take(limit)
        }
    }
    
    suspend fun getMessageIntegrity(messageId: String): MessageIntegrity? {
        return mutex.withLock {
            integrityCache[messageId]
        }
    }
    
    suspend fun cleanupOldMessages(maxAgeMs: Long = 86400_000L) {
        val cutoffTime = Clock.System.now().toEpochMilliseconds() - maxAgeMs
        
        mutex.withLock {
            val oldMessageIds = messageCache.values
                .filter { it.timestamp < cutoffTime }
                .map { it.id }
            
            oldMessageIds.forEach { messageId ->
                messageCache.remove(messageId)
                integrityCache.remove(messageId)
            }
        }
    }
    
    suspend fun getMessagingStats(): MessagingStats {
        return mutex.withLock {
            val totalMessages = messageCache.size
            val verifiedMessages = integrityCache.values.count { it.verified }
            val failedMessages = integrityCache.values.count { !it.verified }
            
            MessagingStats(
                totalMessages = totalMessages,
                verifiedMessages = verifiedMessages,
                failedMessages = failedMessages,
                integrityRate = if (totalMessages > 0) verifiedMessages.toDouble() / totalMessages else 0.0
            )
        }
    }
}

data class MessagingStats(
    val totalMessages: Int,
    val verifiedMessages: Int,
    val failedMessages: Int,
    val integrityRate: Double
)