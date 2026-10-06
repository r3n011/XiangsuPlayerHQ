package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 「列表内搜索」通用入口：队列弹窗与播放列表详情页共用。
 *
 * 折叠态：一行胶囊按钮，点击展开并聚焦。
 * 展开态：胶囊搜索框（CircleShape + Search 前导图标 + 清空按钮 +
 * surfaceContainerHigh 容器 + 透明描边），右侧匹配数量与「取消」。
 *
 * 注意：展开后键盘会弹出——若宿主按「键盘可见即撤下列表宿主」的方式管理
 * 状态，必须把本组件的展开状态纳入豁免（见 UnifiedPlayerSheetV2 的
 * shouldRenderQueueHost 门控），否则整个列表会在键盘弹出时被卸载。
 */
@Composable
fun InlinePlaylistSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    resultCountText: String?,
    hintText: String,
    clearSearchContentDescription: String,
    cancelText: String,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }
    if (!expanded) {
        Surface(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { onExpandedChange(true) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(hintText) },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty() && resultCountText != null) {
                        Text(
                            text = resultCountText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = clearSearchContentDescription
                            )
                        }
                    }
                    TextButton(onClick = { onQueryChange(""); onExpandedChange(false) }) {
                        Text(cancelText)
                    }
                }
            },
            // ⚡ 固定 56dp 高度：输入后 Clear IconButton 出现会把 trailing 行撑高、
            //    导致输入框高度跳变——固定高度后 48dp 的尾部按钮恒在预算内，不再跳动
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(56.dp)
                .focusRequester(focusRequester),
            shape = CircleShape,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
        )
    }
}
