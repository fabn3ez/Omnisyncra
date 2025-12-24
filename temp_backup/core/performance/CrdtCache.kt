package com.omnisyncra.core.performance

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.crdt.CrdtState
import com.omnisyncra.core.domain.OmnisyncraState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

data class CacheConfig(
    val maxCacheSize: Int = 1000,
    val ttlMs: Long = 300_000L, // 5 minutes
    val maxMemoryMB: Int = 50
)

data class CacheEntry<T>(
    val key: String,
    val value: T,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    var lastAccessedAt: Long = Clock.System.now().toEpochMilliseconds(),
    var accessCount: Int = 1,
    val sizeBytes: Int
)

class CrdtCache(
    private val config: CacheConfig = CacheConfig()
) {
    private val stateCache = mutableMapOf<String, CacheEntry<CrdtState>>()
    private val materializedCache = mutableMapOf<String, CacheEntry<OmnisyncraState>>()
    private val operationCache = mutableMapOf<String, CacheEntry<List<Any>>>()
    private val mutex = Mutex()
    private val cleanupJob: Job
    
    private var currentMemoryUsage = 0
    
    init {
        cleanupJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(60_000L) // Cleanup every minute
                cleanupExpiredEntries()
            }
        }
    }
    
    suspend fun getCrdtState(nodeId: Uuid): CrdtState? {
        return getFromCache(stateCache, "state_$nodeId")
    }
    
    suspend fun putCrdtState(nodeId: Uuid, state: CrdtState) {
        val sizeBytes = estimateStateSize(state)
        putInCache(stateCache, "state_$nodeId", state, sizeBytes)
    }
    
    suspend fun getMaterializedState(nodeId: Uuid): OmnisyncraState? {
        return getFromCache(materializedCache, "materialized_$nodeId")
    }
    
    suspend fun putMaterializedState(nodeId: Uuid, state: OmnisyncraState) {
        val sizeBytes = estimateMaterializedStateSize(state)
        putInCache(materializedCache, "materialized_$nodeId", state, sizeBytes)
    }
    
    suspend fun getOperations(nodeId: Uuid, fromVersion: Long): List<Any>? {
        return getFromCache(operationCache, "ops_${nodeId}_$fromVersion")
    }
    
    suspend fun putOperations(nodeId: Uuid, fromVersion: Long, operations: List<Any>) {
        val sizeBytes = estimateOperationsSize(operations)
        putInCache(operationCache, "ops_${nodeId}_$fromVersion", operations, sizeBytes)
    }
    
    private suspend fun <T> getFromCache(
        cache: MutableMap<String, CacheEntry<T>>,
        key: String
    ): T? {
        return mutex.withLock {
            val entry = cache[key]
            if (entry != null && !isExpired(entry)) {
                entry.lastAccessedAt = Clock.System.now().toEpochMilliseconds()
                entry.accessCount++
                entry.value
            } else {
                cache.remove(key)
                null
            }
        }
    }
    
    private suspend fun <T> putInCache(
        cache: MutableMap<String, CacheEntry<T>>,
        key: String,
        value: T,
        sizeBytes: Int
    ) {
        mutex.withLock {
            // Remove existing entry if present
            cache[key]?.let { oldEntry ->
                currentMemoryUsage -= oldEntry.sizeBytes
            }
            
            // Check memory limits
            if (currentMemoryUsage + sizeBytes > config.maxMemoryMB * 1024 * 1024) {
                evictLeastRecentlyUsed()
            }
            
            // Check size limits
            if (cache.size >= config.maxCacheSize) {
                evictLeastRecentlyUsed(cache)
            }
            
            val entry = CacheEntry(key, value, sizeBytes = sizeBytes)
            cache[key] = entry
            currentMemoryUsage += sizeBytes
        }
    }
    
    private fun isExpired(entry: CacheEntry<*>): Boolean {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        return (currentTime - entry.createdAt) > config.ttlMs
    }
    
    private suspend fun cleanupExpiredEntries() {
        mutex.withLock {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            
            listOf(stateCache, materializedCache, operationCache).forEach { cache ->
                val expiredKeys = cache.filter { (_, entry) ->
                    (currentTime - entry.createdAt) > config.ttlMs
                }.keys
                
                expiredKeys.forEach { key ->
                    cache[key]?.let { entry ->
                        currentMemoryUsage -= entry.sizeBytes
                    }
                    cache.remove(key)
                }
            }
        }
    }
    
    private fun <T> evictLeastRecentlyUsed(cache: MutableMap<String, CacheEntry<T>>) {
        if (cache.isEmpty()) return
        
        val lruEntry = cache.values.minByOrNull { it.lastAccessedAt }
        lruEntry?.let { entry ->
            cache.remove(entry.key)
            currentMemoryUsage -= entry.sizeBytes
        }
    }
    
    private fun evictLeastRecentlyUsed() {
        // Find LRU entry across all caches
        val allEntries = listOf(
            stateCache.values,
            materializedCache.values,
            operationCache.values
        ).flatten()
        
        val lruEntry = allEntries.minByOrNull { it.lastAccessedAt }
        lruEntry?.let { entry ->
            when {
                stateCache.containsKey(entry.key) -> stateCache.remove(entry.key)
                materializedCache.containsKey(entry.key) -> materializedCache.remove(entry.key)
                operationCache.containsKey(entry.key) -> operationCache.remove(entry.key)
            }
            currentMemoryUsage -= entry.sizeBytes
        }
    }
    
    private fun estimateStateSize(state: CrdtState): Int {
        // Rough estimation: 100 bytes base + 50 bytes per operation
        return 100 + (state.operations.size * 50)
    }
    
    private fun estimateMaterializedStateSize(state: OmnisyncraState): Int {
        // Rough estimation: 200 bytes base + 100 bytes per context
        return 200 + (state.contextGraph.contexts.size * 100)
    }
    
    private fun estimateOperationsSize(operations: List<Any>): Int {
        // Rough estimation: 50 bytes per operation
        return operations.size * 50
    }
    
    suspend fun getStats(): CacheStats {
        return mutex.withLock {
            CacheStats(
                stateEntries = stateCache.size,
                materializedEntries = materializedCache.size,
                operationEntries = operationCache.size,
                totalMemoryUsageMB = currentMemoryUsage / (1024 * 1024),
                hitRate = calculateHitRate()
            )
        }
    }
    
    private fun calculateHitRate(): Double {
        val allEntries = listOf(
            stateCache.values,
            materializedCache.values,
            operationCache.values
        ).flatten()
        
        if (allEntries.isEmpty()) return 0.0
        
        val totalAccesses = allEntries.sumOf { it.accessCount }
        val totalEntries = allEntries.size
        
        return if (totalEntries > 0) totalAccesses.toDouble() / totalEntries else 0.0
    }
    
    suspend fun invalidate(nodeId: Uuid) {
        mutex.withLock {
            listOf("state_$nodeId", "materialized_$nodeId").forEach { key ->
                stateCache.remove(key)?.let { entry ->
                    currentMemoryUsage -= entry.sizeBytes
                }
                materializedCache.remove(key)?.let { entry ->
                    currentMemoryUsage -= entry.sizeBytes
                }
            }
            
            // Remove operation caches for this node
            val operationKeys = operationCache.keys.filter { it.startsWith("ops_$nodeId") }
            operationKeys.forEach { key ->
                operationCache.remove(key)?.let { entry ->
                    currentMemoryUsage -= entry.sizeBytes
                }
            }
        }
    }
    
    fun cleanup() {
        cleanupJob.cancel()
        runBlocking {
            mutex.withLock {
                stateCache.clear()
                materializedCache.clear()
                operationCache.clear()
                currentMemoryUsage = 0
            }
        }
    }
}

data class CacheStats(
    val stateEntries: Int,
    val materializedEntries: Int,
    val operationEntries: Int,
    val totalMemoryUsageMB: Int,
    val hitRate: Double
)