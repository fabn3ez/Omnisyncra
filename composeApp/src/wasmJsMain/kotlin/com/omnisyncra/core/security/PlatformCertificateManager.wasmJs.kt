package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * WASM implementation with simplified crypto operations
 * Note: Full crypto support in WASM is limited, this provides basic functionality
 */
actual class PlatformCertificateManager {
    
    // Simple storage map for WASM (in-memory only)
    private val certificateStorage = mutableMapOf<String, String>()
    
    actual suspend fun generateKeyPair(): KeyPair {
        // Generate a simple key pair (not cryptographically secure in WASM)
        // In a real implementation, this would use a proper crypto library
        val keyData = generateRandomBytes(32)
        val publicKey = keyData.copyOf()
        val privateKey = keyData.copyOf()
        
        return KeyPair(
            publicKey = publicKey,
            privateKey = privateKey
        )
    }
    
    actual suspend fun signCertificate(certificateData: ByteArray, privateKey: ByteArray): ByteArray {
        // Simple signature simulation (not cryptographically secure)
        // In a real implementation, this would use proper Ed25519 signing
        val combined = privateKey + certificateData
        return simpleHash(combined)
    }
    
    actual suspend fun verifyCertificateSignature(
        certificateData: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Boolean {
        // Simple verification simulation
        val expectedSignature = simpleHash(publicKey + certificateData)
        return signature.contentEquals(expectedSignature)
    }
    
    actual suspend fun securelyStoreCertificate(certificate: DeviceCertificate): Boolean {
        return try {
            val serializedCert = SerializableCertificate.fromDeviceCertificate(certificate)
            val json = Json.encodeToString(serializedCert)
            
            certificateStorage["omnisyncra_cert_${certificate.deviceId}"] = json
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun securelyRetrieveCertificate(deviceId: Uuid): DeviceCertificate? {
        return try {
            val json = certificateStorage["omnisyncra_cert_$deviceId"] ?: return null
            val serializedCert = Json.decodeFromString<SerializableCertificate>(json)
            
            serializedCert.toDeviceCertificate()
        } catch (e: Exception) {
            null
        }
    }
    
    actual suspend fun securelyDeleteCertificate(deviceId: Uuid): Boolean {
        return try {
            certificateStorage.remove("omnisyncra_cert_$deviceId")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // Simple random byte generation (not cryptographically secure)
    private fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        for (i in bytes.indices) {
            bytes[i] = (kotlin.random.Random.nextInt(256) - 128).toByte()
        }
        return bytes
    }
    
    // Simple hash function (not cryptographically secure)
    private fun simpleHash(data: ByteArray): ByteArray {
        var hash = 0
        for (byte in data) {
            hash = ((hash shl 5) - hash + byte.toInt()) and 0xFFFFFF
        }
        
        val result = ByteArray(32)
        for (i in result.indices) {
            result[i] = ((hash shr (i % 24)) and 0xFF).toByte()
        }
        return result
    }
}

/**
 * Serializable version of DeviceCertificate for WASM storage
 */
@Serializable
private data class SerializableCertificate(
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
        fun fromDeviceCertificate(cert: DeviceCertificate): SerializableCertificate {
            return SerializableCertificate(
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
            deviceId = com.benasher44.uuid.uuid4(), // Simplified for WASM demo
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