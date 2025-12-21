package com.omnisyncra.core.discovery

import com.benasher44.uuid.Uuid
import com.omnisyncra.core.domain.Device
import com.omnisyncra.core.platform.Platform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

class OmnisyncraDeviceDiscovery(
    private val platform: Platform,
    private val mdnsService: MdnsService,
    private val bluetoothService: BluetoothService,
    private val config: DiscoveryConfiguration = DiscoveryConfiguration()
) : DeviceDiscovery {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    override val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()
    
    private val _proximityUpdates = MutableSharedFlow<ProximityUpdate>()
    override val proximityUpdates: Flow<ProximityUpdate> = _proximityUpdates.asSharedFlow()
    
    private val _connectedDevices = MutableStateFlow<List<Device>>(emptyList())
    
    private var isDiscovering = false
    private var isAdvertising = false
    private var discoveryJob: Job? = null
    
    override suspend fun startDiscovery() {
        if (isDiscovering) return
        
        isDiscovering = true
        discoveryJob = scope.launch {
            supervisorScope {
                // Start mDNS discovery
                if (config.enableMdns) {
                    launch {
                        try {
                            mdnsService.startDiscovery(config.serviceType)
                            mdnsService.discoveredServices.collect { services ->
                                val devices = services.mapNotNull { it.toDevice() }
                                updateDiscoveredDevices(devices, "mdns")
                            }
                        } catch (e: Exception) {
                            // Handle mDNS errors gracefully
                        }
                    }
                }
                
                // Start Bluetooth discovery
                if (config.enableBluetooth && platform.capabilities.hasBluetoothLE) {
                    launch {
                        try {
                            bluetoothService.startScanning()
                            bluetoothService.discoveredDevices.collect { bluetoothDevices ->
                                // Convert Bluetooth devices and update proximity
                                bluetoothDevices.forEach { btDevice ->
                                    val proximityUpdate = ProximityUpdate(
                                        deviceId = btDevice.deviceId,
                                        proximityInfo = btDevice.toProximityInfo(),
                                        timestamp = Clock.System.now().toEpochMilliseconds()
                                    )
                                    _proximityUpdates.emit(proximityUpdate)
                                }
                            }
                        } catch (e: Exception) {
                            // Handle Bluetooth errors gracefully
                        }
                    }
                    
                    // Collect proximity updates
                    launch {
                        bluetoothService.proximityUpdates.collect { update ->
                            _proximityUpdates.emit(update)
                            updateDeviceProximity(update)
                        }
                    }
                }
                
                // Periodic cleanup of stale devices
                launch {
                    while (isActive) {
                        delay(config.discoveryIntervalMs)
                        cleanupStaleDevices()
                    }
                }
            }
        }
    }
    
    override suspend fun stopDiscovery() {
        if (!isDiscovering) return
        
        isDiscovering = false
        discoveryJob?.cancel()
        
        if (config.enableMdns) {
            mdnsService.stopDiscovery()
        }
        
        if (config.enableBluetooth && platform.capabilities.hasBluetoothLE) {
            bluetoothService.stopScanning()
        }
        
        _discoveredDevices.value = emptyList()
    }
    
    override suspend fun advertiseDevice(device: Device) {
        if (isAdvertising) return
        
        isAdvertising = true
        
        // Advertise via mDNS
        if (config.enableMdns) {
            try {
                val serviceInfo = MdnsServiceInfo.fromDevice(
                    device = device,
                    serviceType = config.serviceType,
                    port = device.networkInfo?.port ?: 8080
                )
                mdnsService.registerService(serviceInfo)
            } catch (e: Exception) {
                // Handle mDNS advertising errors
            }
        }
        
        // Advertise via Bluetooth
        if (config.enableBluetooth && platform.capabilities.hasBluetoothLE) {
            try {
                val bluetoothInfo = BluetoothDeviceInfo(
                    deviceId = device.id,
                    name = device.name,
                    address = "", // Will be filled by platform implementation
                    rssi = 0,
                    serviceUuids = listOf(config.bluetoothServiceUuid)
                )
                bluetoothService.startAdvertising(bluetoothInfo)
            } catch (e: Exception) {
                // Handle Bluetooth advertising errors
            }
        }
    }
    
    override suspend fun stopAdvertising() {
        if (!isAdvertising) return
        
        isAdvertising = false
        
        if (config.enableMdns) {
            mdnsService.unregisterService()
        }
        
        if (config.enableBluetooth && platform.capabilities.hasBluetoothLE) {
            bluetoothService.stopAdvertising()
        }
    }
    
    override suspend fun connectToDevice(deviceId: Uuid): Boolean {
        val device = _discoveredDevices.value.find { it.id == deviceId } ?: return false
        
        // Add to connected devices
        val currentConnected = _connectedDevices.value
        if (!currentConnected.any { it.id == deviceId }) {
            _connectedDevices.value = currentConnected + device
        }
        
        return true
    }
    
    override suspend fun disconnectFromDevice(deviceId: Uuid) {
        val currentConnected = _connectedDevices.value
        _connectedDevices.value = currentConnected.filter { it.id != deviceId }
    }
    
    override fun isDiscovering(): Boolean = isDiscovering
    
    override fun isAdvertising(): Boolean = isAdvertising
    
    override fun getConnectedDevices(): List<Device> = _connectedDevices.value
    
    private fun updateDiscoveredDevices(newDevices: List<Device>, source: String) {
        val currentDevices = _discoveredDevices.value.toMutableList()
        
        newDevices.forEach { newDevice ->
            val existingIndex = currentDevices.indexOfFirst { it.id == newDevice.id }
            if (existingIndex >= 0) {
                // Update existing device
                currentDevices[existingIndex] = newDevice.copy(
                    lastSeen = Clock.System.now().toEpochMilliseconds()
                )
            } else {
                // Add new device
                currentDevices.add(newDevice.copy(
                    lastSeen = Clock.System.now().toEpochMilliseconds()
                ))
            }
        }
        
        _discoveredDevices.value = currentDevices
    }
    
    private fun updateDeviceProximity(proximityUpdate: ProximityUpdate) {
        val currentDevices = _discoveredDevices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == proximityUpdate.deviceId }
        
        if (deviceIndex >= 0) {
            currentDevices[deviceIndex] = currentDevices[deviceIndex].copy(
                proximityInfo = proximityUpdate.proximityInfo,
                lastSeen = proximityUpdate.timestamp
            )
            _discoveredDevices.value = currentDevices
        }
    }
    
    private fun cleanupStaleDevices() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val staleThreshold = 30000L // 30 seconds
        
        val activeDevices = _discoveredDevices.value.filter { device ->
            (currentTime - device.lastSeen) < staleThreshold
        }
        
        if (activeDevices.size != _discoveredDevices.value.size) {
            _discoveredDevices.value = activeDevices
        }
    }
    
    fun cleanup() {
        scope.cancel()
    }
}