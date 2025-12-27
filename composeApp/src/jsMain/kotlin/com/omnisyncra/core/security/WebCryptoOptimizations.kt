package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * Web Crypto API optimizations for browser environments
 */
class WebCryptoOptimizations(
    private val deviceId: Uuid,
    private val securityLogger: SecurityEventLogger
) {
    
    companion object {
        private const val ALGORITHM_NAME = "AES-GCM"
        private const val KEY_LENGTH = 256
        private const val IV_LENGTH = 12
    }
    
    /**
     * Check if Web Crypto API is available
     */
    fun isWebCryptoAvailable(): Boolean {
        return try {
            js("typeof window !== 'undefined' && window.crypto && window.crypto.subtle") != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generate a cryptographic key using Web Crypto API
     */
    suspend fun generateWebCryptoKey(): Result<dynamic> {
        return try {
            if (!isWebCryptoAvailable()) {
                return Result.failure(UnsupportedOperationException("Web Crypto API not available"))
            }
            
            val keyGenParams = js("""({
                name: '$ALGORITHM_NAME',
                length: $KEY_LENGTH
            })""")
            
            val key = js("window.crypto.subtle.generateKey(keyGenParams, true, ['encrypt', 'decrypt'])")
                .unsafeCast<Promise<dynamic>>()
                .await()
            
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_GENERATED,
                severity = SecurityEventSeverity.INFO,
                message = "Web Crypto key generated",
                details = mapOf(
                    "algorithm" to ALGORITHM_NAME,
                    "key_length" to KEY_LENGTH.toString(),
                    "extractable" to "true"
                )
            )
            
            Result.success(key)
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Web Crypto key generation failed",
                error = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * Encrypt data using Web Crypto API
     */
    suspend fun encryptWithWebCrypto(
        data: ByteArray,
        key: dynamic
    ): Result<EncryptedData> {
        return try {
            if (!isWebCryptoAvailable()) {
                return Result.failure(UnsupportedOperationException("Web Crypto API not available"))
            }
            
            // Generate random IV
            val iv = js("window.crypto.getRandomValues(new Uint8Array($IV_LENGTH))")
                .unsafeCast<Uint8Array>()
            
            // Convert ByteArray to ArrayBuffer
            val dataBuffer = data.toArrayBuffer()
            
            val encryptParams = js("""({
                name: '$ALGORITHM_NAME',
                iv: iv
            })""")
            
            val encryptedBuffer = js("window.crypto.subtle.encrypt(encryptParams, key, dataBuffer)")
                .unsafeCast<Promise<ArrayBuffer>>()
                .await()
            
            val encryptedData = EncryptedData(
                ciphertext = encryptedBuffer.toByteArray(),
                nonce = iv.toByteArray(),
                algorithm = EncryptionAlgorithm.AES_256_GCM,
                keyId = "webcrypto"
            )
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Web Crypto encryption completed",
                details = mapOf(
                    "data_size" to data.size.toString(),
                    "algorithm" to ALGORITHM_NAME
                )
            )
            
            Result.success(encryptedData)
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Web Crypto encryption failed",
                error = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * Decrypt data using Web Crypto API
     */
    suspend fun decryptWithWebCrypto(
        encryptedData: EncryptedData,
        key: dynamic
    ): Result<ByteArray> {
        return try {
            if (!isWebCryptoAvailable()) {
                return Result.failure(UnsupportedOperationException("Web Crypto API not available"))
            }
            
            val decryptParams = js("""({
                name: '$ALGORITHM_NAME',
                iv: new Uint8Array([${encryptedData.nonce.joinToString(",")}])
            })""")
            
            val encryptedBuffer = encryptedData.ciphertext.toArrayBuffer()
            
            val decryptedBuffer = js("window.crypto.subtle.decrypt(decryptParams, key, encryptedBuffer)")
                .unsafeCast<Promise<ArrayBuffer>>()
                .await()
            
            val decryptedData = decryptedBuffer.toByteArray()
            
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Web Crypto decryption completed",
                details = mapOf(
                    "data_size" to decryptedData.size.toString(),
                    "algorithm" to ALGORITHM_NAME
                )
            )
            
            Result.success(decryptedData)
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Web Crypto decryption failed",
                error = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * Generate secure random bytes using Web Crypto API
     */
    fun generateSecureRandom(size: Int): ByteArray {
        return try {
            if (!isWebCryptoAvailable()) {
                // Fallback to Math.random (less secure)
                return ByteArray(size) { (kotlin.js.Math.random() * 256).toInt().toByte() }
            }
            
            val randomArray = js("window.crypto.getRandomValues(new Uint8Array($size))")
                .unsafeCast<Uint8Array>()
            
            randomArray.toByteArray()
        } catch (e: Exception) {
            // Fallback to Math.random
            ByteArray(size) { (kotlin.js.Math.random() * 256).toInt().toByte() }
        }
    }
    
    /**
     * Get Web Crypto capabilities
     */
    suspend fun getWebCryptoCapabilities(): Map<String, Boolean> {
        return try {
            val capabilities = mutableMapOf<String, Boolean>()
            
            capabilities["web_crypto_available"] = isWebCryptoAvailable()
            
            if (isWebCryptoAvailable()) {
                // Test AES-GCM support
                capabilities["aes_gcm_support"] = try {
                    generateWebCryptoKey().isSuccess
                } catch (e: Exception) {
                    false
                }
                
                // Check for other algorithms
                capabilities["rsa_support"] = try {
                    js("window.crypto.subtle.generateKey({name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256'}, true, ['encrypt', 'decrypt'])") != null
                } catch (e: Exception) {
                    false
                }
                
                capabilities["ecdsa_support"] = try {
                    js("window.crypto.subtle.generateKey({name: 'ECDSA', namedCurve: 'P-256'}, true, ['sign', 'verify'])") != null
                } catch (e: Exception) {
                    false
                }
                
                capabilities["secure_random"] = true
            } else {
                capabilities["aes_gcm_support"] = false
                capabilities["rsa_support"] = false
                capabilities["ecdsa_support"] = false
                capabilities["secure_random"] = false
            }
            
            capabilities.toMap()
        } catch (e: Exception) {
            mapOf(
                "web_crypto_available" to false,
                "aes_gcm_support" to false,
                "rsa_support" to false,
                "ecdsa_support" to false,
                "secure_random" to false
            )
        }
    }
    
    /**
     * Optimize for web browser performance characteristics
     */
    suspend fun optimizeForWeb(): SecurityPerformanceConfig {
        val capabilities = getWebCryptoCapabilities()
        
        return SecurityPerformanceConfig(
            enableSessionKeyCache = true,
            sessionKeyCacheSize = 200, // Browsers can handle larger caches
            sessionKeyCacheTTL = 3600_000L, // 1 hour
            enableStreamingEncryption = true,
            streamingThreshold = 2 * 1024 * 1024L, // 2MB for web
            streamingChunkSize = 128 * 1024, // 128KB chunks for web
            enableBatchOperations = true,
            batchSize = 20, // Larger batches for web
            batchTimeout = 200L, // Longer timeout acceptable for web
            enablePrecomputedKeys = capabilities["web_crypto_available"] != true, // Only if no Web Crypto
            precomputedKeyPoolSize = 30 // Larger pool for web
        )
    }
    
    /**
     * Check browser security features
     */
    fun getBrowserSecurityFeatures(): Map<String, Boolean> {
        return try {
            mapOf(
                "https_only" to (js("window.location.protocol") == "https:"),
                "secure_context" to js("window.isSecureContext").unsafeCast<Boolean>(),
                "web_crypto_available" to isWebCryptoAvailable(),
                "local_storage_available" to (js("typeof Storage !== 'undefined'") != null),
                "indexed_db_available" to (js("typeof indexedDB !== 'undefined'") != null),
                "service_worker_available" to (js("'serviceWorker' in navigator") != null),
                "web_assembly_available" to (js("typeof WebAssembly !== 'undefined'") != null)
            )
        } catch (e: Exception) {
            mapOf(
                "https_only" to false,
                "secure_context" to false,
                "web_crypto_available" to false,
                "local_storage_available" to false,
                "indexed_db_available" to false,
                "service_worker_available" to false,
                "web_assembly_available" to false
            )
        }
    }
    
    // Extension functions for type conversion
    private fun ByteArray.toArrayBuffer(): ArrayBuffer {
        val buffer = js("new ArrayBuffer(this.length)").unsafeCast<ArrayBuffer>()
        val view = js("new Uint8Array(buffer)").unsafeCast<Uint8Array>()
        for (i in this.indices) {
            view[i] = this[i]
        }
        return buffer
    }
    
    private fun ArrayBuffer.toByteArray(): ByteArray {
        val view = js("new Uint8Array(this)").unsafeCast<Uint8Array>()
        return view.toByteArray()
    }
    
    private fun Uint8Array.toByteArray(): ByteArray {
        val result = ByteArray(this.length)
        for (i in 0 until this.length) {
            result[i] = this[i].toByte()
        }
        return result
    }
}