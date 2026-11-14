package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.utils.AppLocaleManager

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 设置页顶部的推荐卡片（轮播，模仿 Rhythm 的 SettingsTipsCarousel）
 *
 * 加权布局：中央卡片最大、两侧卡片收窄为箭头提示；
 * 5 秒自动轮播（弹簧动画切换），用户拖动时暂停；
 * 点击中央卡片跳转到对应设置分类，点击侧边卡片先滚动过去。
 */
data class SettingsTipData(
    val category: SettingsCategory,
    val title: String,
    val text: String,
    val isPrimary: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsTipsCarousel(
    tips: List<SettingsTipData>,
    onTipClick: (SettingsTipData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tips.isEmpty()) return

    val itemsCount = tips.size
    val pagerState = rememberPagerState(pageCount = { itemsCount })
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacing = 4.dp

    val carouselAnimationSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    }

    val autoScrollProgress = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 自动轮播：5 秒后弹簧切到下一页，仅在 RESUMED 状态下进行
    LaunchedEffect(pagerState.settledPage, itemsCount, lifecycleOwner) {
        if (itemsCount > 1) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                autoScrollProgress.snapTo(0f)
                val startTime = System.currentTimeMillis()
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
                    autoScrollProgress.snapTo(p)
                    if (p >= 1f) break
                    delay(16)
                }
                if (!pagerState.isScrollInProgress) {
                    val nextStep = (pagerState.currentPage + 1) % itemsCount
                    pagerState.animateScrollToPage(
                        page = nextStep,
                        animationSpec = carouselAnimationSpec
                    )
                }
            }
        } else {
            autoScrollProgress.snapTo(0f)
        }
    }

    val interactionSources = remember(itemsCount) { List(itemsCount) { MutableInteractionSource() } }

    val expressiveSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    val visualProgress by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val spacingPx = with(density) { spacing.toPx() }

            // 加权卡片行：中央卡片权重最大，越远越窄直至消失
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                for (i in 0 until itemsCount) {
                    val dist = (visualProgress - i).absoluteValue
                    val currentWeight = when {
                        dist < 1.0f -> {
                            val maxW = if (i == 0 || i == itemsCount - 1) 0.9f else 0.82f
                            lerp(maxW, 0.1f, dist)
                        }
                        dist < 2.0f -> lerp(0.1f, 0.0f, dist - 1.0f)
                        else -> 0.0f
                    }

                    if (currentWeight > 0.005f) {
                        val currentCornerRadius = if (dist < 1.0f) lerp(24f, 16f, dist) else 16f
                        val currentAlpha = when {
                            dist < 1.0f -> lerp(1f, 0.4f, dist)
                            dist < 2.0f -> lerp(0.4f, 0f, dist - 1.0f)
                            else -> 0f
                        }

                        val baseColor = if (tips[i].isPrimary) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }

                        SettingsTipCard(
                            tip = tips[i],
                            dist = dist,
                            isToTheLeft = i < visualProgress,
                            interactionSource = interactionSources[i],
                            modifier = Modifier.weight(currentWeight),
                            containerColor = baseColor.copy(alpha = currentAlpha),
                            cornerRadius = currentCornerRadius.dp,
                            motionSpec = expressiveSpring,
                            onClick = { onTipClick(tips[i]) }
                        )
                    }
                }
            }

            // 透明 Pager 覆盖层：负责滑动切换与命中检测（包含点击卡片）
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .alpha(0f)
                    .pointerInput(itemsCount) {
                        detectTapGestures { offset ->
                            val tapX = offset.x
                            var currentX = 0f
                            val currentProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction

                            val renderedWeights = (0 until itemsCount).map { i ->
                                val dist = (currentProgress - i).absoluteValue
                                when {
                                    dist < 1.0f -> lerp(if (i == 0 || i == itemsCount - 1) 0.9f else 0.82f, 0.1f, dist)
                                    dist < 2.0f -> lerp(0.1f, 0.0f, dist - 1.0f)
                                    else -> 0.0f
                                }
                            }

                            val visibleIndices = renderedWeights.indices.filter { renderedWeights[it] > 0.005f }
                            val totalGaps = (visibleIndices.size - 1).coerceAtLeast(0)
                            val availableWidthForCards = totalWidthPx - (spacingPx * totalGaps)

                            for (i in visibleIndices) {
                                val weight = renderedWeights[i]
                                val cardWidth = weight * availableWidthForCards

                                if (tapX >= currentX && tapX <= currentX + cardWidth) {
                                    coroutineScope.launch {
                                        val press = PressInteraction.Press(offset)
                                        interactionSources[i].emit(press)
                                        delay(150)
                                        interactionSources[i].emit(PressInteraction.Release(press))

                                        if (pagerState.currentPage == i) {
                                            tips[i].let(onTipClick)
                                        } else {
                                            pagerState.animateScrollToPage(
                                                page = i,
                                                animationSpec = carouselAnimationSpec
                                            )
                                        }
                                    }
                                    break
                                }
                                currentX += cardWidth + spacingPx
                            }
                        }
                    }
            ) {
                Box(Modifier.fillMaxSize())
            }
        }

        // 指示点
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(itemsCount) { i ->
                val selected = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.SettingsTipCard(
    tip: SettingsTipData,
    dist: Float,
    isToTheLeft: Boolean,
    interactionSource: MutableInteractionSource,
    containerColor: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    motionSpec: SpringSpec<Float>,
    onClick: () -> Unit
) {
    val isFocused = dist < 0.6f
    val isPrimary = tip.isPrimary
    val contentColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(cornerRadius)
    ) {
        AnimatedContent(
            targetState = isFocused,
            transitionSpec = {
                val springSpec = spring<IntOffset>(
                    stiffness = motionSpec.stiffness,
                    dampingRatio = motionSpec.dampingRatio
                )
                val floatSpring = spring<Float>(
                    stiffness = motionSpec.stiffness,
                    dampingRatio = motionSpec.dampingRatio
                )

                val slideIn = if (targetState) {
                    slideInHorizontally(animationSpec = springSpec) { if (isToTheLeft) -it else it }
                } else {
                    slideInHorizontally(animationSpec = springSpec) { if (isToTheLeft) it else -it }
                }

                val slideOut = if (targetState) {
                    slideOutHorizontally(animationSpec = springSpec) { if (isToTheLeft) it else -it }
                } else {
                    slideOutHorizontally(animationSpec = springSpec) { if (isToTheLeft) -it else it }
                }

                (fadeIn(animationSpec = floatSpring) + slideIn +
                    scaleIn(initialScale = 0.92f, animationSpec = floatSpring))
                    .togetherWith(
                        fadeOut(animationSpec = floatSpring) + slideOut +
                            scaleOut(targetScale = 0.92f, animationSpec = floatSpring)
                    )
            },
            label = "TipCardContentTransition",
            modifier = Modifier.fillMaxSize()
        ) { focused ->
            if (focused) {
                // 聚焦卡片：图标 + 标题 + 描述
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = tip.category.icon ?: Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = tip.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = tip.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.85f),
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                // 侧边卡片：仅显示箭头提示，指示还有更多推荐
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isToTheLeft) {
                            Icons.Rounded.ChevronLeft
                        } else {
                            Icons.Rounded.ChevronRight
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * 根据当前设置状态生成推荐卡片（模仿 Rhythm：状态感知 + 多模板随机文案）。
 */
@Composable
fun rememberSettingsTips(uiState: SettingsUiState): List<SettingsTipData> {
    val appearanceTitle = stringResource(R.string.settings_category_appearance_title)
    val playbackTitle = stringResource(R.string.settings_category_playback_title)
    val equalizerTitle = stringResource(R.string.settings_category_equalizer_title)
    val aiTitle = stringResource(R.string.settings_category_ai_title)
    val libraryTitle = stringResource(R.string.settings_category_library_title)
    val backupTitle = stringResource(R.string.settings_category_backup_title)

    return remember(
        uiState.pitchFollowSpeed,
        uiState.lyricsGradientOverlayEnabled,
        uiState.hiFiModeEnabled,
        uiState.appThemeMode
    ) {
        listOf(
            SettingsTipData(
                category = SettingsCategory.APPEARANCE,
                title = appearanceTitle,
                text = if (uiState.appThemeMode == "DARK" || uiState.appThemeMode == "LIGHT") {
                    AppLocaleManager.getString(R.string.ui_your_current_theme_is_try_dynamic_colors_that_match_the, (if (uiState.appThemeMode == "DARK") AppLocaleManager.getString(R.string.ui_residue_onboarding_theme_dark) else AppLocaleManager.getString(R.string.ui_residue_onboarding_theme_light)).toString())
                } else {
                    AppLocaleManager.getString(R.string.ui_match_the_player_colors_to_your_album_art_for_a)
                },
                isPrimary = true
            ),
            SettingsTipData(
                category = SettingsCategory.PLAYBACK,
                title = playbackTitle,
                text = if (uiState.pitchFollowSpeed) {
                    AppLocaleManager.getString(R.string.ui_pitch_follows_playback_speed_long_press_the_speed_button_to)
                } else {
                    AppLocaleManager.getString(R.string.ui_enable_pitch_following_to_change_speed_and_pitch_together)
                }
            ),
            SettingsTipData(
                category = SettingsCategory.EQUALIZER,
                title = equalizerTitle,
                text = if (uiState.hiFiModeEnabled) {
                    AppLocaleManager.getString(R.string.ui_hifi_effects_are_on_enjoy_clearer_sound)
                } else {
                    AppLocaleManager.getString(R.string.ui_enable_hifi_effects_for_clearer_sound)
                }
            ),
            SettingsTipData(
                category = SettingsCategory.APPEARANCE,
                title = appearanceTitle,
                text = if (uiState.lyricsGradientOverlayEnabled) {
                    AppLocaleManager.getString(R.string.ui_the_lyrics_gradient_mask_is_on_to_keep_text_clear)
                } else {
                    AppLocaleManager.getString(R.string.ui_enable_the_lyrics_gradient_mask_for_comfortable_reading_over_colorful)
                }
            ),
            SettingsTipData(
                category = SettingsCategory.AI_INTEGRATION,
                title = aiTitle,
                text = AppLocaleManager.getString(R.string.ui_ask_ai_to_find_songs_create_playlists_or_explain_lyrics)
            ),
            SettingsTipData(
                category = SettingsCategory.LIBRARY,
                title = libraryTitle,
                text = AppLocaleManager.getString(R.string.ui_connect_netease_qq_music_and_other_online_sources_for_more)
            ),
            SettingsTipData(
                category = SettingsCategory.BACKUP_RESTORE,
                title = backupTitle,
                text = AppLocaleManager.getString(R.string.ui_back_up_your_settings_and_playback_data_for_an_easier)
            )
        )
    }
}
