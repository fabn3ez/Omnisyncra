package com.omnisyncra.test

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual fun testSystems() {
    GlobalScope.promise {
        testSystemsImpl()
    }
}