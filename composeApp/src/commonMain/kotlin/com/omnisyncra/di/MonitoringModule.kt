package com.omnisyncra.di

import com.omnisyncra.core.monitoring.*
import com.omnisyncra.core.performance.*
import org.koin.dsl.module

val monitoringModule = module {
    // Performance Monitor
    single<PerformanceMonitor> { 
        PerformanceMonitor()
    }
    
    // Base Real System Monitor
    single<RealSystemMonitor> { 
        RealSystemMonitor(
            securitySystem = get(),
            networkCommunicator = get(),
            aiSystem = get()
        )
    }
    
    // Enhanced Real System Monitor (primary interface)
    single<SystemMonitor> { 
        EnhancedRealSystemMonitor(
            baseMonitor = get<RealSystemMonitor>(),
            performanceMonitor = get<PerformanceMonitor>(),
            securitySystem = get(),
            networkCommunicator = get(),
            aiSystem = get()
        )
    }
    
    // Enhanced Performance Monitor interface
    single<EnhancedPerformanceMonitor> { 
        get<SystemMonitor>() as EnhancedRealSystemMonitor
    }
}