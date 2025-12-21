package com.omnisyncra.di

import com.omnisyncra.core.discovery.*
import com.omnisyncra.core.platform.JvmPlatform
import com.omnisyncra.core.storage.FileStorage
import com.omnisyncra.core.storage.LocalStorage
import org.koin.dsl.module

val jvmModule = module {
    single { JvmPlatform() }
    single<LocalStorage> { FileStorage() }
    
    // Discovery services
    single<MdnsService> { JvmMdnsService() }
    single<BluetoothService> { JvmBluetoothService() }
    single<DeviceDiscovery> { 
        OmnisyncraDeviceDiscovery(
            platform = get(),
            mdnsService = get(),
            bluetoothService = get()
        )
    }
}