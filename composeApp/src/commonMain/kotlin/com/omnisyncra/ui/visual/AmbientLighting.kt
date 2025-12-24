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
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

data class LightSource(
    val position: Offset,
    val intensity: Float,
    val color: Color,
    val radius: Float,
    val type: LightType = LightType.POINT
)

enum class LightType {
    POINT, DIRECTIONAL, AMBIENT, PROXIMITY
}

data class ProximityZone(
    val center: Offset,
    val radius: Float,
    val strength: Float,
    val color: Color
)

@Composable
fun AmbientLighting(
    lightSources: List<LightSource> = emptyList(),
    proximityZones: List<ProximityZone> = emptyList(),
    modifier: Modifier = Modifier,
    responseToProximity: Boolean = true,
    ambientIntensity: Float = 0.3f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    
    // Breathing ambient light
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )
    
    // Proximity response animation
    val proximityResponse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "proximityResponse"
    )
    
    // Light flicker simulation
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Base ambient lighting
        drawAmbientBase(ambientIntensity * ambientPulse, primaryColor)
        
        // Proximity-based lighting zones
        if (responseToProximity) {
            proximityZones.forEach { zone ->
                drawProximityZone(
                    zone = zone,
                    response = proximityResponse,
                    flicker = flicker
                )
            }
        }
        
        // Individual light sources
        lightSources.forEach { light ->
            drawLightSource(
                light = light,
                flicker = flicker,
                ambientPulse = ambientPulse
            )
        }
        
        // Dynamic light interactions
        drawLightInteractions(lightSources, proximityResponse)
        
        // Atmospheric particles
        drawAtmosphericParticles(ambientPulse, primaryColor)
    }
}

private fun DrawScope.drawAmbientBase(
    intensity: Float,
    color: Color
) {
    // Create a subtle gradient overlay
    val gradient = Brush.radialGradient(
        colors = listOf(
            color.copy(alpha = intensity * 0.1f),
            color.copy(alpha = intensity * 0.05f),
            Color.Transparent
        ),
        center = Offset(size.width / 2, size.height / 2),
        radius = maxOf(size.width, size.height) * 0.8f
    )
    
    drawRect(
        brush = gradient,
        size = size
    )
    
    // Add corner vignetting
    val corners = listOf(
        Offset(0f, 0f),
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height)
    )
    
    corners.forEach { corner ->
        val vignetteGradient = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.1f * intensity),
                Color.Transparent
            ),
            center = corner,
            radius = minOf(size.width, size.height) * 0.3f
        )
        
        drawCircle(
            brush = vignetteGradient,
            radius = minOf(size.width, size.height) * 0.3f,
            center = corner
        )
    }
}

private fun DrawScope.drawProximityZone(
    zone: ProximityZone,
    response: Float,
    flicker: Float
) {
    val adjustedRadius = zone.radius * (1f + response * 0.2f)
    val adjustedIntensity = zone.strength * flicker
    
    // Main proximity glow
    val gradient = Brush.radialGradient(
        colors = listOf(
            zone.color.copy(alpha = adjustedIntensity * 0.3f),
            zone.color.copy(alpha = adjustedIntensity * 0.15f),
            zone.color.copy(alpha = adjustedIntensity * 0.05f),
            Color.Transparent
        ),
        center = zone.center,
        radius = adjustedRadius
    )
    
    drawCircle(
        brush = gradient,
        radius = adjustedRadius,
        center = zone.center
    )
    
    // Proximity pulse rings
    repeat(3) { i ->
        val ringRadius = adjustedRadius * (0.3f + i * 0.2f) * (1f + response * 0.1f)
        val ringAlpha = (adjustedIntensity * 0.2f) / (i + 1)
        
        drawCircle(
            color = zone.color.copy(alpha = ringAlpha),
            radius = ringRadius,
            center = zone.center,
            style = Stroke(width = (2f - i * 0.5f).dp.toPx())
        )
    }
    
    // Dynamic proximity particles
    repeat(8) { i ->
        val angle = i * 45f + response * 360f
        val particleDistance = adjustedRadius * (0.6f + response * 0.3f)
        val particlePos = Offset(
            zone.center.x + cos(angle * PI / 180f).toFloat() * particleDistance,
            zone.center.y + sin(angle * PI / 180f).toFloat() * particleDistance
        )
        
        drawCircle(
            color = zone.color.copy(alpha = adjustedIntensity * 0.6f),
            radius = (2f + response * 2f).dp.toPx(),
            center = particlePos
        )
    }
}

private fun DrawScope.drawLightSource(
    light: LightSource,
    flicker: Float,
    ambientPulse: Float
) {
    val adjustedIntensity = light.intensity * flicker
    val adjustedRadius = light.radius * ambientPulse
    
    when (light.type) {
        LightType.POINT -> {
            drawPointLight(light, adjustedIntensity, adjustedRadius)
        }
        LightType.DIRECTIONAL -> {
            drawDirectionalLight(light, adjustedIntensity, adjustedRadius)
        }
        LightType.AMBIENT -> {
            drawAmbientLight(light, adjustedIntensity, adjustedRadius)
        }
        LightType.PROXIMITY -> {
            drawProximityLight(light, adjustedIntensity, adjustedRadius, ambientPulse)
        }
    }
}

private fun DrawScope.drawPointLight(
    light: LightSource,
    intensity: Float,
    radius: Float
) {
    // Core light source
    drawCircle(
        color = light.color.copy(alpha = intensity * 0.8f),
        radius = 8.dp.toPx(),
        center = light.position
    )
    
    // Light spread
    val gradient = Brush.radialGradient(
        colors = listOf(
            light.color.copy(alpha = intensity * 0.4f),
            light.color.copy(alpha = intensity * 0.2f),
            light.color.copy(alpha = intensity * 0.1f),
            Color.Transparent
        ),
        center = light.position,
        radius = radius
    )
    
    drawCircle(
        brush = gradient,
        radius = radius,
        center = light.position
    )
    
    // Light rays
    repeat(12) { i ->
        val angle = i * 30f
        val rayLength = radius * 0.8f
        val rayEnd = Offset(
            light.position.x + cos(angle * PI / 180f).toFloat() * rayLength,
            light.position.y + sin(angle * PI / 180f).toFloat() * rayLength
        )
        
        drawLine(
            color = light.color.copy(alpha = intensity * 0.3f),
            start = light.position,
            end = rayEnd,
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawDirectionalLight(
    light: LightSource,
    intensity: Float,
    radius: Float
) {
    // Directional light creates a cone effect
    val coneAngle = 60f // degrees
    val coneDirection = 0f // pointing right
    
    // Create cone gradient
    val coneGradient = Brush.sweepGradient(
        colors = listOf(
            Color.Transparent,
            light.color.copy(alpha = intensity * 0.3f),
            light.color.copy(alpha = intensity * 0.5f),
            light.color.copy(alpha = intensity * 0.3f),
            Color.Transparent
        ),
        center = light.position
    )
    
    // Draw cone sector (simplified as circle for this implementation)
    drawCircle(
        brush = coneGradient,
        radius = radius,
        center = light.position
    )
}

private fun DrawScope.drawAmbientLight(
    light: LightSource,
    intensity: Float,
    radius: Float
) {
    // Ambient light affects the entire area uniformly
    val ambientGradient = Brush.radialGradient(
        colors = listOf(
            light.color.copy(alpha = intensity * 0.2f),
            light.color.copy(alpha = intensity * 0.1f),
            Color.Transparent
        ),
        center = light.position,
        radius = radius * 2f
    )
    
    drawCircle(
        brush = ambientGradient,
        radius = radius * 2f,
        center = light.position
    )
}

private fun DrawScope.drawProximityLight(
    light: LightSource,
    intensity: Float,
    radius: Float,
    pulse: Float
) {
    // Proximity light responds to nearby activity
    val proximityRadius = radius * (1f + pulse * 0.3f)
    val proximityIntensity = intensity * (0.7f + pulse * 0.3f)
    
    // Pulsing proximity glow
    val proximityGradient = Brush.radialGradient(
        colors = listOf(
            light.color.copy(alpha = proximityIntensity * 0.5f),
            light.color.copy(alpha = proximityIntensity * 0.3f),
            light.color.copy(alpha = proximityIntensity * 0.1f),
            Color.Transparent
        ),
        center = light.position,
        radius = proximityRadius
    )
    
    drawCircle(
        brush = proximityGradient,
        radius = proximityRadius,
        center = light.position
    )
    
    // Proximity detection rings
    repeat(2) { i ->
        val ringRadius = proximityRadius * (0.5f + i * 0.3f)
        drawCircle(
            color = light.color.copy(alpha = proximityIntensity * 0.4f / (i + 1)),
            radius = ringRadius,
            center = light.position,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

private fun DrawScope.drawLightInteractions(
    lightSources: List<LightSource>,
    response: Float
) {
    // Draw interactions between nearby light sources
    for (i in lightSources.indices) {
        for (j in i + 1 until lightSources.size) {
            val light1 = lightSources[i]
            val light2 = lightSources[j]
            
            val distance = sqrt(
                (light2.position.x - light1.position.x).pow(2) +
                (light2.position.y - light1.position.y).pow(2)
            )
            
            // If lights are close enough, create interaction
            if (distance < (light1.radius + light2.radius) * 0.8f) {
                val midPoint = Offset(
                    (light1.position.x + light2.position.x) / 2,
                    (light1.position.y + light2.position.y) / 2
                )
                
                // Blend colors
                val blendedColor = Color(
                    red = (light1.color.red + light2.color.red) / 2,
                    green = (light1.color.green + light2.color.green) / 2,
                    blue = (light1.color.blue + light2.color.blue) / 2,
                    alpha = (light1.intensity + light2.intensity) / 2 * response
                )
                
                // Draw interaction glow
                drawCircle(
                    color = blendedColor.copy(alpha = 0.3f * response),
                    radius = 20.dp.toPx() * response,
                    center = midPoint
                )
                
                // Draw connection line
                drawLine(
                    color = blendedColor.copy(alpha = 0.2f * response),
                    start = light1.position,
                    end = light2.position,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun DrawScope.drawAtmosphericParticles(
    pulse: Float,
    color: Color
) {
    // Add floating particles for atmospheric effect
    val particleCount = 20
    val random = Random(42) // Fixed seed for consistent particles
    
    repeat(particleCount) { i ->
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val particleSize = (1f + random.nextFloat() * 2f) * pulse
        val alpha = (0.1f + random.nextFloat() * 0.2f) * pulse
        
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = particleSize.dp.toPx(),
            center = Offset(x, y)
        )
    }
}

@Composable
fun rememberAmbientLightSources(): State<List<LightSource>> {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    return remember {
        mutableStateOf(
            listOf(
                LightSource(
                    position = Offset(200f, 150f),
                    intensity = 0.8f,
                    color = primaryColor,
                    radius = 120f,
                    type = LightType.POINT
                ),
                LightSource(
                    position = Offset(400f, 300f),
                    intensity = 0.6f,
                    color = secondaryColor,
                    radius = 100f,
                    type = LightType.PROXIMITY
                ),
                LightSource(
                    position = Offset(300f, 450f),
                    intensity = 0.7f,
                    color = tertiaryColor,
                    radius = 80f,
                    type = LightType.POINT
                )
            )
        )
    }
}

@Composable
fun rememberProximityZones(): State<List<ProximityZone>> {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    return remember {
        mutableStateOf(
            listOf(
                ProximityZone(
                    center = Offset(250f, 200f),
                    radius = 80f,
                    strength = 0.7f,
                    color = primaryColor
                ),
                ProximityZone(
                    center = Offset(450f, 350f),
                    radius = 60f,
                    strength = 0.5f,
                    color = secondaryColor
                )
            )
        )
    }
}