package com.omnisyncra.core.compute

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.Device
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.resilience.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.math.min

class OmnisyncraComputeScheduler(
    private val localPlatform: Platform,
    private val taskExecutor: TaskExecutor,
    private val networkCommunicator: ComputeNetworkCommunicator
) : ComputeScheduler {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errorRecovery = GlobalErrorRecovery.manager
    private val circuitBreaker = errorRecovery.getCircuitBreaker("ComputeScheduler")
    private val retryPolicy = RetryPolicies.computeTask
    
    private val _pendingTasks = MutableStateFlow<List<ComputeTask>>(emptyList())
    override val pendingTasks: Flow<List<ComputeTask>> = _pendingTasks.asStateFlow()
    
    private val _runningTasks = MutableStateFlow<List<TaskExecution>>(emptyList())
    override val runningTasks: Flow<List<TaskExecution>> = _runningTasks.asStateFlow()
    
    private val _completedTasks = MutableStateFlow<List<TaskResult>>(emptyList())
    override val completedTasks: Flow<List<TaskResult>> = _completedTasks.asStateFlow()
    
    private val computeNodes = MutableStateFlow<Map<Uuid, ComputeNode>>(emptyMap())
    private val taskResults = MutableStateFlow<Map<Uuid, TaskResult>>(emptyMap())
    
    private var schedulingJob: Job? = null
    
    init {
        startScheduling()
    }
    
    override suspend fun submitTask(task: ComputeTask): Uuid {
        val currentPending = _pendingTasks.value.toMutableList()
        currentPending.add(task)
        
        // Sort by priority and deadline
        currentPending.sortWith(
            compareByDescending<ComputeTask> { it.priority.ordinal }
                .thenBy { it.deadline ?: Long.MAX_VALUE }
                .thenBy { it.createdAt }
        )
        
        _pendingTasks.value = currentPending
        return task.id
    }
    
    override suspend fun cancelTask(taskId: Uuid): Boolean {
        // Remove from pending tasks
        val currentPending = _pendingTasks.value.toMutableList()
        val removedFromPending = currentPending.removeAll { it.id == taskId }
        if (removedFromPending) {
            _pendingTasks.value = currentPending
            return true
        }
        
        // Cancel running task
        val currentRunning = _runningTasks.value.toMutableList()
        val runningTask = currentRunning.find { it.task.id == taskId }
        if (runningTask != null) {
            // Send cancellation to executor
            taskExecutor.cancelTask(taskId)
            
            // Add cancelled result
            val cancelledResult = TaskResult(
                taskId = taskId,
                status = TaskStatus.CANCELLED,
                executedBy = runningTask.assignedNode.device.id,
                executionTimeMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - runningTask.startedAt,
                completedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
            
            val currentCompleted = _completedTasks.value.toMutableList()
            currentCompleted.add(cancelledResult)
            _completedTasks.value = currentCompleted
            
            currentRunning.removeAll { it.task.id == taskId }
            _runningTasks.value = currentRunning
            
            return true
        }
        
        return false
    }
    
    override suspend fun getTaskStatus(taskId: Uuid): TaskStatus? {
        // Check pending
        if (_pendingTasks.value.any { it.id == taskId }) {
            return TaskStatus.PENDING
        }
        
        // Check running
        if (_runningTasks.value.any { it.task.id == taskId }) {
            return TaskStatus.RUNNING
        }
        
        // Check completed
        return taskResults.value[taskId]?.status
    }
    
    override suspend fun getTaskResult(taskId: Uuid): TaskResult? {
        return taskResults.value[taskId]
    }
    
    override suspend fun registerComputeNode(device: Device) {
        val capacity = estimateNodeCapacity(device)
        val performance = NodePerformance(
            averageTaskCompletionRate = 1.0,
            averageExecutionTimeMs = 1000L,
            successRate = 1.0,
            totalTasksCompleted = 0,
            lastPerformanceUpdate = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        val availability = NodeAvailability(
            status = NodeStatus.AVAILABLE
        )
        
        val computeNode = ComputeNode(
            device = device,
            capacity = capacity,
            performance = performance,
            availability = availability,
            lastSeen = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        val currentNodes = computeNodes.value.toMutableMap()
        currentNodes[device.id] = computeNode
        computeNodes.value = currentNodes
    }
    
    override suspend fun unregisterComputeNode(deviceId: Uuid) {
        val currentNodes = computeNodes.value.toMutableMap()
        currentNodes.remove(deviceId)
        computeNodes.value = currentNodes
    }
    
    override suspend fun updateNodeCapacity(deviceId: Uuid, availableCapacity: NodeCapacity) {
        val currentNodes = computeNodes.value.toMutableMap()
        val existingNode = currentNodes[deviceId]
        if (existingNode != null) {
            currentNodes[deviceId] = existingNode.copy(
                capacity = availableCapacity,
                lastSeen = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
            computeNodes.value = currentNodes
        }
    }
    
    override fun getAvailableNodes(): List<ComputeNode> {
        return computeNodes.value.values
            .filter { it.availability.status == NodeStatus.AVAILABLE }
            .sortedByDescending { it.capacity.computePower.ordinal }
    }
    
    override fun getOptimalNode(task: ComputeTask): ComputeNode? {
        return getAvailableNodes()
            .filter { it.canExecute(task) }
            .maxByOrNull { it.getExecutionScore(task) }
    }
    
    private fun startScheduling() {
        schedulingJob = scope.launch {
            while (isActive) {
                try {
                    scheduleNextTasks()
                    delay(1000) // Schedule every second
                } catch (e: Exception) {
                    // Log error and continue
                    delay(5000) // Wait longer on error
                }
            }
        }
    }
    
    private suspend fun scheduleNextTasks() {
        val pending = _pendingTasks.value
        val running = _runningTasks.value
        val availableNodes = getAvailableNodes()
        
        if (pending.isEmpty() || availableNodes.isEmpty()) return
        
        val tasksToSchedule = mutableListOf<Pair<ComputeTask, ComputeNode>>()
        val remainingNodes = availableNodes.toMutableList()
        
        for (task in pending) {
            if (remainingNodes.isEmpty()) break
            
            val optimalNode = remainingNodes
                .filter { it.canExecute(task) }
                .maxByOrNull { it.getExecutionScore(task) }
            
            if (optimalNode != null) {
                tasksToSchedule.add(task to optimalNode)
                remainingNodes.remove(optimalNode)
            }
        }
        
        // Execute scheduled tasks
        for ((task, node) in tasksToSchedule) {
            executeTask(task, node)
        }
    }
    
    private suspend fun executeTask(task: ComputeTask, node: ComputeNode) {
        // Remove from pending
        val currentPending = _pendingTasks.value.toMutableList()
        currentPending.removeAll { it.id == task.id }
        _pendingTasks.value = currentPending
        
        // Add to running
        val execution = TaskExecution(
            task = task,
            assignedNode = node,
            startedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            estimatedCompletionAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + task.requirements.estimatedDurationMs
        )
        
        val currentRunning = _runningTasks.value.toMutableList()
        currentRunning.add(execution)
        _runningTasks.value = currentRunning
        
        // Execute task
        scope.launch {
            try {
                val result = if (node.device.id.toString() == localPlatform.getDeviceName()) {
                    // Execute locally
                    taskExecutor.executeTask(task)
                } else {
                    // Execute remotely
                    networkCommunicator.executeRemoteTask(task, node.device)
                }
                
                handleTaskCompletion(task.id, result)
            } catch (e: Exception) {
                val errorResult = TaskResult(
                    taskId = task.id,
                    status = TaskStatus.FAILED,
                    error = TaskError(
                        code = "EXECUTION_ERROR",
                        message = e.message ?: "Unknown error",
                        isRetryable = true
                    ),
                    executedBy = node.device.id,
                    executionTimeMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - execution.startedAt,
                    completedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                )
                
                handleTaskCompletion(task.id, errorResult)
            }
        }
    }
    
    private suspend fun handleTaskCompletion(taskId: Uuid, result: TaskResult) {
        // Remove from running
        val currentRunning = _runningTasks.value.toMutableList()
        currentRunning.removeAll { it.task.id == taskId }
        _runningTasks.value = currentRunning
        
        // Add to completed
        val currentCompleted = _completedTasks.value.toMutableList()
        currentCompleted.add(result)
        _completedTasks.value = currentCompleted
        
        // Store result
        val currentResults = taskResults.value.toMutableMap()
        currentResults[taskId] = result
        taskResults.value = currentResults
        
        // Handle retries for failed tasks
        if (result.status == TaskStatus.FAILED && result.error?.isRetryable == true) {
            val originalTask = _pendingTasks.value.find { it.id == taskId }
                ?: currentCompleted.find { it.taskId == taskId }?.let { 
                    // Reconstruct task for retry (simplified)
                    null // Would need to store original tasks
                }
            
            // Retry logic would go here
        }
    }
    
    private fun estimateNodeCapacity(device: Device): NodeCapacity {
        return NodeCapacity(
            computePower = device.capabilities.computePower,
            totalMemoryMB = when (device.capabilities.computePower) {
                com.omnisyncra.core.domain.ComputePower.LOW -> 2048
                com.omnisyncra.core.domain.ComputePower.MEDIUM -> 4096
                com.omnisyncra.core.domain.ComputePower.HIGH -> 8192
                com.omnisyncra.core.domain.ComputePower.EXTREME -> 16384
            },
            availableMemoryMB = when (device.capabilities.computePower) {
                com.omnisyncra.core.domain.ComputePower.LOW -> 1024
                com.omnisyncra.core.domain.ComputePower.MEDIUM -> 2048
                com.omnisyncra.core.domain.ComputePower.HIGH -> 4096
                com.omnisyncra.core.domain.ComputePower.EXTREME -> 8192
            },
            totalSlots = device.capabilities.maxConcurrentTasks,
            availableSlots = device.capabilities.maxConcurrentTasks,
            currentLoad = 0.0,
            hasGPU = device.capabilities.computePower.ordinal >= com.omnisyncra.core.domain.ComputePower.HIGH.ordinal,
            hasNetwork = device.capabilities.hasWiFi
        )
    }
    
    fun cleanup() {
        schedulingJob?.cancel()
        scope.cancel()
    }
}