package com.omnisyncra.core.storage

import com.omnisyncra.core.crdt.CrdtState
import com.omnisyncra.core.domain.OmnisyncraState
import kotlinx.serialization.json.Json

class BrowserStorage(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LocalStorage {
    
    private val crdtStateKey = "omnisyncra_crdt_state"
    private val omnisyncraStateKey = "omnisyncra_state"
    
    override suspend fun saveCrdtState(state: CrdtState) {
        val jsonString = json.encodeToString(CrdtState.serializer(), state)
        localStorage.setItem(crdtStateKey, jsonString)
    }
    
    override suspend fun loadCrdtState(): CrdtState? {
        return try {
            val jsonString = localStorage.getItem(crdtStateKey)
            if (jsonString != null) {
                json.decodeFromString(CrdtState.serializer(), jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun saveOmnisyncraState(state: OmnisyncraState) {
        val jsonString = json.encodeToString(OmnisyncraState.serializer(), state)
        localStorage.setItem(omnisyncraStateKey, jsonString)
    }
    
    override suspend fun loadOmnisyncraState(): OmnisyncraState? {
        return try {
            val jsonString = localStorage.getItem(omnisyncraStateKey)
            if (jsonString != null) {
                json.decodeFromString(OmnisyncraState.serializer(), jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun clearAll() {
        localStorage.removeItem(crdtStateKey)
        localStorage.removeItem(omnisyncraStateKey)
    }
    
    override suspend fun getStorageSize(): Long {
        val crdtSize = localStorage.getItem(crdtStateKey)?.length ?: 0
        val stateSize = localStorage.getItem(omnisyncraStateKey)?.length ?: 0
        return (crdtSize + stateSize).toLong()
    }
}

// Browser localStorage wrapper
private external object localStorage {
    fun setItem(key: String, value: String)
    fun getItem(key: String): String?
    fun removeItem(key: String)
}