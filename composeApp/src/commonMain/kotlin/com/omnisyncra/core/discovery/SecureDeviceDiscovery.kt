package com.omnisyncra.core.discovery

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.security.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Secure device information with privacy protection
 */
@Serializable
data class SecureDeviceInfo(
    val deviceId: Uuid,
    val anonymousId: ByteArray,
    val deviceType: String,
    val capabilities: List<String>,
    val trustLevel: TrustLevel,
    val lastSeen: Long,
    val signalStrength: Double,
    val distance: Double? = null,
    val isAuthenticated: Boolean = false,
    val encryptionSupported: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SecureDeviceInfo
        return deviceId == other.deviceId &&
                anonymousId.contentEquals(other.anonymousId) &&
                deviceType == other.deviceType &&
                capabilities == other.capabilities &&
                trustLevel == other.trustLevel &&
                lastSeen == other.lastSeen &&
                signalStrength == other.signalStrength &&
                distance == other.distance &&
                isAuthenticated == other.isAuthenticated &&
                encryptionSupported == other.encryptionSupported
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + anonymousId.contentHashCode()
        result = 31 * result + deviceType.hashCode()
        result = 31 * result + capabilities.hashCode()
        result = 31 * result + trustLevel.hashCode()
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + signalStrength.hashCode()
        result = 31 * result + (distance?.hashCode() ?: 0)
        result = 31 * result + isAuthenticated.hashCode()
        result = 31 * result + encryptionSupported.hashCode()
        return result
    }
}

/**
 * Secure connection attempt result
 */
data class SecureConnectionResult(
    val success: Boolean,
    val deviceId: Uuid,
    val sessionId: Uuid? = null,
    val trustEstablished: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Discovery configuration for security and privacy
 */
data class SecureDiscoveryConfig(
    val enablePrivacyMode: Boolean = true,
    val maxDiscoveryRange: Double = 100.0, // meters
    val discoveryInterval: Long = 30_000L, // 30 seconds
    val trustRequiredForConnection: Boolean = true,
    val autoConnectTrustedDevices: Boolean = false,
    val maxConcurrentConnections: Int = 10
)

/**
 * Secure device discovery interface with privacy-preserving features
 */
interface SecureDeviceDiscovery {
    val discoveredDevices: StateFlow<List<SecureDeviceInfo>>
    val connectedDevices: StateFlow<List<SecureDeviceInfo>>
    val isDiscovering: StateFlow<Boolean>
    val discoveryConfig: StateFlow<SecureDiscoveryConfig>
    
    suspend fun startSecureDiscovery(): Boolean
    suspend fun stopSecureDiscovery(): Boolean
    suspend fun connectToDevice(deviceId: Uuid): SecureConnectionResult
    suspend fun disconnectFromDevice(deviceId: Uuid): Boolean
    suspend fun authenticateDevice(deviceId: Uuid): Boolean
    suspend fun establishTrust(deviceId: Uuid, method: TrustMethod): Boolean
    suspend fun revealIdentityToDevice(deviceId: Uuid): Boolean
    suspend fun updateDiscoveryConfig(config: SecureDiscoveryConfig)
}

/**
 * Trust establishment methods
 */
enum class TrustMethod {
    QR_CODE,
    PIN_VERIFICATION,
    CERTIFICATE_EXCHANGE,
    OUT_OF_BAND_VERIFICATION,
    MANUAL_APPROVAL
}

/**
 * Implementation of secure device discovery with privacy protection
 */
class OmnisyncraSecureDeviceDiscovery(
    private val deviceId: Uuid,
    private val privacyBeaconSystem: PrivacyBeaconSystem,
    private val identityRevelationProtocol: IdentityRevelationProtocol,
    private val sessionManager: SessionManager,
    private val trustManager: TrustManager,
    private val securityLogger: SecurityEventLogger,
    private val initialConfig: SecureDiscoveryConfig = SecureDiscoveryConfig()
) : SecureDeviceDiscovery {
    
    private val _discoveredDevices = MutableStateFlow<List<SecureDeviceInfo>>(emptyList())
    override val discoveredDevices: StateFlow<List<SecureDeviceInfo>> = _discoveredDevices.asStateFlow()
    
    private val _connectedDevices = MutableStateFlow<List<SecureDeviceInfo>>(emptyList())
    override val connectedDevices: StateFlow<List<SecureDeviceInfo>> = _connectedDevices.asStateFlow()
    
    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    
    private val _discoveryConfig = MutableStateFlow(initialConfig)
    override val discoveryConfig: StateFlow<SecureDiscoveryConfig> = _discoveryConfig.asStateFlow()
    
    private val deviceCache = mutableMapOf<Uuid, SecureDeviceInfo>()
    private val connectionSessions = mutableMapOf<Uuid, Uuid>() // deviceId -> sessionId
    
    override suspend fun startSecureDiscovery(): Boolean {
        return try {
            if (_isDiscovering.value) {
                return true // Already discovering
            }
            
            // Start privacy-preserving beacon system
            val beaconStarted = privacyBeaconSystem.startBeaconing()
            if (!beaconStarted) {
                securityLogger.logEvent(
                    type = SecurityEventType.BEACON_STARTED,
                    severity = SecurityEventSeverity.ERROR,
                    message = "Failed to start privacy beacon for secure discovery"
                )
                return false
            }
            
            // Start scanning for other devices
            val detectionFlow = privacyBeaconSystem.detectBeacons()
            
            _isDiscovering.value = true
            
            securityLogger.logEvent(
                type = SecurityEventType.BEACON_STARTED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure device discovery started",
                details = mapOf(
                    "privacy_mode" to _discoveryConfig.value.enablePrivacyMode.toString(),
                    "max_range" to _discoveryConfig.value.maxDiscoveryRange.toString()
                )
            )
            
            // Process beacon detections
            processBeaconDetections(detectionFlow)
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.BEACON_STARTED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to start secure discovery",
                error = e
            )
            false
        }
    }
    
    override suspend fun stopSecureDiscovery(): Boolean {
        return try {
            privacyBeaconSystem.stopBeaconing()
            _isDiscovering.value = false
            _discoveredDevices.value = emptyList()
            
            securityLogger.logEvent(
                type = SecurityEventType.BEACON_STOPPED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure device discovery stopped"
            )
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.BEACON_STOPPED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to stop secure discovery",
                error = e
            )
            false
        }
    }
    
    override suspend fun connectToDevice(deviceId: Uuid): SecureConnectionResult {
        return try {
            val deviceInfo = deviceCache[deviceId]
                ?: return SecureConnectionResult(
                    success = false,
                    deviceId = deviceId,
                    errorMessage = "Device not found in discovery cache"
                )
            
            // Check trust requirements
            if (_discoveryConfig.value.trustRequiredForConnection && 
                deviceInfo.trustLevel == TrustLevel.UNKNOWN) {
                return SecureConnectionResult(
                    success = false,
                    deviceId = deviceId,
                    errorMessage = "Device trust required for connection"
                )
            }
            
            // Check connection limits
            if (_connectedDevices.value.size >= _discoveryConfig.value.maxConcurrentConnections) {
                return SecureConnectionResult(
                    success = false,
                    deviceId = deviceId,
                    errorMessage = "Maximum concurrent connections reached"
                )
            }
            
            // Create secure session
            val sessionResult = sessionManager.createSession(deviceId)
            if (sessionResult.isFailure) {
                return SecureConnectionResult(
                    success = false,
                    deviceId = deviceId,
                    errorMessage = "Failed to create secure session: ${sessionResult.exceptionOrNull()?.message}"
                )
            }
            
            val session = sessionResult.getOrThrow()
            connectionSessions[deviceId] = session.sessionId
            
            // Update device info
            val connectedDeviceInfo = deviceInfo.copy(
                isAuthenticated = true,
                lastSeen = Clock.System.now().toEpochMilliseconds()
            )
            
            deviceCache[deviceId] = connectedDeviceInfo
            updateConnectedDevices()
            
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_ESTABLISHED,
                severity = SecurityEventSeverity.INFO,
                message = "Secure connection established",
                relatedDeviceId = deviceId,
                sessionId = session.sessionId,
                details = mapOf(
                    "trust_level" to deviceInfo.trustLevel.name,
                    "encryption_enabled" to "true"
                )
            )
            
            SecureConnectionResult(
                success = true,
                deviceId = deviceId,
                sessionId = session.sessionId,
                trustEstablished = deviceInfo.trustLevel == TrustLevel.TRUSTED,
                encryptionEnabled = true
            )
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to connect to device",
                relatedDeviceId = deviceId,
                error = e
            )
            
            SecureConnectionResult(
                success = false,
                deviceId = deviceId,
                errorMessage = e.message
            )
        }
    }
    
    override suspend fun disconnectFromDevice(deviceId: Uuid): Boolean {
        return try {
            val sessionId = connectionSessions[deviceId]
            if (sessionId != null) {
                sessionManager.terminateSession(sessionId)
                connectionSessions.remove(deviceId)
            }
            
            // Update device info
            val deviceInfo = deviceCache[deviceId]
            if (deviceInfo != null) {
                deviceCache[deviceId] = deviceInfo.copy(isAuthenticated = false)
            }
            
            updateConnectedDevices()
            
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.INFO,
                message = "Device disconnected",
                relatedDeviceId = deviceId,
                sessionId = sessionId
            )
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.SESSION_TERMINATED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to disconnect from device",
                relatedDeviceId = deviceId,
                error = e
            )
            false
        }
    }
    
    override suspend fun authenticateDevice(deviceId: Uuid): Boolean {
        return try {
            val deviceInfo = deviceCache[deviceId] ?: return false
            
            // Use identity revelation protocol for authentication
            val beacon = AnonymousBeacon(
                identifier = deviceInfo.anonymousId,
                commitment = ByteArray(32), // Would be actual commitment
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
            
            val authResult = identityRevelationProtocol.performMutualAuthentication(beacon)
            val verified = identityRevelationProtocol.verifyMutualAuthentication(authResult)
            
            if (verified) {
                // Update device info
                deviceCache[deviceId] = deviceInfo.copy(isAuthenticated = true)
                updateDiscoveredDevices()
                
                securityLogger.logEvent(
                    type = SecurityEventType.AUTHENTICATION_SUCCESS,
                    severity = SecurityEventSeverity.INFO,
                    message = "Device authentication successful",
                    relatedDeviceId = deviceId
                )
            } else {
                securityLogger.logEvent(
                    type = SecurityEventType.AUTHENTICATION_FAILURE,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Device authentication failed",
                    relatedDeviceId = deviceId
                )
            }
            
            verified
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.AUTHENTICATION_FAILURE,
                severity = SecurityEventSeverity.ERROR,
                message = "Device authentication error",
                relatedDeviceId = deviceId,
                error = e
            )
            false
        }
    }
    
    override suspend fun establishTrust(deviceId: Uuid, method: TrustMethod): Boolean {
        return try {
            val deviceInfo = deviceCache[deviceId] ?: return false
            
            // Establish trust using specified method
            val trustEstablished = when (method) {
                TrustMethod.QR_CODE -> establishTrustViaQRCode(deviceId)
                TrustMethod.PIN_VERIFICATION -> establishTrustViaPIN(deviceId)
                TrustMethod.CERTIFICATE_EXCHANGE -> establishTrustViaCertificate(deviceId)
                TrustMethod.OUT_OF_BAND_VERIFICATION -> establishTrustOutOfBand(deviceId)
                TrustMethod.MANUAL_APPROVAL -> establishTrustManually(deviceId)
            }
            
            if (trustEstablished) {
                trustManager.establishTrust(deviceId)
                
                // Update device info
                deviceCache[deviceId] = deviceInfo.copy(
                    trustLevel = TrustLevel.TRUSTED,
                    isAuthenticated = true
                )
                updateDiscoveredDevices()
                
                securityLogger.logEvent(
                    type = SecurityEventType.TRUST_ESTABLISHED,
                    severity = SecurityEventSeverity.INFO,
                    message = "Trust established with device",
                    relatedDeviceId = deviceId,
                    details = mapOf("method" to method.name)
                )
            }
            
            trustEstablished
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.TRUST_ESTABLISHED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to establish trust",
                relatedDeviceId = deviceId,
                error = e
            )
            false
        }
    }
    
    override suspend fun revealIdentityToDevice(deviceId: Uuid): Boolean {
        return try {
            val deviceInfo = deviceCache[deviceId] ?: return false
            
            val beacon = AnonymousBeacon(
                identifier = deviceInfo.anonymousId,
                commitment = ByteArray(32),
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
            
            val revelation = privacyBeaconSystem.revealIdentity(beacon)
            if (revelation != null) {
                securityLogger.logEvent(
                    type = SecurityEventType.IDENTITY_REVEALED,
                    severity = SecurityEventSeverity.INFO,
                    message = "Identity revealed to device",
                    relatedDeviceId = deviceId
                )
                true
            } else {
                securityLogger.logEvent(
                    type = SecurityEventType.IDENTITY_REVELATION_FAILED,
                    severity = SecurityEventSeverity.WARNING,
                    message = "Failed to reveal identity to device",
                    relatedDeviceId = deviceId
                )
                false
            }
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.IDENTITY_REVELATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Error revealing identity to device",
                relatedDeviceId = deviceId,
                error = e
            )
            false
        }
    }
    
    override suspend fun updateDiscoveryConfig(config: SecureDiscoveryConfig) {
        _discoveryConfig.value = config
        
        securityLogger.logEvent(
            type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
            severity = SecurityEventSeverity.INFO,
            message = "Discovery configuration updated",
            details = mapOf(
                "privacy_mode" to config.enablePrivacyMode.toString(),
                "max_range" to config.maxDiscoveryRange.toString(),
                "trust_required" to config.trustRequiredForConnection.toString()
            )
        )
    }
    
    private suspend fun processBeaconDetections(detectionFlow: kotlinx.coroutines.flow.Flow<BeaconDetection>) {
        detectionFlow.collect { detection ->
            processBeaconDetection(detection)
        }
    }
    
    private suspend fun processBeaconDetection(detection: BeaconDetection) {
        try {
            // Check if device is within range
            val config = _discoveryConfig.value
            if (detection.distance != null && detection.distance > config.maxDiscoveryRange) {
                return // Device too far away
            }
            
            // Generate a temporary device ID for the anonymous beacon
            val tempDeviceId = generateTempDeviceId(detection.beacon.identifier)
            
            // Check if we already know this device
            val existingDevice = deviceCache[tempDeviceId]
            val trustLevel = if (existingDevice != null) {
                existingDevice.trustLevel
            } else {
                TrustLevel.UNKNOWN
            }
            
            val deviceInfo = SecureDeviceInfo(
                deviceId = tempDeviceId,
                anonymousId = detection.beacon.identifier,
                deviceType = "unknown", // Would be determined from beacon metadata
                capabilities = listOf("handoff", "sync"), // From beacon metadata
                trustLevel = trustLevel,
                lastSeen = detection.detectedAt,
                signalStrength = detection.signalStrength,
                distance = detection.distance,
                isAuthenticated = existingDevice?.isAuthenticated ?: false,
                encryptionSupported = true
            )
            
            deviceCache[tempDeviceId] = deviceInfo
            updateDiscoveredDevices()
            
            securityLogger.logEvent(
                type = SecurityEventType.BEACON_DETECTED,
                severity = SecurityEventSeverity.INFO,
                message = "Anonymous device detected",
                details = mapOf(
                    "signal_strength" to detection.signalStrength.toString(),
                    "distance" to (detection.distance?.toString() ?: "unknown"),
                    "trust_level" to trustLevel.name
                )
            )
            
            // Auto-connect to trusted devices if enabled
            if (config.autoConnectTrustedDevices && trustLevel == TrustLevel.TRUSTED) {
                connectToDevice(tempDeviceId)
            }
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.BEACON_DETECTED,
                severity = SecurityEventSeverity.ERROR,
                message = "Error processing beacon detection",
                error = e
            )
        }
    }
    
    private fun generateTempDeviceId(anonymousId: ByteArray): Uuid {
        // Generate a consistent UUID from the anonymous identifier
        // This is a simplified approach - in production, use proper UUID generation
        val hash = anonymousId.contentHashCode()
        return Uuid.fromString("00000000-0000-0000-0000-${hash.toString().padStart(12, '0')}")
    }
    
    private fun updateDiscoveredDevices() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val validDevices = deviceCache.values.filter { device ->
            (currentTime - device.lastSeen) <= 300_000L // 5 minutes
        }
        _discoveredDevices.value = validDevices.toList()
    }
    
    private fun updateConnectedDevices() {
        val connectedDevices = deviceCache.values.filter { it.isAuthenticated }
        _connectedDevices.value = connectedDevices.toList()
    }
    
    // Trust establishment methods (simplified implementations)
    private suspend fun establishTrustViaQRCode(deviceId: Uuid): Boolean {
        // In a real implementation, this would involve QR code scanning and verification
        return true // Simulate success
    }
    
    private suspend fun establishTrustViaPIN(deviceId: Uuid): Boolean {
        // In a real implementation, this would involve PIN exchange and verification
        return true // Simulate success
    }
    
    private suspend fun establishTrustViaCertificate(deviceId: Uuid): Boolean {
        // In a real implementation, this would involve certificate exchange and validation
        return true // Simulate success
    }
    
    private suspend fun establishTrustOutOfBand(deviceId: Uuid): Boolean {
        // In a real implementation, this would involve out-of-band verification
        return true // Simulate success
    }
    
    private suspend fun establishTrustManually(deviceId: Uuid): Boolean {
        // In a real implementation, this would involve manual user approval
        return true // Simulate success
    }
    
    // Cleanup expired devices
    suspend fun cleanupExpiredDevices(): Int {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val expiredDevices = deviceCache.filter { (_, device) ->
            (currentTime - device.lastSeen) > 3600_000L // 1 hour
        }
        
        expiredDevices.keys.forEach { deviceId ->
            deviceCache.remove(deviceId)
            connectionSessions.remove(deviceId)
        }
        
        updateDiscoveredDevices()
        updateConnectedDevices()
        
        return expiredDevices.size
    }
}