package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 1:1 移植自 Rhythm 的 ExpressiveButtonGroup：分段式样式切换按钮组。
 * 选中项变为胶囊高亮（primary 底 + 勾选图标），未选中为 10dp 圆角透明底。
 */
@Composable
fun ExpressiveButtonGroup(
    items: List<String>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(durationMillis = 250),
                label = "expressiveButtonGroupBackground"
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(if (isSelected) CircleShape else RoundedCornerShape(10.dp))
                    .background(backgroundColor)
                    .clickable { onItemClick(index) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = item,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
