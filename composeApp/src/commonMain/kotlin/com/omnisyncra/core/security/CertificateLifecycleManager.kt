package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Certificate lifecycle events
 */
enum class CertificateLifecycleEvent {
    CREATED,
    RENEWED,
    EXPIRING_SOON,
    EXPIRED,
    REVOKED,
    COMPROMISED,
    VALIDATION_FAILED,
    CLEANUP_PERFORMED
}

/**
 * Certificate renewal configuration
 */
data class CertificateRenewalConfig(
    val renewalThreshold: Long = 7 * 24 * 60 * 60 * 1000L, // 7 days before expiry
    val maxRetryAttempts: Int = 3,
    val retryInterval: Long = 60 * 60 * 1000L, // 1 hour
    val autoRenewalEnabled: Boolean = true,
    val notificationEnabled: Boolean = true
)

/**
 * Certificate renewal attempt
 */
data class CertificateRenewalAttempt(
    val attemptId: Uuid,
    val certificateId: String,
    val deviceId: Uuid,
    val attemptTime: Long,
    val success: Boolean,
    val error: String? = null,
    val newCertificate: DeviceCertificate? = null
)

/**
 * Certificate revocation entry
 */
data class CertificateRevocation(
    val revocationId: Uuid,
    val certificateId: String,
    val deviceId: Uuid,
    val revokedAt: Long,
    val reason: String,
    val revokedBy: Uuid,
    val distributionStatus: RevocationDistributionStatus = RevocationDistributionStatus.PENDING
)

/**
 * Revocation distribution status
 */
enum class RevocationDistributionStatus {
    PENDING,
    DISTRIBUTING,
    DISTRIBUTED,
    FAILED
}

/**
 * Certificate lifecycle manager interface
 */
interface CertificateLifecycleManager {
    suspend fun startAutomaticRenewal(): Boolean
    suspend fun stopAutomaticRenewal(): Boolean
    suspend fun checkCertificateExpiry(): List<DeviceCertificate>
    suspend fun renewCertificate(deviceId: Uuid): Result<DeviceCertificate>
    suspend fun revokeCertificate(deviceId: Uuid, reason: String): Result<CertificateRevocation>
    suspend fun distributeCertificateRevocation(revocation: CertificateRevocation): Boolean
    suspend fun handleCertificateCompromise(deviceId: Uuid, evidence: String): Result<CertificateRevocation>
    suspend fun cleanupExpiredCertificates(): Int
    suspend fun getRenewalHistory(deviceId: Uuid): List<CertificateRenewalAttempt>
    suspend fun getRevocationList(): List<CertificateRevocation>
}

/**
 * Implementation of certificate lifecycle manager
 */
class OmnisyncraCertificateLifecycleManager(
    private val deviceId: Uuid,
    private val certificateManager: CertificateManager,
    private val trustManager: TrustManager,
    private val securityLogger: SecurityEventLogger,
    private val renewalConfig: CertificateRenewalConfig = CertificateRenewalConfig()
) : CertificateLifecycleManager {
    
    private val _renewalAttempts = MutableStateFlow<List<CertificateRenewalAttempt>>(emptyList())
    val renewalAttempts: StateFlow<List<CertificateRenewalAttempt>> = _renewalAttempts.asStateFlow()
    
    private val _revocations = MutableStateFlow<List<CertificateRevocation>>(emptyList())
    val revocations: StateFlow<List<CertificateRevocation>> = _revocations.asStateFlow()
    
    private val _isAutoRenewalActive = MutableStateFlow(false)
    val isAutoRenewalActive: StateFlow<Boolean> = _isAutoRenewalActive.asStateFlow()
    
    private val renewalAttemptHistory = mutableMapOf<Uuid, MutableList<CertificateRenewalAttempt>>()
    private val revocationList = mutableMapOf<Uuid, CertificateRevocation>()
    
    override suspend fun startAutomaticRenewal(): Boolean {
        return try {
            if (_isAutoRenewalActive.value) {
                return true // Already active
            }
            
            _isAutoRenewalActive.value = true
            
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
                severity = SecurityEventSeverity.INFO,
                message = "Automatic certificate renewal started",
                details = mapOf(
                    "renewal_threshold" to renewalConfig.renewalThreshold.toString(),
                    "auto_renewal_enabled" to renewalConfig.autoRenewalEnabled.toString()
                )
            )
            
            // Start monitoring certificates for expiry
            if (renewalConfig.autoRenewalEnabled) {
                monitorCertificateExpiry()
            }
            
            true
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to start automatic certificate renewal",
                error = e
            )
            false
        }
    }
    
    override suspend fun stopAutomaticRenewal(): Boolean {
        return try {
            _isAutoRenewalActive.value = false
            
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
                severity = SecurityEventSeverity.INFO,
                message = "Automatic certificate renewal stopped"
            )
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun checkCertificateExpiry(): List<DeviceCertificate> {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val expiringCertificates = mutableListOf<DeviceCertificate>()
        
        try {
            // Get all trusted devices
            val trustedDevices = trustManager.getTrustedDevices()
            
            for (trustedDeviceId in trustedDevices) {
                val certificate = certificateManager.getCertificate(trustedDeviceId)
                if (certificate != null) {
                    val timeUntilExpiry = certificate.validUntil - currentTime
                    
                    if (timeUntilExpiry <= renewalConfig.renewalThreshold) {
                        expiringCertificates.add(certificate)
                        
                        // Log expiring certificate
                        securityLogger.logEvent(
                            type = SecurityEventType.CERTIFICATE_EXPIRING_SOON,
                            severity = if (timeUntilExpiry <= 0) SecurityEventSeverity.CRITICAL else SecurityEventSeverity.WARNING,
                            message = if (timeUntilExpiry <= 0) "Certificate expired" else "Certificate expiring soon",
                            relatedDeviceId = trustedDeviceId,
                            details = mapOf(
                                "expires_at" to certificate.validUntil.toString(),
                                "time_until_expiry" to timeUntilExpiry.toString()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Failed to check certificate expiry",
                error = e
            )
        }
        
        return expiringCertificates
    }
    
    override suspend fun renewCertificate(deviceId: Uuid): Result<DeviceCertificate> {
        val attemptId = com.benasher44.uuid.uuid4()
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        return try {
            // Check if device is trusted
            if (!trustManager.isTrusted(deviceId)) {
                val attempt = CertificateRenewalAttempt(
                    attemptId = attemptId,
                    certificateId = "unknown",
                    deviceId = deviceId,
                    attemptTime = currentTime,
                    success = false,
                    error = "Device not trusted"
                )
                recordRenewalAttempt(attempt)
                
                return Result.failure(SecurityException("Device $deviceId is not trusted"))
            }
            
            // Get current certificate
            val currentCertificate = certificateManager.getCertificate(deviceId)
            val certificateId = currentCertificate?.deviceId ?: deviceId.toString()
            
            // Generate new certificate
            val newCertificate = certificateManager.generateCertificate(deviceId)
                ?: throw IllegalStateException("Failed to generate new certificate")
            
            // Store the new certificate
            val storeResult = certificateManager.storeCertificate(deviceId, newCertificate)
            if (!storeResult) {
                throw IllegalStateException("Failed to store new certificate")
            }
            
            // Record successful renewal attempt
            val successfulAttempt = CertificateRenewalAttempt(
                attemptId = attemptId,
                certificateId = certificateId,
                deviceId = deviceId,
                attemptTime = currentTime,
                success = true,
                newCertificate = newCertificate
            )
            recordRenewalAttempt(successfulAttempt)
            
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_RENEWED,
                severity = SecurityEventSeverity.INFO,
                message = "Certificate renewed successfully",
                relatedDeviceId = deviceId,
                details = mapOf(
                    "old_expires_at" to (currentCertificate?.validUntil?.toString() ?: "unknown"),
                    "new_expires_at" to newCertificate.validUntil.toString(),
                    "attempt_id" to attemptId.toString()
                )
            )
            
            Result.success(newCertificate)
            
        } catch (e: Exception) {
            val failedAttempt = CertificateRenewalAttempt(
                attemptId = attemptId,
                certificateId = deviceId.toString(),
                deviceId = deviceId,
                attemptTime = currentTime,
                success = false,
                error = e.message
            )
            recordRenewalAttempt(failedAttempt)
            
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Certificate renewal failed",
                relatedDeviceId = deviceId,
                error = e,
                details = mapOf("attempt_id" to attemptId.toString())
            )
            
            Result.failure(e)
        }
    }
    
    override suspend fun revokeCertificate(deviceId: Uuid, reason: String): Result<CertificateRevocation> {
        return try {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val revocationId = com.benasher44.uuid.uuid4()
            
            // Get certificate to revoke
            val certificate = certificateManager.getCertificate(deviceId)
                ?: return Result.failure(IllegalArgumentException("Certificate not found for device $deviceId"))
            
            // Create revocation entry
            val revocation = CertificateRevocation(
                revocationId = revocationId,
                certificateId = certificate.deviceId,
                deviceId = deviceId,
                revokedAt = currentTime,
                reason = reason,
                revokedBy = this.deviceId,
                distributionStatus = RevocationDistributionStatus.PENDING
            )
            
            // Store revocation
            revocationList[deviceId] = revocation
            updateRevocationsFlow()
            
            // Revoke trust
            trustManager.revokeTrust(deviceId)
            
            // Remove certificate from storage
            certificateManager.revokeCertificate(deviceId)
            
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_REVOKED,
                severity = SecurityEventSeverity.WARNING,
                message = "Certificate revoked",
                relatedDeviceId = deviceId,
                details = mapOf(
                    "revocation_id" to revocationId.toString(),
                    "reason" to reason,
                    "revoked_by" to this.deviceId.toString()
                )
            )
            
            // Start distribution process
            distributeCertificateRevocation(revocation)
            
            Result.success(revocation)
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Certificate revocation failed",
                relatedDeviceId = deviceId,
                error = e
            )
            Result.failure(e)
        }
    }
    
    override suspend fun distributeCertificateRevocation(revocation: CertificateRevocation): Boolean {
        return try {
            // Update distribution status
            val updatedRevocation = revocation.copy(distributionStatus = RevocationDistributionStatus.DISTRIBUTING)
            revocationList[revocation.deviceId] = updatedRevocation
            updateRevocationsFlow()
            
            // In a real implementation, this would distribute to all connected devices
            // For now, we'll simulate successful distribution
            val distributedRevocation = updatedRevocation.copy(distributionStatus = RevocationDistributionStatus.DISTRIBUTED)
            revocationList[revocation.deviceId] = distributedRevocation
            updateRevocationsFlow()
            
            securityLogger.logEvent(
                type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED, // Reusing for system events
                severity = SecurityEventSeverity.INFO,
                message = "Certificate revocation distributed",
                relatedDeviceId = revocation.deviceId,
                details = mapOf(
                    "revocation_id" to revocation.revocationId.toString(),
                    "distribution_status" to "DISTRIBUTED"
                )
            )
            
            true
        } catch (e: Exception) {
            // Update distribution status to failed
            val failedRevocation = revocation.copy(distributionStatus = RevocationDistributionStatus.FAILED)
            revocationList[revocation.deviceId] = failedRevocation
            updateRevocationsFlow()
            
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Certificate revocation distribution failed",
                relatedDeviceId = revocation.deviceId,
                error = e
            )
            false
        }
    }
    
    override suspend fun handleCertificateCompromise(deviceId: Uuid, evidence: String): Result<CertificateRevocation> {
        securityLogger.logEvent(
            type = SecurityEventType.SYSTEM_COMPROMISE_DETECTED,
            severity = SecurityEventSeverity.CRITICAL,
            message = "Certificate compromise detected",
            relatedDeviceId = deviceId,
            details = mapOf(
                "evidence" to evidence,
                "detected_by" to this.deviceId.toString()
            )
        )
        
        // Immediately revoke the compromised certificate
        return revokeCertificate(deviceId, "Certificate compromise detected: $evidence")
    }
    
    override suspend fun cleanupExpiredCertificates(): Int {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        var cleanedCount = 0
        
        try {
            val trustedDevices = trustManager.getTrustedDevices()
            
            for (trustedDeviceId in trustedDevices) {
                val certificate = certificateManager.getCertificate(trustedDeviceId)
                if (certificate != null && certificate.validUntil < currentTime) {
                    // Certificate is expired, remove it
                    certificateManager.revokeCertificate(trustedDeviceId)
                    trustManager.revokeTrust(trustedDeviceId)
                    cleanedCount++
                    
                    securityLogger.logEvent(
                        type = SecurityEventType.CERTIFICATE_EXPIRED,
                        severity = SecurityEventSeverity.WARNING,
                        message = "Expired certificate cleaned up",
                        relatedDeviceId = trustedDeviceId,
                        details = mapOf(
                            "expired_at" to certificate.validUntil.toString(),
                            "cleanup_time" to currentTime.toString()
                        )
                    )
                }
            }
            
            if (cleanedCount > 0) {
                securityLogger.logEvent(
                    type = SecurityEventType.CLEANUP_PERFORMED,
                    severity = SecurityEventSeverity.INFO,
                    message = "Certificate cleanup completed",
                    details = mapOf("cleaned_count" to cleanedCount.toString())
                )
            }
            
        } catch (e: Exception) {
            securityLogger.logEvent(
                type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                severity = SecurityEventSeverity.ERROR,
                message = "Certificate cleanup failed",
                error = e
            )
        }
        
        return cleanedCount
    }
    
    override suspend fun getRenewalHistory(deviceId: Uuid): List<CertificateRenewalAttempt> {
        return renewalAttemptHistory[deviceId] ?: emptyList()
    }
    
    override suspend fun getRevocationList(): List<CertificateRevocation> {
        return revocationList.values.toList()
    }
    
    private suspend fun monitorCertificateExpiry() {
        // This would typically run in a background coroutine
        // For now, we'll just check once
        val expiringCertificates = checkCertificateExpiry()
        
        for (certificate in expiringCertificates) {
            val deviceId = Uuid.fromString(certificate.deviceId)
            
            // Attempt automatic renewal if enabled
            if (renewalConfig.autoRenewalEnabled) {
                val renewalResult = renewCertificate(deviceId)
                
                if (renewalResult.isFailure && renewalConfig.notificationEnabled) {
                    // In a real implementation, this would send notifications
                    securityLogger.logEvent(
                        type = SecurityEventType.CERTIFICATE_VALIDATION_FAILED,
                        severity = SecurityEventSeverity.ERROR,
                        message = "Automatic certificate renewal failed - manual intervention required",
                        relatedDeviceId = deviceId
                    )
                }
            }
        }
    }
    
    private fun recordRenewalAttempt(attempt: CertificateRenewalAttempt) {
        val deviceHistory = renewalAttemptHistory.getOrPut(attempt.deviceId) { mutableListOf() }
        deviceHistory.add(attempt)
        
        // Keep only last 10 attempts per device
        if (deviceHistory.size > 10) {
            deviceHistory.removeAt(0)
        }
        
        // Update flow
        val allAttempts = renewalAttemptHistory.values.flatten()
        _renewalAttempts.value = allAttempts.sortedByDescending { it.attemptTime }
    }
    
    private fun updateRevocationsFlow() {
        _revocations.value = revocationList.values.toList().sortedByDescending { it.revokedAt }
    }
    
    // Retry logic for failed renewals
    suspend fun retryFailedRenewals(): Int {
        val failedAttempts = _renewalAttempts.value.filter { 
            !it.success && 
            (Clock.System.now().toEpochMilliseconds() - it.attemptTime) >= renewalConfig.retryInterval 
        }
        
        var retriedCount = 0
        
        for (failedAttempt in failedAttempts) {
            // Check if we haven't exceeded max retry attempts
            val deviceAttempts = getRenewalHistory(failedAttempt.deviceId)
            val recentFailures = deviceAttempts.count { 
                !it.success && 
                (Clock.System.now().toEpochMilliseconds() - it.attemptTime) <= (renewalConfig.retryInterval * renewalConfig.maxRetryAttempts)
            }
            
            if (recentFailures < renewalConfig.maxRetryAttempts) {
                val retryResult = renewCertificate(failedAttempt.deviceId)
                if (retryResult.isSuccess) {
                    retriedCount++
                }
            }
        }
        
        return retriedCount
    }
}