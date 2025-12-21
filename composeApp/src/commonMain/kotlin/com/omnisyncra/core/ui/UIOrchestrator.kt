package com.omnisyncra.core.ui

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.*
import com.omnisyncra.core.discovery.DeviceDiscovery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UIOrchestrator {
    val currentUIState: StateFlow<UIState>
    val proximityTriggers: Flow<ProximityTrigger>
    val layoutTransitions: Flow<LayoutTransition>
    
    suspend fun handleProximityChange(deviceId: Uuid, proximityInfo: ProximityInfo)
    suspend fun requestRoleChange(targetRole: DeviceRole, reason: RoleChangeReason)
    suspend fun triggerLayoutAdaptation(trigger: AdaptationTrigger)
    suspend fun synchronizeUIState(targetDevices: List<Uuid>)
    
    fun getCurrentRole(): DeviceRole
    fun getAdaptiveLayout(): AdaptiveLayout
    fun canTransitionTo(targetMode: UIMode): Boolean
}

data class ProximityTrigger(
    val deviceId: Uuid,
    val previousDistance: ProximityDistance?,
    val currentDistance: ProximityDistance,
    val suggestedAction: ProximityAction,
    val timestamp: Long
)

enum class ProximityAction {
    BECOME_SECONDARY,
    BECOME_PRIMARY,
    SHOW_CONTEXT_PALETTE,
    HIDE_CONTEXT_PALETTE,
    ESTABLISH_CONNECTION,
    MAINTAIN_CURRENT,
    DISCONNECT
}

data class LayoutTransition(
    val fromLayout: AdaptiveLayout,
    val toLayout: AdaptiveLayout,
    val transitionType: TransitionType,
    val duration: Long,
    val easing: TransitionEasing
)

enum class TransitionEasing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    SPRING,
    BOUNCE
}

enum class RoleChangeReason {
    PROXIMITY_DETECTED,
    USER_REQUEST,
    CONTEXT_SWITCH,
    DEVICE_CAPABILITY_CHANGE,
    NETWORK_OPTIMIZATION,
    BATTERY_OPTIMIZATION
}

data class AdaptationTrigger(
    val type: AdaptationType,
    val context: Map<String, String>,
    val priority: AdaptationPriority,
    val sourceDeviceId: Uuid?
)

enum class AdaptationType {
    SCREEN_SIZE_CHANGE,
    ORIENTATION_CHANGE,
    PROXIMITY_CHANGE,
    CONTEXT_SWITCH,
    DEVICE_CONNECT,
    DEVICE_DISCONNECT,
    PERFORMANCE_OPTIMIZATION,
    USER_PREFERENCE
}

enum class AdaptationPriority {
    LOW,
    NORMAL,
    HIGH,
    IMMEDIATE
}