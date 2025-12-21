package com.omnisyncra.di

import com.omnisyncra.core.discovery.*
import com.omnisyncra.core.platform.WasmPlatform
import com.omnisyncra.core.storage.WasmStorage
import com.omnisyncra.core.storage.LocalStorage
import org.koin.dsl.module

val wasmModule = module {
    single { WasmPlatform() }
    single<LocalStorage> { WasmStorage() }
    
    // Discovery services
    single<MdnsService> { WasmMdnsService() }
    single<BluetoothService> { WasmBluetoothService() }
    single<DeviceDiscovery> { 
        OmnisyncraDeviceDiscovery(
            platform = get(),
            mdnsService = get(),
            bluetoothService = get()
        )
    }
}