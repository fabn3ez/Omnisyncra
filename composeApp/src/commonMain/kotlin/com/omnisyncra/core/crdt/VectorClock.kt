package com.omnisyncra.core.crdt

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
data class VectorClock(
    val clocks: Map<String, Long> = emptyMap()
) {
    fun increment(nodeId: Uuid): VectorClock {
        val nodeIdStr = nodeId.toString()
        val currentValue = clocks[nodeIdStr] ?: 0L
        return VectorClock(clocks + (nodeIdStr to currentValue + 1))
    }
    
    fun merge(other: VectorClock): VectorClock {
        val allNodes = clocks.keys + other.clocks.keys
        val mergedClocks = allNodes.associateWith { nodeId ->
            maxOf(clocks[nodeId] ?: 0L, other.clocks[nodeId] ?: 0L)
        }
        return VectorClock(mergedClocks)
    }
    
    fun dominates(other: VectorClock): Boolean {
        // This vector clock dominates other if all its values are >= other's values
        // and at least one value is strictly greater
        var hasGreater = false
        
        for ((nodeId, value) in other.clocks) {
            val ourValue = clocks[nodeId] ?: 0L
            if (ourValue < value) {
                return false
            }
            if (ourValue > value) {
                hasGreater = true
            }
        }
        
        // Check if we have any nodes that other doesn't have
        for ((nodeId, value) in clocks) {
            if (nodeId !in other.clocks && value > 0L) {
                hasGreater = true
            }
        }
        
        return hasGreater
    }
    
    fun happensBefore(other: VectorClock): Boolean {
        return other.dominates(this)
    }
    
    fun isConcurrent(other: VectorClock): Boolean {
        return !dominates(other) && !other.dominates(this)
    }
}