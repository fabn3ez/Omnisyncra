package com.omnisyncra.core.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.measureTime

/**
 * Enhanced WebAssembly-specific network adapter with optimized real WebSocket connections
 * Includes proper error handling for WASM network limitations and performance optimizations
 */
class WasmNetworkAdapter : NetworkAdapter {
    
    private val incomingConnections = MutableSharedFlow<IncomingConnection>()
    
    // WASM-specific optimization modes
    enum class WasmOptimizationMode {
        PERFORMANCE,        // Prioritize speed and efficiency
        COMPATIBILITY,      // Prioritize broad compatibility
        MEMORY_EFFICIENT    // Prioritize low memory usage
    }
    
    private var optimizationMode = WasmOptimizationMode.PERFORMANCE
    
    // Optimized port ranges for WASM environments
    private val wasmOptimizedPorts = listOf(8080, 8081, 8082, 3000, 3001, 4000, 5000, 9000)
    
    override suspend fun scanNetwork(portRange: IntRange): List<DiscoveredDevice> = coroutineScope {
        val discoveredDevices = mutableListOf<DiscoveredDevice>()
        
        try {
            // Enhanced WASM discovery with performance optimizations
            when (optimizationMode) {
                WasmOptimizationMode.PERFORMANCE -> {
                    discoveredDevices.addAll(performOptimizedWebSocketScan(portRange))
                }
                WasmOptimizationMode.COMPATIBILITY -> {
                    discoveredDevices.addAll(performCompatibilityWebSocketScan(portRange))
                }
                WasmOptimizationMode.MEMORY_EFFICIENT -> {
                    discoveredDevices.addAll(performMemoryEfficientScan(portRange))
                }
            }
        } catch (e: Exception) {
            console.log("WASM network scan error: ${e.message}")
            // Fallback to basic scan with error recovery
            discoveredDevices.addAll(performBasicWasmScan(portRange))
        }
        
        // WASM-optimized result processing
        discoveredDevices.distinctBy { "${it.ipAddress}:${it.port}" }
            .sortedBy { it.responseTime }
            .take(15) // Limit results for WASM memory efficiency
    }
    
    /**
     * Performance-optimized WebSocket scanning for WASM
     */
    private suspend fun performOptimizedWebSocketScan(portRange: IntRange): List<DiscoveredDevice> = coroutineScope {
        val discoveredDevices = mutableListOf<DiscoveredDevice>()
        
        // Get WASM-accessible hosts
        val hostsToScan = getWasmAccessibleHosts()
        
        // Combine provided ports with WASM-optimized ports
        val portsToScan = (portRange.toList() + wasmOptimizedPorts).distinct().sorted()
        
        // Use chunked parallel processing for better WASM performance
        val scanTasks = hostsToScan.flatMap { host ->
            portsToScan.chunked(3).map { portChunk ->
                async {
                    val chunkDevices = mutableListOf<DiscoveredDevice>()
                    for (port in portChunk) {
                        try {
                            val responseTime = measureTime {
                                if (isWasmWebSocketReachable(host, port, 2000)) {
                                    val deviceInfo = createWasmDeviceInfo(host, port, "optimized")
                                    chunkDevices.add(
                                        DiscoveredDevice(
                                            ipAddress = host,
                                            port = port,
                                            responseTime = responseTime.inWholeMilliseconds,
                                            deviceInfo = deviceInfo
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            // Continue with next port
                        }
                    }
                    chunkDevices
                }
            }
        }
        
        val results = scanTasks.awaitAll()
        results.forEach { devices ->
            discoveredDevices.addAll(devices)
        }
        
        discoveredDevices
    }
    
    /**
     * Compatibility-focused WebSocket scanning for WASM
     */
    private suspend fun performCompatibilityWebSocketScan(portRange: IntRange): List<DiscoveredDevice> = coroutineScope {
        val discoveredDevices = mutableListOf<DiscoveredDevice>()
        
        val hostsToScan = getWasmAccessibleHosts()
        val portsToScan = portRange.toList().take(10) // Limit for compatibility
        
        // Sequential scanning for maximum compatibility
        for (host in hostsToScan) {
            for (port in portsToScan) {
                try {
                    val responseTime = measureTime {
                        if (isWasmWebSocketReachable(host, port, 3000)) {
                            val deviceInfo = createWasmDeviceInfo(host, port, "compatibility")
                            discoveredDevices.add(
                                DiscoveredDevice(
                                    ipAddress = host,
                                    port = port,
                                    responseTime = responseTime.inWholeMilliseconds,
                                    deviceInfo = deviceInfo
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Continue scanning with error tolerance
                }
            }
        }
        
        discoveredDevices
    }
    
    /**
     * Memory-efficient scanning for WASM environments with limited resources
     */
    private suspend fun performMemoryEfficientScan(portRange: IntRange): List<DiscoveredDevice> {
        val discoveredDevices = mutableListOf<DiscoveredDevice>()
        
        val hostsToScan = listOf("localhost", "127.0.0.1") // Minimal host list
        val portsToScan = wasmOptimizedPorts.take(5) // Very limited port range
        
        // Minimal scanning to conserve memory
        for (host in hostsToScan) {
            for (port in portsToScan) {
                try {
                    if (isWasmWebSocketReachable(host, port, 1500)) {
                        val deviceInfo = createWasmDeviceInfo(host, port, "memory_efficient")
                        discoveredDevices.add(
                            DiscoveredDevice(
                                ipAddress = host,
                                port = port,
                                responseTime = 0L, // Skip timing to save memory
                                deviceInfo = deviceInfo
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Continue with minimal error handling
                }
            }
        }
        
        return discoveredDevices
    }
    
    /**
     * Basic WASM scan fallback with error recovery
     */
    private suspend fun performBasicWasmScan(portRange: IntRange): List<DiscoveredDevice> {
        val discoveredDevices = mutableListOf<DiscoveredDevice>()
        val basicHosts = listOf("localhost", "127.0.0.1")
        val basicPorts = portRange.take(5)
        
        for (host in basicHosts) {
            for (port in basicPorts) {
                try {
                    if (isWasmWebSocketReachable(host, port, 1000)) {
                        val deviceInfo = mapOf(
                            "protocol" to "WebSocket",
                            "discovered_via" to "wasm_basic_fallback",
                            "platform" to "wasm"
                        )
                        
                        discoveredDevices.add(
                            DiscoveredDevice(
                                ipAddress = host,
                                port = port,
                                responseTime = 0L,
                                deviceInfo = deviceInfo
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Silent failure for fallback
                }
            }
        }
        
        return discoveredDevices
    }
    
    /**
     * Get hosts accessible from WASM environment
     */
    private fun getWasmAccessibleHosts(): List<String> {
        val hosts = mutableListOf("localhost", "127.0.0.1")
        
        // Try to add current origin if available
        try {
            val currentHostname = js("typeof window !== 'undefined' ? window.location.hostname : null") as? String
            if (currentHostname != null && currentHostname !in hosts) {
                hosts.add(currentHostname)
            }
        } catch (e: Exception) {
            // Ignore if unable to get hostname in WASM context
        }
        
        return hosts
    }
    
    /**
     * WASM-optimized WebSocket reachability test
     */
    private suspend fun isWasmWebSocketReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            val wsUrl = "ws://$host:$port"
            
            kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                try {
                    // Use optimized WebSocket creation for WASM
                    val ws = js("new WebSocket(wsUrl)") as dynamic
                    var resolved = false
                    var timeoutId: Int? = null
                    
                    // Optimized timeout handling for WASM
                    timeoutId = js("setTimeout(() => { if (!resolved) { resolved = true; ws.close(); } }, timeoutMs)") as Int
                    
                    ws.onopen = {
                        if (!resolved) {
                            resolved = true
                            timeoutId?.let { js("clearTimeout(it)") }
                            ws.close()
                            continuation.resumeWith(Result.success(true))
                        }
                    }
                    
                    ws.onerror = {
                        if (!resolved) {
                            resolved = true
                            timeoutId?.let { js("clearTimeout(it)") }
                            continuation.resumeWith(Result.success(false))
                        }
                    }
                    
                    ws.onclose = {
                        if (!resolved) {
                            resolved = true
                            timeoutId?.let { js("clearTimeout(it)") }
                            continuation.resumeWith(Result.success(false))
                        }
                    }
                    
                    // Handle cancellation efficiently in WASM
                    continuation.invokeOnCancellation {
                        if (!resolved) {
                            resolved = true
                            timeoutId?.let { js("clearTimeout(it)") }
                            ws.close()
                        }
                    }
                } catch (e: Exception) {
                    continuation.resumeWith(Result.success(false))
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Create WASM-specific device information
     */
    private fun createWasmDeviceInfo(host: String, port: Int, scanMode: String): Map<String, String> {
        val deviceInfo = mutableMapOf<String, String>()
        
        try {
            deviceInfo["protocol"] = "WebSocket"
            deviceInfo["discovered_via"] = "wasm_enhanced_scan"
            deviceInfo["platform"] = "wasm"
            deviceInfo["scan_mode"] = scanMode
            deviceInfo["optimization_mode"] = optimizationMode.name.lowercase()
            deviceInfo["scan_timestamp"] = js("Date.now()").toString()
            
            // Add WASM-specific performance info
            deviceInfo["wasm_optimized"] = "true"
            deviceInfo["memory_efficient"] = (optimizationMode == WasmOptimizationMode.MEMORY_EFFICIENT).toString()
            
            // Try to get origin info if available
            try {
                val origin = js("typeof window !== 'undefined' ? window.location.origin : 'wasm-context'") as? String
                deviceInfo["origin"] = origin ?: "wasm-context"
            } catch (e: Exception) {
                deviceInfo["origin"] = "wasm-context"
            }
            
        } catch (e: Exception) {
            // Fallback device info
            deviceInfo["protocol"] = "WebSocket"
            deviceInfo["discovered_via"] = "wasm_enhanced_scan"
            deviceInfo["platform"] = "wasm"
            deviceInfo["error"] = "limited_info: ${e.message}"
        }
        
        return deviceInfo
    }
    
    /**
     * Set optimization mode for WASM-specific performance tuning
     */
    fun setOptimizationMode(mode: WasmOptimizationMode) {
        optimizationMode = mode
    }
    
    override suspend fun isHostReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            // Enhanced WASM reachability test with optimization awareness
            when (optimizationMode) {
                WasmOptimizationMode.PERFORMANCE -> {
                    isWasmWebSocketReachable(host, port, minOf(timeoutMs, 2000))
                }
                WasmOptimizationMode.COMPATIBILITY -> {
                    isWasmWebSocketReachable(host, port, timeoutMs)
                }
                WasmOptimizationMode.MEMORY_EFFICIENT -> {
                    isWasmWebSocketReachable(host, port, minOf(timeoutMs, 1000))
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override fun observeIncomingConnections(): Flow<IncomingConnection> {
        return incomingConnections
    }
    
    override suspend fun getLocalNetworkRanges(): List<NetworkRange> {
        // WASM has limited network access, return basic localhost range
        return listOf(
            NetworkRange(
                networkInterface = "localhost",
                baseAddress = "127.0.0.1",
                subnetMask = "255.255.255.255",
                addressRange = listOf("127.0.0.1", "localhost")
            )
        )
    }
}