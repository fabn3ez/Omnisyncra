package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebMdnsService : MdnsService {
    private val _discoveredServices = MutableStateFlow<List<MdnsServiceInfo>>(emptyList())
    override val discoveredServices: Flow<List<MdnsServiceInfo>> = _discoveredServices.asStateFlow()
    
    private var isDiscovering = false
    private var isRegistered = false
    
    override suspend fun startDiscovery(serviceType: String) {
        if (isDiscovering) return
        
        isDiscovering = true
        
        // Web browsers don't have direct mDNS access for security reasons
        // This would typically require a WebRTC-based discovery mechanism
        // or communication through a signaling server
        
        // For demo purposes, simulate some discovered services
        simulateWebDiscovery()
    }
    
    override suspend fun stopDiscovery() {
        isDiscovering = false
        _discoveredServices.value = emptyList()
    }
    
    override suspend fun registerService(serviceInfo: MdnsServiceInfo) {
        if (isRegistered) return
        
        isRegistered = true
        
        // Web browsers cannot directly register mDNS services
        // This would require a backend service or WebRTC signaling
    }
    
    override suspend fun unregisterService() {
        isRegistered = false
    }
    
    override fun isDiscovering(): Boolean = isDiscovering
    
    override fun isRegistered(): Boolean = isRegistered
    
    private fun simulateWebDiscovery() {
        // Simulate discovering devices through WebRTC or signaling server
        val mockServices = listOf(
            MdnsServiceInfo(
                serviceName = "Web-Demo-Desktop",
                serviceType = "_omnisyncra._tcp",
                hostName = "localhost",
                port = 8080,
                txtRecords = mapOf(
                    "deviceId" to "550e8400-e29b-41d4-a716-446655440002",
                    "deviceName" to "Demo Desktop",
                    "deviceType" to "DESKTOP",
                    "computePower" to "HIGH",
                    "screenSize" to "LARGE",
                    "hasBluetoothLE" to "false",
                    "canOffloadCompute" to "true",
                    "maxConcurrentTasks" to "8",
                    "supportedProtocols" to "websocket,webrtc"
                ),
                addresses = listOf("127.0.0.1")
            )
        )
        
        _discoveredServices.value = mockServices
    }
}