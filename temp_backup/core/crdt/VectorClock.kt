package com.omnisyncra.core.crdt

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable

@Serializable
data class VectorClock(
    val clocks: Map<@Serializable(with = UuidSerializer::class) Uuid, Long> = emptyMap()
) {
    fun increment(nodeId: Uuid): VectorClock {
        val currentValue = clocks[nodeId] ?: 0L
        return copy(clocks = clocks + (nodeId to currentValue + 1))
    }
    
    fun merge(other: VectorClock): VectorClock {
        val allNodes = clocks.keys + other.clocks.keys
        val mergedClocks = allNodes.associateWith { nodeId ->
            maxOf(clocks[nodeId] ?: 0L, other.clocks[nodeId] ?: 0L)
        }
        return VectorClock(mergedClocks)
    }
    
    fun happensBefore(other: VectorClock): Boolean {
        return clocks.all { (nodeId, timestamp) ->
            timestamp <= (other.clocks[nodeId] ?: 0L)
        } && this != other
    }
    
    fun isConcurrent(other: VectorClock): Boolean {
        return !happensBefore(other) && !other.happensBefore(this)
    }
    
    fun dominates(other: VectorClock): Boolean {
        return clocks.all { (nodeId, timestamp) ->
            timestamp >= (other.clocks[nodeId] ?: 0L)
        } && clocks.any { (nodeId, timestamp) ->
            timestamp > (other.clocks[nodeId] ?: 0L)
        }
    }
}