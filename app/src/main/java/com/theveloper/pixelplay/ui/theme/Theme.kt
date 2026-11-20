package com.theveloper.pixelplay.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.flow.map

val LocalPixelPlayDarkTheme = staticCompositionLocalOf { false }
val LocalShowScrollbar = staticCompositionLocalOf { true }

/**
 * 当前应用字体（设置 → 外观 → 应用字体）。
 *
 * 主题 Typography 已在根部统一替换，但大量组件显式写了 `fontFamily = GoogleSansRounded`，
 * 会盖过 Typography 导致「换字体很多地方不生效」。这些组件一律改为读取本 CompositionLocal，
 * 由 [PixelPlayTheme] 在根部提供真实当前字体；默认回落主题默认（Google Sans Rounded）。
 * 歌词文字有独立字体设置（resolveLyricsFontFamily），不走这里。
 */
val LocalAppFontFamily = staticCompositionLocalOf<androidx.compose.ui.text.font.FontFamily> {
    GoogleSansRounded
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun PixelPlayStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

val DarkColorScheme = darkColorScheme(
    primary = PixelPlayPurplePrimary,
    secondary = PixelPlayPink,
    tertiary = PixelPlayOrange,
    background = PixelPlayPurpleDark,
    surface = PixelPlaySurface,
    onPrimary = PixelPlayWhite,
    onSecondary = PixelPlayWhite,
    onTertiary = PixelPlayWhite,
    onBackground = PixelPlayWhite,
    onSurface = PixelPlayLightPurple, // Texto sobre superficies
    error = Color(0xFFFF5252),
    onError = PixelPlayWhite
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = PixelPlayWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = PixelPlayPink,
    onSecondary = PixelPlayWhite,
    secondaryContainer = PixelPlayPink.copy(alpha = 0.15f),
    onSecondaryContainer = PixelPlayPink.copy(alpha = 0.85f),
    tertiary = PixelPlayOrange,
    onTertiary = PixelPlayBlack,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.6f),
    surfaceTint = LightPrimary,
    error = Color(0xFFD32F2F),
    onError = PixelPlayWhite
)

@Composable
fun PixelPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val finalColorScheme = when {
        colorSchemePairOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Tema dinámico del sistema como prioridad si no hay override
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (e: Exception) {
                // Fallback a los defaults si dynamic colors falla (raro, pero posible en algunos dispositivos)
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        colorSchemePairOverride != null -> {
            // Usar el esquema del álbum si se proporciona
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        // Fallback final a los defaults si no hay override ni dynamic colors aplicables
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // ⚡ 应用字体（设置 → 外观）：只作用于歌词之外的软件界面。
    //   在主题根部统一读取偏好，所有使用 PixelPlayTheme 的界面自动生效；
    //   歌词文字有独立的字体设置（resolveLyricsFontFamily），不受此处影响。
    val appFontKey by context.dataStore.data
        .map { preferences ->
            preferences[ThemePreferencesRepository.APP_FONT_FAMILY]
                ?: ThemePreferencesRepository.DEFAULT_APP_FONT_FAMILY
        }
        .collectAsStateWithLifecycle(
            initialValue = ThemePreferencesRepository.DEFAULT_APP_FONT_FAMILY
        )
    val appFontFamily = remember(appFontKey) { resolveAppFontFamily(context, appFontKey) }
    val appTypography = remember(appFontFamily) { Typography.withAppFontFamily(appFontFamily) }

    PixelPlayStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    CompositionLocalProvider(
        LocalPixelPlayDarkTheme provides darkTheme,
        LocalAppFontFamily provides appFontFamily
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = appTypography,
            shapes = Shapes,
            content = content
        )
    }
}
