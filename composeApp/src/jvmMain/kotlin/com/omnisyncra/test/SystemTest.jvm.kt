package com.omnisyncra.test

import kotlinx.coroutines.runBlocking

actual fun testSystems() {
    runBlocking {
        testSystemsImpl()
    }
}