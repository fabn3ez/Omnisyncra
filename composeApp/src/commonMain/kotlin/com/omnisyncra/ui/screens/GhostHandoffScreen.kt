package com.omnisyncra.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.core.handoff.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostHandoffScreen(
    modifier: Modifier = Modifier
) {
    val handoffSystem = remember { PlatformHandoffSystem() }
    val scope = rememberCoroutineScope()
    
    val isActive by handoffSystem.isActive.collectAsState()
    val availableTargets by handoffSystem.availableTargets.collectAsState()
    val currentHandoffs by handoffSystem.currentHandoffs.collectAsState()
    
    var selectedTarget by remember { mutableStateOf<String?>(null) }
    var lastHandoffResult by remember { mutableStateOf<HandoffResult?>(null) }
    var mentalContext by remember { mutableStateOf<MentalContext?>(null) }
    var applicationStates by remember { mutableStateOf<List<ApplicationState>>(emptyList()) }
    
    // Update context periodically when active
    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) {
                mentalContext = handoffSystem.getMentalContext()
                applicationStates = handoffSystem.getApplicationStates()
                kotlinx.coroutines.delay(2000)
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "👻 Phase 9: Ghost Handoff System",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Seamless state transfer between devices with mental context preservation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (isActive) {
                                    handoffSystem.stopHandoffCapture()
                                } else {
                                    handoffSystem.startHandoffCapture()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isActive) "🛑 Stop Capture" else "🎯 Start Capture")
                    }
                    
                    Button(
                        onClick = {
                            selectedTarget?.let { target ->
                                scope.launch {
                                    val result = handoffSystem.initiateHandoff(
                                        targetDeviceId = target,
                                        priority = HandoffPriority.NORMAL
                                    )
                                    lastHandoffResult = result
                                }
                            }
                        },
                        enabled = isActive && selectedTarget != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🚀 Initiate Handoff")
                    }
                }
            }
        }
        
        // Status indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusCard(
                title = "Capture Status",
                value = if (isActive) "Active" else "Inactive",
                color = if (isActive) Color.Green else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Available Targets",
                value = availableTargets.size.toString(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Pending Handoffs",
                value = currentHandoffs.size.toString(),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Context Quality",
                value = "${((mentalContext?.focusLevel ?: 0f) * 100).toInt()}%",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Main content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Left panel - Mental Context & Apps
            Card(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                MentalContextPanel(
                    mentalContext = mentalContext,
                    applicationStates = applicationStates,
                    isActive = isActive
                )
            }
            
            // Right panel - Handoff Management
            Card(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(start = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                HandoffManagementPanel(
                    availableTargets = availableTargets,
                    currentHandoffs = currentHandoffs,
                    selectedTarget = selectedTarget,
                    onTargetSelected = { selectedTarget = it },
                    lastResult = lastHandoffResult,
                    onAcceptHandoff = { handoffId ->
                        scope.launch {
                            val result = handoffSystem.acceptHandoff(handoffId)
                            lastHandoffResult = result
                        }
                    },
                    onRejectHandoff = { handoffId ->
                        scope.launch {
                            handoffSystem.rejectHandoff(handoffId, "User rejected")
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MentalContextPanel(
    mentalContext: MentalContext?,
    applicationStates: List<ApplicationState>,
    isActive: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "🧠 Mental Context",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (mentalContext != null && isActive) {
            // Current Task
            ContextItem(
                label = "Current Task",
                value = mentalContext.currentTask.replace("_", " ").replaceFirstChar { it.uppercase() }
            )
            
            // Focus Level
            ContextItem(
                label = "Focus Level",
                value = "${(mentalContext.focusLevel * 100).toInt()}%"
            ) {
                LinearProgressIndicator(
                    progress = { mentalContext.focusLevel },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        mentalContext.focusLevel > 0.7f -> Color.Green
                        mentalContext.focusLevel > 0.4f -> Color.Yellow
                        else -> Color.Red
                    }
                )
            }
            
            // Cognitive Load
            ContextItem(
                label = "Cognitive Load",
                value = "${(mentalContext.cognitiveLoad * 100).toInt()}%"
            ) {
                LinearProgressIndicator(
                    progress = { mentalContext.cognitiveLoad },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        mentalContext.cognitiveLoad < 0.3f -> Color.Green
                        mentalContext.cognitiveLoad < 0.7f -> Color.Yellow
                        else -> Color.Red
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Working Memory
            Text(
                text = "💭 Working Memory",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            mentalContext.workingMemory.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = item,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recent Actions
            Text(
                text = "⚡ Recent Actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.height(120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(mentalContext.recentActions.takeLast(5)) { action ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${getActionEmoji(action.type)} ${action.type.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = action.target,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isActive) "Analyzing context..." else "Start capture to see mental context",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun HandoffManagementPanel(
    availableTargets: List<String>,
    currentHandoffs: List<HandoffPackage>,
    selectedTarget: String?,
    onTargetSelected: (String) -> Unit,
    lastResult: HandoffResult?,
    onAcceptHandoff: (String) -> Unit,
    onRejectHandoff: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "🎯 Handoff Management",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Available Targets
        Text(
            text = "📱 Available Devices",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        if (availableTargets.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.height(120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(availableTargets) { target ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedTarget == target) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        onClick = { onTargetSelected(target) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${getDeviceEmoji(target)} ${formatDeviceName(target)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (selectedTarget == target) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                text = "No devices available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Current Handoffs
        Text(
            text = "🔄 Active Handoffs",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        if (currentHandoffs.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.height(150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentHandoffs) { handoff ->
                    HandoffItem(
                        handoff = handoff,
                        onAccept = { onAcceptHandoff(handoff.id) },
                        onReject = { onRejectHandoff(handoff.id) }
                    )
                }
            }
        } else {
            Text(
                text = "No active handoffs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Last Result
        if (lastResult != null) {
            Text(
                text = "📊 Last Handoff Result",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (lastResult.success) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = if (lastResult.success) "✅ Success" else "❌ Failed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (lastResult.success) {
                        Text(
                            text = "Apps: ${lastResult.transferredApps.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Context Score: ${(lastResult.contextPreservationScore * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Transfer Time: ${lastResult.transferTime}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = lastResult.errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextItem(
    label: String,
    value: String,
    content: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        content?.invoke()
    }
}

@Composable
private fun HandoffItem(
    handoff: HandoffPackage,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "From: ${formatDeviceName(handoff.sourceDeviceId)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = handoff.priority.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (handoff.priority) {
                        HandoffPriority.URGENT -> Color.Red
                        HandoffPriority.HIGH -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
            
            Text(
                text = "Apps: ${handoff.applicationStates.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Text(
                text = "Task: ${handoff.mentalContext.currentTask.replace("_", " ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Accept")
                }
                
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reject")
                }
            }
        }
    }
}

private fun getActionEmoji(actionType: ActionType): String = when (actionType) {
    ActionType.SCROLL -> "📜"
    ActionType.CLICK -> "👆"
    ActionType.TYPE -> "⌨️"
    ActionType.SWIPE -> "👋"
    ActionType.ZOOM -> "🔍"
    ActionType.ROTATE -> "🔄"
    ActionType.VOICE_COMMAND -> "🎤"
    ActionType.GESTURE -> "✋"
    ActionType.EYE_MOVEMENT -> "👁️"
    ActionType.PAUSE -> "⏸️"
    ActionType.CONTEXT_SWITCH -> "🔀"
    ActionType.MULTITASK -> "🔀"
}

private fun getDeviceEmoji(deviceId: String): String = when {
    deviceId.contains("phone") -> "📱"
    deviceId.contains("tablet") -> "📱"
    deviceId.contains("laptop") -> "💻"
    deviceId.contains("desktop") -> "🖥️"
    else -> "📱"
}

private fun formatDeviceName(deviceId: String): String {
    return deviceId.split("-").joinToString(" ") { 
        it.replaceFirstChar { char -> char.uppercase() } 
    }
}