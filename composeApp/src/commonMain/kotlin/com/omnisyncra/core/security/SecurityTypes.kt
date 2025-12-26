package com.omnisyncra.core.security

/**
 * Common security types used across the security system
 */

/**
 * Key pair for asymmetric cryptography
 */
data class KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as KeyPair
        return publicKey.contentEquals(other.publicKey) && 
               privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

/**
 * Encryption algorithms supported by the system
 */
enum class EncryptionAlgorithm {
    AES_256_GCM,
    CHACHA20_POLY1305
}

/**
 * Encrypted data container
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val algorithm: EncryptionAlgorithm,
    val keyId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as EncryptedData
        return ciphertext.contentEquals(other.ciphertext) &&
                nonce.contentEquals(other.nonce) &&
                algorithm == other.algorithm &&
                keyId == other.keyId
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + keyId.hashCode()
        return result
    }
}