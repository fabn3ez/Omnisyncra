package com.omnisyncra.core.security

/**
 * Cross-platform cryptographic engine
 * Provides core cryptographic operations with platform-specific implementations
 */
expect class CryptoEngine() {
    /**
     * Generate secure random bytes
     */
    suspend fun generateRandomBytes(size: Int): ByteArray
    
    /**
     * Encrypt data using AES-256-GCM
     */
    suspend fun encryptAES256GCM(data: ByteArray, key: ByteArray, nonce: ByteArray): EncryptedData
    
    /**
     * Decrypt data using AES-256-GCM
     */
    suspend fun decryptAES256GCM(encryptedData: EncryptedData, key: ByteArray): ByteArray
    
    /**
     * Generate Ed25519 key pair
     */
    suspend fun generateEd25519KeyPair(): KeyPair
    
    /**
     * Sign data using Ed25519
     */
    suspend fun signEd25519(data: ByteArray, privateKey: ByteArray): ByteArray
    
    /**
     * Verify Ed25519 signature
     */
    suspend fun verifyEd25519(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
    
    /**
     * Generate X25519 key pair for key exchange
     */
    suspend fun generateX25519KeyPair(): KeyPair
    
    /**
     * Perform X25519 key exchange
     */
    suspend fun performX25519KeyExchange(privateKey: ByteArray, publicKey: ByteArray): ByteArray
    
    /**
     * Derive key using HKDF-SHA256
     */
    suspend fun deriveKeyHKDF(inputKey: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray
}

/**
 * Cryptographic key pair
 */
data class KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as KeyPair
        
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!privateKey.contentEquals(other.privateKey)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}