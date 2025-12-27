package com.omnisyncra

import android.app.Application
import com.omnisyncra.di.commonModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OmnisyncraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin DI
        startKoin {
            androidContext(this@OmnisyncraApplication)
            modules(commonModule)
        }
    }
}