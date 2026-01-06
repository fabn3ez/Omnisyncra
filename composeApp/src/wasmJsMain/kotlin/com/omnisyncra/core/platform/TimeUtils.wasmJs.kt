package com.omnisyncra.core.platform

actual object TimeUtils {
    actual fun currentTimeMillis(): Long = js("Date.now()").unsafeCast<Double>().toLong()
}