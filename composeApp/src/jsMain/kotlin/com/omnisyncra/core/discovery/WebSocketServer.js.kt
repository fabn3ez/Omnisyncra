package com.omnisyncra.core.discovery

/**
 * JS WebSocket Server implementation
 */
actual class WebSocketServer actual constructor(
    private val port: Int,
    private val onMessage: (clientId: String, message: SyncMessage) -> Unit
) {
    actual suspend fun start() {
        println("🌐 JS WebSocket Server started on port $port (demo mode)")
    }
    
    actual suspend fun stop() {
        println("🌐 JS WebSocket Server stopped")
    }
    
    actual suspend fun broadcast(message: SyncMessage) {
        println("🌐 JS WebSocket Server broadcasting: $message")
    }
}