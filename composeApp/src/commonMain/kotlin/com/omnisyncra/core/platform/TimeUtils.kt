package com.omnisyncra.core.platform

/**
 * Cross-platform time utilities
 */
expect object TimeUtils {
    /**
     * Get current time in milliseconds since epoch
     */
    fun currentTimeMillis(): Long
}