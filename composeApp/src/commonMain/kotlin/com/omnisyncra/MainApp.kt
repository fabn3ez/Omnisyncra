package com.omnisyncra

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.ui.*
import com.omnisyncra.ui.morphing.*
import com.omnisyncra.ui.components.*
import com.omnisyncra.ui.gestures.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val morphingController = remember { MorphingController() }
    val gestureController = remember { GestureController() }
    val hapticFeedback = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    
    val morphingState by morphingController.state
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
            secondary = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
            tertiary = androidx.compose.ui.graphics.Color(0xFF10B981),
            background = androidx.compose.ui.graphics.Color(0xFF0F0F23),
            surface = androidx.compose.ui.graphics.Color(0xFF1E1E3F)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Enhanced Header with Morphing
            MorphingCard(
                role = morphingState.currentRole,
                context = morphingState.currentContext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🌐 Omnisyncra KMP",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Phase 7: Dynamic UI Morphing & Transitions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoleIndicator(
                            currentRole = morphingState.currentRole,
                            targetRole = morphingState.targetRole,
                            isTransitioning = morphingState.isTransitioning
                        )
                        ContextIndicator(
                            currentContext = morphingState.currentContext,
                            targetContext = morphingState.targetContext,
                            isTransitioning = morphingState.isTransitioning
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Role Switching Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DeviceRole.values().forEach { role ->
                    MorphingButton(
                        onClick = { 
                            hapticFeedback.lightImpact()
                            scope.launch {
                                morphingController.morphToRole(role)
                            }
                        },
                        role = morphingState.currentRole,
                        context = morphingState.currentContext,
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = when (role) {
                                DeviceRole.PRIMARY -> "P"
                                DeviceRole.SECONDARY -> "S"
                                DeviceRole.VIEWER -> "V"
                                DeviceRole.COMPUTE -> "C"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tab Navigation with Morphing
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { 
                        selectedTab = 0
                        scope.launch {
                            morphingController.morphToContext(UIContext.EDITOR)
                        }
                    },
                    text = { Text("Devices") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { 
                        selectedTab = 1
                        scope.launch {
                            morphingController.morphToContext(UIContext.VIEWER)
                        }
                    },
                    text = { Text("CRDT State") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { 
                        selectedTab = 2
                        scope.launch {
                            morphingController.morphToContext(UIContext.PALETTE)
                        }
                    },
                    text = { Text("Compute") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { 
                        selectedTab = 3
                        scope.launch {
                            morphingController.morphToContext(UIContext.SETTINGS)
                        }
                    },
                    text = { Text("Security") }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { 
                        selectedTab = 4
                        scope.launch {
                            morphingController.morphToContext(UIContext.EDITOR)
                        }
                    },
                    text = { Text("Enhanced UI") }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { 
                        selectedTab = 5
                        scope.launch {
                            morphingController.morphToContext(UIContext.MESH)
                        }
                    },
                    text = { Text("Morphing Demo") }
                )
            }
            
            // Adaptive Content Layout
            AdaptiveLayout(
                role = morphingState.currentRole,
                context = morphingState.currentContext,
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedTab) {
                    0 -> DeviceDiscoveryScreen()
                    1 -> CrdtStateScreen()
                    2 -> ComputeTasksScreen()
                    3 -> SecurityDemoScreen()
                    4 -> EnhancedUIScreen()
                    5 -> MorphingDemoScreen(morphingController, gestureController, hapticFeedback)
                }
            }
            
            // Transition Overlay
            TransitionOverlay(
                isVisible = morphingState.isTransitioning,
                progress = morphingState.transitionProgress
            )
        }
    }
}

@Composable
private fun MorphingDemoScreen(
    morphingController: MorphingController,
    gestureController: GestureController,
    hapticFeedback: HapticFeedback
) {
    val morphingState by morphingController.state
    val gestureState by gestureController.gestureState
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current State Display
        MorphingCard(
            role = morphingState.currentRole,
            context = morphingState.currentContext
        ) {
            Text(
                text = "🎭 Morphing System Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Current Role: ${morphingState.currentRole}")
            Text("Current Context: ${morphingState.currentContext}")
            if (morphingState.isTransitioning) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = morphingState.transitionProgress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Transitioning... ${(morphingState.transitionProgress * 100).toInt()}%")
            }
        }
        
        // Context Switching
        MorphingCard(
            role = morphingState.currentRole,
            context = morphingState.currentContext
        ) {
            Text(
                text = "🎨 Context Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                UIContext.values().forEach { context ->
                    MorphingButton(
                        onClick = {
                            hapticFeedback.mediumImpact()
                            scope.launch {
                                morphingController.morphToContext(context)
                            }
                        },
                        role = morphingState.currentRole,
                        context = morphingState.currentContext,
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = when (context) {
                                UIContext.EDITOR -> "✏️"
                                UIContext.PALETTE -> "🎨"
                                UIContext.VIEWER -> "👁️"
                                UIContext.MESH -> "🌐"
                                UIContext.SETTINGS -> "⚙️"
                            }
                        )
                    }
                }
            }
        }
        
        // Gesture Recognition Area
        MorphingCard(
            role = morphingState.currentRole,
            context = morphingState.currentContext,
            modifier = Modifier.height(150.dp)
        ) {
            Text(
                text = "👆 Gesture Recognition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                if (gestureState.isActive) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text("🎯 Gesture Active!")
                        Text("Distance: ${gestureState.distance.toInt()}px")
                        gestureState.direction?.let {
                            Text("Direction: ${it.name}")
                        }
                    }
                } else {
                    Text(
                        text = "Swipe here to test gesture recognition\n• Triangle: Context switch\n• Circle: Role switch\n• Z-pattern: Mesh view",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Visual Effects Preview
        MorphingCard(
            role = morphingState.currentRole,
            context = morphingState.currentContext
        ) {
            Text(
                text = "✨ Visual Effects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Proximity visualization\n• Particle effects\n• Ambient lighting\n• Parallax layers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun VisualEffectsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "✨ Visual Effects Demo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "🌐 Proximity Visualization\n(Mesh Network Display)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "✨ Particle Effects\n(Ambient Animations)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        
        Text(
            text = "Features implemented:\n• Proximity visualization with animated connections\n• Particle effects for mesh visualization\n• Ambient lighting effects\n• Parallax layers for depth\n• Real-time device mesh network visualization",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun GesturesScreen(
    gestureController: GestureController,
    hapticFeedback: HapticFeedback
) {
    val gestureState by gestureController.gestureState
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "👆 Gesture Recognition System",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                if (gestureState.isActive) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text("🎯 Gesture Detected!")
                        Text("Distance: ${gestureState.distance.toInt()}px")
                        gestureState.direction?.let {
                            Text("Direction: ${it.name}")
                        }
                    }
                } else {
                    Text(
                        text = "Swipe here to test gesture recognition",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🎭 Gesture Patterns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Triangle Pattern: Context switching")
                Text("• Circle Pattern: Device role switching")
                Text("• Z-Pattern: Mesh network view")
                Text("• Multi-touch: Pinch/zoom and rotation")
                Text("• Haptic Feedback: Integrated across all gestures")
            }
        }
        
        Text(
            text = "Advanced Features:\n• Swipe pattern recognition\n• Multi-touch gesture support\n• Haptic feedback integration\n• Context-aware gesture responses\n• Cross-platform gesture handling",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}