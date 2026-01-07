package com.omnisyncra.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Network Communication Interface for device-to-device communication
 */
interface NetworkCommunicator {
    /**
     * Connection status
     */
    val connectionStatus: StateFlow<ConnectionStatus>
    
    /**
     * Initialize network communication
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * Start discovery service for nearby devices
     */
    suspend fun startDiscovery(): Result<Unit>
    
    /**
     * Stop discovery service
     */
    suspend fun stopDiscovery(): Result<Unit>
    
    /**
     * Send data to a specific device
     */
    suspend fun sendData(deviceId: String, data: ByteArray): Result<Unit>
    
    /**
     * Receive data from devices
     */
    fun receiveData(): Flow<NetworkMessage>
    
    /**
     * Get discovered devices
     */
    suspend fun getDiscoveredDevices(): Result<List<NetworkDevice>>
    
    /**
     * Establish secure connection with device
     */
    suspend fun connectToDevice(deviceId: String): Result<NetworkConnection>
    
    /**
     * Disconnect from device
     */
    suspend fun disconnectFromDevice(deviceId: String): Result<Unit>
    
    /**
     * Shutdown network communication
     */
    suspend fun shutdown()
}

/**
 * Connection status
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERING,
    ERROR
}

/**
 * Network device information
 */
data class NetworkDevice(
    val deviceId: String,
    val deviceName: String,
    val ipAddress: String,
    val port: Int,
    val capabilities: List<String>,
    val signalStrength: Float, // 0.0 to 1.0
    val lastSeen: Long,
    val isSecure: Boolean
)

/**
 * Network message
 */
data class NetworkMessage(
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val messageType: MessageType,
    val data: ByteArray,
    val timestamp: Long,
    val messageId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as NetworkMessage
        
        if (sourceDeviceId != other.sourceDeviceId) return false
        if (targetDeviceId != other.targetDeviceId) return false
        if (messageType != other.messageType) return false
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false
        if (messageId != other.messageId) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = sourceDeviceId.hashCode()
        result = 31 * result + targetDeviceId.hashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + messageId.hashCode()
        return result
    }
}

/**
 * Message types
 */
enum class MessageType {
    HANDOFF_REQUEST,
    HANDOFF_RESPONSE,
    HANDOFF_DATA,
    DEVICE_DISCOVERY,
    HEARTBEAT,
    SECURITY_CHALLENGE,
    SECURITY_RESPONSE
}

/**
 * Network connection
 */
data class NetworkConnection(
    val deviceId: String,
    val connectionId: String,
    val isSecure: Boolean,
    val latency: Long,
    val bandwidth: Long, // bytes per second
    val establishedAt: Long
)

/**
 * Network event for monitoring
 */
data class NetworkEvent(
    val type: NetworkEventType,
    val deviceId: String?,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long
)

/**
 * Network event types
 */
enum class NetworkEventType {
    DEVICE_DISCOVERED,
    DEVICE_LOST,
    CONNECTION_ESTABLISHED,
    CONNECTION_LOST,
    MESSAGE_SENT,
    MESSAGE_RECEIVED,
    SECURITY_VIOLATION,
    NETWORK_ERROR
}