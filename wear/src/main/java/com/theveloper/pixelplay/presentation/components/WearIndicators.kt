package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.ScrollIndicatorDefaults

/**
 * Scroll indicator wrapper for ScalingLazyColumn lists.
 */
@Composable
fun AlwaysOnScalingPositionIndicator(
    listState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val trackColor = if (color == Color.Unspecified) Color.Unspecified else color.copy(alpha = 0.28f)
    ScrollIndicator(
        state = listState,
        modifier = modifier,
        colors = ScrollIndicatorDefaults.colors(
            indicatorColor = color,
            trackColor = trackColor,
        ),
    )
}
