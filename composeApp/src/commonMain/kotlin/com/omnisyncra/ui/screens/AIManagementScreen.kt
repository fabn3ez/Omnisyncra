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
import com.omnisyncra.core.ai.*
import kotlinx.coroutines.launch

/**
 * AI Management Screen showcasing AI capabilities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIManagementScreen() {
    var testInput by remember { mutableStateOf("") }
    var testOutput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Premium colors
    val primaryGlow = Color(0xFF6366F1)
    val accentGlow = Color(0xFF10B981)
    val surfaceGlass = Color(0x1A1E1E3F)
    
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceGlass),
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
                                    primaryGlow.copy(alpha = 0.3f),
                                    primaryGlow.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI System",
                        tint = primaryGlow,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "AI Management",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Privacy-First AI Integration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Test Interface
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
                    text = "AI Testing Interface",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryGlow
                )
                
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    label = { Text("Test Input", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("Enter text to test AI capabilities...", color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                testOutput = testDataSanitization(testInput)
                                isProcessing = false
                            }
                        },
                        enabled = testInput.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGlow.copy(alpha = 0.2f),
                            contentColor = accentGlow
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sanitize Data")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                testOutput = testContextAnalysis(testInput)
                                isProcessing = false
                            }
                        },
                        enabled = testInput.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryGlow.copy(alpha = 0.2f),
                            contentColor = primaryGlow
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Context")
                    }
                }
            }
        }
        
        // Processing Indicator
        if (isProcessing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = surfaceGlass),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = primaryGlow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Processing AI request...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        // Output
        if (testOutput.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = surfaceGlass),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "AI Output",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryGlow
                    )
                    
                    Text(
                        text = testOutput,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        // System Status
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
                    text = "System Status",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryGlow
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatusCard(
                        title = "Local Processing",
                        value = "100%",
                        subtitle = "Privacy preserved",
                        color = accentGlow,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "PII Detection",
                        value = "Active",
                        subtitle = "Real-time scanning",
                        color = primaryGlow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1A1E1E3F)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
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

// Test functions
private suspend fun testDataSanitization(input: String): String {
    return try {
        val sanitizer = DataSanitizer()
        val result = sanitizer.sanitize(input)
        
        buildString {
            appendLine("✅ Data Sanitization Results:")
            appendLine("Original: $input")
            appendLine("Sanitized: ${result.sanitizedContent}")
            appendLine("PII Detected: ${result.detectedPII.size} items")
            result.detectedPII.forEach { pii ->
                appendLine("  - ${pii.type}: ${pii.confidence * 100}% confidence")
            }
            appendLine("Overall Confidence: ${result.confidenceScore * 100}%")
        }
    } catch (e: Exception) {
        "❌ Error: ${e.message}"
    }
}

private suspend fun testContextAnalysis(input: String): String {
    return try {
        val analyzer = ContextAnalyzer()
        val context = AIContext(
            currentActivity = input,
            recentActivities = listOf("User input", "Testing system"),
            deviceContext = DeviceContext(
                deviceType = "test-device",
                capabilities = listOf("compute", "network", "ai")
            )
        )
        
        val result = analyzer.analyzeContext(context)
        
        buildString {
            appendLine("✅ Context Analysis Results:")
            appendLine("Processing Time: ${result.processingTimeMs}ms")
            appendLine("Confidence Score: ${result.confidenceScore * 100}%")
            appendLine("Relevant Topics:")
            result.relevantTopics.forEach { topic ->
                appendLine("  - $topic")
            }
            appendLine("Suggested Actions:")
            result.suggestedActions.forEach { action ->
                appendLine("  - $action")
            }
        }
    } catch (e: Exception) {
        "❌ Error: ${e.message}"
    }
}