package com.omnisyncra.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.omnisyncra.core.platform.TimeUtils

/**
 * Intelligent context analysis system
 * Builds context graphs and analyzes user activity patterns
 */
class ContextAnalyzer {
    
    private val _contextGraph = MutableStateFlow<ContextGraph>(ContextGraph())
    val contextGraph: StateFlow<ContextGraph> = _contextGraph.asStateFlow()
    
    private val activityHistory = mutableListOf<ActivityRecord>()
    private val maxHistorySize = 1000
    
    /**
     * Analyze current context and generate insights
     */
    suspend fun analyzeContext(context: AIContext): ContextAnalysis {
        val startTime = TimeUtils.currentTimeMillis()
        
        // Record activity
        recordActivity(context)
        
        // Analyze patterns
        val relevantTopics = extractRelevantTopics(context)
        val suggestedActions = generateSuggestedActions(context)
        val confidence = calculateConfidence(context, relevantTopics)
        
        val processingTime = TimeUtils.currentTimeMillis() - startTime
        
        return ContextAnalysis(
            relevantTopics = relevantTopics,
            suggestedActions = suggestedActions,
            confidenceScore = confidence,
            processingTimeMs = processingTime
        )
    }
    
    /**
     * Build and update context graph
     */
    suspend fun updateContextGraph(context: AIContext) {
        val currentGraph = _contextGraph.value
        val updatedGraph = currentGraph.copy(
            nodes = currentGraph.nodes + createContextNode(context),
            edges = currentGraph.edges + createContextEdges(context, currentGraph),
            lastUpdated = TimeUtils.currentTimeMillis()
        )
        
        _contextGraph.value = updatedGraph
    }
    
    /**
     * Get behavioral patterns from history
     */
    suspend fun getBehavioralPatterns(): List<BehavioralPattern> {
        if (activityHistory.size < 10) return emptyList()
        
        val patterns = mutableListOf<BehavioralPattern>()
        
        // Time-based patterns
        val timePatterns = analyzeTimePatterns()
        patterns.addAll(timePatterns)
        
        // Activity sequence patterns
        val sequencePatterns = analyzeSequencePatterns()
        patterns.addAll(sequencePatterns)
        
        // Device usage patterns
        val devicePatterns = analyzeDevicePatterns()
        patterns.addAll(devicePatterns)
        
        return patterns
    }
    
    /**
     * Predict next likely actions
     */
    suspend fun predictNextActions(context: AIContext): List<PredictedAction> {
        val patterns = getBehavioralPatterns()
        val predictions = mutableListOf<PredictedAction>()
        
        // Based on time patterns
        val currentHour = (TimeUtils.currentTimeMillis() / (1000 * 60 * 60)) % 24
        patterns.filter { it.type == PatternType.TIME_BASED }
            .forEach { pattern ->
                if (pattern.metadata["hour"]?.toIntOrNull() == currentHour.toInt()) {
                    predictions.add(PredictedAction(
                        action = pattern.description,
                        confidence = pattern.confidence * 0.8f,
                        reasoning = "Based on time-based usage pattern"
                    ))
                }
            }
        
        // Based on activity sequences
        val recentActivities = activityHistory.takeLast(5).map { it.activity }
        patterns.filter { it.type == PatternType.SEQUENCE }
            .forEach { pattern ->
                val sequence = pattern.metadata["sequence"]?.split(",") ?: emptyList()
                if (sequence.size > 1 && recentActivities.containsAll(sequence.dropLast(1))) {
                    predictions.add(PredictedAction(
                        action = sequence.last(),
                        confidence = pattern.confidence * 0.9f,
                        reasoning = "Based on activity sequence pattern"
                    ))
                }
            }
        
        return predictions.sortedByDescending { it.confidence }.take(5)
    }
    
    private fun recordActivity(context: AIContext) {
        val record = ActivityRecord(
            activity = context.currentActivity,
            timestamp = context.timestamp,
            deviceType = context.deviceContext.deviceType,
            metadata = mapOf(
                "batteryLevel" to (context.deviceContext.batteryLevel?.toString() ?: "unknown"),
                "networkType" to (context.deviceContext.networkType ?: "unknown")
            )
        )
        
        activityHistory.add(record)
        
        // Maintain history size
        if (activityHistory.size > maxHistorySize) {
            activityHistory.removeAt(0)
        }
    }
    
    private fun extractRelevantTopics(context: AIContext): List<String> {
        val topics = mutableSetOf<String>()
        
        // Extract from current activity
        topics.addAll(extractTopicsFromText(context.currentActivity))
        
        // Extract from recent activities
        context.recentActivities.forEach { activity ->
            topics.addAll(extractTopicsFromText(activity))
        }
        
        // Add device-specific topics
        topics.add("device:${context.deviceContext.deviceType}")
        context.deviceContext.capabilities.forEach { capability ->
            topics.add("capability:$capability")
        }
        
        return topics.toList()
    }
    
    private fun extractTopicsFromText(text: String): List<String> {
        // Simple keyword extraction - in a real implementation, this would use NLP
        val keywords = text.lowercase()
            .split(Regex("[\\s,.-]+"))
            .filter { it.length > 3 }
            .filter { !commonWords.contains(it) }
            .distinct()
        
        return keywords.take(5) // Limit to top 5 keywords
    }
    
    private fun generateSuggestedActions(context: AIContext): List<String> {
        val actions = mutableListOf<String>()
        
        // Based on device capabilities
        if (context.deviceContext.capabilities.contains("camera")) {
            actions.add("Take photo or scan document")
        }
        if (context.deviceContext.capabilities.contains("gps")) {
            actions.add("Share location or get directions")
        }
        if (context.deviceContext.capabilities.contains("microphone")) {
            actions.add("Record voice note or start call")
        }
        
        // Based on battery level
        context.deviceContext.batteryLevel?.let { battery ->
            if (battery < 0.2f) {
                actions.add("Switch to power-saving mode")
                actions.add("Handoff to another device")
            }
        }
        
        // Based on activity patterns
        val recentActivities = context.recentActivities.takeLast(3)
        if (recentActivities.any { it.contains("document") || it.contains("file") }) {
            actions.add("Continue editing document")
            actions.add("Share document with team")
        }
        
        return actions.take(5)
    }
    
    private fun calculateConfidence(context: AIContext, topics: List<String>): Float {
        var confidence = 0.5f
        
        // Higher confidence with more context
        confidence += (context.recentActivities.size * 0.1f).coerceAtMost(0.3f)
        
        // Higher confidence with more topics
        confidence += (topics.size * 0.05f).coerceAtMost(0.2f)
        
        // Higher confidence with device context
        if (context.deviceContext.batteryLevel != null) confidence += 0.1f
        if (context.deviceContext.networkType != null) confidence += 0.1f
        
        return confidence.coerceAtMost(1.0f)
    }
    
    private fun createContextNode(context: AIContext): ContextNode {
        return ContextNode(
            id = "node_${context.timestamp}",
            activity = context.currentActivity,
            timestamp = context.timestamp,
            deviceType = context.deviceContext.deviceType,
            metadata = mapOf(
                "capabilities" to context.deviceContext.capabilities.joinToString(","),
                "batteryLevel" to (context.deviceContext.batteryLevel?.toString() ?: "unknown"),
                "networkType" to (context.deviceContext.networkType ?: "unknown")
            )
        )
    }
    
    private fun createContextEdges(context: AIContext, currentGraph: ContextGraph): List<ContextEdge> {
        val edges = mutableListOf<ContextEdge>()
        val newNodeId = "node_${context.timestamp}"
        
        // Connect to recent nodes (temporal relationships)
        val recentNodes = currentGraph.nodes
            .filter { TimeUtils.currentTimeMillis() - it.timestamp < 300000 } // 5 minutes
            .sortedByDescending { it.timestamp }
            .take(3)
        
        recentNodes.forEach { node ->
            edges.add(ContextEdge(
                from = node.id,
                to = newNodeId,
                type = EdgeType.TEMPORAL,
                weight = calculateTemporalWeight(context.timestamp - node.timestamp)
            ))
        }
        
        // Connect to similar activities (semantic relationships)
        val similarNodes = currentGraph.nodes
            .filter { calculateSimilarity(it.activity, context.currentActivity) > 0.7f }
            .take(2)
        
        similarNodes.forEach { node ->
            edges.add(ContextEdge(
                from = node.id,
                to = newNodeId,
                type = EdgeType.SEMANTIC,
                weight = calculateSimilarity(node.activity, context.currentActivity)
            ))
        }
        
        return edges
    }
    
    private fun calculateTemporalWeight(timeDiff: Long): Float {
        // Weight decreases with time difference
        val minutes = timeDiff / (1000 * 60)
        return (1.0f / (1.0f + minutes * 0.1f)).coerceAtLeast(0.1f)
    }
    
    private fun calculateSimilarity(text1: String, text2: String): Float {
        val words1 = text1.lowercase().split(Regex("\\s+")).toSet()
        val words2 = text2.lowercase().split(Regex("\\s+")).toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union > 0) intersection.toFloat() / union else 0f
    }
    
    private fun analyzeTimePatterns(): List<BehavioralPattern> {
        val patterns = mutableListOf<BehavioralPattern>()
        val hourlyActivity = activityHistory.groupBy { 
            (it.timestamp / (1000 * 60 * 60)) % 24 
        }
        
        hourlyActivity.forEach { (hour, activities) ->
            if (activities.size >= 5) { // Minimum occurrences for pattern
                val commonActivity = activities.groupBy { it.activity }
                    .maxByOrNull { it.value.size }?.key
                
                if (commonActivity != null) {
                    patterns.add(BehavioralPattern(
                        type = PatternType.TIME_BASED,
                        description = commonActivity,
                        confidence = (activities.size / activityHistory.size.toFloat()).coerceAtMost(1.0f),
                        metadata = mapOf("hour" to hour.toString())
                    ))
                }
            }
        }
        
        return patterns
    }
    
    private fun analyzeSequencePatterns(): List<BehavioralPattern> {
        val patterns = mutableListOf<BehavioralPattern>()
        val sequenceLength = 3
        
        if (activityHistory.size < sequenceLength) return patterns
        
        val sequences = mutableMapOf<List<String>, Int>()
        
        for (i in 0..activityHistory.size - sequenceLength) {
            val sequence = activityHistory.subList(i, i + sequenceLength)
                .map { it.activity }
            sequences[sequence] = (sequences[sequence] ?: 0) + 1
        }
        
        sequences.filter { it.value >= 3 } // Minimum occurrences
            .forEach { (sequence, count) ->
                patterns.add(BehavioralPattern(
                    type = PatternType.SEQUENCE,
                    description = "Activity sequence: ${sequence.joinToString(" -> ")}",
                    confidence = (count / (activityHistory.size - sequenceLength + 1).toFloat()).coerceAtMost(1.0f),
                    metadata = mapOf("sequence" to sequence.joinToString(","))
                ))
            }
        
        return patterns
    }
    
    private fun analyzeDevicePatterns(): List<BehavioralPattern> {
        val patterns = mutableListOf<BehavioralPattern>()
        val deviceActivity = activityHistory.groupBy { it.deviceType }
        
        deviceActivity.forEach { (deviceType, activities) ->
            val commonActivities = activities.groupBy { it.activity }
                .filter { it.value.size >= 3 }
                .keys
            
            if (commonActivities.isNotEmpty()) {
                patterns.add(BehavioralPattern(
                    type = PatternType.DEVICE_BASED,
                    description = "Common activities on $deviceType: ${commonActivities.joinToString(", ")}",
                    confidence = (activities.size / activityHistory.size.toFloat()).coerceAtMost(1.0f),
                    metadata = mapOf(
                        "deviceType" to deviceType,
                        "activities" to commonActivities.joinToString(",")
                    )
                ))
            }
        }
        
        return patterns
    }
    
    companion object {
        private val commonWords = setOf(
            "the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "from", "up", "about", "into", "through", "during", "before", "after", "above",
            "below", "between", "among", "this", "that", "these", "those", "is", "are", "was",
            "were", "been", "have", "has", "had", "will", "would", "could", "should", "may",
            "might", "must", "can", "do", "does", "did", "get", "got", "make", "made", "take",
            "took", "come", "came", "go", "went", "see", "saw", "know", "knew", "think", "thought"
        )
    }
}

// Supporting data classes
data class ContextGraph(
    val nodes: List<ContextNode> = emptyList(),
    val edges: List<ContextEdge> = emptyList(),
    val lastUpdated: Long = TimeUtils.currentTimeMillis()
)

data class ContextNode(
    val id: String,
    val activity: String,
    val timestamp: Long,
    val deviceType: String,
    val metadata: Map<String, String> = emptyMap()
)

data class ContextEdge(
    val from: String,
    val to: String,
    val type: EdgeType,
    val weight: Float
)

enum class EdgeType {
    TEMPORAL,
    SEMANTIC,
    CAUSAL,
    DEVICE_BASED
}

data class ActivityRecord(
    val activity: String,
    val timestamp: Long,
    val deviceType: String,
    val metadata: Map<String, String> = emptyMap()
)

data class BehavioralPattern(
    val type: PatternType,
    val description: String,
    val confidence: Float,
    val metadata: Map<String, String> = emptyMap()
)

enum class PatternType {
    TIME_BASED,
    SEQUENCE,
    DEVICE_BASED,
    FREQUENCY
}

data class PredictedAction(
    val action: String,
    val confidence: Float,
    val reasoning: String
)