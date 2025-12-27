package com.omnisyncra.core.crdt

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import kotlinx.serialization.Serializable

@Serializable
sealed class CrdtOperation {
    abstract val id: Uuid
    abstract val nodeId: Uuid
    abstract val timestamp: Long
    abstract val vectorClock: VectorClock
    
    @Serializable
    data class DeviceUpdate(
        override val id: Uuid = uuid4(),
        override val nodeId: Uuid,
        override val timestamp: Long,
        override val vectorClock: VectorClock,
        val deviceId: Uuid,
        val deviceData: String, // JSON serialized device data
        val operationType: DeviceOperationType
    ) : CrdtOperation()
    
    @Serializable
    data class ContextUpdate(
        override val id: Uuid = uuid4(),
        override val nodeId: Uuid,
        override val timestamp: Long,
        override val vectorClock: VectorClock,
        val contextId: Uuid,
        val contextData: String, // JSON serialized context data
        val operationType: ContextOperationType
    ) : CrdtOperation()
    
    @Serializable
    data class StateSync(
        override val id: Uuid = uuid4(),
        override val nodeId: Uuid,
        override val timestamp: Long,
        override val vectorClock: VectorClock,
        val stateSnapshot: String // JSON serialized state
    ) : CrdtOperation()
}

@Serializable
enum class DeviceOperationType {
    ADD,
    UPDATE,
    REMOVE,
    CONNECT,
    DISCONNECT
}

@Serializable
enum class ContextOperationType {
    CREATE,
    UPDATE,
    DELETE,
    ACTIVATE,
    DEACTIVATE
}