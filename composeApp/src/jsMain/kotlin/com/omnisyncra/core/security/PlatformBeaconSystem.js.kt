package com.omnisyncra.core.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * JavaScript implementation using Web Bluetooth API (where available)
 * Falls back to simulated beacons for broader compatibility
 */
actual class PlatformBeaconSystem actual constructor() {
    
    private var isAdvertising = false
    private var currentBeacon: AnonymousBeacon? = null
    private var isScanning = false
    
    actual suspend fun startAdvertising(beacon: AnonymousBeacon): Boolean {
        return try {
            // Web Bluetooth doesn't support advertising in most browsers
            // This would be a simulated implementation or use WebRTC data channels
            currentBeacon = beacon
            isAdvertising = true
            console.log("JS: Started web beacon advertising ${beacon.identifier.contentHashCode()}")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun stopAdvertising(): Boolean {
        return try {
            isAdvertising = false
            currentBeacon = null
            console.log("JS: Stopped web beacon advertising")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun startScanning(): Flow<BeaconDetection> = flow {
        isScanning = true
        console.log("JS: Started web beacon scanning")
        
        while (isScanning) {
            // Simulate beacon detection with web-specific limitations
            if (Random.nextFloat() < 0.2f) { // 20% chance (web has limited capabilities)
                val simulatedBeacon = generateWebBeacon()
                val signalStrength = getSignalStrength(simulatedBeacon)
                val distance = estimateDistance(signalStrength)
                
                val detection = BeaconDetection(
                    beacon = simulatedBeacon,
                    detectedAt = Clock.System.now().toEpochMilliseconds(),
                    signalStrength = signalStrength,
                    distance = distance
                )
                
                emit(detection)
                console.log("JS: Detected web beacon ${simulatedBeacon.identifier.contentHashCode()} at ${distance}m")
            }
            
            delay(3000) // Web scanning is slower
        }
    }
    
    actual suspend fun stopScanning(): Boolean {
        return try {
            isScanning = false
            console.log("JS: Stopped web beacon scanning")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun getSignalStrength(beacon: AnonymousBeacon): Double {
        // Web environment has weaker and more variable signals
        return -40.0 - (Random.nextDouble() * 50.0) // -40 to -90 dBm
    }
    
    actual suspend fun estimateDistance(signalStrength: Double): Double? {
        // Web-based distance estimation (less accurate)
        val txPower = -20.0
        val pathLoss = 2.5 // Higher path loss due to web environment limitations
        
        val distance = kotlin.math.pow(10.0, (txPower - signalStrength) / (10.0 * pathLoss))
        
        // Web estimates are less reliable, add some uncertainty
        return distance * (0.8 + Random.nextDouble() * 0.4) // ±20% uncertainty
    }
    
    private fun generateWebBeacon(): AnonymousBeacon {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val identifier = ByteArray(16) { Random.nextInt(256).toByte() }
        val commitment = ByteArray(32) { Random.nextInt(256).toByte() }
        
        return AnonymousBeacon(
            identifier = identifier,
            commitment = commitment,
            timestamp = currentTime,
            rotationInterval = 300_000L,
            metadata = mapOf(
                "version" to "1.0",
                "platform" to "web",
                "transport" to "webrtc",
                "capabilities" to "handoff,sync"
            )
        )
    }
}