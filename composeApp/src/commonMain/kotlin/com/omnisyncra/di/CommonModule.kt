package com.omnisyncra.di

import com.omnisyncra.core.security.SecuritySystem
import com.omnisyncra.core.security.OmnisyncraSecuritySystem
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Common dependency injection module for shared components
 */
val commonModule = module {
    
    // Security System
    single<SecuritySystem> { OmnisyncraSecuritySystem() }
    
    // Include AI module
    includes(aiModule)
}