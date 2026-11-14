package com.theveloper.pixelplay.presentation.components.hearingguard

import com.theveloper.pixelplay.utils.localizedStringResource
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.hearingguard.HearingGuardState
import com.theveloper.pixelplay.ui.theme.ShapeCache

/**
 * 像素卫士状态卡片（从下往上弹出，类似云端串流）。
 *
 * 纯展示当前听音状态（只读），右上角提供"设置"入口。
 * 隐去设置弹窗的底部操作按钮；未配置 / 首次进入时在顶部给出引导。
 * 全部使用 Material 3 原生组件（Card / ListItem / LinearProgressIndicator）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearingGuardStatusSheet(
    state: HearingGuardState,
    showSetupHint: Boolean,
    onSettingsClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val progress = state.dailyProgress.coerceIn(0f, 1f)
    val ticket = statusColor(MaterialTheme.colorScheme, progress)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "sheet_daily_progress"
    )
    val pct by animateIntAsState(
        targetValue = (progress * 100).toInt().coerceIn(0, 100),
        animationSpec = tween(400),
        label = "sheet_percent"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 顶栏：标题 + 右上设置 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ticket.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = ticket,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedStringResource(R.string.ui_pixel_guardian),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = if (state.isConfigured)
                            localizedStringResource(R.string.ui_protecting_your_hearing_with_who_safe_listening_guidance)
                        else
                            localizedStringResource(R.string.ui_your_hearing_health_companion),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledIconButton(
                    onClick = onSettingsClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.surfaceContainerHigh,
                        contentColor = colors.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = localizedStringResource(R.string.ui_residue_nav_bar_settings)
                    )
                }
            }

            // 首次使用 / 未配置引导
            if (showSetupHint || !state.isConfigured) {
                SetupHint(onSettings = onSettingsClick)
            }

            if (state.isConfigured && state.plan != null) {
                val plan = state.plan
                val todayMinutes = (state.todayListeningMs / 60_000L).toInt()

                // ── 今日听音状态卡 ──
                Card(
                    shape = ShapeCache.smooth16,
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = ticket
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = localizedStringResource(R.string.ui_min_remaining, (state.remainingMinutes).toString()),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = localizedStringResource(R.string.ui_today_min_708b3e3, (todayMinutes).toString(), (plan.dailyLimitMinutes).toString()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = ticket,
                            trackColor = colors.surfaceContainerHighest,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }

                // ── 听音状态三项 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetric(
                        label = AppLocaleManager.getString(R.string.ui_this_session),
                        value = "${(state.currentSessionMs / 60_000L).toInt().coerceAtLeast(0)}",
                        suffix = "min",
                        tint = if (state.sessionProgress >= 0.9f) colors.error else colors.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetric(
                        label = AppLocaleManager.getString(R.string.ui_session_limit),
                        value = "${plan.sessionLimitMinutes}",
                        suffix = "min",
                        tint = colors.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetric(
                        label = AppLocaleManager.getString(R.string.ui_daily_limit_e95f837),
                        value = "${plan.dailyLimitMinutes}",
                        suffix = "min",
                        tint = colors.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

@Composable
private fun SetupHint(onSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(
            containerColor = colors.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = localizedStringResource(R.string.ui_open_settings_in_the_top_right_and_enter_your_gender),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = localizedStringResource(R.string.ui_open_settings),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSettings() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun StatMetric(
    label: String,
    value: String,
    suffix: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainer
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
            Text(
                text = suffix,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}