package com.omnisyncra.core.security

import com.benasher44.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

// Permission types
enum class Permission {
    READ_DEVICE_INFO,
    WRITE_DEVICE_CONFIG,
    SUBMIT_COMPUTE_TASK,
    ACCESS_COMPUTE_RESULTS,
    MODIFY_CRDT_STATE,
    VIEW_AUDIT_LOGS,
    MANAGE_PERMISSIONS,
    ADMIN_ACCESS
}

// Permission scope
data class PermissionScope(
    val resource: String,
    val actions: Set<String> = emptySet()
)

// Permission grant
data class PermissionGrant(
    val id: String = com.benasher44.uuid.uuid4().toString(),
    val deviceId: Uuid,
    val permission: Permission,
    val scope: PermissionScope? = null,
    val grantedBy: Uuid,
    val grantedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val expiresAt: Long? = null,
    val conditions: Map<String, String> = emptyMap()
) {
    fun isExpired(currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return expiresAt != null && currentTime > expiresAt
    }
    
    fun isValid(currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return !isExpired(currentTime)
    }
}

// Permission request
data class PermissionRequest(
    val id: String = com.benasher44.uuid.uuid4().toString(),
    val requestingDevice: Uuid,
    val permission: Permission,
    val scope: PermissionScope? = null,
    val justification: String,
    val requestedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val status: RequestStatus = RequestStatus.PENDING
)

enum class RequestStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED
}

// Permission manager
class PermissionManager(
    private val nodeId: Uuid,
    private val authService: AuthenticationService,
    private val auditService: AuditLogService
) {
    private val grants = mutableMapOf<String, PermissionGrant>()
    private val requests = mutableMapOf<String, PermissionRequest>()
    private val mutex = Mutex()
    
    // Default permissions for trusted devices
    private val defaultPermissions = setOf(
        Permission.READ_DEVICE_INFO,
        Permission.SUBMIT_COMPUTE_TASK,
        Permission.ACCESS_COMPUTE_RESULTS
    )
    
    suspend fun requestPermission(
        deviceId: Uuid,
        permission: Permission,
        scope: PermissionScope? = null,
        justification: String
    ): PermissionRequest {
        val request = PermissionRequest(
            requestingDevice = deviceId,
            permission = permission,
            scope = scope,
            justification = justification
        )
        
        mutex.withLock {
            requests[request.id] = request
        }
        
        // Auto-approve default permissions for trusted devices
        val trustLevel = authService.getTrustLevel(deviceId)
        if (trustLevel == TrustLevel.TRUSTED && permission in defaultPermissions) {
            approvePermissionRequest(request.id, "Auto-approved default permission")
        }
        
        return request
    }
    
    suspend fun approvePermissionRequest(
        requestId: String,
        reason: String = "",
        expiresAt: Long? = null
    ): Boolean {
        val request = mutex.withLock {
            requests[requestId]?.takeIf { it.status == RequestStatus.PENDING }
        } ?: return false
        
        val grant = PermissionGrant(
            deviceId = request.requestingDevice,
            permission = request.permission,
            scope = request.scope,
            grantedBy = nodeId,
            expiresAt = expiresAt
        )
        
        mutex.withLock {
            grants[grant.id] = grant
            requests[requestId] = request.copy(status = RequestStatus.APPROVED)
        }
        
        auditService.logPermission(
            request.requestingDevice,
            request.permission.name,
            granted = true,
            reason = reason
        )
        
        return true
    }
    
    suspend fun denyPermissionRequest(requestId: String, reason: String): Boolean {
        val request = mutex.withLock {
            requests[requestId]?.takeIf { it.status == RequestStatus.PENDING }
        } ?: return false
        
        mutex.withLock {
            requests[requestId] = request.copy(status = RequestStatus.DENIED)
        }
        
        auditService.logPermission(
            request.requestingDevice,
            request.permission.name,
            granted = false,
            reason = reason
        )
        
        return true
    }
    
    suspend fun hasPermission(
        deviceId: Uuid,
        permission: Permission,
        resource: String? = null
    ): Boolean {
        // Check trust level first
        val trustLevel = authService.getTrustLevel(deviceId)
        if (trustLevel == TrustLevel.UNTRUSTED) return false
        
        // Admin devices have all permissions
        if (trustLevel == TrustLevel.VERIFIED && permission != Permission.ADMIN_ACCESS) {
            return true
        }
        
        // Check explicit grants
        val hasGrant = mutex.withLock {
            grants.values.any { grant ->
                grant.deviceId == deviceId &&
                grant.permission == permission &&
                grant.isValid() &&
                (resource == null || grant.scope?.resource == resource)
            }
        }
        
        return hasGrant
    }
    
    suspend fun checkPermission(
        deviceId: Uuid,
        permission: Permission,
        resource: String? = null
    ): Boolean {
        val hasPermission = hasPermission(deviceId, permission, resource)
        
        if (!hasPermission) {
            auditService.logPermission(
                deviceId,
                permission.name,
                granted = false,
                reason = "Permission check failed"
            )
        }
        
        return hasPermission
    }
    
    suspend fun grantPermission(
        deviceId: Uuid,
        permission: Permission,
        scope: PermissionScope? = null,
        expiresAt: Long? = null
    ): PermissionGrant {
        val grant = PermissionGrant(
            deviceId = deviceId,
            permission = permission,
            scope = scope,
            grantedBy = nodeId,
            expiresAt = expiresAt
        )
        
        mutex.withLock {
            grants[grant.id] = grant
        }
        
        auditService.logPermission(
            deviceId,
            permission.name,
            granted = true,
            reason = "Direct grant"
        )
        
        return grant
    }
    
    suspend fun revokePermission(grantId: String): Boolean {
        val grant = mutex.withLock {
            grants.remove(grantId)
        } ?: return false
        
        auditService.logPermission(
            grant.deviceId,
            grant.permission.name,
            granted = false,
            reason = "Permission revoked"
        )
        
        return true
    }
    
    suspend fun revokeAllPermissions(deviceId: Uuid) {
        val revokedGrants = mutex.withLock {
            val deviceGrants = grants.values.filter { it.deviceId == deviceId }
            deviceGrants.forEach { grant ->
                grants.remove(grant.id)
            }
            deviceGrants
        }
        
        revokedGrants.forEach { grant ->
            auditService.logPermission(
                grant.deviceId,
                grant.permission.name,
                granted = false,
                reason = "All permissions revoked"
            )
        }
    }
    
    suspend fun getDevicePermissions(deviceId: Uuid): List<PermissionGrant> {
        return mutex.withLock {
            grants.values.filter { 
                it.deviceId == deviceId && it.isValid() 
            }
        }
    }
    
    suspend fun getPendingRequests(): List<PermissionRequest> {
        return mutex.withLock {
            requests.values.filter { it.status == RequestStatus.PENDING }
        }
    }
    
    suspend fun getPermissionRequest(requestId: String): PermissionRequest? {
        return mutex.withLock {
            requests[requestId]
        }
    }
    
    suspend fun cleanupExpiredGrants() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val expiredGrants = mutex.withLock {
            val expired = grants.values.filter { it.isExpired(currentTime) }
            expired.forEach { grant ->
                grants.remove(grant.id)
            }
            expired
        }
        
        expiredGrants.forEach { grant ->
            auditService.logPermission(
                grant.deviceId,
                grant.permission.name,
                granted = false,
                reason = "Permission expired"
            )
        }
    }
    
    suspend fun getPermissionStats(): PermissionStats {
        return mutex.withLock {
            val totalGrants = grants.size
            val activeGrants = grants.values.count { it.isValid() }
            val pendingRequests = requests.values.count { it.status == RequestStatus.PENDING }
            val grantsByPermission = grants.values
                .filter { it.isValid() }
                .groupBy { it.permission }
                .mapValues { it.value.size }
            
            PermissionStats(
                totalGrants = totalGrants,
                activeGrants = activeGrants,
                pendingRequests = pendingRequests,
                grantsByPermission = grantsByPermission
            )
        }
    }
}

data class PermissionStats(
    val totalGrants: Int,
    val activeGrants: Int,
    val pendingRequests: Int,
    val grantsByPermission: Map<Permission, Int>
)