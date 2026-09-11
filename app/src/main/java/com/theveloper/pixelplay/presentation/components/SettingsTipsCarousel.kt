package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import com.theveloper.pixelplay.R
import androidx.compose.ui.res.stringResource
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 设置页顶部的推荐卡片（轮播）
 *
 * 中央卡片放大显示、两侧卡片缩小半透明，5 秒自动轮播；
 * 点击卡片跳转到对应设置分类（模仿 Rhythm 的 SettingsTipsCarousel 设计）。
 */
data class SettingsTipData(
    val category: SettingsCategory,
    val title: String,
    val text: String,
    val isPrimary: Boolean = false
)

@Composable
fun SettingsTipsCarousel(
    tips: List<SettingsTipData>,
    onTipClick: (SettingsTipData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tips.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { tips.size })
    val scope = rememberCoroutineScope()

    // 自动轮播：每 5 秒切到下一页；用户拖动中则跳过本次切换
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (pagerState.isScrollInProgress) continue
            scope.launch {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % tips.size)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                val tip = tips[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                val scale = lerp(1f, 0.92f, pageOffset)
                val alpha = lerp(1f, 0.55f, pageOffset)
                SettingsTipCard(
                    tip = tip,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clickable { onTipClick(tip) }
                )
            }
        }
        // 指示点
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(tips.size) { i ->
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

@Composable
private fun SettingsTipCard(
    tip: SettingsTipData,
    modifier: Modifier = Modifier
) {
    val isPrimary = tip.isPrimary
    val containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
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
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tip.category.icon ?: Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = tip.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tip.text,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
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
                    "当前为「${if (uiState.appThemeMode == "DARK") "深色" else "浅色"}」主题，试试动态取色跟随封面变换风格"
                } else {
                    "让播放器跟随专辑封面自动取色，打造专属视觉风格"
                },
                isPrimary = true
            ),
            SettingsTipData(
                category = SettingsCategory.PLAYBACK,
                title = playbackTitle,
                text = if (uiState.pitchFollowSpeed) {
                    "变调跟随倍速已开启，长按倍速按钮即可在弹窗中切换"
                } else {
                    "开启变调跟随，让倍速与音高同步变化"
                }
            ),
            SettingsTipData(
                category = SettingsCategory.EQUALIZER,
                title = equalizerTitle,
                text = if (uiState.hiFiModeEnabled) {
                    "HiFi 音效已开启，尽情享受纯净音质"
                } else {
                    "解锁 HiFi 音效，体验更纯净的声音"
                }
            ),
            SettingsTipData(
                category = SettingsCategory.APPEARANCE,
                title = appearanceTitle,
                text = if (uiState.lyricsGradientOverlayEnabled) {
                    "歌词渐变遮罩已开启，动态背景下的文字依然清晰"
                } else {
                    "开启歌词渐变遮罩，绚丽背景下文字不刺眼"
                }
            ),
            SettingsTipData(
                category = SettingsCategory.AI_INTEGRATION,
                title = aiTitle,
                text = "让 AI 帮你搜歌、生成歌单、解析歌词，试试对它说句话"
            ),
            SettingsTipData(
                category = SettingsCategory.LIBRARY,
                title = libraryTitle,
                text = "接入网易云、QQ 音乐等在线音源，海量歌曲随时听"
            ),
            SettingsTipData(
                category = SettingsCategory.BACKUP_RESTORE,
                title = backupTitle,
                text = "一键备份你的设置与播放数据，换机无痛迁移"
            )
        )
    }
}
