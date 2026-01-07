package com.omnisyncra.core.crdt

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable

/**
 * Vector Clock implementation for CRDT operations
 * Provides causal ordering and conflict detection
 */
@Serializable
data class VectorClock(
    val clocks: Map<@Serializable(with = UuidSerializer::class) Uuid, Long> = emptyMap()
) {
    /**
     * Increment the clock for a specific node
     */
    fun increment(nodeId: Uuid): VectorClock {
        val currentValue = clocks[nodeId] ?: 0L
        return copy(clocks = clocks + (nodeId to currentValue + 1))
    }
    
    /**
     * Merge with another vector clock (take maximum of each node)
     */
    fun merge(other: VectorClock): VectorClock {
        val allNodes = clocks.keys + other.clocks.keys
        val mergedClocks = allNodes.associateWith { nodeId ->
            maxOf(clocks[nodeId] ?: 0L, other.clocks[nodeId] ?: 0L)
        }
        return VectorClock(mergedClocks)
    }
    
    /**
     * Check if this clock happens before another clock
     */
    fun happensBefore(other: VectorClock): Boolean {
        val allNodes = clocks.keys + other.clocks.keys
        var hasStrictlyLess = false
        
        for (nodeId in allNodes) {
            val thisValue = clocks[nodeId] ?: 0L
            val otherValue = other.clocks[nodeId] ?: 0L
            
            if (thisValue > otherValue) {
                return false
            }
            if (thisValue < otherValue) {
                hasStrictlyLess = true
            }
        }
        
        return hasStrictlyLess
    }
    
    /**
     * Check if this clock is concurrent with another clock
     */
    fun isConcurrentWith(other: VectorClock): Boolean {
        return !happensBefore(other) && !other.happensBefore(this) && this != other
    }
    
    /**
     * Get the maximum timestamp across all nodes
     */
    fun maxTimestamp(): Long {
        return clocks.values.maxOrNull() ?: 0L
    }
    
    /**
     * Get operations that happened after this vector clock
     */
    fun getOperationsSince(operations: List<CrdtOperation>): List<CrdtOperation> {
        return operations.filter { operation ->
            val operationClock = operation.vectorClock
            this.happensBefore(operationClock) || this.isConcurrentWith(operationClock)
        }
    }
    
    override fun toString(): String {
        return clocks.entries.joinToString(", ") { "${it.key}:${it.value}" }
    }
}