package com.omnisyncra.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.benasher44.uuid.Uuid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

/**
 * Android implementation using Android Keystore for encryption
 */
actual class PlatformTrustStore actual constructor() : TrustStore {
    
    private val keyAlias = "omnisyncra_trust_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    // Get storage directory (would need context injection in real implementation)
    private fun getStorageDir(context: Context?): File? {
        return context?.let { File(it.filesDir, "trust").apply { mkdirs() } }
    }
    
    // Initialize encryption key in Android Keystore
    private fun initializeEncryptionKey() {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
            
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    actual override suspend fun storeTrustRelationship(relationship: TrustRelationship): Boolean {
        return try {
            val context = getAndroidContext()
            val storageDir = getStorageDir(context) ?: return false
            
            initializeEncryptionKey()
            
            val serializable = SerializableTrustRelationship.fromTrustRelationship(relationship)
            val json = Json.encodeToString(serializable)
            val encrypted = encrypt(json.encodeToByteArray())
            
            val file = File(storageDir, "${relationship.deviceId}.trust")
            file.writeBytes(encrypted)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual override suspend fun getTrustRelationship(deviceId: Uuid): TrustRelationship? {
        return try {
            val context = getAndroidContext()
            val storageDir = getStorageDir(context) ?: return null
            
            val file = File(storageDir, "$deviceId.trust")
            if (!file.exists()) return null
            
            val encrypted = file.readBytes()
            val decrypted = decrypt(encrypted)
            val json = decrypted.decodeToString()
            val serializable = Json.decodeFromString<SerializableTrustRelationship>(json)
            
            serializable.toTrustRelationship()
        } catch (e: Exception) {
            null
        }
    }
    
    actual override suspend fun getAllTrustedDevices(): List<TrustRelationship> {
        return try {
            val context = getAndroidContext()
            val storageDir = getStorageDir(context) ?: return emptyList()
            
            storageDir.listFiles { _, name -> name.endsWith(".trust") }
                ?.mapNotNull { file ->
                    try {
                        val encrypted = file.readBytes()
                        val decrypted = decrypt(encrypted)
                        val json = decrypted.decodeToString()
                        val serializable = Json.decodeFromString<SerializableTrustRelationship>(json)
                        serializable.toTrustRelationship()
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    actual override suspend fun updateTrustLevel(deviceId: Uuid, level: TrustLevel): Boolean {
        return try {
            val existing = getTrustRelationship(deviceId) ?: return false
            val updated = existing.copy(
                level = level,
                lastUpdated = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
            storeTrustRelationship(updated)
        } catch (e: Exception) {
            false
        }
    }
    
    actual override suspend fun revokeTrust(deviceId: Uuid): Boolean {
        return updateTrustLevel(deviceId, TrustLevel.REVOKED)
    }
    
    actual override suspend fun deleteTrustRelationship(deviceId: Uuid): Boolean {
        return try {
            val context = getAndroidContext()
            val storageDir = getStorageDir(context) ?: return false
            
            val file = File(storageDir, "$deviceId.trust")
            if (file.exists()) {
                // Overwrite with random data before deletion
                val randomData = ByteArray(file.length().toInt())
                java.security.SecureRandom().nextBytes(randomData)
                file.writeBytes(randomData)
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val secretKey = keyStore.getKey(keyAlias, null)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        // Prepend IV to encrypted data
        return iv + encrypted
    }
    
    private fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val secretKey = keyStore.getKey(keyAlias, null)
        
        val iv = data.sliceArray(0..15)
        val encrypted = data.sliceArray(16 until data.size)
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(encrypted)
    }
    
    // This would need to be properly injected in a real implementation
    private fun getAndroidContext(): Context? {
        // In a real implementation, this would be injected via DI
        return null
    }
}

/**
 * Serializable version of TrustRelationship for Android storage
 */
@Serializable
private data class SerializableTrustRelationship(
    val deviceId: String,
    val level: String,
    val establishedAt: Long,
    val lastUpdated: Long,
    val certificate: SerializableCertificate?,
    val metadata: Map<String, String>
) {
    companion object {
        fun fromTrustRelationship(trust: TrustRelationship): SerializableTrustRelationship {
            return SerializableTrustRelationship(
                deviceId = trust.deviceId.toString(),
                level = trust.level.name,
                establishedAt = trust.establishedAt,
                lastUpdated = trust.lastUpdated,
                certificate = trust.certificate?.let { TrustSerializableCertificate.fromDeviceCertificate(it) },
                metadata = trust.metadata
            )
        }
    }
    
    fun toTrustRelationship(): TrustRelationship {
        return TrustRelationship(
            deviceId = Uuid.fromString(deviceId),
            level = TrustLevel.valueOf(level),
            establishedAt = establishedAt,
            lastUpdated = lastUpdated,
            certificate = certificate?.toDeviceCertificate(),
            metadata = metadata
        )
    }
}

@Serializable
private data class TrustSerializableCertificate(
    val deviceId: String,
    val publicKey: ByteArray,
    val issuer: String,
    val subject: String,
    val validFrom: Long,
    val validUntil: Long,
    val signature: ByteArray,
    val serialNumber: String,
    val version: Int,
    val keyUsage: List<String>,
    val extensions: Map<String, ByteArray>
) {
    companion object {
        fun fromDeviceCertificate(cert: DeviceCertificate): TrustSerializableCertificate {
            return TrustSerializableCertificate(
                deviceId = cert.deviceId.toString(),
                publicKey = cert.publicKey,
                issuer = cert.issuer,
                subject = cert.subject,
                validFrom = cert.validFrom,
                validUntil = cert.validUntil,
                signature = cert.signature,
                serialNumber = cert.serialNumber,
                version = cert.version,
                keyUsage = cert.keyUsage.map { it.name },
                extensions = cert.extensions
            )
        }
    }
    
    fun toDeviceCertificate(): DeviceCertificate {
        return DeviceCertificate(
            deviceId = Uuid.fromString(deviceId),
            publicKey = publicKey,
            issuer = issuer,
            subject = subject,
            validFrom = validFrom,
            validUntil = validUntil,
            signature = signature,
            serialNumber = serialNumber,
            version = version,
            keyUsage = keyUsage.mapNotNull { 
                try { KeyUsage.valueOf(it) } catch (e: Exception) { null }
            }.toSet(),
            extensions = extensions
        )
    }
}