package com.omnisyncra.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.omnisyncra.core.discovery.DeviceDiscovery
import com.omnisyncra.core.platform.Platform

/**
 * Cross-Platform Context Sharing Demo Screen
 * Shows real-time synchronization between all 4 platforms
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossPlatformDemoScreen() {
    val deviceDiscovery: DeviceDiscovery = koinInject()
    val platform: Platform = koinInject()
    val scope = rememberCoroutineScope()
    
    // Demo state
    var demoInput by remember { mutableStateOf("") }
    var sharedContext by remember { mutableStateOf("") }
    var lastUpdatedBy by remember { mutableStateOf("") }
    
    // Animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAnimation by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Colors
    val primaryGlow = Color(0xFF6366F1)
    val accentGlow = Color(0xFF10B981)
    val surfaceGlass = Color(0x1A1E1E3F)
    val platformColors = mapOf(
        "Android" to Color(0xFF3DDC84),
        "Desktop" to Color(0xFF0F9D58),
        "JVM" to Color(0xFF0F9D58),
        "JavaScript" to Color(0xFFF7DF1E),
        "Web" to Color(0xFFF7DF1E),
        "WebAssembly" to Color(0xFF654FF0),
        "WASM" to Color(0xFF654FF0)
    )
    
    // Listen to shared context changes
    LaunchedEffect(Unit) {
        deviceDiscovery.sharedContext.collect { context ->
            sharedContext = context.data["demo_input"] ?: ""
            lastUpdatedBy = context.updatedBy
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = surfaceGlass
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🚀 CROSS-PLATFORM DEMO",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryGlow,
                    modifier = Modifier.alpha(pulseAnimation)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Type below and watch it sync across all platforms!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        
        // Platform Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = surfaceGlass
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "📱 Current Platform: ${platform.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = platformColors[platform.name] ?: primaryGlow
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlatformIndicator("📱", "Android", platformColors["Android"]!!)
                    PlatformIndicator("💻", "Desktop", platformColors["Desktop"]!!)
                    PlatformIndicator("🌐", "Web", platformColors["Web"]!!)
                    PlatformIndicator("⚡", "WASM", platformColors["WASM"]!!)
                }
            }
        }
        
        // Demo Input
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = surfaceGlass
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "✨ Demo Input",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = demoInput,
                    onValueChange = { newValue ->
                        demoInput = newValue
                        // Share context immediately
                        scope.launch {
                            deviceDiscovery.shareContext(
                                mapOf("demo_input" to newValue)
                            )
                        }
                    },
                    label = { Text("Type something...", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("This will sync to all platforms!", color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                if (demoInput.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Syncing",
                            tint = accentGlow,
                            modifier = Modifier
                                .size(16.dp)
                                .alpha(pulseAnimation)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Syncing to all platforms...",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentGlow,
                            modifier = Modifier.alpha(pulseAnimation)
                        )
                    }
                }
            }
        }
        
        // Shared Context Display
        if (sharedContext.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = accentGlow.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, accentGlow.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Synced",
                            tint = accentGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "✅ Synced Content",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentGlow
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = sharedContext,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.Black.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    )
                    
                    if (lastUpdatedBy.isNotEmpty() && lastUpdatedBy != deviceDiscovery.currentDevice.value.id) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "📡 Updated from another platform",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
        
        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = primaryGlow.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, primaryGlow.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "🎬 For Your Video Demo:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryGlow
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val instructions = listOf(
                    "1. Show all 4 platforms side by side",
                    "2. Type something in this input field",
                    "3. Watch it appear in all other UIs instantly!",
                    "4. Highlight the platform icons and real-time sync"
                )
                
                instructions.forEach { instruction ->
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformIndicator(
    icon: String,
    name: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineSmall
        )
        
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}