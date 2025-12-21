package com.omnisyncra.core.discovery

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuidFrom
import com.omnisyncra.core.domain.Device
import com.omnisyncra.core.domain.NetworkInfo
import com.omnisyncra.core.platform.NetworkType
import kotlinx.coroutines.flow.Flow

interface MdnsService {
    val discoveredServices: Flow<List<MdnsServiceInfo>>
    
    suspend fun startDiscovery(serviceType: String)
    suspend fun stopDiscovery()
    suspend fun registerService(serviceInfo: MdnsServiceInfo)
    suspend fun unregisterService()
    
    fun isDiscovering(): Boolean
    fun isRegistered(): Boolean
}

data class MdnsServiceInfo(
    val serviceName: String,
    val serviceType: String,
    val hostName: String,
    val port: Int,
    val txtRecords: Map<String, String> = emptyMap(),
    val addresses: List<String> = emptyList()
) {
    fun toDevice(): Device? {
        return try {
            val deviceId = txtRecords["deviceId"]?.let { uuidFrom(it) } ?: return null
            val deviceName = txtRecords["deviceName"] ?: serviceName
            val deviceType = txtRecords["deviceType"]?.let { 
                com.omnisyncra.core.domain.DeviceType.valueOf(it) 
            } ?: com.omnisyncra.core.domain.DeviceType.UNKNOWN
            
            val capabilities = com.omnisyncra.core.domain.DeviceCapabilities(
                computePower = txtRecords["computePower"]?.let { 
                    com.omnisyncra.core.domain.ComputePower.valueOf(it) 
                } ?: com.omnisyncra.core.domain.ComputePower.LOW,
                screenSize = txtRecords["screenSize"]?.let { 
                    com.omnisyncra.core.domain.ScreenSize.valueOf(it) 
                } ?: com.omnisyncra.core.domain.ScreenSize.SMALL,
                hasBluetoothLE = txtRecords["hasBluetoothLE"]?.toBoolean() ?: false,
                hasWiFi = true, // Assume WiFi if advertising via mDNS
                canOffloadCompute = txtRecords["canOffloadCompute"]?.toBoolean() ?: false,
                maxConcurrentTasks = txtRecords["maxConcurrentTasks"]?.toInt() ?: 1,
                supportedProtocols = txtRecords["supportedProtocols"]?.split(",") ?: emptyList()
            )
            
            val networkInfo = NetworkInfo(
                ipAddress = addresses.firstOrNull() ?: hostName,
                port = port,
                protocol = "tcp",
                isReachable = true
            )
            
            Device(
                id = deviceId,
                name = deviceName,
                type = deviceType,
                capabilities = capabilities,
                networkInfo = networkInfo,
                lastSeen = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    companion object {
        fun fromDevice(device: Device, serviceType: String, port: Int): MdnsServiceInfo {
            val txtRecords = mapOf(
                "deviceId" to device.id.toString(),
                "deviceName" to device.name,
                "deviceType" to device.type.name,
                "computePower" to device.capabilities.computePower.name,
                "screenSize" to device.capabilities.screenSize.name,
                "hasBluetoothLE" to device.capabilities.hasBluetoothLE.toString(),
                "canOffloadCompute" to device.capabilities.canOffloadCompute.toString(),
                "maxConcurrentTasks" to device.capabilities.maxConcurrentTasks.toString(),
                "supportedProtocols" to device.capabilities.supportedProtocols.joinToString(",")
            )
            
            return MdnsServiceInfo(
                serviceName = device.name,
                serviceType = serviceType,
                hostName = device.networkInfo?.ipAddress ?: "localhost",
                port = port,
                txtRecords = txtRecords,
                addresses = device.networkInfo?.let { listOf(it.ipAddress) } ?: emptyList()
            )
        }
    }
}