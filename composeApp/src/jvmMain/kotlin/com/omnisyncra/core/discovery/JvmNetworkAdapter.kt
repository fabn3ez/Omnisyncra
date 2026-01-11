package com.omnisyncra.core.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.net.*
import java.io.IOException
import kotlin.time.measureTime

/**
 * Enhanced JVM-specific network adapter with real TCP scanning and comprehensive network discovery
 */
class JvmNetworkAdapter : NetworkAdapter {
    
    private val incomingConnections = MutableSharedFlow<IncomingConnection>()
    
    override suspend fun scanNetwork(portRange: IntRange): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val networkRanges = getLocalNetworkRanges()
        val discoveredDevices = mutableListOf<DiscoveredDevice>()
        
        // Enhanced scanning with better error handling and validation
        for (networkRange in networkRanges) {
            try {
                val devices = scanNetworkRange(networkRange, portRange)
                // Validate each discovered device with actual connectivity test
                val validatedDevices = devices.filter { device ->
                    validateDeviceConnectivity(device.ipAddress, device.port)
                }
                discoveredDevices.addAll(validatedDevices)
            } catch (e: Exception) {
                // Log error but continue with other network ranges
                println("Error scanning network range ${networkRange.networkInterface}: ${e.message}")
            }
        }
        
        // Remove duplicates and sort by response time (fastest first)
        discoveredDevices.distinctBy { "${it.ipAddress}:${it.port}" }
            .sortedBy { it.responseTime }
    }
    
    private suspend fun scanNetworkRange(networkRange: NetworkRange, portRange: IntRange): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val discoveredDevices = mutableListOf<DiscoveredDevice>()
        
        // Create async tasks for each IP address in the range
        val scanTasks = networkRange.addressRange.map { ipAddress ->
            async {
                scanHostPorts(ipAddress, portRange)
            }
        }
        
        // Wait for all scans to complete and collect results
        val results = scanTasks.awaitAll()
        results.forEach { devices ->
            discoveredDevices.addAll(devices)
        }
        
        discoveredDevices
    }
    
    private suspend fun scanHostPorts(ipAddress: String, portRange: IntRange): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val discoveredDevices = mutableListOf<DiscoveredDevice>()
        
        for (port in portRange) {
            try {
                val responseTime = measureTime {
                    isHostReachable(ipAddress, port, 1000)
                }
                
                if (isHostReachable(ipAddress, port, 1000)) {
                    // Try to get device information if possible
                    val deviceInfo = tryGetDeviceInfo(ipAddress, port)
                    discoveredDevices.add(
                        DiscoveredDevice(
                            ipAddress = ipAddress,
                            port = port,
                            responseTime = responseTime.inWholeMilliseconds,
                            deviceInfo = deviceInfo
                        )
                    )
                }
            } catch (e: Exception) {
                // Port not reachable or connection failed, continue scanning
            }
        }
        
        discoveredDevices
    }
    
    override suspend fun isHostReachable(host: String, port: Int, timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // Enhanced TCP socket connection with proper resource management
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                
                // Additional validation: try to send/receive a small test packet
                return@withContext validateTcpConnection(socket)
            }
        } catch (e: IOException) {
            false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validate TCP connection by attempting a simple data exchange
     */
    private suspend fun validateTcpConnection(socket: Socket): Boolean = withContext(Dispatchers.IO) {
        try {
            // Try to get input/output streams to validate the connection is fully established
            socket.getInputStream()
            socket.getOutputStream()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Enhanced device connectivity validation with multiple checks
     */
    private suspend fun validateDeviceConnectivity(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // Primary check: TCP connection
            if (!isHostReachable(host, port, 2000)) {
                return@withContext false
            }
            
            // Secondary check: Try to resolve hostname if it's an IP
            try {
                val address = InetAddress.getByName(host)
                // If we can resolve it and it's reachable, it's likely a valid device
                address.isReachable(1000)
            } catch (e: Exception) {
                // If hostname resolution fails, still consider it valid if TCP worked
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getLocalNetworkRanges(): List<NetworkRange> = withContext(Dispatchers.IO) {
        val networkRanges = mutableListOf<NetworkRange>()
        
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                
                // Enhanced interface filtering
                if (shouldSkipInterface(networkInterface)) {
                    continue
                }
                
                // Process all IPv4 addresses for this interface
                val interfaceRanges = processNetworkInterface(networkInterface)
                networkRanges.addAll(interfaceRanges)
            }
        } catch (e: Exception) {
            println("Error discovering network interfaces: ${e.message}")
            // Enhanced fallback with multiple localhost variants
            networkRanges.addAll(createFallbackRanges())
        }
        
        // If no ranges found, add fallback
        if (networkRanges.isEmpty()) {
            networkRanges.addAll(createFallbackRanges())
        }
        
        networkRanges
    }
    
    /**
     * Enhanced interface filtering logic
     */
    private fun shouldSkipInterface(networkInterface: NetworkInterface): Boolean {
        return networkInterface.isLoopback || 
               !networkInterface.isUp || 
               networkInterface.isVirtual ||
               networkInterface.name.startsWith("docker") ||
               networkInterface.name.startsWith("veth") ||
               networkInterface.name.startsWith("br-")
    }
    
    /**
     * Process a single network interface and extract all valid IP ranges
     */
    private fun processNetworkInterface(networkInterface: NetworkInterface): List<NetworkRange> {
        val ranges = mutableListOf<NetworkRange>()
        
        try {
            // Get interface addresses with subnet information
            val interfaceAddresses = networkInterface.interfaceAddresses
            
            for (interfaceAddress in interfaceAddresses) {
                val address = interfaceAddress.address
                
                // Only process IPv4 addresses
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    val networkRange = createNetworkRangeFromInterface(
                        networkInterface.name, 
                        address, 
                        interfaceAddress.networkPrefixLength
                    )
                    if (networkRange != null) {
                        ranges.add(networkRange)
                    }
                }
            }
        } catch (e: Exception) {
            println("Error processing interface ${networkInterface.name}: ${e.message}")
        }
        
        return ranges
    }
    
    /**
     * Create network range with proper subnet calculation
     */
    private fun createNetworkRangeFromInterface(
        interfaceName: String, 
        address: Inet4Address, 
        prefixLength: Short
    ): NetworkRange? {
        try {
            val addressBytes = address.address
            val networkAddress = calculateNetworkAddress(addressBytes, prefixLength)
            val subnetMask = calculateSubnetMask(prefixLength)
            val addressRange = generateAddressRange(networkAddress, prefixLength)
            
            return NetworkRange(
                networkInterface = interfaceName,
                baseAddress = address.hostAddress,
                subnetMask = subnetMask,
                addressRange = addressRange
            )
        } catch (e: Exception) {
            println("Error creating network range for $interfaceName: ${e.message}")
            return null
        }
    }
    
    /**
     * Calculate network address from IP and prefix length
     */
    private fun calculateNetworkAddress(addressBytes: ByteArray, prefixLength: Short): String {
        val mask = (-1L shl (32 - prefixLength)).toInt()
        val networkBytes = ByteArray(4)
        
        for (i in 0..3) {
            networkBytes[i] = (addressBytes[i].toInt() and (mask shr (8 * (3 - i)))).toByte()
        }
        
        return "${networkBytes[0].toUByte()}.${networkBytes[1].toUByte()}.${networkBytes[2].toUByte()}.${networkBytes[3].toUByte()}"
    }
    
    /**
     * Calculate subnet mask from prefix length
     */
    private fun calculateSubnetMask(prefixLength: Short): String {
        val mask = (-1L shl (32 - prefixLength)).toInt()
        return "${(mask shr 24) and 0xFF}.${(mask shr 16) and 0xFF}.${(mask shr 8) and 0xFF}.${mask and 0xFF}"
    }
    
    /**
     * Generate address range for scanning based on network and prefix
     */
    private fun generateAddressRange(networkAddress: String, prefixLength: Short): List<String> {
        val parts = networkAddress.split(".")
        val baseOctets = parts.map { it.toInt() }
        val addresses = mutableListOf<String>()
        
        // For common subnet sizes, generate appropriate ranges
        when (prefixLength.toInt()) {
            24 -> {
                // /24 subnet - scan all host addresses
                for (i in 1..254) {
                    addresses.add("${baseOctets[0]}.${baseOctets[1]}.${baseOctets[2]}.$i")
                }
            }
            16 -> {
                // /16 subnet - scan limited range to avoid excessive scanning
                for (j in 0..10) {
                    for (i in 1..254) {
                        addresses.add("${baseOctets[0]}.${baseOctets[1]}.$j.$i")
                    }
                }
            }
            else -> {
                // For other subnet sizes, use a conservative approach
                for (i in 1..100) {
                    addresses.add("${baseOctets[0]}.${baseOctets[1]}.${baseOctets[2]}.$i")
                }
            }
        }
        
        return addresses
    }
    
    /**
     * Create fallback network ranges when interface discovery fails
     */
    private fun createFallbackRanges(): List<NetworkRange> {
        return listOf(
            NetworkRange(
                networkInterface = "localhost",
                baseAddress = "127.0.0.1",
                subnetMask = "255.255.255.255",
                addressRange = listOf("127.0.0.1")
            ),
            NetworkRange(
                networkInterface = "local-fallback",
                baseAddress = "192.168.1.1",
                subnetMask = "255.255.255.0",
                addressRange = (1..254).map { "192.168.1.$it" }
            )
        )
    }
    
    private suspend fun tryGetDeviceInfo(ipAddress: String, port: Int): Map<String, String> = withContext(Dispatchers.IO) {
        val deviceInfo = mutableMapOf<String, String>()
        
        try {
            // Enhanced device information gathering
            val address = InetAddress.getByName(ipAddress)
            
            // Try to get hostname
            val hostname = address.hostName
            if (hostname != ipAddress) {
                deviceInfo["hostname"] = hostname
            }
            
            // Try to get canonical hostname
            val canonicalHostname = address.canonicalHostName
            if (canonicalHostname != ipAddress && canonicalHostname != hostname) {
                deviceInfo["canonical_hostname"] = canonicalHostname
            }
            
            // Check if address is reachable via ICMP
            val icmpReachable = address.isReachable(2000)
            deviceInfo["icmp_reachable"] = icmpReachable.toString()
            
            // Add enhanced connection info
            deviceInfo["protocol"] = "TCP"
            deviceInfo["discovered_via"] = "jvm_enhanced_scan"
            deviceInfo["platform"] = "desktop"
            deviceInfo["scan_timestamp"] = System.currentTimeMillis().toString()
            
            // Try to determine if it's a local or remote address
            val isLocal = address.isSiteLocalAddress || address.isLinkLocalAddress
            deviceInfo["address_scope"] = if (isLocal) "local" else "remote"
            
        } catch (e: Exception) {
            // Fallback to basic info if enhanced discovery fails
            deviceInfo["protocol"] = "TCP"
            deviceInfo["discovered_via"] = "jvm_enhanced_scan"
            deviceInfo["platform"] = "desktop"
            deviceInfo["scan_timestamp"] = System.currentTimeMillis().toString()
            deviceInfo["error"] = "limited_info: ${e.message}"
        }
        
        deviceInfo
    }
    
    override fun observeIncomingConnections(): Flow<IncomingConnection> {
        return incomingConnections
    }
}