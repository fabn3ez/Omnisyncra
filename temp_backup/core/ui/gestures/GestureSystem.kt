package com.omnisyncra.core.ui.gestures

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntOffset
import com.omnisyncra.core.domain.Device
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Gesture configuration
data class GestureConfig(
    val dragThreshold: Float = 10f,
    val connectionDistance: Float = 100f,
    val longPressThreshold: Long = 500L,
    val doubleTapThreshold: Long = 300L
)

// Drag state for device connections
data class DragState(
    val isDragging: Boolean = false,
    val startPosition: Offset = Offset.Zero,
    val currentPosition: Offset = Offset.Zero,
    val targetDevice: Device? = null
)

// Connection gesture result
sealed class ConnectionGestureResult {
    object None : ConnectionGestureResult()
    data class Connecting(val fromDevice: Device, val toDevice: Device) : ConnectionGestureResult()
    data class Connected(val fromDevice: Device, val toDevice: Device) : ConnectionGestureResult()
    data class Cancelled(val device: Device) : ConnectionGestureResult()
}

// Drag-to-connect gesture
@Composable
fun Modifier.dragToConnect(
    device: Device,
    nearbyDevices: List<Device>,
    onConnectionGesture: (ConnectionGestureResult) -> Unit,
    config: GestureConfig = GestureConfig()
): Modifier {
    var dragState by remember { mutableStateOf(DragState()) }
    val scope = rememberCoroutineScope()
    
    return this
        .offset { 
            if (dragState.isDragging) {
                IntOffset(
                    (dragState.currentPosition.x - dragState.startPosition.x).roundToInt(),
                    (dragState.currentPosition.y - dragState.startPosition.y).roundToInt()
                )
            } else {
                IntOffset.Zero
            }
        }
        .pointerInput(device.id) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragState = dragState.copy(
                        isDragging = true,
                        startPosition = offset,
                        currentPosition = offset
                    )
                },
                onDrag = { change ->
                    val newPosition = dragState.currentPosition + change.change
                    dragState = dragState.copy(currentPosition = newPosition)
                    
                    // Check for nearby devices
                    val targetDevice = findNearbyDevice(
                        newPosition,
                        nearbyDevices,
                        config.connectionDistance
                    )
                    
                    if (targetDevice != dragState.targetDevice) {
                        dragState = dragState.copy(targetDevice = targetDevice)
                        
                        if (targetDevice != null) {
                            scope.launch {
                                onConnectionGesture(
                                    ConnectionGestureResult.Connecting(device, targetDevice)
                                )
                            }
                        }
                    }
                },
                onDragEnd = {
                    val targetDevice = dragState.targetDevice
                    
                    if (targetDevice != null) {
                        scope.launch {
                            onConnectionGesture(
                                ConnectionGestureResult.Connected(device, targetDevice)
                            )
                        }
                    } else {
                        scope.launch {
                            onConnectionGesture(
                                ConnectionGestureResult.Cancelled(device)
                            )
                        }
                    }
                    
                    dragState = DragState()
                }
            )
        }
}

// Long press gesture for device options
@Composable
fun Modifier.longPressGesture(
    onLongPress: (Offset) -> Unit,
    config: GestureConfig = GestureConfig()
): Modifier {
    return this.pointerInput(Unit) {
        detectTapGestures(
            onLongPress = { offset ->
                onLongPress(offset)
            }
        )
    }
}

// Double tap gesture for quick actions
@Composable
fun Modifier.doubleTapGesture(
    onDoubleTap: (Offset) -> Unit,
    config: GestureConfig = GestureConfig()
): Modifier {
    return this.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { offset ->
                onDoubleTap(offset)
            }
        )
    }
}

// Pinch-to-zoom gesture for device mesh view
@Composable
fun Modifier.pinchToZoom(
    onZoom: (Float) -> Unit,
    config: GestureConfig = GestureConfig()
): Modifier {
    return this.pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            onZoom(zoom)
        }
    }
}

// Swipe gesture for navigation
@Composable
fun Modifier.swipeGesture(
    onSwipe: (SwipeDirection) -> Unit,
    config: GestureConfig = GestureConfig()
): Modifier {
    return this.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = {
                // Determine swipe direction based on drag distance
                // Implementation would analyze the drag vector
            }
        ) { change ->
            val dragAmount = change.change
            val threshold = config.dragThreshold
            
            when {
                dragAmount.x > threshold -> onSwipe(SwipeDirection.RIGHT)
                dragAmount.x < -threshold -> onSwipe(SwipeDirection.LEFT)
                dragAmount.y > threshold -> onSwipe(SwipeDirection.DOWN)
                dragAmount.y < -threshold -> onSwipe(SwipeDirection.UP)
            }
        }
    }
}

enum class SwipeDirection {
    LEFT, RIGHT, UP, DOWN
}

// Multi-touch gesture for device selection
@Composable
fun Modifier.multiSelectGesture(
    onSelectionChanged: (List<Device>) -> Unit,
    devices: List<Device>,
    config: GestureConfig = GestureConfig()
): Modifier {
    var selectedDevices by remember { mutableStateOf<List<Device>>(emptyList()) }
    
    return this.pointerInput(devices) {
        awaitEachGesture {
            val down = awaitFirstDown()
            
            // Handle multi-touch selection
            do {
                val event = awaitPointerEvent()
                
                event.changes.forEach { change ->
                    if (change.pressed) {
                        val position = change.position
                        val deviceAtPosition = findDeviceAtPosition(position, devices)
                        
                        if (deviceAtPosition != null) {
                            selectedDevices = if (selectedDevices.contains(deviceAtPosition)) {
                                selectedDevices - deviceAtPosition
                            } else {
                                selectedDevices + deviceAtPosition
                            }
                            
                            onSelectionChanged(selectedDevices)
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

// Rotation gesture for 3D device view
@Composable
fun Modifier.rotationGesture(
    onRotate: (Float) -> Unit,
    config: GestureConfig = GestureConfig()
): Modifier {
    return this.pointerInput(Unit) {
        detectTransformGestures { _, _, _, rotation ->
            onRotate(rotation)
        }
    }
}

// Haptic feedback integration
@Composable
fun Modifier.hapticFeedback(
    trigger: Boolean,
    feedbackType: HapticFeedbackType = HapticFeedbackType.LIGHT
): Modifier {
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    LaunchedEffect(trigger) {
        if (trigger) {
            hapticFeedback.performHapticFeedback(
                when (feedbackType) {
                    HapticFeedbackType.LIGHT -> androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    HapticFeedbackType.MEDIUM -> androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    HapticFeedbackType.HEAVY -> androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                }
            )
        }
    }
    
    return this
}

enum class HapticFeedbackType {
    LIGHT, MEDIUM, HEAVY
}

// Helper functions
private fun findNearbyDevice(
    position: Offset,
    devices: List<Device>,
    threshold: Float
): Device? {
    return devices.find { device ->
        // In a real implementation, this would calculate distance to device position
        // For now, we'll use a simplified approach
        val distance = calculateDistance(position, Offset(100f, 100f)) // Mock device position
        distance <= threshold
    }
}

private fun findDeviceAtPosition(
    position: Offset,
    devices: List<Device>
): Device? {
    // In a real implementation, this would check if position intersects with device UI element
    return devices.firstOrNull()
}

private fun calculateDistance(point1: Offset, point2: Offset): Float {
    val dx = point1.x - point2.x
    val dy = point1.y - point2.y
    return sqrt(dx * dx + dy * dy)
}

// Gesture state management
@Composable
fun rememberGestureState(): GestureState {
    return remember { GestureState() }
}

class GestureState {
    var isDragging by mutableStateOf(false)
    var isLongPressing by mutableStateOf(false)
    var selectedDevices by mutableStateOf<List<Device>>(emptyList())
    var zoomLevel by mutableFloatStateOf(1f)
    var rotationAngle by mutableFloatStateOf(0f)
    
    fun reset() {
        isDragging = false
        isLongPressing = false
        selectedDevices = emptyList()
        zoomLevel = 1f
        rotationAngle = 0f
    }
}