package com.theveloper.pixelplay.data.listentogether

import net.moriafly.ncm.NcmApi
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

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
            ?: throw IOException("网易云没有返回有效的一起听房间")
    }

    /** 查询当前账号所在的一起听房间；不在房间时返回 null */
    suspend fun roomStatus(): ListenTogetherRoom? {
        // status/get 对 weapi 兼容更好：weapi 空响应时降级 eapi（与 MeloX 行为一致）
        val root = runCatching {
            weapi("/api/listen/together/status/get", emptyMap())
        }.getOrElse { error ->
            Timber.d(error, "ListenTogetherApi: status/get weapi failed, falling back to eapi")
            eapi("/api/listen/together/status/get", emptyMap())
        }
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
                    root.optString("msg").ifBlank { "网易云没有返回一起听房间状态" }
                }
            )
        val joinable = if (data.has("joinable")) data.optBoolean("joinable", false)
        else data.optBoolean("canJoin", false)
        if (joinable) return null

        val status = data.optString("status").trim()
        val type = data.optString("type").trim()
        val serverMessage = root.optString("message").ifBlank { root.optString("msg") }.trim()
        return when {
            status.equals("FULL", true) -> "一起听房间人数已满"
            status.equals("END", true) || status.equals("ENDED", true) || status.equals("CLOSED", true) ->
                "一起听房间已结束"
            serverMessage.isNotBlank() -> serverMessage
            status.isNotBlank() && type.isNotBlank() -> "该一起听房间当前无法加入（status=$status，type=$type）"
            status.isNotBlank() -> "该一起听房间当前无法加入（status=$status）"
            type.isNotBlank() -> "该一起听房间当前无法加入（type=$type）"
            else -> "该一起听房间当前无法加入"
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
            ?: throw IOException("加入成功，但未能读取目标房间信息")
    }

    /** 结束一起听（房主） */
    suspend fun endRoom(roomId: String) {
        eapi("/api/listen/together/end/v2", mapOf("roomId" to roomId))
    }

    /** 拉取房间播放快照（队列 + 播放命令） */
    suspend fun playback(roomId: String): ListenTogetherSnapshot {
        val root = eapi("/api/listen/together/sync/playlist/get", mapOf("roomId" to roomId))
        val data = root.optJSONObject("data") ?: throw IOException("房间暂时没有播放数据")
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
        validateAction(root, "同步一起听队列失败")
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
        validateAction(root, "同步一起听播放操作失败")
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
        validateAction(root, "一起听心跳失败")
    }

    // ─── 内部工具 ─────────────────────────────────────────────────────

    private suspend fun eapi(path: String, params: Map<String, Any?>): JSONObject {
        val body = NcmApi.FullAccess.rawEapi(path, params).getOrThrow()
        val root = JSONObject(body)
        val code = root.optInt("code", 200)
        if (code !in 200..299) {
            throw IOException(
                root.optString("message").ifBlank {
                    root.optString("msg").ifBlank { "一起听请求失败（$code）" }
                }
            )
        }
        return root
    }

    private suspend fun weapi(path: String, params: Map<String, Any?>): JSONObject {
        val body = NcmApi.FullAccess.rawWeapi(path, params).getOrThrow()
        val root = JSONObject(body)
        val code = root.optInt("code", 200)
        if (code !in 200..299) {
            throw IOException(
                root.optString("message").ifBlank {
                    root.optString("msg").ifBlank { "一起听请求失败（$code）" }
                }
            )
        }
        return root
    }

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
                            name = profile.optString("nickname").ifBlank { "网易云用户" },
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
