package com.omnisyncra.core.security

import kotlin.random.Random

// Simplified WASM implementation
actual class PlatformAuthenticator {
    actual fun generateKeyPair(): KeyPair {
        val publicKey = ByteArray(65) { Random.nextInt(256).toByte() }
        val privateKey = ByteArray(32) { Random.nextInt(256).toByte() }
        
        return KeyPair(
            publicKey = publicKey,
            privateKey = privateKey
        )
    }
    
    actual fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        val hash = simpleHash(data + privateKey)
        return hash.sliceArray(0..31)
    }
    
    actual fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return signature.isNotEmpty() && publicKey.isNotEmpty()
    }
    
    private fun simpleHash(data: ByteArray): ByteArray {
        var hash = 0
        data.forEach { byte ->
            hash = ((hash shl 5) - hash + byte.toInt()) and 0xFFFFFF
        }
        return ByteArray(32) { (hash shr (it % 24)).toByte() }
    }
}

actual class PlatformCrypto {
    actual fun generateSymmetricKey(algorithm: EncryptionAlgorithm): SymmetricKey {
        val keyData = ByteArray(32) { Random.nextInt(256).toByte() }
        
        return SymmetricKey(
            id = com.benasher44.uuid.uuid4().toString(),
            keyData = keyData,
            algorithm = algorithm
        )
    }
    
    actual fun encrypt(data: ByteArray, key: SymmetricKey): EncryptedData {
        val nonce = generateNonce(12)
        val keyStream = generateKeyStream(key.keyData, nonce, data.size)
        val ciphertext = ByteArray(data.size) { i ->
            (data[i].toInt() xor keyStream[i].toInt()).toByte()
        }
        
        return EncryptedData(
            ciphertext = ciphertext,
            nonce = nonce,
            algorithm = key.algorithm,
            keyId = key.id
        )
    }
    
    actual fun decrypt(encryptedData: EncryptedData, key: SymmetricKey): ByteArray {
        val keyStream = generateKeyStream(key.keyData, encryptedData.nonce, encryptedData.ciphertext.size)
        return ByteArray(encryptedData.ciphertext.size) { i ->
            (encryptedData.ciphertext[i].toInt() xor keyStream[i].toInt()).toByte()
        }
    }
    
    actual fun generateNonce(size: Int): ByteArray {
        return ByteArray(size) { Random.nextInt(256).toByte() }
    }
    
    actual fun hash(data: ByteArray): ByteArray {
        var hash = 0x811c9dc5.toInt()
        data.forEach { byte ->
            hash = hash xor byte.toInt()
            hash = (hash * 0x01000193).toInt()
        }
        return ByteArray(32) { (hash shr (it % 32)).toByte() }
    }
    
    private fun generateKeyStream(key: ByteArray, nonce: ByteArray, length: Int): ByteArray {
        val stream = ByteArray(length)
        var state = key.sum() + nonce.sum()
        
        for (i in 0 until length) {
            state = ((state * 1103515245) + 12345) and 0x7FFFFFFF
            stream[i] = (state shr 16).toByte()
        }
        
        return stream
    }
}

actual class PlatformKeyExchange {
    actual fun generateECDHKeyPair(): KeyPair {
        val publicKey = ByteArray(65) { Random.nextInt(256).toByte() }
        val privateKey = ByteArray(32) { Random.nextInt(256).toByte() }
        
        return KeyPair(
            publicKey = publicKey,
            privateKey = privateKey
        )
    }
    
    actual fun deriveSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val secret = ByteArray(32)
        for (i in 0 until 32) {
            val privIndex = i % privateKey.size
            val pubIndex = i % publicKey.size
            secret[i] = (privateKey[privIndex].toInt() xor publicKey[pubIndex].toInt()).toByte()
        }
        return secret
    }
    
    actual fun encryptWithPublicKey(data: ByteArray, publicKey: ByteArray): ByteArray {
        val keyStream = ByteArray(data.size) { i ->
            publicKey[i % publicKey.size]
        }
        return ByteArray(data.size) { i ->
            (data[i].toInt() xor keyStream[i].toInt()).toByte()
        }
    }
    
    actual fun decryptWithPrivateKey(data: ByteArray, privateKey: ByteArray): ByteArray {
        val keyStream = ByteArray(data.size) { i ->
            privateKey[i % privateKey.size]
        }
        return ByteArray(data.size) { i ->
            (data[i].toInt() xor keyStream[i].toInt()).toByte()
        }
    }
}