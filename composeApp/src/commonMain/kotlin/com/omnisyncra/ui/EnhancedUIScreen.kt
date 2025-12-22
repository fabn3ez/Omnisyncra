package com.omnisyncra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.core.domain.*
import com.omnisyncra.core.security.TrustLevel
import com.omnisyncra.core.ui.animations.*
import com.omnisyncra.core.ui.gestures.*
import com.omnisyncra.core.ui.indicators.*
import com.omnisyncra.core.ui.theme.*
import com.omnisyncra.core.ui.accessibility.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedUIScreen() {
    val themeState = rememberThemeState()
    val gestureState = rememberGestureState()
    val accessibilityState = rememberAccessibilityState()
    val scope = rememberCoroutineScope()
    
    // Demo state
    var selectedDemo by remember { mutableIntStateOf(0) }
    var deviceLoadDemo by remember { mutableFloatStateOf(0.3f) }
    var taskProgressDemo by remember { mutableFloatStateOf(0.0f) }
    var isTaskRunning by remember { mutableStateOf(false) }
    var connectionTrigger by remember { mutableStateOf(false) }
    var errorTrigger by remember { mutableStateOf(false) }
    
    // Simulate task progress
    LaunchedEffect(isTaskRunning) {
        if (isTaskRunning) {
            while (taskProgressDemo < 1.0f && isTaskRunning) {
                delay(100)
                taskProgressDemo += 0.02f
            }
            if (taskProgressDemo >= 1.0f) {
                isTaskRunning = false
            }
        }
    }
    
    OmnisyncraTheme(config = themeState.config) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with theme controls
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
                        text = "🎨 Enhanced UI/UX Demo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Advanced animations, gestures, themes & accessibility",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Theme controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { themeState.toggleDarkMode() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (themeState.config.isDarkMode) "Light" else "Dark")
                        }
                        
                        Button(
                            onClick = { themeState.toggleHighContrast() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Contrast")
                        }
                        
                        Button(
                            onClick = { 
                                val newSize = when (themeState.config.fontSize) {
                                    FontSize.SMALL -> FontSize.MEDIUM
                                    FontSize.MEDIUM -> FontSize.LARGE
                                    FontSize.LARGE -> FontSize.EXTRA_LARGE
                                    FontSize.EXTRA_LARGE -> FontSize.SMALL
                                }
                                themeState.setFontSize(newSize)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Font")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Demo selection tabs
            TabRow(selectedTabIndex = selectedDemo) {
                Tab(
                    selected = selectedDemo == 0,
                    onClick = { selectedDemo = 0 },
                    text = { Text("Animations") }
                )
                Tab(
                    selected = selectedDemo == 1,
                    onClick = { selectedDemo = 1 },
                    text = { Text("Indicators") }
                )
                Tab(
                    selected = selectedDemo == 2,
                    onClick = { selectedDemo = 2 },
                    text = { Text("Gestures") }
                )
                Tab(
                    selected = selectedDemo == 3,
                    onClick = { selectedDemo = 3 },
                    text = { Text("Accessibility") }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Demo content
            when (selectedDemo) {
                0 -> AnimationsDemo(
                    connectionTrigger = connectionTrigger,
                    errorTrigger = errorTrigger,
                    onConnectionTrigger = { connectionTrigger = !connectionTrigger },
                    onErrorTrigger = { errorTrigger = !errorTrigger }
                )
                1 -> IndicatorsDemo(
                    deviceLoad = deviceLoadDemo,
                    taskProgress = taskProgressDemo,
                    isTaskRunning = isTaskRunning,
                    onLoadChange = { deviceLoadDemo = it },
                    onStartTask = { 
                        isTaskRunning = true
                        taskProgressDemo = 0f
                    }
                )
                2 -> GesturesDemo(gestureState)
                3 -> AccessibilityDemo(accessibilityState)
            }
        }
    }
}

@Composable
private fun AnimationsDemo(
    connectionTrigger: Boolean,
    errorTrigger: Boolean,
    onConnectionTrigger: () -> Unit,
    onErrorTrigger: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Animation Showcase",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = onConnectionTrigger) {
                    Text("Trigger Connection")
                }
                Button(onClick = onErrorTrigger) {
                    Text("Trigger Error")
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceAnimation(connectionTrigger)
                    .shakeAnimation(errorTrigger)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Animated Device Card",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("MacBook Pro")
                            Text(
                                text = "High Performance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF10B981))
                                .glowAnimation(connectionTrigger)
                        )
                    }
                }
            }
        }
        
        itemsIndexed(listOf("Device 1", "Device 2", "Device 3")) { index, device ->
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.slideInAnimation(
                    visible = true,
                    direction = SlideDirection.LEFT,
                    config = AnimationConfig(delayMillis = index * 100)
                )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = device,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicatorsDemo(
    deviceLoad: Float,
    taskProgress: Float,
    isTaskRunning: Boolean,
    onLoadChange: (Float) -> Unit,
    onStartTask: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Visual Indicators",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Device Load Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DeviceLoadIndicator(
                            loadPercentage = deviceLoad,
                            showLabel = true
                        )
                        
                        Column {
                            Text("Adjust Load:")
                            Slider(
                                value = deviceLoad,
                                onValueChange = onLoadChange,
                                modifier = Modifier.width(150.dp)
                            )
                        }
                    }
                }
            }
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Task Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = onStartTask,
                            enabled = !isTaskRunning
                        ) {
                            Text("Start Task")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TaskProgressIndicator(
                        progress = taskProgress,
                        taskType = "AI Inference",
                        isRunning = isTaskRunning,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Status Indicators",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SignalStrengthIndicator(signalStrength = -45)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Signal",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SecurityLevelIndicator(
                                trustLevel = TrustLevel.TRUSTED,
                                showLabel = false
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Security",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ComputePowerIndicator(
                                computePower = ComputePower.HIGH
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProximityIndicator(
                                distance = ProximityDistance.CLOSE,
                                signalStrength = -55
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GesturesDemo(gestureState: GestureState) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Gesture Interactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Gesture State",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Dragging: ${gestureState.isDragging}")
                    Text("Long Pressing: ${gestureState.isLongPressing}")
                    Text("Selected Devices: ${gestureState.selectedDevices.size}")
                    Text("Zoom Level: ${"%.1f".format(gestureState.zoomLevel)}x")
                    Text("Rotation: ${"%.0f".format(gestureState.rotationAngle)}°")
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .longPressGesture { offset ->
                        gestureState.isLongPressing = true
                    }
                    .doubleTapGesture { offset ->
                        gestureState.zoomLevel = if (gestureState.zoomLevel > 1f) 1f else 2f
                    }
                    .hapticFeedback(gestureState.isLongPressing)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Interactive Area",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Long press or double tap",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        
        item {
            Button(
                onClick = { gestureState.reset() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Gesture State")
            }
        }
    }
}

@Composable
private fun AccessibilityDemo(accessibilityState: AccessibilityState) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Accessibility Features",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Accessibility Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Screen Reader")
                            Switch(
                                checked = accessibilityState.config.screenReaderEnabled,
                                onCheckedChange = { accessibilityState.toggleScreenReader() }
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("High Contrast")
                            Switch(
                                checked = accessibilityState.config.highContrastEnabled,
                                onCheckedChange = { accessibilityState.toggleHighContrast() }
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Large Text")
                            Switch(
                                checked = accessibilityState.config.largeTextEnabled,
                                onCheckedChange = { accessibilityState.toggleLargeText() }
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reduced Motion")
                            Switch(
                                checked = accessibilityState.config.reducedMotionEnabled,
                                onCheckedChange = { accessibilityState.toggleReducedMotion() }
                            )
                        )
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .deviceSemantics(
                        device = Device(
                            name = "Demo Device",
                            type = DeviceType.LAPTOP,
                            capabilities = DeviceCapabilities(
                                computePower = ComputePower.HIGH,
                                maxConcurrentTasks = 4,
                                supportedTaskTypes = emptyList(),
                                batteryLevel = 0.8f
                            )
                        ),
                        isConnected = true,
                        trustLevel = TrustLevel.TRUSTED
                    )
                    .accessibilityTestTag("demo_device")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Accessible Device Card",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "This card has semantic descriptions for screen readers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        item {
            Button(
                onClick = { 
                    accessibilityState.announce("Accessibility demo completed successfully")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Voice Announcement")
            }
        }
    }
    
    // Show accessibility announcements
    if (accessibilityState.lastAnnouncement.isNotEmpty()) {
        AccessibilityAnnouncement(
            message = accessibilityState.lastAnnouncement,
            priority = AnnouncementPriority.NORMAL
        )
    }
}