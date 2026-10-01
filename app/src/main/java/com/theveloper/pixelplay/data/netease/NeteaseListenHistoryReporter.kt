package com.theveloper.pixelplay.data.netease

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.moriafly.ncm.NcmApi
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * 累计"真实在播"时长：单调时钟计时，暂停/缓冲不计入，seek 不影响累计值。
 * [elapsedMs] 可用歌曲总时长 clamp，保证上报时长不超过歌曲本身。
 *
 * 移植自 MeloX 的 ListenedTimeTracker（与官方客户端"听歌打卡"的有效时长语义一致）。
 */
class ListenedTimeTracker {
    private var listenedMs = 0L
    private var playingSinceMs: Long? = null

    fun reset(nowMs: Long, isPlaying: Boolean) {
        listenedMs = 0L
        playingSinceMs = nowMs.takeIf { isPlaying }
    }

    fun onPlayingChanged(nowMs: Long, isPlaying: Boolean) {
        settle(nowMs)
        playingSinceMs = nowMs.takeIf { isPlaying }
    }

    fun elapsedMs(nowMs: Long, durationMs: Long? = null): Long {
        val current = listenedMs + (playingSinceMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)
        return durationMs?.takeIf { it > 0L }?.let(current::coerceAtMost) ?: current
    }

    private fun settle(nowMs: Long) {
        playingSinceMs?.let { listenedMs += (nowMs - it).coerceAtLeast(0L) }
        playingSinceMs = null
    }
}

/**
 * 网易云听歌数据上报（听歌打卡）：
 * 把真实播放过的网易云歌曲回传给官方 `feedback/weblog`，计入听歌排行/年度报告。
 *
 * 端点与字段对齐 MeloX 的生产实现：
 * - `startplay`：歌曲开播时上报；
 * - `play`：一次收听结算（切歌/自然播完/服务销毁），`time` 为实际收听秒数。
 * 传输优先 eapi + 官方日志域名 clientlog.music.163.com（MeloX 验证路线），
 * 失败回落 weapi music.163.com（社区 scrobble 路线）。
 *
 * 生命周期：每个 [com.theveloper.pixelplay.data.service.MusicService] 实例一个 reporter；
 * [close] 后延迟 1.5s 再取消 scope，给最后一次上报留窗口。仅网易云歌曲会产生上报。
 */
class NeteaseListenHistoryReporter @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun recordStart(songId: Long) {
        if (songId <= 0L) return
        submit(
            action = "startplay",
            fields = JSONObject()
                .put("id", songId.toString())
                .put("type", "song")
                .put("mainsite", "1")
                .put("mainsiteWeb", "1")
                .put("content", "id=0")
        )
    }

    fun recordDuration(songId: Long, elapsedMs: Long, durationMs: Long? = null) {
        if (songId <= 0L) return
        val elapsedSeconds = (elapsedMs.coerceAtLeast(0L) / 1000L).toInt()
        val durationSeconds = durationMs?.takeIf { it > 0L }?.let { (it / 1000L).toInt() }
        val seconds = durationSeconds?.let { minOf(elapsedSeconds, it) } ?: elapsedSeconds
        if (seconds <= 0) return
        submit(
            action = "play",
            fields = JSONObject()
                .put("download", 0)
                .put("end", "playend")
                .put("id", songId.toString())
                .put("sourceId", "0")
                .put("time", seconds.toString())
                .put("type", "song")
                .put("wifi", 0)
                .put("source", "list")
                .put("mainsite", "1")
                .put("mainsiteWeb", "1")
                .put("content", "id=0")
        )
    }

    fun close() {
        scope.launch {
            delay(1_500L)
            scope.cancel()
        }
    }

    private fun submit(action: String, fields: JSONObject) {
        if (!NcmApi.isLogin) return
        scope.launch {
            mutex.withLock {
                runCatching {
                    val logs = JSONArray()
                        .put(JSONObject().put("action", action).put("json", fields))
                    runCatching {
                        // ① eapi + 官方日志域名（MeloX 验证过的生产路线）
                        net.moriafly.ncm.NcmModulesFull.rawEapi(
                            path = "/api/feedback/weblog",
                            params = mapOf("logs" to logs.toString()),
                            url = "https://clientlog.music.163.com/eapi/feedback/weblog"
                        ).getOrThrow()
                    }.getOrElse {
                        // ② 回落：社区 scrobble 的 weapi 路线
                        NcmApi.FullAccess.rawWeapi(
                            path = "/api/feedback/weblog",
                            params = mapOf("logs" to logs.toString())
                        ).getOrThrow()
                    }
                }.onFailure {
                    Timber.w(it, "NetEase listen history upload failed (%s)", action)
                }
            }
        }
    }
}
