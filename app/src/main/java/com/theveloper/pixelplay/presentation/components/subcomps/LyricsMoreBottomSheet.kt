package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import com.theveloper.pixelplay.ui.theme.LyricsFontDisplayNames
import com.theveloper.pixelplay.ui.theme.resolveLyricsFontFamily
import com.theveloper.pixelplay.ui.theme.isCustomFontKey
import com.theveloper.pixelplay.ui.theme.customFontDisplayName
import com.theveloper.pixelplay.ui.theme.listCustomFonts
import com.theveloper.pixelplay.ui.theme.deleteCustomFont
import com.theveloper.pixelplay.ui.theme.CUSTOM_FONT_PREFIX
import com.theveloper.pixelplay.presentation.components.player.BottomToggleRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.theveloper.pixelplay.data.preferences.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun LyricsMoreBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    lyrics: Lyrics?,
    showSyncedLyrics: Boolean,
    isSyncControlsVisible: Boolean,
    onSaveLyricsAsLrc: () -> Unit,
    onResetImportedLyrics: () -> Unit,
    onSearchLyricsOnline: () -> Unit,
    onTranslateViaAi: () -> Unit,
    onExplainLyricsViaAi: () -> Unit,
    onToggleSyncControls: () -> Unit,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    lyricsAlignment: String,
    onLyricsAlignmentChange: (String) -> Unit,
    hasTranslatedLyrics: Boolean,
    hasRomanizedLyrics: Boolean,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowRomanizationChange: (Boolean) -> Unit,
    lyricsFontSize: String,
    onLyricsFontSizeChange: (String) -> Unit,
    lyricsFontFamily: String,
    onLyricsFontFamilyChange: (String) -> Unit,
    onImportCustomFont: () -> Unit,
    immersiveLyricsEnabled: Boolean,
    // BottomToggleRow params
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    // Colors
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentColor: Color = MaterialTheme.colorScheme.onPrimary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onTertiaryColor: Color = MaterialTheme.colorScheme.onTertiary
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showResetDialog by remember { mutableStateOf(false) }

    // 首次打开歌词页面时，提示可长按删除自定义字体
    val hintContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFontDeleteHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val alreadyShown = hintContext.dataStore.data.first()[booleanPreferencesKey("font_delete_hint_shown")] == true
        if (!alreadyShown) {
            showFontDeleteHint = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets(top = 0, bottom = 0) }
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                //.heightIn(max = screenHeight * 0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navigationBarsPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // No Title - "Expressive" relies on visual grouping

            val itemBackgroundColor = contentColor.copy(alpha = 0.08f)

            // Lyrics Actions Group
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 6.dp),
                    text = stringResource(R.string.lyrics),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                )
                 // Save lyrics to .lrc
                if (lyrics != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.save_lyrics_dialog_title).substringBefore("?")) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.outline_save_24),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onSaveLyricsAsLrc()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                // Translate via AI
                if (lyrics != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.ai_translate_via_ai)) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onTranslateViaAi()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                // Search lyrics online
                val onlineSearchShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.search_lyrics_online)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(onlineSearchShape)
                        .background(itemBackgroundColor)
                        .clickable {
                            onDismissRequest()
                            onSearchLyricsOnline()
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = contentColor,
                        leadingIconColor = contentColor
                    )
                )

                // Reset imported lyrics
                val resetShape = if (lyrics != null) {
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                } else {
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.reset_imported_lyrics)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_restart_alt_24),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(resetShape)
                        .background(itemBackgroundColor)
                        .clickable {
                            showResetDialog = true
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = contentColor,
                        leadingIconColor = contentColor
                    )
                )
            }

            if (showResetDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.lyrics_more_dialog_reset_title)) },
                    text = { Text(stringResource(R.string.lyrics_more_dialog_reset_message)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showResetDialog = false
                                onDismissRequest()
                                onResetImportedLyrics()
                            }
                        ) {
                            Text(stringResource(R.string.action_reset), color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showResetDialog = false }
                        ) {
                            Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            }

            // Appearance Group
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 6.dp),
                    text = stringResource(R.string.lyrics_more_appearance),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                 )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(itemBackgroundColor)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_more_alignment),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "left",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("left") },
                            imageVector = Icons.AutoMirrored.Rounded.FormatAlignLeft,
                            contentDesc = stringResource(R.string.cd_lyrics_align_left)
                        )

                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "center",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("center") },
                            imageVector = Icons.Rounded.FormatAlignCenter,
                            contentDesc = stringResource(R.string.cd_lyrics_align_center)
                        )

                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = lyricsAlignment == "right",
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = contentColor.copy(alpha = 0.78f),
                            activeCornerRadius = 50.dp,
                            onClick = { onLyricsAlignmentChange("right") },
                            imageVector = Icons.AutoMirrored.Rounded.FormatAlignRight,
                            contentDesc = stringResource(R.string.cd_lyrics_align_right)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.lyrics_more_font_size),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sizes = listOf("SMALL", "DEFAULT", "LARGE", "EXTRA_LARGE")
                        val sizeLabels = listOf("S", "M", "L", "XL")

                        sizes.forEachIndexed { index, size ->
                            ToggleSegmentButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                active = lyricsFontSize == size,
                                activeColor = accentColor,
                                inactiveColor = containerColor,
                                activeContentColor = onAccentColor,
                                inactiveContentColor = contentColor.copy(alpha = 0.78f),
                                activeCornerRadius = 50.dp,
                                onClick = { onLyricsFontSizeChange(size) },
                                text = sizeLabels[index]
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.lyrics_more_font),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    // 构建字体选项列表：预定义 + 自定义字体
                    val sheetContext = LocalContext.current
                    var customFontsRefreshTick by remember { mutableStateOf(0) }
                    // 直接订阅 DataStore 的字体值：ModalBottomSheet 渲染在独立 window 中，
                    // 外层 lyricsFontFamily 参数变化可能不会触发内部重组，导致导入字体后
                    // 列表不刷新。改为监听 DataStore 自身变化即可在导入后立即刷新。
                    val currentFontFamily by sheetContext.dataStore.data
                        .map { it[stringPreferencesKey("lyrics_font_family")] ?: "DEFAULT" }
                        .distinctUntilChanged()
                        .collectAsState(initial = "DEFAULT")
                    val customFonts = remember(customFontsRefreshTick, currentFontFamily) {
                        listCustomFonts(sheetContext).map { "$CUSTOM_FONT_PREFIX$it" }
                    }
                    val predefinedFonts = LyricsFontDisplayNames.keys.toList()
                    val allFontFamilies = predefinedFonts + customFonts

                    fun displayName(key: String): String =
                        if (isCustomFontKey(key)) customFontDisplayName(key)
                        else LyricsFontDisplayNames[key] ?: key

                    // 长按删除自定义字体；删除当前选中字体时回退到主题默认
                    val onFontLongClick: (String) -> Unit = { key ->
                        if (isCustomFontKey(key)) {
                            deleteCustomFont(sheetContext, key)
                            if (lyricsFontFamily == key) {
                                onLyricsFontFamilyChange("DEFAULT")
                            }
                            customFontsRefreshTick++
                        }
                    }

                    // 字体按钮流式布局：每行最多 3 个，超过自动换行
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 3,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allFontFamilies.forEach { family ->
                            FontOptionButton(
                                text = displayName(family),
                                active = lyricsFontFamily == family,
                                deletable = isCustomFontKey(family),
                                activeColor = accentColor,
                                inactiveColor = containerColor,
                                activeContentColor = onAccentColor,
                                inactiveContentColor = contentColor.copy(alpha = 0.78f),
                                onClick = { onLyricsFontFamilyChange(family) },
                                onDelete = { onFontLongClick(family) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 导入字体按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToggleSegmentButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            active = false,
                            activeColor = accentColor,
                            inactiveColor = containerColor,
                            activeContentColor = onAccentColor,
                            inactiveContentColor = accentColor,
                            activeCornerRadius = 50.dp,
                            onClick = onImportCustomFont,
                            text = "＋ 导入字体"
                        )
                    }
                }
            }

            // Control Settings Group
            val isSyncVisible = showSyncedLyrics
            val isRomanizationVisible = hasRomanizedLyrics
            val isTranslationVisible = hasTranslatedLyrics
            val isImmersiveVisible = showSyncedLyrics && immersiveLyricsEnabled
            val isKeepScreenOnVisible = true

            if (isSyncVisible || isRomanizationVisible || isTranslationVisible || isKeepScreenOnVisible) {
                // Determine first and last items for rounding
                val isRomanizationFirst = isRomanizationVisible && !isSyncVisible
                val isTranslationFirst = isTranslationVisible && !isSyncVisible && !isRomanizationVisible

                val isSyncLast = isSyncVisible && !isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isRomanizationLast = isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isTranslationLast = isTranslationVisible && !isImmersiveVisible && !isKeepScreenOnVisible
                val isImmersiveLast = isImmersiveVisible && !isKeepScreenOnVisible

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier
                            .padding(start = 6.dp, bottom = 6.dp),
                        text = stringResource(R.string.lyrics_more_controls),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyLargeEmphasized
                    )

                    if (isSyncVisible) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (isSyncControlsVisible) {
                                        stringResource(R.string.lyrics_more_hide_sync_controls)
                                    } else {
                                        stringResource(R.string.lyrics_more_adjust_sync)
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isSyncLast) 24.dp else 8.dp,
                                        bottomEnd = if (isSyncLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable {
                                    onDismissRequest()
                                    onToggleSyncControls()
                                },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isRomanizationVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_more_show_romanization)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Abc,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = showRomanization,
                                    onCheckedChange = onShowRomanizationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isRomanizationFirst) 18.dp else 8.dp,
                                        topEnd = if (isRomanizationFirst) 18.dp else 8.dp,
                                        bottomStart = if (isRomanizationLast) 24.dp else 8.dp,
                                        bottomEnd = if (isRomanizationLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onShowRomanizationChange(!showRomanization) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isTranslationVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_more_show_translations)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = showTranslation,
                                    onCheckedChange = onShowTranslationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isTranslationFirst) 18.dp else 8.dp,
                                        topEnd = if (isTranslationFirst) 18.dp else 8.dp,
                                        bottomStart = if (isTranslationLast) 24.dp else 8.dp,
                                        bottomEnd = if (isTranslationLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onShowTranslationChange(!showTranslation) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    // Immersive Mode Toggle
                    if (isImmersiveVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_more_disable_immersive_once)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    modifier = Modifier,
                                    checked = isImmersiveTemporarilyDisabled,
                                    onCheckedChange = {
                                        onSetImmersiveTemporarilyDisabled(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                        bottomStart = if (isImmersiveLast) 24.dp else 8.dp,
                                        bottomEnd = if (isImmersiveLast) 24.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor),
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    // Keep Screen On Toggle
                    if (isKeepScreenOnVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_more_keep_screen_on)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.BrightnessHigh,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = keepScreenOn,
                                    onCheckedChange = onKeepScreenOnChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                        bottomStart = 24.dp,
                                        bottomEnd = 24.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onKeepScreenOnChange(!keepScreenOn) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Playback Options
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    //.background(contentColor.copy(alpha = 0.08f))
                    .padding(vertical = 0.dp, horizontal = 0.dp)
            ) {
                 BottomToggleRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .padding(horizontal = 20.dp),
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    }

    if (showFontDeleteHint) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showFontDeleteHint = false
                scope.launch { hintContext.dataStore.edit { it[booleanPreferencesKey("font_delete_hint_shown")] = true } }
            },
            title = { Text("长按删除字体") },
            text = { Text("普通点击选择字体，长按自定义字体可将其删除。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showFontDeleteHint = false
                        scope.launch { hintContext.dataStore.edit { it[booleanPreferencesKey("font_delete_hint_shown")] = true } }
                    }
                ) {
                    Text("知道了")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontOptionButton(
    text: String,
    active: Boolean,
    deletable: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    activeContentColor: Color,
    inactiveContentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pressProgress = remember { Animatable(0f) }
    val dangerColor = MaterialTheme.colorScheme.error
    val targetBg = if (active) activeColor else inactiveColor
    val bgColor = lerp(targetBg, dangerColor, pressProgress.value)
    val targetContent = if (active) activeContentColor else inactiveContentColor
    val textColor = lerp(targetContent, Color.White, pressProgress.value)
    val corner by animateDpAsState(
        targetValue = if (active) 50.dp else 12.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "FontOptionCorner"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(bgColor)
            .pointerInput(deletable) {
                if (deletable) {
                    detectTapGestures(
                        onPress = {
                            // 长按逐渐变红：按下即开始向 error 色过渡，提示可删除
                            pressProgress.animateTo(1f, tween(durationMillis = 600))
                            tryAwaitRelease()
                            pressProgress.animateTo(0f, tween(durationMillis = 200))
                        },
                        onTap = { onClick() },
                        onLongPress = { onDelete() }
                    )
                } else {
                    detectTapGestures(onTap = { onClick() })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            softWrap = true,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
