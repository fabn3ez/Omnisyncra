package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class JvmBluetoothService : BluetoothService {
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    override val discoveredDevices: Flow<List<BluetoothDeviceInfo>> = _discoveredDevices.asStateFlow()
    
    private val _proximityUpdates = MutableSharedFlow<ProximityUpdate>()
    override val proximityUpdates: Flow<ProximityUpdate> = _proximityUpdates.asSharedFlow()
    
    private var isScanning = false
    private var isAdvertising = false
    
    override suspend fun startScanning() {
        if (isScanning) return
        
        isScanning = true
        
        // Desktop typically doesn't have Bluetooth LE support without additional hardware
        // This is a placeholder implementation
    }
    
    override suspend fun stopScanning() {
        isScanning = false
        _discoveredDevices.value = emptyList()
    }
    
    override suspend fun startAdvertising(deviceInfo: BluetoothDeviceInfo) {
        if (isAdvertising) return
        
        isAdvertising = true
        
        // Desktop Bluetooth advertising would require platform-specific implementation
        // This is a placeholder
    }
    
    override suspend fun stopAdvertising() {
        isAdvertising = false
    }
    
    override fun isScanning(): Boolean = isScanning
    
    override fun isAdvertising(): Boolean = isAdvertising
    
    override fun isBluetoothEnabled(): Boolean {
        // Desktop systems may not have Bluetooth LE by default
        return false
    }
}