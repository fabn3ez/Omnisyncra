package com.omnisyncra.ui.visual

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

data class ProximityDevice(
    val id: String,
    val position: Offset,
    val strength: Float = 1f,
    val isConnected: Boolean = true,
    val deviceType: String = "mobile"
)

data class Connection(
    val from: String,
    val to: String,
    val strength: Float,
    val latency: Float = 0f,
    val isActive: Boolean = true
)

@Composable
fun ProximityVisualization(
    devices: List<ProximityDevice> = emptyList(),
    connections: List<Connection> = emptyList(),
    modifier: Modifier = Modifier,
    showPulse: Boolean = true,
    showConnections: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "proximity")
    
    // Pulsing animation for device nodes
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Rotation for connection indicators
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    
    // Connection flow animation
    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "flow"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        
        // Draw ambient background glow
        drawAmbientGlow(center, primaryColor, pulseScale)
        
        // Draw connections first (behind nodes)
        if (showConnections) {
            connections.forEach { connection ->
                val fromDevice = devices.find { it.id == connection.from }
                val toDevice = devices.find { it.id == connection.to }
                
                if (fromDevice != null && toDevice != null) {
                    drawConnection(
                        from = fromDevice.position,
                        to = toDevice.position,
                        strength = connection.strength,
                        flowOffset = flowOffset,
                        color = when {
                            connection.strength > 0.8f -> primaryColor
                            connection.strength > 0.5f -> secondaryColor
                            else -> tertiaryColor
                        }
                    )
                }
            }
        }
        
        // Draw device nodes
        devices.forEach { device ->
            drawDeviceNode(
                position = device.position,
                strength = device.strength,
                pulseScale = if (showPulse) pulseScale else 1f,
                rotation = rotation,
                color = when (device.deviceType) {
                    "primary" -> primaryColor
                    "secondary" -> secondaryColor
                    "compute" -> tertiaryColor
                    else -> primaryColor.copy(alpha = 0.7f)
                },
                isConnected = device.isConnected
            )
        }
        
        // Draw proximity rings
        drawProximityRings(center, pulseScale, primaryColor)
    }
}

private fun DrawScope.drawAmbientGlow(
    center: Offset,
    color: Color,
    scale: Float
) {
    val radius = minOf(size.width, size.height) * 0.4f * scale
    
    val gradient = RadialGradientShader(
        center = center,
        radius = radius,
        colors = listOf(
            color.copy(alpha = 0.1f),
            color.copy(alpha = 0.05f),
            Color.Transparent
        ),
        colorStops = listOf(0f, 0.7f, 1f)
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.1f),
                color.copy(alpha = 0.05f),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawConnection(
    from: Offset,
    to: Offset,
    strength: Float,
    flowOffset: Float,
    color: Color
) {
    val strokeWidth = (2f + strength * 4f).dp.toPx()
    val alpha = 0.3f + strength * 0.7f
    
    // Main connection line
    drawLine(
        color = color.copy(alpha = alpha),
        start = from,
        end = to,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Animated flow particles
    val distance = sqrt((to.x - from.x).pow(2) + (to.y - from.y).pow(2))
    val direction = Offset((to.x - from.x) / distance, (to.y - from.y) / distance)
    
    repeat(3) { i ->
        val particleOffset = (flowOffset + i * 0.33f) % 1f
        val particlePos = Offset(
            from.x + direction.x * distance * particleOffset,
            from.y + direction.y * distance * particleOffset
        )
        
        drawCircle(
            color = color.copy(alpha = alpha * 0.8f),
            radius = 3.dp.toPx(),
            center = particlePos
        )
    }
}

private fun DrawScope.drawDeviceNode(
    position: Offset,
    strength: Float,
    pulseScale: Float,
    rotation: Float,
    color: Color,
    isConnected: Boolean
) {
    val baseRadius = 20.dp.toPx()
    val radius = baseRadius * strength * pulseScale
    
    // Outer glow ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.3f),
                Color.Transparent
            ),
            center = position,
            radius = radius * 1.5f
        ),
        radius = radius * 1.5f,
        center = position
    )
    
    // Main node
    drawCircle(
        color = color.copy(alpha = if (isConnected) 0.9f else 0.5f),
        radius = radius,
        center = position
    )
    
    // Inner core
    drawCircle(
        color = Color.White.copy(alpha = 0.8f),
        radius = radius * 0.3f,
        center = position
    )
    
    // Rotating connection indicator
    if (isConnected) {
        rotate(rotation, position) {
            repeat(4) { i ->
                val angle = i * 90f
                val indicatorPos = Offset(
                    position.x + cos(angle * PI / 180f).toFloat() * radius * 1.3f,
                    position.y + sin(angle * PI / 180f).toFloat() * radius * 1.3f
                )
                
                drawCircle(
                    color = color.copy(alpha = 0.6f),
                    radius = 2.dp.toPx(),
                    center = indicatorPos
                )
            }
        }
    }
}

private fun DrawScope.drawProximityRings(
    center: Offset,
    scale: Float,
    color: Color
) {
    repeat(3) { i ->
        val radius = (100 + i * 80).dp.toPx() * scale
        val alpha = (0.1f - i * 0.03f) * scale
        
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
fun rememberProximityDevices(): State<List<ProximityDevice>> {
    return remember {
        mutableStateOf(
            listOf(
                ProximityDevice("primary", Offset(200f, 300f), 1f, true, "primary"),
                ProximityDevice("secondary", Offset(400f, 200f), 0.8f, true, "secondary"),
                ProximityDevice("compute", Offset(300f, 450f), 0.9f, true, "compute"),
                ProximityDevice("viewer", Offset(500f, 400f), 0.6f, false, "viewer")
            )
        )
    }
}

@Composable
fun rememberProximityConnections(): State<List<Connection>> {
    return remember {
        mutableStateOf(
            listOf(
                Connection("primary", "secondary", 0.9f, 12f, true),
                Connection("primary", "compute", 0.7f, 25f, true),
                Connection("secondary", "compute", 0.6f, 18f, true),
                Connection("compute", "viewer", 0.4f, 45f, false)
            )
        )
    }
}