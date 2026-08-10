package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.viewmodel.LyricsRefreshProgress
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback

@Composable
fun SettingsSection(title: String, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
fun SettingsItem(
        title: String,
        subtitle: String,
        leadingIcon: @Composable () -> Unit,
        trailingIcon: @Composable () -> Unit = {},
        onClick: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClick)
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center
            ) { leadingIcon() }

            Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun SwitchSettingItem(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        leadingIcon: @Composable (() -> Unit)? = null,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null
) {
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                )
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Box(
                        modifier = Modifier.padding(end = 4.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }
            }

            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    if (enabled) {
                        performAppCompatHapticFeedback(
                            view,
                            appHapticsConfig,
                            HapticFeedbackConstantsCompat.GESTURE_START
                        )
                        onCheckedChange(newValue)
                    }
                },
                enabled = enabled,
                thumbContent = {
                    AnimatedContent(
                        targetState = checked,
                        transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                        label = "switch_thumb_icon"
                    ) { isChecked ->
                        Icon(
                            imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorItem(
        label: String,
        description: String,
        options: Map<String, String>,
        selectedKey: String,
        onSelectionChanged: (String) -> Unit,
        leadingIcon: @Composable () -> Unit,
        optionBadge: (@Composable (optionKey: String) -> Unit)? = null
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey

    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        showSheet = true
                    }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Selected Value Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                             text = selectedOption,
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.primary,
                             fontWeight = FontWeight.Bold,
                             modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall, // Larger header
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options.entries.toList()) { (key, optionLabel) ->
                        val isSelected = key == selectedKey
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        
                        Surface(
                            onClick = {
                                onSelectionChanged(key)
                                showSheet = false
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = containerColor,
                            modifier = Modifier.fillMaxWidth().height(72.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optionLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                optionBadge?.invoke(key)

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.presentation_batch_f_cd_selected),
                                        tint = contentColor
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

/**
 * ⚡ 应用级调色盘选择器（独立于播放器主题）：
 *   - 预设色板（圆角色块，选中放大 + 对勾）
 *   - 自定义取色入口（打开底部弹窗，色相/饱和度/亮度三滑杆）
 *   - 效果预览条：实时展示所选种子色生成的整套配色
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppPalettePicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 6
        ) {
            PresetPaletteColors.forEach { color ->
                PaletteColorSwatch(
                    color = color,
                    selected = color == selectedColor,
                    onClick = { onColorSelected(color) }
                )
            }
            CustomColorEntryTile(onClick = { showCustomPicker = true })
        }

        Spacer(modifier = Modifier.height(14.dp))

        PaletteSchemePreview(seedColor = selectedColor)
    }

    if (showCustomPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCustomPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            CustomColorPickerContent(
                initialColor = selectedColor,
                onConfirm = { color ->
                    onColorSelected(color)
                    showCustomPicker = false
                },
                onDismiss = { showCustomPicker = false }
            )
        }
    }
}

private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val argb = android.graphics.Color.HSVToColor(
        floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    )
    return Color(argb)
}

private fun Color.hexLabel(): String =
    "#" + Integer.toHexString(toArgb() and 0xFFFFFF).uppercase().padStart(6, '0')

// 色相 0°~360° 全色谱（供色相滑杆/自定义色块渐变使用）
private val RainbowHueColors: List<Color> = List(13) { i -> hsvToColor(i * 30f, 1f, 1f) }

private val PresetPaletteColors: List<Color> = listOf(
    Color(0xFFF44336), // Red
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFF673AB7), // Deep Purple
    Color(0xFF3F51B5), // Indigo
    Color(0xFF2196F3), // Blue
    Color(0xFF03A9F4), // Light Blue
    Color(0xFF00BCD4), // Cyan
    Color(0xFF009688), // Teal
    Color(0xFF4CAF50), // Green
    Color(0xFFFFEB3B), // Yellow
    Color(0xFFFF9800), // Orange
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF795548), // Brown
    Color(0xFF607D8B)  // Blue Grey
)

@Composable
private fun PaletteColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "paletteSwatchScale"
    )
    val checkTint = if (color.luminance() > 0.55f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CustomColorEntryTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.sweepGradient(RainbowHueColors))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** 效果预览：所选种子色生成整套配色（跟随当前明暗主题展示对应配色） */
@Composable
private fun PaletteSchemePreview(seedColor: Color) {
    val dark = isSystemInDarkTheme()
    val scheme = remember(seedColor) { generateColorSchemeFromSeed(seedColor) }
    val cs = if (dark) scheme.dark else scheme.light

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.setcat_palette_preview_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = seedColor.hexLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SchemePill(cs.primary, cs.onPrimary, Modifier.weight(1f))
                SchemePill(cs.secondary, cs.onSecondary, Modifier.weight(1f))
                SchemePill(cs.tertiary, cs.onTertiary, Modifier.weight(1f))
                SchemePill(cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f))
                SchemePill(cs.surfaceContainerHigh, cs.onSurface, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SchemePill(bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(fg)
        )
    }
}

@Composable
private fun CustomColorPickerContent(
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val currentColor = hsvToColor(hue, sat, value)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 36.dp)
    ) {
        Text(
            text = stringResource(R.string.setcat_palette_custom_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = currentColor.hexLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.setcat_palette_live_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        ColorGradientSlider(
            label = stringResource(R.string.setcat_palette_hue),
            value = hue,
            range = 0f..360f,
            trackColors = RainbowHueColors,
            onValueChange = { hue = it }
        )
        Spacer(modifier = Modifier.height(18.dp))
        ColorGradientSlider(
            label = stringResource(R.string.setcat_palette_saturation),
            value = sat,
            range = 0f..1f,
            trackColors = listOf(Color(0xFF9E9E9E), hsvToColor(hue, 1f, 1f)),
            onValueChange = { sat = it }
        )
        Spacer(modifier = Modifier.height(18.dp))
        ColorGradientSlider(
            label = stringResource(R.string.setcat_palette_brightness),
            value = value,
            range = 0f..1f,
            trackColors = listOf(Color.Black, hsvToColor(hue, sat, 1f)),
            onValueChange = { value = it }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.setcat_palette_cancel))
            }
            Spacer(modifier = Modifier.width(10.dp))
            FilledTonalButton(onClick = { onConfirm(currentColor) }) {
                Text(stringResource(R.string.setcat_palette_confirm))
            }
        }
    }
}

@Composable
private fun ColorGradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    trackColors: List<Color>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(trackColors))
                )
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
fun ExpressiveSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp)) // Large corners for the group
            .background(Color.Transparent),
        //verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
    }
}

@Composable
fun SliderSettingsItem(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: (() -> Unit)? = null,
        valueText: (Float) -> String
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = valueText(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}

@Composable
fun RefreshLibraryItem(
        isSyncing: Boolean,
        syncProgress: SyncProgress,
        activeOperationLabel: String? = null,
        onFullSync: () -> Unit,
        onRebuild: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = stringResource(R.string.presentation_batch_f_refresh_library_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = stringResource(R.string.presentation_batch_f_refresh_library_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // Full Rescan button
            FilledTonalButton(
                    onClick = onFullSync,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TightWrapText(
                        text = stringResource(R.string.presentation_batch_f_full_rescan),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        lineHeight = 22.sp,
                    )
                }
            }
             
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rebuild Database button - full width, destructive action
            OutlinedButton(
                    onClick = onRebuild,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TightWrapText(
                        text = stringResource(R.string.presentation_batch_f_rebuild_database),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        lineHeight = 22.sp,
                    )
                }
            }

            if (isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                val phaseLabel = activeOperationLabel ?: syncPhaseLabel(syncProgress.phase)
                if (syncProgress.hasProgress) {
                    LinearProgressIndicator(
                            progress = { syncProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.presentation_batch_f_sync_progress_detailed,
                                phaseLabel,
                                (syncProgress.progress * 100).toInt(),
                                syncProgress.currentCount,
                                syncProgress.totalCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.presentation_batch_f_sync_progress_indeterminate,
                                phaseLabel
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun syncPhaseLabel(phase: SyncProgress.SyncPhase): String =
        stringResource(
                when (phase) {
                    SyncProgress.SyncPhase.IDLE -> R.string.presentation_batch_f_sync_phase_preparing
                    SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                            R.string.presentation_batch_f_sync_phase_reading_mediastore
                    SyncProgress.SyncPhase.PROCESSING_FILES ->
                            R.string.presentation_batch_f_sync_phase_processing_tracks
                    SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                            R.string.presentation_batch_f_sync_phase_saving_db
                    SyncProgress.SyncPhase.SCANNING_LRC -> R.string.presentation_batch_f_sync_phase_scanning_lrc
                    SyncProgress.SyncPhase.CLEANING_CACHE ->
                            R.string.presentation_batch_f_sync_phase_cleaning_cache
                    SyncProgress.SyncPhase.SYNCING_CLOUD ->
                            R.string.presentation_batch_f_sync_phase_syncing_cloud
                    SyncProgress.SyncPhase.COMPLETING -> R.string.presentation_batch_f_sync_phase_completing
                }
        )

@Composable
fun RefreshLyricsItem(
        isRefreshing: Boolean,
        progress: LyricsRefreshProgress,
        onRefresh: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            painter = painterResource(id = R.drawable.rounded_lyrics_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = stringResource(R.string.presentation_batch_f_refresh_lyrics_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = stringResource(R.string.presentation_batch_f_refresh_lyrics_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        colors =
                                IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = stringResource(R.string.presentation_batch_f_cd_refresh_lyrics),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (isRefreshing && progress.hasProgress) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = stringResource(
                            R.string.presentation_batch_f_refresh_lyrics_processing,
                            progress.currentCount,
                            progress.totalSongs
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionSettingsItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action
            FilledTonalButton(
                onClick = onPrimaryAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(primaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Secondary Action (Optional)
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondaryAction,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
