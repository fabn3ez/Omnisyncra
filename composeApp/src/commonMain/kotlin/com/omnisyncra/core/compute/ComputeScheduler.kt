package com.omnisyncra.core.compute

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.Device
import com.omnisyncra.core.domain.ComputePower
import kotlinx.coroutines.flow.Flow

interface ComputeScheduler {
    val pendingTasks: Flow<List<ComputeTask>>
    val runningTasks: Flow<List<TaskExecution>>
    val completedTasks: Flow<List<TaskResult>>
    
    suspend fun submitTask(task: ComputeTask): Uuid
    suspend fun cancelTask(taskId: Uuid): Boolean
    suspend fun getTaskStatus(taskId: Uuid): TaskStatus?
    suspend fun getTaskResult(taskId: Uuid): TaskResult?
    
    suspend fun registerComputeNode(device: Device)
    suspend fun unregisterComputeNode(deviceId: Uuid)
    suspend fun updateNodeCapacity(deviceId: Uuid, availableCapacity: NodeCapacity)
    
    fun getAvailableNodes(): List<ComputeNode>
    fun getOptimalNode(task: ComputeTask): ComputeNode?
}

data class TaskExecution(
    val task: ComputeTask,
    val assignedNode: ComputeNode,
    val startedAt: Long,
    val estimatedCompletionAt: Long
)

data class ComputeNode(
    val device: Device,
    val capacity: NodeCapacity,
    val performance: NodePerformance,
    val availability: NodeAvailability,
    val lastSeen: Long
) {
    fun canExecute(task: ComputeTask): Boolean {
        return capacity.computePower.ordinal >= task.requirements.minComputePower.ordinal &&
                capacity.availableMemoryMB >= task.requirements.estimatedMemoryMB &&
                capacity.availableSlots > 0 &&
                availability.status == NodeStatus.AVAILABLE &&
                (!task.requirements.requiresGPU || capacity.hasGPU) &&
                (!task.requirements.requiresNetwork || capacity.hasNetwork)
    }
    
    fun getExecutionScore(task: ComputeTask): Double {
        if (!canExecute(task)) return 0.0
        
        var score = 0.0
        
        // Compute power score (0.0 to 1.0)
        val powerScore = when {
            capacity.computePower.ordinal > task.requirements.minComputePower.ordinal -> 1.0
            capacity.computePower == task.requirements.minComputePower -> 0.7
            else -> 0.0
        }
        score += powerScore * 0.4
        
        // Memory availability score
        val memoryRatio = capacity.availableMemoryMB.toDouble() / task.requirements.estimatedMemoryMB
        val memoryScore = minOf(1.0, memoryRatio / 2.0) // Optimal at 2x required memory
        score += memoryScore * 0.2
        
        // Load score (lower load = higher score)
        val loadScore = 1.0 - (capacity.currentLoad / 100.0)
        score += loadScore * 0.2
        
        // Performance history score
        score += performance.averageTaskCompletionRate * 0.1
        
        // Proximity bonus (if available)
        val proximityScore = when (device.proximityInfo?.distance) {
            com.omnisyncra.core.domain.ProximityDistance.IMMEDIATE -> 1.0
            com.omnisyncra.core.domain.ProximityDistance.NEAR -> 0.8
            com.omnisyncra.core.domain.ProximityDistance.FAR -> 0.5
            else -> 0.3
        }
        score += proximityScore * 0.1
        
        return score
    }
}

data class NodeCapacity(
    val computePower: ComputePower,
    val totalMemoryMB: Int,
    val availableMemoryMB: Int,
    val totalSlots: Int,
    val availableSlots: Int,
    val currentLoad: Double, // 0.0 to 100.0
    val hasGPU: Boolean = false,
    val hasNetwork: Boolean = true
)

data class NodePerformance(
    val averageTaskCompletionRate: Double, // 0.0 to 1.0
    val averageExecutionTimeMs: Long,
    val successRate: Double, // 0.0 to 1.0
    val totalTasksCompleted: Int,
    val lastPerformanceUpdate: Long
)

data class NodeAvailability(
    val status: NodeStatus,
    val estimatedAvailableUntil: Long? = null,
    val maintenanceWindow: TimeWindow? = null
)

enum class NodeStatus {
    AVAILABLE,
    BUSY,
    MAINTENANCE,
    OFFLINE,
    UNKNOWN
}

data class TimeWindow(
    val startTime: Long,
    val endTime: Long
)