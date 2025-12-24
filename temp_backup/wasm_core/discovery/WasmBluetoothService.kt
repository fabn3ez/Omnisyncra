package com.omnisyncra.core.discovery

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

class WasmBluetoothService : BluetoothService {
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
            // Web Bluetooth API in Wasm context
            val hasWebBluetooth = js("typeof navigator !== 'undefined' && typeof navigator.bluetooth !== 'undefined'") as Boolean
            
            if (hasWebBluetooth) {
                // Similar to JS implementation but with Wasm considerations
                val options = js("""({
                    filters: [{
                        services: ['6e400001-b5a3-f393-e0a9-e50e24dcca9e']
                    }],
                    optionalServices: ['6e400001-b5a3-f393-e0a9-e50e24dcca9e']
                })""")
                
                val device = js("navigator.bluetooth.requestDevice(options)")
                
                val deviceInfo = BluetoothDeviceInfo(
                    deviceId = uuid4(),
                    name = device.name as? String ?: "Wasm Bluetooth Device",
                    address = device.id as? String ?: "wasm-unknown",
                    rssi = -45, // Simulated stronger signal for Wasm demo
                    serviceUuids = listOf("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
                )
                
                _discoveredDevices.value = listOf(deviceInfo)
                
                val proximityUpdate = ProximityUpdate(
                    deviceId = deviceInfo.deviceId,
                    proximityInfo = deviceInfo.toProximityInfo(),
                    timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                )
                _proximityUpdates.tryEmit(proximityUpdate)
            }
            
        } catch (e: Exception) {
            isScanning = false
        }
    }
    
    override suspend fun stopScanning() {
        isScanning = false
        _discoveredDevices.value = emptyList()
    }
    
    override suspend fun startAdvertising(deviceInfo: BluetoothDeviceInfo) {
        // Wasm cannot advertise via Bluetooth
        isAdvertising = false
    }
    
    override suspend fun stopAdvertising() {
        isAdvertising = false
    }
    
    override fun isScanning(): Boolean = isScanning
    
    override fun isAdvertising(): Boolean = isAdvertising
    
    override fun isBluetoothEnabled(): Boolean {
        return try {
            js("typeof navigator !== 'undefined' && typeof navigator.bluetooth !== 'undefined'") as Boolean
        } catch (e: Exception) {
            false
        }
    }
}