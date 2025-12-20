package com.omnisyncra.core.storage

import com.omnisyncra.core.crdt.CrdtState
import com.omnisyncra.core.domain.OmnisyncraState
import kotlinx.serialization.json.Json

class InMemoryStorage(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LocalStorage {
    private var crdtState: CrdtState? = null
    private var omnisyncraState: OmnisyncraState? = null
    
    override suspend fun saveCrdtState(state: CrdtState) {
        crdtState = state
    }
    
    override suspend fun loadCrdtState(): CrdtState? {
        return crdtState
    }
    
    override suspend fun saveOmnisyncraState(state: OmnisyncraState) {
        omnisyncraState = state
    }
    
    override suspend fun loadOmnisyncraState(): OmnisyncraState? {
        return omnisyncraState
    }
    
    override suspend fun clearAll() {
        crdtState = null
        omnisyncraState = null
    }
    
    override suspend fun getStorageSize(): Long {
        val crdtSize = crdtState?.let { json.encodeToString(CrdtState.serializer(), it).length } ?: 0
        val stateSize = omnisyncraState?.let { json.encodeToString(OmnisyncraState.serializer(), it).length } ?: 0
        return (crdtSize + stateSize).toLong()
    }
}