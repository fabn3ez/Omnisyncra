package com.omnisyncra.di

import com.omnisyncra.core.platform.JvmPlatform
import com.omnisyncra.core.storage.FileStorage
import com.omnisyncra.core.storage.LocalStorage
import org.koin.dsl.module

val jvmModule = module {
    single { JvmPlatform() }
    single<LocalStorage> { FileStorage() }
}