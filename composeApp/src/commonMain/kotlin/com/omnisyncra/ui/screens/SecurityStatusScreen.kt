package com.omnisyncra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.core.security.*

/**
 * Security status display screen showing system security state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityStatusScreen(
    securitySystem: SecuritySystem? = null,
    modifier: Modifier = Modifier
) {
    var securityStatus by remember { mutableStateOf<SecurityStatus?>(null) }
    var trustedDevices by remember { mutableStateOf<List<TrustedDevice>>(emptyList()) }
    var activeConnections by remember { mutableStateOf<List<ActiveConnection>>(emptyList()) }
    var recentEvents by remember { mutableStateOf<List<SecurityEvent>>(emptyList()) }
    
    // Load security data
    LaunchedEffect(securitySystem) {
        securitySystem?.let { system ->
            securityStatus = system.getSecurityStatus()
            trustedDevices = loadTrustedDevices(system)
            activeConnections = loadActiveConnections(system)
            recentEvents = SecurityEventLogger.getRecentEvents(10)
        }
    }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SecurityOverviewCard(securityStatus)
        }
        
        item {
            ActiveConnectionsCard(activeConnections)
        }
        
        item {
            TrustedDevicesCard(trustedDevices)
        }
        
        item {
            RecentEventsCard(recentEvents)
        }
    }
}

@Composable
private fun SecurityOverviewCard(
    securityStatus: SecurityStatus?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Status",
                    tint = if (securityStatus?.isInitialized == true) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Security System Status",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            SecurityStatusItem(
                label = "System Status",
                value = if (securityStatus?.isInitialized == true) "Active" else "Inactive",
                icon = if (securityStatus?.isInitialized == true) Icons.Default.CheckCircle else Icons.Default.Error,
                color = if (securityStatus?.isInitialized == true) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
            
            SecurityStatusItem(
                label = "Active Channels",
                value = "${securityStatus?.activeChannels ?: 0}",
                icon = Icons.Default.Link
            )
            
            SecurityStatusItem(
                label = "Trusted Devices",
                value = "${securityStatus?.trustedDevices ?: 0}",
                icon = Icons.Default.Devices
            )
            
            securityStatus?.lastKeyRotation?.let { lastRotation ->
                val timeSince = System.currentTimeMillis() - lastRotation
                val hoursSince = timeSince / (1000 * 60 * 60)
                SecurityStatusItem(
                    label = "Last Key Rotation",
                    value = "${hoursSince}h ago",
                    icon = Icons.Default.Key
                )
            }
        }
    }
}

@Composable
private fun ActiveConnectionsCard(
    connections: List<ActiveConnection>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = "Active Connections"
                )
                Text(
                    text = "Active Connections (${connections.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (connections.isEmpty()) {
                Text(
                    text = "No active connections",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                connections.forEach { connection ->
                    ConnectionItem(connection)
                }
            }
        }
    }
}

@Composable
private fun TrustedDevicesCard(
    devices: List<TrustedDevice>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Trusted Devices"
                )
                Text(
                    text = "Trusted Devices (${devices.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (devices.isEmpty()) {
                Text(
                    text = "No trusted devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { device ->
                    TrustedDeviceItem(device)
                }
            }
        }
    }
}

@Composable
private fun RecentEventsCard(
    events: List<SecurityEvent>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Recent Events"
                )
                Text(
                    text = "Recent Security Events",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (events.isEmpty()) {
                Text(
                    text = "No recent events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                events.forEach { event ->
                    SecurityEventItem(event)
                }
            }
        }
    }
}

@Composable
private fun SecurityStatusItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun ConnectionItem(
    connection: ActiveConnection
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = connection.deviceName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Connected ${formatDuration(connection.connectedSince)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = "Connection Status",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(8.dp)
            )
            Text(
                text = connection.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TrustedDeviceItem(
    device: TrustedDevice
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Trust level: ${device.trustLevel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = when (device.trustLevel) {
                TrustLevel.TRUSTED -> Icons.Default.Verified
                TrustLevel.PENDING -> Icons.Default.Schedule
                TrustLevel.REVOKED -> Icons.Default.Block
                else -> Icons.Default.Help
            },
            contentDescription = "Trust Level",
            tint = when (device.trustLevel) {
                TrustLevel.TRUSTED -> MaterialTheme.colorScheme.primary
                TrustLevel.PENDING -> MaterialTheme.colorScheme.tertiary
                TrustLevel.REVOKED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SecurityEventItem(
    event: SecurityEvent
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = getEventIcon(event.type),
            contentDescription = "Event Type",
            tint = getEventColor(event.type),
            modifier = Modifier.size(16.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = event.details,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatTimestamp(event.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper functions
private fun getEventIcon(type: SecurityEventType): ImageVector {
    return when (type) {
        SecurityEventType.AUTH_SUCCESS -> Icons.Default.CheckCircle
        SecurityEventType.AUTH_FAILURE -> Icons.Default.Error
        SecurityEventType.TRUST_ESTABLISHED -> Icons.Default.Handshake
        SecurityEventType.TRUST_REVOKED -> Icons.Default.Block
        SecurityEventType.KEY_EXCHANGE_SUCCESS -> Icons.Default.Key
        SecurityEventType.KEY_EXCHANGE_FAILURE -> Icons.Default.KeyOff
        SecurityEventType.ENCRYPTION_ERROR -> Icons.Default.Lock
        SecurityEventType.SECURITY_VIOLATION -> Icons.Default.Warning
        else -> Icons.Default.Info
    }
}

@Composable
private fun getEventColor(type: SecurityEventType): Color {
    return when (type) {
        SecurityEventType.AUTH_SUCCESS,
        SecurityEventType.TRUST_ESTABLISHED,
        SecurityEventType.KEY_EXCHANGE_SUCCESS -> MaterialTheme.colorScheme.primary
        
        SecurityEventType.AUTH_FAILURE,
        SecurityEventType.TRUST_REVOKED,
        SecurityEventType.KEY_EXCHANGE_FAILURE,
        SecurityEventType.ENCRYPTION_ERROR,
        SecurityEventType.SECURITY_VIOLATION -> MaterialTheme.colorScheme.error
        
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun formatDuration(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    // Simple timestamp formatting - in production use proper date formatting
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}

// Data classes for UI
data class ActiveConnection(
    val deviceId: String,
    val deviceName: String,
    val status: String,
    val connectedSince: Long
)

data class TrustedDevice(
    val deviceId: String,
    val name: String,
    val trustLevel: TrustLevel,
    val addedAt: Long
)

// Helper functions to load data
private suspend fun loadTrustedDevices(securitySystem: SecuritySystem): List<TrustedDevice> {
    // In a real implementation, this would query the trust store
    return listOf(
        TrustedDevice("device1", "My Phone", TrustLevel.TRUSTED, System.currentTimeMillis() - 86400000),
        TrustedDevice("device2", "My Laptop", TrustLevel.TRUSTED, System.currentTimeMillis() - 172800000),
        TrustedDevice("device3", "Unknown Device", TrustLevel.PENDING, System.currentTimeMillis() - 3600000)
    )
}

private suspend fun loadActiveConnections(securitySystem: SecuritySystem): List<ActiveConnection> {
    // In a real implementation, this would query active secure channels
    return listOf(
        ActiveConnection("device1", "My Phone", "Connected", System.currentTimeMillis() - 3600000),
        ActiveConnection("device2", "My Laptop", "Connected", System.currentTimeMillis() - 7200000)
    )
}