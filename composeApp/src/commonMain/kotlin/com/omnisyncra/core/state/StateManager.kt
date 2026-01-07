package com.omnisyncra.core.state

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.storage.LocalStorage
import com.omnisyncra.core.crdt.*
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
    val crdtState: Flow<CrdtState>
    val materializedState: Flow<MaterializedState?>
    val conflictResolutions: Flow<List<ConflictResolution>>
    
    suspend fun initialize()
    suspend fun updateState(key: String, value: String)
    suspend fun getState(key: String): String?
    suspend fun syncWithPeer(peerId: Uuid)
    suspend fun syncWithPeer(peerState: CrdtState): SyncResult
    suspend fun mergeState(otherState: CrdtState): MergeResult
    suspend fun compactOperations()
    
    // CRDT-specific operations
    suspend fun addDevice(deviceId: Uuid, deviceData: String)
    suspend fun updateDevice(deviceId: Uuid, deviceData: String)
    suspend fun removeDevice(deviceId: Uuid)
    suspend fun setValue(key: String, value: String)
    suspend fun deleteValue(key: String)
    suspend fun incrementCounter(key: String)
    fun getValue(key: String): String?
    fun getStateSnapshot(): CrdtState
    fun getOperationsSince(vectorClock: VectorClock): List<CrdtOperation>
}

class CrdtDistributedStateManager(
    private val nodeId: Uuid,
    private val storage: LocalStorage
) : DistributedStateManager {
    
    private val crdtManager = CrdtStateManager(nodeId, storage)
    
    private val _currentState = MutableStateFlow(
        StateSnapshot(
            nodeId = nodeId.toString(),
            version = 0L,
            data = emptyMap()
        )
    )
    override val currentState = _currentState.asStateFlow()
    override val crdtState = crdtManager.crdtState
    override val materializedState = crdtManager.materializedState
    override val conflictResolutions = crdtManager.conflictResolutions
    
    override suspend fun initialize() {
        crdtManager.initialize()
        
        // Update legacy state format when CRDT state changes
        crdtState.collect { crdt ->
            val legacyData = crdt.keyValueStore
            _currentState.value = StateSnapshot(
                nodeId = nodeId.toString(),
                version = crdt.version,
                data = legacyData,
                timestamp = crdt.lastUpdated
            )
        }
    }
    
    override suspend fun updateState(key: String, value: String) {
        crdtManager.setValue(key, value)
    }
    
    override suspend fun getState(key: String): String? {
        return crdtManager.getValue(key)
    }
    
    override suspend fun syncWithPeer(peerId: Uuid) {
        // Mock sync operation for legacy compatibility
        println("Legacy sync with peer: $peerId")
    }
    
    override suspend fun syncWithPeer(peerState: CrdtState): SyncResult {
        return crdtManager.syncWithPeer(peerState)
    }
    
    override suspend fun mergeState(otherState: CrdtState): MergeResult {
        return crdtManager.mergeState(otherState)
    }
    
    override suspend fun compactOperations() {
        crdtManager.compactOperations()
    }
    
    override suspend fun addDevice(deviceId: Uuid, deviceData: String) {
        crdtManager.addDevice(deviceId, deviceData)
    }
    
    override suspend fun updateDevice(deviceId: Uuid, deviceData: String) {
        crdtManager.updateDevice(deviceId, deviceData)
    }
    
    override suspend fun removeDevice(deviceId: Uuid) {
        crdtManager.removeDevice(deviceId)
    }
    
    override suspend fun setValue(key: String, value: String) {
        crdtManager.setValue(key, value)
    }
    
    override suspend fun deleteValue(key: String) {
        crdtManager.deleteValue(key)
    }
    
    override suspend fun incrementCounter(key: String) {
        crdtManager.incrementCounter(key)
    }
    
    override fun getValue(key: String): String? {
        return crdtManager.getValue(key)
    }
    
    override fun getStateSnapshot(): CrdtState {
        return crdtManager.getStateSnapshot()
    }
    
    override fun getOperationsSince(vectorClock: VectorClock): List<CrdtOperation> {
        return crdtManager.getOperationsSince(vectorClock)
    }
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
    
    // Mock CRDT flows for compatibility
    override val crdtState = MutableStateFlow(
        CrdtState(
            nodeId = nodeId,
            vectorClock = VectorClock(mapOf(nodeId to 0L))
        )
    ).asStateFlow()
    
    override val materializedState = MutableStateFlow<MaterializedState?>(null).asStateFlow()
    override val conflictResolutions = MutableStateFlow<List<ConflictResolution>>(emptyList()).asStateFlow()
    
    override suspend fun initialize() {
        // Load persisted state if available
        val stored = storage.retrieve("state_snapshot")
        stored?.let { 
            try {
                val state = Json.decodeFromString<StateSnapshot>(it)
                _currentState.value = state
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
    }
    
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
    
    // CRDT operations - no-op implementations for simple manager
    override suspend fun syncWithPeer(peerState: CrdtState): SyncResult {
        return SyncResult(0, 0, emptyList(), emptyList())
    }
    
    override suspend fun mergeState(otherState: CrdtState): MergeResult {
        return MergeResult(0, _currentState.value.version, emptyList())
    }
    
    override suspend fun compactOperations() {
        // No-op for simple manager
    }
    
    override suspend fun addDevice(deviceId: Uuid, deviceData: String) {
        updateState("device_$deviceId", deviceData)
    }
    
    override suspend fun updateDevice(deviceId: Uuid, deviceData: String) {
        updateState("device_$deviceId", deviceData)
    }
    
    override suspend fun removeDevice(deviceId: Uuid) {
        // Simple removal by setting empty value
        updateState("device_$deviceId", "")
    }
    
    override suspend fun setValue(key: String, value: String) {
        updateState(key, value)
    }
    
    override suspend fun deleteValue(key: String) {
        val current = _currentState.value
        val newData = current.data.toMutableMap()
        newData.remove(key)
        
        val newState = current.copy(
            version = current.version + 1,
            data = newData,
            timestamp = TimeUtils.currentTimeMillis()
        )
        
        _currentState.value = newState
        storage.store("state_snapshot", Json.encodeToString(newState))
    }
    
    override suspend fun incrementCounter(key: String) {
        val currentValue = getState(key)?.toLongOrNull() ?: 0L
        updateState(key, (currentValue + 1).toString())
    }
    
    override fun getValue(key: String): String? {
        return _currentState.value.data[key]
    }
    
    override fun getStateSnapshot(): CrdtState {
        return crdtState.value
    }
    
    override fun getOperationsSince(vectorClock: VectorClock): List<CrdtOperation> {
        return emptyList()
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