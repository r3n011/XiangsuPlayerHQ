@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.toPath
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.hearingguard.HearingGuardConfig
import com.theveloper.pixelplay.data.preferences.AppLanguage
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.MusicQuality
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardSetupDialog
import com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardViewModel
import com.theveloper.pixelplay.presentation.viewmodel.EqualizerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.utils.AppLocaleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 首次启动的新手引导（模仿 Rhythm 的 Onboarding 设计）：
 * 欢迎页（primary 底 + 旋转装饰 + 三胶囊按钮）→ 步骤式向导（HorizontalPager +
 * 按压圆角动画按钮 + 脉冲进度指示器）。
 */
@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun OnboardingScreen(
    themePreferencesRepository: ThemePreferencesRepository,
    userPreferencesRepository: UserPreferencesRepository,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    // 平板适配（与 Rhythm 一致：宽屏显示为 1080dp 圆角卡片）
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 840
    val contentMaxWidth = if (isTablet) 1080.dp else Dp.Infinity

    // 快捷设置所需的 ViewModel
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val equalizerViewModel: EqualizerViewModel = hiltViewModel()

    var showSteps by remember { mutableStateOf(false) }

    // ⚡ 完成过渡：点击完成页中心圆按钮后，引导界面从中间的圆开始扩散并淡出，透明过渡到主页
    var revealing by remember { mutableStateOf(false) }
    val revealProgress = remember { Animatable(0f) }
    val revealScope = rememberCoroutineScope()
    val onRevealFinish = remember {
        {
            if (!revealing) {
                revealing = true
                revealScope.launch {
                    revealProgress.snapTo(0f)
                    revealProgress.animateTo(
                        1f,
                        animationSpec = tween(650, easing = FastOutSlowInEasing)
                    )
                    onFinished()
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // 扩散圆基准尺寸（从中心圆按钮大小扩散到覆盖全屏：按对角线计算放大倍数）
        val circleBaseDp = 88.dp
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val diagonalDp = sqrt(w * w + h * h)
        val maxCircleScale = (diagonalDp / circleBaseDp.value).toFloat() * 1.3f

        // 引导内容：扩散时同步淡出
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - revealProgress.value.coerceIn(0f, 1f)
                }
        ) {
            if (!showSteps) {
                WelcomeContent(
                    themePreferencesRepository = themePreferencesRepository,
                    isTablet = isTablet,
                    contentMaxWidth = contentMaxWidth,
                    onGetStarted = { showSteps = true }
                )
            } else {
                StepsContent(
                    onFinished = onFinished,
                    onRevealFinish = onRevealFinish,
                    themePreferencesRepository = themePreferencesRepository,
                    userPreferencesRepository = userPreferencesRepository,
                    playerViewModel = playerViewModel,
                    equalizerViewModel = equalizerViewModel,
                    isTablet = isTablet,
                    contentMaxWidth = contentMaxWidth
                )
            }
        }

        // 扩散圆覆盖层：从中心圆向外放大并逐渐透明
        if (revealing || revealProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .graphicsLayer { alpha = (1f - revealProgress.value).coerceIn(0f, 1f) }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(circleBaseDp)
                        .graphicsLayer {
                            scaleX = revealProgress.value * maxCircleScale
                            scaleY = revealProgress.value * maxCircleScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 欢迎页
// ─────────────────────────────────────────────────────────────

@Composable
private fun WelcomeContent(
    themePreferencesRepository: ThemePreferencesRepository,
    isTablet: Boolean,
    contentMaxWidth: Dp,
    onGetStarted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appThemeMode by themePreferencesRepository.appThemeModeFlow
        .collectAsState(initial = AppThemeMode.FOLLOW_SYSTEM)
    val isDark = appThemeMode == AppThemeMode.DARK ||
        (appThemeMode == AppThemeMode.FOLLOW_SYSTEM && isSystemInDarkTheme())

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    // 语言切换弹窗状态（Rhythm：点击语言胶囊弹出选择弹窗）
    var showLanguageSwitcher by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(primary),
        contentAlignment = Alignment.Center
    ) {
        // 旋转背景装饰（Rhythm: onPrimary 0.08）
        RotatingBackgroundCookies(
            modifier = Modifier.fillMaxSize(),
            color = onPrimary.copy(alpha = 0.08f)
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (isTablet) Modifier.width(contentMaxWidth) else Modifier.fillMaxWidth())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (isTablet) 48.dp else 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // "Welcome to" & XiangsuPlayer 垂直居中
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_to),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (isTablet) 48.sp else 38.sp
                    ),
                    color = onPrimary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "XiangsuPlayer",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = if (isTablet) 72.sp else 56.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp
                    ),
                    color = onPrimary,
                    textAlign = TextAlign.Center
                )
            }

            // 底部三胶囊：明暗切换 / Get Started / 语言切换（Rhythm 1:1）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左：明暗切换胶囊
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 80.dp)
                        .clip(RoundedCornerShape(34.dp)) // pill shape
                        .background(onPrimary.copy(alpha = 0.15f))
                        .clickable {
                            val next = if (isDark) AppThemeMode.LIGHT else AppThemeMode.DARK
                            scope.launch { themePreferencesRepository.setAppThemeMode(next) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = stringResource(R.string.onboarding_toggle_theme),
                        tint = onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 中：Get Started
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onPrimary,
                        contentColor = primary
                    ),
                    shape = RoundedCornerShape(40.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_get_started),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                }

                // 右：语言切换胶囊
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 80.dp)
                        .clip(RoundedCornerShape(34.dp)) // pill shape
                        .background(onPrimary.copy(alpha = 0.15f))
                        .clickable {
                            showLanguageSwitcher = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = stringResource(R.string.onboarding_toggle_language),
                        tint = onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 语言切换弹窗（Rhythm：LanguageSwitcherBottomSheet 风格）
        if (showLanguageSwitcher) {
            LanguageSwitcherBottomSheet(
                onDismiss = { showLanguageSwitcher = false }
            )
        }
    }
}

/** 语言切换弹窗：列出全部支持语言，选中高亮，点击应用并重建生效 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSwitcherBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val currentLanguage = AppLocaleManager.currentLanguageTag(context)
    val options = remember { AppLanguage.getLanguageOptions(context) }
    val entries = remember(options) { options.entries.toList() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            text = stringResource(R.string.onboarding_select_language),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(entries, key = { _, it -> "lang_${it.key}" }) { index, (tag, label) ->
                val isSelected = currentLanguage == tag
                Card(
                    onClick = {
                        AppLocaleManager.applyLanguage(context, tag)
                        onDismiss()
                        // ⚡ MainActivity 为 ComponentActivity，需手动重建让语言立即生效
                        (context as? android.app.Activity)?.recreate()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = groupedBottomSheetItemShape(index, entries.size),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 分组弹窗条目圆角（首/尾 24dp，中间 8dp） */
private fun groupedBottomSheetItemShape(index: Int, count: Int): RoundedCornerShape {
    return when {
        count <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(8.dp)
    }
}

/** 欢迎页背景装饰（1:1 模仿 Rhythm）：左下 COOKIE_6 + 右上 COOKIE_12，下落碰撞入场 + 缓慢旋转 */
@Composable
private fun RotatingBackgroundCookies(
    modifier: Modifier = Modifier,
    color: Color
) {
    // 入场：两个 Cookie 从屏幕外下落，碰撞回弹（Rhythm 相同动画）
    val lowerY = remember { Animatable(-600f) }
    val upperY = remember { Animatable(-1000f) }

    LaunchedEffect(Unit) {
        // 1. Fall down
        launch {
            lowerY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        upperY.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 600, easing = LinearEasing)
        )
        // 2. Collision impact
        launch {
            lowerY.animateTo(
                targetValue = 40f,
                animationSpec = tween(durationMillis = 80, easing = FastOutLinearInEasing)
            )
            lowerY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            upperY.animateTo(
                targetValue = -60f,
                animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing)
            )
            upperY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // 缓慢旋转（Rhythm：下 50s / 上 60s 反向）
    val infiniteTransition = rememberInfiniteTransition(label = "cookieRotation")
    val rotationLower by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 50000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lowerRotation"
    )
    val rotationUpper by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "upperRotation"
    )

    val cookie6Shape = rememberCookieShape(sides = 6)
    val cookie12Shape = rememberCookieShape(sides = 12)

    Box(modifier = modifier) {
        // Lower cookie (COOKIE_6) 左下角
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(460.dp)
                .graphicsLayer {
                    translationX = -120.dp.toPx()
                    translationY = (140.dp.toPx() + lowerY.value.dp.toPx())
                    rotationZ = rotationLower + 15f
                }
                .clip(cookie6Shape)
                .background(color)
        )

        // Upper cookie (COOKIE_12) 右上角
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(340.dp)
                .graphicsLayer {
                    translationX = 80.dp.toPx()
                    translationY = (-100.dp.toPx() + upperY.value.dp.toPx())
                    rotationZ = rotationUpper - 20f
                }
                .clip(cookie12Shape)
                .background(color)
        )
    }
}

/** 将 MaterialShapes Cookie RoundedPolygon 转为 Compose Shape（用于 clip 背景） */
@Composable
private fun rememberCookieShape(sides: Int): Shape {
    val polygon = remember(sides) {
        when (sides) {
            6 -> MaterialShapes.Cookie6Sided
            else -> MaterialShapes.Cookie12Sided
        }
    }
    return remember(polygon) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val path = polygon.toPath().asComposePath()
                val bounds = path.getBounds()
                val scaleX = size.width / (bounds.width.takeIf { it > 0f } ?: 1f)
                val scaleY = size.height / (bounds.height.takeIf { it > 0f } ?: 1f)
                val scaledWidth = bounds.width * scaleX
                val scaledHeight = bounds.height * scaleY
                val translateX = (size.width - scaledWidth) / 2f - bounds.left * scaleX
                val translateY = (size.height - scaledHeight) / 2f - bounds.top * scaleY
                val matrix = Matrix().apply {
                    scale(scaleX, scaleY)
                    translate(translateX / scaleX, translateY / scaleY)
                }
                val scaledPath = Path().apply { addPath(path) }
                scaledPath.transform(matrix)
                return Outline.Generic(scaledPath)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 步骤向导
// ─────────────────────────────────────────────────────────────

private enum class OnboardingStep(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val tips: List<Int>
) {
    PERMISSIONS(
        Icons.Rounded.Storage,
        R.string.onboarding_step_permissions_title,
        R.string.onboarding_step_permissions_desc,
        listOf(
            R.string.onboarding_tip_permissions_1,
            R.string.onboarding_tip_permissions_2,
            R.string.onboarding_tip_permissions_3
        )
    ),
    MEDIA_SCAN(
        Icons.Rounded.LibraryMusic,
        R.string.onboarding_step_media_title,
        R.string.onboarding_step_media_desc,
        listOf(
            R.string.onboarding_tip_media_1,
            R.string.onboarding_tip_media_2,
            R.string.onboarding_tip_media_3
        )
    ),
    EQUALIZER(
        Icons.Rounded.Equalizer,
        R.string.onboarding_step_equalizer_title,
        R.string.onboarding_step_equalizer_desc,
        listOf(
            R.string.onboarding_tip_equalizer_1,
            R.string.onboarding_tip_equalizer_2,
            R.string.onboarding_tip_equalizer_3
        )
    ),
    AUDIO_PLAYBACK(
        Icons.Rounded.Speed,
        R.string.onboarding_step_audio_title,
        R.string.onboarding_step_audio_desc,
        listOf(
            R.string.onboarding_tip_audio_1,
            R.string.onboarding_tip_audio_2,
            R.string.onboarding_tip_audio_3
        )
    ),
    HEARING_GUARD(
        Icons.Rounded.Headphones,
        R.string.onboarding_step_hearing_title,
        R.string.onboarding_step_hearing_desc,
        listOf(
            R.string.onboarding_tip_hearing_1,
            R.string.onboarding_tip_hearing_2,
            R.string.onboarding_tip_hearing_3
        )
    ),
    AI_ASSISTANT(
        Icons.Rounded.SmartToy,
        R.string.onboarding_step_ai_title,
        R.string.onboarding_step_ai_desc,
        listOf(
            R.string.onboarding_tip_ai_1,
            R.string.onboarding_tip_ai_2,
            R.string.onboarding_tip_ai_3
        )
    ),
    LYRICS_VISUALS(
        Icons.Rounded.Lyrics,
        R.string.onboarding_step_lyrics_title,
        R.string.onboarding_step_lyrics_desc,
        listOf(
            R.string.onboarding_tip_lyrics_1,
            R.string.onboarding_tip_lyrics_2,
            R.string.onboarding_tip_lyrics_3
        )
    ),
    GESTURES(
        Icons.Rounded.Gesture,
        R.string.onboarding_step_gestures_title,
        R.string.onboarding_step_gestures_desc,
        listOf(
            R.string.onboarding_tip_gestures_1,
            R.string.onboarding_tip_gestures_2,
            R.string.onboarding_tip_gestures_3
        )
    ),
    THEME(
        Icons.Rounded.Palette,
        R.string.onboarding_step_theme_title,
        R.string.onboarding_step_theme_desc,
        listOf(
            R.string.onboarding_tip_theme_1,
            R.string.onboarding_tip_theme_2,
            R.string.onboarding_tip_theme_3
        )
    ),
    FINISHED(
        Icons.Rounded.CheckCircle,
        R.string.onboarding_step_finished_title,
        R.string.onboarding_step_finished_desc,
        emptyList()
    )
}

@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
private fun StepsContent(
    onFinished: () -> Unit,
    onRevealFinish: () -> Unit,
    themePreferencesRepository: ThemePreferencesRepository,
    userPreferencesRepository: UserPreferencesRepository,
    playerViewModel: PlayerViewModel,
    equalizerViewModel: EqualizerViewModel,
    isTablet: Boolean,
    contentMaxWidth: Dp
) {
    val steps = OnboardingStep.entries
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val isLast = pagerState.currentPage == steps.lastIndex
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // 背景装饰（Rhythm 1:1）：Cookie 图案 + 下落碰撞入场 + 缓慢旋转
        RotatingBackgroundCookies(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
        )

        // 布局优化：去掉淡蓝色底层容器（surfaceContainerHigh 整层），卡片直接位于背景上；
        // 平板仅保留居中约束（90% 宽高、上限 750dp），不再叠加第二层圆角卡片
        // ⚡ statusBarsPadding：避免顶部内容被通知栏/状态栏遮挡
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .let {
                    if (isTablet) {
                        it
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth(0.9f)
                            .heightIn(max = 750.dp)
                            .fillMaxHeight(0.9f)
                    } else {
                        it
                    }
                }
        ) {
        // 步骤卡片（HorizontalPager）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            key = { steps[it].name }
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val scale = 0.96f + 0.04f * (1f - abs(pageOffset).coerceIn(0f, 1f))
            val alpha = 1f - abs(pageOffset).coerceIn(0f, 1f) * 0.3f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.scaleX = scale
                        this.scaleY = scale
                        this.alpha = alpha
                    }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (page == steps.lastIndex) {
                    // 完成页：中间大圆按钮，点击后圆扩散淡出过渡到主页
                    FinishedContent(onClick = onRevealFinish)
                } else {
                    StepCard(
                        step = steps[page],
                        themePreferencesRepository = themePreferencesRepository,
                        userPreferencesRepository = userPreferencesRepository,
                        playerViewModel = playerViewModel,
                        equalizerViewModel = equalizerViewModel
                    )
                }
            }
        }

        // 底部导航栏：返回 / 应用图标 + Step x/y / 下一步（平板和手机统一）
        // ⚡ 完成页隐藏底部导航栏，只保留中心圆按钮作为唯一操作入口
        if (pagerState.currentPage != steps.lastIndex) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮（spring 动画进出）
                AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    enter = fadeIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + expandHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    exit = fadeOut(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + shrinkHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                ) {
                    OnboardingBackButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        modifier = Modifier.height(48.dp)
                    )
                }

                // 应用图标 + Step x/y（居中，非圆点）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.pixelplay_base_monochrome),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    AnimatedContent(
                        targetState = pagerState.currentPage,
                        transitionSpec = {
                            (slideInVertically { height -> height / 2 } + fadeIn()).togetherWith(
                                slideOutVertically { height -> -height / 2 } + fadeOut()
                            )
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        label = "progressText"
                    ) { step ->
                        Text(
                            text = stringResource(
                                R.string.onboarding_step_of,
                                step + 1,
                                steps.size
                            ),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OnboardingNextButton(
                    text = stringResource(
                        if (isLast) R.string.onboarding_finish else R.string.onboarding_next
                    ),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isLast) {
                            onFinished()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.height(48.dp)
                )
            }
        }
        }
    }
}
}

/** 步骤卡片（Rhythm 丰富布局：大图标 + 标题 + 描述 + 亮点功能卡 + 快捷设置交互区） */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun StepCard(
    step: OnboardingStep,
    themePreferencesRepository: ThemePreferencesRepository,
    userPreferencesRepository: UserPreferencesRepository,
    playerViewModel: PlayerViewModel,
    equalizerViewModel: EqualizerViewModel
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 大图标（Rhythm：72dp 主色，无圆底，scaleIn 入场）
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.scaleIn() + fadeIn()
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // 标题
            Text(
                text = stringResource(step.titleRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            // 描述
            Text(
                text = stringResource(step.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(24.dp))

            // 亮点功能卡（Rhythm：primaryContainer + 标题 + 亮点条目列表）
            if (step.tips.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.onboarding_features),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        step.tips.forEach { tipRes ->
                            LibraryTipItem(
                                icon = Icons.Rounded.CheckCircle,
                                text = stringResource(tipRes)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 快捷设置交互区（Rhythm：每步下方提供实际可操作的设置）
            when (step) {
                OnboardingStep.PERMISSIONS -> PermissionStepContent()
                OnboardingStep.MEDIA_SCAN -> MediaScanStepContent(playerViewModel)
                OnboardingStep.EQUALIZER -> EqualizerStepContent(equalizerViewModel)
                OnboardingStep.AUDIO_PLAYBACK -> AudioStepContent(userPreferencesRepository)
                OnboardingStep.HEARING_GUARD -> HearingGuardStepContent()
                OnboardingStep.AI_ASSISTANT -> AiAssistantStepContent()
                OnboardingStep.LYRICS_VISUALS -> LyricsStepContent(themePreferencesRepository)
                OnboardingStep.GESTURES -> GestureStepContent()
                OnboardingStep.THEME -> ThemeStepContent(themePreferencesRepository)
                else -> {}
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 亮点条目（Rhythm LibraryTipItem）：图标 + 文字 */
@Composable
private fun LibraryTipItem(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

/** 通用快捷设置开关行（Rhythm 风格设置卡）：图标 + 标题 + Switch */
@Composable
private fun QuickSettingSwitch(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors()
            )
        }
    }
}

/** 均衡器步骤快捷设置：启用均衡器 + 启用后可选预设 */
@Composable
private fun EqualizerStepContent(equalizerViewModel: EqualizerViewModel) {
    val uiState by equalizerViewModel.uiState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickSettingSwitch(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.onboarding_quick_enable_equalizer),
            checked = uiState.isEnabled,
            onCheckedChange = { equalizerViewModel.toggleEqualizer() }
        )
        // ⚡ 启用后直接可选具体均衡器预设（真实生效）
        if (uiState.isEnabled) {
            Text(
                text = stringResource(R.string.onboarding_quick_equalizer_preset),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
            val presets = uiState.allAvailablePresets
            presets.forEach { preset ->
                val selected = uiState.currentPreset == preset
                Surface(
                    onClick = { equalizerViewModel.selectPreset(preset) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 播放音质步骤快捷设置：Hi-Fi 模式 + 交叉淡入 + 在线音质 */
@Composable
private fun AudioStepContent(userPreferencesRepository: UserPreferencesRepository) {
    val scope = rememberCoroutineScope()
    val hiFiEnabled by userPreferencesRepository.hiFiModeEnabledFlow.collectAsState(initial = false)
    val crossfadeEnabled by userPreferencesRepository.isCrossfadeEnabledFlow.collectAsState(initial = false)
    val qualityValue by userPreferencesRepository.musicQualityValueFlow.collectAsState(initial = "320k")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickSettingSwitch(
            icon = Icons.Rounded.Speed,
            title = stringResource(R.string.onboarding_quick_hifi),
            checked = hiFiEnabled,
            onCheckedChange = { scope.launch { userPreferencesRepository.setHiFiModeEnabled(it) } }
        )
        QuickSettingSwitch(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.onboarding_quick_crossfade),
            checked = crossfadeEnabled,
            onCheckedChange = { scope.launch { userPreferencesRepository.setCrossfadeEnabled(it) } }
        )
        // ⚡ 在线播放音质选择（真实生效）
        Text(
            text = stringResource(R.string.onboarding_quick_online_quality),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        MusicQuality.entries.forEach { quality ->
            val selected = quality.lxValue == qualityValue
            Surface(
                onClick = { scope.launch { userPreferencesRepository.setMusicQualityValue(quality.lxValue) } },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(quality.labelResId),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 歌词步骤快捷设置：歌词绚丽背景 + 播放器绚丽背景 */
@Composable
private fun LyricsStepContent(themePreferencesRepository: ThemePreferencesRepository) {
    val scope = rememberCoroutineScope()
    val auroraEnabled by themePreferencesRepository.lyricsGradientOverlayEnabledFlow
        .collectAsState(initial = true)
    val playerBgEnabled by themePreferencesRepository.customPlayerBackgroundEnabledFlow
        .collectAsState(initial = false)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickSettingSwitch(
            icon = Icons.Rounded.AutoAwesome,
            title = stringResource(R.string.onboarding_quick_lyrics_aurora),
            checked = auroraEnabled,
            onCheckedChange = { scope.launch { themePreferencesRepository.setLyricsGradientOverlayEnabled(it) } }
        )
        QuickSettingSwitch(
            icon = Icons.Rounded.Wallpaper,
            title = stringResource(R.string.onboarding_quick_player_background),
            checked = playerBgEnabled,
            onCheckedChange = { scope.launch { themePreferencesRepository.setCustomPlayerBackgroundEnabled(it) } }
        )
    }
}

/** 权限步骤交互：自动请求 + 状态展示 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionStepContent() {
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissions)
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            delay(400)
            permissionState.launchMultiplePermissionRequest()
        }
    }

    if (permissionState.allPermissionsGranted) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.onboarding_step_permissions_granted),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                permissionState.launchMultiplePermissionRequest()
            },
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(stringResource(R.string.onboarding_step_permissions_request))
        }
    }
}

/** 主题步骤交互：明暗选择 */
@Composable
private fun ThemeStepContent(themePreferencesRepository: ThemePreferencesRepository) {
    val scope = rememberCoroutineScope()
    val appThemeMode by themePreferencesRepository.appThemeModeFlow
        .collectAsState(initial = AppThemeMode.FOLLOW_SYSTEM)

    val options = listOf(
        AppThemeMode.FOLLOW_SYSTEM to R.string.onboarding_theme_follow_system,
        AppThemeMode.LIGHT to R.string.onboarding_theme_light,
        AppThemeMode.DARK to R.string.onboarding_theme_dark
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { (mode, labelRes) ->
            val selected = appThemeMode == mode
            Surface(
                onClick = { scope.launch { themePreferencesRepository.setAppThemeMode(mode) } },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 按钮（按压圆角动画，模仿 Rhythm）
// ─────────────────────────────────────────────────────────────

/** 媒体库扫描步骤：一键扫描本地媒体库（真实触发 SyncManager 全量同步） */
@Composable
private fun MediaScanStepContent(playerViewModel: PlayerViewModel) {
    val isSyncing by playerViewModel.isSyncingStateFlow.collectAsStateWithLifecycle()
    var scanRequested by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(
            onClick = {
                playerViewModel.rescanLibrary()
                scanRequested = true
            },
            enabled = !isSyncing,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isSyncing && scanRequested) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_quick_media_scanning))
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (scanRequested) R.string.onboarding_quick_media_rescan
                        else R.string.onboarding_quick_media_scan
                    )
                )
            }
        }
    }
}

/** 像素卫士步骤：启用 + 初始化设置（复用系统设置弹窗，真实生效） */
@Composable
private fun HearingGuardStepContent() {
    val hearingGuardViewModel: HearingGuardViewModel = hiltViewModel()
    val state by hearingGuardViewModel.state.collectAsStateWithLifecycle()
    var showSetup by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickSettingSwitch(
            icon = Icons.Rounded.Headphones,
            title = stringResource(R.string.onboarding_quick_hearing_enable),
            checked = state.enabled,
            onCheckedChange = { hearingGuardViewModel.setEnabled(it) }
        )
        FilledTonalButton(
            onClick = { showSetup = true },
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (state.isConfigured) R.string.onboarding_quick_hearing_reconfigure
                    else R.string.onboarding_quick_hearing_setup
                )
            )
        }
    }
    if (showSetup) {
        HearingGuardSetupDialog(
            currentConfig = state.config,
            isEnabled = state.enabled,
            onEnabledChange = { hearingGuardViewModel.setEnabled(it) },
            onConfirm = { config ->
                hearingGuardViewModel.setConfig(config)
                showSetup = false
            },
            onDisable = {
                hearingGuardViewModel.clearConfig()
                showSetup = false
            },
            onDismiss = { showSetup = false }
        )
    }
}

/** AI 陪伴助手步骤：选择服务商 + 填入 API Key（真实保存到 AiPreferencesRepository） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiAssistantStepContent() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val provider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val apiKey by settingsViewModel.currentAiApiKey.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf("") }
    LaunchedEffect(provider) {
        keyInput = apiKey
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.onboarding_quick_ai_provider),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        AiProvider.entries.forEach { p ->
            val selected = provider == p.name
            Surface(
                onClick = { settingsViewModel.onAiProviderChange(p.name) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = p.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = keyInput,
            onValueChange = { value ->
                keyInput = value
                settingsViewModel.onAiApiKeyChange(value)
            },
            label = { Text(stringResource(R.string.onboarding_quick_ai_api_key)) },
            placeholder = { Text(stringResource(R.string.onboarding_quick_ai_api_key_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 手势控制步骤：仅提示左右滑动切歌 + 点击进专辑，并带循环动图演示 */
@Composable
private fun GestureStepContent() {
    val infinite = rememberInfiniteTransition(label = "gestureDemo")
    val swipeX by infinite.animateFloat(
        initialValue = -60f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swipeX"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 动图演示：封面卡片左右滑动 + 中间点击脉冲
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .graphicsLayer {
                        translationX = swipeX
                        rotationZ = swipeX * 0.12f
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 8.dp)
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-8).dp)
            )
        }
        GestureHintRow(Icons.Rounded.Swipe, stringResource(R.string.onboarding_quick_gesture_swipe))
        GestureHintRow(Icons.Rounded.TouchApp, stringResource(R.string.onboarding_quick_gesture_tap))
    }
}

/** 手势提示行：图标 + 文字 */
@Composable
private fun GestureHintRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 完成页：中间大圆按钮（应用图标），点击后圆扩散淡出过渡到主页 */
@Composable
private fun FinishedContent(onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 10.dp,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.pixelplay_base_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(58.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_step_finished_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_step_finished_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.onboarding_quick_finished_tap),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

/** 下一步按钮（Rhythm 1:1）：Button，按压圆角收缩，文字 Bold + 箭头图标；isFirst/isLast 控制相连侧小圆角 */
@Composable
private fun OnboardingNextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    isFirst: Boolean = true,
    isLast: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val halfR = height / 2
    val pressedR = height * 0.3f
    val animCorner by animateDpAsState(
        targetValue = if (isPressed) pressedR else halfR,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "onboardingNextCorner"
    )
    val innerCorner = 12.dp
    val shape = RoundedCornerShape(
        topStart = if (isFirst) animCorner else innerCorner,
        bottomStart = if (isFirst) animCorner else innerCorner,
        topEnd = if (isLast) animCorner else innerCorner,
        bottomEnd = if (isLast) animCorner else innerCorner
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 返回按钮（Rhythm 1:1）：FilledTonalButton，按压圆角收缩，图标 + 文字；isFirst/isLast 控制相连侧小圆角 */
@Composable
private fun OnboardingBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    text: String = stringResource(R.string.onboarding_back),
    isFirst: Boolean = true,
    isLast: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val halfR = height / 2
    val pressedR = height * 0.3f
    val animCorner by animateDpAsState(
        targetValue = if (isPressed) pressedR else halfR,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "onboardingBackCorner"
    )
    val innerCorner = 12.dp
    val shape = RoundedCornerShape(
        topStart = if (isFirst) animCorner else innerCorner,
        bottomStart = if (isFirst) animCorner else innerCorner,
        topEnd = if (isLast) animCorner else innerCorner,
        bottomEnd = if (isLast) animCorner else innerCorner
    )
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium
            )
        )
    }
}
