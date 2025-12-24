package com.omnisyncra.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisyncra.ui.morphing.DeviceRole
import com.omnisyncra.ui.morphing.UIContext

/**
 * Morphing UI components that adapt based on device role and context
 */

@Composable
fun MorphingCard(
    role: DeviceRole,
    context: UIContext,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardElevation by animateFloatAsState(
        targetValue = when (role) {
            DeviceRole.PRIMARY -> 8f
            DeviceRole.SECONDARY -> 4f
            DeviceRole.VIEWER -> 2f
            DeviceRole.COMPUTE -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_elevation"
    )
    
    val cornerRadius by animateFloatAsState(
        targetValue = when (context) {
            UIContext.EDITOR -> 12f
            UIContext.PALETTE -> 8f
            UIContext.VIEWER -> 16f
            UIContext.MESH -> 20f
            UIContext.SETTINGS -> 10f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "corner_radius"
    )
    
    val cardColor by animateColorAsState(
        targetValue = when (role) {
            DeviceRole.PRIMARY -> MaterialTheme.colorScheme.surface
            DeviceRole.SECONDARY -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            DeviceRole.VIEWER -> MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            DeviceRole.COMPUTE -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        },
        animationSpec = tween(600),
        label = "card_color"
    )
    
    Card(
        modifier = modifier
            .graphicsLayer {
                shadowElevation = cardElevation
            },
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun MorphingButton(
    onClick: () -> Unit,
    role: DeviceRole,
    context: UIContext,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val buttonHeight by animateFloatAsState(
        targetValue = when (role) {
            DeviceRole.PRIMARY -> 48f
            DeviceRole.SECONDARY -> 40f
            DeviceRole.VIEWER -> 36f
            DeviceRole.COMPUTE -> 32f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button_height"
    )
    
    val buttonWidth by animateFloatAsState(
        targetValue = when (context) {
            UIContext.EDITOR -> 200f
            UIContext.PALETTE -> 120f
            UIContext.VIEWER -> 160f
            UIContext.MESH -> 100f
            UIContext.SETTINGS -> 180f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button_width"
    )
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(buttonHeight.dp)
            .width(buttonWidth.dp),
        content = content
    )
}

@Composable
fun MorphingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    role: DeviceRole,
    context: UIContext,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null
) {
    val textFieldHeight by animateFloatAsState(
        targetValue = when (role) {
            DeviceRole.PRIMARY -> 56f
            DeviceRole.SECONDARY -> 48f
            DeviceRole.VIEWER -> 40f
            DeviceRole.COMPUTE -> 36f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "textfield_height"
    )
    
    val cornerRadius by animateFloatAsState(
        targetValue = when (context) {
            UIContext.EDITOR -> 8f
            UIContext.PALETTE -> 12f
            UIContext.VIEWER -> 16f
            UIContext.MESH -> 20f
            UIContext.SETTINGS -> 6f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "textfield_corner"
    )
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(textFieldHeight.dp)
            .clip(RoundedCornerShape(cornerRadius.dp)),
        label = label,
        placeholder = placeholder
    )
}

@Composable
fun AdaptiveLayout(
    role: DeviceRole,
    context: UIContext,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val arrangement by animateFloatAsState(
        targetValue = when (context) {
            UIContext.EDITOR -> 16f
            UIContext.PALETTE -> 8f
            UIContext.VIEWER -> 24f
            UIContext.MESH -> 12f
            UIContext.SETTINGS -> 20f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "layout_spacing"
    )
    
    val padding by animateFloatAsState(
        targetValue = when (role) {
            DeviceRole.PRIMARY -> 16f
            DeviceRole.SECONDARY -> 12f
            DeviceRole.VIEWER -> 8f
            DeviceRole.COMPUTE -> 4f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "layout_padding"
    )
    
    Column(
        modifier = modifier.padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(arrangement.dp)
    ) {
        content()
    }
}

@Composable
fun RoleIndicator(
    currentRole: DeviceRole,
    targetRole: DeviceRole,
    isTransitioning: Boolean,
    modifier: Modifier = Modifier
) {
    val indicatorColor by animateColorAsState(
        targetValue = when (targetRole) {
            DeviceRole.PRIMARY -> Color(0xFF6366F1)
            DeviceRole.SECONDARY -> Color(0xFF8B5CF6)
            DeviceRole.VIEWER -> Color(0xFF10B981)
            DeviceRole.COMPUTE -> Color(0xFFF59E0B)
        },
        animationSpec = tween(600),
        label = "role_color"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isTransitioning) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "role_scale"
    )
    
    val rotation by animateFloatAsState(
        targetValue = if (isTransitioning) 360f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "role_rotation"
    )
    
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .background(
                color = indicatorColor,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (targetRole) {
                DeviceRole.PRIMARY -> "P"
                DeviceRole.SECONDARY -> "S"
                DeviceRole.VIEWER -> "V"
                DeviceRole.COMPUTE -> "C"
            },
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ContextIndicator(
    currentContext: UIContext,
    targetContext: UIContext,
    isTransitioning: Boolean,
    modifier: Modifier = Modifier
) {
    val contextIcon = when (targetContext) {
        UIContext.EDITOR -> "✏️"
        UIContext.PALETTE -> "🎨"
        UIContext.VIEWER -> "👁️"
        UIContext.MESH -> "🌐"
        UIContext.SETTINGS -> "⚙️"
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isTransitioning) 0.6f else 1f,
        animationSpec = tween(300),
        label = "context_alpha"
    )
    
    val offsetY by animateFloatAsState(
        targetValue = if (isTransitioning) -10f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "context_offset"
    )
    
    Text(
        text = contextIcon,
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetY
            },
        style = MaterialTheme.typography.headlineMedium
    )
}

@Composable
fun TransitionOverlay(
    isVisible: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = 0.3f * (1f - progress))
                ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
        }
    }
}

@Composable
fun MorphingTopBar(
    title: String,
    role: DeviceRole,
    context: UIContext,
    onRoleChange: (DeviceRole) -> Unit,
    onContextChange: (UIContext) -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight by animateFloatAsState(
        targetValue = when (role) {
            DeviceRole.PRIMARY -> 64f
            DeviceRole.SECONDARY -> 56f
            DeviceRole.VIEWER -> 48f
            DeviceRole.COMPUTE -> 40f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bar_height"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleIndicator(
                    currentRole = role,
                    targetRole = role,
                    isTransitioning = false
                )
                
                ContextIndicator(
                    currentContext = context,
                    targetContext = context,
                    isTransitioning = false
                )
            }
        }
    }
}