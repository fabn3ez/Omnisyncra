package com.omnisyncra.di

import com.benasher44.uuid.uuid4
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.platform.getPlatform
import com.omnisyncra.core.state.DistributedStateManager
import com.omnisyncra.core.state.StateRecovery
import com.omnisyncra.core.storage.InMemoryStorage
import com.omnisyncra.core.storage.LocalStorage
import org.koin.dsl.module

val commonModule = module {
    // Platform
    single<Platform> { getPlatform() }
    
    // Storage - will be overridden by platform-specific modules
    single<LocalStorage> { InMemoryStorage() }
    
    // State Management
    single { uuid4() } // Node ID
    single { StateRecovery(get(), get()) }
    single { DistributedStateManager(get(), get()) }
}