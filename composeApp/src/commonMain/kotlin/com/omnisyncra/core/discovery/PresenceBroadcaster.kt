package com.omnisyncra.core.discovery

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.omnisyncra.core.domain.*
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.platform.TimeUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Real presence broadcasting system that announces device availability and capabilities
 * across the network using actual network protocols
 */
class PresenceBroadcaster(
    private val platform: Platform,
    private val networkAdapter: NetworkAdapter,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _presenceUpdates = MutableSharedFlow<PresenceAnnouncement>()
    val presenceUpdates: SharedFlow<PresenceAnnouncement> = _presenceUpdates.asSharedFlow()
    
    private val _deviceStatusHistory = MutableStateFlow<Map<String, DeviceStatusHistory>>(emptyMap())
    val deviceStatusHistory: StateFlow<Map<String, DeviceStatusHistory>> = _deviceStatusHistory.asStateFlow()
    
    private var broadcastJob: Job? = null
    private var monitoringJob: Job? = null
    private var isActive = false
    
    private var lastPresenceInfo: PresenceInfo? = null
    private val statusChangeListeners = mutableListOf<(String, PresenceStatus, PresenceStatus) -> Unit>()
    
    /**
     * Start real presence broadcasting with actual network announcements
     */
    suspend fun startBroadcasting(deviceId: String, deviceName: String): Result<Unit> {
        return try {
            if (isActive) return Result.success(Unit)
            
            isActive = true
            
            // Start periodic presence broadcasting
            broadcastJob = scope.launch {
                while (isActive) {
                    try {
                        val currentPresence = collectCurrentPresenceInfo()
                        val announcement = PresenceAnnouncement(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            platformName = platform.name,
                            presenceInfo = currentPresence,
                            timestamp = TimeUtils.currentTimeMillis(),
                            capabilities = collectCurrentCapabilities()
                        )
                        
                        // Broadcast presence using real network protocols
                        broadcastPresenceAnnouncement(announcement)
                        
                        // Emit to local listeners
                        _presenceUpdates.emit(announcement)
                        
                        // Check for status changes
                        checkForStatusChanges(deviceId, currentPresence)
                        
                        lastPresenceInfo = currentPresence
                        
                        // Broadcast every 5 seconds as per requirements
                        delay(5000)
                    } catch (e: Exception) {
                        println("Presence broadcasting error: ${e.message}")
                        delay(5000) // Continue broadcasting even on errors
                    }
                }
            }
            
            // Start real-time status change monitoring
            monitoringJob = scope.launch {
                while (isActive) {
                    try {
                        val currentPresence = collectCurrentPresenceInfo()
                        
                        // Check for significant changes that warrant immediate broadcast
                        if (shouldBroadcastImmediately(currentPresence)) {
                            val announcement = PresenceAnnouncement(
                                deviceId = deviceId,
                                deviceName = deviceName,
                                platformName = platform.name,
                                presenceInfo = currentPresence,
                                timestamp = TimeUtils.currentTimeMillis(),
                                capabilities = collectCurrentCapabilities(),
                                isStatusChange = true
                            )
                            
                            broadcastPresenceAnnouncement(announcement)
                            _presenceUpdates.emit(announcement)
                            
                            lastPresenceInfo = currentPresence
                        }
                        
                        // Monitor every 2 seconds for status changes
                        delay(2000)
                    } catch (e: Exception) {
                        println("Status monitoring error: ${e.message}")
                        delay(2000)
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Stop presence broadcasting
     */
    suspend fun stopBroadcasting(): Result<Unit> {
        isActive = false
        broadcastJob?.cancel()
        monitoringJob?.cancel()
        return Result.success(Unit)
    }
    
    /**
     * Add a listener for status changes
     */
    fun addStatusChangeListener(listener: (deviceId: String, fromStatus: PresenceStatus, toStatus: PresenceStatus) -> Unit) {
        statusChangeListeners.add(listener)
    }
    
    /**
     * Enhanced device presence update processing with immediate metadata updates
     * and comprehensive status lifecycle management
     */
    suspend fun updateDevicePresence(deviceId: String, presenceInfo: PresenceInfo) {
        val currentHistory = _deviceStatusHistory.value[deviceId] ?: DeviceStatusHistory(deviceId)
        val currentTime = TimeUtils.currentTimeMillis()
        
        // Check for status change with enhanced lifecycle tracking
        val lastStatus = currentHistory.statusChanges.lastOrNull()?.toStatus ?: PresenceStatus.OFFLINE
        val hasStatusChanged = lastStatus != presenceInfo.status
        
        // Enhanced status change tracking with detailed reasons
        val statusChangeReason = when {
            hasStatusChanged && presenceInfo.status == PresenceStatus.OFFLINE -> "Device went offline"
            hasStatusChanged && presenceInfo.status == PresenceStatus.ONLINE && lastStatus == PresenceStatus.OFFLINE -> "Device came online"
            hasStatusChanged && presenceInfo.status == PresenceStatus.BUSY -> "Device became busy (CPU: ${(presenceInfo.cpuUsage * 100).toInt()}%)"
            hasStatusChanged && presenceInfo.status == PresenceStatus.IDLE -> "Device became idle (Load: ${(presenceInfo.currentLoad * 100).toInt()}%)"
            hasStatusChanged && presenceInfo.status == PresenceStatus.AWAY -> "Device went away"
            hasStatusChanged -> "Status changed from $lastStatus to ${presenceInfo.status}"
            else -> "Regular presence update"
        }
        
        // Create enhanced status change with metadata
        val updatedHistory = if (hasStatusChanged) {
            val statusChange = StatusChange(
                timestamp = currentTime,
                fromStatus = lastStatus,
                toStatus = presenceInfo.status,
                reason = statusChangeReason
            )
            
            currentHistory.copy(
                statusChanges = currentHistory.statusChanges + statusChange,
                lastPresenceUpdate = currentTime
            )
        } else {
            // Even without status change, update the last presence timestamp
            currentHistory.copy(lastPresenceUpdate = currentTime)
        }
        
        // Update device status history map
        val updatedMap = _deviceStatusHistory.value + (deviceId to updatedHistory)
        _deviceStatusHistory.value = updatedMap
        
        // Enhanced status change notifications with detailed context
        if (hasStatusChanged) {
            statusChangeListeners.forEach { listener ->
                try {
                    listener(deviceId, lastStatus, presenceInfo.status)
                } catch (e: Exception) {
                    println("Status change listener error: ${e.message}")
                }
            }
            
            // Log detailed status lifecycle event
            logStatusLifecycleEvent(deviceId, lastStatus, presenceInfo.status, presenceInfo, statusChangeReason)
        }
        
        // Immediate metadata processing for real-time updates
        processImmediateMetadataUpdates(deviceId, presenceInfo, hasStatusChanged)
    }
    
    /**
     * Process immediate metadata updates from real presence data
     */
    private suspend fun processImmediateMetadataUpdates(deviceId: String, presenceInfo: PresenceInfo, statusChanged: Boolean) {
        try {
            // Create comprehensive metadata from presence info
            val immediateMetadata = mutableMapOf<String, String>()
            
            // Core presence metadata
            immediateMetadata["status"] = presenceInfo.status.name.lowercase()
            immediateMetadata["last_seen"] = presenceInfo.lastSeen.toString()
            immediateMetadata["available_for_tasks"] = presenceInfo.availableForTasks.toString()
            
            // Performance metrics metadata
            immediateMetadata["cpu_usage_percent"] = "${(presenceInfo.cpuUsage * 100).toInt()}"
            immediateMetadata["memory_usage_percent"] = "${(presenceInfo.memoryUsage * 100).toInt()}"
            immediateMetadata["system_load_percent"] = "${(presenceInfo.currentLoad * 100).toInt()}"
            
            // Network and connectivity metadata
            immediateMetadata["network_bandwidth_mbps"] = "${presenceInfo.networkBandwidth / (1024 * 1024)}"
            
            // Battery metadata (if available)
            presenceInfo.batteryLevel?.let { battery ->
                immediateMetadata["battery_level_percent"] = "${(battery * 100).toInt()}"
                immediateMetadata["battery_status"] = when {
                    battery > 0.8f -> "high"
                    battery > 0.5f -> "medium"
                    battery > 0.2f -> "low"
                    else -> "critical"
                }
            }
            
            // Storage metadata (if available)
            presenceInfo.storageInfo?.let { storage ->
                immediateMetadata["storage_total_gb"] = "${storage.totalSpace / (1024 * 1024 * 1024)}"
                immediateMetadata["storage_available_gb"] = "${storage.availableSpace / (1024 * 1024 * 1024)}"
                immediateMetadata["storage_used_percent"] = "${((storage.usedSpace.toFloat() / storage.totalSpace.toFloat()) * 100).toInt()}"
            }
            
            // Device capability metadata
            immediateMetadata["processing_capacity"] = when {
                presenceInfo.cpuUsage < 0.3f -> "high"
                presenceInfo.cpuUsage < 0.7f -> "medium"
                else -> "low"
            }
            
            immediateMetadata["memory_pressure"] = when {
                presenceInfo.memoryUsage < 0.5f -> "low"
                presenceInfo.memoryUsage < 0.8f -> "medium"
                else -> "high"
            }
            
            // Availability scoring
            val availabilityScore = calculateAvailabilityScore(presenceInfo)
            immediateMetadata["availability_score"] = "${(availabilityScore * 100).toInt()}"
            immediateMetadata["availability_rating"] = when {
                availabilityScore > 0.8f -> "excellent"
                availabilityScore > 0.6f -> "good"
                availabilityScore > 0.4f -> "fair"
                else -> "poor"
            }
            
            // Add all custom capabilities
            immediateMetadata.putAll(presenceInfo.capabilities)
            
            // Timestamp for metadata freshness
            immediateMetadata["metadata_updated"] = TimeUtils.currentTimeMillis().toString()
            immediateMetadata["status_changed"] = statusChanged.toString()
            
            // Store processed metadata for immediate access
            storeProcessedMetadata(deviceId, immediateMetadata)
            
            println("📊 Processed immediate metadata for device $deviceId: ${immediateMetadata.size} fields updated")
            
        } catch (e: Exception) {
            println("Error processing immediate metadata updates: ${e.message}")
        }
    }
    
    /**
     * Calculate device availability score based on presence information
     */
    private fun calculateAvailabilityScore(presenceInfo: PresenceInfo): Float {
        var score = 1.0f
        
        // Status impact
        score *= when (presenceInfo.status) {
            PresenceStatus.ONLINE -> 1.0f
            PresenceStatus.IDLE -> 0.9f
            PresenceStatus.BUSY -> 0.6f
            PresenceStatus.AWAY -> 0.3f
            PresenceStatus.OFFLINE -> 0.0f
        }
        
        // CPU usage impact
        score *= (1.0f - presenceInfo.cpuUsage * 0.5f).coerceAtLeast(0.1f)
        
        // Memory usage impact
        score *= (1.0f - presenceInfo.memoryUsage * 0.3f).coerceAtLeast(0.2f)
        
        // System load impact
        score *= (1.0f - presenceInfo.currentLoad * 0.4f).coerceAtLeast(0.1f)
        
        // Battery impact (if applicable)
        presenceInfo.batteryLevel?.let { battery ->
            if (battery < 0.2f) score *= 0.5f // Low battery significantly impacts availability
            else if (battery < 0.5f) score *= 0.8f // Medium battery moderately impacts availability
        }
        
        // Task availability
        if (!presenceInfo.availableForTasks) score *= 0.3f
        
        return score.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Store processed metadata for immediate access by other components
     */
    private val _processedMetadata = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val processedMetadata: StateFlow<Map<String, Map<String, String>>> = _processedMetadata.asStateFlow()
    
    private suspend fun storeProcessedMetadata(deviceId: String, metadata: Map<String, String>) {
        val currentMetadata = _processedMetadata.value.toMutableMap()
        currentMetadata[deviceId] = metadata
        _processedMetadata.value = currentMetadata
    }
    
    /**
     * Get immediate metadata for a specific device
     */
    fun getImmediateMetadata(deviceId: String): Map<String, String> {
        return _processedMetadata.value[deviceId] ?: emptyMap()
    }
    
    /**
     * Log detailed status lifecycle events for monitoring and debugging
     */
    private fun logStatusLifecycleEvent(
        deviceId: String, 
        fromStatus: PresenceStatus, 
        toStatus: PresenceStatus, 
        presenceInfo: PresenceInfo,
        reason: String
    ) {
        val statusIcon = when (toStatus) {
            PresenceStatus.ONLINE -> "🟢"
            PresenceStatus.BUSY -> "🔴"
            PresenceStatus.IDLE -> "🟡"
            PresenceStatus.AWAY -> "🟠"
            PresenceStatus.OFFLINE -> "⚫"
        }
        
        println("🔄 $statusIcon Device Lifecycle Event: $deviceId")
        println("   Status: $fromStatus → $toStatus")
        println("   Reason: $reason")
        println("   CPU: ${(presenceInfo.cpuUsage * 100).toInt()}%, Memory: ${(presenceInfo.memoryUsage * 100).toInt()}%, Load: ${(presenceInfo.currentLoad * 100).toInt()}%")
        println("   Available: ${presenceInfo.availableForTasks}, Battery: ${presenceInfo.batteryLevel?.let { "${(it * 100).toInt()}%" } ?: "N/A"}")
        println("   Timestamp: ${TimeUtils.currentTimeMillis()}")
    }
    
    /**
     * Get connection session history for a device
     */
    fun getConnectionSessions(deviceId: String): List<ConnectionSession> {
        return _deviceStatusHistory.value[deviceId]?.connectionSessions ?: emptyList()
    }
    
    /**
     * Enhanced connection session tracking with comprehensive metrics
     */
    suspend fun startConnectionSession(deviceId: String, connectionType: String = "unknown"): String {
        val sessionId = "session-${TimeUtils.currentTimeMillis()}-${deviceId.take(8)}"
        val session = ConnectionSession(
            sessionId = sessionId,
            deviceId = deviceId,
            startTime = TimeUtils.currentTimeMillis()
        )
        
        val currentHistory = _deviceStatusHistory.value[deviceId] ?: DeviceStatusHistory(deviceId)
        val updatedHistory = currentHistory.copy(
            connectionSessions = currentHistory.connectionSessions + session
        )
        
        val updatedMap = _deviceStatusHistory.value + (deviceId to updatedHistory)
        _deviceStatusHistory.value = updatedMap
        
        // Log session start with context
        println("🔗 Started connection session: $sessionId")
        println("   Device: $deviceId")
        println("   Type: $connectionType")
        println("   Start Time: ${TimeUtils.currentTimeMillis()}")
        
        return sessionId
    }
    
    /**
     * Enhanced connection session termination with comprehensive metrics
     */
    suspend fun endConnectionSession(
        sessionId: String, 
        messagesExchanged: Int = 0, 
        averageLatency: Float = 0.0f,
        disconnectionReason: String = "Normal termination",
        dataTransferred: Long = 0L
    ) {
        val currentHistoryMap = _deviceStatusHistory.value.toMutableMap()
        
        for ((deviceId, history) in currentHistoryMap) {
            val sessionIndex = history.connectionSessions.indexOfFirst { it.sessionId == sessionId }
            if (sessionIndex >= 0) {
                val session = history.connectionSessions[sessionIndex]
                val endTime = TimeUtils.currentTimeMillis()
                val duration = endTime - session.startTime
                
                // Enhanced connection quality determination
                val connectionQuality = determineEnhancedConnectionQuality(
                    averageLatency, 
                    messagesExchanged, 
                    duration,
                    dataTransferred,
                    disconnectionReason
                )
                
                val updatedSession = session.copy(
                    endTime = endTime,
                    duration = duration,
                    messagesExchanged = messagesExchanged,
                    averageLatency = averageLatency,
                    connectionQuality = connectionQuality
                )
                
                val updatedSessions = history.connectionSessions.toMutableList()
                updatedSessions[sessionIndex] = updatedSession
                
                val updatedHistory = history.copy(connectionSessions = updatedSessions)
                currentHistoryMap[deviceId] = updatedHistory
                
                // Log detailed session end
                logConnectionSessionEnd(sessionId, deviceId, updatedSession, disconnectionReason, dataTransferred)
                
                // Update connection session analytics
                updateConnectionSessionAnalytics(deviceId, updatedSession)
                
                break
            }
        }
        
        _deviceStatusHistory.value = currentHistoryMap
    }
    
    /**
     * Enhanced connection quality determination with multiple factors
     */
    private fun determineEnhancedConnectionQuality(
        averageLatency: Float, 
        messagesExchanged: Int, 
        duration: Long,
        dataTransferred: Long,
        disconnectionReason: String
    ): ConnectionQuality {
        var qualityScore = 1.0f
        
        // Latency factor (40% weight)
        val latencyScore = when {
            averageLatency <= 50f -> 1.0f
            averageLatency <= 100f -> 0.8f
            averageLatency <= 200f -> 0.6f
            averageLatency <= 500f -> 0.4f
            else -> 0.2f
        }
        qualityScore *= latencyScore * 0.4f
        
        // Message throughput factor (25% weight)
        val throughputScore = if (duration > 0) {
            val messagesPerSecond = messagesExchanged.toFloat() / (duration / 1000f)
            when {
                messagesPerSecond >= 10f -> 1.0f
                messagesPerSecond >= 5f -> 0.8f
                messagesPerSecond >= 2f -> 0.6f
                messagesPerSecond >= 1f -> 0.4f
                else -> 0.2f
            }
        } else 0.5f
        qualityScore += throughputScore * 0.25f
        
        // Connection stability factor (20% weight)
        val stabilityScore = when (disconnectionReason.lowercase()) {
            "normal termination", "user initiated" -> 1.0f
            "timeout", "network error" -> 0.3f
            "protocol error", "authentication failed" -> 0.1f
            else -> 0.6f
        }
        qualityScore += stabilityScore * 0.2f
        
        // Data transfer efficiency factor (15% weight)
        val dataScore = if (duration > 0 && dataTransferred > 0) {
            val bytesPerSecond = dataTransferred.toFloat() / (duration / 1000f)
            when {
                bytesPerSecond >= 1024 * 1024 -> 1.0f // >= 1MB/s
                bytesPerSecond >= 512 * 1024 -> 0.8f  // >= 512KB/s
                bytesPerSecond >= 100 * 1024 -> 0.6f  // >= 100KB/s
                bytesPerSecond >= 10 * 1024 -> 0.4f   // >= 10KB/s
                else -> 0.2f
            }
        } else 0.5f
        qualityScore += dataScore * 0.15f
        
        // Convert score to quality enum
        return when {
            qualityScore >= 0.9f -> ConnectionQuality.EXCELLENT
            qualityScore >= 0.7f -> ConnectionQuality.GOOD
            qualityScore >= 0.5f -> ConnectionQuality.FAIR
            qualityScore >= 0.3f -> ConnectionQuality.POOR
            else -> ConnectionQuality.UNKNOWN
        }
    }
    
    /**
     * Log detailed connection session end information
     */
    private fun logConnectionSessionEnd(
        sessionId: String,
        deviceId: String,
        session: ConnectionSession,
        disconnectionReason: String,
        dataTransferred: Long
    ) {
        val qualityIcon = when (session.connectionQuality) {
            ConnectionQuality.EXCELLENT -> "🟢"
            ConnectionQuality.GOOD -> "🟡"
            ConnectionQuality.FAIR -> "🟠"
            ConnectionQuality.POOR -> "🔴"
            ConnectionQuality.UNKNOWN -> "⚪"
        }
        
        println("🔌 $qualityIcon Connection session ended: $sessionId")
        println("   Device: $deviceId")
        println("   Duration: ${session.duration}ms (${session.duration / 1000}s)")
        println("   Messages: ${session.messagesExchanged}")
        println("   Avg Latency: ${session.averageLatency}ms")
        println("   Quality: ${session.connectionQuality}")
        println("   Data Transferred: ${dataTransferred} bytes")
        println("   Reason: $disconnectionReason")
    }
    
    /**
     * Connection session analytics storage
     */
    private val _connectionAnalytics = MutableStateFlow<Map<String, ConnectionAnalytics>>(emptyMap())
    val connectionAnalytics: StateFlow<Map<String, ConnectionAnalytics>> = _connectionAnalytics.asStateFlow()
    
    /**
     * Update connection session analytics for a device
     */
    private suspend fun updateConnectionSessionAnalytics(deviceId: String, session: ConnectionSession) {
        val currentAnalytics = _connectionAnalytics.value[deviceId] ?: ConnectionAnalytics(deviceId)
        
        val updatedAnalytics = currentAnalytics.copy(
            totalSessions = currentAnalytics.totalSessions + 1,
            totalConnectionTime = currentAnalytics.totalConnectionTime + session.duration,
            totalMessagesExchanged = currentAnalytics.totalMessagesExchanged + session.messagesExchanged,
            averageLatency = if (currentAnalytics.totalSessions > 0) {
                (currentAnalytics.averageLatency * currentAnalytics.totalSessions + session.averageLatency) / (currentAnalytics.totalSessions + 1)
            } else {
                session.averageLatency
            },
            lastConnectionTime = session.startTime,
            connectionQualityDistribution = updateQualityDistribution(
                currentAnalytics.connectionQualityDistribution,
                session.connectionQuality
            )
        )
        
        val updatedMap = _connectionAnalytics.value + (deviceId to updatedAnalytics)
        _connectionAnalytics.value = updatedMap
    }
    
    /**
     * Update connection quality distribution statistics
     */
    private fun updateQualityDistribution(
        current: Map<ConnectionQuality, Int>,
        newQuality: ConnectionQuality
    ): Map<ConnectionQuality, Int> {
        return current + (newQuality to (current[newQuality] ?: 0) + 1)
    }
    
    /**
     * Get comprehensive connection analytics for a device
     */
    fun getConnectionAnalytics(deviceId: String): ConnectionAnalytics? {
        return _connectionAnalytics.value[deviceId]
    }
    
    /**
     * Get connection session summary for all devices
     */
    fun getAllConnectionAnalytics(): Map<String, ConnectionAnalytics> {
        return _connectionAnalytics.value
    }
    
    /**
     * Collect current real device capabilities from platform
     */
    private suspend fun collectCurrentCapabilities(): Map<String, String> {
        return try {
            val storageInfo = platform.getStorageInfo()
            mapOf(
                "platform" to platform.name,
                "cpu_cores" to "4", // Default value since we don't have a direct method
                "total_memory" to platform.getTotalMemory().toString(),
                "available_memory" to platform.getAvailableMemory().toString(),
                "total_storage" to storageInfo.totalSpace.toString(),
                "available_storage" to storageInfo.availableSpace.toString(),
                "network_bandwidth" to platform.getNetworkBandwidth().toString(),
                "battery_supported" to (platform.getBatteryLevel() != null).toString(),
                "network_available" to platform.isNetworkAvailable().toString()
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to collect capabilities: ${e.message}")
        }
    }
    
    /**
     * Collect current real presence information from platform
     */
    private suspend fun collectCurrentPresenceInfo(): PresenceInfo {
        return try {
            val cpuUsage = platform.getCurrentCpuUsage()
            val totalMemory = platform.getTotalMemory()
            val availableMemory = platform.getAvailableMemory()
            val memoryUsage = if (totalMemory > 0) {
                1.0f - (availableMemory.toFloat() / totalMemory.toFloat())
            } else {
                0.0f
            }
            val batteryLevel = platform.getBatteryLevel()
            val systemLoad = platform.getSystemLoad()
            val isAvailable = platform.isAvailableForTasks()
            val storageInfo = platform.getStorageInfo()
            val networkBandwidth = platform.getNetworkBandwidth()
            
            // Determine status based on system metrics
            val status = when {
                !platform.isNetworkAvailable() -> PresenceStatus.OFFLINE
                systemLoad > 0.9f || cpuUsage > 0.9f -> PresenceStatus.BUSY
                !isAvailable -> PresenceStatus.AWAY
                systemLoad < 0.3f && cpuUsage < 0.3f -> PresenceStatus.IDLE
                else -> PresenceStatus.ONLINE
            }
            
            PresenceInfo(
                status = status,
                lastSeen = TimeUtils.currentTimeMillis(),
                batteryLevel = batteryLevel,
                currentLoad = systemLoad,
                availableForTasks = isAvailable,
                cpuUsage = cpuUsage,
                memoryUsage = memoryUsage,
                networkBandwidth = networkBandwidth,
                storageInfo = com.omnisyncra.core.domain.StorageInfo(
                    totalSpace = storageInfo.totalSpace,
                    availableSpace = storageInfo.availableSpace,
                    usedSpace = storageInfo.usedSpace
                ),
                capabilities = collectCurrentCapabilities()
            )
        } catch (e: Exception) {
            println("Error collecting presence info: ${e.message}")
            PresenceInfo(
                status = PresenceStatus.ONLINE,
                lastSeen = TimeUtils.currentTimeMillis()
            )
        }
    }
    
    /**
     * Check if immediate broadcast is needed due to significant status changes
     */
    private fun shouldBroadcastImmediately(currentPresence: PresenceInfo): Boolean {
        val lastPresence = lastPresenceInfo ?: return false
        
        return when {
            // Status changed
            lastPresence.status != currentPresence.status -> true
            // Battery level changed significantly (>10%)
            lastPresence.batteryLevel != null && currentPresence.batteryLevel != null &&
                kotlin.math.abs(lastPresence.batteryLevel - currentPresence.batteryLevel) > 0.1f -> true
            // CPU usage changed significantly (>20%)
            kotlin.math.abs(lastPresence.cpuUsage - currentPresence.cpuUsage) > 0.2f -> true
            // Availability changed
            lastPresence.availableForTasks != currentPresence.availableForTasks -> true
            else -> false
        }
    }
    
    /**
     * Check for status changes and notify listeners
     */
    private fun checkForStatusChanges(deviceId: String, currentPresence: PresenceInfo) {
        val lastPresence = lastPresenceInfo
        if (lastPresence != null && lastPresence.status != currentPresence.status) {
            // Update device status history
            scope.launch {
                val currentHistory = _deviceStatusHistory.value[deviceId] ?: DeviceStatusHistory(deviceId)
                val statusChange = StatusChange(
                    timestamp = TimeUtils.currentTimeMillis(),
                    fromStatus = lastPresence.status,
                    toStatus = currentPresence.status,
                    reason = "Local status change detected"
                )
                
                val updatedHistory = currentHistory.copy(
                    statusChanges = currentHistory.statusChanges + statusChange,
                    lastPresenceUpdate = TimeUtils.currentTimeMillis()
                )
                
                val updatedMap = _deviceStatusHistory.value + (deviceId to updatedHistory)
                _deviceStatusHistory.value = updatedMap
            }
            
            // Notify listeners
            statusChangeListeners.forEach { listener ->
                try {
                    listener(deviceId, lastPresence.status, currentPresence.status)
                } catch (e: Exception) {
                    println("Status change listener error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Broadcast presence announcement using real network protocols
     */
    private suspend fun broadcastPresenceAnnouncement(announcement: PresenceAnnouncement) {
        try {
            // Convert announcement to JSON for network transmission
            val json = Json.encodeToString(PresenceAnnouncement.serializer(), announcement)
            
            // In a real implementation, this would:
            // 1. Send UDP broadcast packets to local network
            // 2. Announce via mDNS/Bonjour service discovery
            // 3. Send WebSocket messages to connected peers
            // 4. Use platform-specific discovery protocols (Bluetooth, etc.)
            
            println("🔄 Broadcasting presence: ${announcement.deviceName} (${announcement.platformName}) - Status: ${announcement.presenceInfo.status}")
            println("   CPU: ${(announcement.presenceInfo.cpuUsage * 100).toInt()}%, Memory: ${(announcement.presenceInfo.memoryUsage * 100).toInt()}%, Available: ${announcement.presenceInfo.availableForTasks}")
            
            // Simulate real network broadcast - in actual implementation this would use networkAdapter
            // networkAdapter.broadcastPresence(json)
            
        } catch (e: Exception) {
            println("Failed to broadcast presence: ${e.message}")
        }
    }
    
    /**
     * Comprehensive device status lifecycle management
     */
    
    /**
     * Get device lifecycle state with comprehensive status information
     */
    fun getDeviceLifecycleState(deviceId: String): DeviceLifecycleState? {
        val history = _deviceStatusHistory.value[deviceId] ?: return null
        val analytics = _connectionAnalytics.value[deviceId]
        val metadata = _processedMetadata.value[deviceId] ?: emptyMap()
        
        return DeviceLifecycleState(
            deviceId = deviceId,
            currentStatus = history.statusChanges.lastOrNull()?.toStatus ?: PresenceStatus.OFFLINE,
            statusHistory = history.statusChanges,
            connectionSessions = history.connectionSessions,
            connectionAnalytics = analytics,
            lastPresenceUpdate = history.lastPresenceUpdate,
            immediateMetadata = metadata,
            lifecycleMetrics = calculateLifecycleMetrics(history, analytics)
        )
    }
    
    /**
     * Calculate comprehensive lifecycle metrics for a device
     */
    private fun calculateLifecycleMetrics(
        history: DeviceStatusHistory,
        analytics: ConnectionAnalytics?
    ): DeviceLifecycleMetrics {
        val currentTime = TimeUtils.currentTimeMillis()
        
        // Status distribution analysis
        val statusDistribution = history.statusChanges.groupBy { it.toStatus }
            .mapValues { it.value.size }
        
        // Time spent in each status
        val statusDurations = mutableMapOf<PresenceStatus, Long>()
        for (i in history.statusChanges.indices) {
            val change = history.statusChanges[i]
            val nextChange = history.statusChanges.getOrNull(i + 1)
            val duration = (nextChange?.timestamp ?: currentTime) - change.timestamp
            statusDurations[change.toStatus] = (statusDurations[change.toStatus] ?: 0L) + duration
        }
        
        // Stability metrics
        val totalObservationTime = if (history.statusChanges.isNotEmpty()) {
            currentTime - history.statusChanges.first().timestamp
        } else 0L
        
        val statusChangeFrequency = if (totalObservationTime > 0) {
            history.statusChanges.size.toFloat() / (totalObservationTime / 3600000f) // changes per hour
        } else 0.0f
        
        // Availability metrics
        val onlineTime = statusDurations[PresenceStatus.ONLINE] ?: 0L
        val idleTime = statusDurations[PresenceStatus.IDLE] ?: 0L
        val availableTime = onlineTime + idleTime
        val availabilityPercentage = if (totalObservationTime > 0) {
            (availableTime.toFloat() / totalObservationTime.toFloat()) * 100f
        } else 0.0f
        
        // Connection reliability
        val connectionReliability = analytics?.let { a ->
            val excellentConnections = a.connectionQualityDistribution[ConnectionQuality.EXCELLENT] ?: 0
            val goodConnections = a.connectionQualityDistribution[ConnectionQuality.GOOD] ?: 0
            val totalConnections = a.totalSessions
            
            if (totalConnections > 0) {
                ((excellentConnections + goodConnections).toFloat() / totalConnections.toFloat()) * 100f
            } else 0.0f
        } ?: 0.0f
        
        return DeviceLifecycleMetrics(
            totalObservationTime = totalObservationTime,
            statusChangeFrequency = statusChangeFrequency,
            statusDistribution = statusDistribution,
            statusDurations = statusDurations,
            availabilityPercentage = availabilityPercentage,
            connectionReliability = connectionReliability,
            averageConnectionDuration = analytics?.averageSessionDurationCalculated ?: 0L,
            totalDataExchanged = analytics?.totalMessagesExchanged ?: 0
        )
    }
    
    /**
     * Trigger device status lifecycle transition with validation
     */
    suspend fun triggerStatusTransition(
        deviceId: String, 
        newStatus: PresenceStatus, 
        reason: String = "Manual transition"
    ): Result<Unit> {
        return try {
            val currentHistory = _deviceStatusHistory.value[deviceId] ?: DeviceStatusHistory(deviceId)
            val currentStatus = currentHistory.statusChanges.lastOrNull()?.toStatus ?: PresenceStatus.OFFLINE
            
            // Validate transition
            if (!isValidStatusTransition(currentStatus, newStatus)) {
                return Result.failure(Exception("Invalid status transition: $currentStatus -> $newStatus"))
            }
            
            // Create status change
            val statusChange = StatusChange(
                timestamp = TimeUtils.currentTimeMillis(),
                fromStatus = currentStatus,
                toStatus = newStatus,
                reason = reason
            )
            
            val updatedHistory = currentHistory.copy(
                statusChanges = currentHistory.statusChanges + statusChange,
                lastPresenceUpdate = TimeUtils.currentTimeMillis()
            )
            
            val updatedMap = _deviceStatusHistory.value + (deviceId to updatedHistory)
            _deviceStatusHistory.value = updatedMap
            
            // Notify listeners
            statusChangeListeners.forEach { listener ->
                try {
                    listener(deviceId, currentStatus, newStatus)
                } catch (e: Exception) {
                    println("Status change listener error: ${e.message}")
                }
            }
            
            println("🔄 Status transition triggered: $deviceId ($currentStatus -> $newStatus) - $reason")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validate if a status transition is allowed
     */
    private fun isValidStatusTransition(from: PresenceStatus, to: PresenceStatus): Boolean {
        // Define valid transitions
        return when (from) {
            PresenceStatus.OFFLINE -> to in setOf(PresenceStatus.ONLINE, PresenceStatus.AWAY)
            PresenceStatus.ONLINE -> to in setOf(PresenceStatus.BUSY, PresenceStatus.IDLE, PresenceStatus.AWAY, PresenceStatus.OFFLINE)
            PresenceStatus.BUSY -> to in setOf(PresenceStatus.ONLINE, PresenceStatus.IDLE, PresenceStatus.OFFLINE)
            PresenceStatus.IDLE -> to in setOf(PresenceStatus.ONLINE, PresenceStatus.BUSY, PresenceStatus.OFFLINE)
            PresenceStatus.AWAY -> to in setOf(PresenceStatus.ONLINE, PresenceStatus.OFFLINE)
        }
    }
    
    /**
     * Clean up stale device status history (older than 24 hours)
     */
    suspend fun cleanupStaleDeviceHistory() {
        val currentTime = TimeUtils.currentTimeMillis()
        val staleThreshold = 24 * 60 * 60 * 1000L // 24 hours
        
        val currentHistory = _deviceStatusHistory.value.toMutableMap()
        val staleDevices = mutableListOf<String>()
        
        for ((deviceId, history) in currentHistory) {
            val timeSinceLastUpdate = currentTime - history.lastPresenceUpdate
            
            if (timeSinceLastUpdate > staleThreshold) {
                // Mark device as stale but keep recent history
                val recentStatusChanges = history.statusChanges.filter { 
                    currentTime - it.timestamp < staleThreshold 
                }
                val recentSessions = history.connectionSessions.filter { 
                    currentTime - it.startTime < staleThreshold 
                }
                
                if (recentStatusChanges.isEmpty() && recentSessions.isEmpty()) {
                    staleDevices.add(deviceId)
                } else {
                    currentHistory[deviceId] = history.copy(
                        statusChanges = recentStatusChanges,
                        connectionSessions = recentSessions
                    )
                }
            }
        }
        
        // Remove completely stale devices
        staleDevices.forEach { deviceId ->
            currentHistory.remove(deviceId)
            println("🧹 Cleaned up stale device history: $deviceId")
        }
        
        _deviceStatusHistory.value = currentHistory
        
        // Also cleanup stale metadata and analytics
        cleanupStaleMetadata(staleThreshold)
        cleanupStaleAnalytics(staleThreshold)
    }
    
    /**
     * Clean up stale metadata
     */
    private suspend fun cleanupStaleMetadata(staleThreshold: Long) {
        val currentTime = TimeUtils.currentTimeMillis()
        val currentMetadata = _processedMetadata.value.toMutableMap()
        
        val staleMetadataDevices = currentMetadata.filter { (_, metadata) ->
            val lastUpdate = metadata["metadata_updated"]?.toLongOrNull() ?: 0L
            currentTime - lastUpdate > staleThreshold
        }.keys
        
        staleMetadataDevices.forEach { deviceId ->
            currentMetadata.remove(deviceId)
        }
        
        _processedMetadata.value = currentMetadata
    }
    
    /**
     * Clean up stale analytics
     */
    private suspend fun cleanupStaleAnalytics(staleThreshold: Long) {
        val currentTime = TimeUtils.currentTimeMillis()
        val currentAnalytics = _connectionAnalytics.value.toMutableMap()
        
        val staleAnalyticsDevices = currentAnalytics.filter { (_, analytics) ->
            val lastConnection = analytics.lastConnectionTime ?: 0L
            currentTime - lastConnection > staleThreshold
        }.keys
        
        staleAnalyticsDevices.forEach { deviceId ->
            currentAnalytics.remove(deviceId)
        }
        
        _connectionAnalytics.value = currentAnalytics
    }
    
    /**
     * Get comprehensive device status summary
     */
    fun getDeviceStatusSummary(): DeviceStatusSummary {
        val allHistory = _deviceStatusHistory.value
        val allAnalytics = _connectionAnalytics.value
        val allMetadata = _processedMetadata.value
        
        val totalDevices = allHistory.size
        val activeDevices = allHistory.count { (_, history) ->
            val timeSinceLastUpdate = TimeUtils.currentTimeMillis() - history.lastPresenceUpdate
            timeSinceLastUpdate < 5 * 60 * 1000L // Active within 5 minutes
        }
        
        val statusDistribution = allHistory.values
            .mapNotNull { it.statusChanges.lastOrNull()?.toStatus }
            .groupBy { it }
            .mapValues { it.value.size }
        
        val totalSessions = allAnalytics.values.sumOf { it.totalSessions }
        val totalConnectionTime = allAnalytics.values.sumOf { it.totalConnectionTime }
        
        return DeviceStatusSummary(
            totalDevices = totalDevices,
            activeDevices = activeDevices,
            statusDistribution = statusDistribution,
            totalConnectionSessions = totalSessions,
            totalConnectionTime = totalConnectionTime,
            devicesWithMetadata = allMetadata.size
        )
    }
}

/**
 * Presence announcement message for network broadcasting
 */
@Serializable
data class PresenceAnnouncement(
    val deviceId: String,
    val deviceName: String,
    val platformName: String,
    val presenceInfo: PresenceInfo,
    val timestamp: Long,
    val capabilities: Map<String, String> = emptyMap(),
    val isStatusChange: Boolean = false
)