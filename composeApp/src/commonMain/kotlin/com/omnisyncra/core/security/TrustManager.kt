package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Trust relationship between devices
 */
data class TrustRelationship(
    val deviceId: Uuid,
    val level: TrustLevel,
    val establishedAt: Long,
    val lastUpdated: Long,
    val certificate: DeviceCertificate?,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Trust store for managing device trust relationships
 */
interface TrustStore {
    suspend fun storeTrustRelationship(relationship: TrustRelationship): Boolean
    suspend fun getTrustRelationship(deviceId: Uuid): TrustRelationship?
    suspend fun getAllTrustedDevices(): List<TrustRelationship>
    suspend fun updateTrustLevel(deviceId: Uuid, level: TrustLevel): Boolean
    suspend fun revokeTrust(deviceId: Uuid): Boolean
    suspend fun deleteTrustRelationship(deviceId: Uuid): Boolean
}

/**
 * Platform-specific secure storage for trust relationships
 */
expect class PlatformTrustStore() : TrustStore

/**
 * Trust management system for handling device trust relationships
 */
class TrustManager(
    private val trustStore: TrustStore = PlatformTrustStore(),
    private val certificateManager: CertificateManager
) {
    
    private val _trustUpdates = MutableStateFlow<TrustRelationship?>(null)
    val trustUpdates: StateFlow<TrustRelationship?> = _trustUpdates.asStateFlow()
    
    /**
     * Establish trust with a device using its certificate
     */
    suspend fun establishTrust(
        deviceId: Uuid,
        certificate: DeviceCertificate,
        metadata: Map<String, String> = emptyMap()
    ): Result<TrustRelationship> {
        return try {
            // Verify the certificate is valid
            val validationResult = certificateManager.validateCertificate(certificate)
            if (validationResult !is CertificateValidationResult.Valid) {
                return Result.failure(IllegalArgumentException("Invalid certificate for device $deviceId: $validationResult"))
            }
            
            val now = Clock.System.now().toEpochMilliseconds()
            val relationship = TrustRelationship(
                deviceId = deviceId,
                level = TrustLevel.PENDING,
                establishedAt = now,
                lastUpdated = now,
                certificate = certificate,
                metadata = metadata
            )
            
            val stored = trustStore.storeTrustRelationship(relationship)
            if (stored) {
                _trustUpdates.value = relationship
                Result.success(relationship)
            } else {
                Result.failure(IllegalStateException("Failed to store trust relationship"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Confirm trust for a pending device
     */
    suspend fun confirmTrust(deviceId: Uuid): Result<TrustRelationship> {
        return try {
            val existing = trustStore.getTrustRelationship(deviceId)
                ?: return Result.failure(IllegalArgumentException("No trust relationship found for device $deviceId"))
            
            if (existing.level != TrustLevel.PENDING) {
                return Result.failure(IllegalStateException("Device $deviceId is not in pending state"))
            }
            
            val updated = trustStore.updateTrustLevel(deviceId, TrustLevel.TRUSTED)
            if (updated) {
                val relationship = existing.copy(
                    level = TrustLevel.TRUSTED,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                _trustUpdates.value = relationship
                Result.success(relationship)
            } else {
                Result.failure(IllegalStateException("Failed to update trust level"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Revoke trust for a device
     */
    suspend fun revokeTrust(deviceId: Uuid, reason: String = ""): Result<TrustRelationship> {
        return try {
            val existing = trustStore.getTrustRelationship(deviceId)
                ?: return Result.failure(IllegalArgumentException("No trust relationship found for device $deviceId"))
            
            val updated = trustStore.updateTrustLevel(deviceId, TrustLevel.REVOKED)
            if (updated) {
                val relationship = existing.copy(
                    level = TrustLevel.REVOKED,
                    lastUpdated = Clock.System.now().toEpochMilliseconds(),
                    metadata = existing.metadata + ("revocation_reason" to reason)
                )
                _trustUpdates.value = relationship
                Result.success(relationship)
            } else {
                Result.failure(IllegalStateException("Failed to revoke trust"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get trust level for a device
     */
    suspend fun getTrustLevel(deviceId: Uuid): TrustLevel {
        return try {
            trustStore.getTrustRelationship(deviceId)?.level ?: TrustLevel.UNKNOWN
        } catch (e: Exception) {
            TrustLevel.UNKNOWN
        }
    }
    
    /**
     * Check if a device is trusted
     */
    suspend fun isTrusted(deviceId: Uuid): Boolean {
        return getTrustLevel(deviceId) == TrustLevel.TRUSTED
    }
    
    /**
     * Get all trusted devices
     */
    suspend fun getTrustedDevices(): List<TrustRelationship> {
        return try {
            trustStore.getAllTrustedDevices().filter { it.level == TrustLevel.TRUSTED }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get trust relationship details
     */
    suspend fun getTrustRelationship(deviceId: Uuid): TrustRelationship? {
        return try {
            trustStore.getTrustRelationship(deviceId)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Clean up expired or invalid trust relationships
     */
    suspend fun cleanupTrustRelationships(): Int {
        var cleaned = 0
        try {
            val allRelationships = trustStore.getAllTrustedDevices()
            val now = Clock.System.now().toEpochMilliseconds()
            
            for (relationship in allRelationships) {
                val certificate = relationship.certificate
                
                // Remove relationships with expired certificates
                if (certificate != null && certificate.validUntil < now) {
                    trustStore.deleteTrustRelationship(relationship.deviceId)
                    cleaned++
                }
                
                // Remove very old pending relationships (older than 24 hours)
                if (relationship.level == TrustLevel.PENDING && 
                    (now - relationship.establishedAt) > 24 * 60 * 60 * 1000) {
                    trustStore.deleteTrustRelationship(relationship.deviceId)
                    cleaned++
                }
            }
        } catch (e: Exception) {
            // Log error but don't fail
        }
        
        return cleaned
    }
}