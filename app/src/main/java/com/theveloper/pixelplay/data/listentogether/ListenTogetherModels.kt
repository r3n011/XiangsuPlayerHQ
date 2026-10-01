package com.theveloper.pixelplay.data.listentogether

import com.theveloper.pixelplay.data.model.Song
import java.net.URLDecoder

/** 一起听房间成员 */
data class ListenTogetherMember(
    val id: String,
    val name: String,
    val avatarUrl: String?
)

/** 一起听房间（roomId + 创建者 + 成员列表） */
data class ListenTogetherRoom(
    val id: String,
    val creatorId: String,
    val users: List<ListenTogetherMember>
) {
    fun memberName(userId: String?): String? =
        users.firstOrNull { it.id == userId }?.name
}

enum class ListenTogetherPhase {
    Idle,
    Connecting,
    Connected,
    Reconnecting
}

/** 一起听会话状态（UI 与播放门禁都读这里） */
data class ListenTogetherState(
    val phase: ListenTogetherPhase = ListenTogetherPhase.Idle,
    val room: ListenTogetherRoom? = null,
    val isHost: Boolean = false,
    val consecutiveFailures: Int = 0,
    val lastError: String? = null,
    /** 当前登录账号的网易云 uid，用于在成员列表里区分"我" */
    val selfUserId: String? = null,
    /** 房主是否仍在房间成员列表中（网易云无专用暂离字段，用它推导） */
    val hostPresent: Boolean = true
) {
    val active: Boolean get() = room != null

    /** 我是听众，且房主已不在成员列表：处于"等待房主"状态 */
    val waitingForHost: Boolean get() = active && !isHost && !hostPresent

    /** 我自己的成员信息（可能为 null：uid 未解析或不在列表里） */
    val selfMember: ListenTogetherMember?
        get() = selfUserId?.let { id -> room?.users?.firstOrNull { it.id == id } }

    /** 除我以外的成员，通常就是一起听的对象 */
    val otherMembers: List<ListenTogetherMember>
        get() = room?.users?.filter { it.id != selfUserId }.orEmpty()
}

/** 远端播放命令类型（与网易云 listen/together 协议的字符串值一致） */
enum class ListenTogetherCommandType(val wireValue: String) {
    Play("PLAY"),
    Pause("PAUSE"),
    Next("NEXT"),
    Previous("PREV"),
    GoTo("GOTO"),
    Progress("PROGRESS")
}

/**
 * 一次 `listen/together/sync/playlist/get` 返回的播放快照。
 *
 * - [displaySongIds] ：房间的展示顺序队列
 * - [randomSongIds]  ：随机模式下的确定播放顺序（已由服务端排好，客户端不再自行 shuffle）
 */
data class ListenTogetherSnapshot(
    val displaySongIds: List<Long>,
    val randomSongIds: List<Long>,
    val playMode: String?,
    val targetSongId: Long?,
    val formerSongId: Long?,
    val progressMs: Long,
    val isPlaying: Boolean,
    val commandUserId: String?,
    val clientSequence: Long,
    val serverSequence: Long
) {
    val randomMode: Boolean
        get() = playMode?.uppercase()?.let { it.contains("RANDOM") || it.contains("SHUFFLE") } == true

    val playbackSongIds: List<Long>
        get() = if (randomMode && randomSongIds.isNotEmpty()) randomSongIds else displaySongIds
}

/** 一起听邀请链接（网易云官方分享链接，兼容新旧两种参数拼写） */
data class ListenTogetherInvitation(
    val roomId: String,
    val inviterId: String,
    val songId: Long?
)

/**
 * 解析一起听邀请文本。网易云目前至少有两种链接格式：
 * - /listen-together/share/?roomId=...&inviterId=...
 * - /listen-together/multishare/index.html?roomId=...&inviterUid=...
 * 文本里可能混有其它说明文字或 HTML 转义的 `&amp;`，因此全文扫描而不是按固定 URL 前缀截取。
 */
fun parseListenTogetherInvitation(text: String): ListenTogetherInvitation? {
    val normalized = text.trim().replace("&amp;", "&", ignoreCase = true)
    if (normalized.isBlank()) return null

    val values = mutableMapOf<String, String>()
    QUERY_PARAMETER.findAll(normalized).forEach { match ->
        val key = match.groupValues[1].lowercase()
        val value = runCatching { URLDecoder.decode(match.groupValues[2], "UTF-8") }
            .getOrDefault(match.groupValues[2])
            .trim()
        if (value.isNotBlank() && key !in values) values[key] = value
    }

    val roomId = values["roomid"]?.takeIf(String::isNotBlank) ?: return null
    val inviterId = values["inviterid"]?.takeIf(String::isNotBlank)
        ?: values["inviteruid"]?.takeIf(String::isNotBlank)
        ?: return null

    return ListenTogetherInvitation(
        roomId = roomId,
        inviterId = inviterId,
        songId = values["songid"]?.toLongOrNull()
    )
}

private val QUERY_PARAMETER = Regex(
    pattern = "(?i)(?:[?&]|^)(roomId|inviterId|inviterUid|songId)=([^&#\\s)\\]]+)"
)

/** 网易云官方邀请分享链接（房主用系统分享面板发出，收件人复制后加入） */
fun buildListenTogetherInviteText(songId: Long?, room: ListenTogetherRoom): String {
    val inviter = room.creatorId.ifBlank { room.users.firstOrNull()?.id.orEmpty() }
    val songPart = songId?.takeIf { it > 0L }?.let { "songId=$it&" } ?: ""
    return "https://st.music.163.com/listen-together/share/?${songPart}roomId=${room.id}&inviterId=$inviter"
}

/** 判断歌曲是否为可一起听的网易云歌曲（含官方代理 URI 与已带的 neteaseId） */
fun Song.isNeteaseTogetherSong(): Boolean =
    neteaseId != null || contentUriString.startsWith("netease://", ignoreCase = true)
