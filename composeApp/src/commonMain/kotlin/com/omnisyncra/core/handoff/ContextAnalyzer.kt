package com.omnisyncra.core.handoff

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Analyzes user behavior and context to enable intelligent handoffs
 */
interface ContextAnalyzer {
    suspend fun analyzeUserBehavior(actions: List<UserAction>): BehaviorPattern
    suspend fun predictNextAction(context: MentalContext): List<PredictedAction>
    suspend fun calculateCognitiveLoad(context: MentalContext): Float
    suspend fun assessHandoffReadiness(context: MentalContext): HandoffReadiness
    suspend fun optimizeForTargetDevice(context: MentalContext, targetDevice: DeviceContext): MentalContext
}

data class BehaviorPattern(
    val patternType: PatternType,
    val confidence: Float,
    val duration: Duration,
    val frequency: Float,
    val triggers: List<String>
)

enum class PatternType {
    FOCUSED_WORK, BROWSING, MULTITASKING, CREATIVE_FLOW, 
    COMMUNICATION, RESEARCH, ENTERTAINMENT, IDLE
}

data class PredictedAction(
    val action: ActionType,
    val target: String,
    val probability: Float,
    val timeframe: Duration
)

data class HandoffReadiness(
    val isReady: Boolean,
    val confidence: Float,
    val optimalTiming: Duration,
    val reasons: List<String>
)

class SmartContextAnalyzer : ContextAnalyzer {
    
    override suspend fun analyzeUserBehavior(actions: List<UserAction>): BehaviorPattern {
        if (actions.isEmpty()) {
            return BehaviorPattern(PatternType.IDLE, 0f, Duration.ZERO, 0f, emptyList())
        }
        
        val recentActions = actions.takeLast(20)
        val actionTypes = recentActions.map { it.type }
        val timeSpan = recentActions.last().timestamp - recentActions.first().timestamp
        
        // Analyze patterns
        val patternType = when {
            actionTypes.count { it == ActionType.TYPE } > actionTypes.size * 0.6f -> PatternType.FOCUSED_WORK
            actionTypes.count { it == ActionType.SCROLL } > actionTypes.size * 0.5f -> PatternType.BROWSING
            actionTypes.distinct().size > actionTypes.size * 0.8f -> PatternType.MULTITASKING
            actionTypes.count { it == ActionType.PAUSE } > actionTypes.size * 0.3f -> PatternType.IDLE
            else -> PatternType.FOCUSED_WORK
        }
        
        val confidence = calculatePatternConfidence(actionTypes, patternType)
        val frequency = recentActions.size.toFloat() / (timeSpan / 1000f) // actions per second
        
        return BehaviorPattern(
            patternType = patternType,
            confidence = confidence,
            duration = Duration.parse("${timeSpan}ms"),
            frequency = frequency,
            triggers = extractTriggers(recentActions)
        )
    }
    
    override suspend fun predictNextAction(context: MentalContext): List<PredictedAction> {
        val predictions = mutableListOf<PredictedAction>()
        
        // Based on recent actions, predict what's likely next
        val lastAction = context.recentActions.lastOrNull()
        
        when (lastAction?.type) {
            ActionType.SCROLL -> {
                predictions.add(PredictedAction(ActionType.SCROLL, "continue", 0.7f, Duration.parse("2s")))
                predictions.add(PredictedAction(ActionType.CLICK, "link", 0.3f, Duration.parse("5s")))
            }
            ActionType.TYPE -> {
                predictions.add(PredictedAction(ActionType.TYPE, "continue", 0.8f, Duration.parse("1s")))
                predictions.add(PredictedAction(ActionType.PAUSE, "think", 0.2f, Duration.parse("3s")))
            }
            ActionType.CLICK -> {
                predictions.add(PredictedAction(ActionType.SCROLL, "explore", 0.5f, Duration.parse("2s")))
                predictions.add(PredictedAction(ActionType.TYPE, "input", 0.3f, Duration.parse("3s")))
            }
            else -> {
                predictions.add(PredictedAction(ActionType.SCROLL, "browse", 0.4f, Duration.parse("3s")))
            }
        }
        
        return predictions.sortedByDescending { it.probability }
    }
    
    override suspend fun calculateCognitiveLoad(context: MentalContext): Float {
        var load = 0f
        
        // Factor in working memory
        load += (context.workingMemory.size / 7f).coerceAtMost(1f) * 0.3f
        
        // Factor in recent action frequency
        val recentActionCount = context.recentActions.count { 
            getCurrentTimeMillis() - it.timestamp < 30000 // last 30 seconds
        }
        load += (recentActionCount / 20f).coerceAtMost(1f) * 0.3f
        
        // Factor in multitasking
        val uniqueTargets = context.recentActions.map { it.target }.distinct().size
        load += (uniqueTargets / 5f).coerceAtMost(1f) * 0.2f
        
        // Factor in current focus level (inverse)
        load += (1f - context.focusLevel) * 0.2f
        
        return load.coerceIn(0f, 1f)
    }
    
    override suspend fun assessHandoffReadiness(context: MentalContext): HandoffReadiness {
        val cognitiveLoad = calculateCognitiveLoad(context)
        val lastActionTime = context.recentActions.lastOrNull()?.timestamp ?: 0L
        val timeSinceLastAction = getCurrentTimeMillis() - lastActionTime
        
        val isReady = when {
            cognitiveLoad > 0.8f -> false // Too busy
            timeSinceLastAction < 2000 -> false // Too recent activity
            context.focusLevel < 0.3f -> true // Low focus, good time to handoff
            timeSinceLastAction > 10000 -> true // Been idle
            else -> cognitiveLoad < 0.5f
        }
        
        val confidence = when {
            isReady && timeSinceLastAction > 5000 -> 0.9f
            isReady -> 0.7f
            else -> 0.3f
        }
        
        val reasons = mutableListOf<String>()
        if (cognitiveLoad > 0.8f) reasons.add("High cognitive load")
        if (timeSinceLastAction < 2000) reasons.add("Recent activity")
        if (context.focusLevel < 0.3f) reasons.add("Low focus level")
        if (timeSinceLastAction > 10000) reasons.add("User idle")
        
        return HandoffReadiness(
            isReady = isReady,
            confidence = confidence,
            optimalTiming = if (isReady) Duration.ZERO else Duration.parse("${5000 - timeSinceLastAction}ms"),
            reasons = reasons
        )
    }
    
    override suspend fun optimizeForTargetDevice(
        context: MentalContext, 
        targetDevice: DeviceContext
    ): MentalContext {
        // Adapt context based on target device capabilities
        val adaptedWorkingMemory = when (targetDevice.deviceType) {
            "phone" -> context.workingMemory.take(3) // Smaller screen, less memory
            "tablet" -> context.workingMemory.take(5)
            "desktop" -> context.workingMemory // Full memory
            else -> context.workingMemory.take(4)
        }
        
        val adaptedEnvironmentalFactors = context.environmentalFactors.toMutableMap()
        adaptedEnvironmentalFactors["target_device"] = targetDevice.deviceType
        adaptedEnvironmentalFactors["screen_size"] = "${targetDevice.screenSize.first}x${targetDevice.screenSize.second}"
        
        return context.copy(
            workingMemory = adaptedWorkingMemory,
            environmentalFactors = adaptedEnvironmentalFactors
        )
    }
    
    private fun calculatePatternConfidence(actions: List<ActionType>, pattern: PatternType): Float {
        return when (pattern) {
            PatternType.FOCUSED_WORK -> {
                val typingRatio = actions.count { it == ActionType.TYPE } / actions.size.toFloat()
                typingRatio.coerceIn(0f, 1f)
            }
            PatternType.BROWSING -> {
                val scrollingRatio = actions.count { it == ActionType.SCROLL } / actions.size.toFloat()
                scrollingRatio.coerceIn(0f, 1f)
            }
            PatternType.MULTITASKING -> {
                val diversity = actions.distinct().size / actions.size.toFloat()
                diversity.coerceIn(0f, 1f)
            }
            else -> 0.5f
        }
    }
    
    private fun extractTriggers(actions: List<UserAction>): List<String> {
        return actions
            .groupBy { it.target }
            .filter { it.value.size > 1 }
            .keys
            .toList()
    }
}