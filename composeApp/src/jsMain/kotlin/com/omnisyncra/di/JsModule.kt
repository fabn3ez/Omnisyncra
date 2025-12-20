package com.omnisyncra.di

import com.omnisyncra.core.platform.JsPlatform
import com.omnisyncra.core.storage.BrowserStorage
import com.omnisyncra.core.storage.LocalStorage
import org.koin.dsl.module

val jsModule = module {
    single { JsPlatform() }
    single<LocalStorage> { BrowserStorage() }
}