package com.omnisyncra.core.discovery

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.domain.Device
import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.platform.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device discovery service for finding nearby devices
 */
interface DeviceDiscovery {
    val discoveredDevices: Flow<List<Device>>
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    suspend fun connectToDevice(deviceId: Uuid): Boolean
}

class MockDeviceDiscovery(private val platform: Platform) : DeviceDiscovery {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    override val discoveredDevices = _discoveredDevices.asStateFlow()
    
    private var isDiscovering = false
    
    override suspend fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true
        
        // Mock some discovered devices
        val mockDevices = listOf(
            Device(
                id = uuid4(),
                name = "MacBook Pro",
                capabilities = DeviceCapabilities(),
                isOnline = true
            ),
            Device(
                id = uuid4(),
                name = "iPhone 15 Pro",
                capabilities = DeviceCapabilities(),
                isOnline = true
            ),
            Device(
                id = uuid4(),
                name = "Desktop PC",
                capabilities = DeviceCapabilities(),
                isOnline = false
            )
        )
        
        _discoveredDevices.value = mockDevices
    }
    
    override suspend fun stopDiscovery() {
        isDiscovering = false
        _discoveredDevices.value = emptyList()
    }
    
    override suspend fun connectToDevice(deviceId: Uuid): Boolean {
        // Mock connection success
        return true
    }
}