package com.omnisyncra.core.state

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.storage.LocalStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.omnisyncra.core.platform.TimeUtils
import kotlinx.serialization.decodeFromString

/**
 * Distributed state management using CRDT principles
 */
@Serializable
data class StateSnapshot(
    val nodeId: String,
    val version: Long,
    val data: Map<String, String>,
    val timestamp: Long = TimeUtils.currentTimeMillis()
)

interface DistributedStateManager {
    val currentState: Flow<StateSnapshot>
    suspend fun updateState(key: String, value: String)
    suspend fun getState(key: String): String?
    suspend fun syncWithPeer(peerId: Uuid)
}

class SimpleStateManager(
    private val nodeId: Uuid,
    private val storage: LocalStorage
) : DistributedStateManager {
    
    private val _currentState = MutableStateFlow(
        StateSnapshot(
            nodeId = nodeId.toString(),
            version = 0L,
            data = emptyMap()
        )
    )
    override val currentState = _currentState.asStateFlow()
    
    override suspend fun updateState(key: String, value: String) {
        val current = _currentState.value
        val newData = current.data.toMutableMap()
        newData[key] = value
        
        val newState = current.copy(
            version = current.version + 1,
            data = newData,
            timestamp = TimeUtils.currentTimeMillis()
        )
        
        _currentState.value = newState
        
        // Persist to storage
        storage.store("state_snapshot", Json.encodeToString(newState))
    }
    
    override suspend fun getState(key: String): String? {
        return _currentState.value.data[key]
    }
    
    override suspend fun syncWithPeer(peerId: Uuid) {
        // Mock sync operation
        println("Syncing state with peer: $peerId")
    }
}

class StateRecovery(
    private val storage: LocalStorage,
    private val nodeId: Uuid
) {
    suspend fun recoverState(): StateSnapshot? {
        return try {
            val stored = storage.retrieve("state_snapshot")
            stored?.let { Json.decodeFromString<StateSnapshot>(it) }
        } catch (e: Exception) {
            null
        }
    }
}