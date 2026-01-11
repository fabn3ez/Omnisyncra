package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Simplified JS Network Adapter for demo
 */
class JsNetworkAdapter : NetworkAdapter {
    override suspend fun scanNetwork(portRange: IntRange): List<DiscoveredDevice> {
        // Simplified for demo - return empty list
        return emptyList()
    }
    
    override suspend fun isHostReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        // Simplified for demo
        return false
    }
    
    override suspend fun getLocalNetworkRanges(): List<NetworkRange> {
        // Simplified for demo - return empty list
        return emptyList()
    }
    
    override fun observeIncomingConnections(): Flow<IncomingConnection> {
        // Simplified for demo - return empty flow
        return emptyFlow()
    }
}