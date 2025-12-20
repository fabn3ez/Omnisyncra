package com.omnisyncra.core.storage

import com.omnisyncra.core.crdt.CrdtState
import com.omnisyncra.core.domain.OmnisyncraState
import kotlinx.serialization.json.Json

class WasmStorage(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LocalStorage {
    
    private val crdtStateKey = "omnisyncra_crdt_state"
    private val omnisyncraStateKey = "omnisyncra_state"
    
    override suspend fun saveCrdtState(state: CrdtState) {
        val jsonString = json.encodeToString(CrdtState.serializer(), state)
        setLocalStorageItem(crdtStateKey, jsonString)
    }
    
    override suspend fun loadCrdtState(): CrdtState? {
        return try {
            val jsonString = getLocalStorageItem(crdtStateKey)
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
        setLocalStorageItem(omnisyncraStateKey, jsonString)
    }
    
    override suspend fun loadOmnisyncraState(): OmnisyncraState? {
        return try {
            val jsonString = getLocalStorageItem(omnisyncraStateKey)
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
        removeLocalStorageItem(crdtStateKey)
        removeLocalStorageItem(omnisyncraStateKey)
    }
    
    override suspend fun getStorageSize(): Long {
        val crdtSize = getLocalStorageItem(crdtStateKey)?.length ?: 0
        val stateSize = getLocalStorageItem(omnisyncraStateKey)?.length ?: 0
        return (crdtSize + stateSize).toLong()
    }
    
    private fun setLocalStorageItem(key: String, value: String) {
        js("localStorage.setItem(key, value)")
    }
    
    private fun getLocalStorageItem(key: String): String? {
        return js("localStorage.getItem(key)") as String?
    }
    
    private fun removeLocalStorageItem(key: String) {
        js("localStorage.removeItem(key)")
    }
}