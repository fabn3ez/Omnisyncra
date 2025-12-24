package com.omnisyncra

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.omnisyncra.core.compute.*
import com.omnisyncra.core.discovery.DeviceDiscovery
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.state.DistributedStateManager
import com.omnisyncra.core.ui.UIOrchestrator
import com.omnisyncra.core.ui.AdaptiveContainer
import com.omnisyncra.core.ui.ProximityAwareLayout
import com.omnisyncra.core.ui.ContextPalette
import com.omnisyncra.core.ui.ProximityIndicator
import com.omnisyncra.core.domain.*
import org.koin.compose.koinInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
@Preview
fun AppWithUIOrchestration() {
    val platform = koinInject<Platform>()
    val stateManager = koinInject<DistributedStateManager>()
    val deviceDiscovery = koinInject<DeviceDiscovery>()
    val computeScheduler = koinInject<ComputeScheduler>()
    val uiOrchestrator = koinInject<UIOrchestrator>()
    val scope = rememberCoroutineScope()
    
    // Initialize services
    LaunchedEffect(Unit) {
        stateManager.initialize()
        deviceDiscovery.startDiscovery()
        
        // Start advertising this device
        val localDevice = com.omnisyncra.core.domain.Device(
            name = platform.getDeviceName(),
            type = platform.deviceType,
            capabilities = platform.capabilities
        )
        deviceDiscovery.advertiseDevice(localDevice)
        computeScheduler.registerComputeNode(localDevice)
    }
    
    val crdtState by stateManager.crdtState.collectAsState()
    val omnisyncraState by stateManager.omnisyncraState.collectAsState()
    val discoveredDevices by deviceDiscovery.discoveredDevices.collectAsState(initial = emptyList())
    val pendingTasks by computeScheduler.pendingTasks.collectAsState(initial = emptyList())
    val runningTasks by computeScheduler.runningTasks.collectAsState(initial = emptyList())
    val completedTasks by computeScheduler.completedTasks.collectAsState(initial = emptyList())
    val currentUIState by uiOrchestrator.currentUIState.collectAsState()
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
            secondary = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
            tertiary = androidx.compose.ui.graphics.Color(0xFF10B981),
            background = androidx.compose.ui.graphics.Color(0xFF0F0F23),
            surface = androidx.compose.ui.graphics.Color(0xFF1E1E3F)
        )
    ) {
        AdaptiveContainer(
            uiState = currentUIState,
            modifier = Modifier.fillMaxSize()
        ) {
            ProximityAwareLayout(
                uiState = currentUIState,
                primaryContent = {
                    MainContentWithOrchestration(
                        platform = platform,
                        crdtState = crdtState,
                        omnisyncraState = omnisyncraState,
                        discoveredDevices = discoveredDevices,
                        pendingTasks = pendingTasks,
                        runningTasks = runningTasks,
                        completedTasks = completedTasks,
                        currentUIState = currentUIState,
                        uiOrchestrator = uiOrchestrator,
                        computeScheduler = computeScheduler,
                        deviceDiscovery = deviceDiscovery,
                        scope = scope
                    )
                },
                secondaryContent = {
                    SecondaryContentPanel(
                        currentUIState = currentUIState,
                        discoveredDevices = discoveredDevices,
                        runningTasks = runningTasks
                    )
                },
                contextPalette = {
                    val activeContext = omnisyncraState?.contextGraph?.getActiveContext()
                    val relatedContexts = activeContext?.let { context ->
                        omnisyncraState?.contextGraph?.getRelatedContexts(context.id) ?: emptyList()
                    } ?: emptyList()
                    
                    ContextPalette(
                        activeContext = activeContext,
                        relatedContexts = relatedContexts,
                        onContextSelect = { context ->
                            // Handle context selection
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun MainContentWithOrchestration(
    platform: Platform,
    crdtState: com.omnisyncra.core.crdt.CrdtState,
    omnisyncraState: OmnisyncraState?,
    discoveredDevices: List<com.omnisyncra.core.domain.Device>,
    pendingTasks: List<ComputeTask>,
    runningTasks: List<TaskExecution>,
    completedTasks: List<TaskResult>,
    currentUIState: UIState,
    uiOrchestrator: UIOrchestrator,
    computeScheduler: ComputeScheduler,
    deviceDiscovery: DeviceDiscovery,
    scope: CoroutineScope
) {
    var showUI by remember { mutableStateOf(true) }
    var showCompute by remember { mutableStateOf(false) }
    var showDiscovery by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with UI Mode Indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🌐 Omnisyncra",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // UI Mode Badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (currentUIState.currentMode) {
                                UIMode.PRIMARY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                UIMode.SECONDARY -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                UIMode.CONTEXT_PALETTE -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                UIMode.VIEWER -> androidx.compose.ui.graphics.Color(0xFFF59E0B).copy(alpha = 0.2f)
                                UIMode.STANDALONE -> MaterialTheme.colorScheme.surface
                                UIMode.TRANSITIONING -> androidx.compose.ui.graphics.Color(0xFFEF4444).copy(alpha = 0.2f)
                            }
                        )
                    ) {
                        Text(
                            text = currentUIState.currentMode.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when (currentUIState.currentMode) {
                                UIMode.PRIMARY -> MaterialTheme.colorScheme.primary
                                UIMode.SECONDARY -> MaterialTheme.colorScheme.secondary
                                UIMode.CONTEXT_PALETTE -> MaterialTheme.colorScheme.tertiary
                                UIMode.VIEWER -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
                                UIMode.STANDALONE -> MaterialTheme.colorScheme.onSurface
                                UIMode.TRANSITIONING -> androidx.compose.ui.graphics.Color(0xFFEF4444)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Context-Aware UI Orchestration",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Control Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { showUI = !showUI },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFF59E0B)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("UI", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Button(
                        onClick = { showCompute = !showCompute },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Compute", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Button(
                        onClick = { showDiscovery = !showDiscovery },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Discovery", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Demo Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                uiOrchestrator.requestRoleChange(
                                    DeviceRole.SECONDARY,
                                    com.omnisyncra.core.ui.RoleChangeReason.USER_REQUEST
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("→ Secondary", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                uiOrchestrator.requestRoleChange(
                                    DeviceRole.PRIMARY,
                                    com.omnisyncra.core.ui.RoleChangeReason.USER_REQUEST
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("→ Primary", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                val demoTask = ComputeTask(
                                    type = TaskType.CONTEXT_GENERATION,
                                    priority = TaskPriority.NORMAL,
                                    payload = TaskPayload(
                                        data = mapOf(
                                            "context_type" to "work_session",
                                            "user_activity" to "ui_design"
                                        ),
                                        inputFormat = "json",
                                        expectedOutputFormat = "json"
                                    ),
                                    requirements = ComputeRequirements(
                                        minComputePower = com.omnisyncra.core.domain.ComputePower.MEDIUM,
                                        estimatedMemoryMB = 512,
                                        estimatedDurationMs = 1500L
                                    ),
                                    metadata = TaskMetadata(
                                        originDeviceId = crdtState.nodeId,
                                        tags = listOf("demo", "context", "ui")
                                    ),
                                    createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                )
                                computeScheduler.submitTask(demoTask)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF10B981)
                        )
                    ) {
                        Text("Context Task", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        // Information Sections
        AnimatedVisibility(showUI) {
            InfoCard(
                title = "UI Orchestration",
                titleColor = androidx.compose.ui.graphics.Color(0xFFF59E0B)
            ) {
                PlatformInfoRow("Current Mode", currentUIState.currentMode.name)
                PlatformInfoRow("Current Role", uiOrchestrator.getCurrentRole().name)
                PlatformInfoRow("Is Transitioning", if (currentUIState.animations.isTransitioning) "✅" else "❌")
                PlatformInfoRow("Transition Progress", "${(currentUIState.animations.progress * 100).toInt()}%")
                PlatformInfoRow("Primary Space", "${(currentUIState.adaptiveLayout.availableSpace.primary * 100).toInt()}%")
                PlatformInfoRow("Secondary Space", "${(currentUIState.adaptiveLayout.availableSpace.secondary * 100).toInt()}%")
                PlatformInfoRow("Theme", if (currentUIState.theme.isDarkMode) "Dark" else "Light")
                PlatformInfoRow("Proximity Style", currentUIState.theme.proximityIndicatorStyle.name)
                
                if (currentUIState.animations.transitionType != null) {
                    PlatformInfoRow("Transition Type", currentUIState.animations.transitionType.name)
                }
            }
        }
        
        AnimatedVisibility(showCompute) {
            InfoCard(
                title = "Compute Offloading",
                titleColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
            ) {
                PlatformInfoRow("Available Nodes", computeScheduler.getAvailableNodes().size.toString())
                PlatformInfoRow("Pending Tasks", pendingTasks.size.toString())
                PlatformInfoRow("Running Tasks", runningTasks.size.toString())
                PlatformInfoRow("Completed Tasks", completedTasks.size.toString())
                
                if (runningTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Running Tasks:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    runningTasks.take(2).forEach { execution ->
                        TaskCard(execution)
                    }
                }
            }
        }
        
        AnimatedVisibility(showDiscovery) {
            InfoCard(
                title = "Device Discovery",
                titleColor = MaterialTheme.colorScheme.tertiary
            ) {
                PlatformInfoRow("Discovery Active", if (deviceDiscovery.isDiscovering()) "✅" else "❌")
                PlatformInfoRow("Advertising", if (deviceDiscovery.isAdvertising()) "✅" else "❌")
                PlatformInfoRow("Discovered Devices", discoveredDevices.size.toString())
                PlatformInfoRow("Connected Devices", deviceDiscovery.getConnectedDevices().size.toString())
                
                if (discoveredDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Discovered Devices:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    discoveredDevices.take(2).forEach { device ->
                        DeviceCard(device)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryContentPanel(
    currentUIState: UIState,
    discoveredDevices: List<com.omnisyncra.core.domain.Device>,
    runningTasks: List<TaskExecution>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Secondary Panel",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Text(
            text = "Mode: ${currentUIState.currentMode.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        
        if (discoveredDevices.isNotEmpty()) {
            Text(
                text = "Nearby Devices:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            discoveredDevices.take(2).forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    ProximityIndicator(
                        proximityInfo = device.proximityInfo,
                        style = currentUIState.theme.proximityIndicatorStyle
                    )
                }
            }
        }
        
        if (runningTasks.isNotEmpty()) {
            Text(
                text = "Active Tasks:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            runningTasks.take(2).forEach { execution ->
                Text(
                    text = execution.task.type.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

@Composable
private fun DeviceCard(device: com.omnisyncra.core.domain.Device) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "${device.type.name} • ${device.capabilities.computePower.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            device.proximityInfo?.let { proximity ->
                Text(
                    text = "Proximity: ${proximity.distance.name} (${proximity.signalStrength} dBm)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun TaskCard(execution: TaskExecution) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFFEF4444).copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = execution.task.type.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color(0xFFEF4444)
            )
            Text(
                text = "Running on: ${execution.assignedNode.device.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "Priority: ${execution.task.priority.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun PlatformInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}