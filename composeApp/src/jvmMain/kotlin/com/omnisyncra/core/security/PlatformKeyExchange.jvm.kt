package com.omnisyncra.core.security

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * JVM implementation of key exchange using Java Security APIs
 */
actual class PlatformKeyExchange {
    
    private val javaSecureRandom = java.security.SecureRandom()
    
    actual suspend fun generateX25519KeyPair(): KeyPair {
        // Use ECDH with P-256 for broader compatibility
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1") // P-256 curve
        keyPairGenerator.initialize(ecSpec, javaSecureRandom)
        
        val javaKeyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPair(
            publicKey = javaKeyPair.public.encoded,
            privateKey = javaKeyPair.private.encoded
        )
    }
    
    actual suspend fun deriveSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKeySpec = PKCS8EncodedKeySpec(privateKey)
        val publicKeySpec = X509EncodedKeySpec(publicKey)
        
        val javaPrivateKey = keyFactory.generatePrivate(privateKeySpec)
        val javaPublicKey = keyFactory.generatePublic(publicKeySpec)
        
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(javaPrivateKey)
        keyAgreement.doPhase(javaPublicKey, true)
        
        return keyAgreement.generateSecret()
    }
    
    actual suspend fun generateNonce(size: Int): ByteArray {
        val nonce = ByteArray(size)
        javaSecureRandom.nextBytes(nonce)
        return nonce
    }
    
    actual suspend fun hkdfExpand(
        sharedSecret: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        // HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
        val mac = Mac.getInstance("HmacSHA256")
        val saltKey = SecretKeySpec(salt, "HmacSHA256")
        mac.init(saltKey)
        val prk = mac.doFinal(sharedSecret)
        
        // HKDF-Expand: OKM = HMAC-Hash(PRK, info || counter)
        val okm = ByteArray(outputLength)
        val hmac = Mac.getInstance("HmacSHA256")
        val prkKey = SecretKeySpec(prk, "HmacSHA256")
        hmac.init(prkKey)
        
        var offset = 0
        var counter = 1
        
        while (offset < outputLength) {
            hmac.update(info)
            hmac.update(counter.toByte())
            
            val t = hmac.doFinal()
            val copyLength = minOf(t.size, outputLength - offset)
            System.arraycopy(t, 0, okm, offset, copyLength)
            
            offset += copyLength
            counter++
            
            if (counter > 255) break // HKDF limit
        }
        
        return okm
    }
}