package com.omnisyncra.core.state

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.crdt.*
import com.omnisyncra.core.domain.OmnisyncraState
import com.omnisyncra.core.storage.LocalStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

class DistributedStateManager(
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
    
    private val _omnisyncraState = MutableStateFlow<OmnisyncraState?>(null)
    val omnisyncraState: StateFlow<OmnisyncraState?> = _omnisyncraState.asStateFlow()
    
    private val _conflictResolutions = MutableStateFlow<List<ConflictResolution>>(emptyList())
    val conflictResolutions: StateFlow<List<ConflictResolution>> = _conflictResolutions.asStateFlow()
    
    suspend fun initialize() {
        // Load persisted state
        val persistedState = localStorage.loadCrdtState()
        if (persistedState != null) {
            _crdtState.value = persistedState
            materializeState()
        }
    }
    
    suspend fun applyLocalOperation(operation: CrdtOperation) {
        val currentState = _crdtState.value
        val incrementedClock = currentState.vectorClock.increment(nodeId)
        
        val timestampedOperation = when (operation) {
            is CrdtOperation.DeviceUpdate -> operation.copy(
                nodeId = nodeId,
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                vectorClock = incrementedClock
            )
            is CrdtOperation.ContextUpdate -> operation.copy(
                nodeId = nodeId,
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                vectorClock = incrementedClock
            )
            is CrdtOperation.StateSync -> operation.copy(
                nodeId = nodeId,
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                vectorClock = incrementedClock
            )
        }
        
        val newState = currentState.applyOperation(timestampedOperation)
        _crdtState.value = newState
        
        // Persist state
        localStorage.saveCrdtState(newState)
        
        // Materialize the new application state
        materializeState()
    }
    
    suspend fun applyRemoteOperations(operations: List<CrdtOperation>) {
        var currentState = _crdtState.value
        val conflicts = mutableListOf<ConflictResolution>()
        
        operations.forEach { operation ->
            // Check for conflicts
            val conflictingOps = currentState.operations.filter { existing ->
                existing.timestamp == operation.timestamp && 
                existing.nodeId != operation.nodeId &&
                areConflicting(existing, operation)
            }
            
            if (conflictingOps.isNotEmpty()) {
                val resolution = resolveConflict(operation, conflictingOps)
                conflicts.add(resolution)
                
                if (resolution.resolution == ConflictResolutionType.REJECT) {
                    return@forEach // Skip this operation
                }
            }
            
            currentState = currentState.applyOperation(operation)
        }
        
        _crdtState.value = currentState
        _conflictResolutions.value = _conflictResolutions.value + conflicts
        
        // Persist state
        localStorage.saveCrdtState(currentState)
        
        // Materialize the new application state
        materializeState()
    }
    
    suspend fun syncWithPeer(peerState: CrdtState): List<CrdtOperation> {
        val currentState = _crdtState.value
        
        // Get operations that peer doesn't have
        val operationsToSend = currentState.getOperationsSince(peerState.vectorClock)
        
        // Apply peer's operations that we don't have
        val operationsToApply = peerState.getOperationsSince(currentState.vectorClock)
        if (operationsToApply.isNotEmpty()) {
            applyRemoteOperations(operationsToApply)
        }
        
        return operationsToSend
    }
    
    private fun materializeState() {
        val currentCrdtState = _crdtState.value
        val materializedState = currentCrdtState.materializeState(json)
        _omnisyncraState.value = materializedState
    }
    
    private fun areConflicting(op1: CrdtOperation, op2: CrdtOperation): Boolean {
        return when {
            op1 is CrdtOperation.DeviceUpdate && op2 is CrdtOperation.DeviceUpdate ->
                op1.deviceId == op2.deviceId
            op1 is CrdtOperation.ContextUpdate && op2 is CrdtOperation.ContextUpdate ->
                op1.contextId == op2.contextId
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
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
    }
    
    suspend fun compactState() {
        val currentState = _crdtState.value
        val compactedState = currentState.compactOperations()
        _crdtState.value = compactedState
        localStorage.saveCrdtState(compactedState)
    }
}

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