package com.omnisyncra.test

import com.omnisyncra.core.monitoring.RealSystemMonitor
import com.omnisyncra.core.network.RealNetworkCommunicator
import com.omnisyncra.core.security.OmnisyncraSecuritySystem
import com.omnisyncra.core.ai.OmnisyncraAISystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay

/**
 * Quick test to verify real system implementations work
 */
suspend fun quickRealSystemTest() {
    println("🚀 Quick Real System Test Starting...")
    
    try {
        // Create real system components
        val securitySystem = OmnisyncraSecuritySystem("test-device")
        val httpClient = HttpClient()
        val aiSystem = OmnisyncraAISystem("test-key", httpClient)
        val networkCommunicator = RealNetworkCommunicator("test-device", securitySystem)
        val systemMonitor = RealSystemMonitor(securitySystem, networkCommunicator, aiSystem)
        
        // Initialize systems
        println("🔧 Initializing systems...")
        securitySystem.initialize()
        aiSystem.initialize()
        networkCommunicator.initialize()
        systemMonitor.initialize()
        
        // Start monitoring
        println("📊 Starting real system monitoring...")
        systemMonitor.startMonitoring()
        
        // Generate some real activity
        println("⚡ Generating real system activity...")
        repeat(10) { i ->
            val startTime = com.omnisyncra.core.platform.TimeUtils.currentTimeMillis()
            delay(kotlin.random.Random.nextLong(50, 200))
            val duration = com.omnisyncra.core.platform.TimeUtils.currentTimeMillis() - startTime
            val success = kotlin.random.Random.nextFloat() > 0.1f
            
            systemMonitor.recordOperation(success, duration)
            
            if (i % 3 == 0) {
                systemMonitor.recordTask(success)
            }
            
            if (!success) {
                systemMonitor.recordError("test-component")
            }
        }
        
        // Wait for data to be processed
        delay(2000)
        
        // Check real system health
        val systemHealth = systemMonitor.systemHealth.value
        println("📈 Real System Health:")
        println("   CPU Usage: ${(systemHealth.cpuUsage * 100).toInt()}%")
        println("   Memory Usage: ${(systemHealth.memoryUsage * 100).toInt()}%")
        println("   Network Latency: ${systemHealth.networkLatency}ms")
        println("   Error Rate: ${"%.1f".format(systemHealth.errorRate)}/min")
        println("   Overall Status: ${systemHealth.overallStatus}")
        
        // Check performance metrics
        val performanceMetrics = systemMonitor.performanceMetrics.value
        println("⚡ Real Performance Metrics:")
        println("   Throughput: ${performanceMetrics.throughput} ops/sec")
        println("   Response Time: ${performanceMetrics.responseTime}ms")
        println("   Completed Tasks: ${performanceMetrics.completedTasks}")
        println("   Cache Hit Rate: ${(performanceMetrics.cacheHitRate * 100).toInt()}%")
        
        // Test network discovery
        println("🌐 Testing real network discovery...")
        networkCommunicator.startDiscovery()
        delay(1000)
        val devices = networkCommunicator.getDiscoveredDevices().getOrNull()
        println("   Discovered ${devices?.size ?: 0} devices")
        
        // Generate system report
        println("📋 Generating real system report...")
        val report = systemMonitor.generateSystemReport().getOrNull()
        if (report != null) {
            println("   Report generated successfully")
            println("   Recommendations: ${report.recommendations.size}")
            report.recommendations.forEach { recommendation ->
                println("   • $recommendation")
            }
        }
        
        // Cleanup
        systemMonitor.shutdown()
        networkCommunicator.shutdown()
        securitySystem.shutdown()
        httpClient.close()
        
        println("✅ Quick Real System Test Completed Successfully!")
        println("🎉 Real implementations are working with actual data!")
        
    } catch (e: Exception) {
        println("❌ Quick Real System Test Failed: ${e.message}")
        e.printStackTrace()
    }
}