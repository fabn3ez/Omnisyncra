package com.omnisyncra.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.math.max

import kotlinx.coroutines.runBlocking

/**
 * Privacy-first data sanitizer implementation
 * Detects and removes/anonymizes PII before cloud processing
 */
class OmnisyncraDataSanitizer : DataSanitizer {
    
    private val _confidenceScore = MutableStateFlow(0.0f)
    private val confidenceScore: StateFlow<Float> = _confidenceScore.asStateFlow()
    
    // PII detection patterns
    private val emailPattern = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    private val phonePattern = Regex("""(\+?1[-.\s]?)?\(?([0-9]{3})\)?[-.\s]?([0-9]{3})[-.\s]?([0-9]{4})""")
    private val ssnPattern = Regex("""\b\d{3}-?\d{2}-?\d{4}\b""")
    private val creditCardPattern = Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b""")
    private val ipAddressPattern = Regex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b""")
    
    // Common name patterns (simplified)
    private val namePatterns = listOf(
        Regex("""\b[A-Z][a-z]+ [A-Z][a-z]+\b"""), // First Last
        Regex("""\b[A-Z]\. [A-Z][a-z]+\b"""), // F. Last
        Regex("""\b[A-Z][a-z]+ [A-Z]\. [A-Z][a-z]+\b""") // First M. Last
    )
    
    // Location patterns
    private val locationPatterns = listOf(
        Regex("""\b\d+\s+[A-Z][a-z]+\s+(Street|St|Avenue|Ave|Road|Rd|Drive|Dr|Lane|Ln|Boulevard|Blvd)\b"""),
        Regex("""\b[A-Z][a-z]+,\s*[A-Z]{2}\s*\d{5}\b""") // City, ST ZIP
    )
    
    override suspend fun sanitizeText(text: String): SanitizedText {
        val sensitiveInfo = detectSensitiveInfo(text)
        var sanitizedContent = text
        val removedItems = mutableListOf<String>()
        
        // Sort by position (descending) to avoid index shifting
        val sortedInfo = sensitiveInfo.sortedByDescending { it.startIndex }
        
        for (info in sortedInfo) {
            val replacement = generateReplacement(info.type, info.text)
            sanitizedContent = sanitizedContent.replaceRange(
                info.startIndex,
                info.endIndex,
                replacement
            )
            removedItems.add("${info.type}: ${info.text}")
        }
        
        val confidence = calculateConfidence(sensitiveInfo, text.length)
        _confidenceScore.value = confidence
        
        return SanitizedText(
            content = sanitizedContent,
            originalHash = text.hashCode().toString(),
            removedItems = removedItems,
            confidence = confidence
        )
    }
    
    override suspend fun sanitizeCode(code: String, language: String): SanitizedCode {
        val sensitiveInfo = detectSensitiveInfo(code)
        var sanitizedCode = code
        val removedItems = mutableListOf<String>()
        
        // Language-specific sanitization
        when (language.lowercase()) {
            "kotlin", "java" -> {
                sanitizedCode = sanitizeJvmCode(sanitizedCode, removedItems)
            }
            "javascript", "typescript" -> {
                sanitizedCode = sanitizeJsCode(sanitizedCode, removedItems)
            }
            "python" -> {
                sanitizedCode = sanitizePythonCode(sanitizedCode, removedItems)
            }
            else -> {
                // Generic code sanitization
                sanitizedCode = sanitizeGenericCode(sanitizedCode, removedItems)
            }
        }
        
        // Remove PII from code comments and strings
        val sortedInfo = sensitiveInfo.sortedByDescending { it.startIndex }
        for (info in sortedInfo) {
            val replacement = generateCodeReplacement(info.type)
            sanitizedCode = sanitizedCode.replaceRange(
                info.startIndex,
                info.endIndex,
                replacement
            )
            removedItems.add("${info.type}: ${info.text}")
        }
        
        val confidence = calculateConfidence(sensitiveInfo, code.length)
        _confidenceScore.value = confidence
        
        return SanitizedCode(
            code = sanitizedCode,
            language = language,
            removedItems = removedItems,
            preservedFunctionality = true // Assume functionality preserved for now
        )
    }
    
    override suspend fun sanitizeStructuredData(data: Map<String, Any>): SanitizedData {
        val sanitizedMap = mutableMapOf<String, Any>()
        val report = SanitizationReport(
            itemsRemoved = mutableListOf(),
            itemsAnonymized = mutableListOf(),
            confidenceScore = 0.0f,
            processingTime = System.currentTimeMillis()
        )
        
        val startTime = System.currentTimeMillis()
        
        for ((key, value) in data) {
            when (value) {
                is String -> {
                    val sanitized = sanitizeText(value)
                    sanitizedMap[key] = sanitized.content
                    
                    if (sanitized.removedItems.isNotEmpty()) {
                        report.itemsRemoved.addAll(
                            sanitized.removedItems.map { 
                                SanitizedItem(value, sanitized.content, "text", sanitized.confidence)
                            }
                        )
                    }
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val nestedResult = sanitizeStructuredData(value as Map<String, Any>)
                    sanitizedMap[key] = nestedResult.sanitizedContent
                }
                is List<*> -> {
                    sanitizedMap[key] = sanitizeList(value, report)
                }
                else -> {
                    sanitizedMap[key] = value // Keep non-string values as-is
                }
            }
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        report.processingTime = processingTime
        report.confidenceScore = calculateOverallConfidence(report)
        
        return SanitizedData(
            sanitizedContent = sanitizedMap.toString(),
            originalHash = data.hashCode().toString(),
            sanitizationReport = report,
            confidenceScore = report.confidenceScore,
            privacyLevel = PrivacyLevel.INTERNAL
        )
    }
    
    override suspend fun detectSensitiveInfo(content: String): List<SensitiveInfo> {
        val sensitiveItems = mutableListOf<SensitiveInfo>()
        
        // Email detection
        emailPattern.findAll(content).forEach { match ->
            sensitiveItems.add(
                SensitiveInfo(
                    text = match.value,
                    type = "EMAIL",
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    confidence = 0.95f
                )
            )
        }
        
        // Phone number detection
        phonePattern.findAll(content).forEach { match ->
            sensitiveItems.add(
                SensitiveInfo(
                    text = match.value,
                    type = "PHONE",
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    confidence = 0.90f
                )
            )
        }
        
        // SSN detection
        ssnPattern.findAll(content).forEach { match ->
            sensitiveItems.add(
                SensitiveInfo(
                    text = match.value,
                    type = "SSN",
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    confidence = 0.98f
                )
            )
        }
        
        // Credit card detection
        creditCardPattern.findAll(content).forEach { match ->
            if (isValidCreditCard(match.value)) {
                sensitiveItems.add(
                    SensitiveInfo(
                        text = match.value,
                        type = "CREDIT_CARD",
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        confidence = 0.92f
                    )
                )
            }
        }
        
        // IP address detection
        ipAddressPattern.findAll(content).forEach { match ->
            if (isValidIPAddress(match.value)) {
                sensitiveItems.add(
                    SensitiveInfo(
                        text = match.value,
                        type = "IP_ADDRESS",
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        confidence = 0.85f
                    )
                )
            }
        }
        
        // Name detection (lower confidence)
        namePatterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                if (isProbablyName(match.value)) {
                    sensitiveItems.add(
                        SensitiveInfo(
                            text = match.value,
                            type = "PERSON_NAME",
                            startIndex = match.range.first,
                            endIndex = match.range.last + 1,
                            confidence = 0.70f
                        )
                    )
                }
            }
        }
        
        // Location detection
        locationPatterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                sensitiveItems.add(
                    SensitiveInfo(
                        text = match.value,
                        type = "ADDRESS",
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        confidence = 0.80f
                    )
                )
            }
        }
        
        return sensitiveItems.distinctBy { "${it.startIndex}-${it.endIndex}" }
    }
    
    override fun getConfidenceScore(): Float = _confidenceScore.value
    
    private fun generateReplacement(type: String, originalText: String): String {
        return when (type) {
            "EMAIL" -> "[EMAIL_REDACTED]"
            "PHONE" -> "[PHONE_REDACTED]"
            "SSN" -> "[SSN_REDACTED]"
            "CREDIT_CARD" -> "[CARD_REDACTED]"
            "IP_ADDRESS" -> "[IP_REDACTED]"
            "PERSON_NAME" -> "[NAME_REDACTED]"
            "ADDRESS" -> "[ADDRESS_REDACTED]"
            else -> "[PII_REDACTED]"
        }
    }
    
    private fun generateCodeReplacement(type: String): String {
        return when (type) {
            "EMAIL" -> "\"user@example.com\""
            "PHONE" -> "\"555-0123\""
            "SSN" -> "\"000-00-0000\""
            "CREDIT_CARD" -> "\"0000-0000-0000-0000\""
            "IP_ADDRESS" -> "\"127.0.0.1\""
            "PERSON_NAME" -> "\"John Doe\""
            "ADDRESS" -> "\"123 Main St\""
            else -> "\"[REDACTED]\""
        }
    }
    
    private fun sanitizeJvmCode(code: String, removedItems: MutableList<String>): String {
        var sanitized = code
        
        // Remove API keys and secrets from string literals
        val apiKeyPattern = Regex("""["']([A-Za-z0-9_-]{20,})["']""")
        apiKeyPattern.findAll(code).forEach { match ->
            if (looksLikeApiKey(match.groupValues[1])) {
                sanitized = sanitized.replace(match.value, "\"API_KEY_REDACTED\"")
                removedItems.add("API_KEY: ${match.groupValues[1]}")
            }
        }
        
        // Remove database connection strings
        val dbPattern = Regex("""jdbc:[^"']+""")
        dbPattern.findAll(code).forEach { match ->
            sanitized = sanitized.replace(match.value, "jdbc:h2:mem:testdb")
            removedItems.add("DB_CONNECTION: ${match.value}")
        }
        
        return sanitized
    }
    
    private fun sanitizeJsCode(code: String, removedItems: MutableList<String>): String {
        var sanitized = code
        
        // Remove API endpoints
        val urlPattern = Regex("""['"`](https?://[^'"`]+)['"`]""")
        urlPattern.findAll(code).forEach { match ->
            if (!isLocalhost(match.groupValues[1])) {
                sanitized = sanitized.replace(match.value, "\"https://api.example.com\"")
                removedItems.add("URL: ${match.groupValues[1]}")
            }
        }
        
        return sanitized
    }
    
    private fun sanitizePythonCode(code: String, removedItems: MutableList<String>): String {
        var sanitized = code
        
        // Remove environment variable access with secrets
        val envPattern = Regex("""os\.environ\[['"]([^'"]+)['"]\]""")
        envPattern.findAll(code).forEach { match ->
            val envVar = match.groupValues[1]
            if (looksLikeSecret(envVar)) {
                sanitized = sanitized.replace(match.value, "os.environ['EXAMPLE_VAR']")
                removedItems.add("ENV_VAR: $envVar")
            }
        }
        
        return sanitized
    }
    
    private fun sanitizeGenericCode(code: String, removedItems: MutableList<String>): String {
        var sanitized = code
        
        // Generic string literal sanitization
        val stringPattern = Regex("""["']([^"']{10,})["']""")
        stringPattern.findAll(code).forEach { match ->
            val content = match.groupValues[1]
            if (containsPII(content)) {
                sanitized = sanitized.replace(match.value, "\"REDACTED_STRING\"")
                removedItems.add("STRING_LITERAL: $content")
            }
        }
        
        return sanitized
    }
    
    private fun sanitizeList(list: List<*>, report: SanitizationReport): List<Any> {
        return list.map { item ->
            when (item) {
                is String -> {
                    val sanitized = runBlocking { sanitizeText(item) }
                    if (sanitized.removedItems.isNotEmpty()) {
                        report.itemsRemoved.addAll(
                            sanitized.removedItems.map { 
                                SanitizedItem(item, sanitized.content, "text", sanitized.confidence)
                            }
                        )
                    }
                    sanitized.content
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    runBlocking { sanitizeStructuredData(item as Map<String, Any>) }.sanitizedContent
                }
                else -> item
            }
        }
    }
    
    private fun calculateConfidence(sensitiveInfo: List<SensitiveInfo>, contentLength: Int): Float {
        if (sensitiveInfo.isEmpty()) return 1.0f
        
        val avgConfidence = sensitiveInfo.map { it.confidence }.average().toFloat()
        val coverage = sensitiveInfo.sumOf { it.endIndex - it.startIndex }.toFloat() / contentLength
        
        // Higher confidence when we detect more PII with high certainty
        return max(0.0f, avgConfidence - (coverage * 0.1f))
    }
    
    private fun calculateOverallConfidence(report: SanitizationReport): Float {
        val totalItems = report.itemsRemoved.size + report.itemsAnonymized.size
        if (totalItems == 0) return 1.0f
        
        val avgConfidence = (report.itemsRemoved + report.itemsAnonymized)
            .map { it.confidence }
            .average()
            .toFloat()
        
        return avgConfidence
    }
    
    private fun isValidCreditCard(number: String): Boolean {
        val digits = number.replace(Regex("[^0-9]"), "")
        return digits.length in 13..19 && luhnCheck(digits)
    }
    
    private fun luhnCheck(number: String): Boolean {
        var sum = 0
        var alternate = false
        
        for (i in number.length - 1 downTo 0) {
            var n = number[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n = (n % 10) + 1
            }
            sum += n
            alternate = !alternate
        }
        
        return sum % 10 == 0
    }
    
    private fun isValidIPAddress(ip: String): Boolean {
        val parts = ip.split(".")
        return parts.size == 4 && parts.all { 
            it.toIntOrNull()?.let { num -> num in 0..255 } == true 
        }
    }
    
    private fun isProbablyName(text: String): Boolean {
        // Simple heuristics to reduce false positives
        val words = text.split(" ")
        return words.size in 2..3 && 
               words.all { it.length > 1 } &&
               !commonWords.contains(text.lowercase())
    }
    
    private fun looksLikeApiKey(text: String): Boolean {
        return text.length >= 20 && 
               text.any { it.isDigit() } && 
               text.any { it.isLetter() } &&
               (text.contains("key", ignoreCase = true) || 
                text.contains("token", ignoreCase = true) ||
                text.matches(Regex("[A-Za-z0-9_-]{32,}")))
    }
    
    private fun isLocalhost(url: String): Boolean {
        return url.contains("localhost") || 
               url.contains("127.0.0.1") || 
               url.contains("0.0.0.0")
    }
    
    private fun looksLikeSecret(envVar: String): Boolean {
        val secretKeywords = listOf("key", "secret", "token", "password", "pass", "auth", "api")
        return secretKeywords.any { envVar.lowercase().contains(it) }
    }
    
    private fun containsPII(content: String): Boolean {
        return emailPattern.containsMatchIn(content) ||
               phonePattern.containsMatchIn(content) ||
               ssnPattern.containsMatchIn(content) ||
               creditCardPattern.containsMatchIn(content)
    }
    
    companion object {
        private val commonWords = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "had", "her", "was", "one", "our", "out", "day", "get", "has", "him", "his", "how", "man", "new", "now", "old", "see", "two", "way", "who", "boy", "did", "its", "let", "put", "say", "she", "too", "use"
        )
    }
}

