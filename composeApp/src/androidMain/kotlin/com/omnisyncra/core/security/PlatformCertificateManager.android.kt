package com.omnisyncra.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.benasher44.uuid.Uuid
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Android implementation using Android Keystore for enhanced security
 */
actual class PlatformCertificateManager {
    
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyFactory = KeyFactory.getInstance("EC") // Ed25519 not widely supported, use EC
    private val signature = Signature.getInstance("SHA256withECDSA")
    private val secureRandom = java.security.SecureRandom()
    
    // Use app's private storage for certificates
    private fun getStorageDir(context: Context): File {
        return File(context.filesDir, "certificates").apply { mkdirs() }
    }
    
    actual suspend fun generateKeyPair(): KeyPair {
        // Generate key pair in Android Keystore for enhanced security
        val keyAlias = "omnisyncra_device_key_${System.currentTimeMillis()}"
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // For automatic operations
            .build()
        
        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        keyPairGenerator.initialize(keyGenParameterSpec)
        
        val javaKeyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPair(
            publicKey = javaKeyPair.public.encoded,
            privateKey = keyAlias.encodeToByteArray() // Store alias instead of actual private key
        )
    }
    
    actual suspend fun signCertificate(certificateData: ByteArray, privateKey: ByteArray): ByteArray {
        val keyAlias = privateKey.decodeToString()
        val keystorePrivateKey = keyStore.getKey(keyAlias, null) as PrivateKey
        
        signature.initSign(keystorePrivateKey, java.security.SecureRandom())
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
            // Get Android context - this would need to be injected in real implementation
            val context = getAndroidContext() ?: return false
            
            val serializedCert = SerializableCertificate.fromDeviceCertificate(certificate)
            val json = Json.encodeToString(serializedCert)
            
            val certFile = File(getStorageDir(context), "${certificate.deviceId}.cert")
            certFile.writeBytes(json.encodeToByteArray())
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun securelyRetrieveCertificate(deviceId: Uuid): DeviceCertificate? {
        return try {
            val context = getAndroidContext() ?: return null
            
            val certFile = File(getStorageDir(context), "$deviceId.cert")
            if (!certFile.exists()) return null
            
            val json = certFile.readBytes().decodeToString()
            val serializedCert = Json.decodeFromString<SerializableCertificate>(json)
            
            serializedCert.toDeviceCertificate()
        } catch (e: Exception) {
            null
        }
    }
    
    actual suspend fun securelyDeleteCertificate(deviceId: Uuid): Boolean {
        return try {
            val context = getAndroidContext() ?: return false
            
            val certFile = File(getStorageDir(context), "$deviceId.cert")
            if (certFile.exists()) {
                // Overwrite with random data before deletion
                val randomData = ByteArray(certFile.length().toInt())
                java.security.SecureRandom().nextBytes(randomData)
                certFile.writeBytes(randomData)
                
                certFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    // This would need to be properly injected in a real implementation
    private fun getAndroidContext(): Context? {
        // In a real implementation, this would be injected via DI
        // For now, return null and handle gracefully
        return null
    }
}

/**
 * Serializable version of DeviceCertificate for Android storage
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