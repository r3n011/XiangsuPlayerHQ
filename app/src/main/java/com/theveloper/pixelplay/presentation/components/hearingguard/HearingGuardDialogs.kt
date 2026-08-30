package com.theveloper.pixelplay.presentation.components.hearingguard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.hearingguard.Gender
import com.theveloper.pixelplay.data.hearingguard.HearingGuardConfig
import com.theveloper.pixelplay.data.hearingguard.HearingGuardState
import com.theveloper.pixelplay.data.hearingguard.ListeningPlan
import com.theveloper.pixelplay.ui.theme.ShapeCache

/**
 * 听力保护设置弹窗
 *
 * 用户选择性别和年龄后，自动计算每日听音计划
 */
@Composable
fun HearingGuardSetupDialog(
    currentConfig: HearingGuardConfig?,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onConfirm: (HearingGuardConfig) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGender by remember { mutableStateOf(currentConfig?.gender ?: Gender.MALE) }
    var selectedAge by remember { mutableIntStateOf(currentConfig?.age ?: 18) }

    // 预览计划
    val previewPlan = remember(selectedGender, selectedAge) {
        calculatePlanPreview(selectedGender, selectedAge)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Column {
                Text(
                    text = "听力保护",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "基于 WHO 安全听音指南，保护您的听力健康",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 功能开关
                if (currentConfig != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isEnabled) "保护已启用" else "保护已暂停",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isEnabled) "正在跟踪听音时长" else "听音时长未被跟踪",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = onEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                // 性别选择
                GenderSelector(
                    selectedGender = selectedGender,
                    onGenderSelected = { selectedGender = it }
                )

                // 年龄选择
                AgeSelector(
                    selectedAge = selectedAge,
                    onAgeChanged = { selectedAge = it }
                )

                // 计划预览
                PlanPreviewCard(
                    gender = selectedGender,
                    age = selectedAge,
                    plan = previewPlan
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(HearingGuardConfig(selectedGender, selectedAge)) }) {
                Text("启用保护", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (currentConfig != null) {
                    TextButton(onClick = onDisable) {
                        Text("关闭保护", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

/**
 * 休息提醒弹窗
 *
 * 当达到会话限制或每日限制时弹出，暂停播放
 */
@Composable
fun RestReminderDialog(
    state: HearingGuardState,
    onRestConfirm: () -> Unit,
    onContinueListening: () -> Unit,
    onDismiss: () -> Unit
) {
    val plan = state.plan ?: return
    val isDailyExhausted = state.todayListeningMs >= plan.dailyLimitMinutes * 60_000L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // 盾牌图标
                val shieldColor = if (isDailyExhausted) Color(0xFFEF4444) else Color(0xFFFBBF24)
                val icon = if (isDailyExhausted) Icons.Rounded.Close else Icons.Rounded.Warning

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(shieldColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = shieldColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (isDailyExhausted) "今日听音已达上限" else "该休息一下了",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isDailyExhausted) {
                    Text(
                        text = "您今日已听音 ${plan.dailyLimitMinutes} 分钟，达到安全上限。\n为了保护听力，请明天再继续享受音乐吧。",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "您已连续听音 ${state.sessionRemainingMinutes.coerceAtLeast(0) + (plan.sessionLimitMinutes - state.sessionRemainingMinutes.coerceAtLeast(0))} 分钟。\n建议休息 ${plan.restMinutes} 分钟后再继续。",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "今日已听 ${state.todayListeningMs / 60_000} 分钟 / ${plan.dailyLimitMinutes} 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRestConfirm) {
                Text(
                    if (isDailyExhausted) "好的" else "休息一下",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (!isDailyExhausted) {
                TextButton(onClick = onContinueListening) {
                    Text("继续听", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

// ─── 内部组件 ──────────────────────────────────────────────────────

@Composable
private fun GenderSelector(
    selectedGender: Gender,
    onGenderSelected: (Gender) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "性别",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenderChip(
                label = "男",
                selected = selectedGender == Gender.MALE,
                onClick = { onGenderSelected(Gender.MALE) },
                modifier = Modifier.weight(1f)
            )
            GenderChip(
                label = "女",
                selected = selectedGender == Gender.FEMALE,
                onClick = { onGenderSelected(Gender.FEMALE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GenderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(200),
        label = "gender_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "gender_text"
    )

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(
                if (selected) Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (selected) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AgeSelector(
    selectedAge: Int,
    onAgeChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "年龄",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${selectedAge} 岁",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Slider(
            value = selectedAge.toFloat(),
            onValueChange = { onAgeChanged(it.toInt()) },
            valueRange = 6f..80f,
            steps = 73,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("6 岁", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("80 岁", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanPreviewCard(
    gender: Gender,
    age: Int,
    plan: ListeningPlan
) {
    Surface(
        shape = ShapeCache.smooth16,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "每日听音计划",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            PlanItem(label = "每次最长听歌", value = "${plan.sessionLimitMinutes} 分钟")
            PlanItem(label = "每次休息时长", value = "${plan.restMinutes} 分钟")
            PlanItem(label = "每日总上限", value = "${plan.dailyLimitMinutes} 分钟")
        }
    }
}

@Composable
private fun PlanItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 计算计划预览（与 HearingGuardManager.calculatePlan 逻辑一致）
 */
private fun calculatePlanPreview(gender: Gender, age: Int): ListeningPlan {
    val isMale = gender == Gender.MALE
    return when {
        age <= 12 -> ListeningPlan(
            sessionLimitMinutes = if (isMale) 30 else 25,
            restMinutes = 15,
            dailyLimitMinutes = if (isMale) 60 else 50
        )
        age <= 17 -> ListeningPlan(
            sessionLimitMinutes = if (isMale) 45 else 40,
            restMinutes = 15,
            dailyLimitMinutes = if (isMale) 90 else 80
        )
        age <= 64 -> ListeningPlan(
            sessionLimitMinutes = if (isMale) 60 else 50,
            restMinutes = 15,
            dailyLimitMinutes = if (isMale) 120 else 100
        )
        else -> ListeningPlan(
            sessionLimitMinutes = if (isMale) 50 else 45,
            restMinutes = 20,
            dailyLimitMinutes = if (isMale) 90 else 80
        )
    }
}
