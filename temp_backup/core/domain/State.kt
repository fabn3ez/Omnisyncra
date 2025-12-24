package com.omnisyncra.core.domain

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable

@Serializable
data class OmnisyncraState(
    val deviceMesh: DeviceMesh,
    val contextGraph: ContextGraph,
    val uiState: UIState,
    val version: Long = 0L,
    val lastSyncedAt: Long = 0L
)

@Serializable
data class DeviceMesh(
    val localDevice: Device,
    val discoveredDevices: Map<@Serializable(with = UuidSerializer::class) Uuid, Device> = emptyMap(),
    val connectedDevices: Map<@Serializable(with = UuidSerializer::class) Uuid, Device> = emptyMap(),
    val meshTopology: MeshTopology = MeshTopology()
) {
    fun getAllDevices(): List<Device> = 
        listOf(localDevice) + discoveredDevices.values + connectedDevices.values
    
    fun getNearbyDevices(): List<Device> =
        discoveredDevices.values.filter { 
            it.proximityInfo?.distance in listOf(
                ProximityDistance.IMMEDIATE, 
                ProximityDistance.NEAR
            )
        }
}

@Serializable
data class MeshTopology(
    val connections: Map<@Serializable(with = UuidSerializer::class) Uuid, List<@Serializable(with = UuidSerializer::class) Uuid>> = emptyMap(),
    val roles: Map<@Serializable(with = UuidSerializer::class) Uuid, DeviceRole> = emptyMap()
)

@Serializable
enum class DeviceRole {
    PRIMARY,        // Main interaction device
    SECONDARY,      // Context palette/companion
    COMPUTE_NODE,   // Background processing
    VIEWER,         // Display-only mode
    BRIDGE          // Network bridge/relay
}

@Serializable
data class UIState(
    val currentMode: UIMode,
    val adaptiveLayout: AdaptiveLayout,
    val theme: ThemeState,
    val animations: AnimationState = AnimationState()
)

@Serializable
enum class UIMode {
    STANDALONE,     // Single device mode
    PRIMARY,        // Main device in mesh
    SECONDARY,      // Companion device
    CONTEXT_PALETTE, // Specialized resource view
    VIEWER,         // Read-only display
    TRANSITIONING   // Between modes
}

@Serializable
data class AdaptiveLayout(
    val screenConfiguration: ScreenConfiguration,
    val availableSpace: LayoutSpace,
    val contentPriority: List<ContentType>
)

@Serializable
data class ScreenConfiguration(
    val width: Int,
    val height: Int,
    val density: Float,
    val orientation: Orientation
)

@Serializable
enum class Orientation {
    PORTRAIT,
    LANDSCAPE
}

@Serializable
data class LayoutSpace(
    val primary: Float,    // 0.0 to 1.0
    val secondary: Float,  // 0.0 to 1.0
    val tertiary: Float    // 0.0 to 1.0
)

@Serializable
enum class ContentType {
    MAIN_CONTENT,
    CONTEXT_TOOLS,
    RESOURCE_PALETTE,
    NAVIGATION,
    STATUS_INFO
}

@Serializable
data class ThemeState(
    val isDarkMode: Boolean,
    val accentColor: String,
    val proximityIndicatorStyle: ProximityStyle
)

@Serializable
enum class ProximityStyle {
    SUBTLE,
    AMBIENT,
    PROMINENT
}

@Serializable
data class AnimationState(
    val isTransitioning: Boolean = false,
    val transitionType: TransitionType? = null,
    val progress: Float = 0f
)

@Serializable
enum class TransitionType {
    ROLE_CHANGE,
    DEVICE_CONNECT,
    DEVICE_DISCONNECT,
    CONTEXT_SWITCH,
    LAYOUT_ADAPT
}