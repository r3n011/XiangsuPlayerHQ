package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.scoped.CustomNavigationBarItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val NavBarContentHeight = 84.dp // Altura del contenido de la barra de navegación
internal val NavBarCompactContentHeight = 64.dp
internal val NavBarContentHeightFullWidth = NavBarContentHeight // Altura del contenido de la barra de navegación en modo completo
internal val NavBarContentHeightFloating = MiniPlayerHeight // 悬浮底栏高度与 mini player 保持一致
private val MainScreenBottomGradientExtraHeight = MiniPlayerHeight + MiniPlayerBottomSpacer + 8.dp
// Some OEM freeform/floating-window modes can report a bottom inset close to the whole window height.
internal val MaxNavigationBarBottomInset = 96.dp

internal fun sanitizeNavigationBarBottomInset(systemNavBarInset: Dp): Dp {
    if (!systemNavBarInset.value.isFinite()) return 0.dp
    return systemNavBarInset.coerceIn(0.dp, MaxNavigationBarBottomInset)
}

// ⚡ 用户隐藏系统「小白条」（手势提示条）后，navigationBars inset 会上报为 0，
// 底部导航栏随即贴到屏幕下边缘；这里给出兜底间距，保持导航栏与屏幕底边的留白。
internal val MinNavigationBarBottomSpacing = 16.dp

/**
 * 隐藏「小白条」导致 inset 为 0 时的兜底：非全宽样式使用最小间距，
 * 全宽样式（FULL_WIDTH）本就贴底，保持原值。
 */
internal fun resolveNavigationBarBottomSpacing(
    systemNavBarInset: Dp,
    navBarStyle: String
): Dp = when {
    navBarStyle == NavBarStyle.FULL_WIDTH -> systemNavBarInset
    systemNavBarInset > 0.dp -> systemNavBarInset
    else -> MinNavigationBarBottomSpacing
}

internal fun calculatePlayerSheetCollapsedTargetY(
    containerHeightPx: Float,
    collapsedContentHeightPx: Float,
    bottomMarginPx: Float,
    bottomSpacerPx: Float
): Float {
    val safeContainerHeightPx = containerHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeCollapsedContentHeightPx = collapsedContentHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomMarginPx = bottomMarginPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomSpacerPx = bottomSpacerPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxTargetY = (safeContainerHeightPx - safeCollapsedContentHeightPx).coerceAtLeast(0f)

    return (safeContainerHeightPx - safeCollapsedContentHeightPx - safeBottomMarginPx - safeBottomSpacerPx)
        .coerceIn(0f, maxTargetY)
}

internal fun resolveNavBarContentHeight(compactMode: Boolean): Dp =
    if (compactMode) NavBarCompactContentHeight else NavBarContentHeight

internal fun resolveMainScreenBottomGradientHeight(compactMode: Boolean): Dp =
    resolveNavBarContentHeight(compactMode) + MainScreenBottomGradientExtraHeight

internal fun resolveNavBarSurfaceHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp {
    return when (navBarStyle) {
        NavBarStyle.FULL_WIDTH -> resolveNavBarContentHeight(compactMode) + systemNavBarInset
        NavBarStyle.FLOATING -> NavBarContentHeightFloating + systemNavBarInset
        else -> resolveNavBarContentHeight(compactMode)
    }
}

internal fun resolveNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = resolveNavBarContentHeight(compactMode) + systemNavBarInset

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp,
    onSearchIconDoubleTap: () -> Unit,
    onCenterNavClick: () -> Unit = {}
) {
    val navBarInsetPadding = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ).let { inset ->
        // Some devices report 0 inset when the system nav bar exists.
        // Use a minimum fallback to prevent items sticking to the screen edge.
        // ⚡ 隐藏「小白条」时 inset 与 bottomBarPadding 都可能为 0（全宽样式），
        // 此时使用最小兜底间距，避免导航项贴到屏幕下边缘。
        when {
            inset > 0.dp -> inset
            bottomBarPadding > 0.dp -> bottomBarPadding
            else -> MinNavigationBarBottomSpacing
        }
    }
    val innerRowPadding = (navBarInsetPadding - bottomBarPadding).coerceAtLeast(0.dp)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val navigationDebounceEnabled = remember { mutableStateOf(true) }
    val debounceTimeout = 300L

    val rowModifier = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = innerRowPadding, start = 12.dp, end = 12.dp)
    } else {
        modifier
            .padding(start = 10.dp, end = 10.dp, bottom = innerRowPadding)
            .fillMaxWidth()
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scope = rememberCoroutineScope()
        var lastSearchTapTimestamp by remember { mutableStateOf(0L) }
        navItems.forEach { item ->
            val isSelected = currentRoute != null && currentRoute == item.screen.route
            val selectedColor = MaterialTheme.colorScheme.primary
            val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val indicatorColorFromTheme = MaterialTheme.colorScheme.secondaryContainer

            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId
            } else {
                item.iconResId
            }
            val labelText = stringResource(item.labelResId)
            val iconLambda: @Composable () -> Unit = remember(iconPainterResId, item.labelResId, item.imageVectorIcon) {
                {
                    when {
                        item.imageVectorIcon != null -> {
                            Icon(
                                imageVector = item.imageVectorIcon,
                                contentDescription = labelText
                            )
                        }
                        iconPainterResId != null -> {
                            Icon(
                                painter = painterResource(id = iconPainterResId),
                                contentDescription = labelText
                            )
                        }
                        else -> Unit
                    }
                }
            }
            val selectedIconLambda: @Composable () -> Unit = remember(iconPainterResId, item.labelResId, item.imageVectorIcon) {
                {
                    when {
                        item.imageVectorIcon != null -> {
                            Icon(
                                imageVector = item.imageVectorIcon,
                                contentDescription = labelText
                            )
                        }
                        iconPainterResId != null -> {
                            Icon(
                                painter = painterResource(id = iconPainterResId),
                                contentDescription = labelText
                            )
                        }
                        else -> Unit
                    }
                }
            }
            val labelLambda: (@Composable () -> Unit)? = if (compactMode) {
                null
            } else {
                remember(item.labelResId) {
                    { Text(labelText) }
                }
            }
            val latestOnCenterNavClick by rememberUpdatedState(onCenterNavClick)
            val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
                click@{
                    val itemRoute = item.screen.route
                    val isSearchTab = itemRoute == Screen.Search.route
                    val isCenterActionTab = itemRoute == Screen.Roaming.route
                    val isAlreadySelected = latestCurrentRoute == itemRoute

                    if (isCenterActionTab) {
                        latestOnCenterNavClick()
                        return@click
                    }

                    if (isSearchTab) {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                        lastSearchTapTimestamp = now

                        if (!isAlreadySelected) {
                            if (!navigationDebounceEnabled.value) return@click
                            if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                lastSearchTapTimestamp = 0L
                                return@click
                            }
                            navigationDebounceEnabled.value = false
                            scope.launch {
                                delay(debounceTimeout)
                                navigationDebounceEnabled.value = true
                            }
                        }

                        if (isDoubleTap) {
                            lastSearchTapTimestamp = 0L
                            if (isAlreadySelected) {
                                latestOnSearchIconDoubleTap()
                            } else {
                                scope.launch {
                                    delay(160L)
                                    latestOnSearchIconDoubleTap()
                                }
                            }
                        }
                    } else if (!isAlreadySelected) {
                        if (!navigationDebounceEnabled.value) return@click
                        lastSearchTapTimestamp = 0L
                        if (navController.navigateToTopLevelSafely(itemRoute)) {
                            navigationDebounceEnabled.value = false
                            scope.launch {
                                delay(debounceTimeout)
                                navigationDebounceEnabled.value = true
                            }
                        }
                    } else {
                        lastSearchTapTimestamp = 0L
                    }
                }
            }
            CustomNavigationBarItem(
                modifier = Modifier.weight(1f),
                selected = isSelected,
                onClick = onClickLambda,
                enabled = true,
                compactMode = compactMode,
                icon = iconLambda,
                selectedIcon = selectedIconLambda,
                label = labelLambda,
                contentDescription = item.label,
                alwaysShowLabel = true,
                selectedIconColor = selectedColor,
                unselectedIconColor = unselectedColor,
                selectedTextColor = selectedColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = indicatorColorFromTheme
            )
        }
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onSearchIconDoubleTap: () -> Unit = {},
    onCenterNavClick: () -> Unit = {},
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {},
    miniPlayerVisible: Boolean = false,
    blurEnabled: Boolean = true
) {
    // 悬浮底栏模式：使用 FloatingNavBarContent
    if (navBarStyle == NavBarStyle.FLOATING) {
        FloatingNavBarContent(
            navController = navController,
            navItems = navItems,
            currentRoute = currentRoute,
            currentSong = currentSong,
            isPlaying = isPlaying,
            blurEnabled = blurEnabled,
            onSearchIconDoubleTap = onSearchIconDoubleTap,
            onCenterNavClick = onCenterNavClick,
            onNowPlayingClick = onNowPlayingClick,
            miniPlayerVisible = miniPlayerVisible,
            modifier = modifier
        )
        return
    }

    PlayerInternalNavigationItemsRow(
        navController = navController,
        navItems = navItems,
        currentRoute = currentRoute,
        navBarStyle = navBarStyle,
        compactMode = compactMode,
        bottomBarPadding = bottomBarPadding,
        onSearchIconDoubleTap = onSearchIconDoubleTap,
        onCenterNavClick = onCenterNavClick,
        modifier = modifier
    )
}
