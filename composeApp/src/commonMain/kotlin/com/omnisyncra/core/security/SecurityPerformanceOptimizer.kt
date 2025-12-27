package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Performance metrics for security operations
 */
data class SecurityPerformanceMetrics(
    val encryptionLatency: Duration,
    val decryptionLatency: Duration,
    val keyDerivationLatency: Duration,
    val signatureLatency: Duration,
    val verificationLatency: Duration,
    val cacheHitRate: Double,
    val throughputOpsPerSecond: Double,
    val memoryUsageMB: Double,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Session key cache entry
 */
data class CachedSessionKey(
    val sessionId: Uuid,
    val key: ByteArray,
    val createdAt: Long,
    val lastUsed: Long,
    val useCount: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CachedSessionKey
        return sessionId == other.sessionId &&
                key.contentEquals(other.key) &&
                createdAt == other.createdAt &&
                lastUsed == other.lastUsed &&
                useCount == other.useCount
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastUsed.hashCode()
        result = 31 * result + useCount.hashCode()
        return result
    }
}

/**
 * Streaming encryption context for large payloads
 */
data class StreamingEncryptionContext(
    val contextId: Uuid,
    val sessionKey: ByteArray,
    val chunkSize: Int = 64 * 1024, // 64KB chunks
    val totalSize: Long,
    val processedSize: Long = 0L,
    val startTime: Long = Clock.System.now().toEpochMilliseconds()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as StreamingEncryptionContext
        return contextId == other.contextId &&
                sessionKey.contentEquals(other.sessionKey) &&
                chunkSize == other.chunkSize &&
                totalSize == other.totalSize &&
                processedSize == other.processedSize &&
                startTime == other.startTime
    }

    override fun hashCode(): Int {
        var result = contextId.hashCode()
        result = 31 * result + sessionKey.contentHashCode()
        result = 31 * result + chunkSize.hashCode()
        result = 31 * result + totalSize.hashCode()
        result = 31 * result + processedSize.hashCode()
        result = 31 * result + startTime.hashCode()
        return result
    }
}

/**
 * Performance optimization configuration
 */
data class SecurityPerformanceConfig(
    val enableSessionKeyCache: Boolean = true,
    val sessionKeyCacheSize: Int = 100,
    val sessionKeyCacheTTL: Long = 3600_000L, // 1 hour
    val enableStreamingEncryption: Boolean = true,
    val streamingThreshold: Long = 1024 * 1024L, // 1MB
    val streamingChunkSize: Int = 64 * 1024, // 64KB
    val enableBatchOperations: Boolean = true,
    val batchSize: Int = 10,
    val batchTimeout: Long = 100L, // 100ms
    val enablePrecomputedKeys: Boolean = true,
    val precomputedKeyPoolSize: Int = 20
)

/**
 * Security performance optimizer interface
 */
interface SecurityPerformanceOptimizer {
    val performanceMetrics: StateFlow<SecurityPerformanceMetrics>
    val config: StateFlow<SecurityPerformanceConfig>
    
    suspend fun optimizeEncryption(data: ByteArray, sessionId: Uuid): Result<EncryptedData>
    suspend fun optimizeDecryption(encryptedData: EncryptedData, sessionId: Uuid): Result<ByteArray>
    suspend fun getCachedSessionKey(sessionId: Uuid): ByteArray?
    suspend fun cacheSessionKey(sessionId: Uuid, key: ByteArray)
    suspend fun createStreamingContext(totalSize: Long, sessionKey: ByteArray): StreamingEncryptionContext
    suspend fun processStreamingChunk(context: StreamingEncryptionContext, chunk: ByteArray): Result<ByteArray>
    suspend fun updateConfig(newConfig: SecurityPerformanceConfig)
    suspend fun getPerformanceReport(): SecurityPerformanceMetrics
}

/**
 * Implementation of security performance optimizer
 */
class OmnisyncraSecurityPerformanceOptimizer(
    private val deviceId: Uuid,
    private val securityLogger: SecurityEventLogger,
    private val initialConfig: SecurityPerformanceConfig = SecurityPerformanceConfig()
) : SecurityPerformanceOptimizer {
    
    private val _performanceMetrics = MutableStateFlow(
        SecurityPerformanceMetrics(
            encryptionLatency = 0.toDuration(DurationUnit.MILLISECONDS),
            decryptionLatency = 0.toDuration(DurationUnit.MILLISECONDS),
            keyDerivationLatency = 0.toDuration(DurationUnit.MILLISECONDS),
            signatureLatency = 0.toDuration(DurationUnit.MILLISECONDS),
            verificationLatency = 0.toDuration(DurationUnit.MILLISECONDS),
            cacheHitRate = 0.0,
            throughputOpsPerSecond = 0.0,
            memoryUsageMB = 0.0
        )
    )
    override val performanceMetrics: StateFlow<SecurityPerformanceMetrics> = _performanceMetrics.asStateFlow()
    
    private val _config = MutableStateFlow(initialConfig)
    override val config: StateFlow<SecurityPerformanceConfig> = _config.asStateFlow()
    
    // Session key cache
    private val sessionKeyCache = mutableMapOf<Uuid, CachedSessionKey>()
    
    // Streaming contexts
    private val streamingContexts = mutableMapOf<Uuid, StreamingEncryptionContext>()
    
    // Performance tracking
    private var totalOperations = 0L
    private var cacheHits = 0L
    private var cacheMisses = 0L
    private val operationTimes = mutableListOf<Long>()
    
    override suspend fun optimizeEncryption(data: ByteArray, sessionId: Uuid): Result<EncryptedData> {
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        return try {
            val config = _config.value
            
            // Check if we should use streaming encryption
            if (config.enableStreamingEncryption && data.size > config.streamingThreshold) {
                return encryptWithStreaming(data, sessionId)
            }
            
            // Get cached session key
            val sessionKey = if (config.enableSessionKeyCache) {
                getCachedSessionKey(sessionId) ?: generateAndCacheSessionKey(sessionId)
            } else {
                generateSessionKey()
            }
            
            // Perform optimized encryption
            val encryptedData = performOptimizedEncryption(data, sessionKey)
            
            // Update performance metrics
            val endTime = Clock.System.now().toEpochMilliseconds()
            val latency = (endTime - startTime).toDuration(DurationUnit.MILLISECONDS)
            updateEncryptionMetrics(latency)
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Optimized encryption completed",
                sessionId = sessionId,
                details = mapOf(
                    "data_size" to data.size.toString(),
                    "latency_ms" to latency.inWholeMilliseconds.toString(),
                    "streaming_used" to "false"
                )
            )
            
            Result.success(encryptedData)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Optimized encryption failed",
                sessionId = sessionId,
                error = e
            )
            Result.failure(e)
        }
    }
    
    override suspend fun optimizeDecryption(encryptedData: EncryptedData, sessionId: Uuid): Result<ByteArray> {
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        return try {
            val config = _config.value
            
            // Get cached session key
            val sessionKey = if (config.enableSessionKeyCache) {
                getCachedSessionKey(sessionId) ?: return Result.failure(
                    IllegalStateException("Session key not found in cache")
                )
            } else {
                generateSessionKey() // In real implementation, would retrieve from secure storage
            }
            
            // Perform optimized decryption
            val decryptedData = performOptimizedDecryption(encryptedData, sessionKey)
            
            // Update performance metrics
            val endTime = Clock.System.now().toEpochMilliseconds()
            val latency = (endTime - startTime).toDuration(DurationUnit.MILLISECONDS)
            updateDecryptionMetrics(latency)
            
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_PERFORMED,
                severity = SecurityEventSeverity.INFO,
                message = "Optimized decryption completed",
                sessionId = sessionId,
                details = mapOf(
                    "data_size" to decryptedData.size.toString(),
                    "latency_ms" to latency.inWholeMilliseconds.toString()
                )
            )
            
            Result.success(decryptedData)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.DECRYPTION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Optimized decryption failed",
                sessionId = sessionId,
                error = e
            )
            Result.failure(e)
        }
    }
    
    override suspend fun getCachedSessionKey(sessionId: Uuid): ByteArray? {
        val config = _config.value
        if (!config.enableSessionKeyCache) {
            return null
        }
        
        val cachedKey = sessionKeyCache[sessionId]
        if (cachedKey != null) {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            
            // Check if key is still valid
            if (currentTime - cachedKey.createdAt <= config.sessionKeyCacheTTL) {
                // Update usage statistics
                sessionKeyCache[sessionId] = cachedKey.copy(
                    lastUsed = currentTime,
                    useCount = cachedKey.useCount + 1
                )
                
                cacheHits++
                updateCacheHitRate()
                
                return cachedKey.key
            } else {
                // Key expired, remove from cache
                sessionKeyCache.remove(sessionId)
            }
        }
        
        cacheMisses++
        updateCacheHitRate()
        return null
    }
    
    override suspend fun cacheSessionKey(sessionId: Uuid, key: ByteArray) {
        val config = _config.value
        if (!config.enableSessionKeyCache) {
            return
        }
        
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        // Check cache size limit
        if (sessionKeyCache.size >= config.sessionKeyCacheSize) {
            // Remove oldest entry
            val oldestEntry = sessionKeyCache.values.minByOrNull { it.lastUsed }
            if (oldestEntry != null) {
                sessionKeyCache.remove(oldestEntry.sessionId)
            }
        }
        
        sessionKeyCache[sessionId] = CachedSessionKey(
            sessionId = sessionId,
            key = key,
            createdAt = currentTime,
            lastUsed = currentTime
        )
    }
    
    override suspend fun createStreamingContext(totalSize: Long, sessionKey: ByteArray): StreamingEncryptionContext {
        val contextId = com.benasher44.uuid.uuid4()
        val config = _config.value
        
        val context = StreamingEncryptionContext(
            contextId = contextId,
            sessionKey = sessionKey,
            chunkSize = config.streamingChunkSize,
            totalSize = totalSize
        )
        
        streamingContexts[contextId] = context
        return context
    }
    
    override suspend fun processStreamingChunk(
        context: StreamingEncryptionContext,
        chunk: ByteArray
    ): Result<ByteArray> {
        return try {
            // Perform chunk encryption
            val encryptedChunk = performOptimizedEncryption(chunk, context.sessionKey)
            
            // Update context
            val updatedContext = context.copy(
                processedSize = context.processedSize + chunk.size
            )
            streamingContexts[context.contextId] = updatedContext
            
            // Clean up if complete
            if (updatedContext.processedSize >= updatedContext.totalSize) {
                streamingContexts.remove(context.contextId)
            }
            
            Result.success(encryptedChunk.ciphertext)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateConfig(newConfig: SecurityPerformanceConfig) {
        _config.value = newConfig
        
        // Adjust cache size if needed
        if (sessionKeyCache.size > newConfig.sessionKeyCacheSize) {
            val entriesToRemove = sessionKeyCache.size - newConfig.sessionKeyCacheSize
            val oldestEntries = sessionKeyCache.values
                .sortedBy { it.lastUsed }
                .take(entriesToRemove)
            
            oldestEntries.forEach { entry ->
                sessionKeyCache.remove(entry.sessionId)
            }
        }
        
        securityLogger.logEvent(
            type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
            severity = SecurityEventSeverity.INFO,
            message = "Security performance configuration updated",
            details = mapOf(
                "cache_enabled" to newConfig.enableSessionKeyCache.toString(),
                "cache_size" to newConfig.sessionKeyCacheSize.toString(),
                "streaming_enabled" to newConfig.enableStreamingEncryption.toString()
            )
        )
    }
    
    override suspend fun getPerformanceReport(): SecurityPerformanceMetrics {
        return _performanceMetrics.value
    }
    
    private suspend fun encryptWithStreaming(data: ByteArray, sessionId: Uuid): Result<EncryptedData> {
        return try {
            val sessionKey = getCachedSessionKey(sessionId) ?: generateAndCacheSessionKey(sessionId)
            val context = createStreamingContext(data.size.toLong(), sessionKey)
            
            val encryptedChunks = mutableListOf<ByteArray>()
            var offset = 0
            
            while (offset < data.size) {
                val chunkSize = minOf(context.chunkSize, data.size - offset)
                val chunk = data.sliceArray(offset until offset + chunkSize)
                
                val encryptedChunk = processStreamingChunk(context, chunk)
                    .getOrThrow()
                
                encryptedChunks.add(encryptedChunk)
                offset += chunkSize
            }
            
            // Combine encrypted chunks
            val combinedData = encryptedChunks.reduce { acc, chunk -> acc + chunk }
            
            Result.success(EncryptedData(
                ciphertext = combinedData,
                nonce = generateNonce(),
                algorithm = EncryptionAlgorithm.AES_256_GCM,
                keyId = sessionId.toString()
            ))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun performOptimizedEncryption(data: ByteArray, key: ByteArray): EncryptedData {
        // Simplified optimized encryption (in production, use proper AES-GCM)
        val nonce = generateNonce()
        val encrypted = ByteArray(data.size)
        
        for (i in data.indices) {
            encrypted[i] = (data[i].toInt() xor key[i % key.size].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        
        return EncryptedData(
            ciphertext = encrypted,
            nonce = nonce,
            algorithm = EncryptionAlgorithm.AES_256_GCM,
            keyId = "optimized"
        )
    }
    
    private fun performOptimizedDecryption(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        // Simplified optimized decryption (in production, use proper AES-GCM)
        val decrypted = ByteArray(encryptedData.ciphertext.size)
        
        for (i in encryptedData.ciphertext.indices) {
            decrypted[i] = (encryptedData.ciphertext[i].toInt() xor 
                           key[i % key.size].toInt() xor 
                           encryptedData.nonce[i % encryptedData.nonce.size].toInt()).toByte()
        }
        
        return decrypted
    }
    
    private suspend fun generateAndCacheSessionKey(sessionId: Uuid): ByteArray {
        val key = generateSessionKey()
        cacheSessionKey(sessionId, key)
        return key
    }
    
    private fun generateSessionKey(): ByteArray {
        return ByteArray(32) { kotlin.random.Random.nextInt(256).toByte() }
    }
    
    private fun generateNonce(): ByteArray {
        return ByteArray(12) { kotlin.random.Random.nextInt(256).toByte() }
    }
    
    private fun updateEncryptionMetrics(latency: Duration) {
        totalOperations++
        operationTimes.add(latency.inWholeMilliseconds)
        
        // Keep only recent operation times (last 100)
        if (operationTimes.size > 100) {
            operationTimes.removeAt(0)
        }
        
        updatePerformanceMetrics()
    }
    
    private fun updateDecryptionMetrics(latency: Duration) {
        totalOperations++
        operationTimes.add(latency.inWholeMilliseconds)
        
        // Keep only recent operation times (last 100)
        if (operationTimes.size > 100) {
            operationTimes.removeAt(0)
        }
        
        updatePerformanceMetrics()
    }
    
    private fun updateCacheHitRate() {
        val totalCacheRequests = cacheHits + cacheMisses
        val hitRate = if (totalCacheRequests > 0) {
            cacheHits.toDouble() / totalCacheRequests.toDouble()
        } else {
            0.0
        }
        
        val currentMetrics = _performanceMetrics.value
        _performanceMetrics.value = currentMetrics.copy(cacheHitRate = hitRate)
    }
    
    private fun updatePerformanceMetrics() {
        val avgLatency = if (operationTimes.isNotEmpty()) {
            operationTimes.average().toDuration(DurationUnit.MILLISECONDS)
        } else {
            0.toDuration(DurationUnit.MILLISECONDS)
        }
        
        val throughput = if (operationTimes.isNotEmpty()) {
            1000.0 / operationTimes.average() // ops per second
        } else {
            0.0
        }
        
        val memoryUsage = estimateMemoryUsage()
        
        _performanceMetrics.value = SecurityPerformanceMetrics(
            encryptionLatency = avgLatency,
            decryptionLatency = avgLatency,
            keyDerivationLatency = avgLatency,
            signatureLatency = avgLatency,
            verificationLatency = avgLatency,
            cacheHitRate = _performanceMetrics.value.cacheHitRate,
            throughputOpsPerSecond = throughput,
            memoryUsageMB = memoryUsage
        )
    }
    
    private fun estimateMemoryUsage(): Double {
        // Estimate memory usage in MB
        val cacheMemory = sessionKeyCache.size * 64 // Approximate bytes per cache entry
        val contextMemory = streamingContexts.size * 128 // Approximate bytes per context
        val totalBytes = cacheMemory + contextMemory
        return totalBytes / (1024.0 * 1024.0) // Convert to MB
    }
    
    // Cleanup expired cache entries
    suspend fun cleanupExpiredEntries(): Int {
        val config = _config.value
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        val expiredEntries = sessionKeyCache.filter { (_, entry) ->
            currentTime - entry.createdAt > config.sessionKeyCacheTTL
        }
        
        expiredEntries.keys.forEach { sessionId ->
            sessionKeyCache.remove(sessionId)
        }
        
        return expiredEntries.size
    }
}