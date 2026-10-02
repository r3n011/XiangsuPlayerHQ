package com.theveloper.pixelplay.presentation.floating

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.viewmodel.LyricsStateHolder
import com.theveloper.pixelplay.presentation.viewmodel.PlaybackStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** 悬浮歌词的 UI 状态（顶层类：[FloatingLyricsView] 也要用）。 */
data class FloatingLyricsUiState(
    val lineMode: String = UserPreferencesRepository.FLOATING_LYRICS_LINE_MODE_DOUBLE,
    val isPlaying: Boolean = false,
    val hasLyrics: Boolean = false,
    val expanded: Boolean = false,
    val locked: Boolean = false,
    val prevLine: String = "",
    val currentLine: String = "",
    val nextLine: String = "",
    /** 展开控制条时展示的歌曲信息（收起态不占位）。 */
    val songTitle: String = "",
    val songArtist: String = "",
)

/**
 * 悬浮歌词（桌面歌词）对外唯一入口。
 *
 * 数据全部来自现成状态源，不新建任何管道：
 * - [PlaybackStateHolder.stablePlayerState] → 当前歌曲 / 播放态 / 歌词
 * - [PlaybackStateHolder.currentPosition] → 播放进度（二分定位当前行）
 * - [LyricsStateHolder.currentSongSyncOffset] → 用户手动调的歌词偏移
 *
 * 可见性规则：**开关打开 && 系统允许悬浮窗 && 有正在播放的歌曲**。
 * 不新建前台服务 —— 播放时 MusicService 本身就是前台服务，进程活着窗口就活着。
 */
@Singleton
class FloatingLyricsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStateHolder: PlaybackStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(FloatingLyricsUiState())
    val uiState: StateFlow<FloatingLyricsUiState> = _uiState.asStateFlow()

    private var window: FloatingLyricsWindow? = null
    private var contentView: FloatingLyricsView? = null

    private var enabled = false
    private var hasSong = false
    private var locked = false

    /** 「拖到边缘自动收起」开关（偏好 key 沿用 edge_snap，语义已改为贴边收起）。 */
    private var edgeHideEnabled = true

    /** 展开/收起、整体大小变化是否走过渡动画（设置里可关）。 */
    private var animationsEnabled = true

    /** App 主题模式（跟随系统/浅色/深色），与 MainActivity 的判定保持一致。 */
    private var appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM

    /** 整体大小 / 背景不透明度（百分比）。 */
    private var scalePercent = UserPreferencesRepository.FLOATING_LYRICS_SCALE_DEFAULT
    private var backgroundAlphaPercent = UserPreferencesRepository.FLOATING_LYRICS_BG_ALPHA_DEFAULT
    private var savedX = UserPreferencesRepository.FLOATING_LYRICS_POS_UNSET
    private var savedY = UserPreferencesRepository.FLOATING_LYRICS_POS_UNSET

    private var currentLyrics: Lyrics? = null
    private var currentLineIndex = -1
    private var collapseJob: Job? = null

    init {
        observePreferences()
        observePlayback()
        // 状态 → 视图：集中在这一处刷新，避免散落在各分支里漏更新
        scope.launch {
            uiState.collect { state -> contentView?.applyState(state) }
        }
    }

    /** 播放服务销毁时收起窗口（进程即将结束，窗口本身也会随进程消失）。 */
    fun release() {
        hideWindow()
    }

    /* ---------------------------------------------------------------------- */
    /*                                  状态订阅                                */
    /* ---------------------------------------------------------------------- */

    private fun observePreferences() {
        scope.launch {
            userPreferencesRepository.floatingLyricsEnabledFlow.collect {
                enabled = it
                updateVisibility()
            }
        }
        scope.launch {
            userPreferencesRepository.floatingLyricsLineModeFlow.collect { mode ->
                _uiState.update { state -> state.copy(lineMode = mode) }
            }
        }
        scope.launch {
            userPreferencesRepository.floatingLyricsLockedFlow.collect {
                locked = it
                _uiState.update { state -> state.copy(locked = it) }
            }
        }
        scope.launch {
            userPreferencesRepository.floatingLyricsEdgeSnapFlow.collect { enabled ->
                edgeHideEnabled = enabled
                // 关掉这个开关时，如果当前正收在边缘，就把它放出来，避免"永远收着"
                if (!enabled && window?.isEdgeHidden == true) window?.exitEdgeHidden()
            }
        }
        scope.launch {
            userPreferencesRepository.floatingLyricsAnimationsFlow.collect { enabled ->
                animationsEnabled = enabled
                contentView?.setAnimationsEnabled(enabled)
            }
        }
        scope.launch {
            themePreferencesRepository.appThemeModeFlow.collect { mode ->
                appThemeMode = mode
                applyThemeToView()
            }
        }
        scope.launch {
            userPreferencesRepository.floatingLyricsScaleFlow.collect { percent ->
                scalePercent = percent
                applyAppearanceToView()
            }
        }
        scope.launch {
            userPreferencesRepository.floatingLyricsBackgroundAlphaFlow.collect { percent ->
                backgroundAlphaPercent = percent
                applyAppearanceToView()
            }
        }
        scope.launch {
            userPreferencesRepository.floatingLyricsPositionFlow.collect { (x, y) ->
                savedX = x
                savedY = y
            }
        }
    }

    private fun observePlayback() {
        scope.launch {
            playbackStateHolder.stablePlayerState.collect { state ->
                currentLyrics = state.lyrics
                hasSong = state.currentSong != null
                val synced = state.lyrics?.synced
                _uiState.update {
                    it.copy(
                        isPlaying = state.isPlaying,
                        hasLyrics = !synced.isNullOrEmpty(),
                        songTitle = state.currentSong?.title.orEmpty(),
                        songArtist = state.currentSong?.artist.orEmpty()
                    )
                }
                refreshLines(playbackStateHolder.currentPosition.value)
                updateVisibility()
            }
        }
        scope.launch {
            playbackStateHolder.currentPosition.collect { refreshLines(it) }
        }
    }

    /**
     * 按播放进度定位当前行，只在**行发生变化**时更新状态——
     * 悬浮窗更新走的是 WindowManager，绝不能跟着进度每帧刷新。
     */
    private fun refreshLines(positionMs: Long) {
        val lines = currentLyrics?.synced.orEmpty()
        if (lines.isEmpty()) {
            if (currentLineIndex != -1) {
                currentLineIndex = -1
                _uiState.update { it.copy(prevLine = "", currentLine = "", nextLine = "") }
            }
            return
        }
        val adjusted = positionMs + lyricsStateHolder.currentSongSyncOffset.value
        val index = lineIndexAt(lines, adjusted)
        if (index == currentLineIndex) return
        currentLineIndex = index
        _uiState.update {
            it.copy(
                prevLine = lines.getOrNull(index - 1)?.line.orEmpty(),
                currentLine = lines.getOrNull(index)?.line.orEmpty(),
                nextLine = lines.getOrNull(index + 1)?.line.orEmpty()
            )
        }
    }

    /** lines 按时间有序：二分找最后一个 time <= positionMs 的行（早于首句时取首句）。 */
    private fun lineIndexAt(lines: List<SyncedLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].time.toLong() <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result.coerceAtLeast(0)
    }

    /* ---------------------------------------------------------------------- */
    /*                                  窗口控制                                */
    /* ---------------------------------------------------------------------- */

    private fun updateVisibility() {
        if (enabled && hasSong && canDrawOverlays()) showWindow() else hideWindow()
    }

    private fun showWindow() {
        if (window?.isShowing == true) return
        val floatingWindow = window ?: FloatingLyricsWindow(context) { x, y ->
            scope.launch { userPreferencesRepository.setFloatingLyricsPosition(x, y) }
        }.also { window = it }

        val view = contentView ?: createContentView().also {
            contentView = it
            // 动画开关必须在 applyState 之前落地：applyState 可能触发展开/收起动画
            it.setAnimationsEnabled(animationsEnabled)
            it.applyState(_uiState.value)
            applyAppearanceToView()
            applyThemeToView()
        }
        val (x, y) = resolveInitialPosition()
        floatingWindow.show(view, x, y)
    }

    private fun createContentView(): FloatingLyricsView =
        FloatingLyricsView(
            context = context,
            onDrag = { dx, dy -> window?.moveBy(dx, dy) },
            onDragEnd = { window?.settle(edgeHideEnabled) },
            onTap = { toggleExpanded() },
            onLongPress = { toggleLock() },
            onPrevious = { playbackStateHolder.previousSong() },
            onPlayPause = { playbackStateHolder.playPause() },
            onNext = { playbackStateHolder.nextSong() },
            // 控制条上的关闭按钮：关掉「悬浮歌词」开关（偏好驱动 updateVisibility，
            // 窗口随即隐藏，设置页的开关状态也保持一致）
            onClose = {
                scope.launch { userPreferencesRepository.setFloatingLyricsEnabled(false) }
            },
            onRestoreFromEdge = { window?.exitEdgeHidden() }
        )

    /**
     * 整体大小与背景不透明度。
     *
     * 尺寸一变窗口的宽高就跟着变：放大后可能顶出屏幕，贴边收起时箭头宽度也变了、
     * 需要按新宽度重新缩进。这里等一帧布局完成（`post`）再夹回屏幕内，
     * 否则 `view.width` 还是旧值，算出来的位置会偏。
     */
    private fun applyAppearanceToView() {
        val view = contentView ?: return
        view.applyAppearance(
            scale = scalePercent / 100f,
            backgroundAlpha = backgroundAlphaPercent / 100f
        )
        view.post { window?.clampToScreen() }
    }

    /** 跟随 App 的深浅色（AppThemeMode：跟随系统/浅色/深色），判定与 MainActivity 一致。 */
    private fun applyThemeToView() {
        val systemDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (appThemeMode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            else -> systemDark
        }
        contentView?.applyTheme(isDark)
    }

    private fun hideWindow() {
        collapseJob?.cancel()
        _uiState.update { it.copy(expanded = false) }
        window?.remove()
    }

    /** 旋转/分屏导致屏幕尺寸变化后：刷新长句约束、尺寸与深浅色，再把窗口重新落位。 */
    fun onScreenSizeChanged() {
        contentView?.applyState(_uiState.value)
        applyThemeToView()
        // 内部已含「布局完成后夹回屏幕内」，这里不必再单独 clamp
        applyAppearanceToView()
    }

    private fun resolveInitialPosition(): Pair<Int, Int> {
        if (savedX != UserPreferencesRepository.FLOATING_LYRICS_POS_UNSET &&
            savedY != UserPreferencesRepository.FLOATING_LYRICS_POS_UNSET
        ) {
            return savedX to savedY
        }
        // 默认落在偏下位置：不挡视线，又不至于贴到系统导航区
        val metrics = context.resources.displayMetrics
        return (metrics.widthPixels * 0.09f).roundToInt() to
            (metrics.heightPixels * 0.72f).roundToInt()
    }

    /* ---------------------------------------------------------------------- */
    /*                                  交互动作                                */
    /* ---------------------------------------------------------------------- */

    /** 点击：展开/收起迷你控制条（展开后自动收起，避免长期占位）。 */
    private fun toggleExpanded() {
        val expanded = !_uiState.value.expanded
        _uiState.update { it.copy(expanded = expanded) }
        // 展开后胶囊变高，靠屏幕底部的窗口会被挤出可视区（控制条就"看不见"了），
        // 等布局完成把窗口夹回屏幕内
        contentView?.post { window?.ensureVisible() }
        collapseJob?.cancel()
        if (expanded) {
            collapseJob = scope.launch {
                delay(EXPANDED_AUTO_COLLAPSE_MS)
                _uiState.update { it.copy(expanded = false) }
            }
        }
    }

    /** 长按：锁定/解锁位置（锁定时忽略拖动，但点击展开仍可用）。 */
    private fun toggleLock() {
        val next = !locked
        Toast.makeText(
            context,
            if (next) "已锁定位置" else "已解锁位置",
            Toast.LENGTH_SHORT
        ).show()
        scope.launch { userPreferencesRepository.setFloatingLyricsLocked(next) }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    private companion object {
        const val EXPANDED_AUTO_COLLAPSE_MS = 4_000L
    }
}
