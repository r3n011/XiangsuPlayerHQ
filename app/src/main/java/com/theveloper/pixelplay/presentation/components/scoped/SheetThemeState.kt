package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState

/**
 * Theme state for the player sheet.
 *
 * Expansion-dependent values ([miniAlpha], elevation) are **no longer** included here.
 * They are computed inline in the consuming composable's `graphicsLayer` / `derivedStateOf`,
 * reading directly from the [Animatable] expansion fraction during the draw phase.
 * This eliminates per-frame recomposition that the old [Transition]-based approach caused.
 */
internal fun resolvePlayerSheetTargetScheme(
    isAlbumArtTheme: Boolean,
    hasAlbumArt: Boolean,
    currentSongActiveScheme: ColorScheme?,
    lastAlbumScheme: ColorScheme?,
    systemColorScheme: ColorScheme
): ColorScheme {
    return if (!isAlbumArtTheme || !hasAlbumArt) {
        systemColorScheme
    } else {
        currentSongActiveScheme ?: lastAlbumScheme ?: systemColorScheme
    }
}

internal data class SheetThemeState(
    val albumColorScheme: ColorScheme,
    val miniPlayerScheme: ColorScheme,
    val isPreparingPlayback: Boolean,
    val miniReadyAlpha: Float,
    val miniAppearScale: Float,
    val playerAreaBackground: Color
)

/**
 * ⚡ 简化版播放器主题状态。
 *   核心思路：
 *   1. activePlayerSchemePair 是 ThemeStateHolder 维护的当前有效颜色方案（原子更新）
 *   2. 直接使用 activePlayerScheme 作为 miniPlayerScheme / albumColorScheme
 *   3. 如果颜色方案为空，回落到系统色
 *   4. 在切歌时，ThemeStateHolder 需要保证 activePlayerSchemePair 从不为空（保持旧值）
 */
@Composable
internal fun rememberSheetThemeState(
    activePlayerSchemePair: ColorSchemePair?,
    isDarkTheme: Boolean,
    playerThemePreference: String,
    currentSong: Song?,
    themedAlbumArtUri: String?,
    preparingSongId: String?,
    systemColorScheme: ColorScheme,
    currentSheetState: PlayerSheetState,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>
): SheetThemeState {
    val isThemedTheme = playerThemePreference == ThemePreference.ALBUM_ART ||
            playerThemePreference == ThemePreference.CUSTOM_PALETTE
    val hasAlbumArt = !currentSong?.albumArtUriString.isNullOrBlank()

    // ⚡ 从 activePlayerSchemePair 派生颜色方案（根据亮色/暗色主题）
    val activePlayerScheme = remember(activePlayerSchemePair, isDarkTheme) {
        activePlayerSchemePair?.let { if (isDarkTheme) it.dark else it.light }
    }

    // ⚡ currentSongActiveScheme:
    //   - 自定义调色盘：直接套用 activePlayerScheme，不校验封面 URI
    //   - 封面取色：只有 activePlayerScheme 和 currentSong 的 albumArtUri 匹配时才非 null
    //     用于切歌过渡期间保持旧颜色方案
    val currentSongActiveScheme = remember(
        activePlayerScheme,
        playerThemePreference,
        currentSong?.albumArtUriString,
        themedAlbumArtUri
    ) {
        if (activePlayerScheme == null) {
            null
        } else if (playerThemePreference == ThemePreference.CUSTOM_PALETTE) {
            activePlayerScheme
        } else if (hasAlbumArt && currentSong.albumArtUriString == themedAlbumArtUri) {
            activePlayerScheme
        } else {
            null
        }
    }

    // ⚡ lastAlbumScheme: 始终持有最近一次有效的歌曲颜色方案（不依赖 currentSongActiveScheme）
    //   当 activePlayerScheme 变化时，我们检查它是否是"有效的"（URI匹配）。
    //   如果有效，更新 lastAlbumScheme。否则保持旧值。
    var lastAlbumScheme by remember { mutableStateOf<ColorScheme?>(null) }
    LaunchedEffect(currentSongActiveScheme) {
        if (currentSongActiveScheme != null) {
            lastAlbumScheme = currentSongActiveScheme
        }
    }

    val isPreparingPlayback = remember(preparingSongId, currentSong?.id) {
        preparingSongId != null && preparingSongId == currentSong?.id
    }

    // ⚡ 最终颜色方案的选择逻辑：
    //   - 如果不是主题模式（封面取色/自定义调色盘）/ 歌曲无封面 → 系统色
    //   - 如果 currentSongActiveScheme 非 null → 用它
    //   - 否则（切歌过渡期间）→ 用 lastAlbumScheme
    //   - 最后回落系统色
    val targetAlbumColorScheme = if (!isThemedTheme || !hasAlbumArt) {
        systemColorScheme
    } else {
        currentSongActiveScheme ?: lastAlbumScheme ?: systemColorScheme
    }

    var animFromScheme by remember { mutableStateOf<ColorScheme?>(null) }
    var animToScheme by remember { mutableStateOf<ColorScheme?>(null) }
    val colorProgress = remember { Animatable(1f) }
    var pendingTargetScheme by remember { mutableStateOf<ColorScheme?>(null) }

    LaunchedEffect(targetAlbumColorScheme, currentSheetState, playerContentExpansionFraction) {
        val isExpanding = currentSheetState == PlayerSheetState.EXPANDED && 
            playerContentExpansionFraction.value < 0.99f
        
        if (isExpanding) {
            pendingTargetScheme = targetAlbumColorScheme
            return@LaunchedEffect
        }
        
        pendingTargetScheme = null
        val currentAnimated = animToScheme ?: targetAlbumColorScheme
        if (currentAnimated != targetAlbumColorScheme) {
            animFromScheme = currentAnimated
            animToScheme = targetAlbumColorScheme
            colorProgress.snapTo(0f)
            colorProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
        } else if (animToScheme == null) {
            animToScheme = targetAlbumColorScheme
            animFromScheme = targetAlbumColorScheme
        }
    }
    
    LaunchedEffect(currentSheetState, playerContentExpansionFraction) {
        val isFullyExpanded = currentSheetState == PlayerSheetState.EXPANDED && 
            playerContentExpansionFraction.value >= 0.99f
        
        if (isFullyExpanded && pendingTargetScheme != null) {
            val pending = pendingTargetScheme!!
            pendingTargetScheme = null
            val currentAnimated = animToScheme ?: pending
            if (currentAnimated != pending) {
                animFromScheme = currentAnimated
                animToScheme = pending
                colorProgress.snapTo(0f)
                colorProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    // 使用 derivedStateOf 缓存 lerp 结果，量化进度到 5% 步进，减少 ColorScheme 创建频率
    val animatedScheme by remember(animFromScheme, animToScheme) {
        derivedStateOf {
            val from = animFromScheme
            val to = animToScheme
            val progress = colorProgress.value
            if (from != null && to != null && progress < 1f) {
                val quantizedProgress = (progress * 20f).toInt() / 20f
                lerpColorScheme(from, to, quantizedProgress)
            } else {
                to ?: targetAlbumColorScheme
            }
        }
    }

    val albumColorScheme = animatedScheme
    val miniPlayerScheme = animatedScheme

    // 播放器的"出现"动画 - 只有当 currentSong 首次出现时才需要动画
    val miniAppearProgress = remember(currentSong) {
        Animatable(if (currentSong != null) 1f else 0f)
    }
    LaunchedEffect(currentSong?.id) {
        if (currentSong != null && miniAppearProgress.value < 1f) {
            miniAppearProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    val miniReadyAlpha = miniAppearProgress.value
    val miniAppearScale = lerp(0.985f, 1f, miniAppearProgress.value)
    val playerAreaBackground = miniPlayerScheme.primaryContainer

    return SheetThemeState(
        albumColorScheme = albumColorScheme,
        miniPlayerScheme = miniPlayerScheme,
        isPreparingPlayback = isPreparingPlayback,
        miniReadyAlpha = miniReadyAlpha,
        miniAppearScale = miniAppearScale,
        playerAreaBackground = playerAreaBackground
    )
}

/**
 * DEPRECATED: Causes widespread recomposition by returning a new ColorScheme object on every frame.
 * Now we return the target scheme directly and animate colors at the component level.
 */
@Composable
private fun rememberBatchAnimatedColorScheme(target: ColorScheme): ColorScheme = target

/**
 * Manually interpolates every field of two [ColorScheme]s by [t] ∈ [0, 1].
 * Called once per animation frame (inside [derivedStateOf]) — O(29) lerp ops, negligible CPU.
 */
private fun lerpColorScheme(from: ColorScheme, to: ColorScheme, t: Float): ColorScheme =
    to.copy(
        primary                = lerp(from.primary, to.primary, t),
        onPrimary              = lerp(from.onPrimary, to.onPrimary, t),
        primaryContainer       = lerp(from.primaryContainer, to.primaryContainer, t),
        onPrimaryContainer     = lerp(from.onPrimaryContainer, to.onPrimaryContainer, t),
        secondary              = lerp(from.secondary, to.secondary, t),
        onSecondary            = lerp(from.onSecondary, to.onSecondary, t),
        secondaryContainer     = lerp(from.secondaryContainer, to.secondaryContainer, t),
        onSecondaryContainer   = lerp(from.onSecondaryContainer, to.onSecondaryContainer, t),
        tertiary               = lerp(from.tertiary, to.tertiary, t),
        onTertiary             = lerp(from.onTertiary, to.onTertiary, t),
        tertiaryContainer      = lerp(from.tertiaryContainer, to.tertiaryContainer, t),
        onTertiaryContainer    = lerp(from.onTertiaryContainer, to.onTertiaryContainer, t),
        surface                = lerp(from.surface, to.surface, t),
        onSurface              = lerp(from.onSurface, to.onSurface, t),
        surfaceVariant         = lerp(from.surfaceVariant, to.surfaceVariant, t),
        onSurfaceVariant       = lerp(from.onSurfaceVariant, to.onSurfaceVariant, t),
        background             = lerp(from.background, to.background, t),
        onBackground           = lerp(from.onBackground, to.onBackground, t),
        inverseSurface         = lerp(from.inverseSurface, to.inverseSurface, t),
        inverseOnSurface       = lerp(from.inverseOnSurface, to.inverseOnSurface, t),
        inversePrimary         = lerp(from.inversePrimary, to.inversePrimary, t),
        surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, t),
        surfaceContainerLow    = lerp(from.surfaceContainerLow, to.surfaceContainerLow, t),
        surfaceContainer       = lerp(from.surfaceContainer, to.surfaceContainer, t),
        surfaceContainerHigh   = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, t),
        surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, t),
        outline                = lerp(from.outline, to.outline, t),
        outlineVariant         = lerp(from.outlineVariant, to.outlineVariant, t),
        surfaceTint            = lerp(from.surfaceTint, to.surfaceTint, t),
        error                  = lerp(from.error, to.error, t),
        onError                = lerp(from.onError, to.onError, t),
        errorContainer         = lerp(from.errorContainer, to.errorContainer, t),
        onErrorContainer       = lerp(from.onErrorContainer, to.onErrorContainer, t),
        scrim                  = lerp(from.scrim, to.scrim, t),
        surfaceBright          = lerp(from.surfaceBright, to.surfaceBright, t),
        surfaceDim             = lerp(from.surfaceDim, to.surfaceDim, t),
    )
