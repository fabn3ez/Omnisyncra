package com.omnisyncra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.core.state.DistributedStateManager
import org.koin.compose.koinInject

@Composable
fun CrdtStateScreen() {
    val stateManager: DistributedStateManager = koinInject()
    val crdtState by stateManager.crdtState.collectAsState()
    val omnisyncraState by stateManager.omnisyncraState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "🔄 CRDT State",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "CRDT Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Node ID: ${crdtState.nodeId}")
                        Text("Version: ${crdtState.version}")
                        Text("Operations: ${crdtState.operations.size}")
                        Text("Last Updated: ${crdtState.lastUpdated}")
                    }
                }
            }
            
            omnisyncraState?.let { state ->
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Omnisyncra State",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text("Contexts: ${state.contextGraph.contexts.size}")
                            Text("Active Context: ${state.contextGraph.getActiveContext()?.name ?: "None"}")
                            Text("Device Mesh: ${state.deviceMesh.connectedDevices.size} devices")
                        }
                    }
                }
                
                item {
                    Text(
                        text = "Contexts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(state.contextGraph.contexts) { context ->
                    Card {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = context.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Type: ${context.type}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Active: ${context.isActive}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}