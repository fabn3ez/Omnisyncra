package com.omnisyncra.core.discovery

actual class WebSocketServer actual constructor(
    private val port: Int,
    private val onMessage: (clientId: String, message: SyncMessage) -> Unit
) {
    actual suspend fun start() {
        println("WASM WebSocket Server started on port $port")
    }
    
    actual suspend fun stop() {
        // Stop server
    }
    
    actual suspend fun broadcast(message: SyncMessage) {
        println("WASM Broadcasting message: ${message.type}")
    }
}