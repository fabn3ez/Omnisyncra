package com.omnisyncra.ui.visual

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

data class ParallaxLayer(
    val id: String,
    val elements: List<ParallaxElement>,
    val depth: Float, // 0.0 (background) to 1.0 (foreground)
    val speed: Float = 1f - depth, // Inverse relationship with depth
    val opacity: Float = 1f - depth * 0.5f
)

data class ParallaxElement(
    val position: Offset,
    val size: Float,
    val color: Color,
    val shape: ParallaxShape = ParallaxShape.CIRCLE,
    val rotation: Float = 0f
)

enum class ParallaxShape {
    CIRCLE, SQUARE, TRIANGLE, STAR, HEXAGON
}

@Composable
fun ParallaxEffects(
    layers: List<ParallaxLayer> = emptyList(),
    modifier: Modifier = Modifier,
    enableInteraction: Boolean = true,
    autoScroll: Boolean = true,
    scrollSpeed: Float = 0.5f
) {
        var offset by remember { mutableStateOf(Offset.Zero) }
    val infiniteTransition = rememberInfiniteTransition(label = "parallax")
    
    // Auto-scroll animation
    val autoScrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (autoScroll) 1000f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (20000 / scrollSpeed).toInt(),
                easing = LinearEasing
            )
        ),
        label = "autoScroll"
    )
    
    // Floating animation for depth
    val floatAnimation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    
    // Rotation animation
    val rotationAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enableInteraction) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            offset = offset + dragAmount
                        }
                    }
                } else Modifier
            )
    ) {
        val totalOffset = if (autoScroll) {
            Offset(offset.x + autoScrollOffset, offset.y + floatAnimation)
        } else {
            offset
        }
        
        // Draw layers from background to foreground (sorted by depth)
        layers.sortedBy { it.depth }.forEach { layer ->
            drawParallaxLayer(
                layer = layer,
                offset = totalOffset,
                rotation = rotationAnimation,
                canvasSize = size
            )
        }
        
        // Add depth fog effect
        drawDepthFog(primaryColor)
    }
}

private fun DrawScope.drawParallaxLayer(
    layer: ParallaxLayer,
    offset: Offset,
    rotation: Float,
    canvasSize: androidx.compose.ui.geometry.Size
) {
    val layerOffset = Offset(
        offset.x * layer.speed,
        offset.y * layer.speed
    )
    
    translate(layerOffset.x, layerOffset.y) {
        layer.elements.forEach { element ->
            drawParallaxElement(
                element = element,
                layer = layer,
                rotation = rotation,
                canvasSize = canvasSize
            )
        }
    }
}

private fun DrawScope.drawParallaxElement(
    element: ParallaxElement,
    layer: ParallaxLayer,
    rotation: Float,
    canvasSize: androidx.compose.ui.geometry.Size
) {
    val adjustedColor = element.color.copy(alpha = element.color.alpha * layer.opacity)
    val adjustedSize = element.size * (0.5f + layer.depth * 0.5f) // Closer objects are larger
    val elementRotation = element.rotation + rotation * (1f - layer.depth) // Background rotates slower
    
    // Add subtle glow for depth
    if (layer.depth > 0.7f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    adjustedColor.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = element.position,
                radius = adjustedSize * 1.5f
            ),
            radius = adjustedSize * 1.5f,
            center = element.position
        )
    }
    
    when (element.shape) {
        ParallaxShape.CIRCLE -> {
            drawCircle(
                color = adjustedColor,
                radius = adjustedSize,
                center = element.position
            )
        }
        
        ParallaxShape.SQUARE -> {
            drawRect(
                color = adjustedColor,
                topLeft = Offset(
                    element.position.x - adjustedSize,
                    element.position.y - adjustedSize
                ),
                size = androidx.compose.ui.geometry.Size(adjustedSize * 2, adjustedSize * 2)
            )
        }
        
        ParallaxShape.TRIANGLE -> {
            val path = Path().apply {
                moveTo(element.position.x, element.position.y - adjustedSize)
                lineTo(element.position.x - adjustedSize, element.position.y + adjustedSize)
                lineTo(element.position.x + adjustedSize, element.position.y + adjustedSize)
                close()
            }
            drawPath(path, adjustedColor)
        }
        
        ParallaxShape.STAR -> {
            drawStar(element.position, adjustedSize, adjustedColor, elementRotation)
        }
        
        ParallaxShape.HEXAGON -> {
            drawHexagon(element.position, adjustedSize, adjustedColor, elementRotation)
        }
    }
    
    // Add sparkle effect for foreground elements
    if (layer.depth > 0.8f) {
        drawSparkle(element.position, adjustedSize * 0.3f, adjustedColor, rotation)
    }
}

private fun DrawScope.drawStar(
    center: Offset,
    size: Float,
    color: Color,
    rotation: Float
) {
    val path = Path()
    val outerRadius = size
    val innerRadius = size * 0.4f
    val points = 5
    
    for (i in 0 until points * 2) {
        val angle = (i * 36f + rotation) * PI / 180f
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + cos(angle).toFloat() * radius
        val y = center.y + sin(angle).toFloat() * radius
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    
    drawPath(path, color)
}

private fun DrawScope.drawHexagon(
    center: Offset,
    size: Float,
    color: Color,
    rotation: Float
) {
    val path = Path()
    val points = 6
    
    for (i in 0 until points) {
        val angle = (i * 60f + rotation) * PI / 180f
        val x = center.x + cos(angle).toFloat() * size
        val y = center.y + sin(angle).toFloat() * size
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    
    drawPath(path, color)
}

private fun DrawScope.drawSparkle(
    center: Offset,
    size: Float,
    color: Color,
    rotation: Float
) {
    // Draw a simple sparkle with 4 rays
    val rayLength = size
    val angles = listOf(0f, 45f, 90f, 135f)
    
    angles.forEach { baseAngle ->
        val angle = (baseAngle + rotation) * PI / 180f
        val endPoint = Offset(
            center.x + cos(angle).toFloat() * rayLength,
            center.y + sin(angle).toFloat() * rayLength
        )
        
        drawLine(
            color = color.copy(alpha = 0.8f),
            start = center,
            end = endPoint,
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
    
    // Central sparkle point
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = size * 0.2f,
        center = center
    )
}

private fun DrawScope.drawDepthFog(color: Color) {
    // Add atmospheric perspective with subtle fog
    val fogGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            color.copy(alpha = 0.05f),
            color.copy(alpha = 0.1f)
        ),
        startY = 0f,
        endY = size.height
    )
    
    drawRect(
        brush = fogGradient,
        size = size
    )
}

@Composable
fun rememberParallaxLayers(): State<List<ParallaxLayer>> {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    return remember {
        mutableStateOf(
            listOf(
                // Background layer (depth 0.1)
                ParallaxLayer(
                    id = "background",
                    depth = 0.1f,
                    elements = generateRandomElements(
                        count = 15,
                        color = primaryColor.copy(alpha = 0.3f),
                        sizeRange = 20f..40f,
                        shapes = listOf(ParallaxShape.CIRCLE, ParallaxShape.HEXAGON)
                    )
                ),
                
                // Mid-background layer (depth 0.3)
                ParallaxLayer(
                    id = "midBackground",
                    depth = 0.3f,
                    elements = generateRandomElements(
                        count = 12,
                        color = secondaryColor.copy(alpha = 0.4f),
                        sizeRange = 15f..30f,
                        shapes = listOf(ParallaxShape.TRIANGLE, ParallaxShape.SQUARE)
                    )
                ),
                
                // Mid layer (depth 0.5)
                ParallaxLayer(
                    id = "mid",
                    depth = 0.5f,
                    elements = generateRandomElements(
                        count = 10,
                        color = tertiaryColor.copy(alpha = 0.6f),
                        sizeRange = 12f..25f,
                        shapes = listOf(ParallaxShape.STAR, ParallaxShape.CIRCLE)
                    )
                ),
                
                // Mid-foreground layer (depth 0.7)
                ParallaxLayer(
                    id = "midForeground",
                    depth = 0.7f,
                    elements = generateRandomElements(
                        count = 8,
                        color = primaryColor.copy(alpha = 0.7f),
                        sizeRange = 10f..20f,
                        shapes = listOf(ParallaxShape.HEXAGON, ParallaxShape.TRIANGLE)
                    )
                ),
                
                // Foreground layer (depth 0.9)
                ParallaxLayer(
                    id = "foreground",
                    depth = 0.9f,
                    elements = generateRandomElements(
                        count = 6,
                        color = secondaryColor.copy(alpha = 0.8f),
                        sizeRange = 8f..15f,
                        shapes = listOf(ParallaxShape.STAR, ParallaxShape.CIRCLE)
                    )
                )
            )
        )
    }
}

private fun generateRandomElements(
    count: Int,
    color: Color,
    sizeRange: ClosedFloatingPointRange<Float>,
    shapes: List<ParallaxShape>
): List<ParallaxElement> {
    val random = Random(42) // Fixed seed for consistent layout
    return (0 until count).map { i ->
        ParallaxElement(
            position = Offset(
                random.nextFloat() * 800f, // Assuming canvas width
                random.nextFloat() * 600f  // Assuming canvas height
            ),
            size = sizeRange.start + random.nextFloat() * (sizeRange.endInclusive - sizeRange.start),
            color = color,
            shape = shapes[random.nextInt(shapes.size)],
            rotation = random.nextFloat() * 360f
        )
    }
}

@Composable
fun InteractiveParallaxDemo(
    modifier: Modifier = Modifier
) {
    val layers by rememberParallaxLayers()
    var enableInteraction by remember { mutableStateOf(true) }
    var autoScroll by remember { mutableStateOf(true) }
    
    Column(modifier = modifier) {
        ParallaxEffects(
            layers = layers,
            enableInteraction = enableInteraction,
            autoScroll = autoScroll,
            scrollSpeed = 0.3f,
            modifier = Modifier.weight(1f)
        )
    }
}