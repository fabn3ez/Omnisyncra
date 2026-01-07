package com.omnisyncra.di

import com.omnisyncra.core.crdt.CrdtStateManager
import com.omnisyncra.core.state.CrdtDistributedStateManager
import com.omnisyncra.core.state.DistributedStateManager
import org.koin.dsl.module

val crdtModule = module {
    // CRDT State Manager
    single<CrdtStateManager> { 
        CrdtStateManager(
            nodeId = get(),
            localStorage = get()
        )
    }
    
    // CRDT-enabled Distributed State Manager
    factory<DistributedStateManager> { 
        CrdtDistributedStateManager(
            nodeId = get(),
            storage = get()
        )
    }
}