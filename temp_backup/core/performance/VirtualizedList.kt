package com.omnisyncra.core.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

data class VirtualizationConfig(
    val itemHeight: Dp = 60.dp,
    val overscanCount: Int = 5, // Extra items to render outside viewport
    val recycleThreshold: Int = 100 // Start recycling after this many items
)

@Composable
fun <T> VirtualizedLazyColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    config: VirtualizationConfig = VirtualizationConfig(),
    state: LazyListState = rememberLazyListState(),
    key: ((index: Int, item: T) -> Any)? = null,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { config.itemHeight.toPx() }
    
    // Calculate visible range
    val visibleRange by remember {
        derivedStateOf {
            calculateVisibleRange(
                firstVisibleItemIndex = state.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                viewportHeight = itemHeightPx * 10, // Approximate viewport height
                itemHeight = itemHeightPx,
                totalItems = items.size,
                overscanCount = config.overscanCount
            )
        }
    }
    
    LazyColumn(
        modifier = modifier,
        state = state
    ) {
        // Add spacer for items before visible range
        if (visibleRange.start > 0) {
            item(key = "top_spacer") {
                Spacer(modifier = Modifier.height(config.itemHeight * visibleRange.start))
            }
        }
        
        // Render visible items
        items(
            count = visibleRange.count,
            key = if (key != null) { index ->
                val actualIndex = visibleRange.start + index
                key(actualIndex, items[actualIndex])
            } else null
        ) { index ->
            val actualIndex = visibleRange.start + index
            if (actualIndex < items.size) {
                itemContent(actualIndex, items[actualIndex])
            }
        }
        
        // Add spacer for items after visible range
        val remainingItems = items.size - (visibleRange.start + visibleRange.count)
        if (remainingItems > 0) {
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(config.itemHeight * remainingItems))
            }
        }
    }
}

private data class VisibleRange(
    val start: Int,
    val count: Int
)

private fun calculateVisibleRange(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    viewportHeight: Float,
    itemHeight: Float,
    totalItems: Int,
    overscanCount: Int
): VisibleRange {
    if (totalItems == 0 || itemHeight <= 0) {
        return VisibleRange(0, 0)
    }
    
    // Calculate how many items fit in viewport
    val itemsInViewport = (viewportHeight / itemHeight).toInt() + 1
    
    // Calculate start index with overscan
    val startIndex = max(0, firstVisibleItemIndex - overscanCount)
    
    // Calculate end index with overscan
    val endIndex = min(
        totalItems - 1,
        firstVisibleItemIndex + itemsInViewport + overscanCount
    )
    
    val count = max(0, endIndex - startIndex + 1)
    
    return VisibleRange(startIndex, count)
}

// Performance-optimized device list component
@Composable
fun VirtualizedDeviceList(
    devices: List<com.omnisyncra.core.domain.Device>,
    modifier: Modifier = Modifier,
    onDeviceClick: (com.omnisyncra.core.domain.Device) -> Unit = {},
    itemContent: @Composable (device: com.omnisyncra.core.domain.Device) -> Unit
) {
    // Use virtualization only for large lists
    if (devices.size > 20) {
        VirtualizedLazyColumn(
            items = devices,
            modifier = modifier,
            config = VirtualizationConfig(
                itemHeight = 80.dp,
                overscanCount = 3
            ),
            key = { _, device -> device.id }
        ) { _, device ->
            itemContent(device)
        }
    } else {
        // For small lists, use regular LazyColumn for simplicity
        LazyColumn(modifier = modifier) {
            items(
                count = devices.size,
                key = { index -> devices[index].id }
            ) { index ->
                itemContent(devices[index])
            }
        }
    }
}

// Memory-efficient task list
@Composable
fun VirtualizedTaskList(
    tasks: List<com.omnisyncra.core.compute.ComputeTask>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (task: com.omnisyncra.core.compute.ComputeTask) -> Unit
) {
    if (tasks.size > 50) {
        VirtualizedLazyColumn(
            items = tasks,
            modifier = modifier,
            config = VirtualizationConfig(
                itemHeight = 100.dp,
                overscanCount = 5,
                recycleThreshold = 100
            ),
            key = { _, task -> task.id }
        ) { _, task ->
            itemContent(task)
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(
                count = tasks.size,
                key = { index -> tasks[index].id }
            ) { index ->
                itemContent(tasks[index])
            }
        }
    }
}

// Performance metrics for virtualization
class VirtualizationMetrics {
    private var totalItemsRendered = 0
    private var totalItemsSkipped = 0
    private var renderTime = 0L
    
    fun recordRender(itemsRendered: Int, itemsSkipped: Int, timeMs: Long) {
        totalItemsRendered += itemsRendered
        totalItemsSkipped += itemsSkipped
        renderTime += timeMs
    }
    
    fun getEfficiencyRatio(): Double {
        val totalItems = totalItemsRendered + totalItemsSkipped
        return if (totalItems > 0) {
            totalItemsSkipped.toDouble() / totalItems
        } else 0.0
    }
    
    fun getAverageRenderTime(): Double {
        return if (totalItemsRendered > 0) {
            renderTime.toDouble() / totalItemsRendered
        } else 0.0
    }
    
    fun reset() {
        totalItemsRendered = 0
        totalItemsSkipped = 0
        renderTime = 0L
    }
}