package com.omnisyncra.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual class PlatformAuthenticator {
    actual fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
        val spec = ECGenParameterSpec("secp256r1")
        keyPairGenerator.initialize(spec)
        val javaKeyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPair(
            publicKey = javaKeyPair.public.encoded,
            privateKey = javaKeyPair.private.encoded
        )
    }
    
    actual fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        return try {
            val signature = Signature.getInstance("SHA256withECDSA")
            val keyFactory = KeyFactory.getInstance("EC")
            val privKey = keyFactory.generatePrivate(
                java.security.spec.PKCS8EncodedKeySpec(privateKey)
            )
            signature.initSign(privKey)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
    
    actual fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKey = keyFactory.generatePublic(
                java.security.spec.X509EncodedKeySpec(publicKey)
            )
            verifier.initVerify(pubKey)
            verifier.update(data)
            verifier.verify(signature)
        } catch (e: Exception) {
            false
        }
    }
}

actual class PlatformCrypto {
    actual fun generateSymmetricKey(algorithm: EncryptionAlgorithm): SymmetricKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()
        
        return SymmetricKey(
            id = com.benasher44.uuid.uuid4().toString(),
            keyData = secretKey.encoded,
            algorithm = algorithm
        )
    }
    
    actual fun encrypt(data: ByteArray, key: SymmetricKey): EncryptedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.keyData, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(data)
        
        return EncryptedData(
            ciphertext = ciphertext,
            nonce = nonce,
            algorithm = key.algorithm,
            keyId = key.id
        )
    }
    
    actual fun decrypt(encryptedData: EncryptedData, key: SymmetricKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.keyData, "AES")
        val spec = GCMParameterSpec(128, encryptedData.nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(encryptedData.ciphertext)
    }
    
    actual fun generateNonce(size: Int): ByteArray {
        val nonce = ByteArray(size)
        SecureRandom().nextBytes(nonce)
        return nonce
    }
    
    actual fun hash(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}

actual class PlatformKeyExchange {
    actual fun generateECDHKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val spec = ECGenParameterSpec("secp256r1")
        keyPairGenerator.initialize(spec)
        val javaKeyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPair(
            publicKey = javaKeyPair.public.encoded,
            privateKey = javaKeyPair.private.encoded
        )
    }
    
    actual fun deriveSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val privKey = keyFactory.generatePrivate(
                java.security.spec.PKCS8EncodedKeySpec(privateKey)
            )
            val pubKey = keyFactory.generatePublic(
                java.security.spec.X509EncodedKeySpec(publicKey)
            )
            
            val keyAgreement = javax.crypto.KeyAgreement.getInstance("ECDH")
            keyAgreement.init(privKey)
            keyAgreement.doPhase(pubKey, true)
            keyAgreement.generateSecret()
        } catch (e: Exception) {
            ByteArray(32) // Return empty secret on error
        }
    }
    
    actual fun encryptWithPublicKey(data: ByteArray, publicKey: ByteArray): ByteArray {
        return try {
            val keyFactory = KeyFactory.getInstance("RSA")
            val pubKey = keyFactory.generatePublic(
                java.security.spec.X509EncodedKeySpec(publicKey)
            )
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, pubKey)
            cipher.doFinal(data)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
    
    actual fun decryptWithPrivateKey(data: ByteArray, privateKey: ByteArray): ByteArray {
        return try {
            val keyFactory = KeyFactory.getInstance("RSA")
            val privKey = keyFactory.generatePrivate(
                java.security.spec.PKCS8EncodedKeySpec(privateKey)
            )
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privKey)
            cipher.doFinal(data)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
}