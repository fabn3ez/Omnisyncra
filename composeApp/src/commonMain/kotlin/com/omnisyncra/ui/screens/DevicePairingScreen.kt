package com.omnisyncra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnisyncra.core.security.*
import kotlinx.coroutines.delay

/**
 * Device pairing interface for establishing trust between devices
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePairingScreen(
    securitySystem: SecuritySystem? = null,
    onPairingComplete: (String) -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pairingMethod by remember { mutableStateOf(PairingMethod.QR_CODE) }
    var pairingState by remember { mutableStateOf(PairingState.SELECTING_METHOD) }
    var generatedCode by remember { mutableStateOf("") }
    var enteredCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Device Pairing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel"
                )
            }
        }
        
        when (pairingState) {
            PairingState.SELECTING_METHOD -> {
                PairingMethodSelection(
                    selectedMethod = pairingMethod,
                    onMethodSelected = { pairingMethod = it },
                    onContinue = { 
                        pairingState = PairingState.GENERATING_CODE
                        generatePairingCode { code ->
                            generatedCode = code
                            pairingState = when (pairingMethod) {
                                PairingMethod.QR_CODE -> PairingState.SHOWING_QR
                                PairingMethod.PIN -> PairingState.SHOWING_PIN
                                PairingMethod.MANUAL -> PairingState.ENTERING_CODE
                            }
                        }
                    }
                )
            }
            
            PairingState.GENERATING_CODE -> {
                GeneratingCodeView()
            }
            
            PairingState.SHOWING_QR -> {
                QRCodeView(
                    code = generatedCode,
                    onComplete = { deviceId -> onPairingComplete(deviceId) },
                    onBack = { pairingState = PairingState.SELECTING_METHOD }
                )
            }
            
            PairingState.SHOWING_PIN -> {
                PINView(
                    pin = generatedCode,
                    onComplete = { deviceId -> onPairingComplete(deviceId) },
                    onBack = { pairingState = PairingState.SELECTING_METHOD }
                )
            }
            
            PairingState.ENTERING_CODE -> {
                ManualCodeEntry(
                    enteredCode = enteredCode,
                    onCodeChanged = { enteredCode = it },
                    onSubmit = {
                        isLoading = true
                        errorMessage = null
                        // Simulate pairing process
                        performPairing(enteredCode) { success, deviceId, error ->
                            isLoading = false
                            if (success && deviceId != null) {
                                onPairingComplete(deviceId)
                            } else {
                                errorMessage = error ?: "Pairing failed"
                            }
                        }
                    },
                    onBack = { pairingState = PairingState.SELECTING_METHOD },
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
        }
    }
}

@Composable
private fun PairingMethodSelection(
    selectedMethod: PairingMethod,
    onMethodSelected: (PairingMethod) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Choose a pairing method to establish trust with another device:",
            style = MaterialTheme.typography.bodyLarge
        )
        
        PairingMethodCard(
            method = PairingMethod.QR_CODE,
            title = "QR Code",
            description = "Generate a QR code for the other device to scan",
            icon = Icons.Default.QrCode,
            isSelected = selectedMethod == PairingMethod.QR_CODE,
            onClick = { onMethodSelected(PairingMethod.QR_CODE) }
        )
        
        PairingMethodCard(
            method = PairingMethod.PIN,
            title = "PIN Code",
            description = "Generate a 6-digit PIN to enter on the other device",
            icon = Icons.Default.Pin,
            isSelected = selectedMethod == PairingMethod.PIN,
            onClick = { onMethodSelected(PairingMethod.PIN) }
        )
        
        PairingMethodCard(
            method = PairingMethod.MANUAL,
            title = "Manual Entry",
            description = "Enter a pairing code from another device",
            icon = Icons.Default.Keyboard,
            isSelected = selectedMethod == PairingMethod.MANUAL,
            onClick = { onMethodSelected(PairingMethod.MANUAL) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun PairingMethodCard(
    method: PairingMethod,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.foundation.BorderStroke(
                    2.dp, 
                    MaterialTheme.colorScheme.primary
                ).brush
            ) 
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun GeneratingCodeView() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Generating pairing code...",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QRCodeView(
    code: String,
    onComplete: (String) -> Unit,
    onBack: () -> Unit
) {
    var isWaitingForScan by remember { mutableStateOf(true) }
    
    // Simulate waiting for QR scan
    LaunchedEffect(code) {
        delay(5000) // Simulate scan after 5 seconds
        isWaitingForScan = false
        onComplete("scanned-device-id")
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "QR Code Pairing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Show this QR code to the other device:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        // QR Code placeholder (in production, use actual QR code generation)
        Card(
            modifier = Modifier.size(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "QR Code",
                        modifier = Modifier.size(120.dp),
                        tint = Color.Black
                    )
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black
                    )
                }
            }
        }
        
        if (isWaitingForScan) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text(
                    text = "Waiting for scan...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun PINView(
    pin: String,
    onComplete: (String) -> Unit,
    onBack: () -> Unit
) {
    var isWaitingForEntry by remember { mutableStateOf(true) }
    
    // Simulate waiting for PIN entry
    LaunchedEffect(pin) {
        delay(8000) // Simulate entry after 8 seconds
        isWaitingForEntry = false
        onComplete("pin-device-id")
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PIN Code Pairing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Enter this PIN on the other device:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = pin,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        if (isWaitingForEntry) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text(
                    text = "Waiting for PIN entry...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun ManualCodeEntry(
    enteredCode: String,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Manual Code Entry",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Enter the pairing code from the other device:",
            style = MaterialTheme.typography.bodyLarge
        )
        
        OutlinedTextField(
            value = enteredCode,
            onValueChange = onCodeChanged,
            label = { Text("Pairing Code") },
            placeholder = { Text("Enter 6-digit code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
        )
        
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = enteredCode.length == 6 && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Pair Device")
            }
        }
        
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

// Enums and data classes
enum class PairingMethod {
    QR_CODE, PIN, MANUAL
}

enum class PairingState {
    SELECTING_METHOD, GENERATING_CODE, SHOWING_QR, SHOWING_PIN, ENTERING_CODE
}

// Helper functions
private fun generatePairingCode(onGenerated: (String) -> Unit) {
    // Simulate code generation
    val code = (100000..999999).random().toString()
    onGenerated(code)
}

private fun performPairing(
    code: String,
    onResult: (success: Boolean, deviceId: String?, error: String?) -> Unit
) {
    // Simulate pairing process
    if (code == "123456") {
        onResult(true, "paired-device-id", null)
    } else {
        onResult(false, null, "Invalid pairing code")
    }
}