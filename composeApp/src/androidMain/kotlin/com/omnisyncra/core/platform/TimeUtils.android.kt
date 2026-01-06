package com.omnisyncra.core.platform

actual object TimeUtils {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}