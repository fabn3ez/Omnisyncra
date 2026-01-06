package com.omnisyncra.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec as HmacKeySpec

/**
 * JVM implementation of CryptoEngine using standard Java cryptography
 */
actual class CryptoEngine {
    private val secureRandom = SecureRandom()
    
    actual suspend fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }
    
    actual suspend fun encryptAES256GCM(data: ByteArray, key: ByteArray, nonce: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce) // 128-bit tag
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(data)
        
        // Extract tag from the end of ciphertext (GCM appends tag)
        val tagLength = 16 // 128 bits = 16 bytes
        val actualCiphertext = ciphertext.sliceArray(0 until ciphertext.size - tagLength)
        val tag = ciphertext.sliceArray(ciphertext.size - tagLength until ciphertext.size)
        
        return EncryptedData(actualCiphertext, nonce, tag)
    }
    
    actual suspend fun decryptAES256GCM(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, encryptedData.nonce)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        // Combine ciphertext and tag for GCM decryption
        val ciphertextWithTag = encryptedData.ciphertext + encryptedData.tag
        return cipher.doFinal(ciphertextWithTag)
    }
    
    actual suspend fun generateEd25519KeyPair(): KeyPair {
        // Simplified Ed25519 simulation using secure random
        // In a real implementation, this would use proper Ed25519 libraries
        val privateKey = generateRandomBytes(32)
        val publicKey = generateRandomBytes(32) // Simulated public key
        return KeyPair(publicKey, privateKey)
    }
    
    actual suspend fun signEd25519(data: ByteArray, privateKey: ByteArray): ByteArray {
        // Simplified Ed25519 signature simulation
        // In a real implementation, this would use proper Ed25519 signing
        val hash = MessageDigest.getInstance("SHA-256").digest(data + privateKey)
        return hash.sliceArray(0..31) // 32-byte signature
    }
    
    actual suspend fun verifyEd25519(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        // Simplified Ed25519 verification simulation
        // In a real implementation, this would use proper Ed25519 verification
        val expectedSignature = MessageDigest.getInstance("SHA-256").digest(data + publicKey.reversedArray())
        return signature.contentEquals(expectedSignature.sliceArray(0..31))
    }
    
    actual suspend fun generateX25519KeyPair(): KeyPair {
        // Simplified X25519 simulation
        // In a real implementation, this would use proper X25519 libraries
        val privateKey = generateRandomBytes(32)
        val publicKey = generateRandomBytes(32) // Simulated public key
        return KeyPair(publicKey, privateKey)
    }
    
    actual suspend fun performX25519KeyExchange(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        // Simplified X25519 key exchange simulation
        // In a real implementation, this would use proper X25519 ECDH
        val combined = privateKey + publicKey
        return MessageDigest.getInstance("SHA-256").digest(combined)
    }
    
    actual suspend fun deriveKeyHKDF(inputKey: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        // Simplified HKDF implementation using HMAC-SHA256
        val hmac = Mac.getInstance("HmacSHA256")
        val saltKey = if (salt.isEmpty()) ByteArray(32) else salt
        hmac.init(HmacKeySpec(saltKey, "HmacSHA256"))
        
        // Extract phase
        val prk = hmac.doFinal(inputKey)
        
        // Expand phase
        hmac.init(HmacKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var offset = 0
        var counter = 1
        
        while (offset < length) {
            val input = info + byteArrayOf(counter.toByte())
            val hash = hmac.doFinal(input)
            val copyLength = minOf(hash.size, length - offset)
            System.arraycopy(hash, 0, result, offset, copyLength)
            offset += copyLength
            counter++
        }
        
        return result
    }
}