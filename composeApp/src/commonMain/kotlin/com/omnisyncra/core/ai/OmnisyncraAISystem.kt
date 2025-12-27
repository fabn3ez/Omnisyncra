package com.omnisyncra.core.ai

import com.omnisyncra.core.handoff.HandoffContext
import com.omnisyncra.core.security.SecuritySystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Main AI system implementation that orchestrates all AI components
 * Provides privacy-first intelligent features for Omnisyncra
 */
class OmnisyncraAISystem(
    private val dataSanitizer: DataSanitizer,
    private val contextAnalyzer: ContextAnalyzer,
    private val geminiClient: GeminiAPIClient,
    private val securitySystem: SecuritySystem
) : AISystem {
    
    private val _systemStatus = MutableStateFlow(
        AISystemStatus(
            isInitialized = false,
            modelsLoaded = emptySet(),
            apiConnected = false,
            processingQueue = 0,
            lastActivity = 0L,
            performanceMetrics = PerformanceMetrics(0L, 0L, 0, 0.0f)
        )
    )
    
    private var isInitialized = false
    private val processingQueue = mutableListOf<ProcessingTask>()
    private val performanceTracker = PerformanceTracker()
    
    override suspend fun initialize(): Boolean {
        return try {
            // Initialize security system first
            if (!securitySystem.getSecurityStatus().isInitialized) {
                securitySystem.initialize()
            }
            
            // Test Gemini API connection
            val testResponse = geminiClient.analyzeContent(
                sanitizedContent = "Test connection",
                analysisType = AnalysisType.CONTEXT_EXTRACTION,
                privacyLevel = PrivacyLevel.PUBLIC
            )
            
            isInitialized = true
            updateSystemStatus(
                isInitialized = true,
                apiConnected = true,
                modelsLoaded = setOf("gemini-pro"),
                lastActivity = System.currentTimeMillis()
            )
            
            true
        } catch (e: Exception) {
            updateSystemStatus(
                isInitialized = false,
                apiConnected = false
            )
            false
        }
    }
    
    override suspend fun analyzeContext(data: UserData): ContextAnalysis {
        if (!isInitialized) {
            throw IllegalStateException("AI System not initialized")
        }
        
        val startTime = System.currentTimeMillis()
        addToProcessingQueue("analyzeContext")
        
        try {
            // Step 1: Sanitize the data
            val sanitizedData = sanitizeData(data)
            
            // Step 2: Analyze with Gemini API
            val geminiResponse = geminiClient.analyzeContent(
                sanitizedContent = sanitizedData.sanitizedContent,
                analysisType = AnalysisType.CONTEXT_EXTRACTION,
                privacyLevel = sanitizedData.privacyLevel
            )
            
            // Step 3: Extract entities
            val entities = geminiClient.extractEntities(
                text = sanitizedData.sanitizedContent,
                entityTypes = setOf(
                    EntityType.PERSON,
                    EntityType.ORGANIZATION,
                    EntityType.TECHNOLOGY,
                    EntityType.CONCEPT,
                    EntityType.PROJECT
                )
            )
            
            // Step 4: Build context graph
            val userActivity = UserActivity(
                id = "activity_${System.currentTimeMillis()}",
                type = data.contentType.name,
                content = data.content,
                timestamp = data.timestamp,
                context = data.metadata
            )
            
            contextAnalyzer.updateContextGraph(userActivity)
            val contextGraph = contextAnalyzer.buildContextGraph(listOf(userActivity))
            
            // Step 5: Analyze relationships
            val relationships = extractRelationshipsFromGemini(geminiResponse)
            
            // Step 6: Calculate sentiment
            val sentiment = extractSentimentFromGemini(geminiResponse)
            
            val processingTime = System.currentTimeMillis() - startTime
            performanceTracker.recordProcessing(processingTime)
            
            return ContextAnalysis(
                mainTopics = extractTopicsFromGemini(geminiResponse),
                entities = entities,
                relationships = relationships,
                sentiment = sentiment,
                importance = calculateImportance(geminiResponse),
                contextGraph = contextGraph
            )
            
        } finally {
            removeFromProcessingQueue("analyzeContext")
            updateLastActivity()
        }
    }
    
    override suspend fun sanitizeData(data: Any): SanitizedData {
        return when (data) {
            is String -> {
                val sanitized = dataSanitizer.sanitizeText(data)
                SanitizedData(
                    sanitizedContent = sanitized.content,
                    originalHash = sanitized.originalHash,
                    sanitizationReport = SanitizationReport(
                        itemsRemoved = sanitized.removedItems.map { 
                            SanitizedItem(data, sanitized.content, "text", sanitized.confidence)
                        },
                        itemsAnonymized = emptyList(),
                        confidenceScore = sanitized.confidence,
                        processingTime = System.currentTimeMillis()
                    ),
                    confidenceScore = sanitized.confidence,
                    privacyLevel = determinePrivacyLevel(sanitized.removedItems)
                )
            }
            is UserData -> {
                val sanitized = dataSanitizer.sanitizeText(data.content)
                SanitizedData(
                    sanitizedContent = sanitized.content,
                    originalHash = sanitized.originalHash,
                    sanitizationReport = SanitizationReport(
                        itemsRemoved = sanitized.removedItems.map { 
                            SanitizedItem(data.content, sanitized.content, "text", sanitized.confidence)
                        },
                        itemsAnonymized = emptyList(),
                        confidenceScore = sanitized.confidence,
                        processingTime = System.currentTimeMillis()
                    ),
                    confidenceScore = sanitized.confidence,
                    privacyLevel = determinePrivacyLevel(sanitized.removedItems)
                )
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                dataSanitizer.sanitizeStructuredData(data as Map<String, Any>)
            }
            else -> {
                // For unknown types, convert to string and sanitize
                val stringData = data.toString()
                val sanitized = dataSanitizer.sanitizeText(stringData)
                SanitizedData(
                    sanitizedContent = sanitized.content,
                    originalHash = sanitized.originalHash,
                    sanitizationReport = SanitizationReport(
                        itemsRemoved = sanitized.removedItems.map { 
                            SanitizedItem(stringData, sanitized.content, "text", sanitized.confidence)
                        },
                        itemsAnonymized = emptyList(),
                        confidenceScore = sanitized.confidence,
                        processingTime = System.currentTimeMillis()
                    ),
                    confidenceScore = sanitized.confidence,
                    privacyLevel = PrivacyLevel.INTERNAL
                )
            }
        }
    }
    
    override suspend fun generateSummary(content: String): ContentSummary {
        if (!isInitialized) {
            throw IllegalStateException("AI System not initialized")
        }
        
        addToProcessingQueue("generateSummary")
        
        try {
            // Sanitize content first
            val sanitized = dataSanitizer.sanitizeText(content)
            
            // Generate summary with Gemini
            val summary = geminiClient.generateSummary(
                content = sanitized.content,
                summaryType = SummaryType.CONTEXTUAL,
                maxLength = 500
            )
            
            // Extract key points and entities
            val entities = geminiClient.extractEntities(
                text = sanitized.content,
                entityTypes = setOf(EntityType.CONCEPT, EntityType.TECHNOLOGY, EntityType.PROJECT)
            )
            
            return ContentSummary(
                summary = summary,
                keyPoints = extractKeyPoints(summary),
                entities = entities,
                confidence = 0.8f,
                originalLength = content.length,
                summaryLength = summary.length,
                sources = listOf("gemini-analysis")
            )
            
        } finally {
            removeFromProcessingQueue("generateSummary")
            updateLastActivity()
        }
    }
    
    override suspend fun optimizeHandoff(context: HandoffContext): HandoffPlan {
        if (!isInitialized) {
            throw IllegalStateException("AI System not initialized")
        }
        
        addToProcessingQueue("optimizeHandoff")
        
        try {
            // Analyze current context
            val currentContext = contextAnalyzer.analyzeCurrentContext()
            
            // Predict next actions
            val predictions = contextAnalyzer.predictNextActions(currentContext)
            
            // Prioritize data based on context and predictions
            val prioritizedData = prioritizeHandoffData(
                availableData = context.availableData,
                predictions = predictions,
                bandwidth = context.bandwidth,
                userPreferences = context.userPreferences
            )
            
            // Calculate transfer estimates
            val estimatedSize = prioritizedData.sumOf { it.size }
            val transferTime = estimateTransferTime(estimatedSize, context.bandwidth)
            
            // Generate reasoning
            val reasoning = generateHandoffReasoning(prioritizedData, predictions, context)
            
            // Create alternative plans
            val alternatives = generateAlternativePlans(context, prioritizedData)
            
            return HandoffPlan(
                prioritizedData = prioritizedData,
                estimatedSize = estimatedSize,
                transferTime = transferTime,
                reasoning = reasoning,
                alternatives = alternatives
            )
            
        } finally {
            removeFromProcessingQueue("optimizeHandoff")
            updateLastActivity()
        }
    }
    
    override fun getPrivacyReport(): PrivacyReport {
        val usageStats = geminiClient.getUsageStats()
        
        return PrivacyReport(
            dataProcessedLocally = performanceTracker.localProcessingCount,
            dataSentToAPI = usageStats.requestsToday.toLong(),
            piiDetected = performanceTracker.piiDetectedCount,
            piiSanitized = performanceTracker.piiSanitizedCount,
            confidenceScores = performanceTracker.confidenceScores,
            privacyViolations = performanceTracker.privacyViolations
        )
    }
    
    override fun getSystemStatus(): StateFlow<AISystemStatus> = _systemStatus.asStateFlow()
    
    private fun updateSystemStatus(
        isInitialized: Boolean = this.isInitialized,
        modelsLoaded: Set<String> = _systemStatus.value.modelsLoaded,
        apiConnected: Boolean = _systemStatus.value.apiConnected,
        processingQueue: Int = this.processingQueue.size,
        lastActivity: Long = _systemStatus.value.lastActivity,
        performanceMetrics: PerformanceMetrics = _systemStatus.value.performanceMetrics
    ) {
        _systemStatus.value = AISystemStatus(
            isInitialized = isInitialized,
            modelsLoaded = modelsLoaded,
            apiConnected = apiConnected,
            processingQueue = processingQueue,
            lastActivity = lastActivity,
            performanceMetrics = performanceMetrics
        )
    }
    
    private fun addToProcessingQueue(taskType: String) {
        processingQueue.add(ProcessingTask(taskType, System.currentTimeMillis()))
        updateSystemStatus(processingQueue = processingQueue.size)
    }
    
    private fun removeFromProcessingQueue(taskType: String) {
        processingQueue.removeAll { it.type == taskType }
        updateSystemStatus(processingQueue = processingQueue.size)
    }
    
    private fun updateLastActivity() {
        updateSystemStatus(lastActivity = System.currentTimeMillis())
    }
    
    private fun determinePrivacyLevel(removedItems: List<String>): PrivacyLevel {
        return when {
            removedItems.any { it.contains("SSN") || it.contains("CREDIT_CARD") } -> PrivacyLevel.RESTRICTED
            removedItems.any { it.contains("EMAIL") || it.contains("PHONE") } -> PrivacyLevel.CONFIDENTIAL
            removedItems.isNotEmpty() -> PrivacyLevel.INTERNAL
            else -> PrivacyLevel.PUBLIC
        }
    }
    
    private fun extractTopicsFromGemini(response: GeminiResponse): List<String> {
        return try {
            @Suppress("UNCHECKED_CAST")
            (response.analysis["topics"] as? List<String>) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun extractRelationshipsFromGemini(response: GeminiResponse): List<Relationship> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val relationships = response.analysis["relationships"] as? List<Map<String, Any>> ?: emptyList()
            relationships.mapNotNull { relationshipMap ->
                try {
                    Relationship(
                        source = relationshipMap["source"] as String,
                        target = relationshipMap["target"] as String,
                        type = relationshipMap["type"] as String,
                        confidence = (relationshipMap["confidence"] as? Number)?.toFloat() ?: 0.5f
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun extractSentimentFromGemini(response: GeminiResponse): Sentiment {
        return try {
            @Suppress("UNCHECKED_CAST")
            val sentiment = response.analysis["sentiment"] as? Map<String, Any>
            if (sentiment != null) {
                Sentiment(
                    polarity = (sentiment["polarity"] as? Number)?.toFloat() ?: 0.0f,
                    magnitude = (sentiment["magnitude"] as? Number)?.toFloat() ?: 0.0f,
                    confidence = (sentiment["confidence"] as? Number)?.toFloat() ?: 0.5f
                )
            } else {
                Sentiment(0.0f, 0.0f, 0.0f)
            }
        } catch (e: Exception) {
            Sentiment(0.0f, 0.0f, 0.0f)
        }
    }
    
    private fun calculateImportance(response: GeminiResponse): Float {
        return try {
            (response.analysis["importance_score"] as? Number)?.toFloat() ?: 0.5f
        } catch (e: Exception) {
            0.5f
        }
    }
    
    private fun extractKeyPoints(summary: String): List<String> {
        // Simple key point extraction - split by sentences and take important ones
        return summary.split(". ")
            .filter { it.length > 20 }
            .take(5)
    }
    
    private fun prioritizeHandoffData(
        availableData: List<String>,
        predictions: List<PredictedAction>,
        bandwidth: Long,
        userPreferences: Map<String, String>
    ): List<PrioritizedItem> {
        return availableData.map { data ->
            val priority = calculateDataPriority(data, predictions, userPreferences)
            val size = estimateDataSize(data)
            val reasoning = generatePriorityReasoning(data, predictions)
            
            PrioritizedItem(
                data = data,
                priority = priority,
                size = size,
                reasoning = reasoning
            )
        }.sortedByDescending { it.priority }
    }
    
    private fun calculateDataPriority(
        data: String,
        predictions: List<PredictedAction>,
        userPreferences: Map<String, String>
    ): Float {
        var priority = 0.5f
        
        // Increase priority if data matches predicted actions
        predictions.forEach { prediction ->
            if (data.contains(prediction.action, ignoreCase = true)) {
                priority += prediction.confidence * 0.3f
            }
        }
        
        // Adjust based on user preferences
        userPreferences.forEach { (key, value) ->
            if (data.contains(key, ignoreCase = true)) {
                priority += when (value.lowercase()) {
                    "high" -> 0.3f
                    "medium" -> 0.1f
                    else -> 0.0f
                }
            }
        }
        
        return kotlin.math.min(priority, 1.0f)
    }
    
    private fun estimateDataSize(data: String): Long {
        // Simple size estimation - could be more sophisticated
        return data.length.toLong() * 2 // Assume some overhead
    }
    
    private fun estimateTransferTime(size: Long, bandwidth: Long): Long {
        return if (bandwidth > 0) size / bandwidth else Long.MAX_VALUE
    }
    
    private fun generateHandoffReasoning(
        prioritizedData: List<PrioritizedItem>,
        predictions: List<PredictedAction>,
        context: HandoffContext
    ): String {
        val topPrediction = predictions.firstOrNull()
        val highPriorityCount = prioritizedData.count { it.priority > 0.7f }
        
        return buildString {
            append("Handoff optimization based on ")
            if (topPrediction != null) {
                append("predicted action: ${topPrediction.action} (${(topPrediction.confidence * 100).toInt()}% confidence). ")
            }
            append("Prioritized $highPriorityCount high-priority items. ")
            append("Estimated transfer time: ${context.bandwidth / 1000}s for ${prioritizedData.sumOf { it.size } / 1024}KB.")
        }
    }
    
    private fun generatePriorityReasoning(data: String, predictions: List<PredictedAction>): String {
        val matchingPrediction = predictions.find { data.contains(it.action, ignoreCase = true) }
        return if (matchingPrediction != null) {
            "Matches predicted action: ${matchingPrediction.action}"
        } else {
            "Standard priority based on content analysis"
        }
    }
    
    private fun generateAlternativePlans(
        context: HandoffContext,
        prioritizedData: List<PrioritizedItem>
    ): List<AlternativePlan> {
        val alternatives = mutableListOf<AlternativePlan>()
        
        // High-priority only plan
        val highPriorityData = prioritizedData.filter { it.priority > 0.7f }
        if (highPriorityData.isNotEmpty()) {
            alternatives.add(
                AlternativePlan(
                    description = "Transfer only high-priority data",
                    estimatedSize = highPriorityData.sumOf { it.size },
                    tradeoffs = listOf("Faster transfer", "May miss some relevant data")
                )
            )
        }
        
        // Compressed plan
        val compressedSize = prioritizedData.sumOf { it.size } / 2 // Assume 50% compression
        alternatives.add(
            AlternativePlan(
                description = "Compress all data before transfer",
                estimatedSize = compressedSize,
                tradeoffs = listOf("Smaller transfer size", "Additional processing time")
            )
        )
        
        return alternatives
    }
}

/**
 * Helper classes for AI system implementation
 */
@Serializable
private data class ProcessingTask(
    val type: String,
    val startTime: Long
)

private class PerformanceTracker {
    var localProcessingCount: Long = 0
    var piiDetectedCount: Int = 0
    var piiSanitizedCount: Int = 0
    val confidenceScores = mutableListOf<Float>()
    val privacyViolations = mutableListOf<String>()
    
    fun recordProcessing(processingTime: Long) {
        localProcessingCount++
    }
}