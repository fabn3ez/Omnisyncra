package com.omnisyncra.core.discovery

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.Device
import com.omnisyncra.core.domain.NetworkInfo
import com.omnisyncra.core.domain.ProximityInfo
import kotlinx.coroutines.flow.Flow

interface DeviceDiscovery {
    val discoveredDevices: Flow<List<Device>>
    val proximityUpdates: Flow<ProximityUpdate>
    
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    suspend fun advertiseDevice(device: Device)
    suspend fun stopAdvertising()
    suspend fun connectToDevice(deviceId: Uuid): Boolean
    suspend fun disconnectFromDevice(deviceId: Uuid)
    
    fun isDiscovering(): Boolean
    fun isAdvertising(): Boolean
    fun getConnectedDevices(): List<Device>
}

data class ProximityUpdate(
    val deviceId: Uuid,
    val proximityInfo: ProximityInfo,
    val timestamp: Long
)

data class DiscoveryConfiguration(
    val enableMdns: Boolean = true,
    val enableBluetooth: Boolean = true,
    val enableWebBluetooth: Boolean = false,
    val discoveryIntervalMs: Long = 5000L,
    val proximityThresholdRssi: Int = -70,
    val serviceName: String = "omnisyncra",
    val serviceType: String = "_omnisyncra._tcp",
    val bluetoothServiceUuid: String = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
)