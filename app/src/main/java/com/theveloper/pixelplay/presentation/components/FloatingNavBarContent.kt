package com.theveloper.pixelplay.presentation.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
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
    // ⚡ 遵守「导航栏模糊」与「禁用所有模糊」设置：关闭时用不透明 surface 底色替代磨砂
    blurEnabled: Boolean = true,
    onSearchIconDoubleTap: () -> Unit = {},
    onCenterNavClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    miniPlayerVisible: Boolean = false
) {
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    var lastNavTimestamp by remember { mutableStateOf(0L) }
    val debounceTimeout = 300L

    // 悬浮底栏按屏幕密度自动缩放：以 xxhdpi(3.0) 为基准，过高/过低密度做轻微补偿，
    // 避免在平板或超高 DPI 设备上元件显得过小或过大。
    val densityValue = LocalDensity.current.density
    val dpiScale = remember(densityValue) {
        (densityValue / 3.0f).coerceIn(0.9f, 1.25f)
    }

    // 过滤出搜索项和其他导航项
    val searchItem = remember(navItems) { navItems.find { it.screen.route == Screen.Search.route } }
    val otherItems = remember(navItems) { navItems.filter { it.screen.route != Screen.Search.route } }

    // ⚡ 与 mini player 共用同一套折叠宽度规则：宽屏 + 横屏时两条栏都是 520dp 且右对齐，
    //    这样底栏的左右边缘和上面的 mini player 对齐（此前底栏铺满全宽，比 mini player 宽一截）。
    val collapsedBarWidth = rememberCollapsedBarWidth()
    val stretchToEdges = collapsedBarWidth.limitWidth

    /** 单个导航项的点击逻辑（两种布局共用） */
    val onItemClick: (BottomNavItem) -> Unit = { item ->
        val itemRoute = item.screen.route
        val isCenterActionTab = itemRoute == Screen.Roaming.route
        val isAlreadySelected = latestCurrentRoute == itemRoute

        if (isCenterActionTab) {
            onCenterNavClick()
        } else if (!isAlreadySelected) {
            // ⚡ 防抖用「时间戳」而不是「布尔开关 + 协程复位」：后者一旦协程被取消
            //   （重组/切换页面），开关会永久停在 false → 之后点导航栏完全没反应。
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastNavTimestamp >= debounceTimeout) {
                if (navController.navigateToTopLevelSafely(itemRoute)) {
                    lastNavTimestamp = now
                }
            }
        }
    }

    // 布局：[导航胶囊组] [搜索圆]
    // - 宽屏（底栏被限宽到 520dp）时：两组元件分列两端（SpaceBetween），左右边缘与 mini player 对齐；
    // - 其余情况：整行居中，保持原有观感。
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // ⚡ 自动决定是否「始终显示每个页面的名字」：宽度够（宽屏/横屏）就全部显示文字，
    //    此时切换动画用「选中指示器平移」；不够就回到「只有选中项展开文字」的原有动画。
    val showAllLabels = maxWidth >= NavBarAllLabelsMinWidth
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (stretchToEdges) Arrangement.SpaceBetween else Arrangement.Center,
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
                .then(
                    if (blurEnabled) {
                        Modifier.hazeEffect(
                            state = MainActivity.LocalHazeState.current,
                            style = HazeMaterials.ultraThin(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            // 中强模糊：加强底栏模糊半径
                            blurRadius = 40.dp
                        }
                    } else {
                        Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        )
                    }
                )
                // 上下/左右内边距保持一致，让选中指示胶囊在磨砂容器内四周等距，
                // 避免原来「上下 1dp、左右 4dp」造成的上下挤压、左右松散的观感
                .padding(
                    horizontal = NavGroupPaddingHorizontal * dpiScale,
                    vertical = NavGroupPaddingVertical * dpiScale
                )
        ) {
            if (showAllLabels) {
                // 名字全部显示：选中指示器在各项之间平移
                LabeledNavGroup(
                    items = otherItems,
                    currentRoute = currentRoute,
                    dpiScale = dpiScale,
                    onItemClick = onItemClick
                )
            } else {
                otherItems.forEach { item ->
                    FloatingNavItem(
                        item = item,
                        isSelected = currentRoute != null && currentRoute == item.screen.route,
                        dpiScale = dpiScale,
                        onClick = { onItemClick(item) }
                    )
                }
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
                    blurEnabled = blurEnabled,
                    onClick = {
                        val itemRoute = Screen.Search.route
                        val isAlreadySelected = latestCurrentRoute == itemRoute

                        if (!isAlreadySelected) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastNavTimestamp < debounceTimeout) return@FloatingSearchButton
                            if (navController.navigateToTopLevelSafely(itemRoute)) {
                                lastNavTimestamp = now
                            }
                        }

                        // 双击搜索
                        if (isAlreadySelected) {
                            latestOnSearchIconDoubleTap()
                        }
                    }
                )
            }
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
    // ⚡ 不在此处以 by 委托读取（否则每帧都会重组本组件）；改为把 State 传进 graphicsLayer，
    //   让旋转只在绘制阶段生效，避免高频重组。
    val infiniteTransition = rememberInfiniteTransition(label = "nowPlayingSpin")
    val rotationState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coverRotation"
    )

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
                            rotationZ = if (isPlaying) rotationState.value else 0f
                        },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(ballSize)
                        .graphicsLayer { rotationZ = if (isPlaying) rotationState.value else 0f },
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
 * 「始终显示所有页面名字」的最小可用宽度：底栏被限宽到 520dp（宽屏 + 横屏）时空间足够，
 * 就全部显示文字并用「选中指示器平移」；窄屏（竖屏手机）保持「只有选中项展开文字」的原有动画。
 */
private val NavBarAllLabelsMinWidth = 470.dp

/** 显示名字时每个导航项的固定宽度（选中指示器按「该项宽度 + 间距」平移） */
private val NavLabeledItemWidth = 100.dp

/**
 * 显示全部名字时的导航组：一个选中指示器（药丸）在各项之间平移，每项「图标 + 文字」常显。
 * 与不显示名字时的唯一区别就在这里：后者是选中项自己展开文字。
 */
@Composable
private fun LabeledNavGroup(
    items: List<BottomNavItem>,
    currentRoute: String?,
    dpiScale: Float,
    onItemClick: (BottomNavItem) -> Unit,
) {
    val selectedIndex = items.indexOfFirst { currentRoute != null && it.screen.route == currentRoute }
    val indicatorOffset by animateDpAsState(
        targetValue = (NavLabeledItemWidth + NavGroupItemSpacing) * dpiScale *
            selectedIndex.coerceAtLeast(0),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navIndicatorOffset"
    )

    Box {
        // 选中指示器：随选中项平移的药丸（不是每项各自变色）
        if (selectedIndex >= 0) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(NavLabeledItemWidth * dpiScale)
                    .height(NavPillHeight * dpiScale)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NavGroupItemSpacing * dpiScale)) {
            items.forEach { item ->
                LabeledNavItem(
                    item = item,
                    isSelected = currentRoute != null && item.screen.route == currentRoute,
                    dpiScale = dpiScale,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

/** 常显名字的导航项（背景由外层滑动指示器负责，这里只画图标 + 文字 + 按压反馈） */
@Composable
private fun LabeledNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    dpiScale: Float,
    onClick: () -> Unit,
) {
    val labelText = stringResource(item.labelResId)
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "labeledNavContentColor"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "labeledNavPressScale"
    )
    val iconResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
        item.selectedIconResId
    } else {
        item.iconResId
    }

    Row(
        modifier = Modifier
            .width(NavLabeledItemWidth * dpiScale)
            .height(NavPillHeight * dpiScale)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when {
            item.imageVectorIcon != null -> Icon(
                imageVector = item.imageVectorIcon,
                contentDescription = labelText,
                tint = contentColor,
                modifier = Modifier.size(NavPillIconSize * dpiScale)
            )
            iconResId != null -> Icon(
                painter = painterResource(id = iconResId),
                contentDescription = labelText,
                tint = contentColor,
                modifier = Modifier.size(NavPillIconSize * dpiScale)
            )
        }
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = NavPillSpacing * dpiScale)
        )
    }
}

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
    blurEnabled: Boolean = true,
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
            // 与导航胶囊组（NavGroupHeight=56dp）等高，避免矮一截导致顶边不对齐
            .size(NavGroupHeight * dpiScale)
            .clip(CircleShape)
            .then(
                if (blurEnabled) {
                    Modifier.hazeEffect(
                        state = MainActivity.LocalHazeState.current,
                        style = HazeMaterials.ultraThin(containerColor = backgroundColor)
                    ) {
                        // 中强模糊：加强底栏模糊半径
                        blurRadius = 40.dp
                    }
                } else {
                    Modifier.background(backgroundColor)
                }
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
