package com.omnisyncra.core.storage

import android.content.Context
import com.omnisyncra.core.crdt.CrdtState
import com.omnisyncra.core.domain.OmnisyncraState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class AndroidStorage(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LocalStorage {
    
    private val crdtStateFile = File(context.filesDir, "crdt_state.json")
    private val omnisyncraStateFile = File(context.filesDir, "omnisyncra_state.json")
    
    override suspend fun saveCrdtState(state: CrdtState) = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(CrdtState.serializer(), state)
        crdtStateFile.writeText(jsonString)
    }
    
    override suspend fun loadCrdtState(): CrdtState? = withContext(Dispatchers.IO) {
        try {
            if (crdtStateFile.exists()) {
                val jsonString = crdtStateFile.readText()
                json.decodeFromString(CrdtState.serializer(), jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun saveOmnisyncraState(state: OmnisyncraState) = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(OmnisyncraState.serializer(), state)
        omnisyncraStateFile.writeText(jsonString)
    }
    
    override suspend fun loadOmnisyncraState(): OmnisyncraState? = withContext(Dispatchers.IO) {
        try {
            if (omnisyncraStateFile.exists()) {
                val jsonString = omnisyncraStateFile.readText()
                json.decodeFromString(OmnisyncraState.serializer(), jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        crdtStateFile.delete()
        omnisyncraStateFile.delete()
        Unit
    }
    
    override suspend fun getStorageSize(): Long = withContext(Dispatchers.IO) {
        val crdtSize = if (crdtStateFile.exists()) crdtStateFile.length() else 0L
        val stateSize = if (omnisyncraStateFile.exists()) omnisyncraStateFile.length() else 0L
        crdtSize + stateSize
    }
}