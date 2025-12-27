package com.omnisyncra.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent context analyzer for understanding user activities and relationships
 */
class OmnisyncraContextAnalyzer : ContextAnalyzer {
    
    private val _contextGraph = MutableStateFlow(ContextGraph(emptyList(), emptyList(), emptyList(), emptyList()))
    private val contextGraph: StateFlow<ContextGraph> = _contextGraph.asStateFlow()
    
    private val _currentContext = MutableStateFlow(
        CurrentContext(
            activeTask = "",
            recentActivities = emptyList(),
            relevantEntities = emptyList(),
            contextScore = 0.0f
        )
    )
    
    private val activityHistory = mutableListOf<UserActivity>()
    private val contextNodes = mutableMapOf<String, ContextNode>()
    private val contextEdges = mutableListOf<ContextEdge>()
    
    override suspend fun buildContextGraph(activities: List<UserActivity>): ContextGraph {
        // Clear existing graph
        contextNodes.clear()
        contextEdges.clear()
        
        // Build nodes from activities
        activities.forEach { activity ->
            val node = ContextNode(
                id = activity.id,
                type = activity.type,
                content = activity.content,
                timestamp = activity.timestamp,
                importance = calculateImportance(activity)
            )
            contextNodes[activity.id] = node
        }
        
        // Build edges (relationships between activities)
        buildRelationshipEdges(activities)
        
        // Create clusters of related activities
        val clusters = createContextClusters()
        
        // Build temporal sequence
        val temporalSequence = activities
            .sortedBy { it.timestamp }
            .map { activity ->
                TemporalEvent(
                    timestamp = activity.timestamp,
                    nodeId = activity.id,
                    eventType = activity.type
                )
            }
        
        val graph = ContextGraph(
            nodes = contextNodes.values.toList(),
            edges = contextEdges,
            clusters = clusters,
            temporalSequence = temporalSequence
        )
        
        _contextGraph.value = graph
        return graph
    }
    
    override suspend fun analyzeCurrentContext(): CurrentContext {
        val recentActivities = activityHistory
            .sortedByDescending { it.timestamp }
            .take(10)
        
        val activeTask = inferActiveTask(recentActivities)
        val relevantEntities = extractRelevantEntities(recentActivities)
        val contextScore = calculateContextScore(recentActivities)
        
        val context = CurrentContext(
            activeTask = activeTask,
            recentActivities = recentActivities,
            relevantEntities = relevantEntities,
            contextScore = contextScore
        )
        
        _currentContext.value = context
        return context
    }
    
    override suspend fun predictNextActions(context: CurrentContext): List<PredictedAction> {
        val predictions = mutableListOf<PredictedAction>()
        
        // Analyze patterns in recent activities
        val patterns = analyzeActivityPatterns(context.recentActivities)
        
        // Predict based on temporal patterns
        patterns.forEach { pattern ->
            val confidence = calculatePredictionConfidence(pattern, context)
            if (confidence > 0.3f) {
                predictions.add(
                    PredictedAction(
                        action = pattern.nextAction,
                        confidence = confidence,
                        reasoning = "Based on ${pattern.frequency} similar sequences",
                        suggestedData = pattern.suggestedData
                    )
                )
            }
        }
        
        // Sort by confidence
        return predictions.sortedByDescending { it.confidence }
    }
    
    override suspend fun updateContextGraph(newActivity: UserActivity) {
        // Add to activity history
        activityHistory.add(newActivity)
        
        // Keep only recent activities (last 1000)
        if (activityHistory.size > 1000) {
            activityHistory.removeAt(0)
        }
        
        // Create new context node
        val node = ContextNode(
            id = newActivity.id,
            type = newActivity.type,
            content = newActivity.content,
            timestamp = newActivity.timestamp,
            importance = calculateImportance(newActivity)
        )
        contextNodes[newActivity.id] = node
        
        // Update relationships with recent activities
        updateRelationships(newActivity)
        
        // Update current context
        analyzeCurrentContext()
    }
    
    override suspend fun getRelevantContext(query: String): List<ContextItem> {
        val relevantItems = mutableListOf<ContextItem>()
        
        // Search through activity history
        activityHistory.forEach { activity ->
            val relevanceScore = calculateRelevance(query, activity)
            if (relevanceScore > 0.2f) {
                relevantItems.add(
                    ContextItem(
                        content = activity.content,
                        relevanceScore = relevanceScore,
                        source = activity.type,
                        timestamp = activity.timestamp
                    )
                )
            }
        }
        
        // Search through context nodes
        contextNodes.values.forEach { node ->
            val relevanceScore = calculateRelevance(query, node.content)
            if (relevanceScore > 0.2f) {
                relevantItems.add(
                    ContextItem(
                        content = node.content,
                        relevanceScore = relevanceScore,
                        source = node.type,
                        timestamp = node.timestamp
                    )
                )
            }
        }
        
        return relevantItems
            .distinctBy { it.content }
            .sortedByDescending { it.relevanceScore }
            .take(20)
    }
    
    private fun calculateImportance(activity: UserActivity): Float {
        var importance = 0.5f
        
        // Increase importance based on activity type
        when (activity.type) {
            "DOCUMENT_EDIT" -> importance += 0.3f
            "CODE_EDIT" -> importance += 0.4f
            "COMMUNICATION" -> importance += 0.2f
            "SEARCH" -> importance += 0.1f
            "NAVIGATION" -> importance += 0.05f
        }
        
        // Increase importance based on content length
        importance += min(activity.content.length / 1000.0f, 0.2f)
        
        // Increase importance based on context metadata
        if (activity.context.containsKey("priority")) {
            importance += when (activity.context["priority"]) {
                "high" -> 0.3f
                "medium" -> 0.1f
                else -> 0.0f
            }
        }
        
        return min(importance, 1.0f)
    }
    
    private fun buildRelationshipEdges(activities: List<UserActivity>) {
        for (i in activities.indices) {
            for (j in i + 1 until activities.size) {
                val activity1 = activities[i]
                val activity2 = activities[j]
                
                val relationship = analyzeRelationship(activity1, activity2)
                if (relationship.strength > 0.1f) {
                    contextEdges.add(
                        ContextEdge(
                            sourceId = activity1.id,
                            targetId = activity2.id,
                            relationshipType = relationship.type,
                            strength = relationship.strength
                        )
                    )
                }
            }
        }
    }
    
    private fun analyzeRelationship(activity1: UserActivity, activity2: UserActivity): ActivityRelationship {
        var strength = 0.0f
        var type = "UNKNOWN"
        
        // Temporal proximity
        val timeDiff = kotlin.math.abs(activity1.timestamp - activity2.timestamp)
        if (timeDiff < 60000) { // Within 1 minute
            strength += 0.4f
            type = "TEMPORAL"
        } else if (timeDiff < 300000) { // Within 5 minutes
            strength += 0.2f
            type = "TEMPORAL"
        }
        
        // Content similarity
        val contentSimilarity = calculateContentSimilarity(activity1.content, activity2.content)
        strength += contentSimilarity * 0.3f
        if (contentSimilarity > 0.5f) {
            type = "CONTENT_SIMILAR"
        }
        
        // Same type activities
        if (activity1.type == activity2.type) {
            strength += 0.2f
            type = "SAME_TYPE"
        }
        
        // Context similarity
        val contextSimilarity = calculateContextSimilarity(activity1.context, activity2.context)
        strength += contextSimilarity * 0.1f
        
        return ActivityRelationship(type, strength)
    }
    
    private fun calculateContentSimilarity(content1: String, content2: String): Float {
        val words1 = content1.lowercase().split("\\s+".toRegex()).toSet()
        val words2 = content2.lowercase().split("\\s+".toRegex()).toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f
    }
    
    private fun calculateContextSimilarity(context1: Map<String, String>, context2: Map<String, String>): Float {
        val keys1 = context1.keys
        val keys2 = context2.keys
        val commonKeys = keys1.intersect(keys2)
        
        if (commonKeys.isEmpty()) return 0.0f
        
        var similarity = 0.0f
        commonKeys.forEach { key ->
            if (context1[key] == context2[key]) {
                similarity += 1.0f
            }
        }
        
        return similarity / commonKeys.size
    }
    
    private fun createContextClusters(): List<ContextCluster> {
        val clusters = mutableListOf<ContextCluster>()
        val visited = mutableSetOf<String>()
        
        contextNodes.values.forEach { node ->
            if (!visited.contains(node.id)) {
                val cluster = findCluster(node, visited)
                if (cluster.nodeIds.size > 1) {
                    clusters.add(cluster)
                }
            }
        }
        
        return clusters
    }
    
    private fun findCluster(startNode: ContextNode, visited: MutableSet<String>): ContextCluster {
        val clusterNodes = mutableSetOf<String>()
        val queue = mutableListOf(startNode.id)
        
        while (queue.isNotEmpty()) {
            val nodeId = queue.removeAt(0)
            if (visited.contains(nodeId)) continue
            
            visited.add(nodeId)
            clusterNodes.add(nodeId)
            
            // Find connected nodes
            contextEdges.forEach { edge ->
                if (edge.sourceId == nodeId && edge.strength > 0.3f && !visited.contains(edge.targetId)) {
                    queue.add(edge.targetId)
                } else if (edge.targetId == nodeId && edge.strength > 0.3f && !visited.contains(edge.sourceId)) {
                    queue.add(edge.sourceId)
                }
            }
        }
        
        val theme = inferClusterTheme(clusterNodes)
        val coherence = calculateClusterCoherence(clusterNodes)
        
        return ContextCluster(
            id = "cluster_${System.currentTimeMillis()}",
            nodeIds = clusterNodes.toList(),
            theme = theme,
            coherence = coherence
        )
    }
    
    private fun inferClusterTheme(nodeIds: List<String>): String {
        val types = nodeIds.mapNotNull { contextNodes[it]?.type }
        val mostCommonType = types.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return mostCommonType ?: "MIXED"
    }
    
    private fun calculateClusterCoherence(nodeIds: List<String>): Float {
        if (nodeIds.size < 2) return 1.0f
        
        var totalStrength = 0.0f
        var edgeCount = 0
        
        contextEdges.forEach { edge ->
            if (nodeIds.contains(edge.sourceId) && nodeIds.contains(edge.targetId)) {
                totalStrength += edge.strength
                edgeCount++
            }
        }
        
        return if (edgeCount > 0) totalStrength / edgeCount else 0.0f
    }
    
    private fun inferActiveTask(recentActivities: List<UserActivity>): String {
        if (recentActivities.isEmpty()) return "IDLE"
        
        val recentTypes = recentActivities.take(5).map { it.type }
        val mostCommonType = recentTypes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        
        return when (mostCommonType) {
            "CODE_EDIT" -> "CODING"
            "DOCUMENT_EDIT" -> "WRITING"
            "COMMUNICATION" -> "COMMUNICATING"
            "SEARCH" -> "RESEARCHING"
            "NAVIGATION" -> "BROWSING"
            else -> "WORKING"
        }
    }
    
    private fun extractRelevantEntities(activities: List<UserActivity>): List<Entity> {
        val entities = mutableListOf<Entity>()
        
        activities.forEach { activity ->
            // Simple entity extraction based on patterns
            val words = activity.content.split("\\s+".toRegex())
            words.forEachIndexed { index, word ->
                if (word.matches("[A-Z][a-z]+".toRegex()) && word.length > 3) {
                    entities.add(
                        Entity(
                            text = word,
                            type = EntityType.CONCEPT,
                            confidence = 0.7f,
                            startIndex = index,
                            endIndex = index + word.length
                        )
                    )
                }
            }
        }
        
        return entities.distinctBy { it.text }.take(10)
    }
    
    private fun calculateContextScore(activities: List<UserActivity>): Float {
        if (activities.isEmpty()) return 0.0f
        
        val avgImportance = activities.map { calculateImportance(it) }.average().toFloat()
        val recency = calculateRecencyScore(activities)
        val diversity = calculateDiversityScore(activities)
        
        return (avgImportance * 0.5f + recency * 0.3f + diversity * 0.2f)
    }
    
    private fun calculateRecencyScore(activities: List<UserActivity>): Float {
        if (activities.isEmpty()) return 0.0f
        
        val now = System.currentTimeMillis()
        val avgAge = activities.map { now - it.timestamp }.average()
        
        // Score decreases with age (exponential decay)
        return exp(-(avgAge / 3600000.0)).toFloat() // 1 hour half-life
    }
    
    private fun calculateDiversityScore(activities: List<UserActivity>): Float {
        val types = activities.map { it.type }.distinct()
        return min(types.size / 5.0f, 1.0f) // Max diversity at 5 different types
    }
    
    private fun analyzeActivityPatterns(activities: List<UserActivity>): List<ActivityPattern> {
        val patterns = mutableListOf<ActivityPattern>()
        
        // Look for sequences of 2-3 activities
        for (i in 0 until activities.size - 1) {
            val sequence = activities.subList(i, min(i + 3, activities.size))
            val pattern = findPattern(sequence)
            if (pattern != null) {
                patterns.add(pattern)
            }
        }
        
        return patterns.groupBy { it.signature }
            .map { (_, patternGroup) ->
                patternGroup.first().copy(frequency = patternGroup.size)
            }
            .filter { it.frequency > 1 }
    }
    
    private fun findPattern(sequence: List<UserActivity>): ActivityPattern? {
        if (sequence.size < 2) return null
        
        val signature = sequence.map { it.type }.joinToString("->")
        val nextAction = predictNextActionFromSequence(sequence)
        
        return ActivityPattern(
            signature = signature,
            nextAction = nextAction,
            frequency = 1,
            suggestedData = extractSuggestedData(sequence)
        )
    }
    
    private fun predictNextActionFromSequence(sequence: List<UserActivity>): String {
        // Simple prediction based on common patterns
        val types = sequence.map { it.type }
        
        return when {
            types.contains("SEARCH") && types.contains("NAVIGATION") -> "DOCUMENT_EDIT"
            types.contains("CODE_EDIT") -> "CODE_TEST"
            types.contains("DOCUMENT_EDIT") -> "DOCUMENT_SAVE"
            types.contains("COMMUNICATION") -> "COMMUNICATION_REPLY"
            else -> "CONTINUE_TASK"
        }
    }
    
    private fun extractSuggestedData(sequence: List<UserActivity>): List<String> {
        return sequence.map { it.content }
            .filter { it.isNotBlank() }
            .take(3)
    }
    
    private fun calculatePredictionConfidence(pattern: ActivityPattern, context: CurrentContext): Float {
        var confidence = 0.3f
        
        // Increase confidence based on frequency
        confidence += min(pattern.frequency / 10.0f, 0.4f)
        
        // Increase confidence if pattern matches current context
        if (context.activeTask.contains(pattern.nextAction.split("_")[0], ignoreCase = true)) {
            confidence += 0.2f
        }
        
        // Increase confidence based on recency of similar activities
        val recentSimilar = context.recentActivities.count { activity ->
            pattern.signature.contains(activity.type)
        }
        confidence += min(recentSimilar / 5.0f, 0.1f)
        
        return min(confidence, 1.0f)
    }
    
    private fun updateRelationships(newActivity: UserActivity) {
        // Find relationships with recent activities
        val recentActivities = activityHistory.takeLast(10)
        
        recentActivities.forEach { recentActivity ->
            if (recentActivity.id != newActivity.id) {
                val relationship = analyzeRelationship(newActivity, recentActivity)
                if (relationship.strength > 0.1f) {
                    contextEdges.add(
                        ContextEdge(
                            sourceId = newActivity.id,
                            targetId = recentActivity.id,
                            relationshipType = relationship.type,
                            strength = relationship.strength
                        )
                    )
                }
            }
        }
        
        // Limit edge count to prevent memory issues
        if (contextEdges.size > 5000) {
            contextEdges.removeAll { it.strength < 0.2f }
        }
    }
    
    private fun calculateRelevance(query: String, content: String): Float {
        val queryWords = query.lowercase().split("\\s+".toRegex()).toSet()
        val contentWords = content.lowercase().split("\\s+".toRegex()).toSet()
        
        val intersection = queryWords.intersect(contentWords).size
        val union = queryWords.union(contentWords).size
        
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f
    }
    
    private fun calculateRelevance(query: String, activity: UserActivity): Float {
        val contentRelevance = calculateRelevance(query, activity.content)
        val typeRelevance = if (query.contains(activity.type, ignoreCase = true)) 0.3f else 0.0f
        val contextRelevance = activity.context.values.maxOfOrNull { 
            calculateRelevance(query, it) 
        } ?: 0.0f
        
        return maxOf(contentRelevance, typeRelevance, contextRelevance)
    }
}

/**
 * Helper data classes for context analysis
 */
private data class ActivityRelationship(
    val type: String,
    val strength: Float
)

private data class ActivityPattern(
    val signature: String,
    val nextAction: String,
    val frequency: Int,
    val suggestedData: List<String>
)