package com.omnisyncra.core.compute

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

interface TaskExecutor {
    suspend fun executeTask(task: ComputeTask): TaskResult
    suspend fun cancelTask(taskId: Uuid): Boolean
    fun getSupportedTaskTypes(): List<TaskType>
    fun getExecutorCapabilities(): ExecutorCapabilities
}

data class ExecutorCapabilities(
    val maxConcurrentTasks: Int,
    val supportedTaskTypes: List<TaskType>,
    val hasGPUAcceleration: Boolean,
    val maxMemoryMB: Int,
    val estimatedPerformanceMultiplier: Double // 1.0 = baseline
)

class LocalTaskExecutor(
    private val deviceId: Uuid,
    private val capabilities: ExecutorCapabilities
) : TaskExecutor {
    
    private val runningTasks = mutableMapOf<Uuid, ComputeTask>()
    
    override suspend fun executeTask(task: ComputeTask): TaskResult {
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        try {
            runningTasks[task.id] = task
            
            val result = when (task.type) {
                TaskType.AI_INFERENCE -> executeAIInference(task)
                TaskType.DATA_PROCESSING -> executeDataProcessing(task)
                TaskType.ENCRYPTION -> executeEncryption(task)
                TaskType.SEMANTIC_ANALYSIS -> executeSemanticAnalysis(task)
                TaskType.IMAGE_PROCESSING -> executeImageProcessing(task)
                TaskType.TEXT_ANALYSIS -> executeTextAnalysis(task)
                TaskType.CONTEXT_GENERATION -> executeContextGeneration(task)
                TaskType.CUSTOM -> executeCustomTask(task)
            }
            
            val executionTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            
            return TaskResult(
                taskId = task.id,
                status = TaskStatus.COMPLETED,
                result = result,
                executedBy = deviceId,
                executionTimeMs = executionTime,
                completedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
            
        } catch (e: Exception) {
            val executionTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            
            return TaskResult(
                taskId = task.id,
                status = TaskStatus.FAILED,
                error = TaskError(
                    code = "EXECUTION_FAILED",
                    message = e.message ?: "Unknown execution error",
                    isRetryable = true
                ),
                executedBy = deviceId,
                executionTimeMs = executionTime,
                completedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
        } finally {
            runningTasks.remove(task.id)
        }
    }
    
    override suspend fun cancelTask(taskId: Uuid): Boolean {
        return runningTasks.remove(taskId) != null
    }
    
    override fun getSupportedTaskTypes(): List<TaskType> {
        return capabilities.supportedTaskTypes
    }
    
    override fun getExecutorCapabilities(): ExecutorCapabilities {
        return capabilities
    }
    
    private suspend fun executeAIInference(task: ComputeTask): TaskPayload {
        // Simulate AI inference processing
        val processingTime = (task.requirements.estimatedDurationMs * 0.8).toLong()
        delay(processingTime)
        
        val inputText = task.payload.data["input"] ?: ""
        val model = task.payload.data["model"] ?: "default"
        
        // Simulate AI processing result
        val confidence = kotlin.random.Random.nextInt(80, 96) / 100.0
        val result = "AI inference result for: ${inputText.take(50)}... (model: $model, confidence: $confidence)"
        
        return TaskPayload(
            data = mapOf(
                "result" to result,
                "confidence" to confidence.toString(),
                "model_used" to model,
                "processing_time_ms" to processingTime.toString()
            ),
            inputFormat = task.payload.inputFormat,
            expectedOutputFormat = task.payload.expectedOutputFormat
        )
    }
    
    private suspend fun executeDataProcessing(task: ComputeTask): TaskPayload {
        // Simulate data processing
        val processingTime = (task.requirements.estimatedDurationMs * 0.6).toLong()
        delay(processingTime)
        
        val dataSize = task.payload.data["size"]?.toIntOrNull() ?: 1000
        val operation = task.payload.data["operation"] ?: "transform"
        
        return TaskPayload(
            data = mapOf(
                "processed_records" to dataSize.toString(),
                "operation" to operation,
                "status" to "completed",
                "processing_time_ms" to processingTime.toString()
            ),
            inputFormat = task.payload.inputFormat,
            expectedOutputFormat = task.payload.expectedOutputFormat
        )
    }
    
    private suspend fun executeEncryption(task: ComputeTask): TaskPayload {
        // Simulate encryption processing
        val processingTime = (task.requirements.estimatedDurationMs * 0.4).toLong()
        delay(processingTime)
        
        val algorithm = task.payload.data["algorithm"] ?: "AES-256"
        val dataSize = task.payload.data["data_size"] ?: "unknown"
        
        return TaskPayload(
            data = mapOf(
                "encrypted_data" to "encrypted_payload_${task.id}",
                "algorithm" to algorithm,
                "key_id" to "key_${System.currentTimeMillis()}",
                "data_size" to dataSize,
                "processing_time_ms" to processingTime.toString()
            ),
            inputFormat = task.payload.inputFormat,
            expectedOutputFormat = task.payload.expectedOutputFormat
        )
    }
    
    private suspend fun executeSemanticAnalysis(task: ComputeTask): TaskPayload {
        // Simulate semantic analysis
        val processingTime = (task.requirements.estimatedDurationMs * 0.7).toLong()
        delay(processingTime)
        
        val text = task.payload.data["text"] ?: ""
        val analysisType = task.payload.data["analysis_type"] ?: "sentiment"
        
        val sentiment = listOf("positive", "negative", "neutral").shuffled().first()
        val keywords = listOf("innovation", "technology", "efficiency", "collaboration").shuffled().take(3)
        
        return TaskPayload(
            data = mapOf(
                "sentiment" to sentiment,
                "keywords" to keywords.joinToString(","),
                "confidence" to (0.75 + kotlin.random.Random.nextDouble() * 0.2).toString(),
                "analysis_type" to analysisType,
                "word_count" to text.split(" ").size.toString(),
                "processing_time_ms" to processingTime.toString()
            ),
            inputFormat = task.payload.inputFormat,
            expectedOutputFormat = task.payload.expectedOutputFormat
        )
    }
    
    private suspend fun executeImageProcessing(task: ComputeTask): TaskPayload {
        // Simulate image processing
        val processingTime = (task.requirements.estimatedDurationMs * 1.2).toLong()
        delay(processingTime)
        
        val operation = task.payload.data["operation"] ?: "resize"
        val format = task.payload.data["format"] ?: "JPEG"
        
        return TaskPayload(
            data = mapOf(
                "processed_image_id" to "img_${task.id}",
                "operation" to operation,
                "output_format" to format,
                "dimensions" to "1920x1080",
                "file_size_kb" to kotlin.random.Random.nextInt(500, 2001).toString(),
                "processing_time_ms" to processingTime.toString()
            ),
            inputFormat = task.payload.inputFormat,
            expectedOutputFormat = task.payload.expectedOutputFormat
        )
    }
    
    private suspend fun executeTextAnalysis(task: ComputeTask): TaskPayload {
        // Simulate text analysis
        val processingTime = (task.requirements.estimatedDurationMs * 0.5).toLong()
        delay(processingTime)
        
        val text = task.payload.data["text"] ?: ""
        val analysisType = task.payload.data["type"] ?: "summary"
        
        return TaskPayload(
            data = mapOf(
                "summary" to "Analyzed text contains ${text.length} characters with key themes around technology and innovation.",
                "readability_score" to kotlin.random.Random.nextInt(60, 91).toString(),
                "language" to "en",
                "analysis_type" to analysisType,
                "processing_time_ms" to processingTime.toString()
            ),
            inputFormat = task.payload.inputFormat,
            expectedOutputFormat = task.payload.expectedOutputFormat
        )
    }
    
    private suspend fun executeContextGeneration(task: ComputeTask): TaskPayload {
        // Simulate context generation for Omnisyncra
        val processingTime = (task.requirements.estimatedDurationMs * 0.9).toLong()
        delay(processingTime)
        
        val contextType = task.payload.data["context_type"] ?: "work_session"
        val userActivity = task.payload.data["user_activity"] ?: "document_editing"
        
        val suggestions = listOf(
            "Research related topics",
            "Prepare presentation slides",
            "Schedule follow-up meeting",
            "Review similar documents"
        ).shuffled().take(2)
        
        return TaskPayload(
            data = mapOf(
                "context_type" to contextType,
                "suggestions" to suggestions.joinToString("|"),
                "priority_score" to (0.6 + kotlin.random.Random.nextDouble() * 0.4).toString(),
                "related_contexts" to "ctx_${kotlin.random.Random.nextInt(1000, 10000)},ctx_${kotlin.random.Random.nextInt(1000, 10000)}",
                "user_activity" to userActivity,
                "processing_time_ms" to processingTime.toString()
            ),
            inputFormat = task.payload.inputFormat,
            expectedOutputFormat = task.payload.expectedOutputFormat
        )
    }
    
    private suspend fun executeCustomTask(task: ComputeTask): TaskPayload {
        // Simulate custom task processing
        val processingTime = task.requirements.estimatedDurationMs
        delay(processingTime)
        
        return TaskPayload(
            data = mapOf(
                "status" to "completed",
                "custom_result" to "Custom task ${task.id} processed successfully",
                "processing_time_ms" to processingTime.toString()
            ),
            inputFormat = task.payload.inputFormat,
            expectedOutputFormat = task.payload.expectedOutputFormat
        )
    }
}