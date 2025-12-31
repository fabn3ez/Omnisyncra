package com.omnisyncra.core.state

import com.omnisyncra.core.crdt.CrdtOperation

/**
 * Basic distributed state manager interface
 * This is a simplified version for compilation compatibility
 */
interface DistributedStateManager {
    suspend fun initialize(): Boolean
    suspend fun applyLocalOperation(operation: CrdtOperation): Boolean
    suspend fun applyRemoteOperations(operations: List<CrdtOperation>): Boolean
}

/**
 * Simple implementation of DistributedStateManager for compilation compatibility
 */
class SimpleDistributedStateManager : DistributedStateManager {
    override suspend fun initialize(): Boolean {
        return true
    }
    
    override suspend fun applyLocalOperation(operation: CrdtOperation): Boolean {
        // Simple implementation - just return success
        return true
    }
    
    override suspend fun applyRemoteOperations(operations: List<CrdtOperation>): Boolean {
        // Simple implementation - just return success
        return true
    }
}