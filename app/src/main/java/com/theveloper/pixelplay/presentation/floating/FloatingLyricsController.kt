package com.theveloper.pixelplay.presentation.floating

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
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
    private var lastOverlayHintAtMs = 0L

    /**
     * 后台播放兜底：UI 的 PlayerViewModel 被销毁后，PlaybackStateHolder 不再更新，
     * 悬浮歌词会拿到 hasSong=false。直接监听 MediaSession 的 Player 可以继续拿到
     * 当前媒体项、播放态和进度，保证悬浮窗在后台仍然可见。
     */
    private var fallbackPlayer: Player? = null
    private val fallbackPlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            syncFromFallbackPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromFallbackPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromFallbackPlayer()
        }
    }

    init {
        observePreferences()
        observePlayback()
        observeLyrics()
        // 状态 → 视图：集中在这一处刷新，避免散落在各分支里漏更新
        scope.launch {
            uiState.collect { state -> contentView?.applyState(state) }
        }
        // 播放服务仍在跑、UI 状态却停止更新时，用 Player 自身的位置继续驱动歌词。
        scope.launch {
            while (isActive) {
                delay(500L)
                fallbackPlayer?.let { player ->
                    refreshLines(player.currentPosition)
                    if (player.currentMediaItem != null && !hasSong) syncFromFallbackPlayer()
                }
            }
        }
    }

    /**
     * MusicService 加载完当前歌曲歌词后直接喂给悬浮歌词。
     * 这条链路不依赖 PlayerViewModel / PlaybackStateHolder，后台播放也可靠。
     */
    fun setLyrics(lyrics: Lyrics?) {
        // MusicService 的歌词加载在 Dispatchers.IO 上完成；Media3 的 Player
        // 只允许在创建它的主线程访问，WindowManager 操作也必须在主线程。
        // 这里统一切回 scope（Main.immediate），避免跨线程读取 currentPosition 崩溃。
        scope.launch {
            Timber.tag("FloatingLyrics").d(
                "setLyrics synced=%d plain=%d",
                lyrics?.synced?.size ?: 0,
                lyrics?.plain?.size ?: 0,
            )
            currentLyrics = lyrics
            currentLineIndex = -1
            _uiState.update { it.copy(hasLyrics = hasAnyLyrics(lyrics)) }
            refreshLines(fallbackPlayer?.currentPosition ?: playbackStateHolder.currentPosition.value)
            updateVisibility()
        }
    }

    /** MusicService 创建/切换 MediaSession player 时调用。 */
    fun attachPlayer(player: Player?) {
        scope.launch {
            if (fallbackPlayer === player) return@launch
            fallbackPlayer?.removeListener(fallbackPlayerListener)
            fallbackPlayer = player
            player?.addListener(fallbackPlayerListener)
            syncFromFallbackPlayer()
        }
    }

    private fun syncFromFallbackPlayer() {
        scope.launch {
            val player = fallbackPlayer ?: return@launch
            val item = player.currentMediaItem
            if (item != null) {
                hasSong = true
                val metadata = item.mediaMetadata
                _uiState.update {
                    it.copy(
                        isPlaying = player.isPlaying,
                        hasLyrics = hasAnyLyrics(currentLyrics),
                        songTitle = metadata.title?.toString().orEmpty(),
                        songArtist = metadata.artist?.toString().orEmpty(),
                    )
                }
                refreshLines(player.currentPosition)
                updateVisibility()
            }
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
                // UI 侧歌词/歌曲状态优先；UI 停止更新时用 MediaSession player 兜底。
                state.lyrics?.let { currentLyrics = it }
                hasSong = state.currentSong != null || fallbackPlayer?.currentMediaItem != null
                _uiState.update {
                    it.copy(
                        isPlaying = state.isPlaying,
                        hasLyrics = hasAnyLyrics(currentLyrics),
                        songTitle = state.currentSong?.title
                            ?: fallbackPlayer?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                        songArtist = state.currentSong?.artist
                            ?: fallbackPlayer?.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
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
     * 歌词不一定写回 PlaybackStateHolder（后台/ViewModel 销毁时尤其如此），
     * 这里直接监听 LyricsStateHolder 的加载结果，作为悬浮歌词的独立歌词源。
     */
    private fun observeLyrics() {
        scope.launch {
            lyricsStateHolder.searchUiState.collect { state ->
                if (state is LyricsSearchUiState.Success) {
                    currentLyrics = state.lyrics
                    currentLineIndex = -1
                    _uiState.update {
                        it.copy(hasLyrics = hasAnyLyrics(state.lyrics))
                    }
                    refreshLines(fallbackPlayer?.currentPosition ?: playbackStateHolder.currentPosition.value)
                    updateVisibility()
                }
            }
        }
        scope.launch {
            lyricsStateHolder.songUpdates.collect { (_, lyrics) ->
                if (lyrics != null) {
                    currentLyrics = lyrics
                    currentLineIndex = -1
                    _uiState.update { it.copy(hasLyrics = hasAnyLyrics(lyrics)) }
                    refreshLines(fallbackPlayer?.currentPosition ?: playbackStateHolder.currentPosition.value)
                    updateVisibility()
                }
            }
        }
    }

    /**
     * 按播放进度定位当前行，只在**行发生变化**时更新状态——
     * 悬浮窗更新走的是 WindowManager，绝不能跟着进度每帧刷新。
     */
    private fun refreshLines(positionMs: Long) {
        val lines = currentLyrics?.synced.orEmpty()
        if (lines.isEmpty()) {
            // 没有逐行时间轴时，至少把纯文本歌词的前几行显示出来，而不是直接“暂无歌词”。
            val plain = currentLyrics?.plain.orEmpty()
            if (plain.isNotEmpty()) {
                if (currentLineIndex != -2) {
                    currentLineIndex = -2
                    _uiState.update {
                        it.copy(
                            prevLine = "",
                            currentLine = plain.getOrNull(0).orEmpty(),
                            nextLine = plain.getOrNull(1).orEmpty(),
                        )
                    }
                }
            } else if (currentLineIndex != -1) {
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

    private fun hasAnyLyrics(lyrics: Lyrics?): Boolean =
        !lyrics?.synced.isNullOrEmpty() || !lyrics?.plain.isNullOrEmpty()

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
        val overlays = canDrawOverlays()
        Timber.tag("FloatingLyrics").d(
            "updateVisibility enabled=%s hasSong=%s canDrawOverlays=%s showing=%s",
            enabled, hasSong, overlays, window?.isShowing == true
        )
        if (enabled && hasSong && overlays) {
            showWindow()
        } else {
            if (enabled && hasSong && !overlays) {
                // 开关开着、有歌，但没有悬浮窗权限：给一次明确提示，避免“点了没反应”。
                val now = System.currentTimeMillis()
                if (now - lastOverlayHintAtMs > 15_000L) {
                    lastOverlayHintAtMs = now
                    Toast.makeText(
                        context,
                        "悬浮歌词需要「显示在其他应用上层」权限，请到设置中授予",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            hideWindow()
        }
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
        val shown = floatingWindow.show(view, x, y)
        if (shown) {
            // 首次布局完成后夹回屏幕内：旧版本保存的坐标可能来自其它分辨率/方向，
            // 直接使用会让窗口落在屏幕外，看起来就是“打开了但没显示”。
            view.post { floatingWindow.clampToScreen() }
        }
        if (!shown) {
            Timber.tag("FloatingLyrics").e("showWindow failed: overlay addView returned false")
            if (canDrawOverlays()) {
                Toast.makeText(context, "悬浮歌词窗口创建失败，请检查系统悬浮窗限制", Toast.LENGTH_LONG).show()
            }
        }
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
        val metrics = context.resources.displayMetrics
        // 旧坐标可能来自横竖屏切换/其它分辨率，先夹到当前屏幕内，
        // 避免窗口被放到屏幕外导致“开关开了但看不见”。
        val maxX = (metrics.widthPixels - 80).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - 80).coerceAtLeast(0)
        if (savedX != UserPreferencesRepository.FLOATING_LYRICS_POS_UNSET &&
            savedY != UserPreferencesRepository.FLOATING_LYRICS_POS_UNSET
        ) {
            return savedX.coerceIn(0, maxX) to savedY.coerceIn(0, maxY)
        }
        // 默认落在偏下位置：不挡视线，又不至于贴到系统导航区
        return (metrics.widthPixels * 0.09f).roundToInt().coerceIn(0, maxX) to
            (metrics.heightPixels * 0.72f).roundToInt().coerceIn(0, maxY)
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
