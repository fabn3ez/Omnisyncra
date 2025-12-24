package com.omnisyncra.ui.visual

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/**
 * Immersive visual effects system for Omnisyncra
 * Creates stunning proximity visualizations and mesh network effects
 */

data class DeviceNode(
    val id: String,
    val name: String,
    val position: Offset,
    val isConnected: Boolean,
    val signalStrength: Float,
    val deviceType: String,
    val color: Color
)

data class ConnectionLine(
    val from: DeviceNode,
    val to: DeviceNode,
    val strength: Float,
    val isActive: Boolean
)

class MeshVisualizationState {
    private val _nodes = mutableStateListOf<DeviceNode>()
    val nodes: List<DeviceNode> = _nodes
    
    private val _connections = mutableStateListOf<ConnectionLine>()
    val connections: List<ConnectionLine> = _connections
    
    private val _animationProgress = mutableFloatStateOf(0f)
    val animationProgress: Float by _animationProgress
    
    fun addNode(node: DeviceNode) {
        _nodes.add(node)
        updateConnections()
    }
    
    fun removeNode(nodeId: String) {
        _nodes.removeAll { it.id == nodeId }
        updateConnections()
    }
    
    fun updateNodePosition(nodeId: String, position: Offset) {
        val index = _nodes.indexOfFirst { it.id == nodeId }
        if (index >= 0) {
            _nodes[index] = _nodes[index].copy(position = position)
            updateConnections()
        }
    }
    
    private fun updateConnections() {
        _connections.clear()
        for (i in _nodes.indices) {
            for (j in i + 1 until _nodes.size) {
                val node1 = _nodes[i]
                val node2 = _nodes[j]
                val distance = (node1.position - node2.position).getDistance()
                
                if (distance < 300f) { // Connection threshold
                    val strength = 1f - (distance / 300f)
                    _connections.add(
                        ConnectionLine(
                            from = node1,
                            to = node2,
                            strength = strength,
                            isActive = node1.isConnected && node2.isConnected
                        )
                    )
                }
            }
        }
    }
    
    suspend fun startAnimation() {
        while (true) {
            _animationProgress.floatValue = (_animationProgress.floatValue + 0.01f) % 1f
            delay(16) // ~60fps
        }
    }
}

@Composable
fun ProximityVisualization(
    devices: List<DeviceNode>,
    modifier: Modifier = Modifier,
    showConnections: Boolean = true,
    showSignalRings: Boolean = true
) {
    val meshState = remember { MeshVisualizationState() }
    
    LaunchedEffect(devices) {
        devices.forEach { device ->
            meshState.addNode(device)
        }
        meshState.startAnimation()
    }
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        drawMeshNetwork(
            meshState = meshState,
            showConnections = showConnections,
            showSignalRings = showSignalRings
        )
    }
}

private fun DrawScope.drawMeshNetwork(
    meshState: MeshVisualizationState,
    showConnections: Boolean,
    showSignalRings: Boolean
) {
    // Draw connections first (behind nodes)
    if (showConnections) {
        meshState.connections.forEach { connection ->
            drawConnection(connection, meshState.animationProgress)
        }
    }
    
    // Draw signal rings
    if (showSignalRings) {
        meshState.nodes.forEach { node ->
            drawSignalRings(node, meshState.animationProgress)
        }
    }
    
    // Draw nodes on top
    meshState.nodes.forEach { node ->
        drawDeviceNode(node, meshState.animationProgress)
    }
}

private fun DrawScope.drawConnection(
    connection: ConnectionLine,
    animationProgress: Float
) {
    val alpha = if (connection.isActive) {
        0.6f + 0.4f * sin(animationProgress * 2 * PI).toFloat()
    } else {
        0.2f
    }
    
    val strokeWidth = 2f + connection.strength * 4f
    
    // Animated gradient along the connection
    val gradient = Brush.linearGradient(
        colors = listOf(
            connection.from.color.copy(alpha = alpha),
            connection.to.color.copy(alpha = alpha)
        ),
        start = connection.from.position,
        end = connection.to.position
    )
    
    drawLine(
        brush = gradient,
        start = connection.from.position,
        end = connection.to.position,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Animated pulse along the line
    if (connection.isActive) {
        val pulsePosition = (animationProgress * 2) % 1f
        val pulsePoint = Offset(
            x = connection.from.position.x + (connection.to.position.x - connection.from.position.x) * pulsePosition,
            y = connection.from.position.y + (connection.to.position.y - connection.from.position.y) * pulsePosition
        )
        
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = pulsePoint,
            alpha = 1f - pulsePosition
        )
    }
}

private fun DrawScope.drawSignalRings(
    node: DeviceNode,
    animationProgress: Float
) {
    if (!node.isConnected) return
    
    val maxRadius = 100f * node.signalStrength
    val ringCount = 3
    
    repeat(ringCount) { ring ->
        val ringProgress = (animationProgress + ring * 0.33f) % 1f
        val radius = maxRadius * ringProgress
        val alpha = (1f - ringProgress) * 0.3f
        
        drawCircle(
            color = node.color.copy(alpha = alpha),
            radius = radius,
            center = node.position,
            style = Stroke(width = 2f)
        )
    }
}

private fun DrawScope.drawDeviceNode(
    node: DeviceNode,
    animationProgress: Float
) {
    val baseRadius = 20f
    val pulseRadius = if (node.isConnected) {
        baseRadius + 5f * sin(animationProgress * 4 * PI).toFloat()
    } else {
        baseRadius
    }
    
    // Outer glow
    drawCircle(
        color = node.color.copy(alpha = 0.3f),
        radius = pulseRadius + 10f,
        center = node.position
    )
    
    // Main node
    drawCircle(
        color = node.color,
        radius = pulseRadius,
        center = node.position
    )
    
    // Inner highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.6f),
        radius = pulseRadius * 0.3f,
        center = node.position + Offset(-5f, -5f)
    )
    
    // Connection status indicator
    val statusColor = if (node.isConnected) Color.Green else Color.Red
    drawCircle(
        color = statusColor,
        radius = 4f,
        center = node.position + Offset(15f, -15f)
    )
}

/**
 * Ambient lighting effects
 */
@Composable
fun AmbientLighting(
    intensity: Float = 0.5f,
    color: Color = Color.Blue,
    modifier: Modifier = Modifier
) {
    val animatedIntensity by rememberInfiniteTransition(label = "ambient").animateFloat(
        initialValue = intensity * 0.5f,
        targetValue = intensity,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "intensity"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = animatedIntensity),
                        Color.Transparent
                    ),
                    radius = 1000f
                )
            )
    )
}

/**
 * Particle system for mesh visualization
 */
@Composable
fun ParticleSystem(
    particleCount: Int = 50,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val particles = remember {
        List(particleCount) {
            Particle(
                position = Offset(Random.nextFloat() * 1000f, Random.nextFloat() * 1000f),
                velocity = Offset(
                    Random.nextFloat() * 2f - 1f,
                    Random.nextFloat() * 2f - 1f
                ),
                color = Color.White.copy(alpha = Random.nextFloat() * 0.5f + 0.2f),
                size = Random.nextFloat() * 3f + 1f
            )
        }
    }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) {
                particles.forEach { it.update() }
                delay(16) // ~60fps
            }
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            drawCircle(
                color = particle.color,
                radius = particle.size,
                center = particle.position
            )
        }
    }
}

private class Particle(
    var position: Offset,
    var velocity: Offset,
    val color: Color,
    val size: Float
) {
    fun update() {
        position += velocity
        
        // Wrap around screen
        if (position.x < 0 || position.x > 1000f) {
            velocity = velocity.copy(x = -velocity.x)
        }
        if (position.y < 0 || position.y > 1000f) {
            velocity = velocity.copy(y = -velocity.y)
        }
    }
}

/**
 * Parallax effect system
 */
@Composable
fun ParallaxLayer(
    offset: Offset,
    speed: Float = 0.5f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.offset(
            x = (offset.x * speed).dp,
            y = (offset.y * speed).dp
        )
    ) {
        content()
    }
}

/**
 * Depth-based visual effects
 */
@Composable
fun DepthLayer(
    depth: Float, // 0f = foreground, 1f = background
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha = 1f - (depth * 0.5f)
    val scale = 1f - (depth * 0.2f)
    
    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
    }
}