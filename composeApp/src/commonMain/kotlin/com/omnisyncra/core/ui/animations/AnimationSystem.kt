package com.omnisyncra.core.ui.animations

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

// Animation configuration
data class AnimationConfig(
    val duration: Int = 300,
    val easing: Easing = FastOutSlowInEasing,
    val delayMillis: Int = 0
)

// Device proximity animation states
enum class ProximityAnimationState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

// Compute task animation states
enum class TaskAnimationState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

// Proximity-based animations
@Composable
fun Modifier.proximityAnimation(
    state: ProximityAnimationState,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val transition = updateTransition(targetState = state, label = "proximity")
    
    val scale by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = config.duration,
                easing = config.easing,
                delayMillis = config.delayMillis
            )
        },
        label = "scale"
    ) { proximityState ->
        when (proximityState) {
            ProximityAnimationState.DISCOVERED -> 1.0f
            ProximityAnimationState.CONNECTING -> 1.1f
            ProximityAnimationState.CONNECTED -> 1.05f
            ProximityAnimationState.DISCONNECTED -> 0.95f
            ProximityAnimationState.ERROR -> 0.9f
        }
    }
    
    val alpha by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = config.duration,
                easing = config.easing
            )
        },
        label = "alpha"
    ) { proximityState ->
        when (proximityState) {
            ProximityAnimationState.DISCOVERED -> 0.8f
            ProximityAnimationState.CONNECTING -> 1.0f
            ProximityAnimationState.CONNECTED -> 1.0f
            ProximityAnimationState.DISCONNECTED -> 0.6f
            ProximityAnimationState.ERROR -> 0.7f
        }
    }
    
    return this
        .scale(scale)
        .alpha(alpha)
}

// Task progress animations
@Composable
fun Modifier.taskProgressAnimation(
    state: TaskAnimationState,
    progress: Float = 0f,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val transition = updateTransition(targetState = state, label = "task")
    
    val animatedProgress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = config.duration,
                easing = LinearEasing
            )
        },
        label = "progress"
    ) { taskState ->
        when (taskState) {
            TaskAnimationState.PENDING -> 0f
            TaskAnimationState.RUNNING -> progress
            TaskAnimationState.COMPLETED -> 1f
            TaskAnimationState.FAILED -> 0f
        }
    }
    
    val pulseScale by transition.animateFloat(
        transitionSpec = {
            if (targetState == TaskAnimationState.RUNNING) {
                infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            } else {
                tween(config.duration)
            }
        },
        label = "pulse"
    ) { taskState ->
        when (taskState) {
            TaskAnimationState.PENDING -> 1.0f
            TaskAnimationState.RUNNING -> 1.02f
            TaskAnimationState.COMPLETED -> 1.0f
            TaskAnimationState.FAILED -> 1.0f
        }
    }
    
    return this
        .scale(pulseScale)
        .graphicsLayer {
            // Custom progress indicator overlay could be added here
        }
}

// Connection line animation
@Composable
fun Modifier.connectionLineAnimation(
    isConnected: Boolean,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0f,
        animationSpec = tween(
            durationMillis = config.duration,
            easing = config.easing
        ),
        label = "connection_alpha"
    )
    
    return this.alpha(animatedAlpha)
}

// Slide-in animation for new devices
@Composable
fun Modifier.slideInAnimation(
    visible: Boolean,
    direction: SlideDirection = SlideDirection.LEFT,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slideDistance = with(density) { 300.dp.toPx() }.roundToInt()
    
    val offsetX by animateIntAsState(
        targetValue = if (visible) 0 else when (direction) {
            SlideDirection.LEFT -> -slideDistance
            SlideDirection.RIGHT -> slideDistance
            else -> 0
        },
        animationSpec = tween(
            durationMillis = config.duration,
            easing = config.easing,
            delayMillis = config.delayMillis
        ),
        label = "slide_x"
    )
    
    val offsetY by animateIntAsState(
        targetValue = if (visible) 0 else when (direction) {
            SlideDirection.UP -> -slideDistance
            SlideDirection.DOWN -> slideDistance
            else -> 0
        },
        animationSpec = tween(
            durationMillis = config.duration,
            easing = config.easing,
            delayMillis = config.delayMillis
        ),
        label = "slide_y"
    )
    
    return this.offset { IntOffset(offsetX, offsetY) }
}

enum class SlideDirection {
    LEFT, RIGHT, UP, DOWN
}

// Bounce animation for successful connections
@Composable
fun Modifier.bounceAnimation(
    trigger: Boolean,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (trigger) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce"
    )
    
    return this.scale(scale)
}

// Shake animation for errors
@Composable
fun Modifier.shakeAnimation(
    trigger: Boolean,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val offsetX by animateFloatAsState(
        targetValue = if (trigger) 10f else 0f,
        animationSpec = if (trigger) {
            infiniteRepeatable(
                animation = tween(50, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                iterations = 6
            )
        } else {
            tween(config.duration)
        },
        label = "shake"
    )
    
    return this.offset { IntOffset(offsetX.roundToInt(), 0) }
}

// Glow effect for active elements
@Composable
fun Modifier.glowAnimation(
    isActive: Boolean,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Blue,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.3f else 0f,
        animationSpec = if (isActive) {
            infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(config.duration)
        },
        label = "glow"
    )
    
    return this.graphicsLayer {
        shadowElevation = if (isActive) 8f else 0f
        // Custom glow effect would be implemented here
    }
}

// Stagger animation for lists
@Composable
fun getStaggeredDelay(index: Int, baseDelay: Int = 50): Int {
    return index * baseDelay
}

// Morphing animation between UI states
@Composable
fun Modifier.morphAnimation(
    targetState: String,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val transition = updateTransition(targetState = targetState, label = "morph")
    
    val cornerRadius by transition.animateDp(
        transitionSpec = {
            tween(
                durationMillis = config.duration,
                easing = config.easing
            )
        },
        label = "corner_radius"
    ) { state ->
        when (state) {
            "card" -> 12.dp
            "button" -> 24.dp
            "circle" -> 50.dp
            else -> 8.dp
        }
    }
    
    return this.graphicsLayer {
        // Corner radius animation would be applied here
    }
}

// Particle effect animation (simplified)
@Composable
fun Modifier.particleAnimation(
    isActive: Boolean,
    particleCount: Int = 5,
    config: AnimationConfig = AnimationConfig()
): Modifier {
    val particles = remember { List(particleCount) { ParticleState() } }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            // Animate particles - simplified implementation
            particles.forEach { particle ->
                particle.animate()
            }
        }
    }
    
    return this.graphicsLayer {
        // Particle rendering would be implemented here
    }
}

private class ParticleState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(1f)
    
    suspend fun animate() {
        // Simplified particle animation
        // In a real implementation, this would use coroutines to animate position and alpha
    }
}