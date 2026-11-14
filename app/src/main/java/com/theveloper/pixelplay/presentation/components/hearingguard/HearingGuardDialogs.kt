package com.theveloper.pixelplay.presentation.components.hearingguard

import com.theveloper.pixelplay.utils.localizedStringResource
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

import androidx.compose.animation.animateColorAsState
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.data.hearingguard.Gender
import com.theveloper.pixelplay.data.hearingguard.HearingGuardConfig
import com.theveloper.pixelplay.data.hearingguard.HearingGuardState
import com.theveloper.pixelplay.data.hearingguard.ListeningPlan
import com.theveloper.pixelplay.ui.theme.ShapeCache
import java.time.LocalDate
import java.time.Period

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
    var selectedBirthdayEpochDay by remember {
        mutableStateOf(
            currentConfig?.birthdayEpochDay
                ?: defaultBirthdayEpochDay(currentConfig?.age ?: 18)
        )
    }
    val birthdayAge = remember(selectedBirthdayEpochDay) {
        ageFromEpochDay(selectedBirthdayEpochDay)
    }

    // 预览计划
    val previewPlan = remember(selectedGender, birthdayAge) {
        calculatePlanPreview(selectedGender, birthdayAge)
    }

    PixelAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Column {
                Text(
                    text = localizedStringResource(R.string.ui_hearing_protection),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(R.string.ui_protect_your_hearing_with_who_safe_listening_guidance),
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
                                text = if (isEnabled) localizedStringResource(R.string.ui_protection_enabled) else localizedStringResource(R.string.ui_protection_paused),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isEnabled) localizedStringResource(R.string.ui_listening_time_is_being_tracked) else localizedStringResource(R.string.ui_listening_time_is_not_being_tracked),
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

                // 生日选择（自动计算年龄）
                BirthdaySelector(
                    birthdayEpochDay = selectedBirthdayEpochDay,
                    age = birthdayAge,
                    onBirthdaySelected = { selectedBirthdayEpochDay = it }
                )

                // 计划预览
                PlanPreviewCard(
                    gender = selectedGender,
                    age = birthdayAge,
                    plan = previewPlan
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 同步 age 字段以便旧逻辑与持久化保持一致
                    onConfirm(
                        HearingGuardConfig(
                            gender = selectedGender,
                            age = birthdayAge,
                            birthdayEpochDay = selectedBirthdayEpochDay
                        )
                    )
                }
            ) {
                Text(localizedStringResource(R.string.ui_residue_ok), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedStringResource(R.string.ui_residue_cancel))
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

    PixelAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // 盾牌图标
                val shieldColor = if (isDailyExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
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
                    text = if (isDailyExhausted) localizedStringResource(R.string.ui_daily_listening_limit_reached) else localizedStringResource(R.string.ui_time_for_a_break),
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
                        text = localizedStringResource(R.string.ui_you_have_listened_for_minutes_today_and_reached_your_daily, (plan.dailyLimitMinutes).toString()),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = localizedStringResource(R.string.ui_you_have_listened_for_minutes_without_a_break_a_break, (state.sessionRemainingMinutes.coerceAtLeast(0) + (plan.sessionLimitMinutes - state.sessionRemainingMinutes.coerceAtLeast(0))).toString(), (plan.restMinutes).toString()),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = localizedStringResource(R.string.ui_today_min, (state.todayListeningMs / 60_000).toString(), (plan.dailyLimitMinutes).toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRestConfirm) {
                Text(
                    if (isDailyExhausted) localizedStringResource(R.string.ui_ok) else localizedStringResource(R.string.ui_take_a_break),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (!isDailyExhausted) {
                TextButton(onClick = onContinueListening) {
                    Text(localizedStringResource(R.string.ui_keep_listening), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            text = localizedStringResource(R.string.ui_gender),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenderChip(
                label = AppLocaleManager.getString(R.string.ui_male),
                selected = selectedGender == Gender.MALE,
                onClick = { onGenderSelected(Gender.MALE) },
                modifier = Modifier.weight(1f)
            )
            GenderChip(
                label = AppLocaleManager.getString(R.string.ui_female),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdaySelector(
    birthdayEpochDay: Long,
    age: Int,
    onBirthdaySelected: (Long) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = birthdayEpochDay * 86_400_000L,
        yearRange = 1940..LocalDate.now().year
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = localizedStringResource(R.string.ui_birthday),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable { showDatePicker = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = formatBirthday(birthdayEpochDay),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = localizedStringResource(R.string.ui_age, (age).toString()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = localizedStringResource(R.string.ui_choose_birthday),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        AssistantText(
            text = AppLocaleManager.getString(R.string.ui_your_birthday_determines_your_age_and_listening_plan)
        )
    }

    if (showDatePicker) {
        // 使用 decorFitsSystemWindows=false，键盘弹出/收起时窗口位置不重算，避免切换闪屏
        Dialog(
            onDismissRequest = { showDatePicker = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CjkDatePicker(state = datePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDatePicker = false }) { Text(localizedStringResource(R.string.ui_residue_cancel)) }
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let {
                                    onBirthdaySelected(it / 86_400_000L)
                                }
                                showDatePicker = false
                            }
                        ) { Text(localizedStringResource(R.string.ui_residue_ok)) }
                    }
                }
            }
        }
    }
}

/**
 * 用系统默认字体渲染 DatePicker，规避 Google Sans 可变字体缺少 CJK 周几字形、
 * 导致星期几只显示成"星"的问题。字体仅作用于本 DatePicker 上下文。
 */
@Composable
private fun CjkDatePicker(state: androidx.compose.material3.DatePickerState) {
    val theme = MaterialTheme
    MaterialTheme(
        typography = Typography(
            titleLarge = theme.typography.titleLarge.copy(fontFamily = FontFamily.Default),
            headlineMedium = theme.typography.titleLarge.copy(fontFamily = FontFamily.Default),
            bodyLarge = theme.typography.bodyLarge.copy(fontFamily = FontFamily.Default),
            bodyMedium = theme.typography.bodyMedium.copy(fontFamily = FontFamily.Default),
            bodySmall = theme.typography.bodySmall.copy(fontFamily = FontFamily.Default),
            labelLarge = theme.typography.labelLarge.copy(fontFamily = FontFamily.Default),
            labelMedium = theme.typography.labelMedium.copy(fontFamily = FontFamily.Default),
            labelSmall = theme.typography.labelSmall.copy(fontFamily = FontFamily.Default)
        )
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun AssistantText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatBirthday(epochDay: Long): String {
    return try {
        val locale = AppLocaleManager.currentLocale()
        val formatter = if (locale.language == "en") {
            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(locale)
        } else {
            java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日")
        }
        LocalDate.ofEpochDay(epochDay).format(
            formatter
        )
    } catch (_: Exception) {
        "--"
    }
}

private fun ageFromEpochDay(epochDay: Long): Int {
    return try {
        Period.between(LocalDate.ofEpochDay(epochDay), LocalDate.now()).years
    } catch (_: Exception) {
        18
    }
}

private fun defaultBirthdayEpochDay(age: Int): Long {
    return try {
        LocalDate.now().minusYears(age.toLong()).toEpochDay()
    } catch (_: Exception) {
        LocalDate.of(2006, 1, 1).toEpochDay()
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
                text = localizedStringResource(R.string.ui_daily_listening_plan),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            PlanItem(label = AppLocaleManager.getString(R.string.ui_maximum_session_length), value = AppLocaleManager.getString(R.string.ui_min, (plan.sessionLimitMinutes).toString()))
            PlanItem(label = AppLocaleManager.getString(R.string.ui_break_length), value = AppLocaleManager.getString(R.string.ui_min, (plan.restMinutes).toString()))
            PlanItem(label = AppLocaleManager.getString(R.string.ui_daily_limit), value = AppLocaleManager.getString(R.string.ui_min, (plan.dailyLimitMinutes).toString()))
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
