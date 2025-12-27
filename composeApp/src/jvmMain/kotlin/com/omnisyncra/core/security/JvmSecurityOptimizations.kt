package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM-specific security optimizations using Java security providers
 */
class JvmSecurityOptimizations(
    private val deviceId: Uuid,
    private val securityLogger: SecurityEventLogger
) {
    
    private val secureRandom = SecureRandom.getInstanceStrong()
    
    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_LENGTH = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
    
    /**
     * Get available security providers
     */
    fun getAvailableProviders(): List<String> {
        return Security.getProviders().map { "${it.name} ${it.version}" }
    }
    
    /**
     * Check for hardware security module support
     */
    suspend fun isHardwareSecurityAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check for PKCS#11 providers (common for HSMs)
            val providers = Security.getProviders()
            val hasHSM = providers.any { provider ->
                provider.name.contains("PKCS11", ignoreCase = true) ||
                provider.name.contains("HSM", ignoreCase = true) ||
                provider.name.contains("Hardware", ignoreCase = true)
            }
            
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = if (hasHSM) SecurityEventSeverity.INFO else SecurityEventSeverity.WARNING,
                message = if (hasHSM) "Hardware security module detected" else "No hardware security module found",
                details = mapOf(
                    "providers" to providers.map { it.name }.toString(),
                    "hsm_available" to hasHSM.toString()
                )
            )
            
            hasHSM
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to check hardware security availability",
                error = e
            )
            false
        }
    }
    
    /**
     * Generate optimized encryption key using best available provider
     */
    suspend fun generateOptimizedKey(): Result<SecretKey> = withContext(Dispatchers.IO) {
        try {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
            keyGenerator.init(KEY_LENGTH, secureRandom)
            val key = keyGenerator.generateKey()
            
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_GENERATED,
                severity = SecurityEventSeverity.INFO,
                message = "JVM optimized key generated",
                details = mapOf(
                    "algorithm" to ALGORITHM,
                    "key_length" to KEY_LENGTH.toString(),
                    "provider" to keyGenerator.provider.name
                )
            )
            
            Result.success(key)
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "JVM key generation failed",
                error = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * Encrypt data using JVM optimizations
     */
    suspend fun encryptWithJvmOptimizations(
        data: ByteArray,
        key: SecretKey
    ): Result<EncryptedData> = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, secureRandom)
            
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data)
            
            val result = EncryptedData(
                ciphertext = encryptedData,
                nonce = iv,
                algorithm = EncryptionAlgorithm.AES_256_GCM,
                keyId = "jvm_optimized"
            )
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "JVM optimized encryption completed",
                details = mapOf(
                    "data_size" to data.size.toString(),
                    "provider" to cipher.provider.name,
                    "transformation" to TRANSFORMATION
                )
            )
            
            Result.success(result)
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "JVM optimized encryption failed",
                error = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * Decrypt data using JVM optimizations
     */
    suspend fun decryptWithJvmOptimizations(
        encryptedData: EncryptedData,
        key: SecretKey
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, encryptedData.nonce)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            
            val decryptedData = cipher.doFinal(encryptedData.ciphertext)
            
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "JVM optimized decryption completed",
                details = mapOf(
                    "data_size" to decryptedData.size.toString(),
                    "provider" to cipher.provider.name
                )
            )
            
            Result.success(decryptedData)
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "JVM optimized decryption failed",
                error = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * Get JVM security capabilities
     */
    suspend fun getJvmSecurityCapabilities(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        try {
            val providers = Security.getProviders()
            
            mapOf(
                "strong_secure_random" to try {
                    SecureRandom.getInstanceStrong(); true
                } catch (e: Exception) { false },
                "aes_gcm_support" to providers.any { 
                    it.getService("Cipher", TRANSFORMATION) != null 
                },
                "hardware_rng" to try {
                    val rng = SecureRandom.getInstance("NativePRNG")
                    rng.algorithm == "NativePRNG"
                } catch (e: Exception) { false },
                "pkcs11_support" to providers.any { 
                    it.name.contains("PKCS11", ignoreCase = true) 
                },
                "bouncy_castle" to providers.any { 
                    it.name.contains("BC", ignoreCase = true) 
                },
                "unlimited_crypto" to try {
                    Cipher.getMaxAllowedKeyLength("AES") == Int.MAX_VALUE
                } catch (e: Exception) { false }
            )
        } catch (e: Exception) {
            mapOf(
                "strong_secure_random" to false,
                "aes_gcm_support" to false,
                "hardware_rng" to false,
                "pkcs11_support" to false,
                "bouncy_castle" to false,
                "unlimited_crypto" to false
            )
        }
    }
    
    /**
     * Optimize for JVM performance characteristics
     */
    suspend fun optimizeForJvm(): SecurityPerformanceConfig {
        val capabilities = getJvmSecurityCapabilities()
        
        return SecurityPerformanceConfig(
            enableSessionKeyCache = true,
            sessionKeyCacheSize = 500, // JVM can handle large caches
            sessionKeyCacheTTL = 7200_000L, // 2 hours
            enableStreamingEncryption = true,
            streamingThreshold = 10 * 1024 * 1024L, // 10MB for JVM
            streamingChunkSize = 1024 * 1024, // 1MB chunks for JVM
            enableBatchOperations = true,
            batchSize = 50, // Large batches for JVM
            batchTimeout = 500L, // Longer timeout for JVM
            enablePrecomputedKeys = true,
            precomputedKeyPoolSize = 100 // Large pool for JVM
        )
    }
    
    /**
     * Benchmark encryption performance
     */
    suspend fun benchmarkEncryptionPerformance(
        dataSizes: List<Int> = listOf(1024, 10240, 102400, 1048576)
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Long>()
        
        try {
            val key = generateOptimizedKey().getOrThrow()
            
            for (size in dataSizes) {
                val testData = ByteArray(size) { it.toByte() }
                val iterations = maxOf(1, 10000 / size) // Adjust iterations based on size
                
                val startTime = System.nanoTime()
                repeat(iterations) {
                    encryptWithJvmOptimizations(testData, key)
                }
                val endTime = System.nanoTime()
                
                val avgTimeNs = (endTime - startTime) / iterations
                results["encrypt_${size}_bytes_ns"] = avgTimeNs
            }
            
            securityLogger.logEvent(
                type = SecurityEventType.PERFORMANCE_METRIC,
                severity = SecurityEventSeverity.INFO,
                message = "JVM encryption performance benchmark completed",
                details = results.mapValues { it.value.toString() }
            )
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.PERFORMANCE_METRIC,
                severity = SecurityEventSeverity.ERROR,
                message = "JVM encryption performance benchmark failed",
                error = e
            )
        }
        
        results
    }
    
    /**
     * Configure JVM-specific security properties
     */
    fun configureJvmSecurity() {
        try {
            // Enable strong cryptography
            System.setProperty("crypto.policy", "unlimited")
            
            // Configure secure random
            System.setProperty("java.security.egd", "file:/dev/./urandom")
            
            // Enable TLS 1.3
            System.setProperty("https.protocols", "TLSv1.3,TLSv1.2")
            
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.INFO,
                message = "JVM security properties configured",
                details = mapOf(
                    "crypto_policy" to "unlimited",
                    "secure_random" to "urandom",
                    "tls_protocols" to "TLSv1.3,TLSv1.2"
                )
            )
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.WARNING,
                message = "Failed to configure JVM security properties",
                error = e
            )
        }
    }
}