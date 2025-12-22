package com.omnisyncra.core.ui.indicators

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisyncra.core.domain.*
import com.omnisyncra.core.security.TrustLevel
import kotlin.math.*

// Device load indicator
@Composable
fun DeviceLoadIndicator(
    loadPercentage: Float,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val animatedLoad by animateFloatAsState(
        targetValue = loadPercentage,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "device_load"
    )
    
    val loadColor = when {
        animatedLoad < 0.3f -> Color(0xFF10B981) // Green
        animatedLoad < 0.7f -> Color(0xFFF59E0B) // Yellow
        else -> Color(0xFFEF4444) // Red
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background circle
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.3f),
                    radius = size.minDimension / 2,
                    style = Stroke(width = 8.dp.toPx())
                )
                
                // Progress arc
                val sweepAngle = animatedLoad * 360f
                drawArc(
                    color = loadColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(
                        width = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
            
            // Load percentage text
            Text(
                text = "${(animatedLoad * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = loadColor
            )
        }
        
        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Load",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

// Task progress indicator with animation
@Composable
fun TaskProgressIndicator(
    progress: Float,
    taskType: String,
    isRunning: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "task_progress"
    )
    
    val pulseAnimation by animateFloatAsState(
        targetValue = if (isRunning) 1.1f else 1.0f,
        animationSpec = if (isRunning) {
            infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "pulse"
    )
    
    Card(
        modifier = modifier.size(width = 200.dp, height = 80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = taskType,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                            .graphicsLayer {
                                scaleX = pulseAnimation
                                scaleY = pulseAnimation
                            }
                    )
                }
            }
            
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isRunning) Color(0xFF3B82F6) else Color(0xFF10B981)
                )
            }
        }
    }
}

// Network signal strength indicator
@Composable
fun SignalStrengthIndicator(
    signalStrength: Int, // dBm value
    modifier: Modifier = Modifier
) {
    val bars = when {
        signalStrength >= -50 -> 4
        signalStrength >= -60 -> 3
        signalStrength >= -70 -> 2
        signalStrength >= -80 -> 1
        else -> 0
    }
    
    val signalColor = when (bars) {
        4 -> Color(0xFF10B981)
        3 -> Color(0xFF10B981)
        2 -> Color(0xFFF59E0B)
        1 -> Color(0xFFEF4444)
        else -> Color.Gray
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            val barHeight = (index + 1) * 4.dp
            val isActive = index < bars
            
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .background(
                        color = if (isActive) signalColor else Color.Gray.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

// Security level indicator
@Composable
fun SecurityLevelIndicator(
    trustLevel: TrustLevel,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val (color, icon, label) = when (trustLevel) {
        TrustLevel.VERIFIED -> Triple(Color(0xFF8B5CF6), "🛡️", "Verified")
        TrustLevel.TRUSTED -> Triple(Color(0xFF10B981), "✅", "Trusted")
        TrustLevel.PENDING -> Triple(Color(0xFFF59E0B), "⏳", "Pending")
        TrustLevel.UNTRUSTED -> Triple(Color(0xFFEF4444), "⚠️", "Untrusted")
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            fontSize = 16.sp
        )
        
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Connection quality indicator
@Composable
fun ConnectionQualityIndicator(
    quality: ConnectionQuality,
    modifier: Modifier = Modifier
) {
    val (color, dots) = when (quality) {
        ConnectionQuality.EXCELLENT -> Color(0xFF10B981) to 4
        ConnectionQuality.GOOD -> Color(0xFF10B981) to 3
        ConnectionQuality.FAIR -> Color(0xFFF59E0B) to 2
        ConnectionQuality.POOR -> Color(0xFFEF4444) to 1
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val isActive = index < dots
            val dotSize = if (isActive) 6.dp else 4.dp
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        if (isActive) color else Color.Gray.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

enum class ConnectionQuality {
    POOR, FAIR, GOOD, EXCELLENT
}

// Compute power indicator
@Composable
fun ComputePowerIndicator(
    computePower: ComputePower,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val targetProgress = when (computePower) {
        ComputePower.LOW -> 0.25f
        ComputePower.MEDIUM -> 0.5f
        ComputePower.HIGH -> 0.75f
        ComputePower.EXTREME -> 1.0f
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (animated) targetProgress else targetProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "compute_power"
    )
    
    val powerColor = when (computePower) {
        ComputePower.LOW -> Color(0xFF6B7280)
        ComputePower.MEDIUM -> Color(0xFF3B82F6)
        ComputePower.HIGH -> Color(0xFF8B5CF6)
        ComputePower.EXTREME -> Color(0xFFEF4444)
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier.size(40.dp)
        ) {
            val barWidth = size.width / 5
            val barSpacing = barWidth * 0.2f
            
            repeat(4) { index ->
                val barHeight = (index + 1) * (size.height / 4)
                val x = index * (barWidth + barSpacing)
                val isActive = (index + 1) <= (animatedProgress * 4).toInt()
                
                drawRect(
                    color = if (isActive) powerColor else Color.Gray.copy(alpha = 0.3f),
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = computePower.name,
            style = MaterialTheme.typography.labelSmall,
            color = powerColor,
            fontWeight = FontWeight.Medium
        )
    }
}

// Proximity distance indicator
@Composable
fun ProximityIndicator(
    distance: ProximityDistance,
    signalStrength: Int,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val (color, size, pulseSpeed) = when (distance) {
        ProximityDistance.VERY_CLOSE -> Triple(Color(0xFF10B981), 24.dp, 500)
        ProximityDistance.CLOSE -> Triple(Color(0xFFF59E0B), 20.dp, 750)
        ProximityDistance.MEDIUM -> Triple(Color(0xFFEF4444), 16.dp, 1000)
        ProximityDistance.FAR -> Triple(Color(0xFF6B7280), 12.dp, 1500)
    }
    
    val pulseAnimation by animateFloatAsState(
        targetValue = if (animated) 1.2f else 1.0f,
        animationSpec = if (animated) {
            infiniteRepeatable(
                animation = tween(pulseSpeed),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "proximity_pulse"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f))
                .graphicsLayer {
                    scaleX = pulseAnimation
                    scaleY = pulseAnimation
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.6f)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = distance.name,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "${signalStrength} dBm",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// Battery level indicator
@Composable
fun BatteryIndicator(
    batteryLevel: Float, // 0.0 to 1.0
    isCharging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val batteryColor = when {
        batteryLevel > 0.5f -> Color(0xFF10B981)
        batteryLevel > 0.2f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    
    val chargingAnimation by animateFloatAsState(
        targetValue = if (isCharging) 1.0f else 0.0f,
        animationSpec = if (isCharging) {
            infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "charging"
    )
    
    Canvas(
        modifier = modifier.size(width = 24.dp, height = 12.dp)
    ) {
        val batteryWidth = size.width * 0.85f
        val batteryHeight = size.height
        val tipWidth = size.width * 0.15f
        val tipHeight = size.height * 0.4f
        
        // Battery outline
        drawRoundRect(
            color = Color.Gray,
            size = Size(batteryWidth, batteryHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        
        // Battery tip
        drawRoundRect(
            color = Color.Gray,
            topLeft = Offset(batteryWidth, (batteryHeight - tipHeight) / 2),
            size = Size(tipWidth, tipHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        )
        
        // Battery fill
        val fillWidth = (batteryWidth - 4.dp.toPx()) * batteryLevel
        if (fillWidth > 0) {
            drawRoundRect(
                color = batteryColor.copy(alpha = if (isCharging) chargingAnimation else 1.0f),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(fillWidth, batteryHeight - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
            )
        }
        
        // Charging indicator
        if (isCharging) {
            // Draw lightning bolt icon (simplified)
            drawPath(
                path = Path().apply {
                    moveTo(batteryWidth * 0.4f, batteryHeight * 0.2f)
                    lineTo(batteryWidth * 0.6f, batteryHeight * 0.2f)
                    lineTo(batteryWidth * 0.5f, batteryHeight * 0.5f)
                    lineTo(batteryWidth * 0.7f, batteryHeight * 0.5f)
                    lineTo(batteryWidth * 0.4f, batteryHeight * 0.8f)
                    lineTo(batteryWidth * 0.5f, batteryHeight * 0.5f)
                    lineTo(batteryWidth * 0.3f, batteryHeight * 0.5f)
                    close()
                },
                color = Color.White,
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }
    }
}