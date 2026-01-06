package com.omnisyncra.di

import com.benasher44.uuid.uuid4
import com.omnisyncra.core.compute.*
import com.omnisyncra.core.discovery.DeviceDiscovery
import com.omnisyncra.core.discovery.MockDeviceDiscovery
import com.omnisyncra.core.performance.*
import com.omnisyncra.core.platform.Platform
import com.omnisyncra.core.platform.getPlatform
import com.omnisyncra.core.resilience.*
import com.omnisyncra.core.security.*
import com.omnisyncra.core.state.DistributedStateManager
import com.omnisyncra.core.state.SimpleStateManager
import com.omnisyncra.core.state.StateRecovery
import com.omnisyncra.core.storage.InMemoryStorage
import com.omnisyncra.core.storage.LocalStorage
import com.omnisyncra.core.ui.OmnisyncraUIOrchestrator
import com.omnisyncra.core.ui.UIOrchestrator
import org.koin.dsl.module

val commonModule = module {
    // Platform
    single<Platform> { getPlatform() }
    
    // Storage
    single<LocalStorage> { InMemoryStorage() }
    
    // Performance Components
    single { ConnectionPool() }
    single { CrdtCache() }
    single { PerformanceMonitor() }
    single { BatchManager() }
    
    // Resilience Components
    single<ErrorRecoveryManager> { ErrorRecoveryManagerImpl() }
    single<GracefulDegradationManager> { GracefulDegradationManagerImpl() }
    
    // Security Components (now using real implementations)
    single<CryptoEngine> { CryptoEngine() }
    single<SecuritySystem> { 
        val deviceId = get<com.benasher44.uuid.Uuid>().toString()
        OmnisyncraSecuritySystem(deviceId)
    }
    
    // Legacy mock services for compatibility
    single<EncryptionService> { MockEncryptionService() }
    single<AuditLogService> { MockAuditLogService(get()) }
    single<AuthenticationService> { MockAuthenticationService(get()) }
    single<KeyExchangeService> { MockKeyExchangeService(get(), get(), get()) }
    single<PermissionManager> { MockPermissionManager(get(), get(), get()) }
    single<SecureMessagingService> { MockSecureMessagingService(get(), get(), get(), get(), get()) }
    
    // Core Services
    single { uuid4() } // Node ID
    single<DeviceDiscovery> { MockDeviceDiscovery(get()) }
    single { StateRecovery(get(), get()) }
    single<DistributedStateManager> { SimpleStateManager(get(), get()) }
    
    // UI Orchestration
    single<UIOrchestrator> { 
        OmnisyncraUIOrchestrator(
            platform = get(),
            deviceDiscovery = get(),
            stateManager = get(),
            nodeId = get()
        )
    }
    
    // Compute Services
    single<PerformanceProfiler> { OmnisyncraPerformanceProfiler(get()) }
    single<TaskExecutor> {
        val nodeId = get<com.benasher44.uuid.Uuid>()
        val platform = get<Platform>()
        val capabilities = ExecutorCapabilities(
            maxConcurrentTasks = platform.capabilities.maxConcurrentTasks,
            supportedTaskTypes = com.omnisyncra.core.domain.TaskType.values().toList(),
            hasGPUAcceleration = platform.capabilities.computePower.ordinal >= 2,
            maxMemoryMB = when (platform.capabilities.computePower) {
                com.omnisyncra.core.domain.ComputePower.LOW -> 2048
                com.omnisyncra.core.domain.ComputePower.MEDIUM -> 4096
                com.omnisyncra.core.domain.ComputePower.HIGH -> 8192
                com.omnisyncra.core.domain.ComputePower.EXTREME -> 16384
            },
            estimatedPerformanceMultiplier = when (platform.capabilities.computePower) {
                com.omnisyncra.core.domain.ComputePower.LOW -> 0.5
                com.omnisyncra.core.domain.ComputePower.MEDIUM -> 1.0
                com.omnisyncra.core.domain.ComputePower.HIGH -> 1.5
                com.omnisyncra.core.domain.ComputePower.EXTREME -> 2.0
            }
        )
        LocalTaskExecutor(nodeId, capabilities)
    }
    single<ComputeNetworkCommunicator> { KtorComputeNetworkCommunicator() }
    single<ComputeScheduler> { 
        OmnisyncraComputeScheduler(
            localPlatform = get(),
            taskExecutor = get(),
            networkCommunicator = get()
        )
    }
}

// All modules for easy import
val allModules = listOf(
    commonModule,
    securityModule,
    aiModule,
    handoffModule
)