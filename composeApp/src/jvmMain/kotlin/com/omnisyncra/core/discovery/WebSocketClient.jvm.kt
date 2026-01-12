package com.omnisyncra.core.discovery

import com.omnisyncra.core.domain.*
import com.omnisyncra.core.platform.TimeUtils
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.net.*
import java.io.*

actual class WebSocketClient actual constructor(
    private val host: String,
    private val port: Int
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var isConnected = false
    
    actual suspend fun connect(onMessage: (message: SyncMessage) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                socket = Socket()
                socket?.connect(InetSocketAddress(host, port), 5000) // 5 second timeout
                
                reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                writer = PrintWriter(socket?.getOutputStream(), true)
                
                // Wait for handshake
                val handshake = reader?.readLine()
                if (handshake == "OMNISYNCRA_HANDSHAKE") {
                    isConnected = true
                    
                    // Start message listening loop
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            var line: String?
                            while (isConnected && socket?.isConnected == true) {
                                line = reader?.readLine()
                                if (line != null) {
                                    try {
                                        val message = Json.decodeFromString(SyncMessage.serializer(), line)
                                        onMessage(message)
                                    } catch (e: Exception) {
                                        println("Error parsing received message: ${e.message}")
                                    }
                                } else {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            println("Error in message listening: ${e.message}")
                        }
                    }
                } else {
                    throw Exception("Invalid handshake response")
                }
            } catch (e: Exception) {
                throw Exception("Failed to connect to $host:$port - ${e.message}")
            }
        }
    }
    
    actual suspend fun disconnect() {
        isConnected = false
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        socket = null
        reader = null
        writer = null
    }
    
    actual suspend fun sendMessage(message: SyncMessage) {
        if (isConnected && writer != null) {
            try {
                val messageJson = Json.encodeToString(SyncMessage.serializer(), message)
                writer?.println(messageJson)
            } catch (e: Exception) {
                throw Exception("Failed to send message: ${e.message}")
            }
        } else {
            throw Exception("Not connected")
        }
    }
    
    actual suspend fun getDeviceInfo(): NetworkDevice? {
        return try {
            if (!isConnected) {
                connect { /* ignore messages during info request */ }
            }
            
            // Request device info
            val request = SyncMessage(
                type = MessageType.DEVICE_INFO,
                senderId = "info_request",
                timestamp = TimeUtils.currentTimeMillis(),
                data = "request"
            )
            
            sendMessage(request)
            
            // For now, return a mock device info
            // In a real implementation, we'd wait for the response
            NetworkDevice(
                id = "remote_device_${host}_${port}",
                name = "Remote Omnisyncra Device",
                type = DeviceType.UNKNOWN,
                capabilities = DeviceCapabilities(
                    canCompute = true,
                    canStore = true,
                    canDisplay = true,
                    canInput = true,
                    processingPower = ProcessingPower.MEDIUM,
                    memoryCapacity = MemoryCapacity.MEDIUM,
                    storageCapacity = StorageCapacity.MEDIUM,
                    networkCapability = NetworkCapability.FULL,
                    batteryCapacity = BatteryCapacity.UNLIMITED
                ),
                ipAddress = host,
                port = port,
                lastSeen = TimeUtils.currentTimeMillis(),
                signalStrength = 0.8f,
                isConnected = true,
                batteryLevel = 100,
                networkType = NetworkType.WIFI
            )
        } catch (e: Exception) {
            null
        }
    }
}