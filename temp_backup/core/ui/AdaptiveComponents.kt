package com.omnisyncra.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.core.domain.*

@Composable
fun AdaptiveContainer(
    uiState: UIState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val animationProgress by animateFloatAsState(
        targetValue = if (uiState.animations.isTransitioning) uiState.animations.progress else 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = when (uiState.animations.transitionType) {
                TransitionType.ROLE_CHANGE -> FastOutSlowInEasing
                TransitionType.DEVICE_CONNECT -> LinearOutSlowInEasing
                TransitionType.DEVICE_DISCONNECT -> FastOutLinearInEasing
                TransitionType.LAYOUT_ADAPT -> LinearOutSlowInEasing
                TransitionType.CONTEXT_SWITCH -> FastOutSlowInEasing
                null -> LinearEasing
            }
        ),
        label = "adaptive_container_animation"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(animationProgress)
            .scale(0.95f + (0.05f * animationProgress))
    ) {
        content()
        
        // Show transition overlay
        if (uiState.animations.isTransitioning) {
            TransitionOverlay(
                transitionType = uiState.animations.transitionType,
                progress = animationProgress
            )
        }
    }
}

@Composable
fun ProximityAwareLayout(
    uiState: UIState,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit = {},
    contextPalette: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val layout = uiState.adaptiveLayout
    
    when (uiState.currentMode) {
        UIMode.STANDALONE -> {
            Box(modifier = modifier.fillMaxSize()) {
                primaryContent()
            }
        }
        
        UIMode.PRIMARY -> {
            Row(modifier = modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(layout.availableSpace.primary)
                ) {
                    primaryContent()
                }
                
                if (layout.availableSpace.secondary > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(layout.availableSpace.secondary)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                            )
                    ) {
                        secondaryContent()
                    }
                }
            }
        }
        
        UIMode.SECONDARY -> {
            Row(modifier = modifier.fillMaxSize()) {
                if (layout.availableSpace.secondary > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(layout.availableSpace.secondary)
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                                RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                            )
                    ) {
                        secondaryContent()
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(layout.availableSpace.primary)
                ) {
                    primaryContent()
                }
            }
        }
        
        UIMode.CONTEXT_PALETTE -> {
            Row(modifier = modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(layout.availableSpace.primary)
                ) {
                    primaryContent()
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(layout.availableSpace.secondary)
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                ) {
                    contextPalette()
                }
            }
        }
        
        UIMode.VIEWER -> {
            Box(modifier = modifier.fillMaxSize()) {
                primaryContent()
            }
        }
        
        UIMode.TRANSITIONING -> {
            Box(modifier = modifier.fillMaxSize()) {
                primaryContent()
                
                // Show transition state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ContextPalette(
    activeContext: OmnisyncraContext?,
    relatedContexts: List<OmnisyncraContext>,
    onContextSelect: (OmnisyncraContext) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Context Palette",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        
        activeContext?.let { context ->
            ContextCard(
                context = context,
                isActive = true,
                onClick = { onContextSelect(context) }
            )
        }
        
        if (relatedContexts.isNotEmpty()) {
            Text(
                text = "Related Contexts",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            relatedContexts.forEach { context ->
                ContextCard(
                    context = context,
                    isActive = false,
                    onClick = { onContextSelect(context) }
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // AI-generated suggestions
        SuggestionsSection()
    }
}

@Composable
private fun ContextCard(
    context: OmnisyncraContext,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = context.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Text(
                text = context.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            if (context.metadata.aiSummary != null) {
                Text(
                    text = context.metadata.aiSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun SuggestionsSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "AI Suggestions",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        
        val suggestions = listOf(
            "Research related topics",
            "Prepare presentation slides",
            "Schedule follow-up meeting"
        )
        
        suggestions.forEach { suggestion ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun TransitionOverlay(
    transitionType: TransitionType?,
    progress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.1f * (1f - progress))),
        contentAlignment = Alignment.Center
    ) {
        when (transitionType) {
            TransitionType.ROLE_CHANGE -> {
                Text(
                    text = "Adapting Role...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }
            TransitionType.DEVICE_CONNECT -> {
                Text(
                    text = "Device Connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }
            TransitionType.DEVICE_DISCONNECT -> {
                Text(
                    text = "Device Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }
            TransitionType.LAYOUT_ADAPT -> {
                Text(
                    text = "Adapting Layout...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }
            TransitionType.CONTEXT_SWITCH -> {
                Text(
                    text = "Switching Context...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }
            null -> {}
        }
    }
}

@Composable
fun ProximityIndicator(
    proximityInfo: ProximityInfo?,
    style: ProximityStyle,
    modifier: Modifier = Modifier
) {
    if (proximityInfo == null) return
    
    val color = when (proximityInfo.distance) {
        ProximityDistance.IMMEDIATE -> Color(0xFF10B981)
        ProximityDistance.NEAR -> Color(0xFF3B82F6)
        ProximityDistance.FAR -> Color(0xFFF59E0B)
        ProximityDistance.UNKNOWN -> Color(0xFF6B7280)
    }
    
    val alpha by animateFloatAsState(
        targetValue = when (style) {
            ProximityStyle.SUBTLE -> 0.3f
            ProximityStyle.AMBIENT -> 0.6f
            ProximityStyle.PROMINENT -> 1.0f
        },
        animationSpec = tween(300),
        label = "proximity_alpha"
    )
    
    Box(
        modifier = modifier
            .size(12.dp)
            .background(
                color.copy(alpha = alpha),
                RoundedCornerShape(6.dp)
            )
    )
}