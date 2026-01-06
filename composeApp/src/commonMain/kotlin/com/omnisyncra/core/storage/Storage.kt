package com.omnisyncra.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Storage abstraction for cross-platform data persistence
 */
interface LocalStorage {
    suspend fun store(key: String, value: String): Boolean
    suspend fun retrieve(key: String): String?
    suspend fun delete(key: String): Boolean
    suspend fun clear(): Boolean
    fun observeKey(key: String): Flow<String?>
}

class InMemoryStorage : LocalStorage {
    private val storage = mutableMapOf<String, String>()
    private val observers = mutableMapOf<String, MutableStateFlow<String?>>()
    
    override suspend fun store(key: String, value: String): Boolean {
        storage[key] = value
        observers[key]?.value = value
        return true
    }
    
    override suspend fun retrieve(key: String): String? {
        return storage[key]
    }
    
    override suspend fun delete(key: String): Boolean {
        storage.remove(key)
        observers[key]?.value = null
        return true
    }
    
    override suspend fun clear(): Boolean {
        storage.clear()
        observers.values.forEach { it.value = null }
        return true
    }
    
    override fun observeKey(key: String): Flow<String?> {
        return observers.getOrPut(key) { 
            MutableStateFlow(storage[key]) 
        }
    }
}