package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.SecurityContext
import com.omnisyncra.core.domain.TrustLevel
import com.omnisyncra.core.storage.LocalStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.omnisyncra.core.platform.TimeUtils

/**
 * Security services for encryption, authentication, and trust management
 */

interface EncryptionService {
    suspend fun encrypt(data: String, deviceId: Uuid): String
    suspend fun decrypt(encryptedData: String, deviceId: Uuid): String?
}

class MockEncryptionService : EncryptionService {
    override suspend fun encrypt(data: String, deviceId: Uuid): String {
        // Mock encryption - just base64 encode for demo
        return "encrypted_$data"
    }
    
    override suspend fun decrypt(encryptedData: String, deviceId: Uuid): String? {
        // Mock decryption
        return if (encryptedData.startsWith("encrypted_")) {
            encryptedData.removePrefix("encrypted_")
        } else null
    }
}

interface AuthenticationService {
    suspend fun authenticateDevice(deviceId: Uuid): Boolean
    suspend fun generateDeviceToken(deviceId: Uuid): String?
    suspend fun validateToken(token: String): Uuid?
}

class MockAuthenticationService(private val storage: LocalStorage) : AuthenticationService {
    override suspend fun authenticateDevice(deviceId: Uuid): Boolean {
        // Mock authentication - always succeed for demo
        return true
    }
    
    override suspend fun generateDeviceToken(deviceId: Uuid): String? {
        val token = "token_${deviceId}_${TimeUtils.currentTimeMillis()}"
        storage.store("device_token_$deviceId", token)
        return token
    }
    
    override suspend fun validateToken(token: String): Uuid? {
        // Mock validation
        return if (token.startsWith("token_")) {
            try {
                val parts = token.split("_")
                if (parts.size >= 2) com.benasher44.uuid.uuidFrom(parts[1]) else null
            } catch (e: Exception) {
                null
            }
        } else null
    }
}

interface KeyExchangeService {
    suspend fun initiateKeyExchange(peerId: Uuid): Boolean
    suspend fun completeKeyExchange(peerId: Uuid, publicKey: String): Boolean
}

class MockKeyExchangeService(
    private val encryptionService: EncryptionService,
    private val authService: AuthenticationService,
    private val storage: LocalStorage
) : KeyExchangeService {
    
    override suspend fun initiateKeyExchange(peerId: Uuid): Boolean {
        // Mock key exchange initiation
        storage.store("key_exchange_$peerId", "initiated")
        return true
    }
    
    override suspend fun completeKeyExchange(peerId: Uuid, publicKey: String): Boolean {
        // Mock key exchange completion
        storage.store("public_key_$peerId", publicKey)
        return true
    }
}

interface PermissionManager {
    suspend fun grantPermission(deviceId: Uuid, permission: String): Boolean
    suspend fun revokePermission(deviceId: Uuid, permission: String): Boolean
    suspend fun hasPermission(deviceId: Uuid, permission: String): Boolean
}

class MockPermissionManager(
    private val authService: AuthenticationService,
    private val storage: LocalStorage,
    private val encryptionService: EncryptionService
) : PermissionManager {
    
    override suspend fun grantPermission(deviceId: Uuid, permission: String): Boolean {
        storage.store("permission_${deviceId}_$permission", "granted")
        return true
    }
    
    override suspend fun revokePermission(deviceId: Uuid, permission: String): Boolean {
        storage.delete("permission_${deviceId}_$permission")
        return true
    }
    
    override suspend fun hasPermission(deviceId: Uuid, permission: String): Boolean {
        return storage.retrieve("permission_${deviceId}_$permission") == "granted"
    }
}

interface SecureMessagingService {
    suspend fun sendSecureMessage(peerId: Uuid, message: String): Boolean
    val incomingMessages: Flow<List<SecureMessage>>
}

@kotlinx.serialization.Serializable
data class SecureMessage(
    val fromId: String,
    val toId: String,
    val content: String,
    val timestamp: Long,
    val isEncrypted: Boolean
)

class MockSecureMessagingService(
    private val encryptionService: EncryptionService,
    private val authService: AuthenticationService,
    private val keyExchange: KeyExchangeService,
    private val permissions: PermissionManager,
    private val storage: LocalStorage
) : SecureMessagingService {
    
    private val _incomingMessages = MutableStateFlow<List<SecureMessage>>(emptyList())
    override val incomingMessages = _incomingMessages.asStateFlow()
    
    override suspend fun sendSecureMessage(peerId: Uuid, message: String): Boolean {
        val encryptedMessage = encryptionService.encrypt(message, peerId)
        // Mock sending - just add to incoming for demo
        val secureMessage = SecureMessage(
            fromId = "self",
            toId = peerId.toString(),
            content = encryptedMessage,
            timestamp = TimeUtils.currentTimeMillis(),
            isEncrypted = true
        )
        
        val current = _incomingMessages.value.toMutableList()
        current.add(secureMessage)
        _incomingMessages.value = current
        
        return true
    }
}

interface AuditLogService {
    suspend fun logSecurityEvent(event: SecurityEvent)
    fun getRecentEvents(): Flow<List<SecurityEvent>>
}

@kotlinx.serialization.Serializable
data class SecurityEvent(
    val id: String,
    val type: String,
    val deviceId: String?,
    val message: String,
    val timestamp: Long,
    val severity: String = "INFO"
)

class MockAuditLogService(private val storage: LocalStorage) : AuditLogService {
    private val _recentEvents = MutableStateFlow<List<SecurityEvent>>(emptyList())
    
    override suspend fun logSecurityEvent(event: SecurityEvent) {
        val current = _recentEvents.value.toMutableList()
        current.add(event)
        if (current.size > 100) {
            current.removeAt(0) // Keep only recent 100 events
        }
        _recentEvents.value = current
    }
    
    override fun getRecentEvents(): Flow<List<SecurityEvent>> = _recentEvents.asStateFlow()
}