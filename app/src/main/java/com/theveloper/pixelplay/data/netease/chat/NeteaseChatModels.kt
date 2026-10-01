package com.theveloper.pixelplay.data.netease.chat

import com.theveloper.pixelplay.data.listentogether.ListenTogetherInvitation

/** 用户搜索/关注列表里的用户摘要 */
data class ChatUserSummary(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val signature: String?,
    val followed: Boolean = false
)

/** 会话列表项（对方 + 最后一条预览 + 未读数） */
data class ChatContact(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val lastMessagePreview: String,
    val lastMessageTimeMs: Long,
    val unreadCount: Int
)

/** 用户主页摘要（聊天页标题旁展示） */
data class ChatUserDetail(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val signature: String?,
    val followed: Boolean,
    val follows: Int,
    val followeds: Int,
    val playlistCount: Int,
    val level: Int,
    val listenSongs: Long
)

/** 私信里的资源卡片（歌曲 / 歌单 / 专辑） */
sealed interface ChatResource {
    val title: String

    data class Song(
        val songId: Long,
        val name: String,
        val artists: String,
        val coverUrl: String?
    ) : ChatResource {
        override val title: String get() = name
    }

    data class Playlist(
        val playlistId: Long,
        val name: String,
        val coverUrl: String?,
        val creator: String?
    ) : ChatResource {
        override val title: String get() = name
    }

    data class Album(
        val albumId: Long,
        val name: String,
        val coverUrl: String?
    ) : ChatResource {
        override val title: String get() = name
    }
}

/** 一条私信 */
data class ChatMessage(
    val id: Long,
    val fromUserId: Long,
    val toUserId: Long,
    val timeMs: Long,
    val text: String,
    val resource: ChatResource? = null,
    val invitation: ListenTogetherInvitation? = null,
    /** 本地乐观插入、尚未回执的消息 */
    val pending: Boolean = false
)
