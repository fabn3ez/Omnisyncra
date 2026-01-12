package com.omnisyncra.core.discovery

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.net.*
import java.io.*
import java.util.concurrent.ConcurrentHashMap

actual class WebSocketServer actual constructor(
    private val port: Int,
    private val onMessage: (clientId: String, message: SyncMessage) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap<String, ClientHandler>()
    private var isRunning = false
    
    actual suspend fun start() {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                
                println("Omnisyncra WebSocket Server started on port $port")
                
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        if (clientSocket != null) {
                            val clientId = "client_${System.currentTimeMillis()}"
                            val handler = ClientHandler(clientId, clientSocket, onMessage)
                            clients[clientId] = handler
                            
                            // Start handling client in separate coroutine
                            CoroutineScope(Dispatchers.IO).launch {
                                handler.handle()
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            println("Error accepting client: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error starting WebSocket server: ${e.message}")
            }
        }
    }
    
    actual suspend fun stop() {
        isRunning = false
        clients.values.forEach { it.close() }
        clients.clear()
        serverSocket?.close()
        serverSocket = null
    }
    
    actual suspend fun broadcast(message: SyncMessage) {
        val messageJson = Json.encodeToString(SyncMessage.serializer(), message)
        clients.values.forEach { client ->
            try {
                client.sendMessage(messageJson)
            } catch (e: Exception) {
                println("Error broadcasting to client: ${e.message}")
            }
        }
    }
    
    private class ClientHandler(
        private val clientId: String,
        private val socket: Socket,
        private val onMessage: (clientId: String, message: SyncMessage) -> Unit
    ) {
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        private val writer = PrintWriter(socket.getOutputStream(), true)
        
        suspend fun handle() {
            withContext(Dispatchers.IO) {
                try {
                    // Send handshake
                    writer.println("OMNISYNCRA_HANDSHAKE")
                    
                    var line: String?
                    while (socket.isConnected && !socket.isClosed) {
                        line = reader.readLine()
                        if (line != null) {
                            try {
                                val message = Json.decodeFromString(SyncMessage.serializer(), line)
                                onMessage(clientId, message)
                            } catch (e: Exception) {
                                println("Error parsing message: ${e.message}")
                            }
                        } else {
                            break
                        }
                    }
                } catch (e: Exception) {
                    println("Client handler error: ${e.message}")
                } finally {
                    close()
                }
            }
        }
        
        fun sendMessage(message: String) {
            writer.println(message)
        }
        
        fun close() {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}