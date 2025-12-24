package com.omnisyncra.core.compute

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable

@Serializable
data class ComputeTask(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid = uuid4(),
    val type: TaskType,
    val priority: TaskPriority,
    val payload: TaskPayload,
    val requirements: ComputeRequirements,
    val metadata: TaskMetadata,
    val createdAt: Long,
    val deadline: Long? = null
)

@Serializable
enum class TaskType {
    AI_INFERENCE,
    DATA_PROCESSING,
    ENCRYPTION,
    SEMANTIC_ANALYSIS,
    IMAGE_PROCESSING,
    TEXT_ANALYSIS,
    CONTEXT_GENERATION,
    CUSTOM
}

@Serializable
enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

@Serializable
data class TaskPayload(
    val data: Map<String, String>,
    val inputFormat: String,
    val expectedOutputFormat: String,
    val processingHints: List<String> = emptyList()
)

@Serializable
data class ComputeRequirements(
    val minComputePower: com.omnisyncra.core.domain.ComputePower,
    val estimatedMemoryMB: Int,
    val estimatedDurationMs: Long,
    val requiresGPU: Boolean = false,
    val requiresNetwork: Boolean = false,
    val canBeBatched: Boolean = true,
    val maxRetries: Int = 3
)

@Serializable
data class TaskMetadata(
    @Serializable(with = UuidSerializer::class)
    val originDeviceId: Uuid,
    val contextId: String? = null,
    val tags: List<String> = emptyList(),
    val customProperties: Map<String, String> = emptyMap()
)

@Serializable
data class TaskResult(
    @Serializable(with = UuidSerializer::class)
    val taskId: Uuid,
    val status: TaskStatus,
    val result: TaskPayload? = null,
    val error: TaskError? = null,
    @Serializable(with = UuidSerializer::class)
    val executedBy: Uuid,
    val executionTimeMs: Long,
    val completedAt: Long
)

@Serializable
enum class TaskStatus {
    PENDING,
    ASSIGNED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT
}

@Serializable
data class TaskError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val isRetryable: Boolean = true
)