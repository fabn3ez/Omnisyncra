package com.omnisyncra.core.state

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.crdt.CrdtState
import com.omnisyncra.core.crdt.VectorClock
import com.omnisyncra.core.domain.*
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.storage.LocalStorage
import kotlinx.datetime.Clock

class StateRecovery(
    private val platform: Platform,
    private val localStorage: LocalStorage
) {
    suspend fun recoverOrCreateInitialState(nodeId: Uuid): CrdtState {
        // Try to recover from storage
        val recoveredState = localStorage.loadCrdtState()
        if (recoveredState != null) {
            return recoveredState.copy(
                // Update node ID in case device changed
                nodeId = nodeId
            )
        }
        
        // Create fresh initial state
        return createInitialState(nodeId)
    }
    
    private fun createInitialState(nodeId: Uuid): CrdtState {
        val initialDevice = Device(
            id = nodeId,
            name = platform.getDeviceName(),
            type = platform.deviceType,
            capabilities = platform.capabilities,
            lastSeen = Clock.System.now().toEpochMilliseconds()
        )
        
        val initialContext = OmnisyncraContext(
            name = "Welcome to Omnisyncra",
            type = ContextType.CUSTOM,
            metadata = ContextMetadata(
                tags = listOf("welcome", "initial"),
                aiSummary = "Initial context for new Omnisyncra installation"
            ),
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastModified = Clock.System.now().toEpochMilliseconds(),
            deviceId = nodeId
        )
        
        val contextGraph = ContextGraph(
            contexts = mapOf(initialContext.id to initialContext),
            activeContext = initialContext.id,
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        )
        
        val deviceMesh = DeviceMesh(
            localDevice = initialDevice
        )
        
        val screenDimensions = platform.getScreenDimensions()
        val uiState = UIState(
            currentMode = UIMode.STANDALONE,
            adaptiveLayout = AdaptiveLayout(
                screenConfiguration = ScreenConfiguration(
                    width = screenDimensions.first,
                    height = screenDimensions.second,
                    density = 1.0f,
                    orientation = if (screenDimensions.first > screenDimensions.second) {
                        Orientation.LANDSCAPE
                    } else {
                        Orientation.PORTRAIT
                    }
                ),
                availableSpace = LayoutSpace(
                    primary = 1.0f,
                    secondary = 0.0f,
                    tertiary = 0.0f
                ),
                contentPriority = listOf(
                    ContentType.MAIN_CONTENT,
                    ContentType.NAVIGATION,
                    ContentType.STATUS_INFO
                )
            ),
            theme = ThemeState(
                isDarkMode = true,
                accentColor = "#6366F1",
                proximityIndicatorStyle = ProximityStyle.AMBIENT
            )
        )
        
        return CrdtState(
            nodeId = nodeId,
            vectorClock = VectorClock(mapOf(nodeId to 1L)),
            operations = emptyList(),
            lastSyncTimestamp = Clock.System.now().toEpochMilliseconds()
        )
    }
    
    suspend fun validateStateIntegrity(state: CrdtState): StateValidationResult {
        val issues = mutableListOf<StateIssue>()
        
        // Check vector clock consistency
        if (state.vectorClock.clocks.isEmpty()) {
            issues.add(StateIssue.EMPTY_VECTOR_CLOCK)
        }
        
        // Check operation ordering
        val operations = state.operations.sortedBy { it.timestamp }
        for (i in 1 until operations.size) {
            if (operations[i].timestamp < operations[i-1].timestamp) {
                issues.add(StateIssue.OPERATION_ORDERING_VIOLATION)
                break
            }
        }
        
        // Check for duplicate operations
        val operationIds = state.operations.map { it.id }
        if (operationIds.size != operationIds.distinct().size) {
            issues.add(StateIssue.DUPLICATE_OPERATIONS)
        }
        
        return StateValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            canRecover = issues.all { it.isRecoverable }
        )
    }
    
    suspend fun repairState(state: CrdtState): CrdtState {
        var repairedState = state
        
        // Remove duplicate operations
        val uniqueOperations = state.operations.distinctBy { it.id }
        repairedState = repairedState.copy(operations = uniqueOperations)
        
        // Fix operation ordering
        val sortedOperations = repairedState.operations.sortedBy { it.timestamp }
        repairedState = repairedState.copy(operations = sortedOperations)
        
        // Rebuild vector clock if empty
        if (repairedState.vectorClock.clocks.isEmpty()) {
            val nodeIds = repairedState.operations.map { it.nodeId }.distinct()
            val clockMap = nodeIds.associateWith { nodeId ->
                repairedState.operations
                    .filter { it.nodeId == nodeId }
                    .maxOfOrNull { it.vectorClock.clocks[nodeId] ?: 0L } ?: 0L
            }
            repairedState = repairedState.copy(
                vectorClock = VectorClock(clockMap)
            )
        }
        
        return repairedState
    }
}

data class StateValidationResult(
    val isValid: Boolean,
    val issues: List<StateIssue>,
    val canRecover: Boolean
)

enum class StateIssue(val isRecoverable: Boolean) {
    EMPTY_VECTOR_CLOCK(true),
    OPERATION_ORDERING_VIOLATION(true),
    DUPLICATE_OPERATIONS(true),
    CORRUPTED_OPERATION_DATA(false),
    MISSING_REQUIRED_FIELDS(false)
}