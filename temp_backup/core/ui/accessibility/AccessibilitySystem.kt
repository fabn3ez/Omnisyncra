package com.omnisyncra.core.ui.accessibility

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.semantics.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.omnisyncra.core.domain.Device
import com.omnisyncra.core.security.TrustLevel

// Accessibility configuration
data class AccessibilityConfig(
    val screenReaderEnabled: Boolean = true,
    val highContrastEnabled: Boolean = false,
    val largeTextEnabled: Boolean = false,
    val reducedMotionEnabled: Boolean = false,
    val voiceAnnouncementsEnabled: Boolean = true
)

// Semantic descriptions for devices
@Composable
fun Modifier.deviceSemantics(
    device: Device,
    isConnected: Boolean = false,
    trustLevel: TrustLevel? = null
): Modifier {
    val description = buildString {
        append("Device: ${device.name}")
        append(", Type: ${device.type.name}")
        append(", Compute Power: ${device.capabilities.computePower.name}")
        
        if (isConnected) {
            append(", Connected")
        } else {
            append(", Not connected")
        }
        
        trustLevel?.let {
            append(", Trust Level: ${it.name}")
        }
        
        device.proximityInfo?.let { proximity ->
            append(", Distance: ${proximity.distance.name}")
            append(", Signal Strength: ${proximity.signalStrength} dBm")
        }
    }
    
    return this.semantics {
        contentDescription = description
        role = Role.Button
        
        // Custom actions for device interaction
        customActions = listOf(
            CustomAccessibilityAction(
                label = "Connect to device",
                action = { true }
            ),
            CustomAccessibilityAction(
                label = "View device details",
                action = { true }
            ),
            CustomAccessibilityAction(
                label = "Trust device",
                action = { true }
            )
        )
    }
}

// Semantic descriptions for compute tasks
@Composable
fun Modifier.taskSemantics(
    taskType: String,
    status: String,
    progress: Float? = null,
    deviceName: String? = null
): Modifier {
    val description = buildString {
        append("Task: $taskType")
        append(", Status: $status")
        
        progress?.let {
            append(", Progress: ${(it * 100).toInt()}%")
        }
        
        deviceName?.let {
            append(", Running on: $it")
        }
    }
    
    return this.semantics {
        contentDescription = description
        role = Role.Button
        
        progress?.let {
            progressBarRangeInfo = ProgressBarRangeInfo(it, 0f..1f)
        }
        
        customActions = listOf(
            CustomAccessibilityAction(
                label = "View task details",
                action = { true }
            ),
            CustomAccessibilityAction(
                label = "Cancel task",
                action = { true }
            )
        )
    }
}

// Semantic descriptions for security elements
@Composable
fun Modifier.securitySemantics(
    elementType: String,
    securityLevel: String,
    isEncrypted: Boolean = false
): Modifier {
    val description = buildString {
        append("$elementType")
        append(", Security Level: $securityLevel")
        
        if (isEncrypted) {
            append(", Encrypted")
        } else {
            append(", Not encrypted")
        }
    }
    
    return this.semantics {
        contentDescription = description
        role = Role.Button
        
        customActions = listOf(
            CustomAccessibilityAction(
                label = "View security details",
                action = { true }
            ),
            CustomAccessibilityAction(
                label = "Change security settings",
                action = { true }
            )
        )
    }
}

// Navigation semantics
@Composable
fun Modifier.navigationSemantics(
    currentScreen: String,
    totalScreens: Int,
    screenIndex: Int
): Modifier {
    return this.semantics {
        contentDescription = "Screen $screenIndex of $totalScreens: $currentScreen"
        role = Role.Tab
        
        customActions = listOf(
            CustomAccessibilityAction(
                label = "Go to previous screen",
                action = { true }
            ),
            CustomAccessibilityAction(
                label = "Go to next screen",
                action = { true }
            )
        )
    }
}

// Connection line semantics
@Composable
fun Modifier.connectionSemantics(
    fromDevice: String,
    toDevice: String,
    connectionStrength: String
): Modifier {
    val description = "Connection from $fromDevice to $toDevice, Strength: $connectionStrength"
    
    return this.semantics {
        contentDescription = description
        role = Role.Image
        
        customActions = listOf(
            CustomAccessibilityAction(
                label = "View connection details",
                action = { true }
            ),
            CustomAccessibilityAction(
                label = "Disconnect devices",
                action = { true }
            )
        )
    }
}

// Progress indicator semantics
@Composable
fun Modifier.progressSemantics(
    operation: String,
    progress: Float,
    isIndeterminate: Boolean = false
): Modifier {
    val description = if (isIndeterminate) {
        "$operation in progress"
    } else {
        "$operation: ${(progress * 100).toInt()}% complete"
    }
    
    return this.semantics {
        contentDescription = description
        role = Role.ProgressBar
        
        if (!isIndeterminate) {
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        }
    }
}

// Status indicator semantics
@Composable
fun Modifier.statusSemantics(
    status: String,
    isPositive: Boolean
): Modifier {
    val description = if (isPositive) {
        "Status: $status (Good)"
    } else {
        "Status: $status (Attention needed)"
    }
    
    return this.semantics {
        contentDescription = description
        role = Role.Image
        
        // Add state description for screen readers
        stateDescription = if (isPositive) "Good" else "Warning"
    }
}

// List semantics for device/task collections
@Composable
fun Modifier.collectionSemantics(
    collectionType: String,
    itemCount: Int,
    selectedCount: Int = 0
): Modifier {
    val description = buildString {
        append("$collectionType list")
        append(", $itemCount items")
        
        if (selectedCount > 0) {
            append(", $selectedCount selected")
        }
    }
    
    return this.semantics {
        contentDescription = description
        role = Role.List
        
        customActions = listOf(
            CustomAccessibilityAction(
                label = "Select all items",
                action = { true }
            ),
            CustomAccessibilityAction(
                label = "Clear selection",
                action = { true }
            )
        )
    }
}

// Gesture hint semantics
@Composable
fun Modifier.gestureHintSemantics(
    gestureType: String,
    action: String
): Modifier {
    val description = "$gestureType to $action"
    
    return this.semantics {
        contentDescription = description
        
        customActions = listOf(
            CustomAccessibilityAction(
                label = action,
                action = { true }
            )
        )
    }
}

// Accessibility announcements
@Composable
fun AccessibilityAnnouncement(
    message: String,
    priority: AnnouncementPriority = AnnouncementPriority.NORMAL
) {
    val accessibilityManager = LocalAccessibilityManager.current
    
    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            accessibilityManager?.announce(message)
        }
    }
}

enum class AnnouncementPriority {
    LOW, NORMAL, HIGH
}

// Accessibility state management
@Composable
fun rememberAccessibilityState(): AccessibilityState {
    return remember { AccessibilityState() }
}

class AccessibilityState {
    var config by mutableStateOf(AccessibilityConfig())
        private set
    
    var lastAnnouncement by mutableStateOf("")
        private set
    
    fun updateConfig(newConfig: AccessibilityConfig) {
        config = newConfig
    }
    
    fun announce(message: String) {
        lastAnnouncement = message
    }
    
    fun toggleScreenReader() {
        config = config.copy(screenReaderEnabled = !config.screenReaderEnabled)
    }
    
    fun toggleHighContrast() {
        config = config.copy(highContrastEnabled = !config.highContrastEnabled)
    }
    
    fun toggleLargeText() {
        config = config.copy(largeTextEnabled = !config.largeTextEnabled)
    }
    
    fun toggleReducedMotion() {
        config = config.copy(reducedMotionEnabled = !config.reducedMotionEnabled)
    }
    
    fun toggleVoiceAnnouncements() {
        config = config.copy(voiceAnnouncementsEnabled = !config.voiceAnnouncementsEnabled)
    }
}

// Accessibility testing helpers
@Composable
fun Modifier.accessibilityTestTag(tag: String): Modifier {
    return this.semantics {
        testTag = tag
    }
}

// Focus management for keyboard navigation
@Composable
fun Modifier.focusSemantics(
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit
): Modifier {
    return this.semantics {
        focused = isFocused
        
        customActions = listOf(
            CustomAccessibilityAction(
                label = if (isFocused) "Remove focus" else "Focus",
                action = {
                    onFocusChanged(!isFocused)
                    true
                }
            )
        )
    }
}

// Accessibility utility functions
fun getAccessibleDeviceDescription(device: Device): String {
    return buildString {
        append("${device.name} device")
        append(" with ${device.capabilities.computePower.name.lowercase()} compute power")
        
        device.proximityInfo?.let { proximity ->
            append(", located at ${proximity.distance.name.lowercase()} distance")
        }
    }
}

fun getAccessibleTaskDescription(taskType: String, status: String): String {
    return "$taskType task is currently $status"
}

fun getAccessibleSecurityDescription(trustLevel: TrustLevel): String {
    return when (trustLevel) {
        TrustLevel.TRUSTED -> "Device is trusted and secure"
        TrustLevel.VERIFIED -> "Device is verified and highly secure"
        TrustLevel.PENDING -> "Device trust is pending verification"
        TrustLevel.UNTRUSTED -> "Device is not trusted, use caution"
    }
}

// Platform-specific accessibility manager
@Composable
expect fun LocalAccessibilityManager.current: AccessibilityManager?

expect class AccessibilityManager {
    fun announce(message: String)
    fun isScreenReaderEnabled(): Boolean
    fun isHighContrastEnabled(): Boolean
    fun isLargeTextEnabled(): Boolean
    fun isReducedMotionEnabled(): Boolean
}