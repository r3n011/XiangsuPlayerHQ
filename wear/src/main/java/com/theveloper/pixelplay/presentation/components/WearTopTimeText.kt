package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Unspecified
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults

@Composable
fun WearTopTimeText(
    modifier: Modifier = Modifier,
    color: Color = Unspecified,
) {
    TimeText(
        modifier = modifier,
        timeTextStyle = TimeTextDefaults.timeTextStyle(
            color = color,
        ),
    )
}
