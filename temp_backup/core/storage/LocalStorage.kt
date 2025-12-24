package com.omnisyncra.core.storage

import com.omnisyncra.core.crdt.CrdtState
import com.omnisyncra.core.domain.OmnisyncraState

interface LocalStorage {
    suspend fun saveCrdtState(state: CrdtState)
    suspend fun loadCrdtState(): CrdtState?
    suspend fun saveOmnisyncraState(state: OmnisyncraState)
    suspend fun loadOmnisyncraState(): OmnisyncraState?
    suspend fun clearAll()
    suspend fun getStorageSize(): Long
}