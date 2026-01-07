package com.omnisyncra.di

import com.omnisyncra.core.network.*
import org.koin.dsl.module

val networkModule = module {
    // Real Network Communication
    single<NetworkCommunicator> { 
        val deviceId = get<com.benasher44.uuid.Uuid>().toString()
        RealNetworkCommunicator(
            deviceId = deviceId,
            securitySystem = get()
        )
    }
}