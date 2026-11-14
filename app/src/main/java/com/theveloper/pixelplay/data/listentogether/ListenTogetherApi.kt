package com.theveloper.pixelplay.data.listentogether

import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

import net.moriafly.ncm.NcmApi
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import kotlinx.coroutines.delay

/** 网易云偶发返回空响应体：与真实业务错误区分开，便于上层决定重试 / 降级 eapi。 */
internal class EmptyResponseException(path: String) : IOException(AppLocaleManager.getString(R.string.ui_netease_returned_an_empty_response, (path).toString()))

private val CODE_IN_MESSAGE = Regex("code=(-?\\d+)")

/** status/get 空响应时的重试次数（对齐 MeloX：3 次、180ms×n 退避） */
private const val STATUS_GET_ATTEMPTS = 3

private const val TAG = "ListenTogetherApi"

/**
 * 网易云「一起听」API 封装（基于内置 net.moriafly.ncm SDK）。
 *
 * 端点家族与官方客户端一致（`/api/listen/together` 前缀，经 [NcmApi.rawEapi] 走 eapi 加密，
 * 登录态 MUSIC_U 由 NcmSession 注入）；请求/响应形状与 MeloX 验证过的实现对齐：
 * - 房间生命周期：create / check / invitation accept / status / end
 * - 同步循环：sync/playlist/get（拉快照）+ heartbeat（上报心跳）
 * - 上报：sync/list/command/report（队列 REPLACE）+ play/command/report（播放命令）
 *
 * 所有解析均为防御式：字段缺失/类型漂移时降级而不是抛错。
 */
class ListenTogetherApi {

    /** 创建一起听房间（房主） */
    suspend fun createRoom(): ListenTogetherRoom {
        val root = eapi(
            "/api/listen/together/room/create",
            mapOf("refer" to "songplay_more")
        )
        return parseRoom(root.optJSONObject("data")?.optJSONObject("roomInfo"))
            ?: throw IOException(AppLocaleManager.getString(R.string.ui_netease_did_not_return_a_valid_listen_together_room))
    }

    /** 查询当前账号所在的一起听房间；不在房间时返回 null */
    suspend fun roomStatus(): ListenTogetherRoom? {
        // ⚡ status/get 固定走 weapi（MeloX 与 NeteaseCloudMusicApi 都是这么写死的），
        //    且**只在 weapi 返回空响应时**才降级 eapi：真实的服务端错误（系统错误 / 登录失效）
        //    必须原样抛出，否则会被伪装成「不在房间」，房间会莫名其妙消失。
        //    空响应本身也重试几次（网易云偶发返回空体），退避 180ms×n，与 MeloX 一致。
        var sawEmptyResponse = false
        for (attempt in 0 until STATUS_GET_ATTEMPTS) {
            val result = runCatching { weapi("/api/listen/together/status/get", emptyMap()) }
            result.getOrNull()?.let { root -> return parseStatus(root) }
            val error = result.exceptionOrNull()
            if (error is EmptyResponseException) {
                sawEmptyResponse = true
                if (attempt < STATUS_GET_ATTEMPTS - 1) delay(180L * (attempt + 1))
            } else {
                throw error ?: IOException(AppLocaleManager.getString(R.string.ui_could_not_check_listen_together_status))
            }
        }
        if (!sawEmptyResponse) return null
        Timber.w("ListenTogetherApi: status/get weapi 连续空响应，降级 eapi")
        return parseStatus(eapi("/api/listen/together/status/get", emptyMap()))
    }

    /** 解析 status/get 的响应：不在房间 / 没有房间信息都返回 null */
    private fun parseStatus(root: JSONObject): ListenTogetherRoom? {
        val data = root.optJSONObject("data") ?: return null
        val roomInfo = data.optJSONObject("roomInfo")
        val inRoom = if (data.has("inRoom")) data.optBoolean("inRoom", false) else roomInfo != null
        if (!inRoom) return null
        return parseRoom(roomInfo)
    }

    /** 检查房间是否可加入（返回不可加入的原因描述，可加入返回 null） */
    suspend fun checkRoomUnjoinableReason(roomId: String): String? {
        val root = eapi("/api/listen/together/room/check", mapOf("roomId" to roomId))
        val data = root.optJSONObject("data")
            ?: throw IOException(
                root.optString("message").ifBlank {
                    root.optString("msg").ifBlank { AppLocaleManager.getString(R.string.ui_netease_did_not_return_listen_together_room_status) }
                }
            )
        val joinable = if (data.has("joinable")) data.optBoolean("joinable", false)
        else data.optBoolean("canJoin", false)
        if (joinable) return null

        val status = data.optString("status").trim()
        val type = data.optString("type").trim()
        val serverMessage = root.optString("message").ifBlank { root.optString("msg") }.trim()
        return when {
            status.equals("FULL", true) -> AppLocaleManager.getString(R.string.ui_the_listen_together_room_is_full)
            status.equals("END", true) || status.equals("ENDED", true) || status.equals("CLOSED", true) ->
                AppLocaleManager.getString(R.string.ui_the_listen_together_room_has_ended)
            serverMessage.isNotBlank() -> serverMessage
            status.isNotBlank() && type.isNotBlank() -> AppLocaleManager.getString(R.string.ui_cannot_join_this_listen_together_room_status_type, (status).toString(), (type).toString())
            status.isNotBlank() -> AppLocaleManager.getString(R.string.ui_cannot_join_this_listen_together_room_status, (status).toString())
            type.isNotBlank() -> AppLocaleManager.getString(R.string.ui_cannot_join_this_listen_together_room_type, (type).toString())
            else -> AppLocaleManager.getString(R.string.ui_cannot_join_this_listen_together_room)
        }
    }

    /** 接受邀请加入房间，返回房间信息 */
    suspend fun acceptInvite(roomId: String, inviterId: String): ListenTogetherRoom {
        val root = eapi(
            "/api/listen/together/play/invitation/accept",
            mapOf(
                "refer" to "inbox_invite",
                "roomId" to roomId,
                "inviterId" to inviterId
            )
        )
        return parseRoom(root.optJSONObject("data")?.optJSONObject("roomInfo"))
            ?: throw IOException(AppLocaleManager.getString(R.string.ui_joined_but_could_not_read_the_target_room_information))
    }

    /** 结束一起听（房主） */
    suspend fun endRoom(roomId: String) {
        eapi("/api/listen/together/end/v2", mapOf("roomId" to roomId))
    }

    /** 拉取房间播放快照（队列 + 播放命令） */
    suspend fun playback(roomId: String): ListenTogetherSnapshot {
        val root = eapi("/api/listen/together/sync/playlist/get", mapOf("roomId" to roomId))
        val data = root.optJSONObject("data") ?: throw IOException(AppLocaleManager.getString(R.string.ui_the_room_has_no_playback_data_yet))
        val playlist = data.optJSONObject("playlist") ?: JSONObject()
        val command = data.optJSONObject("playCommand") ?: JSONObject()

        // ⚡ 播放/暂停状态：服务端字段位置/命名不稳定（playCommand.playStatus / data.playStatus
        //   / 只有 commandType 等），此前只认 playCommand.playStatus 且"取不到就当成播放中"，
        //   一旦字段读不到就永远解析成 PLAY → 对方暂停本机不暂停（双方播放状态无法同步）。
        //   这里多路兜底解析，并把 commandType 也作为判据（PAUSE 命令必然是暂停）。
        val status = command.firstNonBlank("playStatus", "status", "playState")
            .ifBlank { data.firstNonBlank("playStatus", "status", "playState") }
            .uppercase()
        val commandType = command.firstNonBlank("commandType", "type")
            .ifBlank { data.firstNonBlank("commandType", "type") }
            .uppercase()
        val isPlaying = when {
            status.startsWith("PLAY") -> true          // PLAY / PLAYING
            status.startsWith("PAUSE") -> false        // PAUSE / PAUSED
            commandType.startsWith("PAUSE") -> false
            commandType.startsWith("PLAY") -> true
            else -> true                               // 完全没有播放状态信息时才按播放中处理
        }

        return ListenTogetherSnapshot(
            displaySongIds = parseSongList(playlist.opt("displayList")),
            randomSongIds = parseSongList(playlist.opt("randomList")),
            playMode = playlist.optString("playMode").takeIf(String::isNotBlank),
            targetSongId = readIdentifier(command, "targetSongId", "songId"),
            formerSongId = readIdentifier(command, "formerSongId"),
            progressMs = readLong(command, "progress").coerceAtLeast(0L),
            isPlaying = isPlaying,
            commandUserId = command.optString("userId").takeIf(String::isNotBlank),
            clientSequence = readLong(command, "clientSeq"),
            serverSequence = readLong(command, "serverSeq")
        )
    }

    /** 上报整个播放队列（REPLACE）。displayList 为展示顺序，随机模式额外携带 randomList */
    suspend fun reportPlaylist(
        roomId: String,
        userId: Long,
        version: Int,
        displaySongIds: List<Long>,
        randomSongIds: List<Long>
    ) {
        val display = displaySongIds.filter { it > 0L }.distinct()
        if (display.isEmpty()) return
        val random = randomSongIds.filter { it > 0L }.distinct().ifEmpty { display }
        val versionJson = JSONArray()
            .put(JSONObject().put("userId", userId).put("version", version.coerceAtLeast(1)))
        val playlistParam = JSONObject()
            .put("commandType", "REPLACE")
            .put("version", versionJson)
            .put("anchorSongId", "")
            .put("anchorPosition", -1)
            .put("randomList", JSONArray(random.map(Long::toString)))
            .put("displayList", JSONArray(display.map(Long::toString)))

        val root = eapi(
            "/api/listen/together/sync/list/command/report",
            mapOf("roomId" to roomId, "playlistParam" to playlistParam.toString())
        )
        validateAction(root, AppLocaleManager.getString(R.string.ui_could_not_sync_the_listen_together_queue))
    }

    /** 上报播放命令（播放/暂停/切歌/进度） */
    suspend fun reportCommand(
        roomId: String,
        commandType: ListenTogetherCommandType,
        progressMs: Long,
        isPlaying: Boolean,
        formerSongId: Long?,
        targetSongId: Long,
        clientSequence: Int
    ) {
        if (targetSongId <= 0L) return
        val commandInfo = JSONObject()
            .put("commandType", commandType.wireValue)
            .put("progress", progressMs.coerceAtLeast(0L))
            .put("playStatus", if (isPlaying) "PLAY" else "PAUSE")
            .put("formerSongId", (formerSongId ?: -1L).toString())
            .put("targetSongId", targetSongId.toString())
            .put("clientSeq", clientSequence.coerceAtLeast(1))

        val root = eapi(
            "/api/listen/together/play/command/report",
            mapOf("roomId" to roomId, "commandInfo" to commandInfo.toString())
        )
        validateAction(root, AppLocaleManager.getString(R.string.ui_could_not_sync_listen_together_playback))
    }

    /** 心跳：上报当前播放状态（保持房间活跃） */
    suspend fun heartbeat(
        roomId: String,
        songId: Long,
        isPlaying: Boolean,
        progressMs: Long
    ) {
        if (songId <= 0L) return
        val root = eapi(
            "/api/listen/together/heartbeat",
            mapOf(
                "roomId" to roomId,
                "songId" to songId,
                "playStatus" to if (isPlaying) "PLAY" else "PAUSE",
                "progress" to progressMs.coerceAtLeast(0L)
            )
        )
        validateAction(root, AppLocaleManager.getString(R.string.ui_listen_together_heartbeat_failed))
    }

    // ─── 内部工具 ─────────────────────────────────────────────────────

    private suspend fun eapi(path: String, params: Map<String, Any?>): JSONObject =
        request(path, params, isEapi = true)

    private suspend fun weapi(path: String, params: Map<String, Any?>): JSONObject =
        request(path, params, isEapi = false)

    private suspend fun request(
        path: String,
        params: Map<String, Any?>,
        isEapi: Boolean,
    ): JSONObject {
        val transport = if (isEapi) "eapi" else "weapi"
        val raw = if (isEapi) {
            NcmApi.FullAccess.rawEapi(path, params)
        } else {
            NcmApi.FullAccess.rawWeapi(path, params)
        }.getOrElse { error ->
            // ⚡ 服务端错误统一在这里翻译 + 记日志：底层抛的是 "NCM code=xxx msg=yyy"，
            //    直接把原文丢给界面就会出现用户看不懂的「系统错误」。
            val rawMessage = error.message.orEmpty()
            val code = CODE_IN_MESSAGE.find(rawMessage)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val offline = isTransientNetworkError(error, code)
            // ⚡ 离线 / 连不上是常态（一起听空闲时每 60s 会探测一次房间状态），
            //    这类错误只记一行、不打完整堆栈：否则断网时 logcat 每分钟被一条几十行的
            //    UnknownHostException 堆栈刷满，既没诊断价值又白耗电。真正的服务端错误才留堆栈。
            if (offline) {
                Timber.tag(TAG).d(
                    "%s 暂时不可达 path=%s: %s",
                    transport,
                    path,
                    rawMessage.ifBlank { error.javaClass.simpleName }
                )
            } else {
                Timber.w(error, "ListenTogetherApi: %s 失败 path=%s code=%s", transport, path, code)
            }
            throw IOException(friendlyMessage(rawMessage, code, offline))
        }
        // 空响应体：网易云偶发返回空体，单独区分出来，便于上层决定重试 / 降级
        if (raw.isEmpty()) throw EmptyResponseException(path)
        val root = JSONObject(raw)
        val code = root.optInt("code", 200)
        if (code !in 200..299) {
            val message = root.optString("message").ifBlank { root.optString("msg") }
            Timber.w("ListenTogetherApi: %s 返回 code=%s message=%s path=%s", transport, code, message, path)
            throw IOException(friendlyMessage(message, code))
        }
        return root
    }

    /**
     * 是否是「离线 / 暂时连不上」这类可自愈的传输错误（而不是服务端返回的业务错误）。
     * 判据：没有服务端 code，且异常属于网络类（DNS 解析失败、超时、拒绝连接、TLS 失败等）。
     */
    private fun isTransientNetworkError(error: Throwable, code: Int?): Boolean {
        if (code != null) return false
        return when (error) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.net.NoRouteToHostException,
            is java.net.PortUnreachableException,
            is javax.net.ssl.SSLException -> true
            // 其它无 code 的 IO 异常（连接被重置等）同样按可自愈处理
            is IOException -> error !is EmptyResponseException
            else -> false
        }
    }

    /** 服务端文案 → 用户可读文案（原始信息保留在 logcat 里，界面不再直出「系统错误」） */
    private fun friendlyMessage(rawMessage: String, code: Int?, offline: Boolean = false): String =
        friendlyListenTogetherMessage(rawMessage, code, offline)

    private fun validateAction(root: JSONObject, fallback: String) {
        val data = root.optJSONObject("data")
        if (data?.has("result") == true && !data.optBoolean("result", true)) {
            throw IOException(data.optString("message").ifBlank { fallback })
        }
        if (data?.has("success") == true && !data.optBoolean("success", true)) {
            throw IOException(data.optString("message").ifBlank { fallback })
        }
    }

    private fun parseRoom(value: JSONObject?): ListenTogetherRoom? {
        value ?: return null
        val id = value.optString("roomId")
            .ifBlank { value.optLong("roomId", 0L).takeIf { it > 0L }?.toString().orEmpty() }
        if (id.isBlank()) return null
        val users = value.optJSONArray("roomUsers") ?: JSONArray()
        return ListenTogetherRoom(
            id = id,
            creatorId = value.optString("creatorId")
                .ifBlank { value.optLong("creatorId", 0L).takeIf { it > 0L }?.toString().orEmpty() },
            users = buildList {
                for (i in 0 until users.length()) {
                    val entry = users.optJSONObject(i) ?: continue
                    val profile = entry.optJSONObject("userInfo") ?: entry
                    val userId = profile.optString("userId")
                        .ifBlank { profile.optLong("userId", 0L).toString() }
                    if (userId.isBlank() || userId == "0") continue
                    add(
                        ListenTogetherMember(
                            id = userId,
                            name = profile.optString("nickname").ifBlank { AppLocaleManager.getString(R.string.ui_netease_user) },
                            avatarUrl = profile.optString("avatarUrl").takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        )
    }

    /** displayList 可能是 [Long]、字符串数组或 {result: [...]} 包装，统一解析为去重的歌曲 ID 列表 */
    private fun parseSongList(value: Any?): List<Long> {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("result") ?: JSONArray()
            else -> JSONArray()
        }
        val seen = linkedSetOf<Long>()
        for (index in 0 until array.length()) {
            val id = when (val item = array.opt(index)) {
                is Number -> item.toLong()
                is String -> item.toLongOrNull()
                is JSONObject -> readIdentifier(item, "id", "songId")
                else -> null
            }
            if (id != null && id > 0L) seen += id
        }
        return seen.toList()
    }

    private fun readIdentifier(value: JSONObject, vararg keys: String): Long? {
        keys.forEach { key ->
            val parsed = when (val raw = value.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
            if (parsed != null && parsed > 0L) return parsed
        }
        return null
    }

    private fun readLong(value: JSONObject, key: String): Long = when (val raw = value.opt(key)) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
    }

    /** 依次从多个候选键里取第一个非空字符串（服务端字段命名不稳定时兜底） */
    private fun JSONObject.firstNonBlank(vararg keys: String): String {
        keys.forEach { key ->
            val raw = optString(key).trim()
            if (raw.isNotBlank()) return raw
        }
        return ""
    }
}

/**
 * 服务端 / SDK 文案 → 用户可读文案。
 *
 * ⚡ 一起听的错误来源不止 [ListenTogetherApi] 一条链：开房时拉歌单走的是仓库层、
 *    同步循环里取歌曲详情也可能抛 SDK 原文（如「系统错误」「NCM code=500 msg=系统错误」）。
 *    这些异常最终都会经 `error.message` 显示在面板的错误栏里 —— 统一在这里映射，
 *    保证任何来源的错误都不会把服务端原文直出给用户（原文仍完整留在 logcat）。
 */
internal fun friendlyListenTogetherMessage(
    rawMessage: String,
    code: Int? = null,
    offline: Boolean = false,
): String {
    val raw = rawMessage.trim()
    val codeSuffix = code?.let { "（$it）" }.orEmpty()
    return when {
        offline -> AppLocaleManager.getString(R.string.ui_no_network_connection_listen_together_is_temporarily_offline)
        raw.contains("系统错误") || raw.contains("系统繁忙") || code == 500 ->
            AppLocaleManager.getString(R.string.ui_listen_together_is_temporarily_unavailable_retrying_automatically, (codeSuffix).toString())
        raw.contains("登录") || code == 301 || code == -462 ->
            AppLocaleManager.getString(R.string.ui_your_netease_session_has_expired_sign_in_again_and_retry)
        raw.contains("频繁") || code == 429 ->
            AppLocaleManager.getString(R.string.ui_too_many_requests_please_try_again_later)
        raw.isNotBlank() -> raw
        code != null -> AppLocaleManager.getString(R.string.ui_listen_together_request_failed, (codeSuffix).toString())
        else -> AppLocaleManager.getString(R.string.ui_listen_together_request_failed_ec3cbc7)
    }
}
