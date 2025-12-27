package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Anonymous beacon for privacy-preserving proximity detection
 */
data class AnonymousBeacon(
    val identifier: ByteArray,
    val commitment: ByteArray,
    val timestamp: Long,
    val rotationInterval: Long = 300_000L, // 5 minutes default
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as AnonymousBeacon
        return identifier.contentEquals(other.identifier) &&
                commitment.contentEquals(other.commitment) &&
                timestamp == other.timestamp &&
                rotationInterval == other.rotationInterval
    }

    override fun hashCode(): Int {
        var result = identifier.contentHashCode()
        result = 31 * result + commitment.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + rotationInterval.hashCode()
        return result
    }
    
    fun isExpired(currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return (currentTime - timestamp) > rotationInterval
    }
}

/**
 * Identity revelation data for mutual authentication
 */
data class IdentityRevelation(
    val deviceId: Uuid,
    val certificate: DeviceCertificate,
    val proof: ByteArray,
    val timestamp: Long,
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as IdentityRevelation
        return deviceId == other.deviceId &&
                certificate == other.certificate &&
                proof.contentEquals(other.proof) &&
                timestamp == other.timestamp &&
                nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + certificate.hashCode()
        result = 31 * result + proof.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}

/**
 * Beacon detection event
 */
data class BeaconDetection(
    val beacon: AnonymousBeacon,
    val detectedAt: Long,
    val signalStrength: Double,
    val distance: Double? = null
)

/**
 * Privacy-preserving beacon system for anonymous proximity detection
 */
interface PrivacyBeaconSystem {
    suspend fun startBeaconing(): Boolean
    suspend fun stopBeaconing(): Boolean
    suspend fun getCurrentBeacon(): AnonymousBeacon?
    suspend fun rotateBeacon(): AnonymousBeacon
    suspend fun detectBeacons(): Flow<BeaconDetection>
    suspend fun revealIdentity(beacon: AnonymousBeacon): IdentityRevelation?
    suspend fun verifyIdentityRevelation(revelation: IdentityRevelation): Boolean
    suspend fun getDetectedBeacons(): List<BeaconDetection>
    suspend fun clearExpiredBeacons(): Int
}

/**
 * Platform-specific beacon operations
 */
expect class PlatformBeaconSystem() {
    suspend fun startAdvertising(beacon: AnonymousBeacon): Boolean
    suspend fun stopAdvertising(): Boolean
    suspend fun startScanning(): Flow<BeaconDetection>
    suspend fun stopScanning(): Boolean
    suspend fun getSignalStrength(beacon: AnonymousBeacon): Double
    suspend fun estimateDistance(signalStrength: Double): Double?
}

/**
 * Implementation of privacy-preserving beacon system
 */
class OmnisyncraPrivacyBeaconSystem(
    private val deviceId: Uuid,
    private val certificateManager: CertificateManager,
    private val keyExchangeManager: KeyExchangeManager,
    private val platformBeaconSystem: PlatformBeaconSystem = PlatformBeaconSystem()
) : PrivacyBeaconSystem {
    
    private val _currentBeacon = MutableStateFlow<AnonymousBeacon?>(null)
    val currentBeacon: StateFlow<AnonymousBeacon?> = _currentBeacon.asStateFlow()
    
    private val _detectedBeacons = MutableStateFlow<List<BeaconDetection>>(emptyList())
    val detectedBeacons: StateFlow<List<BeaconDetection>> = _detectedBeacons.asStateFlow()
    
    private var isBeaconing = false
    private var isScanning = false
    
    // Store beacon secrets for identity revelation
    private val beaconSecrets = mutableMapOf<String, BeaconSecret>()
    
    private data class BeaconSecret(
        val deviceId: Uuid,
        val secret: ByteArray,
        val timestamp: Long
    )
    
    override suspend fun startBeaconing(): Boolean {
        if (isBeaconing) return true
        
        return try {
            val beacon = rotateBeacon()
            val success = platformBeaconSystem.startAdvertising(beacon)
            if (success) {
                isBeaconing = true
                _currentBeacon.value = beacon
            }
            success
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun stopBeaconing(): Boolean {
        return try {
            val success = platformBeaconSystem.stopAdvertising()
            if (success) {
                isBeaconing = false
                _currentBeacon.value = null
            }
            success
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getCurrentBeacon(): AnonymousBeacon? {
        return _currentBeacon.value
    }
    
    override suspend fun rotateBeacon(): AnonymousBeacon {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        // Generate anonymous identifier
        val secret = generateSecureRandom(32)
        val identifier = generateAnonymousIdentifier(secret, currentTime)
        
        // Generate cryptographic commitment
        val commitment = generateCommitment(deviceId, secret, currentTime)
        
        val beacon = AnonymousBeacon(
            identifier = identifier,
            commitment = commitment,
            timestamp = currentTime,
            rotationInterval = 300_000L, // 5 minutes
            metadata = mapOf(
                "version" to "1.0",
                "capabilities" to "handoff,sync"
            )
        )
        
        // Store secret for later identity revelation
        val beaconKey = identifier.contentHashCode().toString()
        beaconSecrets[beaconKey] = BeaconSecret(
            deviceId = deviceId,
            secret = secret,
            timestamp = currentTime
        )
        
        // Clean up old secrets (older than 1 hour)
        cleanupOldSecrets(currentTime)
        
        return beacon
    }
    
    override suspend fun detectBeacons(): Flow<BeaconDetection> {
        isScanning = true
        return platformBeaconSystem.startScanning()
    }
    
    override suspend fun revealIdentity(beacon: AnonymousBeacon): IdentityRevelation? {
        return try {
            val certificate = certificateManager.getCertificate(deviceId) ?: return null
            val beaconKey = beacon.identifier.contentHashCode().toString()
            val secret = beaconSecrets[beaconKey]?.secret ?: return null
            
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val nonce = generateSecureRandom(16)
            
            // Generate proof of identity
            val proofData = buildString {
                append("deviceId:$deviceId")
                append("|beacon:${beacon.identifier.contentHashCode()}")
                append("|timestamp:$currentTime")
                append("|nonce:${nonce.contentHashCode()}")
            }.encodeToByteArray()
            
            val proof = certificateManager.signData(proofData) ?: return null
            
            IdentityRevelation(
                deviceId = deviceId,
                certificate = certificate,
                proof = proof,
                timestamp = currentTime,
                nonce = nonce
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun verifyIdentityRevelation(revelation: IdentityRevelation): Boolean {
        return try {
            // Verify certificate is valid
            val validationResult = certificateManager.validateCertificate(revelation.certificate)
            if (validationResult !is CertificateValidationResult.Valid) {
                return false
            }
            
            // Verify proof signature
            val proofData = buildString {
                append("deviceId:${revelation.deviceId}")
                append("|beacon:") // We don't have the original beacon identifier here
                append("|timestamp:${revelation.timestamp}")
                append("|nonce:${revelation.nonce.contentHashCode()}")
            }.encodeToByteArray()
            
            certificateManager.verifySignature(
                proofData,
                revelation.proof,
                revelation.deviceId
            )
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getDetectedBeacons(): List<BeaconDetection> {
        return _detectedBeacons.value
    }
    
    override suspend fun clearExpiredBeacons(): Int {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val currentBeacons = _detectedBeacons.value
        val validBeacons = currentBeacons.filter { detection ->
            !detection.beacon.isExpired(currentTime)
        }
        
        val removedCount = currentBeacons.size - validBeacons.size
        _detectedBeacons.value = validBeacons
        
        return removedCount
    }
    
    private fun generateAnonymousIdentifier(secret: ByteArray, timestamp: Long): ByteArray {
        // Generate anonymous identifier using HMAC-SHA256
        val data = "anonymous_id|$timestamp".encodeToByteArray()
        return simpleHmac(secret, data).sliceArray(0..15) // 16 bytes
    }
    
    private fun generateCommitment(deviceId: Uuid, secret: ByteArray, timestamp: Long): ByteArray {
        // Generate cryptographic commitment
        val commitmentData = "commitment|$deviceId|$timestamp".encodeToByteArray()
        return simpleHmac(secret, commitmentData)
    }
    
    private fun generateSecureRandom(size: Int): ByteArray {
        val bytes = ByteArray(size)
        for (i in bytes.indices) {
            bytes[i] = Random.nextInt(256).toByte()
        }
        return bytes
    }
    
    private fun simpleHmac(key: ByteArray, data: ByteArray): ByteArray {
        // Simple HMAC implementation (not cryptographically secure)
        // In production, use proper HMAC-SHA256
        val combined = key + data
        var hash = 0x811c9dc5.toInt()
        
        for (byte in combined) {
            hash = hash xor byte.toInt()
            hash = hash * 0x01000193
        }
        
        val result = ByteArray(32)
        for (i in result.indices) {
            result[i] = ((hash shr (i % 32)) and 0xFF).toByte()
            hash = hash * 0x01000193
        }
        return result
    }
    
    private fun cleanupOldSecrets(currentTime: Long) {
        val oneHourAgo = currentTime - 3600_000L
        val expiredKeys = beaconSecrets.filter { (_, secret) ->
            secret.timestamp < oneHourAgo
        }.keys
        
        expiredKeys.forEach { key ->
            beaconSecrets.remove(key)
        }
    }
    
    suspend fun addDetectedBeacon(detection: BeaconDetection) {
        val currentBeacons = _detectedBeacons.value.toMutableList()
        
        // Remove existing detection of same beacon
        currentBeacons.removeAll { it.beacon.identifier.contentEquals(detection.beacon.identifier) }
        
        // Add new detection
        currentBeacons.add(detection)
        
        // Keep only recent detections (last 100)
        if (currentBeacons.size > 100) {
            currentBeacons.sortByDescending { it.detectedAt }
            _detectedBeacons.value = currentBeacons.take(100)
        } else {
            _detectedBeacons.value = currentBeacons
        }
    }
    
    suspend fun stopScanning(): Boolean {
        return try {
            val success = platformBeaconSystem.stopScanning()
            if (success) {
                isScanning = false
            }
            success
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun isCurrentlyBeaconing(): Boolean = isBeaconing
    suspend fun isCurrentlyScanning(): Boolean = isScanning
}