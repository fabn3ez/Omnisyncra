package com.omnisyncra.core.domain

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid = uuid4(),
    val name: String,
    val type: DeviceType,
    val capabilities: DeviceCapabilities,
    val networkInfo: NetworkInfo? = null,
    val proximityInfo: ProximityInfo? = null,
    val lastSeen: Long = 0L
)

@Serializable
enum class DeviceType {
    ANDROID_PHONE,
    ANDROID_TABLET,
    DESKTOP,
    WEB_BROWSER,
    UNKNOWN
}

@Serializable
data class DeviceCapabilities(
    val computePower: ComputePower,
    val screenSize: ScreenSize,
    val hasBluetoothLE: Boolean,
    val hasWiFi: Boolean,
    val canOffloadCompute: Boolean,
    val maxConcurrentTasks: Int,
    val supportedProtocols: List<String>
)

@Serializable
enum class ComputePower {
    LOW,      // Web/Wasm, older mobile
    MEDIUM,   // Modern mobile, basic desktop
    HIGH,     // High-end desktop, server
    EXTREME   // Workstation, dedicated compute
}

@Serializable
enum class ScreenSize {
    SMALL,    // Phone
    MEDIUM,   // Tablet, small laptop
    LARGE,    // Desktop, large laptop
    EXTRA_LARGE // Ultra-wide, multi-monitor
}

@Serializable
data class NetworkInfo(
    val ipAddress: String,
    val port: Int,
    val protocol: String,
    val isReachable: Boolean = false
)

@Serializable
data class ProximityInfo(
    val distance: ProximityDistance,
    val signalStrength: Int, // RSSI for BLE, signal strength for WiFi
    val lastUpdated: Long
)

@Serializable
enum class ProximityDistance {
    IMMEDIATE,  // < 1m
    NEAR,       // 1-3m
    FAR,        // 3-10m
    UNKNOWN     // > 10m or no signal
}