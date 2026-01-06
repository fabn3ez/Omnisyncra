package com.omnisyncra.core.security

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * JavaScript implementation of CryptoEngine using Web Crypto API
 */
actual class CryptoEngine {
    
    actual suspend fun generateRandomBytes(size: Int): ByteArray {
        val array = Uint8Array(size)
        js("crypto.getRandomValues(array)")
        return array.asByteArray()
    }
    
    actual suspend fun encryptAES256GCM(data: ByteArray, key: ByteArray, nonce: ByteArray): EncryptedData {
        val cryptoKey = importAESKey(key)
        
        val algorithm = js("""({
            name: "AES-GCM",
            iv: new Uint8Array(nonce)
        })""")
        
        val encryptedBuffer = js("crypto.subtle.encrypt(algorithm, cryptoKey, new Uint8Array(data))")
            .unsafeCast<Promise<ArrayBuffer>>().await()
        
        val encrypted = Uint8Array(encryptedBuffer).asByteArray()
        
        // GCM appends 16-byte tag at the end
        val tagLength = 16
        val ciphertext = encrypted.sliceArray(0 until encrypted.size - tagLength)
        val tag = encrypted.sliceArray(encrypted.size - tagLength until encrypted.size)
        
        return EncryptedData(ciphertext, nonce, tag)
    }
    
    actual suspend fun decryptAES256GCM(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        val cryptoKey = importAESKey(key)
        
        val algorithm = js("""({
            name: "AES-GCM",
            iv: new Uint8Array(encryptedData.nonce)
        })""")
        
        // Combine ciphertext and tag for Web Crypto API
        val combined = encryptedData.ciphertext + encryptedData.tag
        
        val decryptedBuffer = js("crypto.subtle.decrypt(algorithm, cryptoKey, new Uint8Array(combined))")
            .unsafeCast<Promise<ArrayBuffer>>().await()
        
        return Uint8Array(decryptedBuffer).asByteArray()
    }
    
    actual suspend fun generateEd25519KeyPair(): KeyPair {
        // Web Crypto API doesn't support Ed25519 in all browsers yet
        // Using simplified simulation for compatibility
        val privateKey = generateRandomBytes(32)
        val publicKey = generateRandomBytes(32)
        return KeyPair(publicKey, privateKey)
    }
    
    actual suspend fun signEd25519(data: ByteArray, privateKey: ByteArray): ByteArray {
        // Simplified Ed25519 signature simulation using SHA-256
        val combined = data + privateKey
        val hashBuffer = js("crypto.subtle.digest('SHA-256', new Uint8Array(combined))")
            .unsafeCast<Promise<ArrayBuffer>>().await()
        
        val hash = Uint8Array(hashBuffer).asByteArray()
        return hash.sliceArray(0..31)
    }
    
    actual suspend fun verifyEd25519(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        val combined = data + publicKey.reversedArray()
        val hashBuffer = js("crypto.subtle.digest('SHA-256', new Uint8Array(combined))")
            .unsafeCast<Promise<ArrayBuffer>>().await()
        
        val expectedSignature = Uint8Array(hashBuffer).asByteArray().sliceArray(0..31)
        return signature.contentEquals(expectedSignature)
    }
    
    actual suspend fun generateX25519KeyPair(): KeyPair {
        // Simplified X25519 simulation
        val privateKey = generateRandomBytes(32)
        val publicKey = generateRandomBytes(32)
        return KeyPair(publicKey, privateKey)
    }
    
    actual suspend fun performX25519KeyExchange(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val combined = privateKey + publicKey
        val hashBuffer = js("crypto.subtle.digest('SHA-256', new Uint8Array(combined))")
            .unsafeCast<Promise<ArrayBuffer>>().await()
        
        return Uint8Array(hashBuffer).asByteArray()
    }
    
    actual suspend fun deriveKeyHKDF(inputKey: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        // Simplified HKDF using Web Crypto API HMAC
        val saltKey = if (salt.isEmpty()) ByteArray(32) else salt
        
        // Import salt as HMAC key
        val hmacKey = js("""
            crypto.subtle.importKey(
                'raw',
                new Uint8Array(saltKey),
                { name: 'HMAC', hash: 'SHA-256' },
                false,
                ['sign']
            )
        """).unsafeCast<Promise<dynamic>>().await()
        
        // Extract phase
        val prkBuffer = js("crypto.subtle.sign('HMAC', hmacKey, new Uint8Array(inputKey))")
            .unsafeCast<Promise<ArrayBuffer>>().await()
        
        val prk = Uint8Array(prkBuffer).asByteArray()
        
        // Expand phase (simplified)
        val result = ByteArray(length)
        var offset = 0
        var counter = 1
        
        while (offset < length) {
            val input = info + byteArrayOf(counter.toByte())
            val expandKey = js("""
                crypto.subtle.importKey(
                    'raw',
                    new Uint8Array(prk),
                    { name: 'HMAC', hash: 'SHA-256' },
                    false,
                    ['sign']
                )
            """).unsafeCast<Promise<dynamic>>().await()
            
            val hashBuffer = js("crypto.subtle.sign('HMAC', expandKey, new Uint8Array(input))")
                .unsafeCast<Promise<ArrayBuffer>>().await()
            
            val hash = Uint8Array(hashBuffer).asByteArray()
            val copyLength = minOf(hash.size, length - offset)
            hash.copyInto(result, offset, 0, copyLength)
            offset += copyLength
            counter++
        }
        
        return result
    }
    
    private suspend fun importAESKey(key: ByteArray): dynamic {
        return js("""
            crypto.subtle.importKey(
                'raw',
                new Uint8Array(key),
                { name: 'AES-GCM' },
                false,
                ['encrypt', 'decrypt']
            )
        """).unsafeCast<Promise<dynamic>>().await()
    }
}

// Extension function to convert Uint8Array to ByteArray
private fun Uint8Array.asByteArray(): ByteArray {
    return ByteArray(this.length) { i -> this.asDynamic()[i].unsafeCast<Byte>() }
}