package com.theveloper.pixelplay.data.listentogether

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.MediaItemBuilder
import com.theveloper.pixelplay.utils.NeteaseMediaIds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 网易云「一起听」协调器（进程级单例）。
 *
 * 移植自 MeloX 的 Listen Together 状态机，协议走内置 net.moriafly.ncm SDK：
 * - 拥有自己的 [MediaController]（连接 [com.theveloper.pixelplay.data.service.MusicService]），
 *   房间在面板关闭后仍然持续同步；
 * - 1s 轮询房间快照（队列 + 播放命令），5s 心跳，5s 房间状态刷新，空闲时 60s 轮询一次状态
 *   （App 重启后可自动恢复已在的房间）；
 * - 队列/命令变化通过签名去重；忽略自己发出的命令回声；应用远端命令后抑制本地事件上报；
 * - ⚠️ 一起听激活期间本地播放被限制为仅网易云歌曲（门禁另见 PlayerViewModel 的播放入口，
 *   本类构造/应用的队列也只包含网易云歌曲）。
 */
@Singleton
class ListenTogetherCoordinator @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionToken: SessionToken,
    private val neteaseRepository: com.theveloper.pixelplay.data.netease.NeteaseRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainExecutor = Handler(Looper.getMainLooper()).let { handler ->
        java.util.concurrent.Executor { command -> handler.post(command) }
    }
    private val api = ListenTogetherApi()

    private val _state = MutableStateFlow(ListenTogetherState())
    val state: StateFlow<ListenTogetherState> = _state.asStateFlow()

    /** 一起听事件提示（激活/恢复房间等），由 UI 层转发为 Toast */
    private val _messageEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messageEvents = _messageEvents.asSharedFlow()

    private fun notify(message: String) {
        _messageEvents.tryEmit(message)
    }

    private val commandMutex = Mutex()
    private val playlistMutex = Mutex()
    private val commandSequence = java.util.concurrent.atomic.AtomicInteger(1)

    private var monitorJob: Job? = null
    private var queueReportJob: Job? = null
    private var controller: MediaController? = null
    private var controllerConnectionPending = false

    private var room: ListenTogetherRoom? = null
    private var isHost = false
    private var cachedUserId: Long? = null
    private var lastCookieFingerprint: Int? = null
    private var lastKnownSongId: Long? = null
    private var lastRemoteQueueSignature: String? = null
    private var lastRemoteCommandSignature: String? = null
    private var playlistVersion = 1
    private var failures = 0
    private var heartbeatTick = 0
    private var statusTick = 0
    /** 空闲探测（不在房间时检查是否被拉进房间）的连续失败次数，用于退避，避免离线时每分钟锤一次。 */
    private var idleFailures = 0
    private var firstSyncForRoom = true
    @Volatile
    private var suppressLocalUntilRealtime = 0L

    /** 抑制硬上限：超过它就恢复上报，避免长期屏蔽用户自己的操作 */
    @Volatile
    private var suppressHardUntilRealtime = 0L

    /** 应用远端状态后"本地应该处于的歌曲"，用于判断是否已收敛（见 localEventsSuppressed） */
    @Volatile
    private var expectedRemoteSongId: Long? = null

    fun ensureStarted() {
        if (monitorJob?.isActive == true) return
        synchronized(this) {
            if (monitorJob?.isActive == true) return
            monitorJob = scope.launch { monitorLoop() }
        }
    }

    // ─── 对外动作 ─────────────────────────────────────────────────────

    /** 房主开房：以指定网易云歌单作为一起听队列 */
    fun startHostRoom(playlistId: Long) {
        ensureStarted()
        if (_state.value.active) {
            setError("已在一间一起听房间中")
            return
        }
        scope.launch {
            _state.update {
                it.copy(phase = ListenTogetherPhase.Connecting, lastError = null)
            }
            runCatching {
                val songs = withContext(Dispatchers.IO) {
                    neteaseRepository.getPlaylistSongsOnce(playlistId)
                }.filter { it.isNeteaseTogetherSong() }
                if (songs.isEmpty()) throw IllegalStateException("该歌单没有可一起听的网易云歌曲，请先在网易云面板同步歌单")

                startHostRoomInternal(songs)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.w(error, "ListenTogether: startHostRoom failed")
                resetRoom()
                // ⚡ 拉歌单等非 API 链路的异常文案可能是服务端原文（如「系统错误」），
                //    统一过一遍友好映射，不让原文直出面板
                setError(error.userFacingMessage() ?: "开始一起听失败")
            }
        }
    }

    /**
     * 房主开房：直接以一组网易云歌曲作为队列（一起听漫游：私人 FM/漫游推荐）。
     * 列表中的非网易云歌曲会被过滤；过滤后为空则失败。
     */
    fun startHostRoomWithSongs(
        songs: List<Song>,
        failureMessage: String = "开始一起听失败",
        /** ⚡ 从这首歌开始播（当前播放队列开房时传入正在播放的那首，避免从头换歌） */
        startSongId: String? = null
    ) {
        ensureStarted()
        if (_state.value.active) {
            setError("已在一间一起听房间中")
            return
        }
        scope.launch {
            _state.update {
                it.copy(phase = ListenTogetherPhase.Connecting, lastError = null)
            }
            runCatching {
                val neteaseSongs = songs.filter { it.isNeteaseTogetherSong() }
                if (neteaseSongs.isEmpty()) throw IllegalStateException("没有可一起听的网易云歌曲")
                startHostRoomInternal(neteaseSongs, startSongId)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.w(error, "ListenTogether: startHostRoomWithSongs failed")
                resetRoom()
                setError(error.userFacingMessage() ?: failureMessage)
            }
        }
    }

    private suspend fun startHostRoomInternal(songs: List<Song>, startSongId: String? = null) {
        val created = api.createRoom()
        adoptRoom(created, isHost = true)
        connectControllerIfNeeded()
        val active = awaitController(timeoutMs = 10_000L)
            ?: throw IllegalStateException("播放器未就绪，无法开始一起听")

        // 以传入顺序作为初始队列（全网易云歌曲）；指定了 startSongId 时从那一首开始播
        val items = songs.map { MediaItemBuilder.build(it) }
        val startIndex = startSongId
            ?.let { id -> songs.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 } ?: 0
        suppressLocalEvents()
        active.shuffleModeEnabled = false
        active.setMediaItems(items, startIndex, 0L)
        active.prepare()
        active.play()
        lastKnownSongId = neteaseIdOf(active.currentMediaItem)

        reportQueueNow(created)
    }

    /** 加入别人的房间（邀请文本里的 roomId + inviterId） */
    fun joinRoom(roomId: String, inviterId: String) {
        ensureStarted()
        if (_state.value.active) {
            setError("已在一间一起听房间中")
            return
        }
        scope.launch {
            _state.update {
                it.copy(phase = ListenTogetherPhase.Connecting, lastError = null)
            }
            runCatching {
                // 本账号可能已在目标房间（服务端为准），直接恢复会话而不是报错
                api.roomStatus()?.takeIf { it.id == roomId }?.let { existing ->
                    adoptRoom(existing, isHost = existing.creatorId == currentUserId()?.toString())
                    return@runCatching
                }

                api.checkRoomUnjoinableReason(roomId)?.let { reason ->
                    throw IllegalStateException(reason)
                }
                val joined = api.acceptInvite(roomId, inviterId)
                adoptRoom(joined, isHost = false)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.w(error, "ListenTogether: joinRoom failed")
                resetRoom()
                setError(error.userFacingMessage() ?: "加入一起听失败")
            }
        }
    }

    /**
     * 退出房间。
     *
     * ⚡ 房主与成员都调用 `listen/together/end/v2`（只有 roomId 参数）：
     * 对房主而言是解散整场，对成员而言是把自己移出房间。
     * 之前成员只做本地清理、依赖服务端超时移除，会导致对方成员列表长时间残留自己；
     * 参考实现（MeloX）也是两者共用同一端点，故这里统一上报。
     */
    fun leaveRoom() {
        ensureStarted()
        val activeRoom = room ?: return
        scope.launch {
            runCatching { api.endRoom(activeRoom.id) }
                .onFailure { Timber.w(it, "ListenTogether: end/leave room failed") }
            resetRoom()
        }
    }

    /** 房主分享邀请文本 */
    fun inviteText(): String? {
        val activeRoom = room ?: return null
        return buildListenTogetherInviteText(songId = lastKnownSongId, room = activeRoom)
    }

    // ─── 同步循环 ─────────────────────────────────────────────────────

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            // 账号切换（cookie 变化）会同时让缓存的 uid 与房间身份失效
            val cookieFingerprint = net.moriafly.ncm.NcmSession.INSTANCE?.cookies.hashCode()
            if (cookieFingerprint != lastCookieFingerprint) {
                lastCookieFingerprint = cookieFingerprint
                cachedUserId = null
                if (room != null) resetRoom()
            }

            if (!net.moriafly.ncm.NcmApi.isLogin) {
                if (room != null) resetRoom()
                delay(IDLE_POLL_MS)
                continue
            }

            statusTick++
            if (room != null) {
                // 房内：每 5 个 tick（约 5s）刷新一次房间状态（成员/房间是否结束）
                if (statusTick >= STATUS_EVERY_TICKS) {
                    statusTick = 0
                    refreshRoomStatus()
                }
            } else {
                // 空闲：每轮查一次账号是否被拉进房间（App 重启后自动恢复会话）。
                // ⚡ 失败（离线 / 服务连不上）时按轮数退避：60s → 120s → 240s → 480s，
                //    否则断网时每分钟都会锤一次接口并产生一条错误日志。
                statusTick = 0
                idleFailures = if (refreshRoomStatus()) 0 else (idleFailures + 1).coerceAtMost(MAX_IDLE_BACKOFF_SHIFT)
            }

            if (room != null && controller == null && !controllerConnectionPending) {
                connectControllerIfNeeded()
            }
            val activeRoom = room ?: run {
                delay(idlePollDelayMs())
                return@run null
            } ?: continue

            // ⚡ 等待房主：房主已不在房间成员里，远端快照不会再变化。
            //    降频轮询房间成员（房主一回来立刻恢复跟随），期间只保留心跳维持房间活跃。
            if (_state.value.waitingForHost) {
                refreshRoomStatus()
                heartbeatTick++
                if (heartbeatTick >= HEARTBEAT_EVERY_TICKS) {
                    heartbeatTick = 0
                    sendHeartbeat(activeRoom)
                }
                delay(HOST_WAITING_POLL_MS)
                continue
            }

            runCatching { api.playback(activeRoom.id) }
                .onSuccess { snapshot ->
                    failures = 0
                    _state.update {
                        it.copy(
                            phase = ListenTogetherPhase.Connected,
                            room = activeRoom,
                            consecutiveFailures = 0
                        )
                    }
                    synchronizeRemote(activeRoom, snapshot)
                }
                .onFailure(::recordFailure)

            heartbeatTick++
            if (heartbeatTick >= HEARTBEAT_EVERY_TICKS) {
                heartbeatTick = 0
                sendHeartbeat(activeRoom)
            }
            // ⚡ 连续失败时退避（1s → 2s → 4s → 8s）：服务端「系统错误 / 限流」时
            //    每秒锤接口只会把限流拖得更久；成功一次立刻回到 1s。
            delay(syncDelayMs())
        }
    }

    /** 刷新房间状态；返回本次请求是否成功（供空闲探测决定是否退避）。 */
    private suspend fun refreshRoomStatus(): Boolean {
        return runCatching { api.roomStatus() }
            .onSuccess { latest ->
                if (latest == null) {
                    if (room != null) resetRoom()
                    return@onSuccess
                }
                val changedRoom = room?.id != latest.id
                val wasWaitingForHost = _state.value.waitingForHost
                room = latest
                val selfId = currentUserId()?.toString()
                val host = latest.creatorId == selfId
                // ⚡ 网易云没有专门的"房主暂离/waiting"字段：用 creatorId 是否仍出现在
                //    roomUsers 里推导房主是否还在房间。users 为空表示服务端没下发成员，
                //    按"在"处理，避免误报让听众白等。
                val hostPresent = latest.users.isEmpty() ||
                    latest.users.any { it.id == latest.creatorId }
                if (changedRoom) {
                    isHost = host
                    firstSyncForRoom = true
                    lastRemoteQueueSignature = null
                    lastRemoteCommandSignature = null
                    playlistVersion = 1
                    heartbeatTick = HEARTBEAT_EVERY_TICKS
                    // 服务端发现"已在房间"（含 App 重启恢复）同样提示门禁生效
                    notify(ACTIVATION_MESSAGE)
                }
                _state.update {
                    it.copy(
                        phase = if (failures > 0) ListenTogetherPhase.Reconnecting else ListenTogetherPhase.Connected,
                        room = latest,
                        isHost = host,
                        selfUserId = selfId,
                        hostPresent = hostPresent
                    )
                }
                // 房主离开 / 回到房间各提示一次（听众侧）
                val waitingNow = !host && !hostPresent
                when {
                    !wasWaitingForHost && waitingNow -> notify(HOST_LEFT_MESSAGE)
                    wasWaitingForHost && !waitingNow -> notify(HOST_BACK_MESSAGE)
                }
            }
            .onFailure { if (room != null) recordFailure(it) }
            .isSuccess
    }

    /** 空闲探测间隔：随连续失败次数退避（60s → 120s → 240s → 480s）。 */
    private fun idlePollDelayMs(): Long = IDLE_POLL_MS shl idleFailures

    private suspend fun adoptRoom(latest: ListenTogetherRoom, isHost: Boolean) {
        room = latest
        this.isHost = isHost
        failures = 0
        firstSyncForRoom = true
        lastRemoteQueueSignature = null
        lastRemoteCommandSignature = null
        playlistVersion = 1
        heartbeatTick = HEARTBEAT_EVERY_TICKS
        statusTick = 0
        _state.value = ListenTogetherState(
            phase = ListenTogetherPhase.Connected,
            room = latest,
            isHost = isHost,
            selfUserId = currentUserId()?.toString(),
            hostPresent = latest.users.isEmpty() || latest.users.any { it.id == latest.creatorId }
        )
        // ⚡ 激活即提示：一起听期间仅支持播放网易云歌曲
        notify(ACTIVATION_MESSAGE)
        connectControllerIfNeeded()
    }

    private suspend fun synchronizeRemote(
        activeRoom: ListenTogetherRoom,
        snapshot: ListenTogetherSnapshot
    ) {
        val active = controller ?: return

        if (firstSyncForRoom) {
            firstSyncForRoom = false
            if (isHost && active.mediaItemCount > 0) {
                reportQueueNow(activeRoom)
            } else {
                applyRemoteQueue(snapshot)
                if (snapshot.commandUserId != currentUserId()?.toString()) {
                    applyRemoteCommand(snapshot)
                }
            }
            return
        }

        val queueSignature = buildString {
            append(snapshot.playMode.orEmpty()).append('|')
            append(snapshot.displaySongIds.joinToString(",")).append('|')
            append(snapshot.randomSongIds.joinToString(","))
        }
        if (queueSignature != lastRemoteQueueSignature) {
            lastRemoteQueueSignature = queueSignature
            applyRemoteQueue(snapshot)
        }

        // ⚡ 去重键**不能包含 progressMs**：进度是连续量，播放中每秒都在变，
        //   把它算进来会让每秒都判成"新命令"→ 每秒重新应用一次远端状态并重新抑制本地事件上报，
        //   结果是本地按暂停既上报不出去、下一秒又被远端状态拉回播放（双方播放状态永远对不上）。
        //   这里只保留"命令身份"字段（序列号 + 命令发起人 + 目标歌曲 + 播放态）。
        val commandSignature = listOf(
            snapshot.serverSequence,
            snapshot.clientSequence,
            snapshot.commandUserId.orEmpty(),
            snapshot.targetSongId ?: -1L,
            snapshot.isPlaying
        ).joinToString(":")
        val commandFromSelf = snapshot.commandUserId == currentUserId()?.toString()
        if (!commandFromSelf && commandSignature != lastRemoteCommandSignature) {
            lastRemoteCommandSignature = commandSignature
            applyRemoteCommand(snapshot)
        }
    }

    private suspend fun applyRemoteQueue(snapshot: ListenTogetherSnapshot) {
        val active = controller ?: return
        val remoteIds = snapshot.playbackSongIds
        if (remoteIds.isEmpty()) return
        val localIds = queueNeteaseIds(active)
        applyRemoteMode(snapshot, active)
        if (remoteIds == localIds) return

        val songs = withContext(Dispatchers.IO) {
            neteaseRepository.getNeteaseSongsByIds(remoteIds)
        }
        val byId = songs.associateBy { it.neteaseId ?: 0L }
        val ordered = remoteIds.mapNotNull(byId::get)
        if (ordered.isEmpty()) return
        val target = snapshot.targetSongId
            ?.takeIf { id -> ordered.any { it.neteaseId == id } }
            ?: ordered.first().neteaseId
            ?: return
        val targetIndex = ordered.indexOfFirst { it.neteaseId == target }.coerceAtLeast(0)
        val items = ordered.map { MediaItemBuilder.build(it) }
        suppressLocalEvents()
        expectedRemoteSongId = target
        active.shuffleModeEnabled = false
        active.setMediaItems(items, targetIndex, snapshot.progressMs.coerceAtLeast(0L))
        active.prepare()
        if (snapshot.isPlaying) active.play() else active.pause()
        lastKnownSongId = target
    }

    private fun applyRemoteMode(snapshot: ListenTogetherSnapshot, active: MediaController) {
        // randomList 已经是确定的播放顺序，再开 Media3 shuffle 会二次随机
        active.shuffleModeEnabled = false
        val mode = snapshot.playMode?.uppercase() ?: return
        active.repeatMode = when {
            mode.contains("ONE") || mode.contains("SINGLE") -> Player.REPEAT_MODE_ONE
            mode.contains("LOOP") || mode.contains("ALL") -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun applyRemoteCommand(snapshot: ListenTogetherSnapshot) {
        val active = controller ?: return
        val targetId = snapshot.targetSongId
        expectedRemoteSongId = targetId ?: neteaseIdOf(active.currentMediaItem)

        val targetIndex = targetId?.let { id ->
            (0 until active.mediaItemCount).firstOrNull {
                neteaseIdOf(active.getMediaItemAt(it)) == id
            }
        }
        val switchSong = targetIndex != null && targetIndex != active.currentMediaItemIndex
        val needsDriftCorrection = !switchSong && targetIndex != null &&
            kotlin.math.abs(active.currentPosition - snapshot.progressMs) >= DRIFT_CORRECTION_MS
        // ⚡ 播放/暂停只在「与本地播放意图不一致」时才下发，并且用 playWhenReady（意图）判断，
        //   而不是 isPlaying（缓冲期间会短暂为 false）——否则会把缓冲误判成暂停，反复 play/pause 抖动。
        //   幂等判断同时保证了"本地刚按下暂停时不会被远端旧状态立刻拉回播放"。
        val needsPlayStateSync = snapshot.isPlaying != active.playWhenReady

        if (!switchSong && !needsDriftCorrection && !needsPlayStateSync) return

        // 仅在实际动作前抑制本地回声上报：稳态下不再调用，避免把用户操作无限期屏蔽掉
        suppressLocalEvents()
        when {
            switchSong -> {
                active.seekTo(targetIndex!!, snapshot.progressMs.coerceAtLeast(0L))
                lastKnownSongId = targetId
            }
            needsDriftCorrection -> active.seekTo(snapshot.progressMs.coerceAtLeast(0L))
        }
        if (needsPlayStateSync) {
            if (snapshot.isPlaying) active.play() else active.pause()
        }
    }

    // ─── 本地事件 → 远端上报 ─────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (localEventsSuppressed()) return
            val active = controller ?: return
            // Media3 缓冲期间会短暂上报 isPlaying=false（playWhenReady=true），
            // 把它当 PAUSE 上报会让对方也暂停
            if (!isPlaying && active.playWhenReady && active.playbackState == Player.STATE_BUFFERING) return
            val songId = neteaseIdOf(active.currentMediaItem) ?: return
            reportCommand(
                if (isPlaying) ListenTogetherCommandType.Play else ListenTogetherCommandType.Pause,
                targetSongId = songId,
                formerSongId = lastKnownSongId ?: songId
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val target = neteaseIdOf(mediaItem)
            val former = lastKnownSongId
            lastKnownSongId = target
            if (localEventsSuppressed() || target == null) return
            reportCommand(ListenTogetherCommandType.GoTo, target, former)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK || localEventsSuppressed()) return
            val active = controller ?: return
            val target = neteaseIdOf(active.currentMediaItem) ?: return
            reportCommand(ListenTogetherCommandType.Progress, target, lastKnownSongId ?: target)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (localEventsSuppressed()) return
            scheduleQueueReport()
        }
    }

    private fun reportCommand(
        type: ListenTogetherCommandType,
        targetSongId: Long,
        formerSongId: Long?
    ) {
        val activeRoom = room ?: return
        val active = controller ?: return
        scope.launch {
            commandMutex.withLock {
                runCatching {
                    api.reportCommand(
                        roomId = activeRoom.id,
                        commandType = type,
                        progressMs = active.currentPosition.coerceAtLeast(0L),
                        isPlaying = active.isPlaying,
                        formerSongId = formerSongId,
                        targetSongId = targetSongId,
                        clientSequence = commandSequence.getAndIncrement()
                    )
                }.onFailure(::recordFailure)
            }
        }
    }

    private fun scheduleQueueReport() {
        if (room == null) return
        queueReportJob?.cancel()
        queueReportJob = scope.launch {
            delay(QUEUE_DEBOUNCE_MS)
            room?.let { reportQueueNow(it) }
        }
    }

    private suspend fun reportQueueNow(activeRoom: ListenTogetherRoom) {
        val active = controller ?: return
        val userId = currentUserId() ?: return
        val neteaseIds = queueNeteaseIds(active)
        if (neteaseIds.isEmpty()) return
        playlistMutex.withLock {
            runCatching {
                api.reportPlaylist(
                    roomId = activeRoom.id,
                    userId = userId,
                    version = playlistVersion++,
                    displaySongIds = neteaseIds,
                    randomSongIds = neteaseIds
                )
            }.onFailure(::recordFailure)
        }
    }

    private suspend fun sendHeartbeat(activeRoom: ListenTogetherRoom) {
        val active = controller ?: return
        val songId = neteaseIdOf(active.currentMediaItem) ?: return
        runCatching {
            api.heartbeat(
                roomId = activeRoom.id,
                songId = songId,
                isPlaying = active.isPlaying,
                progressMs = active.currentPosition.coerceAtLeast(0L)
            )
        }.onFailure(::recordFailure)
    }

    // ─── 控制器与工具 ─────────────────────────────────────────────────

    private fun connectControllerIfNeeded() {
        if (controller != null || controllerConnectionPending || room == null) return
        controllerConnectionPending = true
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { connected ->
                        controllerConnectionPending = false
                        if (room == null) {
                            connected.release()
                            return@onSuccess
                        }
                        controller?.removeListener(playerListener)
                        controller = connected
                        connected.addListener(playerListener)
                        lastKnownSongId = neteaseIdOf(connected.currentMediaItem)
                    }
                    .onFailure { error ->
                        controllerConnectionPending = false
                        Timber.w(error, "ListenTogether: MediaController unavailable")
                    }
            },
            mainExecutor
        )
    }

    private suspend fun awaitController(timeoutMs: Long): MediaController? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            controller?.let { return it }
            if (!controllerConnectionPending && controller == null) connectControllerIfNeeded()
            delay(100L)
        }
        return controller
    }

    private suspend fun currentUserId(): Long? {
        cachedUserId?.let { return it }
        val resolved = runCatching { neteaseRepository.getNeteaseUserId() }.getOrNull()
        if (resolved != null && resolved > 0L) cachedUserId = resolved
        return resolved
    }

    /** 从队列 MediaItem 提取网易云歌曲 ID（统一逻辑见 [NeteaseMediaIds]） */
    private fun neteaseIdOf(item: MediaItem?): Long? = NeteaseMediaIds.neteaseIdOf(item)

    private fun queueNeteaseIds(player: Player): List<Long> = buildList {
        for (index in 0 until player.mediaItemCount) {
            neteaseIdOf(player.getMediaItemAt(index))?.let(::add)
        }
    }

    private fun suppressLocalEvents() {
        val now = SystemClock.elapsedRealtime()
        suppressLocalUntilRealtime = now + REMOTE_SUPPRESSION_MS
        suppressHardUntilRealtime = now + REMOTE_SUPPRESSION_MAX_MS
    }

    /**
     * 本地事件（Media3 的 Player.Listener 回调）是否应当被忽略、不上报给服务端。
     *
     * 除了固定的抑制窗口外，还额外判断"本地是否已经收敛到远端目标状态"：
     * 一次换队列/切歌要走网络取歌 + prepare，慢的时候远超窗口长度，
     * 期间触发的 onIsPlayingChanged / onMediaItemTransition 若被当成用户操作回传，
     * 就会形成回声（对方又跟着切一次）。
     */
    private fun localEventsSuppressed(): Boolean {
        if (room == null) return true
        val now = SystemClock.elapsedRealtime()
        if (now < suppressLocalUntilRealtime) return true
        // 硬上限：即使用户网络一直卡在缓冲，也不能无限期屏蔽用户自己的操作上报
        if (now >= suppressHardUntilRealtime) return false
        val active = controller ?: return false
        val expected = expectedRemoteSongId
        return active.playbackState == Player.STATE_BUFFERING ||
            (expected != null && neteaseIdOf(active.currentMediaItem) != expected)
    }

    private fun recordFailure(error: Throwable) {
        failures = (failures + 1).coerceAtMost(99)
        _state.update {
            it.copy(
                phase = if (room == null) ListenTogetherPhase.Idle else ListenTogetherPhase.Reconnecting,
                consecutiveFailures = failures,
                // 统一走友好映射：同步循环里也可能夹着非一起听 API 的异常（取歌曲详情等），
                // 避免「系统错误」这类服务端原文直出面板
                lastError = error.userFacingMessage() ?: it.lastError
            )
        }
        Timber.w(error, "ListenTogether: sync failure #%d", failures)
    }

    /** 异常 → 用户可读文案；空白消息返回 null（保留原有提示） */
    private fun Throwable.userFacingMessage(): String? =
        message?.takeIf { it.isNotBlank() }?.let { friendlyListenTogetherMessage(it) }

    /** 同步轮询间隔：连续失败时指数退避，避免把服务端限流拖得更久 */
    private fun syncDelayMs(): Long = SYNC_INTERVAL_MS shl failures.coerceIn(0, MAX_BACKOFF_SHIFT)

    private fun setError(message: String) {
        _state.update { it.copy(lastError = message) }
    }

    /** 供 UI 层主动提示错误（显示在面板的错误栏） */
    fun reportError(message: String) {
        setError(message)
    }

    private fun resetRoom() {
        room = null
        isHost = false
        failures = 0
        heartbeatTick = 0
        statusTick = 0
        idleFailures = 0
        firstSyncForRoom = true
        lastRemoteQueueSignature = null
        lastRemoteCommandSignature = null
        lastKnownSongId = null
        expectedRemoteSongId = null
        suppressLocalUntilRealtime = 0L
        suppressHardUntilRealtime = 0L
        queueReportJob?.cancel()
        queueReportJob = null
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        _state.value = ListenTogetherState()
    }

    private companion object {
        const val SYNC_INTERVAL_MS = 1_000L
        const val IDLE_POLL_MS = 60_000L
        /** 空闲探测失败时的最大退避位移：60s << 3 = 8min */
        const val MAX_IDLE_BACKOFF_SHIFT = 3
        const val STATUS_EVERY_TICKS = 5
        const val HEARTBEAT_EVERY_TICKS = 5
        const val QUEUE_DEBOUNCE_MS = 350L

        /**
         * 应用远端状态后抑制本地事件上报的基础时长。
         * 900ms 在慢速 prepare（缓冲/切歌需要数秒）时会提前失效，
         * 导致本地播放器回调被当成"用户操作"回传给服务端，形成回声。
         */
        const val REMOTE_SUPPRESSION_MS = 1_500L

        /** 抑制的硬上限：即使本地一直没收敛，也不能无限抑制用户操作上报 */
        const val REMOTE_SUPPRESSION_MAX_MS = 8_000L

        /** 等待房主期间的房间状态轮询间隔（比正常 1s 慢，但仍能较快发现房主回来） */
        const val HOST_WAITING_POLL_MS = 3_000L

        const val DRIFT_CORRECTION_MS = 1_200L

        /** 同步退避的最大位移：SYNC_INTERVAL_MS << 3 = 8s */
        const val MAX_BACKOFF_SHIFT = 3

        /** 激活提示：一起听开启时自动弹一次 */
        const val ACTIVATION_MESSAGE = "一起听已开启：期间仅支持播放网易云歌曲"

        /** 听众侧：房主离开 / 回来各提示一次 */
        const val HOST_LEFT_MESSAGE = "房主已离开一起听房间，正在等待房主回来"
        const val HOST_BACK_MESSAGE = "房主已回到一起听房间，恢复同步"
    }
}
