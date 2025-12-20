package com.omnisyncra.di

import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.platform.getPlatform
import org.koin.dsl.module

val commonModule = module {
    // Platform
    single<Platform> { getPlatform() }
    
    // Will add more dependencies as we progress through phases
}