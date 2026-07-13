package com.theveloper.pixelplay.presentation.focusmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (studyMinutes: Int, breakMinutes: Int) -> Unit
) {
    var studyMinutes by remember { mutableIntStateOf(25) }
    var breakMinutes by remember { mutableIntStateOf(5) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = "专注学习",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "设置你的番茄钟时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))

                // 学习时间设置
                MaterialTimeDurationPicker(
                    title = "学习时间",
                    subtitle = "专注工作的时间",
                    value = studyMinutes,
                    minValue = 5,
                    maxValue = 120,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onValueChange = { studyMinutes = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 休息时间设置
                MaterialTimeDurationPicker(
                    title = "休息时间",
                    subtitle = "放松的时间",
                    value = breakMinutes,
                    minValue = 1,
                    maxValue = 30,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onValueChange = { breakMinutes = it }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Material 3 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    FilledTonalButton(
                        onClick = { onConfirm(studyMinutes, breakMinutes) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "开始专注",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialTimeDurationPicker(
    title: String,
    subtitle: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    accentColor: androidx.compose.ui.graphics.Color,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Material 3 SuggestionChip 显示当前值
            SuggestionChip(
                onClick = { },
                label = {
                    Text(
                        text = "$value 分钟",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = accentColor.copy(alpha = 0.12f)
                ),
                border = null
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Material 3 Slider
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = minValue.toFloat()..maxValue.toFloat(),
            steps = (maxValue - minValue - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.24f)
            )
        )

        // Material 3 快速预设
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = if (title == "学习时间") listOf(15, 25, 45, 60) else listOf(3, 5, 10, 15)
            presets.forEach { preset ->
                val isSelected = value == preset
                // Material 3 SuggestionChip 替代自定义 Box
                SuggestionChip(
                    onClick = { onValueChange(preset) },
                    label = {
                        Text(
                            text = "${preset}m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isSelected) accentColor.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = if (isSelected) accentColor
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = if (isSelected) null else SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
