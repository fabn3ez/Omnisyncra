package com.omnisyncra.core.ui

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.*
import com.omnisyncra.core.discovery.DeviceDiscovery
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.state.DistributedStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class OmnisyncraUIOrchestrator(
    private val platform: Platform,
    private val deviceDiscovery: DeviceDiscovery,
    private val stateManager: DistributedStateManager,
    private val nodeId: Uuid
) : UIOrchestrator {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _currentUIState = MutableStateFlow(createInitialUIState())
    override val currentUIState: StateFlow<UIState> = _currentUIState.asStateFlow()
    
    private val _proximityTriggers = MutableSharedFlow<ProximityTrigger>()
    override val proximityTriggers: Flow<ProximityTrigger> = _proximityTriggers.asSharedFlow()
    
    private val _layoutTransitions = MutableSharedFlow<LayoutTransition>()
    override val layoutTransitions: Flow<LayoutTransition> = _layoutTransitions.asSharedFlow()
    
    private var currentRole = DeviceRole.PRIMARY
    private val proximityHistory = mutableMapOf<Uuid, ProximityDistance>()
    
    init {
        startProximityMonitoring()
        startContextMonitoring()
    }
    
    override suspend fun handleProximityChange(deviceId: Uuid, proximityInfo: ProximityInfo) {
        val previousDistance = proximityHistory[deviceId]
        val currentDistance = proximityInfo.distance
        
        proximityHistory[deviceId] = currentDistance
        
        val suggestedAction = determineProximityAction(
            deviceId = deviceId,
            previousDistance = previousDistance,
            currentDistance = currentDistance
        )
        
        val trigger = ProximityTrigger(
            deviceId = deviceId,
            previousDistance = previousDistance,
            currentDistance = currentDistance,
            suggestedAction = suggestedAction,
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        _proximityTriggers.emit(trigger)
        
        // Execute suggested action
        when (suggestedAction) {
            ProximityAction.BECOME_SECONDARY -> {
                requestRoleChange(DeviceRole.SECONDARY, RoleChangeReason.PROXIMITY_DETECTED)
            }
            ProximityAction.BECOME_PRIMARY -> {
                requestRoleChange(DeviceRole.PRIMARY, RoleChangeReason.PROXIMITY_DETECTED)
            }
            ProximityAction.SHOW_CONTEXT_PALETTE -> {
                transitionToContextPalette(deviceId)
            }
            ProximityAction.HIDE_CONTEXT_PALETTE -> {
                transitionFromContextPalette()
            }
            ProximityAction.ESTABLISH_CONNECTION -> {
                deviceDiscovery.connectToDevice(deviceId)
            }
            ProximityAction.DISCONNECT -> {
                deviceDiscovery.disconnectFromDevice(deviceId)
            }
            ProximityAction.MAINTAIN_CURRENT -> {
                // No action needed
            }
        }
    }
    
    override suspend fun requestRoleChange(targetRole: DeviceRole, reason: RoleChangeReason) {
        if (currentRole == targetRole) return
        
        val currentState = _currentUIState.value
        val newMode = when (targetRole) {
            DeviceRole.PRIMARY -> UIMode.PRIMARY
            DeviceRole.SECONDARY -> UIMode.SECONDARY
            DeviceRole.COMPUTE_NODE -> UIMode.STANDALONE
            DeviceRole.VIEWER -> UIMode.VIEWER
            DeviceRole.BRIDGE -> UIMode.STANDALONE
        }
        
        val newLayout = adaptLayoutForMode(newMode, currentState.adaptiveLayout)
        
        val transition = LayoutTransition(
            fromLayout = currentState.adaptiveLayout,
            toLayout = newLayout,
            transitionType = TransitionType.ROLE_CHANGE,
            duration = calculateTransitionDuration(currentState.currentMode, newMode),
            easing = TransitionEasing.EASE_IN_OUT
        )
        
        _layoutTransitions.emit(transition)
        
        val newUIState = currentState.copy(
            currentMode = newMode,
            adaptiveLayout = newLayout,
            animations = AnimationState(
                isTransitioning = true,
                transitionType = TransitionType.ROLE_CHANGE,
                progress = 0f
            )
        )
        
        _currentUIState.value = newUIState
        currentRole = targetRole
        
        // Animate transition
        animateTransition(transition)
    }
    
    override suspend fun triggerLayoutAdaptation(trigger: AdaptationTrigger) {
        val currentState = _currentUIState.value
        val newLayout = when (trigger.type) {
            AdaptationType.SCREEN_SIZE_CHANGE -> adaptToScreenSize(currentState.adaptiveLayout)
            AdaptationType.ORIENTATION_CHANGE -> adaptToOrientation(currentState.adaptiveLayout)
            AdaptationType.PROXIMITY_CHANGE -> adaptToProximity(currentState.adaptiveLayout, trigger)
            AdaptationType.CONTEXT_SWITCH -> adaptToContext(currentState.adaptiveLayout, trigger)
            AdaptationType.DEVICE_CONNECT -> adaptToDeviceConnection(currentState.adaptiveLayout, trigger)
            AdaptationType.DEVICE_DISCONNECT -> adaptToDeviceDisconnection(currentState.adaptiveLayout, trigger)
            AdaptationType.PERFORMANCE_OPTIMIZATION -> optimizeForPerformance(currentState.adaptiveLayout)
            AdaptationType.USER_PREFERENCE -> adaptToUserPreference(currentState.adaptiveLayout, trigger)
        }
        
        if (newLayout != currentState.adaptiveLayout) {
            val transition = LayoutTransition(
                fromLayout = currentState.adaptiveLayout,
                toLayout = newLayout,
                transitionType = TransitionType.LAYOUT_ADAPT,
                duration = when (trigger.priority) {
                    AdaptationPriority.IMMEDIATE -> 150L
                    AdaptationPriority.HIGH -> 300L
                    AdaptationPriority.NORMAL -> 500L
                    AdaptationPriority.LOW -> 800L
                },
                easing = TransitionEasing.SPRING
            )
            
            _layoutTransitions.emit(transition)
            
            _currentUIState.value = currentState.copy(
                adaptiveLayout = newLayout,
                animations = AnimationState(
                    isTransitioning = true,
                    transitionType = TransitionType.LAYOUT_ADAPT,
                    progress = 0f
                )
            )
            
            animateTransition(transition)
        }
    }
    
    override suspend fun synchronizeUIState(targetDevices: List<Uuid>) {
        // Synchronize UI state across connected devices
        val currentState = _currentUIState.value
        
        // In a real implementation, this would send the UI state to target devices
        // For now, we'll simulate the synchronization
        delay(100) // Simulate network delay
        
        // Update state to reflect synchronization
        _currentUIState.value = currentState.copy(
            animations = currentState.animations.copy(
                isTransitioning = false,
                transitionType = null,
                progress = 1f
            )
        )
    }
    
    override fun getCurrentRole(): DeviceRole = currentRole
    
    override fun getAdaptiveLayout(): AdaptiveLayout = _currentUIState.value.adaptiveLayout
    
    override fun canTransitionTo(targetMode: UIMode): Boolean {
        val currentMode = _currentUIState.value.currentMode
        
        return when (currentMode) {
            UIMode.STANDALONE -> true // Can transition to any mode
            UIMode.PRIMARY -> targetMode in listOf(UIMode.SECONDARY, UIMode.VIEWER, UIMode.STANDALONE)
            UIMode.SECONDARY -> targetMode in listOf(UIMode.PRIMARY, UIMode.CONTEXT_PALETTE, UIMode.STANDALONE)
            UIMode.CONTEXT_PALETTE -> targetMode in listOf(UIMode.SECONDARY, UIMode.VIEWER)
            UIMode.VIEWER -> targetMode in listOf(UIMode.PRIMARY, UIMode.SECONDARY)
            UIMode.TRANSITIONING -> false // Cannot transition while already transitioning
        }
    }
    
    private fun startProximityMonitoring() {
        scope.launch {
            deviceDiscovery.proximityUpdates.collect { proximityUpdate ->
                handleProximityChange(proximityUpdate.deviceId, proximityUpdate.proximityInfo)
            }
        }
    }
    
    private fun startContextMonitoring() {
        scope.launch {
            stateManager.omnisyncraState.collect { state ->
                state?.let { omnisyncraState ->
                    // Monitor context changes and adapt UI accordingly
                    val activeContext = omnisyncraState.contextGraph.getActiveContext()
                    activeContext?.let { context ->
                        adaptToContextType(context.type)
                    }
                }
            }
        }
    }
    
    private suspend fun transitionToContextPalette(nearbyDeviceId: Uuid) {
        val currentState = _currentUIState.value
        
        if (currentState.currentMode == UIMode.CONTEXT_PALETTE) return
        
        val paletteLayout = createContextPaletteLayout(currentState.adaptiveLayout)
        
        val transition = LayoutTransition(
            fromLayout = currentState.adaptiveLayout,
            toLayout = paletteLayout,
            transitionType = TransitionType.DEVICE_CONNECT,
            duration = 400L,
            easing = TransitionEasing.EASE_IN_OUT
        )
        
        _layoutTransitions.emit(transition)
        
        _currentUIState.value = currentState.copy(
            currentMode = UIMode.CONTEXT_PALETTE,
            adaptiveLayout = paletteLayout,
            animations = AnimationState(
                isTransitioning = true,
                transitionType = TransitionType.DEVICE_CONNECT,
                progress = 0f
            )
        )
        
        animateTransition(transition)
    }
    
    private suspend fun transitionFromContextPalette() {
        val currentState = _currentUIState.value
        
        if (currentState.currentMode != UIMode.CONTEXT_PALETTE) return
        
        val normalLayout = createNormalLayout(currentState.adaptiveLayout)
        
        val transition = LayoutTransition(
            fromLayout = currentState.adaptiveLayout,
            toLayout = normalLayout,
            transitionType = TransitionType.DEVICE_DISCONNECT,
            duration = 300L,
            easing = TransitionEasing.EASE_OUT
        )
        
        _layoutTransitions.emit(transition)
        
        _currentUIState.value = currentState.copy(
            currentMode = UIMode.SECONDARY,
            adaptiveLayout = normalLayout,
            animations = AnimationState(
                isTransitioning = true,
                transitionType = TransitionType.DEVICE_DISCONNECT,
                progress = 0f
            )
        )
        
        animateTransition(transition)
    }
    
    private suspend fun animateTransition(transition: LayoutTransition) {
        val steps = 20
        val stepDuration = transition.duration / steps
        
        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val easedProgress = applyEasing(progress, transition.easing)
            
            val currentState = _currentUIState.value
            _currentUIState.value = currentState.copy(
                animations = currentState.animations.copy(
                    progress = easedProgress
                )
            )
            
            delay(stepDuration)
        }
        
        // Complete transition
        val finalState = _currentUIState.value
        _currentUIState.value = finalState.copy(
            animations = AnimationState(
                isTransitioning = false,
                transitionType = null,
                progress = 1f
            )
        )
    }
    
    private fun determineProximityAction(
        deviceId: Uuid,
        previousDistance: ProximityDistance?,
        currentDistance: ProximityDistance
    ): ProximityAction {
        val connectedDevices = deviceDiscovery.getConnectedDevices()
        val isConnected = connectedDevices.any { it.id == deviceId }
        
        return when {
            // Device moved from far/unknown to immediate/near
            previousDistance in listOf(ProximityDistance.FAR, ProximityDistance.UNKNOWN, null) &&
            currentDistance in listOf(ProximityDistance.IMMEDIATE, ProximityDistance.NEAR) -> {
                if (currentRole == DeviceRole.PRIMARY) {
                    ProximityAction.SHOW_CONTEXT_PALETTE
                } else {
                    ProximityAction.ESTABLISH_CONNECTION
                }
            }
            
            // Device moved from immediate/near to far/unknown
            previousDistance in listOf(ProximityDistance.IMMEDIATE, ProximityDistance.NEAR) &&
            currentDistance in listOf(ProximityDistance.FAR, ProximityDistance.UNKNOWN) -> {
                if (isConnected) {
                    ProximityAction.HIDE_CONTEXT_PALETTE
                } else {
                    ProximityAction.MAINTAIN_CURRENT
                }
            }
            
            // Device is very close and we're not primary
            currentDistance == ProximityDistance.IMMEDIATE && currentRole != DeviceRole.PRIMARY -> {
                ProximityAction.BECOME_SECONDARY
            }
            
            // Device moved away and we should become primary
            currentDistance == ProximityDistance.FAR && currentRole == DeviceRole.SECONDARY -> {
                ProximityAction.BECOME_PRIMARY
            }
            
            else -> ProximityAction.MAINTAIN_CURRENT
        }
    }
    
    private fun createInitialUIState(): UIState {
        val screenDimensions = platform.getScreenDimensions()
        
        return UIState(
            currentMode = UIMode.STANDALONE,
            adaptiveLayout = AdaptiveLayout(
                screenConfiguration = ScreenConfiguration(
                    width = screenDimensions.first,
                    height = screenDimensions.second,
                    density = 1.0f,
                    orientation = if (screenDimensions.first > screenDimensions.second) {
                        Orientation.LANDSCAPE
                    } else {
                        Orientation.PORTRAIT
                    }
                ),
                availableSpace = LayoutSpace(
                    primary = 1.0f,
                    secondary = 0.0f,
                    tertiary = 0.0f
                ),
                contentPriority = listOf(
                    ContentType.MAIN_CONTENT,
                    ContentType.NAVIGATION,
                    ContentType.STATUS_INFO
                )
            ),
            theme = ThemeState(
                isDarkMode = true,
                accentColor = "#6366F1",
                proximityIndicatorStyle = ProximityStyle.AMBIENT
            )
        )
    }
    
    private fun adaptLayoutForMode(mode: UIMode, currentLayout: AdaptiveLayout): AdaptiveLayout {
        return when (mode) {
            UIMode.PRIMARY -> currentLayout.copy(
                availableSpace = LayoutSpace(primary = 0.8f, secondary = 0.2f, tertiary = 0.0f),
                contentPriority = listOf(
                    ContentType.MAIN_CONTENT,
                    ContentType.CONTEXT_TOOLS,
                    ContentType.NAVIGATION
                )
            )
            UIMode.SECONDARY -> currentLayout.copy(
                availableSpace = LayoutSpace(primary = 0.6f, secondary = 0.4f, tertiary = 0.0f),
                contentPriority = listOf(
                    ContentType.CONTEXT_TOOLS,
                    ContentType.MAIN_CONTENT,
                    ContentType.STATUS_INFO
                )
            )
            UIMode.CONTEXT_PALETTE -> currentLayout.copy(
                availableSpace = LayoutSpace(primary = 0.3f, secondary = 0.7f, tertiary = 0.0f),
                contentPriority = listOf(
                    ContentType.RESOURCE_PALETTE,
                    ContentType.CONTEXT_TOOLS,
                    ContentType.STATUS_INFO
                )
            )
            UIMode.VIEWER -> currentLayout.copy(
                availableSpace = LayoutSpace(primary = 1.0f, secondary = 0.0f, tertiary = 0.0f),
                contentPriority = listOf(
                    ContentType.MAIN_CONTENT,
                    ContentType.STATUS_INFO
                )
            )
            else -> currentLayout
        }
    }
    
    private fun createContextPaletteLayout(currentLayout: AdaptiveLayout): AdaptiveLayout {
        return currentLayout.copy(
            availableSpace = LayoutSpace(primary = 0.2f, secondary = 0.8f, tertiary = 0.0f),
            contentPriority = listOf(
                ContentType.RESOURCE_PALETTE,
                ContentType.CONTEXT_TOOLS,
                ContentType.STATUS_INFO,
                ContentType.MAIN_CONTENT
            )
        )
    }
    
    private fun createNormalLayout(currentLayout: AdaptiveLayout): AdaptiveLayout {
        return currentLayout.copy(
            availableSpace = LayoutSpace(primary = 0.7f, secondary = 0.3f, tertiary = 0.0f),
            contentPriority = listOf(
                ContentType.MAIN_CONTENT,
                ContentType.CONTEXT_TOOLS,
                ContentType.NAVIGATION,
                ContentType.STATUS_INFO
            )
        )
    }
    
    private fun adaptToScreenSize(layout: AdaptiveLayout): AdaptiveLayout = layout
    private fun adaptToOrientation(layout: AdaptiveLayout): AdaptiveLayout = layout
    private fun adaptToProximity(layout: AdaptiveLayout, trigger: AdaptationTrigger): AdaptiveLayout = layout
    private fun adaptToContext(layout: AdaptiveLayout, trigger: AdaptationTrigger): AdaptiveLayout = layout
    private fun adaptToDeviceConnection(layout: AdaptiveLayout, trigger: AdaptationTrigger): AdaptiveLayout = layout
    private fun adaptToDeviceDisconnection(layout: AdaptiveLayout, trigger: AdaptationTrigger): AdaptiveLayout = layout
    private fun optimizeForPerformance(layout: AdaptiveLayout): AdaptiveLayout = layout
    private fun adaptToUserPreference(layout: AdaptiveLayout, trigger: AdaptationTrigger): AdaptiveLayout = layout
    
    private suspend fun adaptToContextType(contextType: ContextType) {
        // Adapt UI based on active context type
        val adaptationTrigger = AdaptationTrigger(
            type = AdaptationType.CONTEXT_SWITCH,
            context = mapOf("contextType" to contextType.name),
            priority = AdaptationPriority.NORMAL,
            sourceDeviceId = nodeId
        )
        
        triggerLayoutAdaptation(adaptationTrigger)
    }
    
    private fun calculateTransitionDuration(fromMode: UIMode, toMode: UIMode): Long {
        return when {
            fromMode == UIMode.TRANSITIONING || toMode == UIMode.TRANSITIONING -> 0L
            fromMode == toMode -> 0L
            fromMode == UIMode.STANDALONE -> 300L
            toMode == UIMode.STANDALONE -> 200L
            else -> 400L
        }
    }
    
    private fun applyEasing(progress: Float, easing: TransitionEasing): Float {
        return when (easing) {
            TransitionEasing.LINEAR -> progress
            TransitionEasing.EASE_IN -> progress * progress
            TransitionEasing.EASE_OUT -> 1f - (1f - progress) * (1f - progress)
            TransitionEasing.EASE_IN_OUT -> {
                if (progress < 0.5f) {
                    2f * progress * progress
                } else {
                    1f - 2f * (1f - progress) * (1f - progress)
                }
            }
            TransitionEasing.SPRING -> {
                // Simplified spring easing
                val c4 = (2 * PI) / 3
                if (progress == 0f || progress == 1f) {
                    progress
                } else {
                    -(2.0).pow(10.0 * (progress - 1)).toFloat() * 
                    sin(((progress - 1) * c4).toDouble()).toFloat()
                }
            }
            TransitionEasing.BOUNCE -> {
                // Simplified bounce easing
                val n1 = 7.5625f
                val d1 = 2.75f
                
                when {
                    progress < 1f / d1 -> n1 * progress * progress
                    progress < 2f / d1 -> n1 * (progress - 1.5f / d1) * (progress - 1.5f / d1) + 0.75f
                    progress < 2.5f / d1 -> n1 * (progress - 2.25f / d1) * (progress - 2.25f / d1) + 0.9375f
                    else -> n1 * (progress - 2.625f / d1) * (progress - 2.625f / d1) + 0.984375f
                }
            }
        }
    }
    
    fun cleanup() {
        scope.cancel()
    }
}