package com.omnisyncra.core.ui

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.discovery.DeviceDiscovery
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.state.DistributedStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI orchestration for managing application state and user interactions
 */

@kotlinx.serialization.Serializable
data class UIState(
    val isConnected: Boolean = false,
    val activeDevices: Int = 0,
    val currentScreen: String = "dashboard",
    val notifications: List<String> = emptyList()
)

interface UIOrchestrator {
    val uiState: Flow<UIState>
    suspend fun navigateToScreen(screen: String)
    suspend fun addNotification(message: String)
    suspend fun clearNotifications()
}

class OmnisyncraUIOrchestrator(
    private val platform: Platform,
    private val deviceDiscovery: DeviceDiscovery,
    private val stateManager: DistributedStateManager,
    private val nodeId: Uuid
) : UIOrchestrator {
    
    private val _uiState = MutableStateFlow(UIState())
    override val uiState = _uiState.asStateFlow()
    
    init {
        // Initialize with platform info
        _uiState.value = UIState(
            isConnected = platform.isNetworkAvailable(),
            activeDevices = 0,
            currentScreen = "dashboard"
        )
    }
    
    override suspend fun navigateToScreen(screen: String) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
        stateManager.updateState("current_screen", screen)
    }
    
    override suspend fun addNotification(message: String) {
        val current = _uiState.value
        val newNotifications = current.notifications + message
        _uiState.value = current.copy(notifications = newNotifications)
    }
    
    override suspend fun clearNotifications() {
        _uiState.value = _uiState.value.copy(notifications = emptyList())
    }
}