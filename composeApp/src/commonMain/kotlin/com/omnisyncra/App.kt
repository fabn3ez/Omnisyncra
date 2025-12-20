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
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.state.DistributedStateManager
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val platform = koinInject<Platform>()
    val stateManager = koinInject<DistributedStateManager>()
    val scope = rememberCoroutineScope()
    
    // Initialize state manager
    LaunchedEffect(Unit) {
        stateManager.initialize()
    }
    
    val crdtState by stateManager.crdtState.collectAsState()
    val omnisyncraState by stateManager.omnisyncraState.collectAsState()
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
            secondary = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
            background = androidx.compose.ui.graphics.Color(0xFF0F0F23),
            surface = androidx.compose.ui.graphics.Color(0xFF1E1E3F)
        )
    ) {
        var showDetails by remember { mutableStateOf(false) }
        var showCrdtInfo by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
                        text = "Distributed State Mesh",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showDetails = !showDetails },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (showDetails) "Hide Platform" else "Show Platform")
                        }
                        
                        Button(
                            onClick = { showCrdtInfo = !showCrdtInfo },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(if (showCrdtInfo) "Hide CRDT" else "Show CRDT")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            AnimatedVisibility(showDetails) {
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
                            text = "Platform Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
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
            }
            
            AnimatedVisibility(showCrdtInfo) {
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
                            text = "CRDT State Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        PlatformInfoRow("Node ID", crdtState.nodeId.toString().take(8) + "...")
                        PlatformInfoRow("Vector Clock Size", crdtState.vectorClock.clocks.size.toString())
                        PlatformInfoRow("Operations Count", crdtState.operations.size.toString())
                        PlatformInfoRow("Last Sync", 
                            if (crdtState.lastSyncTimestamp > 0) "Synced" else "Never"
                        )
                        PlatformInfoRow("State Materialized", 
                            if (omnisyncraState != null) "✅" else "❌"
                        )
                        
                        omnisyncraState?.let { state ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Application State:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            PlatformInfoRow("UI Mode", state.uiState.currentMode.name)
                            PlatformInfoRow("Connected Devices", state.deviceMesh.connectedDevices.size.toString())
                            PlatformInfoRow("Active Contexts", state.contextGraph.contexts.size.toString())
                        }
                    }
                }
            }
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