package com.omnisyncra.core.discovery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import java.util.UUID

class AndroidBluetoothService(
    private val context: Context
) : BluetoothService {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    override val discoveredDevices: Flow<List<BluetoothDeviceInfo>> = _discoveredDevices.asStateFlow()
    
    private val _proximityUpdates = MutableSharedFlow<ProximityUpdate>()
    override val proximityUpdates: Flow<ProximityUpdate> = _proximityUpdates.asSharedFlow()
    
    private var isScanning = false
    private var isAdvertising = false
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    
    override suspend fun startScanning() {
        if (isScanning || !isBluetoothEnabled() || !hasPermissions()) return
        
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner ?: return
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanResult ->
                    val deviceInfo = scanResult.toBluetoothDeviceInfo()
                    deviceInfo?.let { info ->
                        updateDiscoveredDevice(info)
                        
                        // Emit proximity update
                        val proximityUpdate = ProximityUpdate(
                            deviceId = info.deviceId,
                            proximityInfo = info.toProximityInfo(),
                            timestamp = Clock.System.now().toEpochMilliseconds()
                        )
                        _proximityUpdates.tryEmit(proximityUpdate)
                    }
                }
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    val deviceInfo = result.toBluetoothDeviceInfo()
                    deviceInfo?.let { updateDiscoveredDevice(it) }
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                isScanning = false
            }
        }
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
                .build()
        )
        
        try {
            bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
        } catch (e: SecurityException) {
            isScanning = false
        }
    }
    
    override suspend fun stopScanning() {
        if (!isScanning) return
        
        scanCallback?.let { callback ->
            try {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(callback)
            } catch (e: SecurityException) {
                // Handle permission error
            }
        }
        
        isScanning = false
        _discoveredDevices.value = emptyList()
    }
    
    override suspend fun startAdvertising(deviceInfo: BluetoothDeviceInfo) {
        if (isAdvertising || !isBluetoothEnabled() || !hasPermissions()) return
        
        val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isAdvertising = true
            }
            
            override fun onStartFailure(errorCode: Int) {
                isAdvertising = false
            }
        }
        
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
            .build()
        
        try {
            bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        } catch (e: SecurityException) {
            isAdvertising = false
        }
    }
    
    override suspend fun stopAdvertising() {
        if (!isAdvertising) return
        
        advertiseCallback?.let { callback ->
            try {
                bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(callback)
            } catch (e: SecurityException) {
                // Handle permission error
            }
        }
        
        isAdvertising = false
    }
    
    override fun isScanning(): Boolean = isScanning
    
    override fun isAdvertising(): Boolean = isAdvertising
    
    override fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    private fun hasPermissions(): Boolean {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        return permissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun updateDiscoveredDevice(deviceInfo: BluetoothDeviceInfo) {
        val currentDevices = _discoveredDevices.value.toMutableList()
        val existingIndex = currentDevices.indexOfFirst { it.deviceId == deviceInfo.deviceId }
        
        if (existingIndex >= 0) {
            currentDevices[existingIndex] = deviceInfo
        } else {
            currentDevices.add(deviceInfo)
        }
        
        _discoveredDevices.value = currentDevices
    }
}

private fun ScanResult.toBluetoothDeviceInfo(): BluetoothDeviceInfo? {
    return try {
        // Extract device ID from service data or manufacturer data
        val deviceId = extractDeviceId() ?: uuid4()
        
        BluetoothDeviceInfo(
            deviceId = deviceId,
            name = device.name ?: "Unknown Device",
            address = device.address,
            rssi = rssi,
            serviceUuids = scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList(),
            manufacturerData = scanRecord?.getManufacturerSpecificData(0x004C), // Apple company ID as example
            serviceData = scanRecord?.serviceData?.mapKeys { it.key.toString() } ?: emptyMap()
        )
    } catch (e: Exception) {
        null
    }
}

private fun ScanResult.extractDeviceId(): Uuid? {
    return try {
        // Try to extract from service data first
        scanRecord?.serviceData?.values?.firstOrNull()?.let { data ->
            if (data.size >= 16) {
                val uuid = UUID.nameUUIDFromBytes(data.take(16).toByteArray())
                Uuid.parse(uuid.toString())
            } else null
        } ?: run {
            // Fallback: generate deterministic UUID from MAC address
            val uuid = UUID.nameUUIDFromBytes(device.address.toByteArray())
            Uuid.parse(uuid.toString())
        }
    } catch (e: Exception) {
        null
    }
}