package com.omnisyncra.core.discovery

/**
 * Android-specific network adapter factory
 */
actual fun createNetworkAdapter(): NetworkAdapter {
    return AndroidNetworkAdapter()
}