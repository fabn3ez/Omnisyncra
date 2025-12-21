package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class JvmMdnsService : MdnsService {
    private val _discoveredServices = MutableStateFlow<List<MdnsServiceInfo>>(emptyList())
    override val discoveredServices: Flow<List<MdnsServiceInfo>> = _discoveredServices.asStateFlow()
    
    private var isDiscovering = false
    private var isRegistered = false
    
    override suspend fun startDiscovery(serviceType: String) {
        if (isDiscovering) return
        
        isDiscovering = true
        
        // TODO: Implement actual mDNS discovery using JmDNS or similar library
        // For now, simulate discovery with mock data
        simulateDiscovery()
    }
    
    override suspend fun stopDiscovery() {
        isDiscovering = false
        _discoveredServices.value = emptyList()
    }
    
    override suspend fun registerService(serviceInfo: MdnsServiceInfo) {
        if (isRegistered) return
        
        isRegistered = true
        
        // TODO: Implement actual mDNS service registration
        // For now, just mark as registered
    }
    
    override suspend fun unregisterService() {
        isRegistered = false
        
        // TODO: Implement actual mDNS service unregistration
    }
    
    override fun isDiscovering(): Boolean = isDiscovering
    
    override fun isRegistered(): Boolean = isRegistered
    
    private fun simulateDiscovery() {
        // Simulate finding some devices for demo purposes
        val mockServices = listOf(
            MdnsServiceInfo(
                serviceName = "Demo-Android-Device",
                serviceType = "_omnisyncra._tcp",
                hostName = "192.168.1.100",
                port = 8080,
                txtRecords = mapOf(
                    "deviceId" to "550e8400-e29b-41d4-a716-446655440001",
                    "deviceName" to "Demo Android Phone",
                    "deviceType" to "ANDROID_PHONE",
                    "computePower" to "MEDIUM",
                    "screenSize" to "SMALL",
                    "hasBluetoothLE" to "true",
                    "canOffloadCompute" to "false",
                    "maxConcurrentTasks" to "4",
                    "supportedProtocols" to "ble,wifi,websocket"
                ),
                addresses = listOf("192.168.1.100")
            )
        )
        
        _discoveredServices.value = mockServices
    }
}