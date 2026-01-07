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
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.omnisyncra.core.monitoring.*
import com.omnisyncra.core.network.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Real System Monitoring Screen
 * Uses actual system data from RealSystemMonitor and RealNetworkCommunicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealSystemMonitoringScreen() {
    val systemMonitor: SystemMonitor = koinInject()
    val networkCommunicator: NetworkCommunicator = koinInject()
    
    // Real data states
    val systemHealth by systemMonitor.systemHealth.collectAsState()
    val performanceMetrics by systemMonitor.performanceMetrics.collectAsState()
    val securityStatus by systemMonitor.securityStatus.collectAsState()
    val networkStatus by systemMonitor.networkStatus.collectAsState()
    val connectionStatus by networkCommunicator.connectionStatus.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    
    // Premium colors
    val primaryGlow = Color(0xFF6366F1)
    val accentGlow = Color(0xFF10B981)
    val warningGlow = Color(0xFFF59E0B)
    val errorGlow = Color(0xFFEF4444)
    val surfaceGlass = Color(0x1A1E1E3F)
    
    // Animation
    val infiniteTransition = rememberInfiniteTransition()
    val glowAnimation by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Initialize monitoring on first composition
    LaunchedEffect(Unit) {
        systemMonitor.initialize()
        systemMonitor.startMonitoring()
        networkCommunicator.initialize()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A0A1A),
                        Color(0xFF0F0F23),
                        Color(0xFF1A1A2E)
                    ),
                    radius = 1000f
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with real status
        RealSystemMonitoringHeader(
            glowAnimation = glowAnimation,
            primaryGlow = primaryGlow,
            systemHealth = systemHealth,
            connectionStatus = connectionStatus
        )
        
        // Tab selector
        RealMonitoringTabs(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            surfaceGlass = surfaceGlass,
            primaryGlow = primaryGlow
        )
        
        // Content based on selected tab
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // System Health Tab
                    item {
                        RealSystemHealthCard(
                            systemHealth = systemHealth,
                            surfaceGlass = surfaceGlass,
                            primaryGlow = primaryGlow,
                            accentGlow = accentGlow,
                            warningGlow = warningGlow,
                            errorGlow = errorGlow
                        )
                    }
                    
                    item {
                        RealPerformanceMetricsCard(
                            performanceMetrics = performanceMetrics,
                            surfaceGlass = surfaceGlass,
                            primaryGlow = primaryGlow,
                            accentGlow = accentGlow
                        )
                    }
                }
                
                1 -> {
                    // Security Tab
                    item {
                        RealSecurityStatusCard(
                            securityStatus = securityStatus,
                            surfaceGlass = surfaceGlass,
                            primaryGlow = primaryGlow,
                            accentGlow = accentGlow,
                            errorGlow = errorGlow
                        )
                    }
                }
                
                2 -> {
                    // Network Tab
                    item {
                        RealNetworkStatusCard(
                            networkStatus = networkStatus,
                            connectionStatus = connectionStatus,
                            surfaceGlass = surfaceGlass,
                            primaryGlow = primaryGlow,
                            accentGlow = accentGlow,
                            warningGlow = warningGlow
                        )
                    }
                    
                    item {
                        RealNetworkDevicesCard(
                            networkCommunicator = networkCommunicator,
                            surfaceGlass = surfaceGlass,
                            primaryGlow = primaryGlow,
                            accentGlow = accentGlow,
                            scope = scope
                        )
                    }
                }
                
                3 -> {
                    // System Reports Tab
                    item {
                        RealSystemReportsCard(
                            systemMonitor = systemMonitor,
                            surfaceGlass = surfaceGlass,
                            primaryGlow = primaryGlow,
                            accentGlow = accentGlow,
                            scope = scope
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealSystemMonitoringHeader(
    glowAnimation: Float,
    primaryGlow: Color,
    systemHealth: SystemHealth,
    connectionStatus: ConnectionStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x1A1E1E3F)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = glowAnimation),
                                primaryGlow.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Real System Monitoring",
                    tint = primaryGlow,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Real System Monitoring",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Live System Data & Performance Analytics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            // Real status indicators
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // System health indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val healthColor = when (systemHealth.overallStatus) {
                        HealthStatus.EXCELLENT -> Color(0xFF10B981)
                        HealthStatus.GOOD -> Color(0xFF10B981)
                        HealthStatus.WARNING -> Color(0xFFF59E0B)
                        HealthStatus.CRITICAL -> Color(0xFFEF4444)
                        else -> Color(0xFF10B981)
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                healthColor.copy(alpha = glowAnimation),
                                CircleShape
                            )
                    )
                    Text(
                        text = systemHealth.overallStatus.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = healthColor
                    )
                }
                
                // Network status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val networkColor = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> Color(0xFF10B981)
                        ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
                        ConnectionStatus.DISCOVERING -> Color(0xFF6366F1)
                        ConnectionStatus.DISCONNECTED -> Color(0xFFEF4444)
                        ConnectionStatus.ERROR -> Color(0xFFEF4444)
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                networkColor.copy(alpha = glowAnimation),
                                CircleShape
                            )
                    )
                    Text(
                        text = connectionStatus.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = networkColor
                    )
                }
            }
        }
    }
}

@Composable
private fun RealMonitoringTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    surfaceGlass: Color,
    primaryGlow: Color
) {
    val tabs = listOf("System Health", "Security", "Network", "Reports")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(index) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTab == index) 
                            primaryGlow.copy(alpha = 0.3f) 
                        else 
                            Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = tab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
@Composable
private fun RealSystemHealthCard(
    systemHealth: SystemHealth,
    surfaceGlass: Color,
    primaryGlow: Color,
    accentGlow: Color,
    warningGlow: Color,
    errorGlow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Real System Health",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RealMetricCard(
                    title = "CPU Usage",
                    value = "${(systemHealth.cpuUsage * 100).toInt()}%",
                    subtitle = if (systemHealth.cpuUsage > 0.8f) "High load" else "Normal load",
                    color = if (systemHealth.cpuUsage > 0.8f) warningGlow else accentGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Memory",
                    value = "${(systemHealth.memoryUsage * 100).toInt()}%",
                    subtitle = "Used",
                    color = if (systemHealth.memoryUsage > 0.8f) warningGlow else accentGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Network",
                    value = "${systemHealth.networkLatency}ms",
                    subtitle = "Latency",
                    color = if (systemHealth.networkLatency > 100) warningGlow else accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RealMetricCard(
                    title = "Connections",
                    value = "${systemHealth.activeConnections}",
                    subtitle = "Active",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Error Rate",
                    value = "${"%.1f".format(systemHealth.errorRate)}/min",
                    subtitle = "Errors",
                    color = if (systemHealth.errorRate > 1f) errorGlow else accentGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Uptime",
                    value = formatUptime(systemHealth.uptime),
                    subtitle = "Running",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RealPerformanceMetricsCard(
    performanceMetrics: PerformanceMetrics,
    surfaceGlass: Color,
    primaryGlow: Color,
    accentGlow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Real Performance Metrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RealMetricCard(
                    title = "Throughput",
                    value = "${performanceMetrics.throughput} ops/sec",
                    subtitle = "Operations",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Response Time",
                    value = "${performanceMetrics.responseTime}ms",
                    subtitle = "Average",
                    color = if (performanceMetrics.responseTime > 500) Color(0xFFF59E0B) else accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RealMetricCard(
                    title = "Active Tasks",
                    value = "${performanceMetrics.concurrentTasks}",
                    subtitle = "Running",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Completed",
                    value = "${performanceMetrics.completedTasks}",
                    subtitle = "Total tasks",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Cache Hit",
                    value = "${(performanceMetrics.cacheHitRate * 100).toInt()}%",
                    subtitle = "Efficiency",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
@Composable
private fun RealSecurityStatusCard(
    securityStatus: SecurityStatus,
    surfaceGlass: Color,
    primaryGlow: Color,
    accentGlow: Color,
    errorGlow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Real Security Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val threatColor = when (securityStatus.threatLevel) {
                    ThreatLevel.LOW -> accentGlow
                    ThreatLevel.MEDIUM -> Color(0xFFF59E0B)
                    ThreatLevel.HIGH -> errorGlow
                    ThreatLevel.CRITICAL -> errorGlow
                }
                
                RealMetricCard(
                    title = "Threat Level",
                    value = securityStatus.threatLevel.name,
                    subtitle = if (securityStatus.activeThreats > 0) "${securityStatus.activeThreats} active" else "Secure",
                    color = threatColor,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Blocked Attempts",
                    value = "${securityStatus.blockedAttempts}",
                    subtitle = "Total blocked",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val encryptionColor = when (securityStatus.encryptionStatus) {
                    EncryptionStatus.ACTIVE -> accentGlow
                    EncryptionStatus.DEGRADED -> Color(0xFFF59E0B)
                    EncryptionStatus.INACTIVE -> errorGlow
                    else -> accentGlow
                }
                
                RealMetricCard(
                    title = "Encryption",
                    value = securityStatus.encryptionStatus.name,
                    subtitle = "AES-256-GCM",
                    color = encryptionColor,
                    modifier = Modifier.weight(1f)
                )
                
                val certColor = when (securityStatus.certificateStatus) {
                    CertificateStatus.VALID -> accentGlow
                    CertificateStatus.EXPIRING_SOON -> Color(0xFFF59E0B)
                    CertificateStatus.EXPIRED -> errorGlow
                    CertificateStatus.INVALID -> errorGlow
                    else -> accentGlow
                }
                
                RealMetricCard(
                    title = "Certificates",
                    value = securityStatus.certificateStatus.name,
                    subtitle = "Ed25519",
                    color = certColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RealNetworkStatusCard(
    networkStatus: NetworkStatus,
    connectionStatus: ConnectionStatus,
    surfaceGlass: Color,
    primaryGlow: Color,
    accentGlow: Color,
    warningGlow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Real Network Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val connectionColor = when (networkStatus.connectionState) {
                    NetworkConnectionState.CONNECTED -> accentGlow
                    NetworkConnectionState.CONNECTING -> warningGlow
                    NetworkConnectionState.DISCONNECTED -> Color(0xFFEF4444)
                    NetworkConnectionState.ERROR -> Color(0xFFEF4444)
                    else -> Color(0xFFEF4444)
                }
                
                RealMetricCard(
                    title = "Connection",
                    value = networkStatus.connectionState.name,
                    subtitle = connectionStatus.name,
                    color = connectionColor,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Bandwidth",
                    value = formatBandwidth(networkStatus.bandwidth),
                    subtitle = "Current",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RealMetricCard(
                    title = "Connected Devices",
                    value = "${networkStatus.connectedDevices}",
                    subtitle = "Active",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Data Transferred",
                    value = formatDataSize(networkStatus.dataTransferred),
                    subtitle = "Total",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
                RealMetricCard(
                    title = "Packet Loss",
                    value = "${(networkStatus.packetLoss * 100).toInt()}%",
                    subtitle = "Loss rate",
                    color = if (networkStatus.packetLoss > 0.05f) warningGlow else accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
@Composable
private fun RealNetworkDevicesCard(
    networkCommunicator: NetworkCommunicator,
    surfaceGlass: Color,
    primaryGlow: Color,
    accentGlow: Color,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var discoveredDevices by remember { mutableStateOf<List<NetworkDevice>>(emptyList()) }
    var isDiscovering by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // Get discovered devices
        networkCommunicator.getDiscoveredDevices().onSuccess { devices ->
            discoveredDevices = devices
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discovered Devices (${discoveredDevices.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryGlow
                )
                
                Button(
                    onClick = {
                        scope.launch {
                            isDiscovering = true
                            networkCommunicator.startDiscovery()
                            kotlinx.coroutines.delay(3000) // Discovery for 3 seconds
                            networkCommunicator.getDiscoveredDevices().onSuccess { devices ->
                                discoveredDevices = devices
                            }
                            isDiscovering = false
                        }
                    },
                    enabled = !isDiscovering,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryGlow.copy(alpha = 0.3f)
                    )
                ) {
                    if (isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Discover",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isDiscovering) "Discovering..." else "Discover")
                }
            }
            
            if (discoveredDevices.isEmpty()) {
                Text(
                    text = "No devices discovered yet. Click 'Discover' to scan for nearby devices.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredDevices) { device ->
                        RealDeviceCard(
                            device = device,
                            accentGlow = accentGlow,
                            primaryGlow = primaryGlow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealDeviceCard(
    device: NetworkDevice,
    accentGlow: Color,
    primaryGlow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x1A1E1E3F)),
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
                imageVector = Icons.Default.Devices,
                contentDescription = "Device",
                tint = primaryGlow,
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = device.ipAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            // Signal strength indicator
            val signalColor = when {
                device.signalStrength > 0.8f -> accentGlow
                device.signalStrength > 0.5f -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(8.dp + (index * 3).dp)
                            .background(
                                color = if (index < (device.signalStrength * 4).toInt()) 
                                    signalColor else Color.Gray,
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
            
            if (device.isSecure) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Secure",
                    tint = accentGlow,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
@Composable
private fun RealSystemReportsCard(
    systemMonitor: SystemMonitor,
    surfaceGlass: Color,
    primaryGlow: Color,
    accentGlow: Color,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var systemReport by remember { mutableStateOf<SystemReport?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Reports",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryGlow
                )
                
                Button(
                    onClick = {
                        scope.launch {
                            isGenerating = true
                            systemMonitor.generateSystemReport().onSuccess { report ->
                                systemReport = report
                            }
                            isGenerating = false
                        }
                    },
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryGlow.copy(alpha = 0.3f)
                    )
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Generate",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isGenerating) "Generating..." else "Generate Report")
                }
            }
            
            systemReport?.let { report ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Report generated at: ${formatTimestamp(report.generatedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    
                    if (report.recommendations.isNotEmpty()) {
                        Text(
                            text = "Recommendations:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accentGlow
                        )
                        
                        report.recommendations.forEach { recommendation ->
                            Text(
                                text = "• $recommendation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        Text(
                            text = "✅ System is running optimally with no recommendations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentGlow
                        )
                    }
                }
            } ?: run {
                Text(
                    text = "Click 'Generate Report' to create a comprehensive system analysis with real data and recommendations.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RealMetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1A1E1E3F)),
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

// Utility functions
private fun formatUptime(uptimeMs: Long): String {
    val seconds = uptimeMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

private fun formatBandwidth(bandwidth: Long): String {
    return when {
        bandwidth >= 1_000_000_000 -> "${bandwidth / 1_000_000_000} GB/s"
        bandwidth >= 1_000_000 -> "${bandwidth / 1_000_000} MB/s"
        bandwidth >= 1_000 -> "${bandwidth / 1_000} KB/s"
        else -> "$bandwidth B/s"
    }
}

private fun formatDataSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB"
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    // Simple timestamp formatting - in real app would use proper date formatting
    return "System time: $timestamp"
}