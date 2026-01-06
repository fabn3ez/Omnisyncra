package com.omnisyncra.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.omnisyncra.core.platform.TimeUtils

/**
 * Core AI System Interface
 * Privacy-first AI integration with local processing and intelligent context analysis
 */
interface AISystem {
    /**
     * Current AI system status
     */
    val status: StateFlow<AIStatus>
    
    /**
     * Initialize the AI system
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * Analyze context and generate insights
     */
    suspend fun analyzeContext(context: AIContext): Result<ContextAnalysis>
    
    /**
     * Sanitize data for privacy protection
     */
    suspend fun sanitizeData(data: String): Result<SanitizedData>
    
    /**
     * Generate intelligent summaries
     */
    suspend fun generateSummary(content: String, type: ContentType): Result<ContentSummary>
    
    /**
     * Optimize handoff data selection
     */
    suspend fun optimizeHandoff(context: AIContext, availableData: List<HandoffData>): Result<List<HandoffData>>
    
    /**
     * Shutdown the AI system
     */
    suspend fun shutdown()
}

/**
 * AI System Status
 */
enum class AIStatus {
    INITIALIZING,
    READY,
    PROCESSING,
    ERROR,
    SHUTDOWN
}

/**
 * AI Context for analysis
 */
data class AIContext(
    val currentActivity: String,
    val recentActivities: List<String>,
    val deviceContext: DeviceContext,
    val timestamp: Long = TimeUtils.currentTimeMillis()
)

/**
 * Device context information
 */
data class DeviceContext(
    val deviceType: String,
    val capabilities: List<String>,
    val batteryLevel: Float? = null,
    val networkType: String? = null
)

/**
 * Context analysis result
 */
data class ContextAnalysis(
    val relevantTopics: List<String>,
    val suggestedActions: List<String>,
    val confidenceScore: Float,
    val processingTimeMs: Long
)

/**
 * Sanitized data result
 */
data class SanitizedData(
    val sanitizedContent: String,
    val detectedPII: List<PIIDetection>,
    val confidenceScore: Float
)

/**
 * PII Detection result
 */
data class PIIDetection(
    val type: PIIType,
    val location: IntRange,
    val confidence: Float,
    val replacement: String
)

/**
 * Types of PII that can be detected
 */
enum class PIIType {
    EMAIL,
    PHONE_NUMBER,
    SSN,
    CREDIT_CARD,
    IP_ADDRESS,
    PERSON_NAME,
    ADDRESS
}

/**
 * Content types for summarization
 */
enum class ContentType {
    TEXT_DOCUMENT,
    CODE_FILE,
    EMAIL,
    CHAT_MESSAGE,
    WEB_PAGE,
    STRUCTURED_DATA
}

/**
 * Content summary result
 */
data class ContentSummary(
    val summary: String,
    val keyPoints: List<String>,
    val contentType: ContentType,
    val originalLength: Int,
    val summaryLength: Int
)

/**
 * Handoff data for optimization
 */
data class HandoffData(
    val id: String,
    val type: String,
    val content: String,
    val size: Long,
    val priority: Float = 0.5f,
    val metadata: Map<String, String> = emptyMap()
)