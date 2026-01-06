package com.omnisyncra.di

import com.benasher44.uuid.uuid4
import com.omnisyncra.core.security.*
import org.koin.dsl.module

val securityModule = module {
    // Crypto Engine
    single<CryptoEngine> { CryptoEngine() }
    
    // Security System
    single<SecuritySystem> { 
        val deviceId = get<com.benasher44.uuid.Uuid>().toString()
        OmnisyncraSecuritySystem(deviceId)
    }
}