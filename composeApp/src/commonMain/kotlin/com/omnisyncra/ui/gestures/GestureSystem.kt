package com.omnisyncra.ui.gestures

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Advanced gesture system for Omnisyncra
 * Handles swipe patterns, multi-touch gestures, and context switching
 */

enum class SwipeDirection {
    LEFT, RIGHT, UP, DOWN, DIAGONAL_UP_LEFT, DIAGONAL_UP_RIGHT, 
    DIAGONAL_DOWN_LEFT, DIAGONAL_DOWN_RIGHT
}

data class GestureState(
    val isActive: Boolean = false,
    val startPosition: Offset = Offset.Zero,
    val currentPosition: Offset = Offset.Zero,
    val velocity: Offset = Offset.Zero,
    val direction: SwipeDirection? = null,
    val distance: Float = 0f
)

class GestureController {
    private val _gestureState = mutableStateOf(GestureState())
    val gestureState: State<GestureState> = _gestureState
    
    private val _onSwipePattern = mutableStateOf<((List<SwipeDirection>) -> Unit)?>(null)
    val onSwipePattern: State<((List<SwipeDirection>) -> Unit)?> = _onSwipePattern
    
    private val swipePattern = mutableListOf<SwipeDirection>()
    private val patternTimeout = 2000L // 2 seconds
    
    fun setSwipePatternHandler(handler: (List<SwipeDirection>) -> Unit) {
        _onSwipePattern.value = handler
    }
    
    fun handleGestureStart(position: Offset) {
        _gestureState.value = _gestureState.value.copy(
            isActive = true,
            startPosition = position,
            currentPosition = position
        )
    }
    
    fun handleGestureUpdate(position: Offset, velocity: Offset) {
        val distance = (position - _gestureState.value.startPosition).getDistance()
        val direction = calculateDirection(
            _gestureState.value.startPosition,
            position
        )
        
        _gestureState.value = _gestureState.value.copy(
            currentPosition = position,
            velocity = velocity,
            direction = direction,
            distance = distance
        )
    }
    
    fun handleGestureEnd() {
        val currentState = _gestureState.value
        
        // Add to pattern if significant swipe
        if (currentState.distance > 100f && currentState.direction != null) {
            swipePattern.add(currentState.direction!!)
            
            // Check for pattern completion
            checkSwipePattern()
        }
        
        _gestureState.value = GestureState()
    }
    
    private fun calculateDirection(start: Offset, end: Offset): SwipeDirection? {
        val delta = end - start
        val distance = delta.getDistance()
        
        if (distance < 50f) return null // Too small to be a gesture
        
        val angle = atan2(delta.y, delta.x) * 180 / PI
        
        return when {
            angle >= -22.5 && angle < 22.5 -> SwipeDirection.RIGHT
            angle >= 22.5 && angle < 67.5 -> SwipeDirection.DIAGONAL_DOWN_RIGHT
            angle >= 67.5 && angle < 112.5 -> SwipeDirection.DOWN
            angle >= 112.5 && angle < 157.5 -> SwipeDirection.DIAGONAL_DOWN_LEFT
            angle >= 157.5 || angle < -157.5 -> SwipeDirection.LEFT
            angle >= -157.5 && angle < -112.5 -> SwipeDirection.DIAGONAL_UP_LEFT
            angle >= -112.5 && angle < -67.5 -> SwipeDirection.UP
            angle >= -67.5 && angle < -22.5 -> SwipeDirection.DIAGONAL_UP_RIGHT
            else -> null
        }
    }
    
    private fun checkSwipePattern() {
        val handler = _onSwipePattern.value ?: return
        
        // Check for known patterns
        when {
            // Triangle pattern (context switch)
            swipePattern.takeLast(3) == listOf(
                SwipeDirection.UP, SwipeDirection.DIAGONAL_DOWN_LEFT, SwipeDirection.RIGHT
            ) -> {
                handler(swipePattern.takeLast(3))
                swipePattern.clear()
            }
            
            // Circle pattern (role switch)
            swipePattern.size >= 4 && isCirclePattern(swipePattern.takeLast(4)) -> {
                handler(swipePattern.takeLast(4))
                swipePattern.clear()
            }
            
            // Z pattern (mesh view)
            swipePattern.takeLast(3) == listOf(
                SwipeDirection.RIGHT, SwipeDirection.DIAGONAL_DOWN_LEFT, SwipeDirection.RIGHT
            ) -> {
                handler(swipePattern.takeLast(3))
                swipePattern.clear()
            }
        }
        
        // Clear old patterns
        if (swipePattern.size > 6) {
            swipePattern.removeAt(0)
        }
    }
    
    private fun isCirclePattern(pattern: List<SwipeDirection>): Boolean {
        // Simplified circle detection - look for 4 directions forming a rough circle
        val directions = pattern.toSet()
        return directions.containsAll(listOf(
            SwipeDirection.UP, SwipeDirection.RIGHT, 
            SwipeDirection.DOWN, SwipeDirection.LEFT
        ))
    }
}

/**
 * Gesture detection modifiers
 */
@Composable
fun Modifier.omnisyncraGestures(
    gestureController: GestureController,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
): Modifier {
    val density = LocalDensity.current
    
    return this.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset ->
                gestureController.handleGestureStart(offset)
            },
            onDrag = { change, _ ->
                gestureController.handleGestureUpdate(
                    change.position,
                    Offset.Zero // Simplified velocity
                )
            },
            onDragEnd = {
                gestureController.handleGestureEnd()
            }
        )
    }.pointerInput(Unit) {
        detectTapGestures(
            onTap = { onTap?.invoke() },
            onDoubleTap = { onDoubleTap?.invoke() },
            onLongPress = { onLongPress?.invoke() }
        )
    }
}

/**
 * Multi-touch gesture detection
 */
@Composable
fun Modifier.multiTouchGestures(
    onPinch: ((Float) -> Unit)? = null,
    onRotate: ((Float) -> Unit)? = null
): Modifier {
    return this.pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, rotation ->
            onPinch?.invoke(zoom)
            onRotate?.invoke(rotation)
        }
    }
}

/**
 * Swipe pattern recognition
 */
@Composable
fun SwipePatternDetector(
    gestureController: GestureController,
    onTrianglePattern: () -> Unit = {},
    onCirclePattern: () -> Unit = {},
    onZPattern: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    LaunchedEffect(gestureController) {
        gestureController.setSwipePatternHandler { pattern ->
            when {
                pattern.size == 3 && pattern == listOf(
                    SwipeDirection.UP, SwipeDirection.DIAGONAL_DOWN_LEFT, SwipeDirection.RIGHT
                ) -> onTrianglePattern()
                
                pattern.size == 4 && pattern.toSet().containsAll(listOf(
                    SwipeDirection.UP, SwipeDirection.RIGHT, 
                    SwipeDirection.DOWN, SwipeDirection.LEFT
                )) -> onCirclePattern()
                
                pattern.size == 3 && pattern == listOf(
                    SwipeDirection.RIGHT, SwipeDirection.DIAGONAL_DOWN_LEFT, SwipeDirection.RIGHT
                ) -> onZPattern()
            }
        }
    }
    
    Box(
        modifier = modifier.omnisyncraGestures(gestureController)
    ) {
        content()
    }
}

/**
 * Gesture feedback system
 */
@Composable
fun GestureFeedback(
    gestureState: GestureState,
    modifier: Modifier = Modifier
) {
    // Visual feedback for active gestures
    if (gestureState.isActive) {
        // Draw gesture trail or indicator
        // This would be implemented with Canvas in a real app
        Box(modifier = modifier)
    }
}

/**
 * Haptic feedback integration (platform-specific implementation needed)
 */
expect class HapticFeedback() {
    fun lightImpact()
    fun mediumImpact()
    fun heavyImpact()
    fun selectionChanged()
}

@Composable
fun rememberHapticFeedback(): HapticFeedback {
    return remember { HapticFeedback() }
}