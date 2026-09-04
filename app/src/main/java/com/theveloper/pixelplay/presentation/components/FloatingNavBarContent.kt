package com.theveloper.pixelplay.presentation.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.size.Size
import com.theveloper.pixelplay.BottomNavItem
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 悬浮底栏内容组件
 *
 * 布局：
 * - 左侧：Now Playing 球（封面 + 旋转光环）
 * - 中间：导航图标（首页/发现/媒体库/设置），选中时显示文字
 * - 右侧：搜索圆形按钮
 */
@Composable
fun FloatingNavBarContent(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    currentSong: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onSearchIconDoubleTap: () -> Unit = {},
    onCenterNavClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    miniPlayerVisible: Boolean = false
) {
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val navigationDebounceEnabled = remember { mutableStateOf(true) }
    val debounceTimeout = 300L
    val scope = rememberCoroutineScope()

    // 悬浮底栏按屏幕密度自动缩放：以 xxhdpi(3.0) 为基准，过高/过低密度做轻微补偿，
    // 避免在平板或超高 DPI 设备上元件显得过小或过大。
    val densityValue = LocalDensity.current.density
    val dpiScale = remember(densityValue) {
        (densityValue / 3.0f).coerceIn(0.9f, 1.25f)
    }

    // 过滤出搜索项和其他导航项
    val searchItem = remember(navItems) { navItems.find { it.screen.route == Screen.Search.route } }
    val otherItems = remember(navItems) { navItems.filter { it.screen.route != Screen.Search.route } }

    // 布局：[导航胶囊组] [搜索圆]
    // 使用 fillMaxWidth + Center 让 Row 自动居中，子项用 weight(1f) 约束防止溢出。
    // Row 的固有尺寸测量确保导航组尽可能紧凑，多余空间均匀分配。
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // === 导航胶囊组（所有导航项放在同一个胶囊容器内） ===
        Row(
            horizontalArrangement = Arrangement.spacedBy(NavGroupItemSpacing * dpiScale),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // 悬浮底栏整体贴底部：胶囊组下沉到栏内容区底部，消除与屏幕下侧之间的大空隙
                // （仅保留手势区 inset 作为安全距离）。
                .align(Alignment.Bottom)
                .height(NavGroupHeight * dpiScale)
                .clip(RoundedCornerShape(percent = 50))
                .hazeEffect(
                    state = MainActivity.LocalHazeState.current,
                    style = HazeMaterials.ultraThin(containerColor = MaterialTheme.colorScheme.surface)
                )
                // 上下/左右内边距保持一致，让选中指示胶囊在磨砂容器内四周等距，
                // 避免原来「上下 1dp、左右 4dp」造成的上下挤压、左右松散的观感
                .padding(
                    horizontal = NavGroupPaddingHorizontal * dpiScale,
                    vertical = NavGroupPaddingVertical * dpiScale
                )
        ) {
            otherItems.forEach { item ->
                val isSelected = currentRoute != null && currentRoute == item.screen.route
                FloatingNavItem(
                    item = item,
                    isSelected = isSelected,
                    dpiScale = dpiScale,
                    onClick = {
                        val itemRoute = item.screen.route
                        val isCenterActionTab = itemRoute == Screen.Roaming.route
                        val isAlreadySelected = latestCurrentRoute == itemRoute

                        if (isCenterActionTab) {
                            onCenterNavClick()
                            return@FloatingNavItem
                        }

                        if (!isAlreadySelected) {
                            if (!navigationDebounceEnabled.value) return@FloatingNavItem
                            if (navController.navigateToTopLevelSafely(itemRoute)) {
                                navigationDebounceEnabled.value = false
                                scope.launch {
                                    delay(debounceTimeout)
                                    navigationDebounceEnabled.value = true
                                }
                            }
                        }
                    }
                )
            }
        }

        // === 右侧：搜索圆形按钮（独立圆） ===
        searchItem?.let { item ->
            Box(
                Modifier
                    .align(Alignment.Bottom)
                    .padding(start = 10.dp * dpiScale)
            ) {
                FloatingSearchButton(
                    item = item,
                    isSelected = currentRoute == Screen.Search.route,
                    dpiScale = dpiScale,
                    onClick = {
                        val itemRoute = Screen.Search.route
                        val isAlreadySelected = latestCurrentRoute == itemRoute

                        if (!isAlreadySelected) {
                            if (!navigationDebounceEnabled.value) return@FloatingSearchButton
                            if (navController.navigateToTopLevelSafely(itemRoute)) {
                                navigationDebounceEnabled.value = false
                                scope.launch {
                                    delay(debounceTimeout)
                                    navigationDebounceEnabled.value = true
                                }
                            }
                        }

                        // 双击搜索
                        val now = SystemClock.elapsedRealtime()
                        if (isAlreadySelected) {
                            latestOnSearchIconDoubleTap()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Now Playing 圆：封面为独立小圆（与搜索圆同尺寸）
 * 播放时封面旋转，不播放时静止。
 * 点击时轻微缩放反馈，然后直接展开全屏播放器。
 */
@Composable
private fun NowPlayingBall(
    song: Song?,
    isPlaying: Boolean,
    onTap: () -> Unit = {},
    miniPlayerVisible: Boolean = false,
    dpiScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val ballSize = 50.dp * dpiScale

    // 封面旋转动画（播放时旋转）
    val infiniteTransition = rememberInfiniteTransition(label = "nowPlayingSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coverRotation"
    )

    val animatedRotation = if (isPlaying) rotation else 0f

    // 点击缩放反馈
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f),
        label = "nowPlayingCoverPressScale"
    )

    Box(
        modifier = modifier
            .width(ballSize + 10.dp * dpiScale)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // 封面圆球
        Box(
            modifier = Modifier
                .size(ballSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            // 用 graphicsLayer 旋转整个封面，播放时转动
            if (song?.albumArtUriString?.isNotBlank() == true) {
                AsyncImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(ballSize)
                        .clip(CircleShape)
                        .graphicsLayer {
                            rotationZ = animatedRotation
                        },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(ballSize)
                        .graphicsLayer { rotationZ = animatedRotation },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            com.theveloper.pixelplay.R.drawable.ic_music_placeholder
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp * dpiScale),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 胶囊固定高度 */
private val NavPillHeight = 52.dp
/** 导航组胶囊纵向内边距（与横向一致，保证选中指示容器四周等距）。
 * 保持紧凑对称：既修复「上下间距过大」，又不会回到之前「上下紧/左右松」的不对称观感。 */
private val NavGroupPaddingVertical = 2.dp
/** 导航组胶囊高度（略高于内部导航项 + 上下内边距，形成包围胶囊） */
private val NavGroupHeight = NavPillHeight + NavGroupPaddingVertical * 2
/** 导航组胶囊横向内边距 */
private val NavGroupPaddingHorizontal = 2.dp
/** 胶囊内相邻导航项间距 */
private val NavGroupItemSpacing = 4.dp
/** 图标直径 */
private val NavPillIconSize = 24.dp
/** 图标与文字间距 */
private val NavPillSpacing = 7.dp
/** 胶囊横向 padding：左右基本对称，左侧比右侧宽 1px，抵消文字的轻微横向重量 */
private val NavPillPaddingStart = 16.dp
private val NavPillPaddingEnd = 15.dp

/**
 * 悬浮导航项：未选中时只显示图标，选中时收缩成"图标 + 文字"的动态选中胶囊。
 * 通过 AnimatedVisibility 让胶囊宽度 + 文字平滑扩展/收缩。
 */
@Composable
private fun FloatingNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    dpiScale: Float = 1f,
    onClick: () -> Unit
) {
    val labelText = stringResource(item.labelResId)

    // 胶囊背景色：选中 primaryContainer，未选中透明
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(250),
        label = "pillBgColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "pillIconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "pillTextColor"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 按压轻微缩小（不产生明显 ripple）
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pillPressScale"
    )

    val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
        item.selectedIconResId
    } else {
        item.iconResId
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(NavPillHeight * dpiScale)
            .clip(RoundedCornerShape(percent = 50))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            // 左右留出胶囊 padding。文字只出现在选中项右侧，为了让选中遮罩视觉居中，
            // 使用基本对称的不对称 padding（左侧 16dp、右侧 15dp，左侧宽 1px），
            // 抵消选中文字带来的轻微横向重量，避免左侧空隙偏大。
            .padding(
                start = NavPillPaddingStart * dpiScale,
                end = NavPillPaddingEnd * dpiScale
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when {
                item.imageVectorIcon != null -> Icon(
                    imageVector = item.imageVectorIcon,
                    contentDescription = labelText,
                    tint = iconColor,
                    modifier = Modifier.size(NavPillIconSize * dpiScale)
                )
                iconPainterResId != null -> Icon(
                    painter = painterResource(id = iconPainterResId),
                    contentDescription = labelText,
                    tint = iconColor,
                    modifier = Modifier.size(NavPillIconSize * dpiScale)
                )
            }

            // 文字：选中时横向扩展进入
            AnimatedVisibility(
                visible = isSelected,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = tween(280)
                ) + fadeIn(animationSpec = tween(280)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = tween(220)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = NavPillSpacing * dpiScale)
                )
            }
        }
    }
}

/**
 * 搜索圆形按钮：右侧独立圆形，点击时颜色加深
 */
@Composable
private fun FloatingSearchButton(
    item: BottomNavItem,
    isSelected: Boolean,
    dpiScale: Float = 1f,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isPressed -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(200),
        label = "searchBgColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "searchIconColor"
    )

    Box(
        modifier = Modifier
            .size(50.dp * dpiScale)
            .clip(CircleShape)
            .hazeEffect(
                state = MainActivity.LocalHazeState.current,
                style = HazeMaterials.ultraThin(containerColor = backgroundColor)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(item.labelResId),
            tint = iconColor,
            modifier = Modifier.size(22.dp * dpiScale)
        )
    }
}
