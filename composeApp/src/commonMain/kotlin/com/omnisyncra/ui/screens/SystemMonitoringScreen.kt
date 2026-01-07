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
import kotlinx.coroutines.launch

/**
 * Advanced System Monitoring Screen
 * Real-time system health and performance monitoring
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMonitoringScreen() {
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
        // Header
        SystemMonitoringHeader(glowAnimation, primaryGlow)
        
        // Content
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SystemHealthCard(surfaceGlass, primaryGlow, accentGlow, warningGlow, errorGlow)
            }
            
            item {
                PerformanceMetricsCard(surfaceGlass, primaryGlow, accentGlow)
            }
            
            item {
                SecurityStatusCard(surfaceGlass, primaryGlow, accentGlow, errorGlow)
            }
            
            item {
                NetworkStatusCard(surfaceGlass, primaryGlow, accentGlow, warningGlow)
            }
        }
    }
}

@Composable
private fun SystemMonitoringHeader(
    glowAnimation: Float,
    primaryGlow: Color
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
                    contentDescription = "System Monitoring",
                    tint = primaryGlow,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Monitoring",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Real-time System Health & Performance Analytics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            // Live indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            Color(0xFF10B981).copy(alpha = glowAnimation),
                            CircleShape
                        )
                )
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
        }
    }
}

@Composable
private fun SystemHealthCard(
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
                text = "System Health",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "CPU Usage",
                    value = "45%",
                    subtitle = "Normal load",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Memory",
                    value = "62%",
                    subtitle = "Available",
                    color = warningGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Network",
                    value = "18ms",
                    subtitle = "Latency",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PerformanceMetricsCard(
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
                text = "Performance Metrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Throughput",
                    value = "325 ops/sec",
                    subtitle = "Operations",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Response Time",
                    value = "89ms",
                    subtitle = "Average",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SecurityStatusCard(
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
                text = "Security Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Threat Level",
                    value = "LOW",
                    subtitle = "Secure",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Blocked Attempts",
                    value = "156",
                    subtitle = "Total blocked",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NetworkStatusCard(
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
                text = "Network Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryGlow
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Bandwidth",
                    value = "2.5 MB/s",
                    subtitle = "Current",
                    color = accentGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Connected Devices",
                    value = "4",
                    subtitle = "Active",
                    color = primaryGlow,
                    modifier = Modifier.weight(1f)
                )
            }
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