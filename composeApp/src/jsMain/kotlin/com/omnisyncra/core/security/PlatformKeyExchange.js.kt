package com.omnisyncra.core.security

/**
 * JavaScript implementation with simplified key exchange operations
 */
actual class PlatformKeyExchange {
    
    actual suspend fun generateX25519KeyPair(): KeyPair {
        // Generate a simple key pair (not cryptographically secure in JS demo)
        val privateKey = generateRandomBytes(32)
        val publicKey = derivePublicKey(privateKey)
        
        return KeyPair(
            publicKey = publicKey,
            privateKey = privateKey
        )
    }
    
    actual suspend fun deriveSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        // Simple shared secret derivation (not cryptographically secure)
        val combined = ByteArray(32)
        for (i in 0 until 32) {
            combined[i] = (privateKey[i].toInt() xor publicKey[i].toInt()).toByte()
        }
        return simpleHash(combined)
    }
    
    actual suspend fun generateNonce(size: Int): ByteArray {
        return generateRandomBytes(size)
    }
    
    actual suspend fun hkdfExpand(
        sharedSecret: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        // Simple HKDF simulation (not cryptographically secure)
        val combined = sharedSecret + salt + info
        val hash = simpleHash(combined)
        
        val result = ByteArray(outputLength)
        var offset = 0
        var counter = 0
        
        while (offset < outputLength) {
            val iterationHash = simpleHash(hash + counter.toByte())
            val copyLength = minOf(iterationHash.size, outputLength - offset)
            // Manual array copy for JS compatibility
            for (i in 0 until copyLength) {
                result[offset + i] = iterationHash[i]
            }
            offset += copyLength
            counter++
        }
        
        return result
    }
    
    // Simple random byte generation (not cryptographically secure)
    private fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        for (i in bytes.indices) {
            bytes[i] = (kotlin.random.Random.nextInt(256) - 128).toByte()
        }
        return bytes
    }
    
    // Simple public key derivation (not cryptographically secure)
    private fun derivePublicKey(privateKey: ByteArray): ByteArray {
        val publicKey = ByteArray(32)
        for (i in 0 until 32) {
            publicKey[i] = (privateKey[i].toInt() * 9 + 25).toByte()
        }
        return publicKey
    }
    
    // Simple hash function (not cryptographically secure)
    private fun simpleHash(data: ByteArray): ByteArray {
        var hash = 0x811c9dc5.toInt() // FNV offset basis
        
        for (byte in data) {
            hash = hash xor byte.toInt()
            hash = hash * 0x01000193 // FNV prime
        }
        
        val result = ByteArray(32)
        for (i in result.indices) {
            result[i] = ((hash shr (i % 32)) and 0xFF).toByte()
            hash = hash * 0x01000193 // Continue mixing
        }
        return result
    }
}