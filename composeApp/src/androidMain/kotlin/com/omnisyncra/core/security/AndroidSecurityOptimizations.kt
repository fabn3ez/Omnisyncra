package com.omnisyncra.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.benasher44.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android-specific security optimizations using Android Keystore
 */
class AndroidSecurityOptimizations(
    private val deviceId: Uuid,
    private val securityLogger: SecurityEventLogger
) {
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "omnisyncra_"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
    
    /**
     * Generate a hardware-backed encryption key using Android Keystore
     */
    suspend fun generateHardwareBackedKey(keyAlias: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                "$KEY_ALIAS_PREFIX$keyAlias",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // Can be enabled for additional security
                .setRandomizedEncryptionRequired(true)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_GENERATED,
                severity = SecurityEventSeverity.INFO,
                message = "Hardware-backed key generated",
                details = mapOf(
                    "key_alias" to keyAlias,
                    "hardware_backed" to "true",
                    "key_size" to "256"
                )
            )
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to generate hardware-backed key",
                error = e,
                details = mapOf("key_alias" to keyAlias)
            )
            false
        }
    }
    
    /**
     * Encrypt data using Android Keystore hardware-backed key
     */
    suspend fun encryptWithHardwareKey(
        data: ByteArray,
        keyAlias: String
    ): Result<EncryptedData> = withContext(Dispatchers.IO) {
        try {
            val secretKey = keyStore.getKey("$KEY_ALIAS_PREFIX$keyAlias", null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data)
            
            val result = EncryptedData(
                ciphertext = encryptedData,
                nonce = iv,
                algorithm = EncryptionAlgorithm.AES_256_GCM,
                keyId = keyAlias
            )
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Hardware-backed encryption completed",
                details = mapOf(
                    "key_alias" to keyAlias,
                    "data_size" to data.size.toString(),
                    "hardware_backed" to "true"
                )
            )
            
            Result.success(result)
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Hardware-backed encryption failed",
                error = e,
                details = mapOf("key_alias" to keyAlias)
            )
            Result.failure(e)
        }
    }
    
    /**
     * Decrypt data using Android Keystore hardware-backed key
     */
    suspend fun decryptWithHardwareKey(
        encryptedData: EncryptedData,
        keyAlias: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val secretKey = keyStore.getKey("$KEY_ALIAS_PREFIX$keyAlias", null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, encryptedData.nonce)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            val decryptedData = cipher.doFinal(encryptedData.ciphertext)
            
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Hardware-backed decryption completed",
                details = mapOf(
                    "key_alias" to keyAlias,
                    "data_size" to decryptedData.size.toString(),
                    "hardware_backed" to "true"
                )
            )
            
            Result.success(decryptedData)
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Hardware-backed decryption failed",
                error = e,
                details = mapOf("key_alias" to keyAlias)
            )
            Result.failure(e)
        }
    }
    
    /**
     * Check if hardware security module is available
     */
    suspend fun isHardwareSecurityAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Try to access Android Keystore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            // Check if we can generate a test key
            val testKeyAlias = "test_hardware_availability"
            val result = generateHardwareBackedKey(testKeyAlias)
            
            // Clean up test key
            if (result) {
                keyStore.deleteEntry("$KEY_ALIAS_PREFIX$testKeyAlias")
            }
            
            result
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.WARNING,
                message = "Hardware security not available",
                error = e
            )
            false
        }
    }
    
    /**
     * Get hardware security capabilities
     */
    suspend fun getHardwareSecurityCapabilities(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        try {
            mapOf(
                "keystore_available" to isHardwareSecurityAvailable(),
                "hardware_backed_keys" to true, // Android Keystore provides this
                "secure_element" to false, // Would need to check device-specific capabilities
                "biometric_auth" to false, // Would need to check BiometricManager
                "strongbox" to false // Would need to check for StrongBox availability
            )
        } catch (e: Exception) {
            mapOf(
                "keystore_available" to false,
                "hardware_backed_keys" to false,
                "secure_element" to false,
                "biometric_auth" to false,
                "strongbox" to false
            )
        }
    }
    
    /**
     * Optimize for Android-specific performance characteristics
     */
    suspend fun optimizeForAndroid(): SecurityPerformanceConfig {
        val capabilities = getHardwareSecurityCapabilities()
        
        return SecurityPerformanceConfig(
            enableSessionKeyCache = true,
            sessionKeyCacheSize = if (capabilities["hardware_backed_keys"] == true) 50 else 100,
            sessionKeyCacheTTL = 1800_000L, // 30 minutes for mobile
            enableStreamingEncryption = true,
            streamingThreshold = 512 * 1024L, // 512KB for mobile
            streamingChunkSize = 32 * 1024, // 32KB chunks for mobile
            enableBatchOperations = true,
            batchSize = 5, // Smaller batches for mobile
            batchTimeout = 50L, // Faster timeout for mobile responsiveness
            enablePrecomputedKeys = capabilities["hardware_backed_keys"] != true, // Only if no hardware backing
            precomputedKeyPoolSize = 10 // Smaller pool for mobile
        )
    }
    
    /**
     * Clean up Android Keystore entries
     */
    suspend fun cleanupKeystoreEntries(): Int = withContext(Dispatchers.IO) {
        try {
            var cleanedCount = 0
            val aliases = keyStore.aliases().toList()
            
            for (alias in aliases) {
                if (alias.startsWith(KEY_ALIAS_PREFIX)) {
                    try {
                        keyStore.deleteEntry(alias)
                        cleanedCount++
                    } catch (e: Exception) {
                        securityLogger.logEvent(
                            type = SecurityEventType.CLEANUP_PERFORMED,
                            severity = SecurityEventSeverity.WARNING,
                            message = "Failed to delete keystore entry",
                            error = e,
                            details = mapOf("alias" to alias)
                        )
                    }
                }
            }
            
            if (cleanedCount > 0) {
                securityLogger.logEvent(
                    type = SecurityEventType.CLEANUP_PERFORMED,
                    severity = SecurityEventSeverity.INFO,
                    message = "Android Keystore cleanup completed",
                    details = mapOf("cleaned_entries" to cleanedCount.toString())
                )
            }
            
            cleanedCount
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.CLEANUP_PERFORMED,
                severity = SecurityEventSeverity.ERROR,
                message = "Android Keystore cleanup failed",
                error = e
            )
            0
        }
    }
}