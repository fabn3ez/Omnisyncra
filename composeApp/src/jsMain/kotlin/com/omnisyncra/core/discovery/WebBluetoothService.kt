package com.omnisyncra.core.discovery

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

class WebBluetoothService : BluetoothService {
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    override val discoveredDevices: Flow<List<BluetoothDeviceInfo>> = _discoveredDevices.asStateFlow()
    
    private val _proximityUpdates = MutableSharedFlow<ProximityUpdate>()
    override val proximityUpdates: Flow<ProximityUpdate> = _proximityUpdates.asSharedFlow()
    
    private var isScanning = false
    private var isAdvertising = false
    
    override suspend fun startScanning() {
        if (isScanning || !isBluetoothEnabled()) return
        
        isScanning = true
        
        try {
            // Use Web Bluetooth API
            val options = js("""({
                filters: [{
                    services: ['6e400001-b5a3-f393-e0a9-e50e24dcca9e']
                }],
                optionalServices: ['6e400001-b5a3-f393-e0a9-e50e24dcca9e']
            })""")
            
            val device = js("navigator.bluetooth.requestDevice(options)")
            
            // Convert to BluetoothDeviceInfo
            val deviceInfo = BluetoothDeviceInfo(
                deviceId = uuid4(), // Generate UUID since Web Bluetooth doesn't expose device ID
                name = device.name as? String ?: "Unknown Device",
                address = device.id as? String ?: "unknown",
                rssi = -50, // Web Bluetooth doesn't expose RSSI
                serviceUuids = listOf("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
            )
            
            _discoveredDevices.value = listOf(deviceInfo)
            
            // Emit proximity update
            val proximityUpdate = ProximityUpdate(
                deviceId = deviceInfo.deviceId,
                proximityInfo = deviceInfo.toProximityInfo(),
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
            _proximityUpdates.tryEmit(proximityUpdate)
            
        } catch (e: Exception) {
            isScanning = false
            console.log("Web Bluetooth scanning failed:", e)
        }
    }
    
    override suspend fun stopScanning() {
        isScanning = false
        _discoveredDevices.value = emptyList()
    }
    
    override suspend fun startAdvertising(deviceInfo: BluetoothDeviceInfo) {
        if (isAdvertising) return
        
        // Web Bluetooth API doesn't support advertising
        // This would require a different approach like WebRTC or server-based signaling
        isAdvertising = false
    }
    
    override suspend fun stopAdvertising() {
        isAdvertising = false
    }
    
    override fun isScanning(): Boolean = isScanning
    
    override fun isAdvertising(): Boolean = isAdvertising
    
    override fun isBluetoothEnabled(): Boolean {
        return try {
            js("typeof navigator.bluetooth !== 'undefined'") as Boolean
        } catch (e: Exception) {
            false
        }
    }
}