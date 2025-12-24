package com.omnisyncra.core.performance

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

data class ConnectionPoolConfig(
    val maxConnectionsPerDevice: Int = 3,
    val connectionTimeoutMs: Long = 30_000L,
    val idleTimeoutMs: Long = 300_000L, // 5 minutes
    val maxTotalConnections: Int = 50
)

data class PooledConnection(
    val id: Uuid = com.benasher44.uuid.uuid4(),
    val deviceId: Uuid,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    var lastUsedAt: Long = Clock.System.now().toEpochMilliseconds(),
    var isInUse: Boolean = false,
    val channel: Channel<ByteArray> = Channel(Channel.UNLIMITED)
)

class ConnectionPool(
    private val config: ConnectionPoolConfig = ConnectionPoolConfig()
) {
    private val connections = mutableMapOf<Uuid, MutableList<PooledConnection>>()
    private val mutex = Mutex()
    private val cleanupJob: Job
    
    init {
        cleanupJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(60_000L) // Cleanup every minute
                cleanupIdleConnections()
            }
        }
    }
    
    suspend fun acquireConnection(deviceId: Uuid): PooledConnection? {
        return mutex.withLock {
            // Try to find an available connection
            val deviceConnections = connections[deviceId] ?: mutableListOf()
            val availableConnection = deviceConnections.find { !it.isInUse }
            
            if (availableConnection != null) {
                availableConnection.isInUse = true
                availableConnection.lastUsedAt = Clock.System.now().toEpochMilliseconds()
                return@withLock availableConnection
            }
            
            // Create new connection if under limit
            if (deviceConnections.size < config.maxConnectionsPerDevice && 
                getTotalConnections() < config.maxTotalConnections) {
                
                val newConnection = PooledConnection(deviceId = deviceId)
                newConnection.isInUse = true
                deviceConnections.add(newConnection)
                connections[deviceId] = deviceConnections
                return@withLock newConnection
            }
            
            null // No connections available
        }
    }
    
    suspend fun releaseConnection(connection: PooledConnection) {
        mutex.withLock {
            connection.isInUse = false
            connection.lastUsedAt = Clock.System.now().toEpochMilliseconds()
        }
    }
    
    suspend fun removeDevice(deviceId: Uuid) {
        mutex.withLock {
            connections[deviceId]?.forEach { connection ->
                connection.channel.close()
            }
            connections.remove(deviceId)
        }
    }
    
    private suspend fun cleanupIdleConnections() {
        mutex.withLock {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val connectionsToRemove = mutableListOf<Pair<Uuid, PooledConnection>>()
            
            connections.forEach { (deviceId, deviceConnections) ->
                deviceConnections.forEach { connection ->
                    if (!connection.isInUse && 
                        (currentTime - connection.lastUsedAt) > config.idleTimeoutMs) {
                        connectionsToRemove.add(deviceId to connection)
                    }
                }
            }
            
            connectionsToRemove.forEach { (deviceId, connection) ->
                connection.channel.close()
                connections[deviceId]?.remove(connection)
                if (connections[deviceId]?.isEmpty() == true) {
                    connections.remove(deviceId)
                }
            }
        }
    }
    
    private fun getTotalConnections(): Int {
        return connections.values.sumOf { it.size }
    }
    
    suspend fun getStats(): ConnectionPoolStats {
        return mutex.withLock {
            val totalConnections = getTotalConnections()
            val activeConnections = connections.values.sumOf { deviceConnections ->
                deviceConnections.count { it.isInUse }
            }
            
            ConnectionPoolStats(
                totalConnections = totalConnections,
                activeConnections = activeConnections,
                idleConnections = totalConnections - activeConnections,
                devicesConnected = connections.size
            )
        }
    }
    
    fun cleanup() {
        cleanupJob.cancel()
        runBlocking {
            mutex.withLock {
                connections.values.flatten().forEach { connection ->
                    connection.channel.close()
                }
                connections.clear()
            }
        }
    }
}

data class ConnectionPoolStats(
    val totalConnections: Int,
    val activeConnections: Int,
    val idleConnections: Int,
    val devicesConnected: Int
)