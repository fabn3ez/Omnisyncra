package com.omnisyncra.core.discovery

/**
 * JavaScript-specific network adapter factory
 */
actual fun createNetworkAdapter(): NetworkAdapter {
    return JsNetworkAdapter()
}