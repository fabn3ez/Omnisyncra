package com.omnisyncra.core.crdt

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.OmnisyncraState
import com.omnisyncra.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CrdtState(
    @Serializable(with = UuidSerializer::class)
    val nodeId: Uuid,
    val vectorClock: VectorClock,
    val operations: List<CrdtOperation> = emptyList(),
    val lastSyncTimestamp: Long = 0L
) {
    fun applyOperation(operation: CrdtOperation): CrdtState {
        // Check if operation is already applied
        if (operations.any { it.id == operation.id }) {
            return this
        }
        
        // Merge vector clocks
        val newVectorClock = vectorClock.merge(operation.vectorClock)
        
        // Add operation to log
        val newOperations = (operations + operation).sortedBy { it.timestamp }
        
        return copy(
            vectorClock = newVectorClock,
            operations = newOperations
        )
    }
    
    fun getOperationsSince(sinceVectorClock: VectorClock): List<CrdtOperation> {
        return operations.filter { operation ->
            !sinceVectorClock.dominates(operation.vectorClock)
        }
    }
    
    fun compactOperations(keepRecentCount: Int = 1000): CrdtState {
        // Keep only recent operations to prevent unbounded growth
        val recentOperations = operations.takeLast(keepRecentCount)
        return copy(operations = recentOperations)
    }
    
    fun materializeState(json: Json): OmnisyncraState? {
        return try {
            // Apply operations in chronological order to build current state
            val stateOperations = operations.filterIsInstance<CrdtOperation.StateSync>()
                .sortedBy { it.timestamp }
            
            stateOperations.lastOrNull()?.let { lastSync ->
                json.decodeFromString<OmnisyncraState>(lastSync.stateSnapshot)
            }
        } catch (e: Exception) {
            null
        }
    }
}