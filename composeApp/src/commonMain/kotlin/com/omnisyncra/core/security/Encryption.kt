package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Encryption algorithms
enum class EncryptionAlgorithm {
    AES_256_GCM,
    CHACHA20_POLY1305
}

// Encrypted data container
data class EncryptedData(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val algorithm: EncryptionAlgorithm,
    val keyId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as EncryptedData
        return ciphertext.contentEquals(other.ciphertext) &&
                nonce.contentEquals(other.nonce) &&
                algorithm == other.algorithm &&
                keyId == other.keyId
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + keyId.hashCode()
        return result
    }
}

// Symmetric key for encryption
data class SymmetricKey(
    val id: String,
    val keyData: ByteArray,
    val algorithm: EncryptionAlgorithm,
    val createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SymmetricKey
        return id == other.id && keyData.contentEquals(other.keyData)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + keyData.contentHashCode()
        return result
    }
}

// Platform-specific encryption interface
expect class PlatformCrypto() {
    fun generateSymmetricKey(algorithm: EncryptionAlgorithm): SymmetricKey
    fun encrypt(data: ByteArray, key: SymmetricKey): EncryptedData
    fun decrypt(encryptedData: EncryptedData, key: SymmetricKey): ByteArray
    fun generateNonce(size: Int = 12): ByteArray
    fun hash(data: ByteArray): ByteArray
}

// Encryption service
class EncryptionService(
    private val crypto: PlatformCrypto = PlatformCrypto()
) {
    private val keys = mutableMapOf<String, SymmetricKey>()
    private val mutex = Mutex()
    
    suspend fun generateKey(
        keyId: String = com.benasher44.uuid.uuid4().toString(),
        algorithm: EncryptionAlgorithm = EncryptionAlgorithm.AES_256_GCM
    ): SymmetricKey {
        return mutex.withLock {
            val key = crypto.generateSymmetricKey(algorithm)
            val namedKey = key.copy(id = keyId)
            keys[keyId] = namedKey
            namedKey
        }
    }
    
    suspend fun storeKey(key: SymmetricKey) {
        mutex.withLock {
            keys[key.id] = key
        }
    }
    
    suspend fun getKey(keyId: String): SymmetricKey? {
        return mutex.withLock {
            keys[keyId]
        }
    }
    
    suspend fun encrypt(data: ByteArray, keyId: String): EncryptedData? {
        val key = getKey(keyId) ?: return null
        return crypto.encrypt(data, key)
    }
    
    suspend fun decrypt(encryptedData: EncryptedData): ByteArray? {
        val key = getKey(encryptedData.keyId) ?: return null
        return try {
            crypto.decrypt(encryptedData, key)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun encryptString(text: String, keyId: String): EncryptedData? {
        return encrypt(text.encodeToByteArray(), keyId)
    }
    
    suspend fun decryptString(encryptedData: EncryptedData): String? {
        val decrypted = decrypt(encryptedData) ?: return null
        return decrypted.decodeToString()
    }
    
    fun hash(data: ByteArray): ByteArray {
        return crypto.hash(data)
    }
    
    fun hashString(text: String): ByteArray {
        return hash(text.encodeToByteArray())
    }
    
    suspend fun rotateKey(oldKeyId: String, newKeyId: String): SymmetricKey? {
        return mutex.withLock {
            val oldKey = keys[oldKeyId] ?: return@withLock null
            val newKey = crypto.generateSymmetricKey(oldKey.algorithm).copy(id = newKeyId)
            keys[newKeyId] = newKey
            keys.remove(oldKeyId)
            newKey
        }
    }
    
    suspend fun removeKey(keyId: String) {
        mutex.withLock {
            keys.remove(keyId)
        }
    }
    
    suspend fun getAllKeyIds(): List<String> {
        return mutex.withLock {
            keys.keys.toList()
        }
    }
}
