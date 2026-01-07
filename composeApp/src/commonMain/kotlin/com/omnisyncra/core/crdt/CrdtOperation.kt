package com.omnisyncra.core.crdt

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable

/**
 * CRDT Operations for distributed state management
 * All operations are commutative, associative, and idempotent
 */
@Serializable
sealed class CrdtOperation {
    @Serializable(with = UuidSerializer::class)
    abstract val id: Uuid
    @Serializable(with = UuidSerializer::class)
    abstract val nodeId: Uuid
    abstract val timestamp: Long
    abstract val vectorClock: VectorClock
    
    /**
     * Device-related operations (add, update, remove devices)
     */
    @Serializable
    data class DeviceUpdate(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid = uuid4(),
        @Serializable(with = UuidSerializer::class)
        override val nodeId: Uuid,
        override val timestamp: Long,
        override val vectorClock: VectorClock,
        @Serializable(with = UuidSerializer::class)
        val deviceId: Uuid,
        val deviceData: String, // JSON serialized device data
        val operationType: DeviceOperationType
    ) : CrdtOperation()
    
    /**
     * Context-related operations (create, update, delete contexts)
     */
    @Serializable
    data class ContextUpdate(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid = uuid4(),
        @Serializable(with = UuidSerializer::class)
        override val nodeId: Uuid,
        override val timestamp: Long,
        override val vectorClock: VectorClock,
        @Serializable(with = UuidSerializer::class)
        val contextId: Uuid,
        val contextData: String, // JSON serialized context data
        val operationType: ContextOperationType
    ) : CrdtOperation()
    
    /**
     * State synchronization operations
     */
    @Serializable
    data class StateSync(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid = uuid4(),
        @Serializable(with = UuidSerializer::class)
        override val nodeId: Uuid,
        override val timestamp: Long,
        override val vectorClock: VectorClock,
        val stateSnapshot: String // JSON serialized state
    ) : CrdtOperation()
    
    /**
     * Document operations for collaborative editing
     */
    @Serializable
    data class DocumentUpdate(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid = uuid4(),
        @Serializable(with = UuidSerializer::class)
        override val nodeId: Uuid,
        override val timestamp: Long,
        override val vectorClock: VectorClock,
        @Serializable(with = UuidSerializer::class)
        val documentId: Uuid,
        val operation: DocumentOperation,
        val position: Int,
        val content: String = ""
    ) : CrdtOperation()
    
    /**
     * Key-value store operations
     */
    @Serializable
    data class KeyValueUpdate(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid = uuid4(),
        @Serializable(with = UuidSerializer::class)
        override val nodeId: Uuid,
        override val timestamp: Long,
        override val vectorClock: VectorClock,
        val key: String,
        val value: String?,
        val operationType: KeyValueOperationType
    ) : CrdtOperation()
}

@Serializable
enum class DeviceOperationType {
    ADD,
    UPDATE,
    REMOVE,
    CONNECT,
    DISCONNECT,
    UPDATE_CAPABILITIES,
    UPDATE_STATUS
}

@Serializable
enum class ContextOperationType {
    CREATE,
    UPDATE,
    DELETE,
    ACTIVATE,
    DEACTIVATE,
    MERGE,
    SPLIT
}

@Serializable
enum class DocumentOperation {
    INSERT,
    DELETE,
    RETAIN,
    FORMAT
}

@Serializable
enum class KeyValueOperationType {
    SET,
    DELETE,
    INCREMENT,
    DECREMENT
}

/**
 * CRDT Data Types for different use cases
 */
@Serializable
sealed class CrdtDataType {
    /**
     * Last-Writer-Wins Register
     */
    @Serializable
    data class LWWRegister(
        val value: String,
        val timestamp: Long,
        @Serializable(with = UuidSerializer::class)
        val nodeId: Uuid
    ) : CrdtDataType()
    
    /**
     * Grow-Only Set
     */
    @Serializable
    data class GSet(
        val elements: Set<String>
    ) : CrdtDataType()
    
    /**
     * Two-Phase Set (Add/Remove)
     */
    @Serializable
    data class TwoPhaseSet(
        val added: Set<String>,
        val removed: Set<String>
    ) : CrdtDataType() {
        val elements: Set<String>
            get() = added - removed
    }
    
    /**
     * PN-Counter (Increment/Decrement)
     */
    @Serializable
    data class PNCounter(
        val increments: Map<@Serializable(with = UuidSerializer::class) Uuid, Long>,
        val decrements: Map<@Serializable(with = UuidSerializer::class) Uuid, Long>
    ) : CrdtDataType() {
        val value: Long
            get() = increments.values.sum() - decrements.values.sum()
    }
    
    /**
     * OR-Set (Observed-Remove Set)
     */
    @Serializable
    data class ORSet(
        val elements: Map<String, Set<@Serializable(with = UuidSerializer::class) Uuid>>,
        val removed: Set<@Serializable(with = UuidSerializer::class) Uuid>
    ) : CrdtDataType() {
        val currentElements: Set<String>
            get() = elements.filterValues { tags -> tags.any { it !in removed } }.keys
    }
}