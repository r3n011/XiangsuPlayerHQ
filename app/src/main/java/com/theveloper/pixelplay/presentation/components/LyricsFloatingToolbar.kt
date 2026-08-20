package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.theveloper.pixelplay.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsFloatingToolbar(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    showSyncedLyrics: Boolean?,
    onShowSyncedLyricsChange: (Boolean) -> Unit,
    hasSyncedLyrics: Boolean,
    onMoreClick: () -> Unit,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    backProgressProvider: () -> Float = { 0f },
    // AI 歌词解析
    isExplainingLyrics: Boolean = false,
    lyricsExplanation: String? = null,
    lyricsExplanationEnabled: Boolean = false,
    onExplainLyricsViaAi: () -> Unit = {},
    onShowExplanation: () -> Unit = {},
) {
    if (showSyncedLyrics == null) return

    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val backInteractionSource = remember { MutableInteractionSource() }
        val isBackPressed by backInteractionSource.collectIsPressedAsState()

        val backPressScale by animateFloatAsState(
            targetValue = if (isBackPressed) 0.82f else 1f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium,
                dampingRatio = Spring.DampingRatioMediumBouncy
            ),
            label = "backPressScale"
        )

        IconButton(
            modifier = Modifier.graphicsLayer {
                val gestureScale = lerp(1f, 0.7f, backProgressProvider())
                val combined = backPressScale * gestureScale
                scaleX = combined
                scaleY = combined
            },
            interactionSource = backInteractionSource,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = backgroundColor,
                contentColor = onBackgroundColor
            ),
            onClick = onNavigateBack
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.auth_cd_back),
                tint = onBackgroundColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToggleSegmentButton(
                modifier = Modifier.weight(1f).height(50.dp),
                active = showSyncedLyrics,
                enabled = hasSyncedLyrics,
                activeColor = accentColor,
                inactiveColor = backgroundColor,
                activeContentColor = onAccentColor,
                inactiveContentColor = onBackgroundColor,
                activeCornerRadius = 50.dp,
                onClick = { onShowSyncedLyricsChange(true) },
                text = stringResource(R.string.presentation_batch_g_lyrics_mode_synced)
            )

            ToggleSegmentButton(
                modifier = Modifier.weight(1f).height(50.dp),
                active = !showSyncedLyrics,
                enabled = true,
                activeColor = accentColor,
                inactiveColor = backgroundColor,
                activeContentColor = onAccentColor,
                inactiveContentColor = onBackgroundColor,
                activeCornerRadius = 50.dp,
                onClick = { onShowSyncedLyricsChange(false) },
                text = stringResource(R.string.presentation_batch_g_lyrics_mode_static)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // AI 歌词解析按钮：加载中 → 背景圆+旋转环；加载完成 → 高亮图标；未加载 → 暗色图标
        if (lyricsExplanationEnabled) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                // 底层背景圆：始终显示
                Surface(
                    shape = CircleShape,
                    color = if (lyricsExplanation != null || isExplainingLyrics) accentColor else backgroundColor,
                    modifier = Modifier.size(40.dp)
                ) {}
                // 图标：加载时隐藏
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isExplainingLyrics,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (lyricsExplanation != null) onAccentColor else onBackgroundColor
                        ),
                        onClick = {
                            if (lyricsExplanation != null) {
                                onShowExplanation()
                            } else {
                                onExplainLyricsViaAi()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (lyricsExplanation != null) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.ai_lyrics_explanation_title),
                            tint = if (lyricsExplanation != null) onAccentColor else onBackgroundColor
                        )
                    }
                }
                // 加载动画：旋转环覆盖在背景圆上方
                androidx.compose.animation.AnimatedVisibility(
                    visible = isExplainingLyrics,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = if (lyricsExplanation != null) onAccentColor else onBackgroundColor
                    )
                }
            }
        }

        IconButton(
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = backgroundColor,
                contentColor = onBackgroundColor
            ),
            onClick = onMoreClick
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.presentation_batch_g_lyrics_cd_options),
                tint = onBackgroundColor
            )
        }
    }
}
