package com.theveloper.pixelplay.data.netease.chat

import com.theveloper.pixelplay.data.listentogether.parseListenTogetherInvitation
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmBool
import net.moriafly.ncm.ncmInt
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString
import org.json.JSONObject
import timber.log.Timber

/**
 * 网易云私信 / 用户搜索关注 数据仓库。
 *
 * 全部请求走 vendored SDK `net.moriafly.ncm`（weapi 加密 + cookie 自动注入），
 * 返回 `Result<Map<String, Any?>>`，此处负责 code 校验与 DTO 解析。
 *
 * ⚠️ 私信类接口必须登录；未登录时服务端返回非 200，统一抛 [IOException]。
 */
@Singleton
class NeteaseChatRepository @Inject constructor(
    private val neteaseRepository: NeteaseRepository
) {
    private companion object {
        private const val USER_SEARCH_TYPE = 1002
    }

    /** 会话列表（对方 / 最后一条预览 / 未读数），按下发顺序即最新在前 */
    suspend fun getConversations(limit: Int = 50): Result<List<ChatContact>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val selfId = neteaseRepository.getNeteaseUserId()
                val map = NcmApi.FullAccess.msgPrivate(limit = limit, offset = 0).getOrThrow()
                map.requireSuccess()

                val contacts = map.ncmList("msgs").mapNotNull { item ->
                    val row = item as? Map<String, Any?> ?: return@mapNotNull null
                    val from = row.ncmObj("fromUser")
                    val to = row.ncmObj("toUser")
                    val fromId = from.ncmLong("userId")
                    val toId = to.ncmLong("userId")

                    // 对方 = from/to 中不等于自己的一方；自己 uid 缺失时优先 toUser
                    val other = when {
                        selfId != null && fromId == selfId -> to
                        selfId != null && toId == selfId -> from
                        toId > 0L -> to
                        else -> from
                    }
                    val otherId = other.ncmLong("userId").takeIf { it > 0L }
                        ?: maxOf(fromId, toId)
                    if (otherId <= 0L) return@mapNotNull null

                    val payload = parseMessagePayload(row.ncmString("lastMsg"))
                    val time = row.ncmLong("lastMsgTime").takeIf { it > 0L } ?: row.ncmLong("time")
                    val unread = row.ncmInt("newMsgCount").takeIf { it > 0 }
                        ?: row.ncmInt("unreadCount").takeIf { it > 0 }
                        ?: row.ncmInt("newCount")

                    ChatContact(
                        userId = otherId,
                        nickname = other.ncmString("nickname").ifBlank { otherId.toString() },
                        avatarUrl = other.ncmString("avatarUrl").ifBlank { null },
                        lastMessagePreview = payload.preview(),
                        lastMessageTimeMs = time,
                        unreadCount = unread.coerceAtLeast(0)
                    )
                }
                contacts.sortedByDescending { it.lastMessageTimeMs }
            }.onFailure { Timber.w(it, "getConversations failed") }
        }

    /** 聊天记录；beforeTime=0 取最新一页，翻页传上一页最早一条的 time */
    suspend fun getHistory(
        userId: Long,
        beforeTime: Long = 0L,
        limit: Int = 30
    ): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            val map = NcmApi.FullAccess
                .msgPrivateHistory(userId.toString(), beforeTime, limit, true)
                .getOrThrow()
            map.requireSuccess()

            map.ncmList("msgs").mapNotNull { item ->
                val row = item as? Map<String, Any?> ?: return@mapNotNull null
                val raw = row.ncmString("msg")
                val payload = parseMessagePayload(raw)
                ChatMessage(
                    id = row.ncmLong("id"),
                    fromUserId = row.ncmObj("fromUser").ncmLong("userId"),
                    toUserId = row.ncmObj("toUser").ncmLong("userId"),
                    timeMs = row.ncmLong("time"),
                    text = payload.text,
                    resource = payload.resource,
                    invitation = parseListenTogetherInvitation(payload.text)
                        ?: parseListenTogetherInvitation(raw)
                )
            }
        }.onFailure { Timber.w(it, "getHistory failed for uid=$userId") }
    }

    /** 发送文本私信 */
    suspend fun sendText(userId: Long, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val map = NcmApi.FullAccess.sendText(userIds = "[$userId]", msg = text).getOrThrow()
            map.requireSuccess()
        }.onFailure { Timber.w(it, "sendText failed for uid=$userId") }
    }

    /** 发送歌曲卡片私信（id 与 songId 都传歌曲 ID） */
    suspend fun sendSong(userId: Long, songId: Long, message: String = ""): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = songId.toString()
                val map = NcmApi.FullAccess.sendSong(userId.toString(), id, id, message).getOrThrow()
                map.requireSuccess()
            }.onFailure { Timber.w(it, "sendSong failed for uid=$userId") }
        }

    /** 用户搜索（type=1002，结果在 result.userprofiles） */
    suspend fun searchUsers(
        keyword: String,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<ChatUserSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val map = NcmApi.search(keyword, type = USER_SEARCH_TYPE, limit = limit, offset = offset)
                .getOrThrow()
            map.requireSuccess()

            map.ncmObj("result").ncmList("userprofiles").mapNotNull { item ->
                val row = item as? Map<String, Any?> ?: return@mapNotNull null
                val uid = row.ncmLong("userId")
                if (uid <= 0L) return@mapNotNull null
                ChatUserSummary(
                    userId = uid,
                    nickname = row.ncmString("nickname").ifBlank { uid.toString() },
                    avatarUrl = row.ncmString("avatarUrl").ifBlank { null },
                    signature = row.ncmString("signature").ifBlank { null },
                    followed = row.ncmBool("followed")
                )
            }
        }.onFailure { Timber.w(it, "searchUsers failed for keyword=$keyword") }
    }

    /** 用户主页摘要 */
    suspend fun getUserDetail(userId: Long): Result<ChatUserDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val map = NcmApi.FullAccess.userDetail(userId.toString()).getOrThrow()
            map.requireSuccess()
            val profile = map.ncmObj("profile")
            ChatUserDetail(
                userId = profile.ncmLong("userId").takeIf { it > 0L } ?: userId,
                nickname = profile.ncmString("nickname"),
                avatarUrl = profile.ncmString("avatarUrl").ifBlank { null },
                signature = profile.ncmString("signature").ifBlank { null },
                followed = profile.ncmBool("followed"),
                follows = profile.ncmInt("follows"),
                followeds = profile.ncmInt("followeds"),
                playlistCount = profile.ncmInt("playlistCount"),
                level = map.ncmInt("level"),
                listenSongs = map.ncmLong("listenSongs")
            )
        }.onFailure { Timber.w(it, "getUserDetail failed for uid=$userId") }
    }

    /** 关注 / 取关 */
    suspend fun setFollowed(userId: Long, followed: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val map = NcmApi.FullAccess.follow(userId.toString(), if (followed) 1 else 0)
                    .getOrThrow()
                map.requireSuccess()
            }.onFailure { Timber.w(it, "setFollowed failed for uid=$userId") }
        }

    /** 一起听邀请候选：关注列表优先，合并最近会话去重 */
    suspend fun getInviteCandidates(): Result<List<ChatContact>> = withContext(Dispatchers.IO) {
        runCatching {
            val selfId = neteaseRepository.getNeteaseUserId()
                ?: throw IOException("请先登录网易云账号")

            val follows = runCatching {
                val map = NcmApi.FullAccess
                    .userFollows(selfId.toString(), limit = 100, offset = 0, order = true)
                    .getOrThrow()
                map.requireSuccess()
                map.ncmList("follow").mapNotNull { item ->
                    val row = item as? Map<String, Any?> ?: return@mapNotNull null
                    val uid = row.ncmLong("userId")
                    if (uid <= 0L) return@mapNotNull null
                    ChatContact(
                        userId = uid,
                        nickname = row.ncmString("nickname").ifBlank { uid.toString() },
                        avatarUrl = row.ncmString("avatarUrl").ifBlank { null },
                        lastMessagePreview = "",
                        lastMessageTimeMs = 0L,
                        unreadCount = 0
                    )
                }
            }.getOrElse {
                Timber.w(it, "getInviteCandidates: userFollows failed")
                emptyList()
            }

            val merged = LinkedHashMap<Long, ChatContact>()
            follows.forEach { merged[it.userId] = it }
            getConversations(limit = 50).getOrNull().orEmpty().forEach { contact ->
                merged.putIfAbsent(contact.userId, contact)
            }
            merged.values.toList()
        }.onFailure { Timber.w(it, "getInviteCandidates failed") }
    }

    // ─── 解析辅助 ─────────────────────────────────────────────────────

    private data class ParsedPayload(val text: String, val resource: ChatResource?) {
        fun preview(): String = when (val r = resource) {
            is ChatResource.Song -> "[歌曲] ${r.name}"
            is ChatResource.Playlist -> "[歌单] ${r.name}"
            is ChatResource.Album -> "[专辑] ${r.name}"
            null -> text
        }
    }

    /**
     * `msg` 字段是一个 JSON 字符串：可能是纯文本，也可能是资源卡片
     * （{"msg": "...", "song": {...}} / "playlist" / "album"）。
     */
    private fun parseMessagePayload(raw: String): ParsedPayload {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParsedPayload("", null)
        if (!trimmed.startsWith("{")) return ParsedPayload(trimmed, null)

        return runCatching {
            val json = JSONObject(trimmed)
            ParsedPayload(
                text = json.optString("msg", ""),
                resource = parseResource(json)
            )
        }.getOrElse { ParsedPayload(trimmed, null) }
    }

    private fun parseResource(json: JSONObject): ChatResource? {
        json.optJSONObject("song")?.let { song ->
            val songId = song.optLong("id")
            if (songId > 0L) {
                val artists = buildString {
                    val ar = song.optJSONArray("ar") ?: song.optJSONArray("artists")
                    for (i in 0 until (ar?.length() ?: 0)) {
                        val name = ar?.optJSONObject(i)?.optString("name").orEmpty()
                        if (name.isBlank()) continue
                        if (isNotEmpty()) append(" / ")
                        append(name)
                    }
                }
                val cover = song.optJSONObject("al")?.optString("picUrl").orEmpty()
                    .ifBlank { song.optJSONObject("album")?.optString("picUrl").orEmpty() }
                return ChatResource.Song(
                    songId = songId,
                    name = song.optString("name", songId.toString()),
                    artists = artists,
                    coverUrl = cover.ifBlank { null }
                )
            }
        }

        json.optJSONObject("playlist")?.let { playlist ->
            val playlistId = playlist.optLong("id")
            if (playlistId > 0L) {
                return ChatResource.Playlist(
                    playlistId = playlistId,
                    name = playlist.optString("name", playlistId.toString()),
                    coverUrl = playlist.optString("coverImgUrl").ifBlank { null },
                    creator = playlist.optJSONObject("creator")?.optString("nickname")?.ifBlank { null }
                )
            }
        }

        json.optJSONObject("album")?.let { album ->
            val albumId = album.optLong("id")
            if (albumId > 0L) {
                return ChatResource.Album(
                    albumId = albumId,
                    name = album.optString("name", albumId.toString()),
                    coverUrl = album.optString("picUrl").ifBlank { null }
                )
            }
        }

        return null
    }

    /** code != 200 时抛 IOException，携带服务端 message 供 UI Toast */
    private fun Map<String, Any?>.requireSuccess() {
        val code = ncmInt("code", 200)
        if (code == 200) return
        val message = ncmString("message").ifBlank { ncmString("msg") }
        throw IOException(message.ifBlank { "网易云接口繁忙($code)" })
    }
}
