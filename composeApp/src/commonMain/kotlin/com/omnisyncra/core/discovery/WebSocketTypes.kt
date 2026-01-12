package com.omnisyncra.core.discovery

import com.omnisyncra.core.domain.NetworkDevice
import kotlinx.serialization.Serializable

/**
 * Message types for WebSocket communication
 */
enum class MessageType {
    DEVICE_INFO,
    SYNC_REQUEST,
    SYNC_RESPONSE,
    HEARTBEAT,
    BROADCAST
}

/**
 * Synchronization message for WebSocket communication
 */
@Serializable
data class SyncMessage(
    val type: MessageType,
    val senderId: String,
    val timestamp: Long,
    val data: String
)

/**
 * Expected WebSocket client for cross-platform communication
 */
expect class WebSocketClient(host: String, port: Int) {
    suspend fun connect(onMessage: (message: SyncMessage) -> Unit)
    suspend fun disconnect()
    suspend fun sendMessage(message: SyncMessage)
    suspend fun getDeviceInfo(): NetworkDevice?
}

/**
 * Expected WebSocket server for cross-platform communication
 */
expect class WebSocketServer(port: Int, onMessage: (clientId: String, message: SyncMessage) -> Unit) {
    suspend fun start()
    suspend fun stop()
    suspend fun broadcast(message: SyncMessage)
}