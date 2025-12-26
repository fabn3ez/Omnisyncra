package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * JVM implementation of certificate management using Java Security APIs
 */
actual class PlatformCertificateManager {
    
    private val keyFactory = KeyFactory.getInstance("RSA") // Use RSA for broader compatibility
    private val signature = Signature.getInstance("SHA256withRSA")
    private val javaSecureRandom = java.security.SecureRandom()
    
    // Storage directory for certificates
    private val storageDir = File(System.getProperty("user.home"), ".omnisyncra/certificates").apply {
        mkdirs()
    }
    
    actual suspend fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, javaSecureRandom)
        
        val javaKeyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPair(
            publicKey = javaKeyPair.public.encoded,
            privateKey = javaKeyPair.private.encoded
        )
    }
    
    actual suspend fun signCertificate(certificateData: ByteArray, privateKey: ByteArray): ByteArray {
        val privateKeySpec = PKCS8EncodedKeySpec(privateKey)
        val javaPrivateKey = keyFactory.generatePrivate(privateKeySpec)
        
        signature.initSign(javaPrivateKey, javaSecureRandom)
        signature.update(certificateData)
        
        return signature.sign()
    }
    
    actual suspend fun verifyCertificateSignature(
        certificateData: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Boolean {
        return try {
            val publicKeySpec = X509EncodedKeySpec(publicKey)
            val javaPublicKey = keyFactory.generatePublic(publicKeySpec)
            
            this.signature.initVerify(javaPublicKey)
            this.signature.update(certificateData)
            
            this.signature.verify(signature)
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun securelyStoreCertificate(certificate: DeviceCertificate): Boolean {
        return try {
            val serializedCert = SerializableCertificate.fromDeviceCertificate(certificate)
            val json = Json.encodeToString(serializedCert)
            
            val certFile = File(storageDir, "${certificate.deviceId}.cert")
            Files.write(
                certFile.toPath(),
                json.encodeToByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            
            // Set restrictive permissions (owner read/write only)
            certFile.setReadable(false, false)
            certFile.setWritable(false, false)
            certFile.setReadable(true, true)
            certFile.setWritable(true, true)
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun securelyRetrieveCertificate(deviceId: Uuid): DeviceCertificate? {
        return try {
            val certFile = File(storageDir, "$deviceId.cert")
            if (!certFile.exists()) return null
            
            val json = Files.readAllBytes(certFile.toPath()).decodeToString()
            val serializedCert = Json.decodeFromString<SerializableCertificate>(json)
            
            serializedCert.toDeviceCertificate()
        } catch (e: Exception) {
            null
        }
    }
    
    actual suspend fun securelyDeleteCertificate(deviceId: Uuid): Boolean {
        return try {
            val certFile = File(storageDir, "$deviceId.cert")
            if (certFile.exists()) {
                // Overwrite with random data before deletion for security
                val fileSize = certFile.length().toInt()
                if (fileSize > 0) {
                    val randomData = ByteArray(fileSize)
                    javaSecureRandom.nextBytes(randomData)
                    Files.write(certFile.toPath(), randomData, StandardOpenOption.WRITE)
                }
                
                certFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Serializable version of DeviceCertificate for storage
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
            deviceId = com.benasher44.uuid.Uuid.fromString(deviceId),
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