package com.omnisyncra.di

import com.omnisyncra.core.platform.WasmPlatform
import com.omnisyncra.core.storage.WasmStorage
import com.omnisyncra.core.storage.LocalStorage
import org.koin.dsl.module

val wasmModule = module {
    single { WasmPlatform() }
    single<LocalStorage> { WasmStorage() }
}