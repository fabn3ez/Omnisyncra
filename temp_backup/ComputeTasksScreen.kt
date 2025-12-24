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
import com.omnisyncra.core.compute.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ComputeTasksScreen() {
    val computeScheduler: ComputeScheduler = koinInject()
    val pendingTasks by computeScheduler.pendingTasks.collectAsState(initial = emptyList())
    val runningTasks by computeScheduler.runningTasks.collectAsState(initial = emptyList())
    val completedTasks by computeScheduler.completedTasks.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "⚡ Compute Tasks",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${pendingTasks.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Pending")
                }
            }
            
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${runningTasks.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text("Running")
                }
            }
            
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${completedTasks.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text("Completed")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        val demoTask = ComputeTask(
                            type = TaskType.AI_INFERENCE,
                            priority = TaskPriority.NORMAL,
                            payload = TaskPayload(
                                data = mapOf("model" to "demo", "input" to "test"),
                                inputFormat = "json",
                                expectedOutputFormat = "json"
                            ),
                            requirements = ComputeRequirements(
                                minComputePower = com.omnisyncra.core.domain.ComputePower.MEDIUM,
                                estimatedMemoryMB = 512,
                                estimatedDurationMs = 2000L
                            ),
                            metadata = TaskMetadata(
                                originDeviceId = com.benasher44.uuid.uuid4(),
                                tags = listOf("demo", "ai")
                            )
                        )
                        computeScheduler.submitTask(demoTask)
                    }
                }
            ) {
                Text("AI Task")
            }
            
            Button(
                onClick = {
                    scope.launch {
                        val encryptionTask = ComputeTask(
                            type = TaskType.ENCRYPTION,
                            priority = TaskPriority.HIGH,
                            payload = TaskPayload(
                                data = mapOf("data" to "sensitive_data", "algorithm" to "AES-256"),
                                inputFormat = "json",
                                expectedOutputFormat = "binary"
                            ),
                            requirements = ComputeRequirements(
                                minComputePower = com.omnisyncra.core.domain.ComputePower.LOW,
                                estimatedMemoryMB = 128,
                                estimatedDurationMs = 500L
                            ),
                            metadata = TaskMetadata(
                                originDeviceId = com.benasher44.uuid.uuid4(),
                                tags = listOf("demo", "encryption")
                            )
                        )
                        computeScheduler.submitTask(encryptionTask)
                    }
                }
            ) {
                Text("Encryption")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (runningTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Running Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(runningTasks) { execution ->
                    TaskExecutionCard(execution)
                }
            }
            
            if (pendingTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(pendingTasks) { task ->
                    TaskCard(task)
                }
            }
            
            if (completedTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Completed Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(completedTasks.take(5)) { result ->
                    TaskResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: ComputeTask) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = task.type.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Priority: ${task.priority.name}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Memory: ${task.requirements.estimatedMemoryMB} MB",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TaskExecutionCard(execution: TaskExecution) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = execution.task.type.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Running on: ${execution.assignedNode.device.name}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Started: ${execution.startedAt}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TaskResultCard(result: TaskResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = result.originalTask.type.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Status: ${if (result.isSuccess) "Success" else "Failed"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (result.isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            )
            Text(
                text = "Duration: ${result.executionTimeMs} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}