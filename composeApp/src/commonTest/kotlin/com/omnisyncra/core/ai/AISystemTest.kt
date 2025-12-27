package com.omnisyncra.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the AI system components
 */
class AISystemTest {
    
    @Test
    fun testDataSanitizerPIIDetection() = runTest {
        val sanitizer = OmnisyncraDataSanitizer()
        
        // Test email detection
        val textWithEmail = "Contact me at john.doe@example.com for more info"
        val sanitizedEmail = sanitizer.sanitizeText(textWithEmail)
        
        assertTrue(sanitizedEmail.removedItems.isNotEmpty(), "Should detect email")
        assertTrue(sanitizedEmail.content.contains("[EMAIL_REDACTED]"), "Should replace email")
        assertTrue(sanitizedEmail.confidence > 0.0f, "Should have confidence score")
    }
    
    @Test
    fun testDataSanitizerPhoneDetection() = runTest {
        val sanitizer = OmnisyncraDataSanitizer()
        
        // Test phone detection
        val textWithPhone = "Call me at (555) 123-4567 tomorrow"
        val sanitizedPhone = sanitizer.sanitizeText(textWithPhone)
        
        assertTrue(sanitizedPhone.removedItems.isNotEmpty(), "Should detect phone")
        assertTrue(sanitizedPhone.content.contains("[PHONE_REDACTED]"), "Should replace phone")
    }
    
    @Test
    fun testDataSanitizerSSNDetection() = runTest {
        val sanitizer = OmnisyncraDataSanitizer()
        
        // Test SSN detection
        val textWithSSN = "My SSN is 123-45-6789 for verification"
        val sanitizedSSN = sanitizer.sanitizeText(textWithSSN)
        
        assertTrue(sanitizedSSN.removedItems.isNotEmpty(), "Should detect SSN")
        assertTrue(sanitizedSSN.content.contains("[SSN_REDACTED]"), "Should replace SSN")
    }
    
    @Test
    fun testDataSanitizerCleanText() = runTest {
        val sanitizer = OmnisyncraDataSanitizer()
        
        // Test clean text (no PII)
        val cleanText = "This is a normal message without any sensitive information"
        val sanitizedClean = sanitizer.sanitizeText(cleanText)
        
        assertTrue(sanitizedClean.removedItems.isEmpty(), "Should not detect PII in clean text")
        assertEquals(cleanText, sanitizedClean.content, "Should not modify clean text")
        assertEquals(1.0f, sanitizedClean.confidence, "Should have high confidence for clean text")
    }
    
    @Test
    fun testDataSanitizerCodeSanitization() = runTest {
        val sanitizer = OmnisyncraDataSanitizer()
        
        // Test code sanitization
        val kotlinCode = """
            fun sendEmail(email: String) {
                val userEmail = "john.doe@company.com"
                sendTo(userEmail)
            }
        """.trimIndent()
        
        val sanitizedCode = sanitizer.sanitizeCode(kotlinCode, "kotlin")
        
        assertNotNull(sanitizedCode, "Should return sanitized code")
        assertTrue(sanitizedCode.preservedFunctionality, "Should preserve functionality")
        assertTrue(sanitizedCode.code.contains("\"user@example.com\""), "Should replace email in code")
    }
    
    @Test
    fun testContextAnalyzerActivityTracking() = runTest {
        val analyzer = OmnisyncraContextAnalyzer()
        
        // Test activity tracking
        val activity = UserActivity(
            id = "test_activity_1",
            type = "CODE_EDIT",
            content = "Working on AI integration features",
            timestamp = System.currentTimeMillis(),
            context = mapOf("priority" to "high", "project" to "omnisyncra")
        )
        
        analyzer.updateContextGraph(activity)
        val currentContext = analyzer.analyzeCurrentContext()
        
        assertNotNull(currentContext, "Should return current context")
        assertEquals("CODING", currentContext.activeTask, "Should infer coding task")
        assertTrue(currentContext.recentActivities.isNotEmpty(), "Should track recent activities")
        assertTrue(currentContext.contextScore > 0.0f, "Should have context score")
    }
    
    @Test
    fun testContextAnalyzerPatternDetection() = runTest {
        val analyzer = OmnisyncraContextAnalyzer()
        
        // Create a sequence of activities
        val activities = listOf(
            UserActivity("1", "SEARCH", "AI integration patterns", System.currentTimeMillis() - 3000, emptyMap()),
            UserActivity("2", "NAVIGATION", "Browse documentation", System.currentTimeMillis() - 2000, emptyMap()),
            UserActivity("3", "CODE_EDIT", "Implement AI features", System.currentTimeMillis() - 1000, emptyMap())
        )
        
        // Build context graph
        val contextGraph = analyzer.buildContextGraph(activities)
        
        assertNotNull(contextGraph, "Should build context graph")
        assertEquals(3, contextGraph.nodes.size, "Should have 3 nodes")
        assertTrue(contextGraph.edges.isNotEmpty(), "Should have relationships")
        assertEquals(3, contextGraph.temporalSequence.size, "Should have temporal sequence")
    }
    
    @Test
    fun testContextAnalyzerRelevanceSearch() = runTest {
        val analyzer = OmnisyncraContextAnalyzer()
        
        // Add some activities
        val activities = listOf(
            UserActivity("1", "CODE_EDIT", "AI integration with Gemini API", System.currentTimeMillis(), emptyMap()),
            UserActivity("2", "DOCUMENT_EDIT", "Writing AI documentation", System.currentTimeMillis(), emptyMap()),
            UserActivity("3", "COMMUNICATION", "Discussing project timeline", System.currentTimeMillis(), emptyMap())
        )
        
        analyzer.buildContextGraph(activities)
        
        // Search for relevant context
        val relevantItems = analyzer.getRelevantContext("AI integration")
        
        assertTrue(relevantItems.isNotEmpty(), "Should find relevant items")
        assertTrue(relevantItems.any { it.content.contains("AI") }, "Should find AI-related content")
        assertTrue(relevantItems.all { it.relevanceScore > 0.0f }, "Should have relevance scores")
    }
    
    @Test
    fun testGeminiAPIClientConfiguration() {
        val config = GeminiAPIConfig(
            apiKey = "test-api-key",
            endpoint = "https://test-endpoint.com",
            model = "gemini-pro",
            maxTokens = 4096,
            temperature = 0.2f,
            rateLimitPerMinute = 30
        )
        
        assertEquals("test-api-key", config.apiKey)
        assertEquals("gemini-pro", config.model)
        assertEquals(4096, config.maxTokens)
        assertEquals(0.2f, config.temperature)
        assertEquals(30, config.rateLimitPerMinute)
    }
    
    @Test
    fun testGeminiAPIRequestStructure() {
        val request = GeminiAPIRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = "Test prompt")))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                maxOutputTokens = 1024,
                topP = 0.8f,
                topK = 40
            ),
            safetySettings = listOf(
                SafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_MEDIUM_AND_ABOVE")
            )
        )
        
        assertEquals(1, request.contents.size)
        assertEquals("Test prompt", request.contents[0].parts[0].text)
        assertEquals(0.1f, request.generationConfig.temperature)
        assertEquals(1024, request.generationConfig.maxOutputTokens)
        assertEquals(1, request.safetySettings.size)
    }
    
    @Test
    fun testAISystemDataTypes() {
        // Test UserData creation
        val userData = UserData(
            content = "Test content for AI analysis",
            contentType = ContentType.TEXT,
            timestamp = System.currentTimeMillis(),
            source = "test",
            metadata = mapOf("priority" to "high")
        )
        
        assertEquals(ContentType.TEXT, userData.contentType)
        assertEquals("test", userData.source)
        assertTrue(userData.metadata.containsKey("priority"))
        
        // Test Entity creation
        val entity = Entity(
            text = "Omnisyncra",
            type = EntityType.PROJECT,
            confidence = 0.95f,
            startIndex = 0,
            endIndex = 10
        )
        
        assertEquals("Omnisyncra", entity.text)
        assertEquals(EntityType.PROJECT, entity.type)
        assertEquals(0.95f, entity.confidence)
        
        // Test Sentiment creation
        val sentiment = Sentiment(
            polarity = 0.8f,
            magnitude = 0.6f,
            confidence = 0.9f
        )
        
        assertEquals(0.8f, sentiment.polarity)
        assertEquals(0.6f, sentiment.magnitude)
        assertEquals(0.9f, sentiment.confidence)
    }
    
    @Test
    fun testPrivacyLevels() {
        // Test privacy level enum
        val levels = PrivacyLevel.values()
        
        assertTrue(levels.contains(PrivacyLevel.PUBLIC))
        assertTrue(levels.contains(PrivacyLevel.INTERNAL))
        assertTrue(levels.contains(PrivacyLevel.CONFIDENTIAL))
        assertTrue(levels.contains(PrivacyLevel.RESTRICTED))
        
        assertEquals(4, levels.size, "Should have 4 privacy levels")
    }
    
    @Test
    fun testAnalysisTypes() {
        // Test analysis type enum
        val types = AnalysisType.values()
        
        assertTrue(types.contains(AnalysisType.CONTEXT_EXTRACTION))
        assertTrue(types.contains(AnalysisType.ENTITY_RECOGNITION))
        assertTrue(types.contains(AnalysisType.SENTIMENT_ANALYSIS))
        assertTrue(types.contains(AnalysisType.TOPIC_MODELING))
        assertTrue(types.contains(AnalysisType.RELATIONSHIP_MAPPING))
        assertTrue(types.contains(AnalysisType.INTENT_DETECTION))
        
        assertEquals(6, types.size, "Should have 6 analysis types")
    }
}