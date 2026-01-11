package com.omnisyncra.core.discovery

/**
 * JVM-specific network adapter factory
 */
actual fun createNetworkAdapter(): NetworkAdapter {
    return JvmNetworkAdapter()
}