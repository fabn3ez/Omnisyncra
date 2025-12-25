package com.omnisyncra.core.handoff

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.time.Duration

expect fun getCurrentTimeMillis(): Long

@Serializable
data class MentalContext(
    val currentTask: String,
    val focusLevel: Float, // 0.0 to 1.0
    val cognitiveLoad: Float, // 0.0 to 1.0
    val workingMemory: List<String>,
    val recentActions: List<UserAction>,
    val environmentalFactors: Map<String, String>,
    val timestamp: Long = getCurrentTimeMillis()
)

@Serializable
data class UserAction(
    val type: ActionType,
    val target: String,
    val context: String,
    val timestamp: Long,
    val duration: Long = 0L
)

enum class ActionType {
    SCROLL, CLICK, TYPE, SWIPE, ZOOM, ROTATE, VOICE_COMMAND, 
    GESTURE, EYE_MOVEMENT, PAUSE, CONTEXT_SWITCH, MULTITASK
}

@Serializable
data class ApplicationState(
    val appId: String,
    val windowState: WindowState,
    val dataState: Map<String, String>,
    val uiState: Map<String, String>,
    val scrollPositions: Map<String, Float>,
    val formData: Map<String, String>,
    val selectionState: SelectionState?,
    val timestamp: Long = getCurrentTimeMillis()
)

@Serializable
data class WindowState(
    val isVisible: Boolean,
    val position: Pair<Float, Float>,
    val size: Pair<Float, Float>,
    val zIndex: Int,
    val isMinimized: Boolean,
    val isMaximized: Boolean
)

@Serializable
data class SelectionState(
    val selectedText: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val elementId: String
)

@Serializable
data class DeviceContext(
    val deviceId: String,
    val deviceType: String,
    val screenSize: Pair<Float, Float>,
    val inputMethods: List<String>,
    val capabilities: List<String>,
    val batteryLevel: Float,
    val networkQuality: Float
)

@Serializable
data class HandoffPackage(
    val id: String,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val mentalContext: MentalContext,
    val applicationStates: List<ApplicationState>,
    val deviceContext: DeviceContext,
    val priority: HandoffPriority,
    val expirationTime: Long,
    val encryptionKey: String? = null
)

enum class HandoffPriority {
    URGENT, HIGH, NORMAL, LOW, BACKGROUND
}

data class HandoffResult(
    val success: Boolean,
    val transferredApps: List<String>,
    val failedApps: List<String>,
    val contextPreservationScore: Float, // 0.0 to 1.0
    val transferTime: Duration,
    val errorMessage: String? = null
)

interface GhostHandoffSystem {
    val isActive: StateFlow<Boolean>
    val availableTargets: StateFlow<List<String>>
    val currentHandoffs: StateFlow<List<HandoffPackage>>
    
    suspend fun startHandoffCapture(): Boolean
    suspend fun stopHandoffCapture()
    suspend fun initiateHandoff(targetDeviceId: String, priority: HandoffPriority = HandoffPriority.NORMAL): HandoffResult
    suspend fun acceptHandoff(handoffId: String): HandoffResult
    suspend fun rejectHandoff(handoffId: String, reason: String): Boolean
    suspend fun cancelHandoff(handoffId: String): Boolean
    
    fun getMentalContext(): MentalContext
    fun getApplicationStates(): List<ApplicationState>
    fun reconstructContext(handoffPackage: HandoffPackage): Boolean
}

expect class PlatformHandoffSystem() : GhostHandoffSystem {
    override val isActive: StateFlow<Boolean>
    override val availableTargets: StateFlow<List<String>>
    override val currentHandoffs: StateFlow<List<HandoffPackage>>
    
    override suspend fun startHandoffCapture(): Boolean
    override suspend fun stopHandoffCapture()
    override suspend fun initiateHandoff(targetDeviceId: String, priority: HandoffPriority): HandoffResult
    override suspend fun acceptHandoff(handoffId: String): HandoffResult
    override suspend fun rejectHandoff(handoffId: String, reason: String): Boolean
    override suspend fun cancelHandoff(handoffId: String): Boolean
    
    override fun getMentalContext(): MentalContext
    override fun getApplicationStates(): List<ApplicationState>
    override fun reconstructContext(handoffPackage: HandoffPackage): Boolean
}