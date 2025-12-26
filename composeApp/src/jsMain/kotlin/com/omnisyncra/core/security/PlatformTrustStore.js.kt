package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * JavaScript implementation using localStorage (simplified for demo)
 */
actual class PlatformTrustStore actual constructor() : TrustStore {
    
    private val storagePrefix = "omnisyncra_trust_"
    
    actual override suspend fun storeTrustRelationship(relationship: TrustRelationship): Boolean {
        return try {
            val serializable = SerializableTrustRelationship.fromTrustRelationship(relationship)
            val json = Json.encodeToString(serializable)
            
            window.localStorage.setItem("$storagePrefix${relationship.deviceId}", json)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    actual override suspend fun getTrustRelationship(deviceId: Uuid): TrustRelationship? {
        return try {
            val json = window.localStorage.getItem("$storagePrefix$deviceId") ?: return null
            val serializable = Json.decodeFromString<SerializableTrustRelationship>(json)
            serializable.toTrustRelationship()
        } catch (e: Exception) {
            null
        }
    }
    
    actual override suspend fun getAllTrustedDevices(): List<TrustRelationship> {
        return try {
            val relationships = mutableListOf<TrustRelationship>()
            
            for (i in 0 until window.localStorage.length) {
                val key = window.localStorage.key(i)
                if (key != null && key.startsWith(storagePrefix)) {
                    val json = window.localStorage.getItem(key)
                    if (json != null) {
                        try {
                            val serializable = Json.decodeFromString<SerializableTrustRelationship>(json)
                            relationships.add(serializable.toTrustRelationship())
                        } catch (e: Exception) {
                            // Skip invalid entries
                        }
                    }
                }
            }
            
            relationships
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    actual override suspend fun updateTrustLevel(deviceId: Uuid, level: TrustLevel): Boolean {
        return try {
            val existing = getTrustRelationship(deviceId) ?: return false
            val updated = existing.copy(
                level = level,
                lastUpdated = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
            storeTrustRelationship(updated)
        } catch (e: Exception) {
            false
        }
    }
    
    actual override suspend fun revokeTrust(deviceId: Uuid): Boolean {
        return updateTrustLevel(deviceId, TrustLevel.REVOKED)
    }
    
    actual override suspend fun deleteTrustRelationship(deviceId: Uuid): Boolean {
        return try {
            window.localStorage.removeItem("$storagePrefix$deviceId")
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Serializable version of TrustRelationship for JavaScript storage
 */
@Serializable
private data class SerializableTrustRelationship(
    val deviceId: String,
    val level: String,
    val establishedAt: Long,
    val lastUpdated: Long,
    val certificate: SerializableCertificate?,
    val metadata: Map<String, String>
) {
    companion object {
        fun fromTrustRelationship(trust: TrustRelationship): SerializableTrustRelationship {
            return SerializableTrustRelationship(
                deviceId = trust.deviceId.toString(),
                level = trust.level.name,
                establishedAt = trust.establishedAt,
                lastUpdated = trust.lastUpdated,
                certificate = trust.certificate?.let { TrustSerializableCertificate.fromDeviceCertificate(it) },
                metadata = trust.metadata
            )
        }
    }
    
    fun toTrustRelationship(): TrustRelationship {
        return TrustRelationship(
            deviceId = com.benasher44.uuid.uuid4(), // Simplified for JS demo
            level = TrustLevel.valueOf(level),
            establishedAt = establishedAt,
            lastUpdated = lastUpdated,
            certificate = certificate?.toDeviceCertificate(),
            metadata = metadata
        )
    }
}

@Serializable
private data class TrustSerializableCertificate(
    val deviceId: String,
    val publicKey: ByteArray,
    val issuer: String,
    val subject: String,
    val validFrom: Long,
    val validUntil: Long,
    val signature: ByteArray,
    val serialNumber: String,
    val version: Int,
    val keyUsage: List<String>,
    val extensions: Map<String, ByteArray>
) {
    companion object {
        fun fromDeviceCertificate(cert: DeviceCertificate): TrustSerializableCertificate {
            return TrustSerializableCertificate(
                deviceId = cert.deviceId.toString(),
                publicKey = cert.publicKey,
                issuer = cert.issuer,
                subject = cert.subject,
                validFrom = cert.validFrom,
                validUntil = cert.validUntil,
                signature = cert.signature,
                serialNumber = cert.serialNumber,
                version = cert.version,
                keyUsage = cert.keyUsage.map { it.name },
                extensions = cert.extensions
            )
        }
    }
    
    fun toDeviceCertificate(): DeviceCertificate {
        return DeviceCertificate(
            deviceId = com.benasher44.uuid.uuid4(), // Simplified for JS demo
            publicKey = publicKey,
            issuer = issuer,
            subject = subject,
            validFrom = validFrom,
            validUntil = validUntil,
            signature = signature,
            serialNumber = serialNumber,
            version = version,
            keyUsage = keyUsage.mapNotNull { 
                try { KeyUsage.valueOf(it) } catch (e: Exception) { null }
            }.toSet(),
            extensions = extensions
        )
    }
}