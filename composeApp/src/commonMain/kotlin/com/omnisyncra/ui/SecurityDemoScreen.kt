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
import com.omnisyncra.core.security.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityDemoScreen() {
    val authService: AuthenticationService = koinInject()
    val encryptionService: EncryptionService = koinInject()
    val auditService: AuditLogService = koinInject()
    val permissionManager: PermissionManager = koinInject()
    val secureMessaging: SecureMessagingService = koinInject()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "🔐 Security Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Authentication") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Encryption") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Audit Logs") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Permissions") }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (selectedTab) {
            0 -> AuthenticationTab(authService, scope)
            1 -> EncryptionTab(encryptionService, scope)
            2 -> AuditTab(auditService, scope)
            3 -> PermissionsTab(permissionManager, scope)
        }
    }
}

@Composable
private fun AuthenticationTab(
    authService: AuthenticationService,
    scope: CoroutineScope
) {
    var certificate by remember { mutableStateOf<DeviceCertificate?>(null) }
    var trustedDevices by remember { mutableStateOf<List<DeviceIdentity>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        certificate = authService.getLocalCertificate()
        trustedDevices = authService.getAllTrustedDevices()
    }
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Local Certificate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    certificate?.let { cert ->
                        Text("Device ID: ${cert.deviceId}")
                        Text("Valid From: ${cert.validFrom}")
                        Text("Valid Until: ${cert.validUntil}")
                        Text("Is Valid: ${cert.isValid()}")
                    } ?: Text("No certificate available")
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
                            text = "Trusted Devices (${trustedDevices.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    authService.initialize()
                                    trustedDevices = authService.getAllTrustedDevices()
                                }
                            }
                        ) {
                            Text("Refresh")
                        }
                    }
                }
            }
        }
        
        items(trustedDevices) { device ->
            Card {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Device: ${device.deviceId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text("Trust Level: ${device.trustLevel}")
                    device.trustedAt?.let { 
                        Text("Trusted At: $it")
                    }
                }
            }
        }
    }
}

@Composable
private fun EncryptionTab(
    encryptionService: EncryptionService,
    scope: CoroutineScope
) {
    var testText by remember { mutableStateOf("Hello, Omnisyncra Security!") }
    var encryptedData by remember { mutableStateOf<EncryptedData?>(null) }
    var decryptedText by remember { mutableStateOf<String?>(null) }
    var keyIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedKeyId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        keyIds = encryptionService.getAllKeyIds()
    }
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Encryption Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        label = { Text("Text to encrypt") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val key = encryptionService.generateKey()
                                    selectedKeyId = key.id
                                    keyIds = encryptionService.getAllKeyIds()
                                }
                            }
                        ) {
                            Text("Generate Key")
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    selectedKeyId?.let { keyId ->
                                        encryptedData = encryptionService.encryptString(testText, keyId)
                                    }
                                }
                            },
                            enabled = selectedKeyId != null
                        ) {
                            Text("Encrypt")
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    encryptedData?.let { data ->
                                        decryptedText = encryptionService.decryptString(data)
                                    }
                                }
                            },
                            enabled = encryptedData != null
                        ) {
                            Text("Decrypt")
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
                    Text(
                        text = "Encryption Keys (${keyIds.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    keyIds.forEach { keyId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = keyId.take(16) + "...",
                                style = MaterialTheme.typography.bodySmall
                            )
                            
                            Row {
                                if (selectedKeyId == keyId) {
                                    Text(
                                        text = "SELECTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    TextButton(
                                        onClick = { selectedKeyId = keyId }
                                    ) {
                                        Text("Select")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        encryptedData?.let { data ->
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Encrypted Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Algorithm: ${data.algorithm}")
                        Text("Key ID: ${data.keyId.take(16)}...")
                        Text("Ciphertext: ${data.ciphertext.size} bytes")
                        Text("Nonce: ${data.nonce.size} bytes")
                    }
                }
            }
        }
        
        decryptedText?.let { text ->
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Decrypted Text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditTab(
    auditService: AuditLogService,
    scope: CoroutineScope
) {
    var auditEvents by remember { mutableStateOf<List<AuditLogEntry>>(emptyList()) }
    var auditStats by remember { mutableStateOf<AuditStats?>(null) }
    
    LaunchedEffect(Unit) {
        auditEvents = auditService.getRecentEvents(20)
        auditStats = auditService.getEventStats()
    }
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                            text = "Audit Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    auditEvents = auditService.getRecentEvents(20)
                                    auditStats = auditService.getEventStats()
                                }
                            }
                        ) {
                            Text("Refresh")
                        }
                    }
                    
                    auditStats?.let { stats ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Events: ${stats.totalEvents}")
                        Text("Security Violations: ${stats.securityViolations}")
                        Text("Auth Failures: ${stats.authenticationFailures}")
                    }
                }
            }
        }
        
        item {
            Text(
                text = "Recent Events (${auditEvents.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(auditEvents) { event ->
            Card {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = event.eventType.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = event.severity.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (event.severity) {
                                AuditSeverity.INFO -> MaterialTheme.colorScheme.primary
                                AuditSeverity.WARNING -> MaterialTheme.colorScheme.secondary
                                AuditSeverity.ERROR -> MaterialTheme.colorScheme.error
                                AuditSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    Text(
                        text = event.message,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Source: ${event.sourceDevice.take(8)}...",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsTab(
    permissionManager: PermissionManager,
    scope: CoroutineScope
) {
    var permissionStats by remember { mutableStateOf<PermissionStats?>(null) }
    var pendingRequests by remember { mutableStateOf<List<PermissionRequest>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        permissionStats = permissionManager.getPermissionStats()
        pendingRequests = permissionManager.getPendingRequests()
    }
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                            text = "Permission Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    permissionStats = permissionManager.getPermissionStats()
                                    pendingRequests = permissionManager.getPendingRequests()
                                }
                            }
                        ) {
                            Text("Refresh")
                        }
                    }
                    
                    permissionStats?.let { stats ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Grants: ${stats.totalGrants}")
                        Text("Active Grants: ${stats.activeGrants}")
                        Text("Pending Requests: ${stats.pendingRequests}")
                    }
                }
            }
        }
        
        item {
            Text(
                text = "Pending Requests (${pendingRequests.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(pendingRequests) { request ->
            Card {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Permission: ${request.permission.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text("Device: ${request.requestingDevice}")
                    Text("Justification: ${request.justification}")
                    Text("Status: ${request.status}")
                    
                    if (request.status == RequestStatus.PENDING) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        permissionManager.approvePermissionRequest(
                                            request.id,
                                            "Approved via UI"
                                        )
                                        pendingRequests = permissionManager.getPendingRequests()
                                    }
                                }
                            ) {
                                Text("Approve")
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        permissionManager.denyPermissionRequest(
                                            request.id,
                                            "Denied via UI"
                                        )
                                        pendingRequests = permissionManager.getPendingRequests()
                                    }
                                }
                            ) {
                                Text("Deny")
                            }
                        }
                    }
                }
            }
        }
    }
}