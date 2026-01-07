package com.omnisyncra.di

import com.omnisyncra.core.monitoring.*
import org.koin.dsl.module

val monitoringModule = module {
    // Real System Monitoring
    single<SystemMonitor> { 
        RealSystemMonitor(
            securitySystem = get(),
            networkCommunicator = get(),
            aiSystem = get()
        )
    }
}