package com.omnisyncra.core.crdt

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.storage.LocalStorage
import com.omnisyncra.core.platform.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * CRDT-based Distributed State Manager
 * Provides conflict-free replicated data types for distributed synchronization
 */
class CrdtStateManager(
    private val nodeId: Uuid,
    private val localStorage: LocalStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val _crdtState = MutableStateFlow(
        CrdtState(
            nodeId = nodeId,
            vectorClock = VectorClock(mapOf(nodeId to 0L))
        )
    )
    val crdtState: StateFlow<CrdtState> = _crdtState.asStateFlow()
    
    private val _materializedState = MutableStateFlow<MaterializedState?>(null)
    val materializedState: StateFlow<MaterializedState?> = _materializedState.asStateFlow()
    
    private val _conflictResolutions = MutableStateFlow<List<ConflictResolution>>(emptyList())
    val conflictResolutions: StateFlow<List<ConflictResolution>> = _conflictResolutions.asStateFlow()
    
    private val _syncEvents = MutableStateFlow<List<SyncEvent>>(emptyList())
    val syncEvents: StateFlow<List<SyncEvent>> = _syncEvents.asStateFlow()
    
    suspend fun initialize() {
        // Load persisted state
        val persistedStateJson = localStorage.retrieve("crdt_state")
        if (persistedStateJson != null) {
            try {
                val persistedState = json.decodeFromString<CrdtState>(persistedStateJson)
                _crdtState.value = persistedState
                updateMaterializedState()
                logSyncEvent(SyncEventType.STATE_LOADED, "Loaded persisted CRDT state")
            } catch (e: Exception) {
                logSyncEvent(SyncEventType.ERROR, "Failed to load persisted state: ${e.message}")
            }
        } else {
            logSyncEvent(SyncEventType.INITIALIZED, "Initialized new CRDT state")
        }
    }
    
    /**
     * Apply a local operation to the CRDT state
     */
    suspend fun applyLocalOperation(operation: CrdtOperation) {
        val currentState = _crdtState.value
        val incrementedClock = currentState.vectorClock.increment(nodeId)
        
        // Timestamp and version the operation
        val timestampedOperation = timestampOperation(operation, incrementedClock)
        
        // Apply the operation
        val newState = currentState.applyOperation(timestampedOperation)
        _crdtState.value = newState
        
        // Persist state
        persistState(newState)
        
        // Update materialized view
        updateMaterializedState()
        
        logSyncEvent(SyncEventType.LOCAL_OPERATION_APPLIED, "Applied ${operation::class.simpleName}")
    }
    
    /**
     * Apply remote operations from other nodes
     */
    suspend fun applyRemoteOperations(operations: List<CrdtOperation>) {
        if (operations.isEmpty()) return
        
        var currentState = _crdtState.value
        val conflicts = mutableListOf<ConflictResolution>()
        var appliedCount = 0
        
        operations.forEach { operation ->
            // Check for conflicts
            val conflictingOps = currentState.operations.filter { existing ->
                areConflicting(existing, operation)
            }
            
            if (conflictingOps.isNotEmpty()) {
                val resolution = resolveConflict(operation, conflictingOps)
                conflicts.add(resolution)
                
                if (resolution.resolution == ConflictResolutionType.REJECT) {
                    logSyncEvent(SyncEventType.CONFLICT_RESOLVED, "Rejected conflicting operation ${operation.id}")
                    return@forEach // Skip this operation
                }
            }
            
            currentState = currentState.applyOperation(operation)
            appliedCount++
        }
        
        _crdtState.value = currentState
        
        if (conflicts.isNotEmpty()) {
            _conflictResolutions.value = _conflictResolutions.value + conflicts
        }
        
        // Persist state
        persistState(currentState)
        
        // Update materialized view
        updateMaterializedState()
        
        logSyncEvent(SyncEventType.REMOTE_OPERATIONS_APPLIED, "Applied $appliedCount remote operations")
    }
    
    /**
     * Sync with a peer node
     */
    suspend fun syncWithPeer(peerState: CrdtState): SyncResult {
        val currentState = _crdtState.value
        
        // Get operations that peer doesn't have
        val operationsToSend = currentState.getOperationsSince(peerState.vectorClock)
        
        // Get operations from peer that we don't have
        val operationsToApply = peerState.getOperationsSince(currentState.vectorClock)
        
        // Apply peer's operations
        if (operationsToApply.isNotEmpty()) {
            applyRemoteOperations(operationsToApply)
        }
        
        logSyncEvent(SyncEventType.PEER_SYNC_COMPLETED, 
            "Synced with peer: sent ${operationsToSend.size}, received ${operationsToApply.size}")
        
        return SyncResult(
            operationsSent = operationsToSend.size,
            operationsReceived = operationsToApply.size,
            operationsToSend = operationsToSend,
            conflicts = _conflictResolutions.value.takeLast(operationsToApply.size)
        )
    }
    
    /**
     * Merge with another CRDT state (for bulk synchronization)
     */
    suspend fun mergeState(otherState: CrdtState): MergeResult {
        val currentState = _crdtState.value
        val mergedState = currentState.merge(otherState)
        
        val operationsAdded = mergedState.operations.size - currentState.operations.size
        
        _crdtState.value = mergedState
        persistState(mergedState)
        updateMaterializedState()
        
        logSyncEvent(SyncEventType.STATE_MERGED, "Merged state: added $operationsAdded operations")
        
        return MergeResult(
            operationsAdded = operationsAdded,
            finalVersion = mergedState.version,
            conflicts = emptyList() // Merging handles conflicts automatically
        )
    }
    
    /**
     * Compact the operation log to reduce memory usage
     */
    suspend fun compactOperations() {
        val currentState = _crdtState.value
        val compactedState = currentState.compactOperations()
        
        val removedOperations = currentState.operations.size - compactedState.operations.size
        
        _crdtState.value = compactedState
        persistState(compactedState)
        
        logSyncEvent(SyncEventType.OPERATIONS_COMPACTED, "Removed $removedOperations redundant operations")
    }
    
    /**
     * Get current state snapshot for synchronization
     */
    fun getStateSnapshot(): CrdtState {
        return _crdtState.value
    }
    
    /**
     * Get operations since a specific vector clock
     */
    fun getOperationsSince(vectorClock: VectorClock): List<CrdtOperation> {
        return _crdtState.value.getOperationsSince(vectorClock)
    }
    
    /**
     * Device-specific operations
     */
    suspend fun addDevice(deviceId: Uuid, deviceData: String) {
        val operation = CrdtOperation.DeviceUpdate(
            nodeId = nodeId,
            timestamp = TimeUtils.currentTimeMillis(),
            vectorClock = _crdtState.value.vectorClock,
            deviceId = deviceId,
            deviceData = deviceData,
            operationType = DeviceOperationType.ADD
        )
        applyLocalOperation(operation)
    }
    
    suspend fun updateDevice(deviceId: Uuid, deviceData: String) {
        val operation = CrdtOperation.DeviceUpdate(
            nodeId = nodeId,
            timestamp = TimeUtils.currentTimeMillis(),
            vectorClock = _crdtState.value.vectorClock,
            deviceId = deviceId,
            deviceData = deviceData,
            operationType = DeviceOperationType.UPDATE
        )
        applyLocalOperation(operation)
    }
    
    suspend fun removeDevice(deviceId: Uuid) {
        val operation = CrdtOperation.DeviceUpdate(
            nodeId = nodeId,
            timestamp = TimeUtils.currentTimeMillis(),
            vectorClock = _crdtState.value.vectorClock,
            deviceId = deviceId,
            deviceData = "",
            operationType = DeviceOperationType.REMOVE
        )
        applyLocalOperation(operation)
    }
    
    /**
     * Key-value operations
     */
    suspend fun setValue(key: String, value: String) {
        val operation = CrdtOperation.KeyValueUpdate(
            nodeId = nodeId,
            timestamp = TimeUtils.currentTimeMillis(),
            vectorClock = _crdtState.value.vectorClock,
            key = key,
            value = value,
            operationType = KeyValueOperationType.SET
        )
        applyLocalOperation(operation)
    }
    
    suspend fun deleteValue(key: String) {
        val operation = CrdtOperation.KeyValueUpdate(
            nodeId = nodeId,
            timestamp = TimeUtils.currentTimeMillis(),
            vectorClock = _crdtState.value.vectorClock,
            key = key,
            value = null,
            operationType = KeyValueOperationType.DELETE
        )
        applyLocalOperation(operation)
    }
    
    suspend fun incrementCounter(key: String) {
        val operation = CrdtOperation.KeyValueUpdate(
            nodeId = nodeId,
            timestamp = TimeUtils.currentTimeMillis(),
            vectorClock = _crdtState.value.vectorClock,
            key = key,
            value = null,
            operationType = KeyValueOperationType.INCREMENT
        )
        applyLocalOperation(operation)
    }
    
    fun getValue(key: String): String? {
        return _crdtState.value.keyValueStore[key]
    }
    
    private fun timestampOperation(operation: CrdtOperation, vectorClock: VectorClock): CrdtOperation {
        val timestamp = TimeUtils.currentTimeMillis()
        return when (operation) {
            is CrdtOperation.DeviceUpdate -> operation.copy(
                nodeId = nodeId,
                timestamp = timestamp,
                vectorClock = vectorClock
            )
            is CrdtOperation.ContextUpdate -> operation.copy(
                nodeId = nodeId,
                timestamp = timestamp,
                vectorClock = vectorClock
            )
            is CrdtOperation.StateSync -> operation.copy(
                nodeId = nodeId,
                timestamp = timestamp,
                vectorClock = vectorClock
            )
            is CrdtOperation.DocumentUpdate -> operation.copy(
                nodeId = nodeId,
                timestamp = timestamp,
                vectorClock = vectorClock
            )
            is CrdtOperation.KeyValueUpdate -> operation.copy(
                nodeId = nodeId,
                timestamp = timestamp,
                vectorClock = vectorClock
            )
        }
    }
    
    private fun areConflicting(op1: CrdtOperation, op2: CrdtOperation): Boolean {
        return when {
            op1 is CrdtOperation.DeviceUpdate && op2 is CrdtOperation.DeviceUpdate ->
                op1.deviceId == op2.deviceId && op1.timestamp == op2.timestamp && op1.nodeId != op2.nodeId
            op1 is CrdtOperation.ContextUpdate && op2 is CrdtOperation.ContextUpdate ->
                op1.contextId == op2.contextId && op1.timestamp == op2.timestamp && op1.nodeId != op2.nodeId
            op1 is CrdtOperation.KeyValueUpdate && op2 is CrdtOperation.KeyValueUpdate ->
                op1.key == op2.key && op1.timestamp == op2.timestamp && op1.nodeId != op2.nodeId
            op1 is CrdtOperation.DocumentUpdate && op2 is CrdtOperation.DocumentUpdate ->
                op1.documentId == op2.documentId && 
                op1.position == op2.position && 
                op1.timestamp == op2.timestamp && 
                op1.nodeId != op2.nodeId
            else -> false
        }
    }
    
    private fun resolveConflict(
        newOperation: CrdtOperation,
        conflictingOperations: List<CrdtOperation>
    ): ConflictResolution {
        // Last-writer-wins with node ID as tiebreaker
        val allOperations = conflictingOperations + newOperation
        val winner = allOperations.maxWithOrNull(
            compareBy<CrdtOperation> { it.timestamp }
                .thenBy { it.nodeId.toString() }
        )
        
        return ConflictResolution(
            conflictId = newOperation.id,
            conflictingOperations = allOperations.map { it.id },
            winningOperation = winner?.id ?: newOperation.id,
            resolution = if (winner?.id == newOperation.id) {
                ConflictResolutionType.ACCEPT
            } else {
                ConflictResolutionType.REJECT
            },
            timestamp = TimeUtils.currentTimeMillis()
        )
    }
    
    private suspend fun persistState(state: CrdtState) {
        try {
            val stateJson = json.encodeToString(state)
            localStorage.store("crdt_state", stateJson)
        } catch (e: Exception) {
            logSyncEvent(SyncEventType.ERROR, "Failed to persist state: ${e.message}")
        }
    }
    
    private fun updateMaterializedState() {
        _materializedState.value = _crdtState.value.materialize()
    }
    
    private fun logSyncEvent(type: SyncEventType, message: String) {
        val event = SyncEvent(
            type = type,
            message = message,
            timestamp = TimeUtils.currentTimeMillis(),
            nodeId = nodeId
        )
        
        val currentEvents = _syncEvents.value
        _syncEvents.value = (currentEvents + event).takeLast(1000) // Keep last 1000 events
        
        println("🔄 CRDT: $message")
    }
}

/**
 * Conflict resolution information
 */
data class ConflictResolution(
    val conflictId: Uuid,
    val conflictingOperations: List<Uuid>,
    val winningOperation: Uuid,
    val resolution: ConflictResolutionType,
    val timestamp: Long
)

enum class ConflictResolutionType {
    ACCEPT,
    REJECT,
    MERGE
}

/**
 * Synchronization result
 */
data class SyncResult(
    val operationsSent: Int,
    val operationsReceived: Int,
    val operationsToSend: List<CrdtOperation>,
    val conflicts: List<ConflictResolution>
)

/**
 * State merge result
 */
data class MergeResult(
    val operationsAdded: Int,
    val finalVersion: Long,
    val conflicts: List<ConflictResolution>
)

/**
 * Sync event for monitoring
 */
data class SyncEvent(
    val type: SyncEventType,
    val message: String,
    val timestamp: Long,
    val nodeId: Uuid
)

enum class SyncEventType {
    INITIALIZED,
    STATE_LOADED,
    LOCAL_OPERATION_APPLIED,
    REMOTE_OPERATIONS_APPLIED,
    PEER_SYNC_COMPLETED,
    STATE_MERGED,
    OPERATIONS_COMPACTED,
    CONFLICT_RESOLVED,
    ERROR
}