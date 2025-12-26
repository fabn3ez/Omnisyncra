package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

/**
 * JVM implementation using encrypted file storage
 */
actual class PlatformTrustStore actual constructor() : TrustStore {
    
    private val storageDir = File(System.getProperty("user.home"), ".omnisyncra/trust").apply { mkdirs() }
    private val secureRandom = SecureRandom()
    
    // Simple encryption key derivation (in production, use proper key management)
    private val encryptionKey = deriveEncryptionKey()
    
    actual override suspend fun storeTrustRelationship(relationship: TrustRelationship): Boolean {
        return try {
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
            val file = File(storageDir, "$deviceId.trust")
            if (file.exists()) {
                // Overwrite with random data before deletion
                val randomData = ByteArray(file.length().toInt())
                secureRandom.nextBytes(randomData)
                file.writeBytes(randomData)
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun deriveEncryptionKey(): SecretKeySpec {
        // Simple key derivation (in production, use proper key management)
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        return SecretKeySpec(key.encoded, "AES")
    }
    
    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec)
        val encrypted = cipher.doFinal(data)
        
        // Prepend IV to encrypted data
        return iv + encrypted
    }
    
    private fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = data.sliceArray(0..15)
        val encrypted = data.sliceArray(16 until data.size)
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, ivSpec)
        return cipher.doFinal(encrypted)
    }
}

/**
 * Serializable version of TrustRelationship for JVM storage
 */
@Serializable
private data class SerializableTrustRelationship(
    val deviceId: String,
    val level: String,
    val establishedAt: Long,
    val lastUpdated: Long,
    val certificate: TrustSerializableCertificate?,
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