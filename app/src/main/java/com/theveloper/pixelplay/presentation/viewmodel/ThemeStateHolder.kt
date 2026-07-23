package com.theveloper.pixelplay.presentation.viewmodel

import android.net.Uri
import android.util.Log
import android.content.ComponentCallbacks2
import android.os.Trace
import androidx.compose.ui.graphics.Color
import com.theveloper.pixelplay.data.preferences.AlbumArtColorAccuracy
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.clearExtractedColorCache
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ⚡ 核心原子状态容器：所有专辑封面主题相关的值（颜色方案、URI、有效颜色方案）
 *   都封装在单一对象中，确保原子更新。UI 层永远只会看到"一致"的状态，
 *   不会出现"颜色变了但 URI 没变"或反之的中间状态。
 */
data class AlbumArtThemeState(
    val colorSchemePair: ColorSchemePair? = null,
    val albumArtUri: String? = null,
    val activeColorSchemePair: ColorSchemePair? = null
)

@Singleton
class ThemeStateHolder @Inject constructor(
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val themePreferencesRepository: ThemePreferencesRepository
) {

    private var scope: CoroutineScope? = null
    @Volatile
    private var currentPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default
    @Volatile
    private var currentPaletteAccuracy: Int = AlbumArtColorAccuracy.DEFAULT
    @Volatile
    private var currentThemePreference: String = ThemePreference.ALBUM_ART
    @Volatile
    private var currentCustomPaletteSeedColor: Int = ThemePreferencesRepository.DEFAULT_CUSTOM_PALETTE_SEED
    @Volatile
    private var currentCustomPaletteSchemePair: ColorSchemePair? = null
    // ⚡ 原子目标 URI：确保并发的 extractAndGenerateColorScheme 协程中，只有持有最新
    //   目标 URI 的协程才能更新 state。其他协程（为旧歌曲提取的）完成后直接丢弃。
    @Volatile
    private var targetSongUri: String? = null

    // ⚡ 单一的、原子的主题状态容器
    //   UI 层通过 collectAsStateWithLifecycle 监听它，每次更新只能看到一个完整的、
    //   自洽的 AlbumArtThemeState，不会出现多个 StateFlow 之间不同步的中间状态。
    private val _albumArtThemeState = MutableStateFlow(AlbumArtThemeState())
    val albumArtThemeState: StateFlow<AlbumArtThemeState> = _albumArtThemeState.asStateFlow()

    // ⚡ 独立的 MutableStateFlow：与 _albumArtThemeState 在 updateAlbumArtThemeState 中
    //   同步更新，保持三者一致。这些供其他代码（如 PlayerViewModel 导出）使用。
    private val _currentAlbumArtColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = _currentAlbumArtColorSchemePair.asStateFlow()

    private val _currentAlbumArtUri = MutableStateFlow<String?>(null)
    val currentAlbumArtUri: StateFlow<String?> = _currentAlbumArtUri.asStateFlow()

    private val _activePlayerColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = _activePlayerColorSchemePair.asStateFlow()

    // ⚡ 全局主题覆盖：用于 Android 10 等无系统取色的设备，把自定义调色盘应用到整个 App
    private val _activeGlobalColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val activeGlobalColorSchemePair: StateFlow<ColorSchemePair?> = _activeGlobalColorSchemePair.asStateFlow()

    private val _lavaLampColors = MutableStateFlow<ImmutableList<Color>>(persistentListOf())
    val lavaLampColors: StateFlow<ImmutableList<Color>> = _lavaLampColors.asStateFlow()

    private val playerThemePreference = themePreferencesRepository.playerThemePreferenceFlow

    /**
     * ⚡ 原子更新：确保 colorSchemePair、uri、activeColorSchemePair 在同一个挂起点更新。
     *   同时做去重：如果新 URI 和已有 URI 相同且已有颜色方案，不做更新（避免无谓的 UI 重组）。
     *
     *   所有 4 个 StateFlow（主容器 + 3 个派生）在同一个函数调用中更新，
     *   保证 UI 层看到的状态始终一致。
     */
    private fun resolveActiveSchemeForPreference(preference: String): ColorSchemePair? {
        return when (preference) {
            ThemePreference.ALBUM_ART -> _albumArtThemeState.value.colorSchemePair
            ThemePreference.CUSTOM_PALETTE -> currentCustomPaletteSchemePair
            else -> null
        }
    }

    private fun resolveGlobalSchemeForPreference(preference: String): ColorSchemePair? {
        // 仅自定义调色盘需要覆盖全局主题；封面取色只作用于播放器
        return if (preference == ThemePreference.CUSTOM_PALETTE) currentCustomPaletteSchemePair else null
    }

    private fun updateAlbumArtThemeState(colorSchemePair: ColorSchemePair?, uri: String?) {
        val current = _albumArtThemeState.value

        // 去重：如果 URI 相同且已有有效的颜色方案，不更新
        if (uri != null && current.albumArtUri != null && current.albumArtUri == uri && current.colorSchemePair != null) {
            return
        }

        // ⚡ 原子更新：4 个 StateFlow 在同一个挂起点连续更新
        val active = resolveActiveSchemeForPreference(currentThemePreference)
        val newState = AlbumArtThemeState(
            colorSchemePair = colorSchemePair,
            albumArtUri = uri,
            activeColorSchemePair = active
        )
        _albumArtThemeState.value = newState
        _currentAlbumArtColorSchemePair.value = colorSchemePair
        _currentAlbumArtUri.value = uri
        _activePlayerColorSchemePair.value = active
        _activeGlobalColorSchemePair.value = resolveGlobalSchemeForPreference(currentThemePreference)
    }

    /**
     * 当用户切换主题偏好时更新 activeColorSchemePair，但不改变 colorSchemePair/albumArtUri。
     */
    private fun updateActiveSchemeForPreference(preference: String) {
        currentThemePreference = preference
        val current = _albumArtThemeState.value
        val active = resolveActiveSchemeForPreference(preference)
        if (current.activeColorSchemePair != active || _activeGlobalColorSchemePair.value != resolveGlobalSchemeForPreference(preference)) {
            _albumArtThemeState.value = current.copy(activeColorSchemePair = active)
            _activePlayerColorSchemePair.value = active
            _activeGlobalColorSchemePair.value = resolveGlobalSchemeForPreference(preference)
        }
    }

    fun initialize(scope: CoroutineScope) {
        this.scope = scope

        // ⚡ 缓存主题偏好值，并响应偏好切换
        scope.launch {
            playerThemePreference.collect { pref ->
                updateActiveSchemeForPreference(pref)
            }
        }

        // ⚡ 自定义调色盘：根据用户选择的种子色 + 当前风格生成配色方案
        scope.launch {
            combine(
                themePreferencesRepository.customPaletteSeedColorFlow,
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.albumArtColorAccuracyFlow
            ) { seed, style, accuracy -> Triple(seed, style, accuracy) }
                .collect { (seed, style, accuracy) ->
                    val changed = currentCustomPaletteSeedColor != seed ||
                            currentPaletteStyle != style ||
                            currentPaletteAccuracy != accuracy
                    if (!changed && currentCustomPaletteSchemePair != null) return@collect

                    currentCustomPaletteSeedColor = seed
                    currentPaletteStyle = style
                    currentPaletteAccuracy = accuracy

                    val scheme = withContext(Dispatchers.IO) {
                        generateColorSchemeFromSeed(
                            seedColor = Color(seed),
                            paletteStyle = style
                        )
                    }
                    currentCustomPaletteSchemePair = scheme

                    // 如果当前正在使用自定义调色盘，立即刷新播放器/全局主题
                    updateActiveSchemeForPreference(currentThemePreference)
                    updateLavaLampColors(resolveActiveSchemeForPreference(currentThemePreference))
                }
        }

        scope.launch {
            combine(
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.albumArtColorAccuracyFlow
            ) { style, accuracy -> style to accuracy }
                .collect { (style, accuracy) ->
                    val paletteChanged =
                        currentPaletteStyle != style || currentPaletteAccuracy != accuracy
                    currentPaletteStyle = style
                    currentPaletteAccuracy = accuracy

                    if (!paletteChanged) return@collect

                    val uri = _albumArtThemeState.value.albumArtUri ?: return@collect
                    val refreshedScheme = colorSchemeProcessor.getOrGenerateColorScheme(
                        albumArtUri = uri,
                        paletteStyle = style,
                        colorAccuracyLevel = accuracy
                    )
                    updateAlbumArtThemeState(refreshedScheme, uri)
                    individualAlbumColorSchemes[uri]?.value = refreshedScheme
                }
        }

        scope.launch {
            albumArtThemeState.collect { state ->
                updateLavaLampColors(state.activeColorSchemePair)
            }
        }
    }

    suspend fun extractAndGenerateColorScheme(albumArtUriAsUri: Uri?, currentSongUriString: String?, isPreload: Boolean = false) {
        Trace.beginSection("ThemeStateHolder.extractAndGenerateColorScheme")
        try {
            Log.w("PixelPlay_Debug", "[Theme] extractAndGenerateColorScheme: " +
                "uri=${albumArtUriAsUri?.toString()?.take(30)}, " +
                "currentSongUriString=${currentSongUriString?.take(30)}, " +
                "isPreload=$isPreload")

            // ⚡ 原子目标 URI 检查：先声明本协程的目标，然后在提取完成后验证是否仍然是最新目标。
            //   如果在此期间有新的 extractAndGenerateColorScheme 调用（为不同的 URI，
            //   本协程完成后将被丢弃，防止旧颜色覆盖新颜色。
            val myTarget = currentSongUriString
            targetSongUri = myTarget

            if (albumArtUriAsUri == null) {
                if (!isPreload && currentSongUriString == null) {
                    // URI 和 target 都是 null，直接更新
                    Log.w("PixelPlay_Debug", "  → uri 为 null，清空 schemePair 和 uri")
                    // 在更新前再次检查目标是否仍然是本协程的目标
                    if (targetSongUri == myTarget) {
                        updateAlbumArtThemeState(null, null)
                    }
                } else {
                    Log.w("PixelPlay_Debug", "  → uri 为 null，isPreload=$isPreload, currentSongUriString=$currentSongUriString → 跳过")
                }
                return
            }

            val uriString = albumArtUriAsUri.toString()
            val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(
                albumArtUri = uriString,
                paletteStyle = currentPaletteStyle,
                colorAccuracyLevel = currentPaletteAccuracy
            )
            Log.w("PixelPlay_Debug", "  → 颜色提取完成: uriString=${uriString.take(30)}, " +
                "校验条件: isPreload=$isPreload, currentSongUriString==uriString: ${currentSongUriString == uriString}, " +
                "targetMatch=${targetSongUri == myTarget}, " +
                "最终结果: ${!isPreload && currentSongUriString == uriString && targetSongUri == myTarget}")

            if (!isPreload && currentSongUriString == uriString && targetSongUri == myTarget) {
                Log.w("PixelPlay_Debug", "  → ✅ 校验通过（目标 URI 仍匹配），原子设置 schemePair=$schemePair 和 albumArtUri=$uriString")
                updateAlbumArtThemeState(schemePair, uriString)
            } else {
                Log.w("PixelPlay_Debug", "  → ⏭️  校验未通过，不设置 schemePair (歌曲可能已变化或目标 URI 已过期")
            }
        } catch (e: Exception) {
            Log.w("PixelPlay_Debug", "  → ❌ 颜色提取异常: ${e.message}")
            if (!isPreload && albumArtUriAsUri != null && currentSongUriString == albumArtUriAsUri.toString() && targetSongUri == currentSongUriString) {
                updateAlbumArtThemeState(null, null)
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun updateLavaLampColors(schemePair: ColorSchemePair?) {
        if (schemePair == null) return

        val schemeForLava = schemePair.dark
        _lavaLampColors.update {
            listOf(schemeForLava.primary, schemeForLava.secondary, schemeForLava.tertiary).distinct().toImmutableList()
        }
    }

    // LRU Cache for individual album schemes
    private val individualAlbumColorSchemes = object : LinkedHashMap<String, MutableStateFlow<ColorSchemePair?>>(
        32, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableStateFlow<ColorSchemePair?>>?): Boolean {
            return size > 96
        }
    }

    private val emptyAlbumColorScheme = MutableStateFlow<ColorSchemePair?>(null).asStateFlow()
    private val pendingAlbumColorSchemeLock = Any()
    private val pendingAlbumColorSchemeTargets = mutableMapOf<String, MutableSet<MutableStateFlow<ColorSchemePair?>>>()

    private fun requestAlbumColorSchemeGeneration(
        uriString: String,
        targetFlow: MutableStateFlow<ColorSchemePair?>
    ) {
        if (uriString.isBlank()) return

        val shouldStartRequest = synchronized(pendingAlbumColorSchemeLock) {
            val existingTargets = pendingAlbumColorSchemeTargets[uriString]
            if (existingTargets != null) {
                existingTargets.add(targetFlow)
                false
            } else {
                pendingAlbumColorSchemeTargets[uriString] = mutableSetOf(targetFlow)
                true
            }
        }

        if (!shouldStartRequest) return

        val requestScope = scope
        if (requestScope == null) {
            synchronized(pendingAlbumColorSchemeLock) {
                pendingAlbumColorSchemeTargets.remove(uriString)
            }
            return
        }

        requestScope.launch(Dispatchers.IO) {
            var scheme: ColorSchemePair? = null
            try {
                scheme = colorSchemeProcessor.getOrGenerateColorScheme(
                    albumArtUri = uriString,
                    paletteStyle = currentPaletteStyle,
                    colorAccuracyLevel = currentPaletteAccuracy
                )
            } catch (_: Exception) {
                // Ignore or log
            } finally {
                val targets = synchronized(pendingAlbumColorSchemeLock) {
                    pendingAlbumColorSchemeTargets.remove(uriString)?.toList().orEmpty()
                }
                targets.forEach { it.value = scheme }
            }
        }
    }

    fun getAlbumColorSchemeFlow(
        uriString: String,
        eager: Boolean = true
    ): StateFlow<ColorSchemePair?> {
        if (uriString.isBlank()) return emptyAlbumColorScheme

        val existingFlow = individualAlbumColorSchemes[uriString]
        if (existingFlow != null) {
            if (eager && existingFlow.value == null) {
                requestAlbumColorSchemeGeneration(uriString, existingFlow)
            }
            return existingFlow.asStateFlow()
        }

        val newFlow = MutableStateFlow<ColorSchemePair?>(null)
        individualAlbumColorSchemes[uriString] = newFlow

        if (eager) {
            requestAlbumColorSchemeGeneration(uriString, newFlow)
        }

        return newFlow.asStateFlow()
    }

    fun ensureAlbumColorScheme(uriString: String) {
        if (uriString.isBlank()) return

        val targetFlow = individualAlbumColorSchemes[uriString]
            ?: MutableStateFlow<ColorSchemePair?>(null).also { individualAlbumColorSchemes[uriString] = it }

        if (targetFlow.value != null) return
        requestAlbumColorSchemeGeneration(uriString, targetFlow)
    }

    suspend fun getOrGenerateColorScheme(uriString: String): ColorSchemePair? {
         return colorSchemeProcessor.getOrGenerateColorScheme(
             albumArtUri = uriString,
             paletteStyle = currentPaletteStyle,
             colorAccuracyLevel = currentPaletteAccuracy
         )
    }

    suspend fun forceRegenerateColorScheme(
        uriString: String?,
        regenerateAllStyles: Boolean = false
    ) {
         if (uriString == null) {
             updateAlbumArtThemeState(null, null)
             return
         }

         android.util.Log.d("ThemeStateHolder", "forceRegenerateColorScheme called for: $uriString")
         val current = _albumArtThemeState.value
         android.util.Log.d("ThemeStateHolder", "Current tracked global URI: ${current.albumArtUri}")

         colorSchemeProcessor.invalidateScheme(uriString)

         val newScheme = if (regenerateAllStyles) {
             var selectedStyleScheme: ColorSchemePair? = null
             AlbumArtPaletteStyle.entries.forEach { style ->
                 val generated = colorSchemeProcessor.getOrGenerateColorScheme(
                     albumArtUri = uriString,
                     paletteStyle = style,
                     colorAccuracyLevel = currentPaletteAccuracy,
                     forceRefresh = true
                 )
                 if (style == currentPaletteStyle) {
                     selectedStyleScheme = generated
                 }
             }
             selectedStyleScheme
         } else {
             colorSchemeProcessor.getOrGenerateColorScheme(
                 albumArtUri = uriString,
                 paletteStyle = currentPaletteStyle,
                 colorAccuracyLevel = currentPaletteAccuracy,
                 forceRefresh = true
             )
         }

         val activeFlow = individualAlbumColorSchemes[uriString]
         if (activeFlow != null) {
             activeFlow.value = newScheme
         }

         if (current.albumArtUri == uriString) {
             android.util.Log.d("ThemeStateHolder", "Updating global color scheme flow directly.")
             updateAlbumArtThemeState(newScheme, uriString)
         } else {
             android.util.Log.d("ThemeStateHolder", "Global URI did not match. Skipping global update.")
         }
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        colorSchemeProcessor.clearMemoryCache()
        clearExtractedColorCache()

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            individualAlbumColorSchemes.clear()
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            synchronized(pendingAlbumColorSchemeLock) {
                pendingAlbumColorSchemeTargets.clear()
            }
        }
    }

    fun onCleared() {
        scope = null
    }

}
