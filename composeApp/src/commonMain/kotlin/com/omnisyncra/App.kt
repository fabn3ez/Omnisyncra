package com.omnisyncra

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
@Preview
fun App() {
    val platform = koinInject<Platform>()
    val stateManager = koinInject<DistributedStateManager>()
    val deviceDiscovery = koinInject<DeviceDiscovery>()
    val computeScheduler = koinInject<ComputeScheduler>()
    val performanceProfiler = koinInject<PerformanceProfiler>()
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
        
        // Register as compute node
        computeScheduler.registerComputeNode(localDevice)
    }
    
    val crdtState by stateManager.crdtState.collectAsState()
    val omnisyncraState by stateManager.omnisyncraState.collectAsState()
    val discoveredDevices by deviceDiscovery.discoveredDevices.collectAsState(initial = emptyList())
    val pendingTasks by computeScheduler.pendingTasks.collectAsState(initial = emptyList())
    val runningTasks by computeScheduler.runningTasks.collectAsState(initial = emptyList())
    val completedTasks by computeScheduler.completedTasks.collectAsState(initial = emptyList())
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
            secondary = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
            tertiary = androidx.compose.ui.graphics.Color(0xFF10B981),
            background = androidx.compose.ui.graphics.Color(0xFF0F0F23),
            surface = androidx.compose.ui.graphics.Color(0xFF1E1E3F)
        )
    ) {
        var showPlatform by remember { mutableStateOf(false) }
        var showCrdt by remember { mutableStateOf(false) }
        var showDiscovery by remember { mutableStateOf(false) }
        var showCompute by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Omnisyncra Header
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
                    Text(
                        text = "🌐 Omnisyncra",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Asymmetric Compute Mesh",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showPlatform = !showPlatform },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Platform", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Button(
                            onClick = { showCrdt = !showCrdt },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CRDT", style = MaterialTheme.typography.bodySmall)
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
                        
                        Button(
                            onClick = { showCompute = !showCompute },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Compute", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Demo Task Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val demoTask = ComputeTask(
                                        type = TaskType.AI_INFERENCE,
                                        priority = TaskPriority.NORMAL,
                                        payload = TaskPayload(
                                            data = mapOf(
                                                "input" to "Analyze this text for sentiment and key themes",
                                                "model" to "omnisyncra-nlp-v1"
                                            ),
                                            inputFormat = "text",
                                            expectedOutputFormat = "json"
                                        ),
                                        requirements = ComputeRequirements(
                                            minComputePower = com.omnisyncra.core.domain.ComputePower.MEDIUM,
                                            estimatedMemoryMB = 512,
                                            estimatedDurationMs = 2000L
                                        ),
                                        metadata = TaskMetadata(
                                            originDeviceId = crdtState.nodeId,
                                            tags = listOf("demo", "ai", "nlp")
                                        ),
                                        createdAt = Clock.System.now().toEpochMilliseconds()
                                    )
                                    computeScheduler.submitTask(demoTask)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF8B5CF6)
                            )
                        ) {
                            Text("AI Task", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    val demoTask = ComputeTask(
                                        type = TaskType.ENCRYPTION,
                                        priority = TaskPriority.HIGH,
                                        payload = TaskPayload(
                                            data = mapOf(
                                                "data" to "sensitive_document_content",
                                                "algorithm" to "AES-256-GCM"
                                            ),
                                            inputFormat = "binary",
                                            expectedOutputFormat = "encrypted"
                                        ),
                                        requirements = ComputeRequirements(
                                            minComputePower = com.omnisyncra.core.domain.ComputePower.LOW,
                                            estimatedMemoryMB = 256,
                                            estimatedDurationMs = 500L
                                        ),
                                        metadata = TaskMetadata(
                                            originDeviceId = crdtState.nodeId,
                                            tags = listOf("demo", "security", "encryption")
                                        ),
                                        createdAt = Clock.System.now().toEpochMilliseconds()
                                    )
                                    computeScheduler.submitTask(demoTask)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF10B981)
                            )
                        ) {
                            Text("Encrypt", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // Platform Information
            AnimatedVisibility(showPlatform) {
                InfoCard(
                    title = "Platform Information",
                    titleColor = MaterialTheme.colorScheme.primary
                ) {
                    PlatformInfoRow("Device", platform.name)
                    PlatformInfoRow("Type", platform.deviceType.name)
                    PlatformInfoRow("Compute Power", platform.capabilities.computePower.name)
                    PlatformInfoRow("Screen Size", platform.capabilities.screenSize.name)
                    PlatformInfoRow("Bluetooth LE", if (platform.capabilities.hasBluetoothLE) "✅" else "❌")
                    PlatformInfoRow("WiFi", if (platform.capabilities.hasWiFi) "✅" else "❌")
                    PlatformInfoRow("Can Offload Compute", if (platform.capabilities.canOffloadCompute) "✅" else "❌")
                    PlatformInfoRow("Max Tasks", platform.capabilities.maxConcurrentTasks.toString())
                }
            }
            
            // CRDT State Information
            AnimatedVisibility(showCrdt) {
                InfoCard(
                    title = "CRDT State Information",
                    titleColor = MaterialTheme.colorScheme.secondary
                ) {
                    PlatformInfoRow("Node ID", crdtState.nodeId.toString().take(8) + "...")
                    PlatformInfoRow("Vector Clock Size", crdtState.vectorClock.clocks.size.toString())
                    PlatformInfoRow("Operations Count", crdtState.operations.size.toString())
                    PlatformInfoRow("Last Sync", 
                        if (crdtState.lastSyncTimestamp > 0) "Synced" else "Never"
                    )
                    PlatformInfoRow("State Materialized", 
                        if (omnisyncraState != null) "✅" else "❌"
                    )
                }
            }
            
            // Device Discovery Information
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
                        
                        discoveredDevices.take(3).forEach { device ->
                            DeviceCard(device)
                        }
                    }
                }
            }
            
            // Compute Offloading Information
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
                        
                        runningTasks.take(3).forEach { execution ->
                            TaskCard(execution)
                        }
                    }
                    
                    if (completedTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Recent Completions:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        completedTasks.takeLast(2).forEach { result ->
                            CompletedTaskCard(result)
                        }
                    }
                }
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
private fun CompletedTaskCard(result: TaskResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (result.status == TaskStatus.COMPLETED) {
                androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = 0.1f)
            } else {
                androidx.compose.ui.graphics.Color(0xFFEF4444).copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Task ${result.taskId.toString().take(8)}...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (result.status == TaskStatus.COMPLETED) {
                    androidx.compose.ui.graphics.Color(0xFF10B981)
                } else {
                    androidx.compose.ui.graphics.Color(0xFFEF4444)
                }
            )
            Text(
                text = "Status: ${result.status.name} • ${result.executionTimeMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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