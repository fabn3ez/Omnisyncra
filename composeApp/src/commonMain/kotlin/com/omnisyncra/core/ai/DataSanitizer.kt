package com.omnisyncra.core.ai

/**
 * Privacy-first data sanitization system
 * Detects and removes PII while preserving semantic meaning
 */
class DataSanitizer {
    
    private val emailPattern = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    private val phonePattern = Regex("""(\+?1[-.\s]?)?\(?([0-9]{3})\)?[-.\s]?([0-9]{3})[-.\s]?([0-9]{4})""")
    private val ssnPattern = Regex("""\b\d{3}-?\d{2}-?\d{4}\b""")
    private val creditCardPattern = Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b""")
    private val ipPattern = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val namePattern = Regex("""\b[A-Z][a-z]+ [A-Z][a-z]+\b""")
    
    /**
     * Sanitize text content by detecting and replacing PII
     */
    suspend fun sanitize(content: String): SanitizedData {
        val detections = mutableListOf<PIIDetection>()
        var sanitizedContent = content
        
        // Detect and replace emails
        emailPattern.findAll(content).forEach { match ->
            detections.add(PIIDetection(
                type = PIIType.EMAIL,
                location = match.range,
                confidence = 0.95f,
                replacement = "[EMAIL_REDACTED]"
            ))
            sanitizedContent = sanitizedContent.replace(match.value, "[EMAIL_REDACTED]")
        }
        
        // Detect and replace phone numbers
        phonePattern.findAll(content).forEach { match ->
            detections.add(PIIDetection(
                type = PIIType.PHONE_NUMBER,
                location = match.range,
                confidence = 0.90f,
                replacement = "[PHONE_REDACTED]"
            ))
            sanitizedContent = sanitizedContent.replace(match.value, "[PHONE_REDACTED]")
        }
        
        // Detect and replace SSNs
        ssnPattern.findAll(content).forEach { match ->
            detections.add(PIIDetection(
                type = PIIType.SSN,
                location = match.range,
                confidence = 0.98f,
                replacement = "[SSN_REDACTED]"
            ))
            sanitizedContent = sanitizedContent.replace(match.value, "[SSN_REDACTED]")
        }
        
        // Detect and replace credit cards
        creditCardPattern.findAll(content).forEach { match ->
            detections.add(PIIDetection(
                type = PIIType.CREDIT_CARD,
                location = match.range,
                confidence = 0.92f,
                replacement = "[CARD_REDACTED]"
            ))
            sanitizedContent = sanitizedContent.replace(match.value, "[CARD_REDACTED]")
        }
        
        // Detect and replace IP addresses
        ipPattern.findAll(content).forEach { match ->
            detections.add(PIIDetection(
                type = PIIType.IP_ADDRESS,
                location = match.range,
                confidence = 0.85f,
                replacement = "[IP_REDACTED]"
            ))
            sanitizedContent = sanitizedContent.replace(match.value, "[IP_REDACTED]")
        }
        
        // Detect and replace names (basic pattern)
        namePattern.findAll(content).forEach { match ->
            detections.add(PIIDetection(
                type = PIIType.PERSON_NAME,
                location = match.range,
                confidence = 0.70f,
                replacement = "[NAME_REDACTED]"
            ))
            sanitizedContent = sanitizedContent.replace(match.value, "[NAME_REDACTED]")
        }
        
        val overallConfidence = if (detections.isNotEmpty()) {
            detections.map { it.confidence }.average().toFloat()
        } else {
            1.0f // High confidence when no PII detected
        }
        
        return SanitizedData(
            sanitizedContent = sanitizedContent,
            detectedPII = detections,
            confidenceScore = overallConfidence
        )
    }
    
    /**
     * Check if content contains potential PII without sanitizing
     */
    suspend fun containsPII(content: String): Boolean {
        return emailPattern.containsMatchIn(content) ||
                phonePattern.containsMatchIn(content) ||
                ssnPattern.containsMatchIn(content) ||
                creditCardPattern.containsMatchIn(content) ||
                ipPattern.containsMatchIn(content) ||
                namePattern.containsMatchIn(content)
    }
    
    /**
     * Get PII detection statistics
     */
    suspend fun getPIIStats(content: String): Map<PIIType, Int> {
        val stats = mutableMapOf<PIIType, Int>()
        
        stats[PIIType.EMAIL] = emailPattern.findAll(content).count()
        stats[PIIType.PHONE_NUMBER] = phonePattern.findAll(content).count()
        stats[PIIType.SSN] = ssnPattern.findAll(content).count()
        stats[PIIType.CREDIT_CARD] = creditCardPattern.findAll(content).count()
        stats[PIIType.IP_ADDRESS] = ipPattern.findAll(content).count()
        stats[PIIType.PERSON_NAME] = namePattern.findAll(content).count()
        
        return stats.filterValues { it > 0 }
    }
}