package com.omnisyncra.core.performance

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.crdt.CrdtOperation
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

data class BatchConfig(
    val maxBatchSize: Int = 50,
    val maxBatchDelayMs: Long = 100L,
    val maxMemoryPerBatchMB: Int = 5
)

data class BatchedOperation(
    val operation: CrdtOperation,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val priority: BatchPriority = BatchPriority.NORMAL
)

enum class BatchPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

data class OperationBatch(
    val id: Uuid = com.benasher44.uuid.uuid4(),
    val operations: List<BatchedOperation>,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val priority: BatchPriority = operations.maxByOrNull { it.priority.ordinal }?.priority ?: BatchPriority.NORMAL
)

class BatchProcessor(
    private val config: BatchConfig = BatchConfig(),
    private val processBatch: suspend (OperationBatch) -> Unit
) {
    private val operationChannel = Channel<BatchedOperation>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _batchStats = MutableStateFlow(BatchStats())
    val batchStats: StateFlow<BatchStats> = _batchStats.asStateFlow()
    
    init {
        startBatchProcessing()
    }
    
    suspend fun submitOperation(
        operation: CrdtOperation,
        priority: BatchPriority = BatchPriority.NORMAL
    ) {
        val batchedOp = BatchedOperation(operation, priority = priority)
        operationChannel.send(batchedOp)
        
        // Update stats
        val currentStats = _batchStats.value
        _batchStats.value = currentStats.copy(
            totalOperationsSubmitted = currentStats.totalOperationsSubmitted + 1
        )
    }
    
    private fun startBatchProcessing() {
        scope.launch {
            val pendingOperations = mutableListOf<BatchedOperation>()
            var lastBatchTime = Clock.System.now().toEpochMilliseconds()
            
            while (isActive) {
                try {
                    // Wait for operations with timeout
                    val operation = withTimeoutOrNull(config.maxBatchDelayMs) {
                        operationChannel.receive()
                    }
                    
                    if (operation != null) {
                        pendingOperations.add(operation)
                    }
                    
                    val currentTime = Clock.System.now().toEpochMilliseconds()
                    val shouldFlush = pendingOperations.isNotEmpty() && (
                        pendingOperations.size >= config.maxBatchSize ||
                        (currentTime - lastBatchTime) >= config.maxBatchDelayMs ||
                        pendingOperations.any { it.priority == BatchPriority.CRITICAL } ||
                        estimateBatchMemoryUsage(pendingOperations) >= config.maxMemoryPerBatchMB * 1024 * 1024
                    )
                    
                    if (shouldFlush) {
                        val batch = createBatch(pendingOperations.toList())
                        pendingOperations.clear()
                        lastBatchTime = currentTime
                        
                        // Process batch
                        launch {
                            processBatchWithStats(batch)
                        }
                    }
                } catch (e: Exception) {
                    // Handle batch processing errors
                    delay(100) // Brief delay before retrying
                }
            }
        }
    }
    
    private fun createBatch(operations: List<BatchedOperation>): OperationBatch {
        // Sort by priority (critical first) and then by timestamp
        val sortedOperations = operations.sortedWith(
            compareByDescending<BatchedOperation> { it.priority.ordinal }
                .thenBy { it.timestamp }
        )
        
        return OperationBatch(operations = sortedOperations)
    }
    
    private suspend fun processBatchWithStats(batch: OperationBatch) {
        val startTime = Clock.System.now().toEpochMilliseconds()
        var success = true
        
        try {
            processBatch(batch)
        } catch (e: Exception) {
            success = false
            throw e
        } finally {
            val endTime = Clock.System.now().toEpochMilliseconds()
            val processingTime = endTime - startTime
            
            // Update stats
            val currentStats = _batchStats.value
            _batchStats.value = currentStats.copy(
                totalBatchesProcessed = currentStats.totalBatchesProcessed + 1,
                totalOperationsProcessed = currentStats.totalOperationsProcessed + batch.operations.size,
                successfulBatches = if (success) currentStats.successfulBatches + 1 else currentStats.successfulBatches,
                failedBatches = if (!success) currentStats.failedBatches + 1 else currentStats.failedBatches,
                avgBatchSize = calculateAvgBatchSize(currentStats, batch.operations.size),
                avgProcessingTimeMs = calculateAvgProcessingTime(currentStats, processingTime),
                lastBatchProcessedAt = endTime
            )
        }
    }
    
    private fun calculateAvgBatchSize(currentStats: BatchStats, newBatchSize: Int): Double {
        val totalBatches = currentStats.totalBatchesProcessed + 1
        return ((currentStats.avgBatchSize * currentStats.totalBatchesProcessed) + newBatchSize) / totalBatches
    }
    
    private fun calculateAvgProcessingTime(currentStats: BatchStats, newProcessingTime: Long): Double {
        val totalBatches = currentStats.totalBatchesProcessed + 1
        return ((currentStats.avgProcessingTimeMs * currentStats.totalBatchesProcessed) + newProcessingTime) / totalBatches
    }
    
    private fun estimateBatchMemoryUsage(operations: List<BatchedOperation>): Int {
        // Rough estimation: 200 bytes per operation
        return operations.size * 200
    }
    
    suspend fun flush() {
        // Force process any pending operations
        val pendingOps = mutableListOf<BatchedOperation>()
        
        // Drain the channel
        while (!operationChannel.isEmpty) {
            try {
                val op = operationChannel.tryReceive().getOrNull()
                if (op != null) {
                    pendingOps.add(op)
                }
            } catch (e: Exception) {
                break
            }
        }
        
        if (pendingOps.isNotEmpty()) {
            val batch = createBatch(pendingOps)
            processBatchWithStats(batch)
        }
    }
    
    fun cleanup() {
        scope.cancel()
        operationChannel.close()
    }
}

data class BatchStats(
    val totalOperationsSubmitted: Long = 0,
    val totalOperationsProcessed: Long = 0,
    val totalBatchesProcessed: Long = 0,
    val successfulBatches: Long = 0,
    val failedBatches: Long = 0,
    val avgBatchSize: Double = 0.0,
    val avgProcessingTimeMs: Double = 0.0,
    val lastBatchProcessedAt: Long = 0
) {
    val successRate: Double
        get() = if (totalBatchesProcessed > 0) successfulBatches.toDouble() / totalBatchesProcessed else 0.0
    
    val compressionRatio: Double
        get() = if (totalBatchesProcessed > 0) totalOperationsProcessed.toDouble() / totalBatchesProcessed else 0.0
}

// Utility class for managing multiple batch processors
class BatchManager {
    private val processors = mutableMapOf<String, BatchProcessor>()
    
    fun createProcessor(
        name: String,
        config: BatchConfig = BatchConfig(),
        processBatch: suspend (OperationBatch) -> Unit
    ): BatchProcessor {
        val processor = BatchProcessor(config, processBatch)
        processors[name] = processor
        return processor
    }
    
    fun getProcessor(name: String): BatchProcessor? {
        return processors[name]
    }
    
    suspend fun submitToProcessor(
        processorName: String,
        operation: CrdtOperation,
        priority: BatchPriority = BatchPriority.NORMAL
    ) {
        processors[processorName]?.submitOperation(operation, priority)
    }
    
    suspend fun flushAll() {
        processors.values.forEach { it.flush() }
    }
    
    fun getAllStats(): Map<String, BatchStats> {
        return processors.mapValues { (_, processor) ->
            processor.batchStats.value
        }
    }
    
    fun cleanup() {
        processors.values.forEach { it.cleanup() }
        processors.clear()
    }
}