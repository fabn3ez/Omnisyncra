package com.omnisyncra.core.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Gemini API configuration
 */
@Serializable
data class GeminiAPIConfig(
    val apiKey: String,
    val endpoint: String = "https://generativelanguage.googleapis.com/v1beta",
    val model: String = "gemini-pro",
    val maxTokens: Int = 8192,
    val temperature: Float = 0.1f,
    val rateLimitPerMinute: Int = 60
)

/**
 * Gemini API request structure
 */
@Serializable
data class GeminiAPIRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
    val safetySettings: List<SafetySetting>
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float,
    val maxOutputTokens: Int,
    val topP: Float,
    val topK: Int
)

@Serializable
data class SafetySetting(
    val category: String,
    val threshold: String
)

/**
 * Gemini API response structure
 */
@Serializable
data class GeminiAPIResponse(
    val candidates: List<Candidate>,
    val usageMetadata: UsageMetadata? = null
)

@Serializable
data class Candidate(
    val content: Content,
    val finishReason: String? = null,
    val safetyRatings: List<SafetyRating>? = null
)

@Serializable
data class SafetyRating(
    val category: String,
    val probability: String
)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int,
    val candidatesTokenCount: Int,
    val totalTokenCount: Int
)

/**
 * Rate limiter for API calls
 */
class RateLimiter(private val maxRequestsPerMinute: Int) {
    private val requestTimes = mutableListOf<Long>()
    
    suspend fun acquire() {
        val now = System.currentTimeMillis()
        
        // Remove requests older than 1 minute
        requestTimes.removeAll { it < now - 60000 }
        
        // If we're at the limit, wait
        if (requestTimes.size >= maxRequestsPerMinute) {
            val oldestRequest = requestTimes.first()
            val waitTime = 60000 - (now - oldestRequest)
            if (waitTime > 0) {
                delay(waitTime)
            }
        }
        
        requestTimes.add(now)
    }
}

/**
 * Omnisyncra Gemini API client implementation
 */
class OmnisyncraGeminiClient(
    private val config: GeminiAPIConfig,
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : GeminiAPIClient {
    
    private val rateLimiter = RateLimiter(config.rateLimitPerMinute)
    private var usageStats = APIUsageStats(0, 0, config.rateLimitPerMinute, 0)
    
    override suspend fun analyzeContent(
        sanitizedContent: String,
        analysisType: AnalysisType,
        privacyLevel: PrivacyLevel
    ): GeminiResponse {
        rateLimiter.acquire()
        
        val prompt = buildAnalysisPrompt(sanitizedContent, analysisType, privacyLevel)
        val request = buildGeminiRequest(prompt, privacyLevel)
        
        return withRetry(maxAttempts = 3) {
            val startTime = System.currentTimeMillis()
            
            val response = httpClient.post("${config.endpoint}/models/${config.model}:generateContent") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GeminiAPIRequest.serializer(), request))
                header("x-goog-api-key", config.apiKey)
            }
            
            val responseBody = response.body<String>()
            val geminiResponse = json.decodeFromString(GeminiAPIResponse.serializer(), responseBody)
            
            val processingTime = System.currentTimeMillis() - startTime
            updateUsageStats(geminiResponse.usageMetadata?.totalTokenCount ?: 0, processingTime)
            
            parseGeminiResponse(geminiResponse, analysisType, processingTime)
        }
    }
    
    override suspend fun generateSummary(
        content: String,
        summaryType: SummaryType,
        maxLength: Int
    ): String {
        rateLimiter.acquire()
        
        val prompt = buildSummaryPrompt(content, summaryType, maxLength)
        val request = buildGeminiRequest(prompt, PrivacyLevel.INTERNAL)
        
        return withRetry(maxAttempts = 3) {
            val response = httpClient.post("${config.endpoint}/models/${config.model}:generateContent") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GeminiAPIRequest.serializer(), request))
                header("x-goog-api-key", config.apiKey)
            }
            
            val responseBody = response.body<String>()
            val geminiResponse = json.decodeFromString(GeminiAPIResponse.serializer(), responseBody)
            
            updateUsageStats(geminiResponse.usageMetadata?.totalTokenCount ?: 0, 0)
            
            geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Summary generation failed"
        }
    }
    
    override suspend fun extractEntities(
        text: String,
        entityTypes: Set<EntityType>
    ): List<Entity> {
        rateLimiter.acquire()
        
        val prompt = buildEntityExtractionPrompt(text, entityTypes)
        val request = buildGeminiRequest(prompt, PrivacyLevel.INTERNAL)
        
        return withRetry(maxAttempts = 3) {
            val response = httpClient.post("${config.endpoint}/models/${config.model}:generateContent") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GeminiAPIRequest.serializer(), request))
                header("x-goog-api-key", config.apiKey)
            }
            
            val responseBody = response.body<String>()
            val geminiResponse = json.decodeFromString(GeminiAPIResponse.serializer(), responseBody)
            
            updateUsageStats(geminiResponse.usageMetadata?.totalTokenCount ?: 0, 0)
            
            parseEntitiesFromResponse(geminiResponse)
        }
    }
    
    override suspend fun classifyContent(
        content: String,
        categories: List<String>
    ): ContentClassification {
        rateLimiter.acquire()
        
        val prompt = buildClassificationPrompt(content, categories)
        val request = buildGeminiRequest(prompt, PrivacyLevel.INTERNAL)
        
        return withRetry(maxAttempts = 3) {
            val response = httpClient.post("${config.endpoint}/models/${config.model}:generateContent") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GeminiAPIRequest.serializer(), request))
                header("x-goog-api-key", config.apiKey)
            }
            
            val responseBody = response.body<String>()
            val geminiResponse = json.decodeFromString(GeminiAPIResponse.serializer(), responseBody)
            
            updateUsageStats(geminiResponse.usageMetadata?.totalTokenCount ?: 0, 0)
            
            parseClassificationFromResponse(geminiResponse)
        }
    }
    
    override fun getUsageStats(): APIUsageStats = usageStats
    
    private fun buildAnalysisPrompt(
        content: String,
        analysisType: AnalysisType,
        privacyLevel: PrivacyLevel
    ): String {
        return when (analysisType) {
            AnalysisType.CONTEXT_EXTRACTION -> """
                Analyze the following content and extract key context information.
                Focus on: main topics, important entities, relationships, and semantic meaning.
                Privacy Level: $privacyLevel - ensure no sensitive information is exposed in analysis.
                
                Content: $content
                
                Provide response in JSON format with:
                {
                  "topics": ["topic1", "topic2"],
                  "entities": [{"text": "entity", "type": "PERSON", "confidence": 0.9}],
                  "relationships": [{"source": "A", "target": "B", "type": "related_to"}],
                  "importance_score": 0.8
                }
            """.trimIndent()
            
            AnalysisType.ENTITY_RECOGNITION -> """
                Extract and classify entities from the following content.
                Include: people, organizations, locations, dates, technologies, concepts.
                Privacy Level: $privacyLevel
                
                Content: $content
                
                Provide response in JSON format with entity arrays by type.
            """.trimIndent()
            
            AnalysisType.TOPIC_MODELING -> """
                Identify main topics and themes in the following content.
                Provide topic hierarchy and relevance scores.
                Privacy Level: $privacyLevel
                
                Content: $content
                
                Provide response in JSON format with topics and confidence scores.
            """.trimIndent()
            
            AnalysisType.SENTIMENT_ANALYSIS -> """
                Analyze the sentiment of the following content.
                Provide polarity (-1 to 1), magnitude (0 to 1), and confidence.
                Privacy Level: $privacyLevel
                
                Content: $content
                
                Provide response in JSON format: {"polarity": 0.5, "magnitude": 0.8, "confidence": 0.9}
            """.trimIndent()
            
            AnalysisType.RELATIONSHIP_MAPPING -> """
                Map relationships between entities and concepts in the following content.
                Focus on semantic and logical connections.
                Privacy Level: $privacyLevel
                
                Content: $content
                
                Provide response in JSON format with relationship mappings.
            """.trimIndent()
            
            AnalysisType.INTENT_DETECTION -> """
                Detect the intent and purpose of the following content.
                Identify what the user is trying to accomplish.
                Privacy Level: $privacyLevel
                
                Content: $content
                
                Provide response in JSON format with detected intents and confidence scores.
            """.trimIndent()
        }
    }
    
    private fun buildSummaryPrompt(
        content: String,
        summaryType: SummaryType,
        maxLength: Int
    ): String {
        val typeDescription = when (summaryType) {
            SummaryType.BRIEF -> "Create a brief, concise summary"
            SummaryType.DETAILED -> "Create a detailed, comprehensive summary"
            SummaryType.TECHNICAL -> "Create a technical summary focusing on implementation details"
            SummaryType.EXECUTIVE -> "Create an executive summary for leadership"
            SummaryType.CONTEXTUAL -> "Create a contextual summary preserving relationships"
        }
        
        return """
            $typeDescription of the following content.
            Maximum length: $maxLength characters.
            Preserve key information and maintain clarity.
            
            Content: $content
            
            Provide only the summary text, no additional formatting.
        """.trimIndent()
    }
    
    private fun buildEntityExtractionPrompt(
        text: String,
        entityTypes: Set<EntityType>
    ): String {
        val types = entityTypes.joinToString(", ")
        
        return """
            Extract entities of the following types from the text: $types
            
            Text: $text
            
            Provide response in JSON format:
            {
              "entities": [
                {"text": "entity_text", "type": "PERSON", "confidence": 0.9, "start": 0, "end": 10}
              ]
            }
        """.trimIndent()
    }
    
    private fun buildClassificationPrompt(
        content: String,
        categories: List<String>
    ): String {
        val categoryList = categories.joinToString(", ")
        
        return """
            Classify the following content into these categories: $categoryList
            Provide confidence scores for each category.
            
            Content: $content
            
            Provide response in JSON format:
            {
              "categories": [
                {"category": "category_name", "score": 0.8}
              ],
              "confidence": 0.9
            }
        """.trimIndent()
    }
    
    private fun buildGeminiRequest(
        prompt: String,
        privacyLevel: PrivacyLevel
    ): GeminiAPIRequest {
        return GeminiAPIRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                temperature = config.temperature,
                maxOutputTokens = config.maxTokens,
                topP = 0.8f,
                topK = 40
            ),
            safetySettings = buildSafetySettings(privacyLevel)
        )
    }
    
    private fun buildSafetySettings(privacyLevel: PrivacyLevel): List<SafetySetting> {
        val threshold = when (privacyLevel) {
            PrivacyLevel.PUBLIC -> "BLOCK_MEDIUM_AND_ABOVE"
            PrivacyLevel.INTERNAL -> "BLOCK_ONLY_HIGH"
            PrivacyLevel.CONFIDENTIAL -> "BLOCK_LOW_AND_ABOVE"
            PrivacyLevel.RESTRICTED -> "BLOCK_LOW_AND_ABOVE"
        }
        
        return listOf(
            SafetySetting("HARM_CATEGORY_HARASSMENT", threshold),
            SafetySetting("HARM_CATEGORY_HATE_SPEECH", threshold),
            SafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", threshold),
            SafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", threshold)
        )
    }
    
    private fun parseGeminiResponse(
        response: GeminiAPIResponse,
        analysisType: AnalysisType,
        processingTime: Long
    ): GeminiResponse {
        val candidate = response.candidates.firstOrNull()
        val content = candidate?.content?.parts?.firstOrNull()?.text ?: ""
        
        // Parse JSON response based on analysis type
        val analysis = try {
            json.decodeFromString<Map<String, Any>>(content)
        } catch (e: Exception) {
            mapOf("raw_response" to content, "parse_error" to e.message)
        }
        
        return GeminiResponse(
            analysis = analysis,
            confidence = 0.8f, // Default confidence, could be extracted from response
            processingTime = processingTime,
            tokensUsed = response.usageMetadata?.totalTokenCount ?: 0,
            requestId = generateRequestId()
        )
    }
    
    private fun parseEntitiesFromResponse(response: GeminiAPIResponse): List<Entity> {
        val candidate = response.candidates.firstOrNull()
        val content = candidate?.content?.parts?.firstOrNull()?.text ?: ""
        
        return try {
            val parsed = json.decodeFromString<Map<String, List<Map<String, Any>>>>(content)
            val entities = parsed["entities"] ?: emptyList()
            
            entities.mapNotNull { entityMap ->
                try {
                    Entity(
                        text = entityMap["text"] as String,
                        type = EntityType.valueOf(entityMap["type"] as String),
                        confidence = (entityMap["confidence"] as Number).toFloat(),
                        startIndex = (entityMap["start"] as Number).toInt(),
                        endIndex = (entityMap["end"] as Number).toInt()
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseClassificationFromResponse(response: GeminiAPIResponse): ContentClassification {
        val candidate = response.candidates.firstOrNull()
        val content = candidate?.content?.parts?.firstOrNull()?.text ?: ""
        
        return try {
            val parsed = json.decodeFromString<Map<String, Any>>(content)
            val categories = (parsed["categories"] as List<Map<String, Any>>).map { categoryMap ->
                CategoryScore(
                    category = categoryMap["category"] as String,
                    score = (categoryMap["score"] as Number).toFloat()
                )
            }
            
            ContentClassification(
                categories = categories,
                confidence = (parsed["confidence"] as Number).toFloat()
            )
        } catch (e: Exception) {
            ContentClassification(
                categories = emptyList(),
                confidence = 0.0f
            )
        }
    }
    
    private fun updateUsageStats(tokensUsed: Int, processingTime: Long) {
        usageStats = usageStats.copy(
            requestsToday = usageStats.requestsToday + 1,
            tokensUsedToday = usageStats.tokensUsedToday + tokensUsed,
            quotaRemaining = maxOf(0, usageStats.quotaRemaining - 1),
            averageResponseTime = if (usageStats.requestsToday == 0) processingTime 
                else (usageStats.averageResponseTime + processingTime) / 2
        )
    }
    
    private fun generateRequestId(): String {
        return "req_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }
}

/**
 * Retry utility with exponential backoff
 */
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    baseDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = baseDelay
    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            delay(currentDelay)
            currentDelay = min(maxDelay, (currentDelay * factor).toLong())
        }
    }
    return block() // Last attempt without catch
}