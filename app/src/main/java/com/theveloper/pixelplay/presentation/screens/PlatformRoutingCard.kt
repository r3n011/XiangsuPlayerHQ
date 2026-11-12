package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.lx.LxScriptInfo
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 平台 → 音源脚本 路由板。
 *
 * 左列 = 平台（kw/kg/tx/wy…），右列 = 已安装脚本；Canvas 画三次贝塞尔连线。
 * 实线 = 首选脚本，虚线 = 备用脚本。点平台选中后再点脚本建立连接；
 * 已连接的脚本再点一次会提升为首选，断开走底部 chip 的 ✕。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlatformRoutingCard(
    scripts: List<LxScriptInfo>,
    scriptsByPlatform: Map<String, List<String>>,
    platformNames: Map<String, String>,
    routes: Map<String, List<String>>,
    onConnect: (String, String) -> Unit,
    onDisconnect: (String, String) -> Unit,
    onPrefer: (String, String) -> Unit,
    onAuto: () -> Unit,
    onClear: () -> Unit,
) {
    val rowHeight = 44.dp
    var selectedPlatform by remember { mutableStateOf<String?>(null) }

    val platforms = remember(scriptsByPlatform) {
        val order = listOf("kw", "kg", "tx", "wy", "mg", "qq", "qs", "bili", "ytb", "xm", "local")
        scriptsByPlatform.keys.sortedWith(
            compareBy({ key -> order.indexOf(key).let { if (it < 0) 999 else it } }, { it })
        )
    }

    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "平台路由",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "点平台 → 点右侧音源连线；实线为首选，虚线为备用",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
                TextButton(onClick = onAuto) { Text("自动连接") }
                TextButton(onClick = onClear) { Text("清空") }
            }

            Spacer(Modifier.height(10.dp))

            if (platforms.isEmpty() || scripts.isEmpty()) {
                Text(
                    text = "导入音源脚本后即可配置路由",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                return@Column
            }

            val boardHeight = rowHeight * maxOf(platforms.size, scripts.size)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(boardHeight)
            ) {
                val leftWidth = maxWidth * 0.44f
                val rightWidth = maxWidth * 0.46f
                val density = LocalDensity.current
                val leftX = with(density) { leftWidth.toPx() }
                val rightX = with(density) { (maxWidth - rightWidth).toPx() }
                val rowPx = with(density) { rowHeight.toPx() }

                Canvas(Modifier.fillMaxSize()) {
                    fun rowCenter(index: Int) = index * rowPx + rowPx / 2f
                    val span = rightX - leftX

                    platforms.forEachIndexed { platformIndex, platform ->
                        val files = routes[platform].orEmpty()
                        files.forEach { file ->
                            val scriptIndex = scripts.indexOfFirst { it.fileName == file }
                            if (scriptIndex < 0) return@forEach
                            val preferred = files.firstOrNull() == file
                            val path = Path().apply {
                                moveTo(leftX, rowCenter(platformIndex))
                                cubicTo(
                                    leftX + span * 0.45f, rowCenter(platformIndex),
                                    leftX + span * 0.55f, rowCenter(scriptIndex),
                                    rightX, rowCenter(scriptIndex),
                                )
                            }
                            drawPath(
                                path = path,
                                color = if (preferred) primary.copy(alpha = 0.9f) else primary.copy(alpha = 0.28f),
                                style = Stroke(
                                    width = if (preferred) 2.2.dp.toPx() else 1.1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    pathEffect = if (preferred) null else PathEffect.dashPathEffect(
                                        floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                                    ),
                                ),
                            )
                        }

                        val selected = selectedPlatform == platform
                        drawCircle(
                            color = when {
                                selected -> primary
                                files.isNotEmpty() -> primary.copy(alpha = 0.75f)
                                else -> muted.copy(alpha = 0.35f)
                            },
                            radius = (if (selected) 6.dp else 4.5.dp).toPx(),
                            center = Offset(leftX, rowCenter(platformIndex)),
                        )
                    }

                    scripts.forEachIndexed { scriptIndex, _ ->
                        drawCircle(
                            color = muted.copy(alpha = 0.5f),
                            radius = 4.dp.toPx(),
                            center = Offset(rightX, rowCenter(scriptIndex)),
                        )
                    }
                }

                Column(Modifier.width(leftWidth)) {
                    platforms.forEach { platform ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedPlatform = if (selectedPlatform == platform) null else platform
                                }
                                .padding(end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = platformNames[platform]?.takeIf { it.isNotBlank() } ?: platform.uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = platform.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(rightWidth)
                ) {
                    scripts.forEach { script ->
                        val connected = selectedPlatform?.let { platform ->
                            routes[platform].orEmpty().contains(script.fileName)
                        } == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = selectedPlatform != null) {
                                    val platform = selectedPlatform ?: return@clickable
                                    if (connected) onPrefer(platform, script.fileName)
                                    else onConnect(platform, script.fileName)
                                }
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = script.name.ifBlank { script.fileName },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selectedPlatform == null) muted else MaterialTheme.colorScheme.onSurface,
                            )
                            if (connected) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            selectedPlatform?.let { platform ->
                Spacer(Modifier.height(10.dp))
                val files = routes[platform].orEmpty()
                Text(
                    text = "「${platformNames[platform]?.takeIf { it.isNotBlank() } ?: platform.uppercase()}」已连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                Spacer(Modifier.height(6.dp))
                if (files.isEmpty()) {
                    Text(
                        text = "尚未连接，点右侧音源添加；也可点「自动连接」",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        files.forEachIndexed { index, file ->
                            val name = scripts.firstOrNull { it.fileName == file }?.name ?: file
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (index == 0) primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(androidx.compose.ui.graphics.Color.Transparent)
                                        .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = (if (index == 0) "★ " else "") + name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (index == 0) primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "断开",
                                        tint = muted,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onDisconnect(platform, file) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
