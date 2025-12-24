package com.omnisyncra.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.ui.visual.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersiveVisualScreen(
    modifier: Modifier = Modifier
) {
    var selectedDemo by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    
    // Demo data
    val proximityDevices by rememberProximityDevices()
    val proximityConnections by rememberProximityConnections()
    val meshNodes by rememberMeshNodes()
    val meshConnections by rememberMeshConnections()
    val lightSources by rememberAmbientLightSources()
    val proximityZones by rememberProximityZones()
    val parallaxLayers by rememberParallaxLayers()
    
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
                    text = "🌟 Phase 8: Immersive Visual Experience",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Real-time proximity visualization, mesh networks, ambient lighting, and parallax effects",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        
        // Demo Selection Tabs
        TabRow(selectedTabIndex = selectedDemo) {
            Tab(
                selected = selectedDemo == 0,
                onClick = { selectedDemo = 0 },
                text = { Text("Proximity") }
            )
            Tab(
                selected = selectedDemo == 1,
                onClick = { selectedDemo = 1 },
                text = { Text("Mesh Network") }
            )
            Tab(
                selected = selectedDemo == 2,
                onClick = { selectedDemo = 2 },
                text = { Text("Ambient Light") }
            )
            Tab(
                selected = selectedDemo == 3,
                onClick = { selectedDemo = 3 },
                text = { Text("Parallax") }
            )
            Tab(
                selected = selectedDemo == 4,
                onClick = { selectedDemo = 4 },
                text = { Text("Combined") }
            )
        }
        
        // Demo Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedDemo) {
                0 -> ProximityVisualizationDemo(proximityDevices, proximityConnections)
                1 -> MeshNetworkDemo(meshNodes, meshConnections)
                2 -> AmbientLightingDemo(lightSources, proximityZones)
                3 -> ParallaxEffectsDemo(parallaxLayers)
                4 -> CombinedVisualDemo(
                    proximityDevices, proximityConnections,
                    meshNodes, meshConnections,
                    lightSources, proximityZones,
                    parallaxLayers
                )
            }
        }
    }
}

@Composable
private fun ProximityVisualizationDemo(
    devices: List<ProximityDevice>,
    connections: List<Connection>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "📡 Proximity Visualization",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            ProximityVisualization(
                devices = devices,
                connections = connections,
                showPulse = true,
                showConnections = true,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Device Status
        LazyDeviceStatus(devices, connections)
    }
}

@Composable
private fun MeshNetworkDemo(
    nodes: List<MeshNode>,
    connections: List<MeshConnection>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🌐 Mesh Network Visualization",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            MeshNetworkVisualization(
                nodes = nodes,
                connections = connections,
                showDataFlow = true,
                showNetworkStats = true,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Network Statistics
        NetworkStatistics(nodes, connections)
    }
}

@Composable
private fun AmbientLightingDemo(
    lightSources: List<LightSource>,
    proximityZones: List<ProximityZone>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "💡 Ambient Lighting Effects",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black
            )
        ) {
            AmbientLighting(
                lightSources = lightSources,
                proximityZones = proximityZones,
                responseToProximity = true,
                ambientIntensity = 0.4f,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Lighting Controls
        LightingControls(lightSources, proximityZones)
    }
}

@Composable
private fun ParallaxEffectsDemo(
    layers: List<ParallaxLayer>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🌌 Parallax Depth Effects",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            ParallaxEffects(
                layers = layers,
                enableInteraction = true,
                autoScroll = true,
                scrollSpeed = 0.3f,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Parallax Info
        ParallaxInfo(layers)
    }
}

@Composable
private fun CombinedVisualDemo(
    devices: List<ProximityDevice>,
    connections: List<Connection>,
    nodes: List<MeshNode>,
    meshConnections: List<MeshConnection>,
    lightSources: List<LightSource>,
    proximityZones: List<ProximityZone>,
    parallaxLayers: List<ParallaxLayer>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "✨ Combined Visual Experience",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background parallax
                ParallaxEffects(
                    layers = parallaxLayers.take(2), // Only background layers
                    enableInteraction = false,
                    autoScroll = true,
                    scrollSpeed = 0.1f,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Ambient lighting
                AmbientLighting(
                    lightSources = lightSources,
                    proximityZones = proximityZones,
                    responseToProximity = true,
                    ambientIntensity = 0.3f,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Mesh network overlay
                MeshNetworkVisualization(
                    nodes = nodes,
                    connections = meshConnections,
                    showDataFlow = true,
                    showNetworkStats = false,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Proximity visualization on top
                ProximityVisualization(
                    devices = devices,
                    connections = connections,
                    showPulse = true,
                    showConnections = true,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Foreground parallax
                ParallaxEffects(
                    layers = parallaxLayers.takeLast(1), // Only foreground layer
                    enableInteraction = true,
                    autoScroll = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Combined stats
        CombinedStats(devices, nodes, lightSources)
    }
}

@Composable
private fun LazyDeviceStatus(
    devices: List<ProximityDevice>,
    connections: List<Connection>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Device Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            devices.forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${device.id} (${device.deviceType})")
                    Text(
                        text = if (device.isConnected) "Connected" else "Disconnected",
                        color = if (device.isConnected) Color.Green else Color.Red
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Active Connections: ${connections.count { it.isActive }}")
        }
    }
}

@Composable
private fun NetworkStatistics(
    nodes: List<MeshNode>,
    connections: List<MeshConnection>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Network Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Total Nodes: ${nodes.size}")
            Text("Active Connections: ${connections.count { it.isActive }}")
            Text("Average Bandwidth: ${(connections.map { it.bandwidth }.average() * 100).toInt() / 100.0}")
            Text("Average Latency: ${(connections.map { it.latency }.average() * 10).toInt() / 10.0} ms")
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Node Types:")
            MeshNodeType.values().forEach { type ->
                val count = nodes.count { it.nodeType == type }
                Text("  $type: $count")
            }
        }
    }
}

@Composable
private fun LightingControls(
    lightSources: List<LightSource>,
    proximityZones: List<ProximityZone>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Lighting Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Light Sources: ${lightSources.size}")
            lightSources.forEach { light ->
                Text("  ${light.type}: Intensity ${light.intensity}")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Proximity Zones: ${proximityZones.size}")
            proximityZones.forEach { zone ->
                Text("  Zone: Strength ${zone.strength}")
            }
        }
    }
}

@Composable
private fun ParallaxInfo(
    layers: List<ParallaxLayer>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Parallax Layers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            layers.forEach { layer ->
                Text("${layer.id}: Depth ${layer.depth}, Elements ${layer.elements.size}")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("💡 Drag to interact with parallax layers")
            Text("🔄 Auto-scroll creates dynamic movement")
        }
    }
}

@Composable
private fun CombinedStats(
    devices: List<ProximityDevice>,
    nodes: List<MeshNode>,
    lightSources: List<LightSource>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Combined Visual System",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${devices.size}", style = MaterialTheme.typography.headlineSmall)
                    Text("Proximity Devices", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${nodes.size}", style = MaterialTheme.typography.headlineSmall)
                    Text("Mesh Nodes", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${lightSources.size}", style = MaterialTheme.typography.headlineSmall)
                    Text("Light Sources", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "✨ All visual systems working together for immersive experience",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}