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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

data class MeshNode(
    val id: String,
    val position: Offset,
    val velocity: Offset = Offset.Zero,
    val connections: List<String> = emptyList(),
    val activity: Float = 0f,
    val nodeType: MeshNodeType = MeshNodeType.PEER
)

enum class MeshNodeType {
    COORDINATOR, PEER, EDGE, COMPUTE
}

data class MeshConnection(
    val from: String,
    val to: String,
    val bandwidth: Float,
    val latency: Float,
    val packetFlow: Float = 0f,
    val isActive: Boolean = true
)

@Composable
fun MeshNetworkVisualization(
    nodes: List<MeshNode> = emptyList(),
    connections: List<MeshConnection> = emptyList(),
    modifier: Modifier = Modifier,
    showDataFlow: Boolean = true,
    showNetworkStats: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    
    // Network pulse animation
    val networkPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "networkPulse"
    )
    
    // Data packet flow
    val dataFlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "dataFlow"
    )
    
    // Node activity animation
    val activityPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "activity"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Draw network background grid
        drawNetworkGrid(networkPulse, primaryColor)
        
        // Draw connections with data flow
        if (showDataFlow) {
            connections.forEach { connection ->
                val fromNode = nodes.find { it.id == connection.from }
                val toNode = nodes.find { it.id == connection.to }
                
                if (fromNode != null && toNode != null) {
                    drawMeshConnection(
                        from = fromNode.position,
                        to = toNode.position,
                        connection = connection,
                        dataFlow = dataFlow,
                        color = when {
                            connection.bandwidth > 0.8f -> primaryColor
                            connection.bandwidth > 0.5f -> secondaryColor
                            else -> tertiaryColor
                        }
                    )
                }
            }
        }
        
        // Draw mesh nodes
        nodes.forEach { node ->
            drawMeshNode(
                node = node,
                activityPulse = activityPulse,
                color = when (node.nodeType) {
                    MeshNodeType.COORDINATOR -> primaryColor
                    MeshNodeType.PEER -> secondaryColor
                    MeshNodeType.EDGE -> tertiaryColor
                    MeshNodeType.COMPUTE -> primaryColor.copy(red = 0.8f)
                }
            )
        }
        
        // Draw network statistics overlay
        if (showNetworkStats) {
            drawNetworkStats(nodes, connections, surfaceColor)
        }
    }
}

private fun DrawScope.drawNetworkGrid(
    pulse: Float,
    color: Color
) {
    val gridSpacing = 50.dp.toPx()
    val alpha = 0.05f + pulse * 0.03f
    
    // Vertical lines
    var x = 0f
    while (x <= size.width) {
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 0.5.dp.toPx()
        )
        x += gridSpacing
    }
    
    // Horizontal lines
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 0.5.dp.toPx()
        )
        y += gridSpacing
    }
}

private fun DrawScope.drawMeshConnection(
    from: Offset,
    to: Offset,
    connection: MeshConnection,
    dataFlow: Float,
    color: Color
) {
    val strokeWidth = (1f + connection.bandwidth * 3f).dp.toPx()
    val alpha = if (connection.isActive) 0.4f + connection.bandwidth * 0.6f else 0.2f
    
    // Main connection line
    drawLine(
        color = color.copy(alpha = alpha),
        start = from,
        end = to,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Bandwidth indicator (thicker line for high bandwidth)
    if (connection.bandwidth > 0.7f) {
        drawLine(
            color = color.copy(alpha = alpha * 0.3f),
            start = from,
            end = to,
            strokeWidth = strokeWidth * 2f,
            cap = StrokeCap.Round
        )
    }
    
    // Data packet visualization
    if (connection.isActive && connection.packetFlow > 0f) {
        val distance = sqrt((to.x - from.x).pow(2) + (to.y - from.y).pow(2))
        val direction = Offset((to.x - from.x) / distance, (to.y - from.y) / distance)
        
        repeat((connection.packetFlow * 5).toInt()) { i ->
            val packetOffset = (dataFlow + i * 0.2f) % 1f
            val packetPos = Offset(
                from.x + direction.x * distance * packetOffset,
                from.y + direction.y * distance * packetOffset
            )
            
            // Data packet
            drawCircle(
                color = color.copy(alpha = 0.8f),
                radius = 2.dp.toPx(),
                center = packetPos
            )
            
            // Packet trail
            val trailLength = 20f
            repeat(3) { j ->
                val trailOffset = packetOffset - (j + 1) * 0.02f
                if (trailOffset > 0f) {
                    val trailPos = Offset(
                        from.x + direction.x * distance * trailOffset,
                        from.y + direction.y * distance * trailOffset
                    )
                    drawCircle(
                        color = color.copy(alpha = 0.3f - j * 0.1f),
                        radius = (2f - j * 0.5f).dp.toPx(),
                        center = trailPos
                    )
                }
            }
        }
    }
    
    // Latency indicator (color coding)
    if (connection.latency > 50f) {
        val midPoint = Offset((from.x + to.x) / 2, (from.y + to.y) / 2)
        drawCircle(
            color = Color.Red.copy(alpha = 0.6f),
            radius = 3.dp.toPx(),
            center = midPoint
        )
    }
}

private fun DrawScope.drawMeshNode(
    node: MeshNode,
    activityPulse: Float,
    color: Color
) {
    val baseRadius = when (node.nodeType) {
        MeshNodeType.COORDINATOR -> 25.dp.toPx()
        MeshNodeType.COMPUTE -> 22.dp.toPx()
        MeshNodeType.PEER -> 18.dp.toPx()
        MeshNodeType.EDGE -> 15.dp.toPx()
    }
    
    val activityScale = 1f + node.activity * activityPulse * 0.3f
    val radius = baseRadius * activityScale
    
    // Node shadow/glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.3f),
                Color.Transparent
            ),
            center = node.position,
            radius = radius * 2f
        ),
        radius = radius * 2f,
        center = node.position
    )
    
    // Main node body
    drawCircle(
        color = color.copy(alpha = 0.8f),
        radius = radius,
        center = node.position
    )
    
    // Node type indicator
    when (node.nodeType) {
        MeshNodeType.COORDINATOR -> {
            // Central star pattern
            repeat(8) { i ->
                val angle = i * 45f
                val rayStart = node.position
                val rayEnd = Offset(
                    node.position.x + cos(angle * PI / 180f).toFloat() * radius * 0.7f,
                    node.position.y + sin(angle * PI / 180f).toFloat() * radius * 0.7f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = rayStart,
                    end = rayEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        MeshNodeType.COMPUTE -> {
            // Square pattern
            val squareSize = radius * 0.6f
            drawRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(
                    node.position.x - squareSize / 2,
                    node.position.y - squareSize / 2
                ),
                size = androidx.compose.ui.geometry.Size(squareSize, squareSize)
            )
        }
        MeshNodeType.PEER -> {
            // Triangle pattern
            val triangleSize = radius * 0.6f
            val path = Path().apply {
                moveTo(node.position.x, node.position.y - triangleSize / 2)
                lineTo(node.position.x - triangleSize / 2, node.position.y + triangleSize / 2)
                lineTo(node.position.x + triangleSize / 2, node.position.y + triangleSize / 2)
                close()
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        MeshNodeType.EDGE -> {
            // Simple dot
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = radius * 0.3f,
                center = node.position
            )
        }
    }
    
    // Activity indicator ring
    if (node.activity > 0.1f) {
        drawCircle(
            color = color.copy(alpha = node.activity * 0.5f),
            radius = radius * (1.2f + activityPulse * 0.2f),
            center = node.position,
            style = Stroke(width = 2.dp.toPx())
        )
    }
    
    // Connection count indicator
    if (node.connections.isNotEmpty()) {
        val connectionCount = node.connections.size
        val indicatorRadius = 8.dp.toPx()
        val indicatorPos = Offset(
            node.position.x + radius * 0.7f,
            node.position.y - radius * 0.7f
        )
        
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = indicatorRadius,
            center = indicatorPos
        )
        
        // Connection count would be drawn as text in a real implementation
        // For now, we'll use a simple visual indicator
        repeat(minOf(connectionCount, 4)) { i ->
            val dotAngle = i * 90f
            val dotPos = Offset(
                indicatorPos.x + cos(dotAngle * PI / 180f).toFloat() * indicatorRadius * 0.5f,
                indicatorPos.y + sin(dotAngle * PI / 180f).toFloat() * indicatorRadius * 0.5f
            )
            drawCircle(
                color = color,
                radius = 1.dp.toPx(),
                center = dotPos
            )
        }
    }
}

private fun DrawScope.drawNetworkStats(
    nodes: List<MeshNode>,
    connections: List<MeshConnection>,
    backgroundColor: Color
) {
    val statsArea = androidx.compose.ui.geometry.Rect(
        offset = Offset(20.dp.toPx(), 20.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(200.dp.toPx(), 100.dp.toPx())
    )
    
    // Stats background
    drawRoundRect(
        color = backgroundColor.copy(alpha = 0.8f),
        topLeft = statsArea.topLeft,
        size = statsArea.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
    )
    
    // Stats content would be drawn as text in a real implementation
    // For now, we'll use visual indicators
    val activeConnections = connections.count { it.isActive }
    val totalBandwidth = connections.sumOf { it.bandwidth.toDouble() }.toFloat()
    val avgLatency = if (connections.isNotEmpty()) {
        connections.map { it.latency }.average().toFloat()
    } else 0f
    
    // Visual representation of stats
    val barHeight = 8.dp.toPx()
    val barY = statsArea.top + 30.dp.toPx()
    
    // Active connections bar
    drawRect(
        color = Color.Green.copy(alpha = 0.7f),
        topLeft = Offset(statsArea.left + 10.dp.toPx(), barY),
        size = androidx.compose.ui.geometry.Size(
            (activeConnections / maxOf(connections.size, 1).toFloat()) * 150.dp.toPx(),
            barHeight
        )
    )
    
    // Bandwidth bar
    drawRect(
        color = Color.Blue.copy(alpha = 0.7f),
        topLeft = Offset(statsArea.left + 10.dp.toPx(), barY + 20.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(
            (totalBandwidth / connections.size.coerceAtLeast(1)) * 150.dp.toPx(),
            barHeight
        )
    )
    
    // Latency indicator (red for high latency)
    val latencyColor = when {
        avgLatency > 100f -> Color.Red
        avgLatency > 50f -> Color.Yellow
        else -> Color.Green
    }
    
    drawRect(
        color = latencyColor.copy(alpha = 0.7f),
        topLeft = Offset(statsArea.left + 10.dp.toPx(), barY + 40.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(
            (avgLatency / 200f).coerceAtMost(1f) * 150.dp.toPx(),
            barHeight
        )
    )
}

@Composable
fun rememberMeshNodes(): State<List<MeshNode>> {
    return remember {
        mutableStateOf(
            listOf(
                MeshNode(
                    "coordinator", 
                    Offset(300f, 200f), 
                    connections = listOf("peer1", "peer2", "compute1"),
                    activity = 0.9f,
                    nodeType = MeshNodeType.COORDINATOR
                ),
                MeshNode(
                    "peer1", 
                    Offset(150f, 350f),
                    connections = listOf("coordinator", "edge1"),
                    activity = 0.6f,
                    nodeType = MeshNodeType.PEER
                ),
                MeshNode(
                    "peer2", 
                    Offset(450f, 350f),
                    connections = listOf("coordinator", "compute1"),
                    activity = 0.7f,
                    nodeType = MeshNodeType.PEER
                ),
                MeshNode(
                    "compute1", 
                    Offset(300f, 450f),
                    connections = listOf("coordinator", "peer2"),
                    activity = 0.8f,
                    nodeType = MeshNodeType.COMPUTE
                ),
                MeshNode(
                    "edge1", 
                    Offset(50f, 300f),
                    connections = listOf("peer1"),
                    activity = 0.3f,
                    nodeType = MeshNodeType.EDGE
                )
            )
        )
    }
}

@Composable
fun rememberMeshConnections(): State<List<MeshConnection>> {
    return remember {
        mutableStateOf(
            listOf(
                MeshConnection("coordinator", "peer1", 0.9f, 15f, 0.8f, true),
                MeshConnection("coordinator", "peer2", 0.8f, 20f, 0.6f, true),
                MeshConnection("coordinator", "compute1", 0.95f, 10f, 0.9f, true),
                MeshConnection("peer1", "edge1", 0.6f, 35f, 0.4f, true),
                MeshConnection("peer2", "compute1", 0.7f, 25f, 0.5f, true)
            )
        )
    }
}