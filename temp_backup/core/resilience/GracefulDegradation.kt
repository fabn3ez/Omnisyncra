package com.omnisyncra.core.resilience

import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

enum class SystemMode {
    FULL_FUNCTIONALITY,    // All features working
    DEGRADED_NETWORK,      // Network issues, local-only mode
    DEGRADED_COMPUTE,      // Compute offloading disabled
    DEGRADED_DISCOVERY,    // Device discovery limited
    OFFLINE_MODE,          // No network connectivity
    EMERGENCY_MODE         // Critical failures, minimal functionality
}

data class DegradationEvent(
    val fromMode: SystemMode,
    val toMode: SystemMode,
    val reason: String,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val affectedFeatures: List<String>
)

class GracefulDegradationManager {
    private val _currentMode = MutableStateFlow(SystemMode.FULL_FUNCTIONALITY)
    val currentMode: StateFlow<SystemMode> = _currentMode.asStateFlow()
    
    private val _degradationEvents = MutableSharedFlow<DegradationEvent>()
    val degradationEvents: SharedFlow<DegradationEvent> = _degradationEvents.asSharedFlow()
    
    private val featureAvailability = mutableMapOf<String, Boolean>()
    
    init {
        initializeFeatureAvailability()
    }
    
    suspend fun handleDeviceDisconnection(deviceCount: Int) {
        when {
            deviceCount == 0 -> {
                degradeTo(
                    SystemMode.OFFLINE_MODE,
                    "All devices disconnected",
                    listOf("device_discovery", "compute_offloading", "ui_orchestration")
                )
            }
            deviceCount < 2 -> {
                degradeTo(
                    SystemMode.DEGRADED_NETWORK,
                    "Limited device connectivity",
                    listOf("compute_offloading", "ui_orchestration")
                )
            }
        }
    }
    
    suspend fun handleNetworkFailure(severity: ErrorSeverity) {
        when (severity) {
            ErrorSeverity.CRITICAL -> {
                degradeTo(
                    SystemMode.OFFLINE_MODE,
                    "Critical network failure",
                    listOf("device_discovery", "compute_offloading", "state_sync")
                )
            }
            ErrorSeverity.HIGH -> {
                degradeTo(
                    SystemMode.DEGRADED_NETWORK,
                    "High network latency/errors",
                    listOf("compute_offloading")
                )
            }
            else -> {
                // Minor network issues, maintain current mode
            }
        }
    }
    
    suspend fun handleComputeNodeFailure(availableNodes: Int) {
        if (availableNodes == 0) {
            degradeTo(
                SystemMode.DEGRADED_COMPUTE,
                "No compute nodes available",
                listOf("compute_offloading")
            )
        }
    }
    
    suspend fun handleCriticalError(component: String) {
        degradeTo(
            SystemMode.EMERGENCY_MODE,
            "Critical error in $component",
            listOf("compute_offloading", "device_discovery", "ui_orchestration")
        )
    }
    
    suspend fun attemptRecovery() {
        val currentMode = _currentMode.value
        
        when (currentMode) {
            SystemMode.EMERGENCY_MODE -> {
                // Try to recover to degraded mode
                if (canRecoverTo(SystemMode.DEGRADED_NETWORK)) {
                    recoverTo(SystemMode.DEGRADED_NETWORK, "Emergency recovery successful")
                }
            }
            SystemMode.OFFLINE_MODE -> {
                // Try to recover network connectivity
                if (canRecoverTo(SystemMode.DEGRADED_NETWORK)) {
                    recoverTo(SystemMode.DEGRADED_NETWORK, "Network connectivity restored")
                }
            }
            SystemMode.DEGRADED_NETWORK -> {
                // Try to recover full functionality
                if (canRecoverTo(SystemMode.FULL_FUNCTIONALITY)) {
                    recoverTo(SystemMode.FULL_FUNCTIONALITY, "Full functionality restored")
                }
            }
            SystemMode.DEGRADED_COMPUTE -> {
                // Try to recover compute capabilities
                if (canRecoverTo(SystemMode.FULL_FUNCTIONALITY)) {
                    recoverTo(SystemMode.FULL_FUNCTIONALITY, "Compute nodes restored")
                }
            }
            SystemMode.DEGRADED_DISCOVERY -> {
                // Try to recover discovery capabilities
                if (canRecoverTo(SystemMode.FULL_FUNCTIONALITY)) {
                    recoverTo(SystemMode.FULL_FUNCTIONALITY, "Device discovery restored")
                }
            }
            SystemMode.FULL_FUNCTIONALITY -> {
                // Already at full functionality
            }
        }
    }
    
    fun isFeatureAvailable(feature: String): Boolean {
        return featureAvailability[feature] ?: false
    }
    
    fun getAvailableFeatures(): List<String> {
        return featureAvailability.filter { it.value }.keys.toList()
    }
    
    private suspend fun degradeTo(
        targetMode: SystemMode,
        reason: String,
        affectedFeatures: List<String>
    ) {
        val currentMode = _currentMode.value
        if (currentMode == targetMode) return
        
        // Update feature availability
        affectedFeatures.forEach { feature ->
            featureAvailability[feature] = false
        }
        
        val event = DegradationEvent(
            fromMode = currentMode,
            toMode = targetMode,
            reason = reason,
            affectedFeatures = affectedFeatures
        )
        
        _currentMode.value = targetMode
        _degradationEvents.emit(event)
    }
    
    private suspend fun recoverTo(targetMode: SystemMode, reason: String) {
        val currentMode = _currentMode.value
        if (currentMode == targetMode) return
        
        // Restore features based on target mode
        updateFeatureAvailabilityForMode(targetMode)
        
        val event = DegradationEvent(
            fromMode = currentMode,
            toMode = targetMode,
            reason = reason,
            affectedFeatures = getRestoredFeatures(currentMode, targetMode)
        )
        
        _currentMode.value = targetMode
        _degradationEvents.emit(event)
    }
    
    private fun canRecoverTo(targetMode: SystemMode): Boolean {
        // Simulate recovery checks
        return when (targetMode) {
            SystemMode.FULL_FUNCTIONALITY -> {
                // Check if all systems are healthy
                true // Simplified for demo
            }
            SystemMode.DEGRADED_NETWORK -> {
                // Check if basic connectivity exists
                true
            }
            else -> false
        }
    }
    
    private fun initializeFeatureAvailability() {
        featureAvailability.apply {
            put("device_discovery", true)
            put("compute_offloading", true)
            put("ui_orchestration", true)
            put("state_sync", true)
            put("context_generation", true)
            put("proximity_detection", true)
        }
    }
    
    private fun updateFeatureAvailabilityForMode(mode: SystemMode) {
        when (mode) {
            SystemMode.FULL_FUNCTIONALITY -> {
                featureAvailability.keys.forEach { feature ->
                    featureAvailability[feature] = true
                }
            }
            SystemMode.DEGRADED_NETWORK -> {
                featureAvailability["device_discovery"] = true
                featureAvailability["ui_orchestration"] = false
                featureAvailability["compute_offloading"] = false
                featureAvailability["state_sync"] = false
            }
            SystemMode.DEGRADED_COMPUTE -> {
                featureAvailability["compute_offloading"] = false
            }
            SystemMode.DEGRADED_DISCOVERY -> {
                featureAvailability["device_discovery"] = false
                featureAvailability["proximity_detection"] = false
            }
            SystemMode.OFFLINE_MODE -> {
                featureAvailability.keys.forEach { feature ->
                    featureAvailability[feature] = feature == "context_generation"
                }
            }
            SystemMode.EMERGENCY_MODE -> {
                featureAvailability.keys.forEach { feature ->
                    featureAvailability[feature] = false
                }
            }
        }
    }
    
    private fun getRestoredFeatures(fromMode: SystemMode, toMode: SystemMode): List<String> {
        // Return features that are being restored
        return featureAvailability.filter { it.value }.keys.toList()
    }
}