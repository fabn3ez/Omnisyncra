package com.omnisyncra.di

import com.omnisyncra.core.platform.AndroidPlatform
import com.omnisyncra.core.storage.AndroidStorage
import com.omnisyncra.core.storage.LocalStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single { AndroidPlatform(androidContext()) }
    single<LocalStorage> { AndroidStorage(androidContext()) }
}