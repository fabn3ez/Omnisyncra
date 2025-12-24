package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WasmMdnsService : MdnsService {
    private val _discoveredServices = MutableStateFlow<List<MdnsServiceInfo>>(emptyList())
    override val discoveredServices: Flow<List<MdnsServiceInfo>> = _discoveredServices.asStateFlow()
    
    private var isDiscovering = false
    private var isRegistered = false
    
    override suspend fun startDiscovery(serviceType: String) {
        if (isDiscovering) return
        
        isDiscovering = true
        
        // Wasm has same limitations as regular web browsers
        // Simulate discovery for demo
        simulateWasmDiscovery()
    }
    
    override suspend fun stopDiscovery() {
        isDiscovering = false
        _discoveredServices.value = emptyList()
    }
    
    override suspend fun registerService(serviceInfo: MdnsServiceInfo) {
        if (isRegistered) return
        isRegistered = true
    }
    
    override suspend fun unregisterService() {
        isRegistered = false
    }
    
    override fun isDiscovering(): Boolean = isDiscovering
    
    override fun isRegistered(): Boolean = isRegistered
    
    private fun simulateWasmDiscovery() {
        val mockServices = listOf(
            MdnsServiceInfo(
                serviceName = "Wasm-Demo-Device",
                serviceType = "_omnisyncra._tcp",
                hostName = "localhost",
                port = 8080,
                txtRecords = mapOf(
                    "deviceId" to "550e8400-e29b-41d4-a716-446655440003",
                    "deviceName" to "Demo Wasm Client",
                    "deviceType" to "WEB_BROWSER",
                    "computePower" to "MEDIUM",
                    "screenSize" to "MEDIUM",
                    "hasBluetoothLE" to "true",
                    "canOffloadCompute" to "false",
                    "maxConcurrentTasks" to "4",
                    "supportedProtocols" to "websocket,webrtc,web-bluetooth"
                ),
                addresses = listOf("127.0.0.1")
            )
        )
        
        _discoveredServices.value = mockServices
    }
}