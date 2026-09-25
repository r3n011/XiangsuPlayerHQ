package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayStatusBarStyle
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreGradientTopBar(
    title: String,
    startColor: Color,
    endColor: Color,
    contentColor: Color,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationIconClick: () -> Unit,
) {
    val gradientBrush = remember(startColor, endColor) {
        Brush.verticalGradient(colors = listOf(startColor, endColor))
    }

    PixelPlayStatusBarStyle(color = startColor)

    LargeTopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = title,
                color = contentColor,
                fontFamily = GoogleSansRounded
            )
        },
        expandedHeight = 160.dp,
        modifier = Modifier.background(brush = gradientBrush),
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(start = 10.dp),
                onClick = onNavigationIconClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = contentColor
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.auth_cd_back),
                    tint = startColor
                )
            }
        },
        colors = topAppBarColors(
            containerColor = Color.Transparent, // Background is handled by the gradient brush
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer, // Or a color that contrasts well with your typical gradient
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer // Same as title
        )
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeGradientTopBar(
    onTelegramClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    isScrolled: Boolean = false,
    disableBlurAllOver: Boolean = false,
    useNewTopBar: Boolean = true,
    hearingGuardState: com.theveloper.pixelplay.data.hearingguard.HearingGuardState = com.theveloper.pixelplay.data.hearingguard.HearingGuardState(),
    onHearingGuardClick: () -> Unit = {},
    onEditHomeOrder: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHighest

    PixelPlayStatusBarStyle(color = surfaceContainerHigh)

    // ⚡ 新版顶栏（渐进模糊遮罩 + 半透明底色）仅在「开启新版顶栏」且未「禁用模糊效果」时生效；
    //   否则回退为与其它页面一致的纯色顶栏（不透明 surfaceContainerHighest）。
    val blurStyleEnabled = useNewTopBar && !disableBlurAllOver

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "topbar_alpha_transition"
    )

    TopAppBar(
        modifier = modifier
            .background(
                surfaceContainerHigh.copy(
                    alpha = if (blurStyleEnabled) animatedAlpha * 0.4f else animatedAlpha
                )
            )
            .then(
                if (blurStyleEnabled) {
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
            ),
        title = { /* nada, usamos solo acciones */ },
        navigationIcon = {
            /* Deleted Beta Label as requested */
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 编辑首页卡片顺序按钮（位于像素卫士胶囊左侧）
                if (onEditHomeOrder != null) {
                    FilledIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onEditHomeOrder
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.home_edit_card_order)
                        )
                    }
                }

                // 听力保护胶囊
                com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardCapsule(
                    state = hearingGuardState,
                    onClick = onHearingGuardClick
                )

                // 云端串流按钮
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = onTelegramClick
                ) {
                    Icon(
                         imageVector = Icons.Rounded.Cloud,
                         contentDescription = stringResource(R.string.presentation_batch_g_topbar_cd_telegram)
                    )
                }
            }
        },
        colors = topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}
