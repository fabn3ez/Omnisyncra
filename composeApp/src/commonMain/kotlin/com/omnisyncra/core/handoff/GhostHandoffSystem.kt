package com.omnisyncra.core.handoff

import kotlinx.coroutines.flow.StateFlow
import com.omnisyncra.core.platform.TimeUtils

/**
 * Ghost Handoff System Interface
 * Enables seamless state transfer with mental context preservation
 */
interface GhostHandoffSystem {
    /**
     * Current handoff system status
     */
    val status: StateFlow<HandoffStatus>
    
    /**
     * Initialize the handoff system
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * Initiate a handoff to another device
     */
    suspend fun initiateHandoff(targetDeviceId: String, context: HandoffContext): Result<HandoffSession>
    
    /**
     * Accept an incoming handoff
     */
    suspend fun acceptHandoff(sessionId: String): Result<HandoffData>
    
    /**
     * Reject an incoming handoff
     */
    suspend fun rejectHandoff(sessionId: String, reason: String): Result<Unit>
    
    /**
     * Get available target devices for handoff
     */
    suspend fun getAvailableDevices(): Result<List<HandoffDevice>>
    
    /**
     * Preserve mental context for handoff
     */
    suspend fun preserveMentalContext(context: MentalContext): Result<PreservedContext>
    
    /**
     * Reconstruct activity from handoff data
     */
    suspend fun reconstructActivity(handoffData: HandoffData): Result<ReconstructedActivity>
    
    /**
     * Shutdown the handoff system
     */
    suspend fun shutdown()
}

/**
 * Handoff System Status
 */
enum class HandoffStatus {
    INITIALIZING,
    READY,
    HANDOFF_IN_PROGRESS,
    RECEIVING_HANDOFF,
    ERROR,
    SHUTDOWN
}

/**
 * Handoff Context containing current state and activity
 */
data class HandoffContext(
    val currentActivity: String,
    val applicationState: Map<String, Any>,
    val userIntent: String,
    val mentalContext: MentalContext,
    val priority: HandoffPriority = HandoffPriority.NORMAL,
    val timestamp: Long = TimeUtils.currentTimeMillis()
)

/**
 * Mental Context - preserves user's cognitive state
 */
data class MentalContext(
    val focusArea: String,
    val workflowStage: String,
    val cognitiveLoad: Float, // 0.0 to 1.0
    val attentionPoints: List<AttentionPoint>,
    val recentActions: List<String>,
    val nextIntendedActions: List<String>,
    val emotionalState: EmotionalState = EmotionalState.NEUTRAL
)

/**
 * Attention Point - specific areas of user focus
 */
data class AttentionPoint(
    val element: String,
    val importance: Float, // 0.0 to 1.0
    val timeSpent: Long, // milliseconds
    val interactionType: InteractionType
)

/**
 * Types of user interactions
 */
enum class InteractionType {
    VIEWING,
    EDITING,
    SELECTING,
    SCROLLING,
    SEARCHING,
    CREATING,
    DELETING
}

/**
 * User's emotional state during handoff
 */
enum class EmotionalState {
    FOCUSED,
    FRUSTRATED,
    EXCITED,
    CONFUSED,
    CONFIDENT,
    NEUTRAL
}

/**
 * Handoff Priority levels
 */
enum class HandoffPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Handoff Session information
 */
data class HandoffSession(
    val sessionId: String,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val context: HandoffContext,
    val status: SessionStatus,
    val createdAt: Long = TimeUtils.currentTimeMillis(),
    val expiresAt: Long = TimeUtils.currentTimeMillis() + 300000L // 5 minutes
)

/**
 * Session Status
 */
enum class SessionStatus {
    INITIATED,
    PENDING_ACCEPTANCE,
    ACCEPTED,
    REJECTED,
    COMPLETED,
    EXPIRED,
    FAILED
}

/**
 * Available device for handoff
 */
data class HandoffDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val capabilities: List<String>,
    val proximity: DeviceProximity,
    val batteryLevel: Float?,
    val networkLatency: Long,
    val isAvailable: Boolean,
    val lastSeen: Long = TimeUtils.currentTimeMillis()
)

/**
 * Device proximity levels
 */
enum class DeviceProximity {
    SAME_ROOM,    // BLE/WiFi Direct
    SAME_NETWORK, // Local network
    REMOTE,       // Internet
    UNKNOWN
}

/**
 * Handoff Data package
 */
data class HandoffData(
    val sessionId: String,
    val context: HandoffContext,
    val preservedContext: PreservedContext,
    val applicationData: ByteArray,
    val metadata: Map<String, String>,
    val checksum: String,
    val timestamp: Long = TimeUtils.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as HandoffData
        
        if (sessionId != other.sessionId) return false
        if (context != other.context) return false
        if (preservedContext != other.preservedContext) return false
        if (!applicationData.contentEquals(other.applicationData)) return false
        if (metadata != other.metadata) return false
        if (checksum != other.checksum) return false
        if (timestamp != other.timestamp) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + context.hashCode()
        result = 31 * result + preservedContext.hashCode()
        result = 31 * result + applicationData.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + checksum.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Preserved Context - mental state preservation
 */
data class PreservedContext(
    val mentalSnapshot: MentalSnapshot,
    val contextualCues: List<ContextualCue>,
    val workflowState: WorkflowState,
    val environmentalFactors: Map<String, String>,
    val preservationQuality: Float // 0.0 to 1.0
)

/**
 * Mental Snapshot - captured cognitive state
 */
data class MentalSnapshot(
    val primaryFocus: String,
    val secondaryFoci: List<String>,
    val cognitiveMap: Map<String, Float>, // concept -> importance
    val workingMemory: List<String>,
    val longTermReferences: List<String>,
    val mentalModel: String // description of user's understanding
)

/**
 * Contextual Cue - helps reconstruct mental state
 */
data class ContextualCue(
    val type: CueType,
    val content: String,
    val importance: Float,
    val associatedElements: List<String>
)

/**
 * Types of contextual cues
 */
enum class CueType {
    VISUAL,      // UI elements, colors, positions
    SEMANTIC,    // Meaning, concepts, relationships
    TEMPORAL,    // Time-based patterns, sequences
    SPATIAL,     // Layout, organization, hierarchy
    BEHAVIORAL   // User patterns, habits, preferences
}

/**
 * Workflow State - current position in user's workflow
 */
data class WorkflowState(
    val currentStage: String,
    val completedStages: List<String>,
    val pendingStages: List<String>,
    val branchingPoints: List<String>,
    val progressPercentage: Float,
    val estimatedTimeRemaining: Long?
)

/**
 * Reconstructed Activity - rebuilt user context
 */
data class ReconstructedActivity(
    val activity: String,
    val reconstructedState: Map<String, Any>,
    val mentalContext: MentalContext,
    val reconstructionQuality: Float, // 0.0 to 1.0
    val missingElements: List<String>,
    val recommendations: List<String>,
    val timestamp: Long = TimeUtils.currentTimeMillis()
)

/**
 * Handoff Event for monitoring and logging
 */
data class HandoffEvent(
    val type: HandoffEventType,
    val sessionId: String,
    val deviceId: String?,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = TimeUtils.currentTimeMillis()
)

/**
 * Types of handoff events
 */
enum class HandoffEventType {
    HANDOFF_INITIATED,
    HANDOFF_ACCEPTED,
    HANDOFF_REJECTED,
    HANDOFF_COMPLETED,
    HANDOFF_FAILED,
    CONTEXT_PRESERVED,
    CONTEXT_RECONSTRUCTED,
    DEVICE_DISCOVERED,
    DEVICE_LOST,
    SESSION_EXPIRED
}