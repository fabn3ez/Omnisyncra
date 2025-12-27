package com.omnisyncra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.core.ai.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * AI Management Screen - Demonstrates AI system capabilities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIManagementScreen() {
    val aiSystem: AISystem = koinInject()
    val scope = rememberCoroutineScope()
    
    var systemStatus by remember { mutableStateOf<AISystemStatus?>(null) }
    var privacyReport by remember { mutableStateOf<PrivacyReport?>(null) }
    var testResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    
    // Collect system status
    LaunchedEffect(Unit) {
        aiSystem.getSystemStatus().collect { status ->
            systemStatus = status
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AI System Management",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        // System Status Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                systemStatus?.let { status ->
                    StatusRow("Initialized", if (status.isInitialized) "✅ Yes" else "❌ No")
                    StatusRow("API Connected", if (status.apiConnected) "✅ Connected" else "❌ Disconnected")
                    StatusRow("Models Loaded", status.modelsLoaded.joinToString(", ").ifEmpty { "None" })
                    StatusRow("Processing Queue", "${status.processingQueue} tasks")
                    StatusRow("Memory Usage", "${status.performanceMetrics.memoryUsage / 1024 / 1024} MB")
                    StatusRow("Avg Processing Time", "${status.performanceMetrics.averageProcessingTime}ms")
                }
            }
        }
        
        // Privacy Report Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy Report",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Button(
                        onClick = {
                            scope.launch {
                                privacyReport = aiSystem.getPrivacyReport()
                            }
                        }
                    ) {
                        Text("Refresh")
                    }
                }
                
                privacyReport?.let { report ->
                    StatusRow("Data Processed Locally", "${report.dataProcessedLocally} items")
                    StatusRow("Data Sent to API", "${report.dataSentToAPI} requests")
                    StatusRow("PII Detected", "${report.piiDetected} instances")
                    StatusRow("PII Sanitized", "${report.piiSanitized} instances")
                    StatusRow("Privacy Violations", "${report.privacyViolations.size} violations")
                    
                    if (report.confidenceScores.isNotEmpty()) {
                        val avgConfidence = report.confidenceScores.average()
                        StatusRow("Avg Confidence", "${(avgConfidence * 100).toInt()}%")
                    }
                }
            }
        }
        
        // AI Testing Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AI Testing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Test Input") },
                    placeholder = { Text("Enter text to analyze...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        testDataSanitization(aiSystem, inputText) { result ->
                                            testResults = testResults + result
                                        }
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank()
                    ) {
                        Text("Test Sanitization")
                    }
                    
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        testContextAnalysis(aiSystem, inputText) { result ->
                                            testResults = testResults + result
                                        }
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank()
                    ) {
                        Text("Test Analysis")
                    }
                    
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        testSummarization(aiSystem, inputText) { result ->
                                            testResults = testResults + result
                                        }
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank()
                    ) {
                        Text("Test Summary")
                    }
                }
                
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                Button(
                    onClick = { testResults = emptyList() },
                    enabled = testResults.isNotEmpty()
                ) {
                    Text("Clear Results")
                }
            }
        }
        
        // Test Results
        if (testResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Test Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(testResults) { result ->
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private suspend fun testDataSanitization(
    aiSystem: AISystem,
    input: String,
    onResult: (String) -> Unit
) {
    try {
        val sanitized = aiSystem.sanitizeData(input)
        onResult("🔒 Sanitization Result:")
        onResult("  Original: ${input.take(50)}${if (input.length > 50) "..." else ""}")
        onResult("  Sanitized: ${sanitized.sanitizedContent.take(50)}${if (sanitized.sanitizedContent.length > 50) "..." else ""}")
        onResult("  Confidence: ${(sanitized.confidenceScore * 100).toInt()}%")
        onResult("  Privacy Level: ${sanitized.privacyLevel}")
        onResult("  Items Removed: ${sanitized.sanitizationReport.itemsRemoved.size}")
        onResult("")
    } catch (e: Exception) {
        onResult("❌ Sanitization Error: ${e.message}")
        onResult("")
    }
}

private suspend fun testContextAnalysis(
    aiSystem: AISystem,
    input: String,
    onResult: (String) -> Unit
) {
    try {
        val userData = UserData(
            content = input,
            contentType = ContentType.TEXT,
            timestamp = System.currentTimeMillis(),
            source = "test"
        )
        
        val analysis = aiSystem.analyzeContext(userData)
        onResult("🧠 Context Analysis Result:")
        onResult("  Topics: ${analysis.mainTopics.joinToString(", ")}")
        onResult("  Entities: ${analysis.entities.size} found")
        onResult("  Relationships: ${analysis.relationships.size} found")
        onResult("  Importance: ${(analysis.importance * 100).toInt()}%")
        onResult("  Sentiment: ${analysis.sentiment.polarity} (${(analysis.sentiment.confidence * 100).toInt()}% confidence)")
        onResult("")
    } catch (e: Exception) {
        onResult("❌ Analysis Error: ${e.message}")
        onResult("")
    }
}

private suspend fun testSummarization(
    aiSystem: AISystem,
    input: String,
    onResult: (String) -> Unit
) {
    try {
        val summary = aiSystem.generateSummary(input)
        onResult("📝 Summary Result:")
        onResult("  Summary: ${summary.summary}")
        onResult("  Key Points: ${summary.keyPoints.size}")
        onResult("  Entities: ${summary.entities.size}")
        onResult("  Confidence: ${(summary.confidence * 100).toInt()}%")
        onResult("  Compression: ${summary.originalLength} → ${summary.summaryLength} chars")
        onResult("")
    } catch (e: Exception) {
        onResult("❌ Summary Error: ${e.message}")
        onResult("")
    }
}