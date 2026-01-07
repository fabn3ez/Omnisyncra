package com.omnisyncra.core.crdt

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.serialization.UuidSerializer
import com.omnisyncra.core.platform.TimeUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * CRDT State container managing all distributed operations
 * Ensures eventual consistency across all nodes
 */
@Serializable
data class CrdtState(
    @Serializable(with = UuidSerializer::class)
    val nodeId: Uuid,
    val vectorClock: VectorClock,
    val operations: List<CrdtOperation> = emptyList(),
    val version: Long = 0L,
    val lastUpdated: Long = TimeUtils.currentTimeMillis(),
    
    // CRDT Data Structures
    val lwwRegisters: Map<String, CrdtDataType.LWWRegister> = emptyMap(),
    val gSets: Map<String, CrdtDataType.GSet> = emptyMap(),
    val twoPhaSets: Map<String, CrdtDataType.TwoPhaseSet> = emptyMap(),
    val pnCounters: Map<String, CrdtDataType.PNCounter> = emptyMap(),
    val orSets: Map<String, CrdtDataType.ORSet> = emptyMap(),
    
    // Application-specific state
    val devices: Map<@Serializable(with = UuidSerializer::class) Uuid, String> = emptyMap(),
    val contexts: Map<@Serializable(with = UuidSerializer::class) Uuid, String> = emptyMap(),
    val documents: Map<@Serializable(with = UuidSerializer::class) Uuid, String> = emptyMap(),
    val keyValueStore: Map<String, String> = emptyMap()
) {
    
    /**
     * Apply a CRDT operation to this state
     */
    fun applyOperation(operation: CrdtOperation): CrdtState {
        // Check if operation already exists (idempotency)
        if (operations.any { it.id == operation.id }) {
            return this
        }
        
        val newVectorClock = vectorClock.merge(operation.vectorClock)
        val newOperations = operations + operation
        val newVersion = version + 1
        
        return when (operation) {
            is CrdtOperation.DeviceUpdate -> applyDeviceUpdate(operation, newVectorClock, newOperations, newVersion)
            is CrdtOperation.ContextUpdate -> applyContextUpdate(operation, newVectorClock, newOperations, newVersion)
            is CrdtOperation.StateSync -> applyStateSync(operation, newVectorClock, newOperations, newVersion)
            is CrdtOperation.DocumentUpdate -> applyDocumentUpdate(operation, newVectorClock, newOperations, newVersion)
            is CrdtOperation.KeyValueUpdate -> applyKeyValueUpdate(operation, newVectorClock, newOperations, newVersion)
        }
    }
    
    private fun applyDeviceUpdate(
        operation: CrdtOperation.DeviceUpdate,
        newVectorClock: VectorClock,
        newOperations: List<CrdtOperation>,
        newVersion: Long
    ): CrdtState {
        val newDevices = when (operation.operationType) {
            DeviceOperationType.ADD, DeviceOperationType.UPDATE, 
            DeviceOperationType.CONNECT, DeviceOperationType.DISCONNECT,
            DeviceOperationType.UPDATE_CAPABILITIES, DeviceOperationType.UPDATE_STATUS -> {
                devices + (operation.deviceId to operation.deviceData)
            }
            DeviceOperationType.REMOVE -> {
                devices - operation.deviceId
            }
        }
        
        return copy(
            vectorClock = newVectorClock,
            operations = newOperations,
            version = newVersion,
            lastUpdated = TimeUtils.currentTimeMillis(),
            devices = newDevices
        )
    }
    
    private fun applyContextUpdate(
        operation: CrdtOperation.ContextUpdate,
        newVectorClock: VectorClock,
        newOperations: List<CrdtOperation>,
        newVersion: Long
    ): CrdtState {
        val newContexts = when (operation.operationType) {
            ContextOperationType.CREATE, ContextOperationType.UPDATE,
            ContextOperationType.ACTIVATE, ContextOperationType.DEACTIVATE,
            ContextOperationType.MERGE, ContextOperationType.SPLIT -> {
                contexts + (operation.contextId to operation.contextData)
            }
            ContextOperationType.DELETE -> {
                contexts - operation.contextId
            }
        }
        
        return copy(
            vectorClock = newVectorClock,
            operations = newOperations,
            version = newVersion,
            lastUpdated = TimeUtils.currentTimeMillis(),
            contexts = newContexts
        )
    }
    
    private fun applyStateSync(
        operation: CrdtOperation.StateSync,
        newVectorClock: VectorClock,
        newOperations: List<CrdtOperation>,
        newVersion: Long
    ): CrdtState {
        // State sync operations update the entire state snapshot
        return copy(
            vectorClock = newVectorClock,
            operations = newOperations,
            version = newVersion,
            lastUpdated = TimeUtils.currentTimeMillis()
        )
    }
    
    private fun applyDocumentUpdate(
        operation: CrdtOperation.DocumentUpdate,
        newVectorClock: VectorClock,
        newOperations: List<CrdtOperation>,
        newVersion: Long
    ): CrdtState {
        val currentDoc = documents[operation.documentId] ?: ""
        val newDoc = when (operation.operation) {
            DocumentOperation.INSERT -> {
                if (operation.position <= currentDoc.length) {
                    currentDoc.substring(0, operation.position) + 
                    operation.content + 
                    currentDoc.substring(operation.position)
                } else {
                    currentDoc + operation.content
                }
            }
            DocumentOperation.DELETE -> {
                if (operation.position < currentDoc.length) {
                    val endPos = minOf(operation.position + operation.content.length, currentDoc.length)
                    currentDoc.substring(0, operation.position) + currentDoc.substring(endPos)
                } else {
                    currentDoc
                }
            }
            DocumentOperation.RETAIN -> currentDoc
            DocumentOperation.FORMAT -> currentDoc // Format operations don't change content
        }
        
        return copy(
            vectorClock = newVectorClock,
            operations = newOperations,
            version = newVersion,
            lastUpdated = TimeUtils.currentTimeMillis(),
            documents = documents + (operation.documentId to newDoc)
        )
    }
    
    private fun applyKeyValueUpdate(
        operation: CrdtOperation.KeyValueUpdate,
        newVectorClock: VectorClock,
        newOperations: List<CrdtOperation>,
        newVersion: Long
    ): CrdtState {
        val newKeyValueStore = when (operation.operationType) {
            KeyValueOperationType.SET -> {
                operation.value?.let { keyValueStore + (operation.key to it) } ?: keyValueStore
            }
            KeyValueOperationType.DELETE -> {
                keyValueStore - operation.key
            }
            KeyValueOperationType.INCREMENT -> {
                val currentValue = keyValueStore[operation.key]?.toLongOrNull() ?: 0L
                keyValueStore + (operation.key to (currentValue + 1).toString())
            }
            KeyValueOperationType.DECREMENT -> {
                val currentValue = keyValueStore[operation.key]?.toLongOrNull() ?: 0L
                keyValueStore + (operation.key to (currentValue - 1).toString())
            }
        }
        
        return copy(
            vectorClock = newVectorClock,
            operations = newOperations,
            version = newVersion,
            lastUpdated = TimeUtils.currentTimeMillis(),
            keyValueStore = newKeyValueStore
        )
    }
    
    /**
     * Get operations that happened after the given vector clock
     */
    fun getOperationsSince(sinceVectorClock: VectorClock): List<CrdtOperation> {
        return operations.filter { operation ->
            sinceVectorClock.happensBefore(operation.vectorClock) || 
            sinceVectorClock.isConcurrentWith(operation.vectorClock)
        }
    }
    
    /**
     * Compact operations by removing redundant ones
     */
    fun compactOperations(): CrdtState {
        // Keep only the latest operation for each key/entity
        val compactedOps = mutableListOf<CrdtOperation>()
        val seenDevices = mutableSetOf<Uuid>()
        val seenContexts = mutableSetOf<Uuid>()
        val seenDocuments = mutableSetOf<Uuid>()
        val seenKeys = mutableSetOf<String>()
        
        // Process operations in reverse chronological order
        operations.sortedByDescending { it.timestamp }.forEach { operation ->
            when (operation) {
                is CrdtOperation.DeviceUpdate -> {
                    if (operation.deviceId !in seenDevices) {
                        compactedOps.add(operation)
                        seenDevices.add(operation.deviceId)
                    }
                }
                is CrdtOperation.ContextUpdate -> {
                    if (operation.contextId !in seenContexts) {
                        compactedOps.add(operation)
                        seenContexts.add(operation.contextId)
                    }
                }
                is CrdtOperation.DocumentUpdate -> {
                    if (operation.documentId !in seenDocuments) {
                        compactedOps.add(operation)
                        seenDocuments.add(operation.documentId)
                    }
                }
                is CrdtOperation.KeyValueUpdate -> {
                    if (operation.key !in seenKeys) {
                        compactedOps.add(operation)
                        seenKeys.add(operation.key)
                    }
                }
                is CrdtOperation.StateSync -> {
                    compactedOps.add(operation) // Always keep state sync operations
                }
            }
        }
        
        return copy(operations = compactedOps.reversed())
    }
    
    /**
     * Merge with another CRDT state
     */
    fun merge(other: CrdtState): CrdtState {
        val mergedVectorClock = vectorClock.merge(other.vectorClock)
        
        // Merge operations (remove duplicates)
        val allOperations = (operations + other.operations).distinctBy { it.id }
            .sortedBy { it.timestamp }
        
        // Apply all operations to get the final state
        var mergedState = copy(
            vectorClock = mergedVectorClock,
            operations = emptyList(),
            version = maxOf(version, other.version)
        )
        
        allOperations.forEach { operation ->
            mergedState = mergedState.applyOperation(operation)
        }
        
        return mergedState
    }
    
    /**
     * Create a materialized view of the current state
     */
    fun materialize(): MaterializedState {
        return MaterializedState(
            nodeId = nodeId,
            version = version,
            lastUpdated = lastUpdated,
            deviceCount = devices.size,
            contextCount = contexts.size,
            documentCount = documents.size,
            keyValueCount = keyValueStore.size,
            operationCount = operations.size,
            vectorClockSummary = vectorClock.toString()
        )
    }
}

/**
 * Materialized view of CRDT state for UI consumption
 */
@Serializable
data class MaterializedState(
    @Serializable(with = UuidSerializer::class)
    val nodeId: Uuid,
    val version: Long,
    val lastUpdated: Long,
    val deviceCount: Int,
    val contextCount: Int,
    val documentCount: Int,
    val keyValueCount: Int,
    val operationCount: Int,
    val vectorClockSummary: String
)