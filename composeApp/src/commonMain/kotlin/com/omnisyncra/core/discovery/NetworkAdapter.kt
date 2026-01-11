package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific network adapter for real device discovery
 */
interface NetworkAdapter {
    /**
     * Scan for devices on the local network
     * @param portRange Range of ports to scan
     * @return List of discovered devices that respond to network requests
     */
    suspend fun scanNetwork(portRange: IntRange = 8080..8090): List<DiscoveredDevice>
    
    /**
     * Test if a specific host and port is reachable
     * @param host IP address or hostname
     * @param port Port number
     * @param timeoutMs Connection timeout in milliseconds
     * @return true if connection successful, false otherwise
     */
    suspend fun isHostReachable(host: String, port: Int, timeoutMs: Int = 3000): Boolean
    
    /**
     * Get local network interfaces and IP ranges
     * @return List of network interfaces with their IP ranges
     */
    suspend fun getLocalNetworkRanges(): List<NetworkRange>
    
    /**
     * Observe incoming connection attempts
     */
    fun observeIncomingConnections(): Flow<IncomingConnection>
}

/**
 * Discovered device information from network scanning
 */
data class DiscoveredDevice(
    val ipAddress: String,
    val port: Int,
    val responseTime: Long,
    val deviceInfo: Map<String, String> = emptyMap()
)

/**
 * Network range for scanning
 */
data class NetworkRange(
    val networkInterface: String,
    val baseAddress: String,
    val subnetMask: String,
    val addressRange: List<String>
)

/**
 * Incoming connection information
 */
data class IncomingConnection(
    val sourceAddress: String,
    val sourcePort: Int,
    val timestamp: Long
)