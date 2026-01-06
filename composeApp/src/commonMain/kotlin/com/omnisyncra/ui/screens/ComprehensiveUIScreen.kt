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
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.omnisyncra.test.testSystems

/**
 * Comprehensive UI Screen showcasing all core functionalities
 * Premium dark theme with glassmorphism effects
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComprehensiveUIScreen() {
    var selectedSection by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    
    // Animation states
    val infiniteTransition = rememberInfiniteTransition()
    val glowAnimation by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Premium dark theme colors
    val primaryGlow = Color(0xFF6366F1)
    val secondaryGlow = Color(0xFF8B5CF6)
    val accentGlow = Color(0xFF10B981)
    val surfaceGlass = Color(0x1A1E1E3F)
    val backgroundDark = Color(0xFF0A0A1A)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        backgroundDark,
                        Color(0xFF0F0F23),
                        Color(0xFF1A1A2E)
                    ),
                    radius = 1000f
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Premium Header with Glassmorphism
            PremiumHeader(
                glowAnimation = glowAnimation,
                primaryGlow = primaryGlow
            )
            
            // Main Content Area
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Sidebar Navigation
                PremiumSidebar(
                    selectedSection = selectedSection,
                    onSectionSelected = { selectedSection = it },
                    surfaceGlass = surfaceGlass,
                    primaryGlow = primaryGlow,
                    modifier = Modifier.width(280.dp)
                )
                
                // Main Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    when (selectedSection) {
                        0 -> DeviceDiscoverySection(primaryGlow, accentGlow)
                        1 -> SecurityManagementSection(secondaryGlow, primaryGlow)
                        2 -> AIManagementScreen()
                        3 -> DistributedStateSection(primaryGlow, secondaryGlow)
                        4 -> ComputeOffloadingSection(accentGlow, secondaryGlow)
                        5 -> SystemStatusSection(primaryGlow, accentGlow)
                    }
                }
            }
        }
        
        // Floating Action Elements
        FloatingActionElements(
            primaryGlow = primaryGlow,
            glowAnimation = glowAnimation,
            onTestSystems = {
                scope.launch {
                    testSystems()
                }
            }
        )
    }
}

@Composable
private fun PremiumHeader(
    glowAnimation: Float,
    primaryGlow: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1A1E1E3F)
        ),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo and Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryGlow.copy(alpha = glowAnimation),
                                    primaryGlow.copy(alpha = 0.1f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "Omnisyncra",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "Omnisyncra",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Privacy-First Distributed Computing",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Status Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator("AI", true, primaryGlow)
                StatusIndicator("Security", true, Color(0xFF10B981))
                StatusIndicator("Network", true, Color(0xFFF59E0B))
                StatusIndicator("Sync", false, Color(0xFFEF4444))
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    label: String,
    isActive: Boolean,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isActive) color else Color.Gray,
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun PremiumSidebar(
    selectedSection: Int,
    onSectionSelected: (Int) -> Unit,
    surfaceGlass: Color,
    primaryGlow: Color,
    modifier: Modifier = Modifier
) {
    val sections = listOf(
        SidebarSection("Device Discovery", Icons.Default.Devices, "Proximity-aware device detection"),
        SidebarSection("Security & Trust", Icons.Default.Security, "End-to-end encryption & trust"),
        SidebarSection("AI Integration", Icons.Default.Psychology, "Privacy-first AI processing"),
        SidebarSection("Distributed State", Icons.Default.Storage, "CRDT-based synchronization"),
        SidebarSection("Compute Offloading", Icons.Default.CloudQueue, "Intelligent task distribution"),
        SidebarSection("System Status", Icons.Default.Dashboard, "Real-time system monitoring")
    )
    
    Card(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = surfaceGlass
        ),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Core Systems",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            itemsIndexed(sections) { index, section ->
                SidebarItem(
                    section = section,
                    isSelected = selectedSection == index,
                    onClick = { onSectionSelected(index) },
                    primaryGlow = primaryGlow
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    section: SidebarSection,
    isSelected: Boolean,
    onClick: () -> Unit,
    primaryGlow: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                primaryGlow.copy(alpha = 0.2f) 
            else 
                Color.Transparent
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
            Icon(
                imageVector = section.icon,
                contentDescription = section.title,
                tint = if (isSelected) primaryGlow else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DeviceDiscoverySection(
    primaryGlow: Color,
    accentGlow: Color
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Device Discovery & Proximity Detection",
                subtitle = "Multi-protocol device discovery with proximity awareness",
                icon = Icons.Default.Devices,
                color = primaryGlow
            )
        }
        
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Discovered Devices",
                    value = "7",
                    subtitle = "Active in network",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Proximity Range",
                    value = "15m",
                    subtitle = "BLE detection",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Network Latency",
                    value = "12ms",
                    subtitle = "Average response",
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            GlassmorphismCard(
                title = "Discovered Devices",
                color = primaryGlow
            ) {
                Text(
                    text = "• MacBook Pro (Primary)\n• iPhone 15 Pro (Secondary)\n• iPad Air (Viewer)\n• Desktop PC (Compute Node)",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        item {
            GlassmorphismCard(
                title = "Proximity Visualization",
                color = accentGlow
            ) {
                Text(
                    text = "Real-time mesh network visualization with animated connections and proximity indicators.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SecurityManagementSection(
    secondaryGlow: Color,
    primaryGlow: Color
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Security & Trust Management",
                subtitle = "End-to-end encryption with device authentication",
                icon = Icons.Default.Security,
                color = secondaryGlow
            )
        }
        
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Trusted Devices",
                    value = "5",
                    subtitle = "Authenticated",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Active Sessions",
                    value = "3",
                    subtitle = "Encrypted",
                    color = secondaryGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Key Rotations",
                    value = "24h",
                    subtitle = "Last rotation",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            GlassmorphismCard(
                title = "Security Status",
                color = secondaryGlow
            ) {
                Text(
                    text = "All connections encrypted with AES-256-GCM\nCertificates valid and up-to-date\nNo security violations detected",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AIIntegrationSection(
    accentGlow: Color,
    primaryGlow: Color
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Privacy-First AI Integration",
                subtitle = "Local processing with intelligent context analysis",
                icon = Icons.Default.Psychology,
                color = accentGlow
            )
        }
        
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Local Processing",
                    value = "94%",
                    subtitle = "Privacy preserved",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Context Analysis",
                    value = "Real-time",
                    subtitle = "Active learning",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Data Sanitized",
                    value = "100%",
                    subtitle = "PII protected",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            GlassmorphismCard(
                title = "AI Processing Pipeline",
                color = accentGlow
            ) {
                Text(
                    text = "Local data sanitization active\nContext analysis running\nPrivacy-preserving ML models loaded",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DistributedStateSection(
    primaryGlow: Color,
    secondaryGlow: Color
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Distributed State Management",
                subtitle = "CRDT-based conflict-free synchronization",
                icon = Icons.Default.Storage,
                color = primaryGlow
            )
        }
        
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Sync Operations",
                    value = "1,247",
                    subtitle = "Conflict-free",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Vector Clock",
                    value = "v7.3.2",
                    subtitle = "Current state",
                    color = secondaryGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Merge Success",
                    value = "99.8%",
                    subtitle = "Automatic resolution",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            GlassmorphismCard(
                title = "CRDT State Visualization",
                color = primaryGlow
            ) {
                Text(
                    text = "Conflict-free replicated data types ensuring eventual consistency across all devices.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ComputeOffloadingSection(
    accentGlow: Color,
    secondaryGlow: Color
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Asymmetric Compute Offloading",
                subtitle = "Intelligent task distribution based on device capabilities",
                icon = Icons.Default.CloudQueue,
                color = accentGlow
            )
        }
        
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Active Tasks",
                    value = "12",
                    subtitle = "Distributed",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Compute Nodes",
                    value = "4",
                    subtitle = "Available",
                    color = secondaryGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Efficiency Gain",
                    value = "340%",
                    subtitle = "Performance boost",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            GlassmorphismCard(
                title = "Compute Nodes",
                color = accentGlow
            ) {
                Text(
                    text = "High-performance nodes available for distributed computing tasks with intelligent load balancing.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SystemStatusSection(
    primaryGlow: Color,
    accentGlow: Color
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "System Status & Monitoring",
                subtitle = "Real-time system health and performance metrics",
                icon = Icons.Default.Dashboard,
                color = primaryGlow
            )
        }
        
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "System Health",
                    value = "98%",
                    subtitle = "Optimal",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Memory Usage",
                    value = "67%",
                    subtitle = "Available",
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Network I/O",
                    value = "2.4MB/s",
                    subtitle = "Throughput",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            GlassmorphismCard(
                title = "System Health",
                color = primaryGlow
            ) {
                Text(
                    text = "All systems operational with optimal performance metrics and no critical issues detected.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// Supporting Composables
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.3f),
                            color.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1A1E1E3F)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun GlassmorphismCard(
    title: String,
    color: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1A1E1E3F)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            content()
        }
    }
}

@Composable
private fun FloatingActionElements(
    primaryGlow: Color,
    glowAnimation: Float,
    onTestSystems: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        FloatingActionButton(
            onClick = onTestSystems,
            modifier = Modifier
                .padding(24.dp)
                .size(56.dp),
            containerColor = primaryGlow.copy(alpha = glowAnimation),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Test Systems",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Data classes
private data class SidebarSection(
    val title: String,
    val icon: ImageVector,
    val description: String
)