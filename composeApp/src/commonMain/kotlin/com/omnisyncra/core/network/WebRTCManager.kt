package com.omnisyncra.core.network

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

// WebRTC connection states
enum class RTCConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSED
}

// WebRTC data channel configuration
data class DataChannelConfig(
    val label: String,
    val ordered: Boolean = true,
    val maxRetransmits: Int? = null,
    val maxPacketLifeTime: Int? = null,
    val protocol: String = "",
    val negotiated: Boolean = false,
    val id: Int? = null
)

// WebRTC peer connection
data class RTCPeerConnection(
    val id: Uuid = com.benasher44.uuid.uuid4(),
    val remoteDeviceId: Uuid,
    val state: RTCConnectionState = RTCConnectionState.NEW,
    val localDescription: String? = null,
    val remoteDescription: String? = null,
    val dataChannels: Map<String, RTCDataChannel> = emptyMap(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastActivity: Long = Clock.System.now().toEpochMilliseconds()
)

// WebRTC data channel
data class RTCDataChannel(
    val label: String,
    val state: RTCDataChannelState = RTCDataChannelState.CONNECTING,
    val bufferedAmount: Long = 0,
    val maxRetransmits: Int? = null,
    val maxPacketLifeTime: Int? = null,
    val ordered: Boolean = true,
    val protocol: String = ""
)

enum class RTCDataChannelState {
    CONNECTING,
    OPEN,
    CLOSING,
    CLOSED
}

// WebRTC signaling message
sealed class SignalingMessage {
    data class Offer(val sdp: String, val fromDevice: Uuid, val toDevice: Uuid) : SignalingMessage()
    data class Answer(val sdp: String, val fromDevice: Uuid, val toDevice: Uuid) : SignalingMessage()
    data class IceCandidate(
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
        val fromDevice: Uuid,
        val toDevice: Uuid
    ) : SignalingMessage()
}

// Platform-specific WebRTC implementation
expect class PlatformWebRTC {
    suspend fun createPeerConnection(config: RTCConfiguration): RTCPeerConnection
    suspend fun createOffer(connection: RTCPeerConnection): String
    suspend fun createAnswer(connection: RTCPeerConnection): String
    suspend fun setLocalDescription(connection: RTCPeerConnection, sdp: String)
    suspend fun setRemoteDescription(connection: RTCPeerConnection, sdp: String)
    suspend fun addIceCandidate(connection: RTCPeerConnection, candidate: String, sdpMid: String?, sdpMLineIndex: Int?)
    suspend fun createDataChannel(connection: RTCPeerConnection, config: DataChannelConfig): RTCDataChannel
    suspend fun sendData(channel: RTCDataChannel, data: ByteArray): Boolean
    suspend fun closeConnection(connection: RTCPeerConnection)
    fun getConnectionState(connection: RTCPeerConnection): RTCConnectionState
    fun getDataChannelState(channel: RTCDataChannel): RTCDataChannelState
}

// WebRTC configuration
data class RTCConfiguration(
    val iceServers: List<RTCIceServer> = listOf(
        RTCIceServer(urls = listOf("stun:stun.l.google.com:19302")),
        RTCIceServer(urls = listOf("stun:stun1.l.google.com:19302"))
    ),
    val iceTransportPolicy: RTCIceTransportPolicy = RTCIceTransportPolicy.ALL,
    val bundlePolicy: RTCBundlePolicy = RTCBundlePolicy.BALANCED,
    val rtcpMuxPolicy: RTCRtcpMuxPolicy = RTCRtcpMuxPolicy.REQUIRE
)

data class RTCIceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

enum class RTCIceTransportPolicy { ALL, RELAY }
enum class RTCBundlePolicy { BALANCED, MAX_COMPAT, MAX_BUNDLE }
enum class RTCRtcpMuxPolicy { NEGOTIATE, REQUIRE }

// WebRTC Manager
class WebRTCManager(
    private val nodeId: Uuid,
    private val platformWebRTC: PlatformWebRTC = PlatformWebRTC(),
    private val signalingService: SignalingService
) {
    private val connections = mutableMapOf<Uuid, RTCPeerConnection>()
    private val _connectionEvents = MutableSharedFlow<RTCConnectionEvent>()
    val connectionEvents: SharedFlow<RTCConnectionEvent> = _connectionEvents.asSharedFlow()
    
    private val _dataChannelEvents = MutableSharedFlow<RTCDataChannelEvent>()
    val dataChannelEvents: SharedFlow<RTCDataChannelEvent> = _dataChannelEvents.asSharedFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // Listen for signaling messages
        scope.launch {
            signalingService.messages.collect { message ->
                handleSignalingMessage(message)
            }
        }
    }
    
    suspend fun createConnection(
        remoteDeviceId: Uuid,
        config: RTCConfiguration = RTCConfiguration()
    ): RTCPeerConnection {
        val connection = platformWebRTC.createPeerConnection(config).copy(
            remoteDeviceId = remoteDeviceId
        )
        
        connections[remoteDeviceId] = connection
        
        _connectionEvents.emit(
            RTCConnectionEvent.ConnectionCreated(connection)
        )
        
        return connection
    }
    
    suspend fun createOffer(remoteDeviceId: Uuid): Boolean {
        val connection = connections[remoteDeviceId] ?: return false
        
        try {
            val sdp = platformWebRTC.createOffer(connection)
            platformWebRTC.setLocalDescription(connection, sdp)
            
            // Send offer through signaling
            signalingService.sendMessage(
                SignalingMessage.Offer(sdp, nodeId, remoteDeviceId)
            )
            
            _connectionEvents.emit(
                RTCConnectionEvent.OfferCreated(connection, sdp)
            )
            
            return true
        } catch (e: Exception) {
            _connectionEvents.emit(
                RTCConnectionEvent.Error(connection, "Failed to create offer: ${e.message}")
            )
            return false
        }
    }
    
    suspend fun createAnswer(remoteDeviceId: Uuid): Boolean {
        val connection = connections[remoteDeviceId] ?: return false
        
        try {
            val sdp = platformWebRTC.createAnswer(connection)
            platformWebRTC.setLocalDescription(connection, sdp)
            
            // Send answer through signaling
            signalingService.sendMessage(
                SignalingMessage.Answer(sdp, nodeId, remoteDeviceId)
            )
            
            _connectionEvents.emit(
                RTCConnectionEvent.AnswerCreated(connection, sdp)
            )
            
            return true
        } catch (e: Exception) {
            _connectionEvents.emit(
                RTCConnectionEvent.Error(connection, "Failed to create answer: ${e.message}")
            )
            return false
        }
    }
    
    suspend fun createDataChannel(
        remoteDeviceId: Uuid,
        config: DataChannelConfig
    ): RTCDataChannel? {
        val connection = connections[remoteDeviceId] ?: return null
        
        try {
            val channel = platformWebRTC.createDataChannel(connection, config)
            
            // Update connection with new data channel
            val updatedChannels = connection.dataChannels + (config.label to channel)
            connections[remoteDeviceId] = connection.copy(dataChannels = updatedChannels)
            
            _dataChannelEvents.emit(
                RTCDataChannelEvent.ChannelCreated(channel, connection)
            )
            
            return channel
        } catch (e: Exception) {
            _connectionEvents.emit(
                RTCConnectionEvent.Error(connection, "Failed to create data channel: ${e.message}")
            )
            return null
        }
    }
    
    suspend fun sendData(
        remoteDeviceId: Uuid,
        channelLabel: String,
        data: ByteArray
    ): Boolean {
        val connection = connections[remoteDeviceId] ?: return false
        val channel = connection.dataChannels[channelLabel] ?: return false
        
        return try {
            val success = platformWebRTC.sendData(channel, data)
            
            if (success) {
                _dataChannelEvents.emit(
                    RTCDataChannelEvent.DataSent(channel, data.size)
                )
            }
            
            success
        } catch (e: Exception) {
            _dataChannelEvents.emit(
                RTCDataChannelEvent.Error(channel, "Failed to send data: ${e.message}")
            )
            false
        }
    }
    
    suspend fun closeConnection(remoteDeviceId: Uuid) {
        val connection = connections.remove(remoteDeviceId) ?: return
        
        try {
            platformWebRTC.closeConnection(connection)
            
            _connectionEvents.emit(
                RTCConnectionEvent.ConnectionClosed(connection)
            )
        } catch (e: Exception) {
            _connectionEvents.emit(
                RTCConnectionEvent.Error(connection, "Failed to close connection: ${e.message}")
            )
        }
    }
    
    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        when (message) {
            is SignalingMessage.Offer -> handleOffer(message)
            is SignalingMessage.Answer -> handleAnswer(message)
            is SignalingMessage.IceCandidate -> handleIceCandidate(message)
        }
    }
    
    private suspend fun handleOffer(offer: SignalingMessage.Offer) {
        if (offer.toDevice != nodeId) return
        
        val connection = connections[offer.fromDevice] 
            ?: createConnection(offer.fromDevice)
        
        try {
            platformWebRTC.setRemoteDescription(connection, offer.sdp)
            createAnswer(offer.fromDevice)
            
            _connectionEvents.emit(
                RTCConnectionEvent.OfferReceived(connection, offer.sdp)
            )
        } catch (e: Exception) {
            _connectionEvents.emit(
                RTCConnectionEvent.Error(connection, "Failed to handle offer: ${e.message}")
            )
        }
    }
    
    private suspend fun handleAnswer(answer: SignalingMessage.Answer) {
        if (answer.toDevice != nodeId) return
        
        val connection = connections[answer.fromDevice] ?: return
        
        try {
            platformWebRTC.setRemoteDescription(connection, answer.sdp)
            
            _connectionEvents.emit(
                RTCConnectionEvent.AnswerReceived(connection, answer.sdp)
            )
        } catch (e: Exception) {
            _connectionEvents.emit(
                RTCConnectionEvent.Error(connection, "Failed to handle answer: ${e.message}")
            )
        }
    }
    
    private suspend fun handleIceCandidate(candidate: SignalingMessage.IceCandidate) {
        if (candidate.toDevice != nodeId) return
        
        val connection = connections[candidate.fromDevice] ?: return
        
        try {
            platformWebRTC.addIceCandidate(
                connection,
                candidate.candidate,
                candidate.sdpMid,
                candidate.sdpMLineIndex
            )
            
            _connectionEvents.emit(
                RTCConnectionEvent.IceCandidateReceived(connection, candidate.candidate)
            )
        } catch (e: Exception) {
            _connectionEvents.emit(
                RTCConnectionEvent.Error(connection, "Failed to add ICE candidate: ${e.message}")
            )
        }
    }
    
    fun getConnection(remoteDeviceId: Uuid): RTCPeerConnection? {
        return connections[remoteDeviceId]
    }
    
    fun getAllConnections(): List<RTCPeerConnection> {
        return connections.values.toList()
    }
    
    fun getConnectionState(remoteDeviceId: Uuid): RTCConnectionState? {
        val connection = connections[remoteDeviceId] ?: return null
        return platformWebRTC.getConnectionState(connection)
    }
    
    suspend fun getConnectionStats(): WebRTCStats {
        val totalConnections = connections.size
        val activeConnections = connections.values.count { connection ->
            platformWebRTC.getConnectionState(connection) == RTCConnectionState.CONNECTED
        }
        val totalDataChannels = connections.values.sumOf { it.dataChannels.size }
        
        return WebRTCStats(
            totalConnections = totalConnections,
            activeConnections = activeConnections,
            totalDataChannels = totalDataChannels,
            averageLatency = calculateAverageLatency()
        )
    }
    
    private fun calculateAverageLatency(): Double {
        // Simplified latency calculation
        return 50.0 // ms
    }
    
    fun cleanup() {
        scope.cancel()
        connections.values.forEach { connection ->
            scope.launch {
                try {
                    platformWebRTC.closeConnection(connection)
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
        connections.clear()
    }
}

// WebRTC Events
sealed class RTCConnectionEvent {
    data class ConnectionCreated(val connection: RTCPeerConnection) : RTCConnectionEvent()
    data class ConnectionClosed(val connection: RTCPeerConnection) : RTCConnectionEvent()
    data class OfferCreated(val connection: RTCPeerConnection, val sdp: String) : RTCConnectionEvent()
    data class OfferReceived(val connection: RTCPeerConnection, val sdp: String) : RTCConnectionEvent()
    data class AnswerCreated(val connection: RTCPeerConnection, val sdp: String) : RTCConnectionEvent()
    data class AnswerReceived(val connection: RTCPeerConnection, val sdp: String) : RTCConnectionEvent()
    data class IceCandidateReceived(val connection: RTCPeerConnection, val candidate: String) : RTCConnectionEvent()
    data class StateChanged(val connection: RTCPeerConnection, val newState: RTCConnectionState) : RTCConnectionEvent()
    data class Error(val connection: RTCPeerConnection, val message: String) : RTCConnectionEvent()
}

sealed class RTCDataChannelEvent {
    data class ChannelCreated(val channel: RTCDataChannel, val connection: RTCPeerConnection) : RTCDataChannelEvent()
    data class ChannelOpened(val channel: RTCDataChannel) : RTCDataChannelEvent()
    data class ChannelClosed(val channel: RTCDataChannel) : RTCDataChannelEvent()
    data class DataReceived(val channel: RTCDataChannel, val data: ByteArray) : RTCDataChannelEvent()
    data class DataSent(val channel: RTCDataChannel, val size: Int) : RTCDataChannelEvent()
    data class Error(val channel: RTCDataChannel, val message: String) : RTCDataChannelEvent()
}

// WebRTC Statistics
data class WebRTCStats(
    val totalConnections: Int,
    val activeConnections: Int,
    val totalDataChannels: Int,
    val averageLatency: Double
)

// Signaling Service Interface
interface SignalingService {
    val messages: Flow<SignalingMessage>
    suspend fun sendMessage(message: SignalingMessage)
}