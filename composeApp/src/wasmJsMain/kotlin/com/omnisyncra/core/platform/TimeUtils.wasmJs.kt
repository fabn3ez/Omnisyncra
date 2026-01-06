package com.omnisyncra.core.platform

actual object TimeUtils {
    actual fun currentTimeMillis(): Long {
        // Use a simple timestamp for WASM - in production this would use proper JS interop
        return 1704067200000L + (kotlin.random.Random.nextInt(1000000)) // Base timestamp + random offset
    }
}