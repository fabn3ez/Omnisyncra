package com.omnisyncra.core.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * WASM implementation with simulated beacon operations
 * Limited by WASM sandbox but provides basic functionality
 */
actual class PlatformBeaconSystem actual constructor() {
    
    private var isAdvertising = false
    private var currentBeacon: AnonymousBeacon? = null
    private var isScanning = false
    
    actual suspend fun startAdvertising(beacon: AnonymousBeacon): Boolean {
        return try {
            // WASM has limited system access, simulate advertising
            currentBeacon = beacon
            isAdvertising = true
            println("WASM: Started simulated beacon advertising ${beacon.identifier.contentHashCode()}")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun stopAdvertising(): Boolean {
        return try {
            isAdvertising = false
            currentBeacon = null
            println("WASM: Stopped simulated beacon advertising")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun startScanning(): Flow<BeaconDetection> = flow {
        isScanning = true
        println("WASM: Started simulated beacon scanning")
        
        while (isScanning) {
            // Simulate beacon detection with WASM limitations
            if (Random.nextFloat() < 0.15f) { // 15% chance (WASM has most limitations)
                val simulatedBeacon = generateWasmBeacon()
                val signalStrength = getSignalStrength(simulatedBeacon)
                val distance = estimateDistance(signalStrength)
                
                val detection = BeaconDetection(
                    beacon = simulatedBeacon,
                    detectedAt = Clock.System.now().toEpochMilliseconds(),
                    signalStrength = signalStrength,
                    distance = distance
                )
                
                emit(detection)
                println("WASM: Detected simulated beacon ${simulatedBeacon.identifier.contentHashCode()} at ${distance}m")
            }
            
            delay(4000) // WASM scanning is slowest
        }
    }
    
    actual suspend fun stopScanning(): Boolean {
        return try {
            isScanning = false
            println("WASM: Stopped simulated beacon scanning")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun getSignalStrength(beacon: AnonymousBeacon): Double {
        // WASM simulated signal strength
        return -50.0 - (Random.nextDouble() * 40.0) // -50 to -90 dBm
    }
    
    actual suspend fun estimateDistance(signalStrength: Double): Double? {
        // WASM distance estimation (basic simulation)
        val txPower = -20.0
        val pathLoss = 2.0
        
        val distance = kotlin.math.pow(10.0, (txPower - signalStrength) / (10.0 * pathLoss))
        
        // WASM simulation adds some randomness
        return distance * (0.7 + Random.nextDouble() * 0.6) // ±30% uncertainty
    }
    
    private fun generateWasmBeacon(): AnonymousBeacon {
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
                "platform" to "wasm",
                "transport" to "simulated",
                "capabilities" to "handoff"
            )
        )
    }
}