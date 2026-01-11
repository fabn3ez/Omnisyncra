package com.omnisyncra.core.discovery

/**
 * WASM-specific network adapter factory
 */
actual fun createNetworkAdapter(): NetworkAdapter {
    return WasmNetworkAdapter()
}