package com.omnisyncra.core.discovery

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.Device
import com.omnisyncra.core.domain.ProximityDistance
import com.omnisyncra.core.domain.ProximityInfo
import kotlinx.coroutines.flow.Flow

interface BluetoothService {
    val discoveredDevices: Flow<List<BluetoothDeviceInfo>>
    val proximityUpdates: Flow<ProximityUpdate>
    
    suspend fun startScanning()
    suspend fun stopScanning()
    suspend fun startAdvertising(deviceInfo: BluetoothDeviceInfo)
    suspend fun stopAdvertising()
    
    fun isScanning(): Boolean
    fun isAdvertising(): Boolean
    fun isBluetoothEnabled(): Boolean
}

data class BluetoothDeviceInfo(
    val deviceId: Uuid,
    val name: String,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: ByteArray? = null,
    val serviceData: Map<String, ByteArray> = emptyMap()
) {
    fun toProximityInfo(): ProximityInfo {
        val distance = when {
            rssi > -50 -> ProximityDistance.IMMEDIATE
            rssi > -70 -> ProximityDistance.NEAR
            rssi > -90 -> ProximityDistance.FAR
            else -> ProximityDistance.UNKNOWN
        }
        
        return ProximityInfo(
            distance = distance,
            signalStrength = rssi,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BluetoothDeviceInfo) return false
        
        if (deviceId != other.deviceId) return false
        if (name != other.name) return false
        if (address != other.address) return false
        if (rssi != other.rssi) return false
        if (serviceUuids != other.serviceUuids) return false
        if (manufacturerData != null) {
            if (other.manufacturerData == null) return false
            if (!manufacturerData.contentEquals(other.manufacturerData)) return false
        } else if (other.manufacturerData != null) return false
        if (serviceData != other.serviceData) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + rssi
        result = 31 * result + serviceUuids.hashCode()
        result = 31 * result + (manufacturerData?.contentHashCode() ?: 0)
        result = 31 * result + serviceData.hashCode()
        return result
    }
}