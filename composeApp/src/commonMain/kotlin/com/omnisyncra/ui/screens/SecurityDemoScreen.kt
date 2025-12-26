package com.omnisyncra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.security.*
import kotlinx.coroutines.launch

/**
 * Demo screen showcasing Phase 13 Security & Encryption features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityDemoScreen() {
    val scope = rememberCoroutineScope()
    val nodeId = remember { uuid4() }
    val securitySystem = remember { OmnisyncraSecuritySystem(nodeId) }
    
    var isInitialized by remember { mutableStateOf(false) }
    var securityStatus by remember { mutableStateOf<SecurityStatus?>(null) }
    var securityEvents by remember { mutableStateOf<List<SecurityEvent>>(emptyList()) }
    var localCertificate by remember { mutableStateOf<DeviceCertificate?>(null) }
    var activeSessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    
    // Initialize security system on first composition
    LaunchedEffect(Unit) {
        scope.launch {
            isInitialized = securitySystem.initialize()
            if (isInitialized) {
                securityStatus = securitySystem.getSecurityStatus()
                securityEvents = securitySystem.getSecurityEvents()
                localCertificate = securitySystem.getCertificateManager().let { manager ->
                    (manager as? OmnisyncraeCertificateManager)?.getLocalCertificate()
                }
                activeSessions = securitySystem.getKeyExchangeManager().getAllActiveSessions()
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isInitialized) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🔐 Phase 13: Security & Encryption",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isInitialized) 
                        "✅ Security System Initialized" 
                    else 
                        "❌ Security System Not Initialized",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Node ID: ${nodeId.toString().take(8)}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Security Status
        securityStatus?.let { status ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Security Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusItem("Active Channels", status.activeChannels.toString())
                        StatusItem("Trusted Devices", status.trustedDevices.toString())
                        StatusItem("Pending Auth", status.pendingAuthentications.toString())
                    }
                }
            }
        }
        
        // Local Certificate Info
        localCertificate?.let { cert ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Local Device Certificate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    CertificateInfo(cert)
                }
            }
        }
        
        // Action Buttons
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Security Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val targetDevice = uuid4()
                                securitySystem.createSecureChannel(targetDevice)
                                securityStatus = securitySystem.getSecurityStatus()
                                securityEvents = securitySystem.getSecurityEvents()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isInitialized
                    ) {
                        Text("Create Channel")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                val targetDevice = uuid4()
                                securitySystem.establishTrust(targetDevice, TrustMethod.QR_CODE)
                                securityEvents = securitySystem.getSecurityEvents()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isInitialized
                    ) {
                        Text("Establish Trust")
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                activeSessions = securitySystem.getKeyExchangeManager().getAllActiveSessions()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isInitialized
                    ) {
                        Text("Refresh Sessions")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                securityEvents = securitySystem.getSecurityEvents()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isInitialized
                    ) {
                        Text("Refresh Events")
                    }
                }
            }
        }
        
        // Active Sessions
        if (activeSessions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Active Sessions (${activeSessions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(activeSessions) { session ->
                            SessionItem(session)
                        }
                    }
                }
            }
        }
        
        // Security Events
        if (securityEvents.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Security Events (${securityEvents.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(securityEvents.takeLast(10).reversed()) { event ->
                            SecurityEventItem(event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CertificateInfo(certificate: DeviceCertificate) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InfoRow("Serial Number", certificate.serialNumber.take(16) + "...")
        InfoRow("Issuer", certificate.issuer)
        InfoRow("Valid From", formatTimestamp(certificate.validFrom))
        InfoRow("Valid Until", formatTimestamp(certificate.validUntil))
        InfoRow("Key Usage", certificate.keyUsage.joinToString(", ") { it.name })
        
        val isExpiringSoon = certificate.isExpiringSoon()
        if (isExpiringSoon) {
            Text(
                text = "⚠️ Certificate expires soon!",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SessionItem(session: SessionInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Device: ${session.deviceId.toString().take(8)}...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Session: ${session.sessionId.take(8)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Established: ${formatTimestamp(session.establishedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.needsRotation) {
                Text(
                    text = "🔄 Needs Key Rotation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SecurityEventItem(event: SecurityEvent) {
    val severityColor = when (event.severity) {
        SecuritySeverity.INFO -> MaterialTheme.colorScheme.primary
        SecuritySeverity.WARNING -> Color(0xFFFF9800)
        SecuritySeverity.ERROR -> MaterialTheme.colorScheme.error
        SecuritySeverity.CRITICAL -> Color(0xFFD32F2F)
    }
    
    val severityIcon = when (event.severity) {
        SecuritySeverity.INFO -> "ℹ️"
        SecuritySeverity.WARNING -> "⚠️"
        SecuritySeverity.ERROR -> "❌"
        SecuritySeverity.CRITICAL -> "🚨"
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = severityIcon,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.type.name.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = severityColor
                )
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimestamp(event.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    // Simple timestamp formatting
    val date = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
    return date.toString().take(19).replace("T", " ")
}