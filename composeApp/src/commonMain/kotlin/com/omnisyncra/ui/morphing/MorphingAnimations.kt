package com.omnisyncra.ui.morphing

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Core morphing animation system for Omnisyncra
 * Handles device role transitions and context-aware UI morphing
 */

enum class DeviceRole {
    PRIMARY,    // Main editing/control device
    SECONDARY,  // Supporting/palette device
    VIEWER,     // Read-only display device
    COMPUTE     // Background processing device
}

enum class UIContext {
    EDITOR,     // Main content editing
    PALETTE,    // Tool/color palette
    VIEWER,     // Content viewing
    MESH,       // Network visualization
    SETTINGS    // Configuration
}

data class MorphingState(
    val currentRole: DeviceRole = DeviceRole.PRIMARY,
    val targetRole: DeviceRole = DeviceRole.PRIMARY,
    val currentContext: UIContext = UIContext.EDITOR,
    val targetContext: UIContext = UIContext.EDITOR,
    val isTransitioning: Boolean = false,
    val transitionProgress: Float = 0f
)

class MorphingController {
    private val _state = mutableStateOf(MorphingState())
    val state: State<MorphingState> = _state
    
    private val transitionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    suspend fun morphToRole(targetRole: DeviceRole) {
        if (_state.value.currentRole == targetRole) return
        
        _state.value = _state.value.copy(
            targetRole = targetRole,
            isTransitioning = true
        )
        
        // Simulate transition animation
        animateTransition { progress ->
            _state.value = _state.value.copy(transitionProgress = progress)
        }
        
        _state.value = _state.value.copy(
            currentRole = targetRole,
            isTransitioning = false,
            transitionProgress = 0f
        )
    }
    
    suspend fun morphToContext(targetContext: UIContext) {
        if (_state.value.currentContext == targetContext) return
        
        _state.value = _state.value.copy(
            targetContext = targetContext,
            isTransitioning = true
        )
        
        animateTransition { progress ->
            _state.value = _state.value.copy(transitionProgress = progress)
        }
        
        _state.value = _state.value.copy(
            currentContext = targetContext,
            isTransitioning = false,
            transitionProgress = 0f
        )
    }
    
    private suspend fun animateTransition(onProgress: (Float) -> Unit) {
        val duration = 800L
        val steps = 60
        val stepDelay = duration / steps
        
        repeat(steps) { step ->
            val progress = (step + 1).toFloat() / steps
            val easedProgress = easeInOutCubic(progress)
            onProgress(easedProgress)
            delay(stepDelay)
        }
    }
    
    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4 * t * t * t
        } else {
            1 - (-2 * t + 2).let { it * it * it } / 2
        }
    }
}

/**
 * Morphing transition animations
 */
@Composable
fun rememberMorphingTransition(
    targetState: Any,
    label: String = "MorphingTransition"
): Transition<Any> {
    return updateTransition(
        targetState = targetState,
        label = label
    )
}

@Composable
fun MorphingContainer(
    morphingState: MorphingState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (morphingState.isTransitioning) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "morphing_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (morphingState.isTransitioning) 0.8f else 1f,
        animationSpec = tween(400),
        label = "morphing_alpha"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    ) {
        content()
    }
}

/**
 * Role-specific morphing animations
 */
@Composable
fun RoleMorphingModifier(
    currentRole: DeviceRole,
    targetRole: DeviceRole,
    progress: Float
): Modifier {
    val rotationY by animateFloatAsState(
        targetValue = when (targetRole) {
            DeviceRole.PRIMARY -> 0f
            DeviceRole.SECONDARY -> 15f
            DeviceRole.VIEWER -> -10f
            DeviceRole.COMPUTE -> 5f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "role_rotation"
    )
    
    val elevation by animateFloatAsState(
        targetValue = when (targetRole) {
            DeviceRole.PRIMARY -> 8f
            DeviceRole.SECONDARY -> 4f
            DeviceRole.VIEWER -> 2f
            DeviceRole.COMPUTE -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "role_elevation"
    )
    
    return Modifier.graphicsLayer {
        this.rotationY = rotationY
        this.shadowElevation = elevation
    }
}

/**
 * Context-aware layout transitions
 */
@Composable
fun ContextMorphingLayout(
    context: UIContext,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val arrangement by animateFloatAsState(
        targetValue = when (context) {
            UIContext.EDITOR -> 16f
            UIContext.PALETTE -> 8f
            UIContext.VIEWER -> 24f
            UIContext.MESH -> 12f
            UIContext.SETTINGS -> 20f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "context_spacing"
    )
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(arrangement.dp)
    ) {
        content()
    }
}

/**
 * Particle effect system for mesh visualization
 */
@Composable
fun ParticleEffect(
    isActive: Boolean,
    particleCount: Int = 20,
    modifier: Modifier = Modifier
) {
    val particles = remember { 
        List(particleCount) { 
            ParticleState(
                x = (0..100).random().toFloat(),
                y = (0..100).random().toFloat(),
                velocity = (1..3).random().toFloat()
            )
        }
    }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) {
                particles.forEach { particle ->
                    particle.update()
                }
                delay(16) // ~60fps
            }
        }
    }
    
    // Particle rendering would go here
    // For now, we'll use a placeholder
    Box(modifier = modifier)
}

private class ParticleState(
    var x: Float,
    var y: Float,
    var velocity: Float
) {
    fun update() {
        x += velocity * 0.1f
        y += velocity * 0.05f
        
        // Wrap around screen
        if (x > 100f) x = 0f
        if (y > 100f) y = 0f
    }
}