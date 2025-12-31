package com.omnisyncra.di

import com.omnisyncra.core.security.SecuritySystem
import com.omnisyncra.core.security.OmnisyncraSecuritySystem
import com.omnisyncra.core.state.DistributedStateManager
import com.omnisyncra.core.state.SimpleDistributedStateManager
import com.benasher44.uuid.uuid4
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Common dependency injection module for shared components
 */
val commonModule = module {
    
    // Security System
    single<SecuritySystem> { OmnisyncraSecuritySystem(nodeId = uuid4()) }
    
    // State Management
    single<DistributedStateManager> { SimpleDistributedStateManager() }
    
    // Include AI module
    includes(aiModule)
}