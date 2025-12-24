package com.omnisyncra.core.network

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

// Mesh network topology
data class MeshTopology(
    val nodes: Map<Uuid, MeshNode>,
    val connections: Map<Uuid, List<MeshConnection>>,
    val routes: Map<Uuid, List<MeshRoute>>
) {
    fun getConnectedNodes(nodeId: Uuid): List<MeshNode> {
        return connections[nodeId]?.mapNotNull { connection ->
            nodes[connection.targetNodeId]
        } ?: emptyList()
    }
    
    fun findRoute(fromNode: Uuid, toNode: Uuid): MeshRoute? {
        return routes[fromNode]?.find { it.targetNodeId == toNode }
    }
    
    fun getNodeCount(): Int = nodes.size
    fun getConnectionCount(): Int = connections.values.sumOf { it.size }
}

// Mesh network node
data class MeshNode(
    val id: Uuid,
    val deviceInfo: com.omnisyncra.core.domain.Device,
    val isOnline: Boolean = true,
    val lastSeen: Long = Clock.System.now().toEpochMilliseconds(),
    val capabilities: MeshCapabilities,
    val networkQuality: NetworkQuality? = null
)

// Mesh node capabilities
data class MeshCapabilities(
    val canRoute: Boolean = true,
    val maxHops: Int = 5,
    val supportedProtocols: Set<String> = setOf("webrtc", "websocket"),
    val bandwidthCapacity: Long = 0, // bytes per second
    val isRelay: Boolean = false
)

// Mesh connection between nodes
data class MeshConnection(
    val id: Uuid = com.benasher44.uuid.uuid4(),
    val sourceNodeId: Uuid,
    val targetNodeId: Uuid,
    val connectionType: ConnectionType,
    val quality: NetworkQuality,
    val isActive: Boolean = true,
    val establishedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastActivity: Long = Clock.System.now().toEpochMilliseconds()
)

enum class ConnectionType {
    DIRECT,      // Direct connection (WebRTC, Bluetooth)
    RELAYED,     // Through another node
    INTERNET     // Through internet infrastructure
}

// Mesh route for multi-hop communication
data class MeshRoute(
    val id: Uuid = com.benasher44.uuid.uuid4(),
    val sourceNodeId: Uuid,
    val targetNodeId: Uuid,
    val hops: List<Uuid>,
    val totalLatency: Long,
    val reliability: Double, // 0.0 to 1.0
    val lastUsed: Long = Clock.System.now().toEpochMilliseconds(),
    val isOptimal: Boolean = false
) {
    val hopCount: Int get() = hops.size
    val isDirectConnection: Boolean get() = hops.isEmpty()
}

// Mesh message for routing
data class MeshMessage(
    val id: Uuid = com.benasher44.uuid.uuid4(),
    val fromNode: Uuid,
    val toNode: Uuid,
    val payload: ByteArray,
    val messageType: String,
    val ttl: Int = 10, // Time to live (max hops)
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val routingPath: MutableList<Uuid> = mutableListOf(),
    val priority: MessagePriority = MessagePriority.NORMAL
) {
    fun addHop(nodeId: Uuid) {
        routingPath.add(nodeId)
    }
    
    fun decrementTTL(): MeshMessage {
        return copy(ttl = ttl - 1)
    }
    
    fun hasVisited(nodeId: Uuid): Boolean {
        return routingPath.contains(nodeId)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MeshMessage
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class MessagePriority {
    LOW, NORMAL, HIGH, CRITICAL
}

// Mesh networking manager
class MeshNetworkingManager(
    private val nodeId: Uuid,
    private val networkQualityManager: NetworkQualityManager
) {
    private var topology = MeshTopology(
        nodes = emptyMap(),
        connections = emptyMap(),
        routes = emptyMap()
    )
    
    private val messageQueue = mutableMapOf<MessagePriority, MutableList<MeshMessage>>()
    private val routingTable = mutableMapOf<Uuid, MeshRoute>()
    
    private val _topologyUpdates = MutableSharedFlow<TopologyUpdate>()
    val topologyUpdates: SharedFlow<TopologyUpdate> = _topologyUpdates.asSharedFlow()
    
    private val _messageEvents = MutableSharedFlow<MeshMessageEvent>()
    val messageEvents: SharedFlow<MeshMessageEvent> = _messageEvents.asSharedFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // Start message processing
        scope.launch {
            while (isActive) {
                processMessageQueue()
                delay(100) // Process every 100ms
            }
        }
        
        // Start topology maintenance
        scope.launch {
            while (isActive) {
                delay(10_000) // Every 10 seconds
                maintainTopology()
            }
        }
    }
    
    suspend fun addNode(node: MeshNode) {
        val updatedNodes = topology.nodes + (node.id to node)
        topology = topology.copy(nodes = updatedNodes)
        
        _topologyUpdates.emit(TopologyUpdate.NodeAdded(node))
        
        // Discover routes to this node
        discoverRoutes(node.id)
    }
    
    suspend fun removeNode(nodeId: Uuid) {
        val node = topology.nodes[nodeId] ?: return
        
        val updatedNodes = topology.nodes - nodeId
        val updatedConnections = topology.connections - nodeId
        val updatedRoutes = topology.routes.mapValues { (_, routes) ->
            routes.filter { route ->
                route.targetNodeId != nodeId && !route.hops.contains(nodeId)
            }
        }
        
        topology = topology.copy(
            nodes = updatedNodes,
            connections = updatedConnections,
            routes = updatedRoutes
        )
        
        _topologyUpdates.emit(TopologyUpdate.NodeRemoved(node))
    }
    
    suspend fun addConnection(connection: MeshConnection) {
        val sourceConnections = topology.connections[connection.sourceNodeId] ?: emptyList()
        val updatedConnections = topology.connections + (
            connection.sourceNodeId to (sourceConnections + connection)
        )
        
        topology = topology.copy(connections = updatedConnections)
        
        _topologyUpdates.emit(TopologyUpdate.ConnectionAdded(connection))
        
        // Update routes based on new connection
        updateRoutingTable()
    }
    
    suspend fun sendMessage(message: MeshMessage) {
        // Add to appropriate priority queue
        val queue = messageQueue.getOrPut(message.priority) { mutableListOf() }
        queue.add(message)
        
        _messageEvents.emit(MeshMessageEvent.MessageQueued(message))
    }
    
    private suspend fun processMessageQueue() {
        // Process messages by priority
        val priorities = listOf(
            MessagePriority.CRITICAL,
            MessagePriority.HIGH,
            MessagePriority.NORMAL,
            MessagePriority.LOW
        )
        
        for (priority in priorities) {
            val queue = messageQueue[priority] ?: continue
            if (queue.isNotEmpty()) {
                val message = queue.removeFirst()
                routeMessage(message)
            }
        }
    }
    
    private suspend fun routeMessage(message: MeshMessage) {
        if (message.ttl <= 0) {
            _messageEvents.emit(MeshMessageEvent.MessageExpired(message))
            return
        }
        
        if (message.toNode == nodeId) {
            // Message reached destination
            _messageEvents.emit(MeshMessageEvent.MessageDelivered(message))
            return
        }
        
        // Find best route to destination
        val route = findBestRoute(message.toNode)
        if (route == null) {
            _messageEvents.emit(MeshMessageEvent.MessageUnroutable(message))
            return
        }
        
        // Forward message to next hop
        val nextHop = if (route.isDirectConnection) {
            route.targetNodeId
        } else {
            route.hops.firstOrNull() ?: route.targetNodeId
        }
        
        val forwardedMessage = message.decrementTTL().apply {
            addHop(nodeId)
        }
        
        // In a real implementation, this would send to the actual next hop
        _messageEvents.emit(MeshMessageEvent.MessageForwarded(forwardedMessage, nextHop))
    }
    
    private fun findBestRoute(targetNodeId: Uuid): MeshRoute? {
        // Check for direct connection first
        val directConnection = topology.connections[nodeId]?.find { 
            it.targetNodeId == targetNodeId && it.isActive 
        }
        
        if (directConnection != null) {
            return MeshRoute(
                sourceNodeId = nodeId,
                targetNodeId = targetNodeId,
                hops = emptyList(),
                totalLatency = directConnection.quality.latency,
                reliability = 1.0 - directConnection.quality.packetLoss,
                isOptimal = true
            )
        }
        
        // Find multi-hop route
        return routingTable[targetNodeId]
    }
    
    private suspend fun discoverRoutes(targetNodeId: Uuid) {
        // Simplified route discovery using breadth-first search
        val visited = mutableSetOf<Uuid>()
        val queue = mutableListOf<Pair<Uuid, List<Uuid>>>() // (nodeId, path)
        
        queue.add(nodeId to emptyList())
        visited.add(nodeId)
        
        while (queue.isNotEmpty()) {
            val (currentNode, path) = queue.removeFirst()
            
            if (currentNode == targetNodeId && path.isNotEmpty()) {
                // Found route
                val totalLatency = calculatePathLatency(path + targetNodeId)
                val reliability = calculatePathReliability(path + targetNodeId)
                
                val route = MeshRoute(
                    sourceNodeId = nodeId,
                    targetNodeId = targetNodeId,
                    hops = path,
                    totalLatency = totalLatency,
                    reliability = reliability
                )
                
                routingTable[targetNodeId] = route
                break
            }
            
            // Add neighbors to queue
            val neighbors = topology.getConnectedNodes(currentNode)
            neighbors.forEach { neighbor ->
                if (!visited.contains(neighbor.id) && path.size < 5) { // Max 5 hops
                    visited.add(neighbor.id)
                    queue.add(neighbor.id to (path + currentNode))
                }
            }
        }
    }
    
    private fun calculatePathLatency(path: List<Uuid>): Long {
        var totalLatency = 0L
        
        for (i in 0 until path.size - 1) {
            val connection = topology.connections[path[i]]?.find { 
                it.targetNodeId == path[i + 1] 
            }
            totalLatency += connection?.quality?.latency ?: 100L
        }
        
        return totalLatency
    }
    
    private fun calculatePathReliability(path: List<Uuid>): Double {
        var totalReliability = 1.0
        
        for (i in 0 until path.size - 1) {
            val connection = topology.connections[path[i]]?.find { 
                it.targetNodeId == path[i + 1] 
            }
            val reliability = connection?.let { 1.0 - it.quality.packetLoss } ?: 0.9
            totalReliability *= reliability
        }
        
        return totalReliability
    }
    
    private suspend fun updateRoutingTable() {
        // Recalculate all routes when topology changes
        val allNodes = topology.nodes.keys.filter { it != nodeId }
        
        allNodes.forEach { targetNode ->
            discoverRoutes(targetNode)
        }
    }
    
    private suspend fun maintainTopology() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val staleThreshold = 60_000L // 1 minute
        
        // Remove stale nodes
        val staleNodes = topology.nodes.values.filter { node ->
            !node.isOnline || (currentTime - node.lastSeen) > staleThreshold
        }
        
        staleNodes.forEach { node ->
            removeNode(node.id)
        }
        
        // Update network quality for active connections
        topology.connections.values.flatten().forEach { connection ->
            if (connection.isActive) {
                scope.launch {
                    try {
                        val quality = networkQualityManager.getCurrentQuality(connection.targetNodeId)
                        if (quality != null) {
                            // Update connection quality
                            val updatedConnection = connection.copy(
                                quality = quality,
                                lastActivity = currentTime
                            )
                            // Update in topology would happen here
                        }
                    } catch (e: Exception) {
                        // Handle quality check errors
                    }
                }
            }
        }
    }
    
    fun getTopology(): MeshTopology = topology
    
    fun getNode(nodeId: Uuid): MeshNode? = topology.nodes[nodeId]
    
    fun getConnectedNodes(): List<MeshNode> = topology.getConnectedNodes(nodeId)
    
    fun getRoute(targetNodeId: Uuid): MeshRoute? = routingTable[targetNodeId]
    
    fun getAllRoutes(): Map<Uuid, MeshRoute> = routingTable.toMap()
    
    suspend fun getMeshStats(): MeshNetworkStats {
        val totalNodes = topology.getNodeCount()
        val totalConnections = topology.getConnectionCount()
        val activeRoutes = routingTable.size
        val averageHops = routingTable.values.map { it.hopCount }.average()
        val networkReliability = routingTable.values.map { it.reliability }.average()
        
        return MeshNetworkStats(
            totalNodes = totalNodes,
            totalConnections = totalConnections,
            activeRoutes = activeRoutes,
            averageHops = averageHops,
            networkReliability = networkReliability,
            messageQueueSize = messageQueue.values.sumOf { it.size }
        )
    }
    
    fun cleanup() {
        scope.cancel()
        messageQueue.clear()
        routingTable.clear()
    }
}

// Topology update events
sealed class TopologyUpdate {
    data class NodeAdded(val node: MeshNode) : TopologyUpdate()
    data class NodeRemoved(val node: MeshNode) : TopologyUpdate()
    data class ConnectionAdded(val connection: MeshConnection) : TopologyUpdate()
    data class ConnectionRemoved(val connection: MeshConnection) : TopologyUpdate()
    data class RouteUpdated(val route: MeshRoute) : TopologyUpdate()
}

// Mesh message events
sealed class MeshMessageEvent {
    data class MessageQueued(val message: MeshMessage) : MeshMessageEvent()
    data class MessageForwarded(val message: MeshMessage, val nextHop: Uuid) : MeshMessageEvent()
    data class MessageDelivered(val message: MeshMessage) : MeshMessageEvent()
    data class MessageExpired(val message: MeshMessage) : MeshMessageEvent()
    data class MessageUnroutable(val message: MeshMessage) : MeshMessageEvent()
}

// Mesh network statistics
data class MeshNetworkStats(
    val totalNodes: Int,
    val totalConnections: Int,
    val activeRoutes: Int,
    val averageHops: Double,
    val networkReliability: Double,
    val messageQueueSize: Int
)