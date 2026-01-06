package com.omnisyncra.core.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.omnisyncra.core.platform.TimeUtils

/**
 * Gemini API Client for semantic analysis
 * Handles API communication with privacy controls and rate limiting
 */
class GeminiAPIClient(
    private val apiKey: String,
    private val httpClient: HttpClient
) {
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"
    private val json = Json { ignoreUnknownKeys = true }
    
    // Rate limiting
    private var lastRequestTime = 0L
    private val minRequestInterval = 100L // 100ms between requests
    private var requestCount = 0
    private var requestWindowStart = 0L
    private val maxRequestsPerMinute = 60
    
    /**
     * Analyze content semantically
     */
    suspend fun analyzeContent(content: String, contentType: ContentType): Result<SemanticAnalysis> {
        return try {
            // Rate limiting
            enforceRateLimit()
            
            val prompt = buildAnalysisPrompt(content, contentType)
            val response = makeRequest(prompt)
            
            val analysis = parseAnalysisResponse(response, contentType)
            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Generate content summary
     */
    suspend fun generateSummary(content: String, contentType: ContentType, maxLength: Int = 200): Result<ContentSummary> {
        return try {
            enforceRateLimit()
            
            val prompt = buildSummaryPrompt(content, contentType, maxLength)
            val response = makeRequest(prompt)
            
            val summary = parseSummaryResponse(response, content, contentType)
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Extract key topics from content
     */
    suspend fun extractTopics(content: String): Result<List<String>> {
        return try {
            enforceRateLimit()
            
            val prompt = buildTopicExtractionPrompt(content)
            val response = makeRequest(prompt)
            
            val topics = parseTopicsResponse(response)
            Result.success(topics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Analyze communication intent
     */
    suspend fun analyzeIntent(content: String): Result<IntentAnalysis> {
        return try {
            enforceRateLimit()
            
            val prompt = buildIntentPrompt(content)
            val response = makeRequest(prompt)
            
            val intent = parseIntentResponse(response)
            Result.success(intent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun enforceRateLimit() {
        val currentTime = TimeUtils.currentTimeMillis()
        
        // Enforce minimum interval between requests
        val timeSinceLastRequest = currentTime - lastRequestTime
        if (timeSinceLastRequest < minRequestInterval) {
            delay(minRequestInterval - timeSinceLastRequest)
        }
        
        // Enforce requests per minute limit
        if (currentTime - requestWindowStart > 60000) {
            // Reset window
            requestWindowStart = currentTime
            requestCount = 0
        }
        
        if (requestCount >= maxRequestsPerMinute) {
            val waitTime = 60000 - (currentTime - requestWindowStart)
            if (waitTime > 0) {
                delay(waitTime)
                requestWindowStart = TimeUtils.currentTimeMillis()
                requestCount = 0
            }
        }
        
        lastRequestTime = TimeUtils.currentTimeMillis()
        requestCount++
    }
    
    private suspend fun makeRequest(prompt: String): String {
        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                topK = 1,
                topP = 1f,
                maxOutputTokens = 1024
            )
        )
        
        val response: HttpResponse = httpClient.post("$baseUrl/models/gemini-pro:generateContent") {
            header("Content-Type", "application/json")
            parameter("key", apiKey)
            setBody(request)
        }
        
        if (response.status != HttpStatusCode.OK) {
            throw Exception("API request failed: ${response.status} - ${response.bodyAsText()}")
        }
        
        val responseBody = response.body<GeminiResponse>()
        return responseBody.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response content received")
    }
    
    private fun buildAnalysisPrompt(content: String, contentType: ContentType): String {
        return when (contentType) {
            ContentType.CODE_FILE -> """
                Analyze this code and provide:
                1. Main functionality and purpose
                2. Key programming concepts used
                3. Potential improvements or issues
                4. Dependencies and relationships
                
                Code:
                $content
                
                Respond in JSON format with fields: functionality, concepts, improvements, dependencies
            """.trimIndent()
            
            ContentType.TEXT_DOCUMENT -> """
                Analyze this document and provide:
                1. Main topics and themes
                2. Key information and facts
                3. Document structure and organization
                4. Relevant entities mentioned
                
                Document:
                $content
                
                Respond in JSON format with fields: topics, keyInfo, structure, entities
            """.trimIndent()
            
            ContentType.EMAIL -> """
                Analyze this email and provide:
                1. Communication intent and purpose
                2. Action items or requests
                3. Urgency level
                4. Key participants and relationships
                
                Email:
                $content
                
                Respond in JSON format with fields: intent, actionItems, urgency, participants
            """.trimIndent()
            
            else -> """
                Analyze this content and provide:
                1. Main topics and themes
                2. Key information
                3. Content type and structure
                4. Important entities or concepts
                
                Content:
                $content
                
                Respond in JSON format with fields: topics, keyInfo, contentType, entities
            """.trimIndent()
        }
    }
    
    private fun buildSummaryPrompt(content: String, contentType: ContentType, maxLength: Int): String {
        val typeSpecific = when (contentType) {
            ContentType.CODE_FILE -> "Focus on functionality, not implementation details."
            ContentType.EMAIL -> "Focus on key points, action items, and decisions."
            ContentType.TEXT_DOCUMENT -> "Focus on main arguments and conclusions."
            else -> "Focus on the most important information."
        }
        
        return """
            Create a concise summary of this content in maximum $maxLength characters.
            $typeSpecific
            
            Content:
            $content
            
            Summary:
        """.trimIndent()
    }
    
    private fun buildTopicExtractionPrompt(content: String): String {
        return """
            Extract the main topics and themes from this content.
            Return only the topics as a comma-separated list, no explanations.
            Maximum 10 topics, each 1-3 words.
            
            Content:
            $content
            
            Topics:
        """.trimIndent()
    }
    
    private fun buildIntentPrompt(content: String): String {
        return """
            Analyze the communication intent of this content.
            Classify the intent as one of: REQUEST, INFORM, QUESTION, COMMAND, OFFER, DECLINE
            Also provide confidence level (0.0-1.0) and brief reasoning.
            
            Content:
            $content
            
            Respond in JSON format with fields: intent, confidence, reasoning
        """.trimIndent()
    }
    
    private fun parseAnalysisResponse(response: String, contentType: ContentType): SemanticAnalysis {
        return try {
            // Try to parse as JSON first
            val jsonResponse = json.decodeFromString<Map<String, String>>(response)
            
            SemanticAnalysis(
                topics = jsonResponse["topics"]?.split(",")?.map { it.trim() } ?: emptyList(),
                entities = jsonResponse["entities"]?.split(",")?.map { it.trim() } ?: emptyList(),
                concepts = jsonResponse["concepts"]?.split(",")?.map { it.trim() } ?: emptyList(),
                structure = jsonResponse["structure"] ?: "",
                metadata = jsonResponse.filterKeys { it !in listOf("topics", "entities", "concepts", "structure") }
            )
        } catch (e: Exception) {
            // Fallback to simple text parsing
            SemanticAnalysis(
                topics = extractSimpleTopics(response),
                entities = emptyList(),
                concepts = emptyList(),
                structure = "unstructured",
                metadata = mapOf("rawResponse" to response)
            )
        }
    }
    
    private fun parseSummaryResponse(response: String, originalContent: String, contentType: ContentType): ContentSummary {
        val summary = response.trim()
        val keyPoints = extractKeyPoints(summary)
        
        return ContentSummary(
            summary = summary,
            keyPoints = keyPoints,
            contentType = contentType,
            originalLength = originalContent.length,
            summaryLength = summary.length
        )
    }
    
    private fun parseTopicsResponse(response: String): List<String> {
        return response.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(10)
    }
    
    private fun parseIntentResponse(response: String): IntentAnalysis {
        return try {
            val jsonResponse = json.decodeFromString<Map<String, String>>(response)
            
            IntentAnalysis(
                intent = jsonResponse["intent"] ?: "UNKNOWN",
                confidence = jsonResponse["confidence"]?.toFloatOrNull() ?: 0.5f,
                reasoning = jsonResponse["reasoning"] ?: "Unable to determine intent"
            )
        } catch (e: Exception) {
            IntentAnalysis(
                intent = "UNKNOWN",
                confidence = 0.3f,
                reasoning = "Failed to parse intent analysis"
            )
        }
    }
    
    private fun extractSimpleTopics(text: String): List<String> {
        // Simple keyword extraction as fallback
        return text.lowercase()
            .split(Regex("[\\s,.-]+"))
            .filter { it.length > 3 }
            .distinct()
            .take(5)
    }
    
    private fun extractKeyPoints(summary: String): List<String> {
        // Extract sentences as key points
        return summary.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 10 }
            .take(5)
    }
}

// Data classes for API communication
@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig
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
    val topK: Int,
    val topP: Float,
    val maxOutputTokens: Int
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

// Analysis result data classes
data class SemanticAnalysis(
    val topics: List<String>,
    val entities: List<String>,
    val concepts: List<String>,
    val structure: String,
    val metadata: Map<String, String>
)

data class IntentAnalysis(
    val intent: String,
    val confidence: Float,
    val reasoning: String
)