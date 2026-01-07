package com.omnisyncra.di

import com.omnisyncra.core.handoff.*
import org.koin.dsl.module

val handoffModule = module {
    // Ghost Handoff System
    single<GhostHandoffSystem> { 
        val deviceId = get<com.benasher44.uuid.Uuid>().toString()
        OmnisyncraGhostHandoffSystem(
            deviceId = deviceId,
            securitySystem = get(),
            aiSystem = get(),
            networkCommunicator = get()
        )
    }
}