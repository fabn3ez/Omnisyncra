package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Security operation priority levels
 */
enum class SecurityOperationPriority {
    CRITICAL,   // Authentication, key exchange failures
    HIGH,       // Encryption/decryption for active sessions
    NORMAL,     // Regular operations
    LOW,        // Background tasks, cleanup
    BACKGROUND  // Non-urgent maintenance
}

/**
 * Security operation types
 */
enum class SecurityOperationType {
    ENCRYPTION,
    DECRYPTION,
    KEY_DERIVATION,
    SIGNATURE_GENERATION,
    SIGNATURE_VERIFICATION,
    CERTIFICATE_VALIDATION,
    TRUST_VERIFICATION,
    SESSION_MANAGEMENT,
    CLEANUP
}

/**
 * Security operation request
 */
data class SecurityOperationRequest(
    val id: Uuid = com.benasher44.uuid.uuid4(),
    val type: SecurityOperationType,
    val priority: SecurityOperationPriority,
    val sessionId: Uuid? = null,
    val deviceId: Uuid? = null,
    val data: ByteArray? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val timeout: Duration = 30.toDuration(DurationUnit.SECONDS)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SecurityOperationRequest
        return id == other.id &&
                type == other.type &&
                priority == other.priority &&
                sessionId == other.sessionId &&
                deviceId == other.deviceId &&
                data?.contentEquals(other.data ?: ByteArray(0)) == true &&
                metadata == other.metadata &&
                createdAt == other.createdAt &&
                timeout == other.timeout
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + (sessionId?.hashCode() ?: 0)
        result = 31 * result + (deviceId?.hashCode() ?: 0)
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + timeout.hashCode()
        return result
    }
}

/**
 * Security operation result
 */
data class SecurityOperationResult(
    val requestId: Uuid,
    val success: Boolean,
    val result: Any? = null,
    val error: Throwable? = null,
    val processingTime: Duration,
    val completedAt: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Concurrent security manager configuration
 */
data class ConcurrentSecurityConfig(
    val maxConcurrentOperations: Int = 10,
    val workerThreads: Int = 4,
    val queueCapacity: Int = 100,
    val enablePriorityQueuing: Boolean = true,
    val enableLoadBalancing: Boolean = true,
    val operationTimeout: Duration = 30.toDuration(DurationUnit.SECONDS),
    val criticalOperationTimeout: Duration = 5.toDuration(DurationUnit.SECONDS)
)

/**
 * Worker thread statistics
 */
data class WorkerThreadStats(
    val workerId: Int,
    val isActive: Boolean,
    val currentOperation: SecurityOperationType? = null,
    val operationsProcessed: Long = 0L,
    val averageProcessingTime: Duration = 0.toDuration(DurationUnit.MILLISECONDS),
    val lastActivityTime: Long = 0L
)

/**
 * Concurrent security manager interface
 */
interface ConcurrentSecurityManager {
    val isActive: StateFlow<Boolean>
    val queueSize: StateFlow<Int>
    val activeOperations: StateFlow<Int>
    val workerStats: StateFlow<List<WorkerThreadStats>>
    val config: StateFlow<ConcurrentSecurityConfig>
    
    suspend fun start(): Boolean
    suspend fun stop()
    suspend fun submitOperation(request: SecurityOperationRequest): Deferred<SecurityOperationResult>
    suspend fun submitCriticalOperation(request: SecurityOperationRequest): SecurityOperationResult
    suspend fun updateConfig(newConfig: ConcurrentSecurityConfig)
    suspend fun getQueueStatus(): Map<SecurityOperationPriority, Int>
}

/**
 * Implementation of concurrent security manager
 */
class OmnisyncraConcurrentSecurityManager(
    private val deviceId: Uuid,
    private val securityLogger: SecurityEventLogger,
    private val performanceOptimizer: SecurityPerformanceOptimizer,
    private val initialConfig: ConcurrentSecurityConfig = ConcurrentSecurityConfig()
) : ConcurrentSecurityManager {
    
    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _queueSize = MutableStateFlow(0)
    override val queueSize: StateFlow<Int> = _queueSize.asStateFlow()
    
    private val _activeOperations = MutableStateFlow(0)
    override val activeOperations: StateFlow<Int> = _activeOperations.asStateFlow()
    
    private val _workerStats = MutableStateFlow<List<WorkerThreadStats>>(emptyList())
    override val workerStats: StateFlow<List<WorkerThreadStats>> = _workerStats.asStateFlow()
    
    private val _config = MutableStateFlow(initialConfig)
    override val config: StateFlow<ConcurrentSecurityConfig> = _config.asStateFlow()
    
    // Priority queues for different operation priorities
    private val criticalQueue = Channel<SecurityOperationRequest>(Channel.UNLIMITED)
    private val highQueue = Channel<SecurityOperationRequest>(Channel.UNLIMITED)
    private val normalQueue = Channel<SecurityOperationRequest>(Channel.UNLIMITED)
    private val lowQueue = Channel<SecurityOperationRequest>(Channel.UNLIMITED)
    private val backgroundQueue = Channel<SecurityOperationRequest>(Channel.UNLIMITED)
    
    // Worker management
    private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val workers = mutableListOf<Job>()
    private val workerStatsMap = mutableMapOf<Int, WorkerThreadStats>()
    
    // Operation tracking
    private val pendingOperations = mutableMapOf<Uuid, CompletableDeferred<SecurityOperationResult>>()
    private val activeOperationCount = kotlinx.coroutines.sync.Mutex()
    
    override suspend fun start(): Boolean {
        return try {
            if (_isActive.value) {
                return true // Already active
            }
            
            val config = _config.value
            
            // Start worker threads
            repeat(config.workerThreads) { workerId ->
                val worker = workerScope.launch {
                    runWorker(workerId)
                }
                workers.add(worker)
                
                workerStatsMap[workerId] = WorkerThreadStats(
                    workerId = workerId,
                    isActive = true,
                    lastActivityTime = Clock.System.now().toEpochMilliseconds()
                )
            }
            
            _isActive.value = true
            updateWorkerStats()
            
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
                severity = SecurityEventSeverity.INFO,
                message = "Concurrent security manager started",
                details = mapOf(
                    "worker_threads" to config.workerThreads.toString(),
                    "max_concurrent_ops" to config.maxConcurrentOperations.toString()
                )
            )
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to start concurrent security manager",
                error = e
            )
            false
        }
    }
    
    override suspend fun stop() {
        try {
            _isActive.value = false
            
            // Cancel all workers
            workers.forEach { it.cancel() }
            workers.clear()
            
            // Complete pending operations with cancellation
            pendingOperations.values.forEach { deferred ->
                deferred.complete(SecurityOperationResult(
                    requestId = com.benasher44.uuid.uuid4(),
                    success = false,
                    error = CancellationException("Security manager stopped"),
                    processingTime = 0.toDuration(DurationUnit.MILLISECONDS)
                ))
            }
            pendingOperations.clear()
            
            // Clear queues
            clearAllQueues()
            
            workerStatsMap.clear()
            updateWorkerStats()
            
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.INFO,
                message = "Concurrent security manager stopped"
            )
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.ERROR,
                message = "Error stopping concurrent security manager",
                error = e
            )
        }
    }
    
    override suspend fun submitOperation(request: SecurityOperationRequest): Deferred<SecurityOperationResult> {
        val deferred = CompletableDeferred<SecurityOperationResult>()
        pendingOperations[request.id] = deferred
        
        // Add to appropriate priority queue
        val queue = when (request.priority) {
            SecurityOperationPriority.CRITICAL -> criticalQueue
            SecurityOperationPriority.HIGH -> highQueue
            SecurityOperationPriority.NORMAL -> normalQueue
            SecurityOperationPriority.LOW -> lowQueue
            SecurityOperationPriority.BACKGROUND -> backgroundQueue
        }
        
        try {
            queue.send(request)
            updateQueueSize()
            
            // Set up timeout
            workerScope.launch {
                delay(request.timeout)
                if (!deferred.isCompleted) {
                    pendingOperations.remove(request.id)
                    deferred.complete(SecurityOperationResult(
                        requestId = request.id,
                        success = false,
                        error = TimeoutCancellationException("Operation timed out"),
                        processingTime = request.timeout
                    ))
                }
            }
            
        } catch (e: Exception) {
            pendingOperations.remove(request.id)
            deferred.complete(SecurityOperationResult(
                requestId = request.id,
                success = false,
                error = e,
                processingTime = 0.toDuration(DurationUnit.MILLISECONDS)
            ))
        }
        
        return deferred
    }
    
    override suspend fun submitCriticalOperation(request: SecurityOperationRequest): SecurityOperationResult {
        val criticalRequest = request.copy(
            priority = SecurityOperationPriority.CRITICAL,
            timeout = _config.value.criticalOperationTimeout
        )
        
        return try {
            val deferred = submitOperation(criticalRequest)
            deferred.await()
        } catch (e: Exception) {
            SecurityOperationResult(
                requestId = request.id,
                success = false,
                error = e,
                processingTime = 0.toDuration(DurationUnit.MILLISECONDS)
            )
        }
    }
    
    override suspend fun updateConfig(newConfig: ConcurrentSecurityConfig) {
        val oldConfig = _config.value
        _config.value = newConfig
        
        // Adjust worker threads if needed
        if (newConfig.workerThreads != oldConfig.workerThreads) {
            // This would require restarting the manager in a real implementation
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                severity = SecurityEventSeverity.WARNING,
                message = "Worker thread count change requires restart",
                details = mapOf(
                    "old_threads" to oldConfig.workerThreads.toString(),
                    "new_threads" to newConfig.workerThreads.toString()
                )
            )
        }
        
        securityLogger.logEvent(
            type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
            severity = SecurityEventSeverity.INFO,
            message = "Concurrent security configuration updated",
            details = mapOf(
                "max_concurrent_ops" to newConfig.maxConcurrentOperations.toString(),
                "queue_capacity" to newConfig.queueCapacity.toString()
            )
        )
    }
    
    override suspend fun getQueueStatus(): Map<SecurityOperationPriority, Int> {
        return mapOf(
            SecurityOperationPriority.CRITICAL to criticalQueue.tryReceive().getOrNull()?.let { 1 } ?: 0,
            SecurityOperationPriority.HIGH to highQueue.tryReceive().getOrNull()?.let { 1 } ?: 0,
            SecurityOperationPriority.NORMAL to normalQueue.tryReceive().getOrNull()?.let { 1 } ?: 0,
            SecurityOperationPriority.LOW to lowQueue.tryReceive().getOrNull()?.let { 1 } ?: 0,
            SecurityOperationPriority.BACKGROUND to backgroundQueue.tryReceive().getOrNull()?.let { 1 } ?: 0
        )
    }
    
    private suspend fun runWorker(workerId: Int) {
        while (_isActive.value) {
            try {
                val request = selectNextOperation()
                if (request != null) {
                    processOperation(workerId, request)
                } else {
                    // No operations available, brief pause
                    delay(10)
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                securityLogger.logEvent(
                    type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
                    severity = SecurityEventSeverity.ERROR,
                    message = "Worker thread error",
                    error = e,
                    details = mapOf("worker_id" to workerId.toString())
                )
                delay(100) // Brief pause before retrying
            }
        }
    }
    
    private suspend fun selectNextOperation(): SecurityOperationRequest? {
        // Priority-based operation selection
        return criticalQueue.tryReceive().getOrNull()
            ?: highQueue.tryReceive().getOrNull()
            ?: normalQueue.tryReceive().getOrNull()
            ?: lowQueue.tryReceive().getOrNull()
            ?: backgroundQueue.tryReceive().getOrNull()
    }
    
    private suspend fun processOperation(workerId: Int, request: SecurityOperationRequest) {
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        // Update worker stats
        updateWorkerActivity(workerId, request.type)
        
        // Increment active operations count
        activeOperationCount.withLock {
            _activeOperations.value = _activeOperations.value + 1
        }
        
        try {
            val result = when (request.type) {
                SecurityOperationType.ENCRYPTION -> processEncryption(request)
                SecurityOperationType.DECRYPTION -> processDecryption(request)
                SecurityOperationType.KEY_DERIVATION -> processKeyDerivation(request)
                SecurityOperationType.SIGNATURE_GENERATION -> processSignatureGeneration(request)
                SecurityOperationType.SIGNATURE_VERIFICATION -> processSignatureVerification(request)
                SecurityOperationType.CERTIFICATE_VALIDATION -> processCertificateValidation(request)
                SecurityOperationType.TRUST_VERIFICATION -> processTrustVerification(request)
                SecurityOperationType.SESSION_MANAGEMENT -> processSessionManagement(request)
                SecurityOperationType.CLEANUP -> processCleanup(request)
            }
            
            val endTime = Clock.System.now().toEpochMilliseconds()
            val processingTime = (endTime - startTime).toDuration(DurationUnit.MILLISECONDS)
            
            val operationResult = SecurityOperationResult(
                requestId = request.id,
                success = true,
                result = result,
                processingTime = processingTime
            )
            
            // Complete the operation
            pendingOperations.remove(request.id)?.complete(operationResult)
            
            // Update worker stats
            updateWorkerCompletion(workerId, processingTime)
            
        } catch (e: Exception) {
            val endTime = Clock.System.now().toEpochMilliseconds()
            val processingTime = (endTime - startTime).toDuration(DurationUnit.MILLISECONDS)
            
            val operationResult = SecurityOperationResult(
                requestId = request.id,
                success = false,
                error = e,
                processingTime = processingTime
            )
            
            pendingOperations.remove(request.id)?.complete(operationResult)
            
            securityLogger.logEvent(
                type = SecurityEventType.ENCRYPTION_FAILED, // Generic for operation failures
                severity = SecurityEventSeverity.ERROR,
                message = "Security operation failed",
                error = e,
                details = mapOf(
                    "operation_type" to request.type.name,
                    "worker_id" to workerId.toString(),
                    "request_id" to request.id.toString()
                )
            )
        } finally {
            // Decrement active operations count
            activeOperationCount.withLock {
                _activeOperations.value = maxOf(0, _activeOperations.value - 1)
            }
            updateQueueSize()
        }
    }
    
    // Simplified operation processors (in real implementation, these would delegate to actual security components)
    private suspend fun processEncryption(request: SecurityOperationRequest): Any {
        return if (request.data != null && request.sessionId != null) {
            performanceOptimizer.optimizeEncryption(request.data, request.sessionId).getOrThrow()
        } else {
            throw IllegalArgumentException("Missing data or session ID for encryption")
        }
    }
    
    private suspend fun processDecryption(request: SecurityOperationRequest): Any {
        // Simplified - in real implementation would extract EncryptedData from request
        return ByteArray(0)
    }
    
    private suspend fun processKeyDerivation(request: SecurityOperationRequest): Any {
        // Simulate key derivation
        delay(5) // Simulate processing time
        return ByteArray(32) { kotlin.random.Random.nextInt(256).toByte() }
    }
    
    private suspend fun processSignatureGeneration(request: SecurityOperationRequest): Any {
        // Simulate signature generation
        delay(10) // Simulate processing time
        return ByteArray(64) { kotlin.random.Random.nextInt(256).toByte() }
    }
    
    private suspend fun processSignatureVerification(request: SecurityOperationRequest): Any {
        // Simulate signature verification
        delay(8) // Simulate processing time
        return true
    }
    
    private suspend fun processCertificateValidation(request: SecurityOperationRequest): Any {
        // Simulate certificate validation
        delay(15) // Simulate processing time
        return true
    }
    
    private suspend fun processTrustVerification(request: SecurityOperationRequest): Any {
        // Simulate trust verification
        delay(5) // Simulate processing time
        return TrustLevel.TRUSTED
    }
    
    private suspend fun processSessionManagement(request: SecurityOperationRequest): Any {
        // Simulate session management
        delay(3) // Simulate processing time
        return "session_managed"
    }
    
    private suspend fun processCleanup(request: SecurityOperationRequest): Any {
        // Simulate cleanup operation
        delay(20) // Simulate processing time
        return "cleanup_completed"
    }
    
    private fun updateWorkerActivity(workerId: Int, operationType: SecurityOperationType) {
        val currentStats = workerStatsMap[workerId]
        if (currentStats != null) {
            workerStatsMap[workerId] = currentStats.copy(
                currentOperation = operationType,
                lastActivityTime = Clock.System.now().toEpochMilliseconds()
            )
            updateWorkerStats()
        }
    }
    
    private fun updateWorkerCompletion(workerId: Int, processingTime: Duration) {
        val currentStats = workerStatsMap[workerId]
        if (currentStats != null) {
            val newOperationCount = currentStats.operationsProcessed + 1
            val newAverageTime = if (currentStats.operationsProcessed == 0L) {
                processingTime
            } else {
                val totalTime = currentStats.averageProcessingTime.inWholeMilliseconds * currentStats.operationsProcessed
                ((totalTime + processingTime.inWholeMilliseconds) / newOperationCount).toDuration(DurationUnit.MILLISECONDS)
            }
            
            workerStatsMap[workerId] = currentStats.copy(
                currentOperation = null,
                operationsProcessed = newOperationCount,
                averageProcessingTime = newAverageTime,
                lastActivityTime = Clock.System.now().toEpochMilliseconds()
            )
            updateWorkerStats()
        }
    }
    
    private fun updateWorkerStats() {
        _workerStats.value = workerStatsMap.values.toList()
    }
    
    private fun updateQueueSize() {
        // Simplified queue size calculation
        _queueSize.value = pendingOperations.size
    }
    
    private suspend fun clearAllQueues() {
        while (criticalQueue.tryReceive().isSuccess) { /* drain */ }
        while (highQueue.tryReceive().isSuccess) { /* drain */ }
        while (normalQueue.tryReceive().isSuccess) { /* drain */ }
        while (lowQueue.tryReceive().isSuccess) { /* drain */ }
        while (backgroundQueue.tryReceive().isSuccess) { /* drain */ }
    }
}