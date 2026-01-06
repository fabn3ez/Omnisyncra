package com.omnisyncra.core.compute

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.*
import com.omnisyncra.core.platform.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Compute services for distributed task execution
 */

@kotlinx.serialization.Serializable
data class ExecutorCapabilities(
    val maxConcurrentTasks: Int,
    val supportedTaskTypes: List<TaskType>,
    val hasGPUAcceleration: Boolean,
    val maxMemoryMB: Int,
    val estimatedPerformanceMultiplier: Double
)

interface TaskExecutor {
    suspend fun executeTask(task: ComputeTask): ComputeTask
    fun getCapabilities(): ExecutorCapabilities
}

class LocalTaskExecutor(
    private val nodeId: Uuid,
    private val capabilities: ExecutorCapabilities
) : TaskExecutor {
    
    override suspend fun executeTask(task: ComputeTask): ComputeTask {
        // Simulate task execution
        delay(task.estimatedDurationMs / 10) // Speed up for demo
        
        return task.copy(
            status = TaskStatus.COMPLETED,
            data = task.data + ("result" to "Task completed on node $nodeId")
        )
    }
    
    override fun getCapabilities(): ExecutorCapabilities = capabilities
}

interface ComputeNetworkCommunicator {
    suspend fun sendTask(peerId: Uuid, task: ComputeTask): Boolean
    suspend fun receiveTaskResult(taskId: Uuid): ComputeTask?
}

class KtorComputeNetworkCommunicator : ComputeNetworkCommunicator {
    override suspend fun sendTask(peerId: Uuid, task: ComputeTask): Boolean {
        // Mock network communication
        delay(100)
        return true
    }
    
    override suspend fun receiveTaskResult(taskId: Uuid): ComputeTask? {
        // Mock result retrieval
        delay(50)
        return null
    }
}

interface ComputeScheduler {
    val activeTasks: Flow<List<ComputeTask>>
    suspend fun scheduleTask(task: ComputeTask): Boolean
    suspend fun getTaskStatus(taskId: Uuid): TaskStatus?
}

class OmnisyncraComputeScheduler(
    private val localPlatform: Platform,
    private val taskExecutor: TaskExecutor,
    private val networkCommunicator: ComputeNetworkCommunicator
) : ComputeScheduler {
    
    private val _activeTasks = MutableStateFlow<List<ComputeTask>>(emptyList())
    override val activeTasks = _activeTasks.asStateFlow()
    
    override suspend fun scheduleTask(task: ComputeTask): Boolean {
        val currentTasks = _activeTasks.value.toMutableList()
        currentTasks.add(task.copy(status = TaskStatus.RUNNING))
        _activeTasks.value = currentTasks
        
        // Execute task locally for now
        val result = taskExecutor.executeTask(task)
        
        // Update task list
        val updatedTasks = _activeTasks.value.map { 
            if (it.id == task.id) result else it 
        }
        _activeTasks.value = updatedTasks
        
        return true
    }
    
    override suspend fun getTaskStatus(taskId: Uuid): TaskStatus? {
        return _activeTasks.value.find { it.id == taskId }?.status
    }
}