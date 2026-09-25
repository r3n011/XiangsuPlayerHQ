package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.theveloper.pixelplay.ui.theme.PixelPlayStatusBarStyle
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.flow.map

@Composable
fun CollapsibleCommonTopBar(
    title: String,
    collapseFraction: Float,
    headerHeight: Dp,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsedTitleStartPadding: Dp = 68.dp,
    expandedTitleStartPadding: Dp = 20.dp,
    collapsedTitleEndPadding: Dp = 24.dp,
    expandedTitleEndPadding: Dp = 24.dp,
    containerHeightRange: Pair<Dp, Dp> = 88.dp to 56.dp,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    titleScaleRange: Pair<Float, Float> = 1.2f to 0.8f,
    titleFontSizeRange: Pair<TextUnit, TextUnit>? = null,
    maxLines: Int = 1,
    collapsedSubtitleMaxLines: Int = 1,
    expandedSubtitleMaxLines: Int = 1,
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fadeSubtitleOnCollapse: Boolean = true,
    enableCollapsedTitleWidthCompression: Boolean = true,
    enableExpandedTitleWidthCompression: Boolean = true,
    titleWidthCompressionThreshold: Dp? = null,
    titleMinWidthAxis: Float = 78f,
    syncStatusBarWithContainer: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showBackButton: Boolean = true
) {
    // Logic from GenreDetailScreen:
    // solidAlpha goes from 0 to 1 as collapseFraction goes from 0 to 0.5 (approx).
    // Actually GenreDetailScreen uses: (collapseFraction * 2f).coerceIn(0f, 1f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)

    // ⚡ 顶栏风格：同时满足「开启新版顶栏」且未开启「禁用模糊」时，使用首页同款渐进模糊遮罩
    //   + 收起标题胶囊；否则回退为原有纯色遮罩样式。
    val context = androidx.compose.ui.platform.LocalContext.current
    val blurPrefs by remember(context) {
        context.dataStore.data
            .map { prefs ->
                (prefs[booleanPreferencesKey("disable_blur_all_over")] ?: false) to
                    (prefs[booleanPreferencesKey("use_new_top_bar")] ?: true)
            }
    }.collectAsStateWithLifecycle(initialValue = false to true)
    val blurStyleEnabled = blurPrefs.second && !blurPrefs.first

    // ⚡ 胶囊会在标题文字左侧外扩 13dp：启用胶囊时加大收起标题起始 padding，
    //   保证胶囊与返回按钮（start 12dp + 40dp，右缘约 52dp）之间保有 ≥14dp 间隔
    val effectiveCollapsedTitleStartPadding = if (blurStyleEnabled) {
        (collapsedTitleStartPadding + 14.dp).coerceAtLeast(80.dp)
    } else {
        collapsedTitleStartPadding
    }

    val backgroundColor = when {
        containerColor != null -> containerColor
        blurStyleEnabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = solidAlpha * 0.35f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = solidAlpha)
    }
    val statusBarFallbackColor = backgroundColor.compositeOver(MaterialTheme.colorScheme.surface)

    if (syncStatusBarWithContainer) {
        PixelPlayStatusBarStyle(color = statusBarFallbackColor)
    }
    // We can also fade the content color if we want, but usually onSurface is fine.
    // GenreDetail interpolates content color, but for standard screens onSurface is usually correct for both states
    // (transparent surface vs surfaceContainer).

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(backgroundColor)
            .then(
                // ⚡ 渐进模糊遮罩（与 HomeGradientTopBar 同款）：随收起进度淡入
                if (blurStyleEnabled && solidAlpha > 0.02f) {
                    Modifier.hazeEffect(
                        state = MainActivity.LocalHazeState.current,
                        style = HazeMaterials.regular()
                    ) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f
                        )
                    }
                } else {
                    Modifier
                }
            )
            .zIndex(5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (showBackButton) {
                FilledIconButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // 布局按旧版：按钮贴顶 4dp
                        .padding(start = 12.dp, top = 4.dp)
                        .zIndex(1f),
                    onClick = onBackClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.auth_cd_back)
                    )
                }
            }

            // Actions (e.g. Equalizer toggle)
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp), // 与返回按钮同高（旧版布局）
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }

            ExpressiveTopBarContent(
                title = title,
                collapseFraction = collapseFraction,
                modifier = Modifier.fillMaxSize(),
                // 副标题照常传入（旧版布局）；收起时由 fadeSubtitleOnCollapse 自动淡出
                subtitle = subtitle,
                collapsedTitleStartPadding = effectiveCollapsedTitleStartPadding,
                expandedTitleStartPadding = expandedTitleStartPadding,
                collapsedTitleEndPadding = collapsedTitleEndPadding,
                expandedTitleEndPadding = expandedTitleEndPadding,
                containerHeightRange = containerHeightRange,
                titleStyle = titleStyle,
                titleScaleRange = titleScaleRange,
                titleFontSizeRange = titleFontSizeRange,
                maxLines = maxLines,
                collapsedSubtitleMaxLines = collapsedSubtitleMaxLines,
                expandedSubtitleMaxLines = expandedSubtitleMaxLines,
                contentColor = contentColor,
                subtitleColor = subtitleColor,
                fadeSubtitleOnCollapse = fadeSubtitleOnCollapse,
                enableCollapsedTitleWidthCompression = enableCollapsedTitleWidthCompression,
                enableExpandedTitleWidthCompression = enableExpandedTitleWidthCompression,
                titleWidthCompressionThreshold = titleWidthCompressionThreshold,
                titleMinWidthAxis = titleMinWidthAxis,
                supportingContent = supportingContent,
                collapsedTitleCapsule = blurStyleEnabled
                // 垂直布局按旧版（ExpressiveTopBarContent 默认 -1f=贴顶），不传即走旧版；
                // 新样式与旧版的唯一差异：收起标题胶囊 + 左侧加大起始 padding 避开返回按钮
            )
        }
    }
}
