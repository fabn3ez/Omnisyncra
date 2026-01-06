package com.omnisyncra.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.omnisyncra.core.handoff.*
import kotlinx.coroutines.launch

/**
 * Ghost Handoff Screen showcasing seamless state transfer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostHandoffScreen() {
    var selectedDevice by remember { mutableStateOf<HandoffDevice?>(null) }
    var handoffStatus by remember { mutableStateOf("Ready") }
    var availableDevices by remember { mutableStateOf<List<HandoffDevice>>(emptyList()) }
    var activeSessions by remember { mutableStateOf<List<HandoffSession>>(emptyList()) }
    var isHandoffInProgress by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Premium colors
    val primaryGlow = Color(0xFF6366F1)
    val accentGlow = Color(0xFF10B981)
    val warningGlow = Color(0xFFF59E0B)
    val surfaceGlass = Color(0x1A1E1E3F)
    
    // Animation
    val infiniteTransition = rememberInfiniteTransition()
    val glowAnimation by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    LaunchedEffect(Unit) {
        // Simulate loading available devices
        availableDevices = listOf(
            HandoffDevice(
                deviceId = "laptop-001",
                deviceName = "MacBook Pro",
                deviceType = "laptop",
                capabilities = listOf("display", "keyboard", "compute"),
                proximity = DeviceProximity.SAME_ROOM,
                batteryLevel = 0.85f,
                networkLatency = 15L,
                isAvailable = true
            ),
            HandoffDevice(
                deviceId = "phone-001",
                deviceName = "iPhone 15 Pro",
                deviceType = "smartphone",
                capabilities = listOf("touch", "camera", "sensors"),
                proximity = DeviceProximity.SAME_ROOM,
                batteryLevel = 0.67f,
                networkLatency = 12L,
                isAvailable = true
            ),
            HandoffDevice(
                deviceId = "tablet-001",
                deviceName = "iPad Air",
                deviceType = "tablet",
                capabilities = listOf("touch", "display", "stylus"),
                proximity = DeviceProximity.SAME_NETWORK,
                batteryLevel = 0.92f,
                networkLatency = 25L,
                isAvailable = true
            )
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A0A1A),
                        Color(0xFF0F0F23),
                        Color(0xFF1A1A2E)
                    ),
                    radius = 1000f
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        GhostHandoffHeader(glowAnimation, primaryGlow, handoffStatus)
        
        // Current Context Card
        CurrentContextCard(surfaceGlass, primaryGlow)
        
        // Available Devices
        AvailableDevicesCard(
            devices = availableDevices,
            selectedDevice = selectedDevice,
            onDeviceSelected = { selectedDevice = it },
            surfaceGlass = surfaceGlass,
            primaryGlow = primaryGlow,
            accentGlow = accentGlow
        )
        
        // Handoff Controls
        HandoffControlsCard(
            selectedDevice = selectedDevice,
            isHandoffInProgress = isHandoffInProgress,
            onInitiateHandoff = {
                scope.launch {
                    isHandoffInProgress = true
                    handoffStatus = "Initiating handoff..."
                    
                    // Simulate handoff process
                    kotlinx.coroutines.delay(2000)
                    handoffStatus = "Preserving mental context..."
                    kotlinx.coroutines.delay(1500)
                    handoffStatus = "Transferring state..."
                    kotlinx.coroutines.delay(1000)
                    handoffStatus = "Handoff completed successfully!"
                    
                    isHandoffInProgress = false
                    kotlinx.coroutines.delay(2000)
                    handoffStatus = "Ready"
                }
            },
            surfaceGlass = surfaceGlass,
            primaryGlow = primaryGlow,
            warningGlow = warningGlow
        )
        
        // Mental Context Visualization
        if (isHandoffInProgress) {
            MentalContextVisualization(surfaceGlass, accentGlow, glowAnimation)
        }
    }
}

@Composable
private fun GhostHandoffHeader(
    glowAnimation: Float,
    primaryGlow: Color,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x1A1E1E3F)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = glowAnimation),
                                primaryGlow.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Ghost Handoff",
                    tint = primaryGlow,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ghost Handoff",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Seamless State Transfer with Mental Context",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            // Status indicator
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = primaryGlow
            )
        }
    }
}

@Composable
private fun CurrentContextCard(
    surfaceGlass: Color,
    primaryGlow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Current Context",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ContextMetric(
                    title = "Focus Area",
                    value = "Document Editing",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                ContextMetric(
                    title = "Workflow Stage",
                    value = "Review & Edit",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                ContextMetric(
                    title = "Cognitive Load",
                    value = "Medium",
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Text(
                text = "Mental Context: User is actively reviewing a document with moderate cognitive load. Recent actions include scrolling, highlighting text, and making annotations. Next intended actions: finalize edits and share document.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun AvailableDevicesCard(
    devices: List<HandoffDevice>,
    selectedDevice: HandoffDevice?,
    onDeviceSelected: (HandoffDevice) -> Unit,
    surfaceGlass: Color,
    primaryGlow: Color,
    accentGlow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Available Devices",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            devices.forEach { device ->
                DeviceCard(
                    device = device,
                    isSelected = selectedDevice == device,
                    onClick = { onDeviceSelected(device) },
                    primaryGlow = primaryGlow,
                    accentGlow = accentGlow
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: HandoffDevice,
    isSelected: Boolean,
    onClick: () -> Unit,
    primaryGlow: Color,
    accentGlow: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                primaryGlow.copy(alpha = 0.2f) 
            else 
                Color(0x1A1E1E3F)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device icon
            Icon(
                imageVector = when (device.deviceType) {
                    "laptop" -> Icons.Default.Laptop
                    "smartphone" -> Icons.Default.PhoneAndroid
                    "tablet" -> Icons.Default.Tablet
                    else -> Icons.Default.DeviceUnknown
                },
                contentDescription = device.deviceType,
                tint = if (isSelected) primaryGlow else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "${device.proximity.name.lowercase().replace('_', ' ')} • ${device.networkLatency}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            // Battery level
            device.batteryLevel?.let { battery ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Battery6Bar,
                        contentDescription = "Battery",
                        tint = accentGlow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${(battery * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentGlow
                    )
                }
            }
        }
    }
}

@Composable
private fun HandoffControlsCard(
    selectedDevice: HandoffDevice?,
    isHandoffInProgress: Boolean,
    onInitiateHandoff: () -> Unit,
    surfaceGlass: Color,
    primaryGlow: Color,
    warningGlow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Handoff Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            if (selectedDevice != null) {
                Text(
                    text = "Ready to handoff to ${selectedDevice.deviceName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Button(
                    onClick = onInitiateHandoff,
                    enabled = !isHandoffInProgress,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryGlow,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isHandoffInProgress) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Handoff in Progress...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Initiate Ghost Handoff")
                    }
                }
            } else {
                Text(
                    text = "Select a device to begin handoff",
                    style = MaterialTheme.typography.bodyMedium,
                    color = warningGlow
                )
            }
        }
    }
}

@Composable
private fun MentalContextVisualization(
    surfaceGlass: Color,
    accentGlow: Color,
    glowAnimation: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            accentGlow.copy(alpha = glowAnimation),
                            CircleShape
                        )
                )
                Text(
                    text = "Mental Context Transfer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentGlow
                )
            }
            
            Text(
                text = "🧠 Preserving cognitive state and user intent\n" +
                      "🎯 Capturing attention points and focus areas\n" +
                      "📊 Analyzing workflow stage and progress\n" +
                      "🔄 Preparing seamless context reconstruction",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ContextMetric(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}