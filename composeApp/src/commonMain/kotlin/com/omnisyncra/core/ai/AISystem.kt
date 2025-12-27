package com.omnisyncra.core.ai

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Core AI system interface for privacy-first intelligent features
 */
interface AISystem {
    suspend fun initialize(): Boolean
    suspend fun analyzeContext(data: UserData): ContextAnalysis
    suspend fun sanitizeData(data: Any): SanitizedData
    suspend fun generateSummary(content: String): ContentSummary
    suspend fun optimizeHandoff(context: HandoffContext): HandoffPlan
    fun getPrivacyReport(): PrivacyReport
    fun getSystemStatus(): StateFlow<AISystemStatus>
}

/**
 * Gemini API client interface for cloud-based AI processing
 */
interface GeminiAPIClient {
    suspend fun analyzeContent(
        sanitizedContent: String,
        analysisType: AnalysisType,
        privacyLevel: PrivacyLevel
    ): GeminiResponse
    
    suspend fun generateSummary(
        content: String,
        summaryType: SummaryType,
        maxLength: Int
    ): String
    
    suspend fun extractEntities(
        text: String,
        entityTypes: Set<EntityType>
    ): List<Entity>
    
    suspend fun classifyContent(
        content: String,
        categories: List<String>
    ): ContentClassification
    
    fun getUsageStats(): APIUsageStats
}

/**
 * Privacy-first data sanitizer interface
 */
interface DataSanitizer {
    suspend fun sanitizeText(text: String): SanitizedText
    suspend fun sanitizeCode(code: String, language: String): SanitizedCode
    suspend fun sanitizeStructuredData(data: Map<String, Any>): SanitizedData
    suspend fun detectSensitiveInfo(content: String): List<SensitiveInfo>
    fun getConfidenceScore(): Float
}

/**
 * Context analyzer for intelligent context understanding
 */
interface ContextAnalyzer {
    suspend fun buildContextGraph(activities: List<UserActivity>): ContextGraph
    suspend fun analyzeCurrentContext(): CurrentContext
    suspend fun predictNextActions(context: CurrentContext): List<PredictedAction>
    suspend fun updateContextGraph(newActivity: UserActivity)
    suspend fun getRelevantContext(query: String): List<ContextItem>
}

/**
 * Local AI model interface for on-device processing
 */
interface LocalAIModel {
    suspend fun loadModel(modelPath: String): Boolean
    suspend fun processLocally(input: String): LocalProcessingResult
    suspend fun isModelAvailable(): Boolean
    fun getModelCapabilities(): Set<AICapability>
    suspend fun unloadModel()
}

/**
 * User data structure for AI processing
 */
@Serializable
data class UserData(
    val content: String,
    val contentType: ContentType,
    val timestamp: Long,
    val source: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Sanitized data with privacy guarantees
 */
@Serializable
data class SanitizedData(
    val sanitizedContent: String,
    val originalHash: String,
    val sanitizationReport: SanitizationReport,
    val confidenceScore: Float,
    val privacyLevel: PrivacyLevel
)

/**
 * Context analysis result
 */
@Serializable
data class ContextAnalysis(
    val mainTopics: List<String>,
    val entities: List<Entity>,
    val relationships: List<Relationship>,
    val sentiment: Sentiment,
    val importance: Float,
    val contextGraph: ContextGraph
)

/**
 * Content summary with metadata
 */
@Serializable
data class ContentSummary(
    val summary: String,
    val keyPoints: List<String>,
    val entities: List<Entity>,
    val confidence: Float,
    val originalLength: Int,
    val summaryLength: Int,
    val sources: List<String>
)

/**
 * Handoff optimization plan
 */
@Serializable
data class HandoffPlan(
    val prioritizedData: List<PrioritizedItem>,
    val estimatedSize: Long,
    val transferTime: Long,
    val reasoning: String,
    val alternatives: List<AlternativePlan>
)

/**
 * Privacy report for transparency
 */
@Serializable
data class PrivacyReport(
    val dataProcessedLocally: Long,
    val dataSentToAPI: Long,
    val piiDetected: Int,
    val piiSanitized: Int,
    val confidenceScores: List<Float>,
    val privacyViolations: List<String>
)

/**
 * AI system status
 */
@Serializable
data class AISystemStatus(
    val isInitialized: Boolean,
    val modelsLoaded: Set<String>,
    val apiConnected: Boolean,
    val processingQueue: Int,
    val lastActivity: Long,
    val performanceMetrics: PerformanceMetrics
)

/**
 * Context graph for relationship mapping
 */
@Serializable
data class ContextGraph(
    val nodes: List<ContextNode>,
    val edges: List<ContextEdge>,
    val clusters: List<ContextCluster>,
    val temporalSequence: List<TemporalEvent>
)

/**
 * Sanitization report for transparency
 */
@Serializable
data class SanitizationReport(
    val itemsRemoved: List<SanitizedItem>,
    val itemsAnonymized: List<SanitizedItem>,
    val confidenceScore: Float,
    val processingTime: Long
)

/**
 * Content types supported by AI system
 */
enum class ContentType {
    TEXT, CODE, DOCUMENT, EMAIL, CHAT, STRUCTURED_DATA, MEDIA_METADATA
}

/**
 * Privacy levels for content classification
 */
enum class PrivacyLevel {
    PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED
}

/**
 * Analysis types for Gemini API
 */
enum class AnalysisType {
    CONTEXT_EXTRACTION, ENTITY_RECOGNITION, SENTIMENT_ANALYSIS, 
    TOPIC_MODELING, RELATIONSHIP_MAPPING, INTENT_DETECTION
}

/**
 * Summary types for different use cases
 */
enum class SummaryType {
    BRIEF, DETAILED, TECHNICAL, EXECUTIVE, CONTEXTUAL
}

/**
 * Entity types for extraction
 */
enum class EntityType {
    PERSON, ORGANIZATION, LOCATION, DATE, TECHNOLOGY, CONCEPT, PROJECT
}

/**
 * AI capabilities for model management
 */
enum class AICapability {
    TEXT_ANALYSIS, CODE_ANALYSIS, ENTITY_EXTRACTION, 
    SUMMARIZATION, TRANSLATION, SENTIMENT_ANALYSIS
}

/**
 * Supporting data classes
 */
@Serializable
data class Entity(
    val text: String,
    val type: EntityType,
    val confidence: Float,
    val startIndex: Int,
    val endIndex: Int
)

@Serializable
data class Relationship(
    val source: String,
    val target: String,
    val type: String,
    val confidence: Float
)

@Serializable
data class Sentiment(
    val polarity: Float, // -1.0 to 1.0
    val magnitude: Float, // 0.0 to 1.0
    val confidence: Float
)

@Serializable
data class PrioritizedItem(
    val data: String,
    val priority: Float,
    val size: Long,
    val reasoning: String
)

@Serializable
data class AlternativePlan(
    val description: String,
    val estimatedSize: Long,
    val tradeoffs: List<String>
)

@Serializable
data class ContextNode(
    val id: String,
    val type: String,
    val content: String,
    val timestamp: Long,
    val importance: Float
)

@Serializable
data class ContextEdge(
    val sourceId: String,
    val targetId: String,
    val relationshipType: String,
    val strength: Float
)

@Serializable
data class ContextCluster(
    val id: String,
    val nodeIds: List<String>,
    val theme: String,
    val coherence: Float
)

@Serializable
data class TemporalEvent(
    val timestamp: Long,
    val nodeId: String,
    val eventType: String
)

@Serializable
data class SanitizedItem(
    val originalText: String,
    val replacementText: String,
    val type: String,
    val confidence: Float
)

@Serializable
data class PerformanceMetrics(
    val averageProcessingTime: Long,
    val memoryUsage: Long,
    val apiCallsPerMinute: Int,
    val cacheHitRate: Float
)

@Serializable
data class UserActivity(
    val id: String,
    val type: String,
    val content: String,
    val timestamp: Long,
    val context: Map<String, String>
)

@Serializable
data class CurrentContext(
    val activeTask: String,
    val recentActivities: List<UserActivity>,
    val relevantEntities: List<Entity>,
    val contextScore: Float
)

@Serializable
data class PredictedAction(
    val action: String,
    val confidence: Float,
    val reasoning: String,
    val suggestedData: List<String>
)

@Serializable
data class ContextItem(
    val content: String,
    val relevanceScore: Float,
    val source: String,
    val timestamp: Long
)

@Serializable
data class LocalProcessingResult(
    val result: String,
    val confidence: Float,
    val processingTime: Long,
    val modelUsed: String
)

@Serializable
data class SensitiveInfo(
    val text: String,
    val type: String,
    val startIndex: Int,
    val endIndex: Int,
    val confidence: Float
)

@Serializable
data class SanitizedText(
    val content: String,
    val originalHash: String,
    val removedItems: List<String>,
    val confidence: Float
)

@Serializable
data class SanitizedCode(
    val code: String,
    val language: String,
    val removedItems: List<String>,
    val preservedFunctionality: Boolean
)

@Serializable
data class HandoffContext(
    val sourceDevice: String,
    val targetDevice: String,
    val currentTask: String,
    val availableData: List<String>,
    val bandwidth: Long,
    val userPreferences: Map<String, String>
)

@Serializable
data class GeminiResponse(
    val analysis: Map<String, Any>,
    val confidence: Float,
    val processingTime: Long,
    val tokensUsed: Int,
    val requestId: String
)

@Serializable
data class ContentClassification(
    val categories: List<CategoryScore>,
    val confidence: Float
)

@Serializable
data class CategoryScore(
    val category: String,
    val score: Float
)

@Serializable
data class APIUsageStats(
    val requestsToday: Int,
    val tokensUsedToday: Int,
    val quotaRemaining: Int,
    val averageResponseTime: Long
)