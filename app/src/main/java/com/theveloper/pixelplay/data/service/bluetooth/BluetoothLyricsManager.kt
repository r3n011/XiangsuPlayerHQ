package com.theveloper.pixelplay.data.service.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.theveloper.pixelplay.data.model.Lyrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 蓝牙歌词管理器（Bluetooth Lyrics Manager）。
 *
 * 设计原则（非常重要）：
 * 1. 所有操作 Player 的方法（`currentMediaItemIndex` / `replaceMediaItem` /
 *    `currentMediaItem`）必须在主线程调用 —— Media3 会用 `Looper.myLooper()`
 *    做校验，违反就抛 `IllegalStateException("Player is accessed on the wrong thread")`。
 * 2. **不要在 player listener 回调内部调用 `replaceMediaItem`** —— replaceMediaItem
 *    会触发 `onTimelineChanged` / `onMediaItemTransition` / `onMediaMetadataChanged`
 *    等事件，如果这些事件又走回我们的 listener 再 replace，就是正反馈环，
 *    直接表现是播放器 UI（按钮/封面/标题）闪烁甚至崩溃。
 * 3. 推送到蓝牙的策略：仅在"当前歌词行确实变了"时执行 replaceMediaItem，
 *    其余时间只是轻量级地更新内部字段，不碰 player。
 */
@OptIn(UnstableApi::class)
@Singleton
class BluetoothLyricsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _featureEnabled = MutableStateFlow(false)
    val featureEnabled: StateFlow<Boolean> = _featureEnabled.asStateFlow()

    // ⚡ 对外广播歌词：开启后不受蓝牙连接限制，直接把系统媒体元数据歌名
    //    刷新为当前歌词，实现应用外（通知栏/锁屏/蓝牙设备/车载）显示歌词。
    @Volatile
    private var externalBroadcastEnabled: Boolean = false

    private val _hasBluetoothOutput = MutableStateFlow(detectBluetoothOutputInternal())
    val hasBluetoothOutput: StateFlow<Boolean> = _hasBluetoothOutput.asStateFlow()

    private val _currentLine = MutableStateFlow<String?>(null)
    val currentLine: StateFlow<String?> = _currentLine.asStateFlow()

    @Volatile private var lyrics: Lyrics? = null
    @Volatile private var currentMediaItem: MediaItem? = null
    @Volatile private var mediaSession: MediaSession? = null

    // 真实的原始歌名（推歌词前捕获）。replaceMediaItem 会把 title 覆盖为歌词，
    // 空行回退、专辑字段展示、恢复原始元数据时都需要它。
    @Volatile private var originalTitle: String? = null

    @Volatile private var currentPositionMs: Long = 0L
    @Volatile private var isPlaying: Boolean = false

    // "上一次推送到 player 的行内容" —— 用来判断是否需要真正调用 replaceMediaItem。
    @Volatile private var lastPushedKey: String? = null
    // 上一次推送时的播放位置 / 曲目索引：用于拦截"位置小幅回退（缓冲抖动）导致的歌词回弹"。
    @Volatile private var lastPushedPositionMs: Long = -1L
    @Volatile private var lastPushedIndex: Int = -1
    // 防重入：正在 push 的过程中，不要再被其他事件触发 push。
    @Volatile private var isPushingNow: Boolean = false

    private var pushJob: Job? = null

    private var audioManager: AudioManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var receiverRegistered = false

    private val bluetoothProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            refreshBluetoothState()
        }

        override fun onServiceDisconnected(profile: Int) {
            refreshBluetoothState()
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> refreshBluetoothState()
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshBluetoothState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshBluetoothState()
        }
    }

    // ------- 公共 API -------

    /**
     * 启动监听（通常在 [MusicService] 的 onCreate 里调用一次）。
     */
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT"])
    fun start() {
        if (receiverRegistered) return
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            bluetoothAdapter = try {
                BluetoothAdapter.getDefaultAdapter()
            } catch (_: Throwable) {
                null
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            }
            context.registerReceiver(broadcastReceiver, filter)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
            }

            try {
                bluetoothAdapter?.getProfileProxy(context, bluetoothProfileListener, BluetoothProfile.A2DP)
                bluetoothAdapter?.getProfileProxy(context, bluetoothProfileListener, BluetoothProfile.HEADSET)
            } catch (_: SecurityException) {
                // 忽略权限不足异常，后续仍然可以用 AudioManager 检测
            }
            receiverRegistered = true
            refreshBluetoothState()
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Failed to start BluetoothLyricsManager")
        }
    }

    /**
     * 停止监听并释放资源。
     */
    fun stop() {
        pushJob?.cancel()
        pushJob = null
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (_: IllegalArgumentException) { /* 已解绑 */ }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
                } catch (_: Throwable) { /* 忽略 */ }
            }
            try {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, null)
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, null)
            } catch (_: Throwable) { /* 忽略 */ }
            receiverRegistered = false
        }
    }

    /**
     * 打开/关闭本功能。由 UI 中的开关调用。
     */
    fun setFeatureEnabled(enabled: Boolean) {
        _featureEnabled.value = enabled
        ensurePushJobState()
        if (!enabled) {
            // 关闭时立即恢复一次原始元数据，避免蓝牙屏上留下最后一行歌词
            pushNow(forceRestoreOriginal = true)
            _currentLine.value = null
        }
    }

    /**
     * ⚡ 对外广播歌词开关：由设置项调用。
     * 开启后无论是否连接蓝牙，都把系统媒体元数据歌名刷新为当前歌词；
     * 关闭时立即恢复原始歌名。
     */
    fun setExternalBroadcastEnabled(enabled: Boolean) {
        externalBroadcastEnabled = enabled
        ensurePushJobState()
        if (!enabled) {
            pushNow(forceRestoreOriginal = true)
            _currentLine.value = null
        }
    }

    /**
     * 绑定 MediaSession，供后续推送歌词使用。
     */
    fun attachMediaSession(session: MediaSession?) {
        mediaSession = session
        ensurePushJobState()
    }

    /**
     * 绑定当前歌词。歌词变化时应调用（IO 线程安全）。
     */
    fun setLyrics(newLyrics: Lyrics?) {
        lyrics = newLyrics
        lastPushedKey = null
        // 拉到歌词后主动推送一次
        mainHandler.post { pushNowInternal(checkPlayerRequired = true, forceRestoreOriginal = false) }
    }

    /**
     * ⚡ 当前正在播放的 mediaId（供外部做歌词异步竞态校验）。
     * 歌词加载协程完成后，用它对比启动时的 songId：不一致说明期间已切歌，
     * 晚到的旧歌歌词必须丢弃，否则设备端会"弹回之前的歌词"。
     */
    val currentMediaItemId: String? get() = currentMediaItem?.mediaId

    /**
     * 绑定当前播放的 MediaItem。切歌时应调用（主线程 / IO 线程皆可）。
     *
     * ⚡ 只在新歌（mediaId 变化）时才重置推送状态并捕获原始歌名：
     * 我们自己 replaceMediaItem 推歌词会触发 onMediaItemTransition(PLAYLIST_CHANGED)，
     * 传入的仍是同一首歌（mediaId 不变）—— 若此时重置 lastPushedKey，
     * 500ms 轮询里"歌词行没变也会 replace 一次"，设备端歌词每 500ms 刷新一次。
     */
    fun setCurrentMediaItem(item: MediaItem?) {
        val newId = item?.mediaId
        val oldId = currentMediaItem?.mediaId
        currentMediaItem = item
        if (newId != oldId) {
            lastPushedKey = null
            lastPushedPositionMs = -1L
            lastPushedIndex = -1
            // ⚡ 切歌时立即清空旧歌词：旧歌的歌词在 getLyrics 完成前仍挂在 lyrics 上，
            // 若此时用新歌的播放位置去二分旧歌词，500ms 轮询会短暂弹出上一首歌的歌词。
            lyrics = null
            // 捕获真实的原始歌名：replaceMediaItem 后 base.title 会变成歌词，
            // 必须记住真正的歌名用于：空行回退 / 专辑字段展示 / 恢复原始元数据。
            originalTitle = item?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            // ⚡ 新歌自带真实歌名（引擎 setMediaItem 时会携带），
            // 无需手动重置媒体元数据 —— 设备端自然从"真实歌名"开始，
            // 等第一句歌词到达后再 replaceMediaItem 覆盖为歌词。
        }
    }

    /**
     * ⚡ 防回环守卫：判断一次 [Player.Listener.onMediaItemTransition] 是否是由
     * "我们自己 replaceMediaItem 推歌词" 引起的（同一首歌 mediaId 未变）。
     *
     * 正常情况下仅改 metadata 的 replaceMediaItem 会走 ExoPlayer 的就地更新路径
     * （[canUpdateMediaItem] = true，不重建 MediaSource）→ 不触发 transition。
     * 但部分数据源（URI 变化等）无法就地更新时会重建 MediaSource 并触发
     * transition；此守卫让 [MusicService] 跳过"重新加载歌词"，杜绝
     * push → transition → setLyrics → push 的无限循环。
     *
     * ⚠️ 必须在调用 [setCurrentMediaItem] 之前判断（setCurrentMediaItem 会更新
     * currentMediaItem，导致新旧 mediaId 恒相等）。
     */
    fun isSelfLyricsTransition(item: MediaItem?): Boolean {
        val newId = item?.mediaId
        val oldId = currentMediaItem?.mediaId
        return newId != null && newId == oldId
    }

    /**
     * 更新播放位置与播放状态。由 [MusicService] 的播放监听器周期性调用。
     *
     * ⚠️ 这个方法里**不调用 player 任何 API** —— 只更新字段。
     * 真正碰 player 的地方只有下面的 `pushNowInternal`，而且它是在主线程执行的，
     * 并有 `isPushingNow` 防重入。
     */
    fun updatePlaybackState(positionMs: Long, playing: Boolean) {
        currentPositionMs = positionMs
        if (isPlaying != playing) {
            isPlaying = playing
            ensurePushJobState()
        }
    }

    /**
     * 立刻推送一次。用于：切歌/暂停恢复/用户开关切换。
     *
     * 任何线程都可调用：内部会自动 post 到主线程。
     */
    fun pushNow(forceRestoreOriginal: Boolean = false) {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            pushNowInternal(checkPlayerRequired = true, forceRestoreOriginal = forceRestoreOriginal)
        } else {
            mainHandler.post {
                pushNowInternal(checkPlayerRequired = true, forceRestoreOriginal = forceRestoreOriginal)
            }
        }
    }

    // ------- 内部实现 -------

    /**
     * 保证 pushJob 生命周期。
     * - 当 feature 开启、有蓝牙输出、正在播放、有歌词时：启动一个 500ms 轮询 job，
     *   在里面周期性调用 pushNowInternal。
     * - 否则停止 job。
     *
     * 这里启动的 job 运行在 Main dispatcher 上，所以循环里的 pushNowInternal
     * 天然就在主线程上，不需要再 post。
     */
    private fun ensurePushJobState() {
        // 对外广播（externalBroadcastEnabled）开启时无需蓝牙连接也推送；
        // 否则仅在蓝牙歌词开关开启且连接了蓝牙输出时推送。
        val btActive = featureEnabled.value && hasBluetoothOutput.value
        val shouldRun = (btActive || externalBroadcastEnabled) && isPlaying &&
                !lyrics?.synced.isNullOrEmpty() && mediaSession != null

        if (shouldRun && (pushJob == null || pushJob?.isActive != true)) {
            pushJob?.cancel()
            pushJob = serviceScope.launch {
                while ((btActive || externalBroadcastEnabled) && isPlaying) {
                    pushNowInternal(checkPlayerRequired = true, forceRestoreOriginal = false)
                    delay(POLL_INTERVAL_MS)
                }
            }
        } else if (!shouldRun) {
            pushJob?.cancel()
            pushJob = null
        }
    }

    /**
     * 真正执行一次推送。
     *
     * **必须在主线程调用**。
     *
     * 策略：
     *   - 先判断"当前歌词行 + 当前歌曲"是否与上一次推送一致；若一致，跳过，
     *     完全不碰 player —— 这样能避免不必要的 player 状态变化，
     *     也消除了因为 player listener 被重新触发而产生的循环。
     *   - 若确实有变化，则调用 `player.replaceMediaItem` 推送新 metadata；
     *     push 期间设 `isPushingNow = true`，防重入。
     */
    private fun pushNowInternal(checkPlayerRequired: Boolean, forceRestoreOriginal: Boolean) {
        if (isPushingNow) return
        val session = mediaSession
        val player = session?.player
        if (checkPlayerRequired && player == null) return
        if (player == null) return

        // ⚡ 播放中用实时位置：MusicService 只在状态变化时调用 updatePlaybackState，
        // currentPositionMs 在播放过程中是陈旧的 → 歌词永远停在第一句。
        // 这里直接读 player.currentPosition（主线程），每次推送都是最新进度。
        currentPositionMs = player.currentPosition

        // --- 先算出想推送的内容（纯计算，不碰 player） ---
        val base = currentMediaItem?.mediaMetadata ?: player.currentMediaItem?.mediaMetadata
        // 艺术家/专辑从不被歌词覆盖，直接取当前值即可；歌名会被歌词覆盖，
        // 因此优先用切歌时捕获的原始歌名（首次推送时懒加载兜底一次）。
        val originalArtist = base?.artist?.toString().orEmpty()
        val originalAlbum = base?.albumTitle?.toString().orEmpty()
        var realTitle = originalTitle
        if (realTitle.isNullOrBlank()) {
            realTitle = base?.title?.toString().orEmpty()
            if (realTitle.isNotBlank()) originalTitle = realTitle
        }

        val shouldShowLyrics = !forceRestoreOriginal &&
                (featureEnabled.value && hasBluetoothOutput.value || externalBroadcastEnabled) &&
                !lyrics?.synced.isNullOrEmpty()

        val pushKey: String
        val newTitle: String
        val newArtist: String
        val newAlbum: String
        val lineNow: String?

        if (shouldShowLyrics) {
            val line = resolveLine(currentPositionMs)
            lineNow = line

            // ⚡ 空行（尚未到第一句歌词 / 两句歌词之间的空白段）时不 replace：
            // 保持设备端当前显示内容不变，避免 title 在"真实歌名 ↔ 歌词"之间
            // 来回 replace 造成闪烁。
            if (line.isBlank()) {
                return
            }

            // ⚡ 歌词只刷新到"歌名"位置；艺术家保持原样（不显示"→ 下一句"）。
            // 专辑字段放真实歌名，方便设备端确认当前播放的歌曲。
            newTitle = line
            newArtist = originalArtist
            newAlbum = if (realTitle.isNotBlank()) realTitle else originalAlbum
            // key = 歌曲 index + 真实歌名 + 当前行：只有行真正变化时才 replace
            pushKey = "${player.currentMediaItemIndex}_${realTitle}_$line"
        } else {
            lineNow = null
            newTitle = realTitle
            newArtist = originalArtist
            newAlbum = originalAlbum
            pushKey = "${player.currentMediaItemIndex}_RESTORE_$realTitle"
        }

        // --- 若与上次推送的内容相同，直接跳过，完全不改 player ---
        if (pushKey == lastPushedKey) {
            return
        }

        // ⚡ 防闪烁：同一首歌内位置"小幅回退"（缓冲/解码抖动，<3 秒）时忽略本次
        // 更新 —— 否则设备端歌词会"回弹"闪一下。超过阈值视为用户手动 seek，
        // 正常更新歌词。
        if (lastPushedPositionMs >= 0 &&
            player.currentMediaItemIndex == lastPushedIndex &&
            currentPositionMs < lastPushedPositionMs &&
            currentPositionMs >= lastPushedPositionMs - 3000L
        ) {
            return
        }
        lastPushedPositionMs = currentPositionMs
        lastPushedIndex = player.currentMediaItemIndex

        // --- 行确实变化了：覆盖 MediaSession 会话元数据（通知栏/锁屏/蓝牙设备显示歌词） ---
        isPushingNow = true
        try {
            lastPushedKey = pushKey
            _currentLine.value = lineNow

            val currentItem = player.currentMediaItem ?: return
            val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                .setTitle(newTitle)
                .setArtist(newArtist)
                .setAlbumTitle(newAlbum)
                .setDescription(newTitle)
                .build()

            // ⚡ 用 player.replaceMediaItem 更新当前条目的元数据（通知栏/锁屏/
            // 蓝牙设备显示歌词）。Media3 1.10.1 没有 setMediaMetadata API，
            // 只能替换当前 MediaItem：
            //   - 仅改 metadata、URI 不变时，ExoPlayer 走 canUpdateMediaItem
            //     就地更新（TimelineWithUpdatedMediaItem），不重建 MediaSource、
            //     不触发 onMediaItemTransition（无循环）、不打断/重缓冲音频。
            //   - 就地更新会触发 onTimelineChanged / onMediaMetadataChanged，
            //     MusicService 不会因它们重载歌词（重载入口只有 onMediaItemTransition），
            //     配合 isSelfLyricsTransition 守卫彻底杜绝回环与闪烁。
            player.replaceMediaItem(player.currentMediaItemIndex, currentItem.buildUpon().setMediaMetadata(updatedMetadata).build())
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Failed to push Bluetooth lyrics")
        } finally {
            // 在 finally 里清掉 flag，保证异常情况下也能恢复
            isPushingNow = false
        }
    }

    private fun refreshBluetoothState() {
        val hasBt = detectBluetoothOutputInternal()
        val changed = _hasBluetoothOutput.value != hasBt
        _hasBluetoothOutput.value = hasBt
        ensurePushJobState()
        // 蓝牙连接/断开时也尝试推送一次；但避免触发循环：仅在"有歌词"
        // 或"刚关闭需要恢复"时才执行。
        if (changed) {
            mainHandler.post {
                pushNowInternal(checkPlayerRequired = true, forceRestoreOriginal = !hasBt)
            }
        }
    }

    private fun detectBluetoothOutputInternal(): Boolean {
        val am = audioManager ?: try {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        } catch (_: Throwable) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (device in devices) {
                    when (device.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLE_SPEAKER,
                        AudioDeviceInfo.TYPE_BLE_BROADCAST -> return true
                    }
                }
            } catch (_: Throwable) { /* fallback */ }
        }

        return try {
            @Suppress("DEPRECATION")
            am.isBluetoothA2dpOn || am.isBluetoothScoOn
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveLine(positionMs: Long): String {
        val lines = lyrics?.synced ?: return ""
        if (lines.isEmpty()) return ""

        val pos = positionMs.toInt()
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val line = lines[mid]
            when {
                line.time <= pos -> {
                    result = mid
                    lo = mid + 1
                }
                else -> hi = mid - 1
            }
        }
        return if (result >= 0) sanitize(lines[result].line) else ""
    }

    private fun sanitize(raw: String?): String {
        if (raw == null) return ""
        return raw.trim()
            .replace("\r", "")
            .replace("\n", "  ")
            .replace("\t", " ")
    }

    companion object {
        private const val TAG = "BluetoothLyricsManager"
        private const val POLL_INTERVAL_MS = 500L
    }
}
