package com.omnisyncra.core.domain

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.omnisyncra.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable

@Serializable
data class OmnisyncraContext(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid = uuid4(),
    val name: String,
    val type: ContextType,
    val data: Map<String, String> = emptyMap(),
    val metadata: ContextMetadata,
    val createdAt: Long,
    val lastModified: Long,
    @Serializable(with = UuidSerializer::class)
    val deviceId: Uuid,
    val priority: ContextPriority = ContextPriority.NORMAL
)

@Serializable
enum class ContextType {
    DOCUMENT_EDITING,
    RESEARCH_SESSION,
    CODING_PROJECT,
    PRESENTATION,
    MEDIA_CONSUMPTION,
    COMMUNICATION,
    CUSTOM
}

@Serializable
data class ContextMetadata(
    val tags: List<String> = emptyList(),
    val relatedContexts: List<@Serializable(with = UuidSerializer::class) Uuid> = emptyList(),
    val aiSummary: String? = null,
    val keyResources: List<String> = emptyList(),
    val estimatedDuration: Long? = null,
    val completionPercentage: Float = 0f
)

@Serializable
enum class ContextPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

@Serializable
data class ContextGraph(
    val contexts: Map<@Serializable(with = UuidSerializer::class) Uuid, OmnisyncraContext> = emptyMap(),
    @Serializable(with = UuidSerializer::class)
    val activeContext: Uuid? = null,
    val contextHistory: List<@Serializable(with = UuidSerializer::class) Uuid> = emptyList(),
    val lastUpdated: Long
) {
    fun getActiveContext(): OmnisyncraContext? = 
        activeContext?.let { contexts[it] }
    
    fun getRelatedContexts(contextId: Uuid): List<OmnisyncraContext> =
        contexts[contextId]?.metadata?.relatedContexts
            ?.mapNotNull { contexts[it] } ?: emptyList()
}