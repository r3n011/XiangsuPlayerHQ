package com.theveloper.pixelplay.data.lx

import kotlinx.serialization.Serializable

@Serializable
data class LxSongInfo(
    val id: String = "",
    val songmid: String = "",
    val hash: String = "",
    val name: String = "",
    val singer: String = "",
    val albumName: String = "",
    val duration: Long = 0L,
    val pic: String = "",
    /** 播放时成功获取 URL 的音源标识（如 "wy", "tx", "kw" 等）。从媒体库播放时会用它来调用 JS 引擎。 */
    val source: String = ""
)

@Serializable
data class LxSearchResult(
    val isEnd: Boolean = true,
    val list: List<LxSongInfo> = emptyList(),
    val total: Int = 0
)

@Serializable
data class LxSourceInfo(
    val name: String = "",
    val type: String = "",
    val actions: List<String> = emptyList(),
    val qualitys: List<String> = emptyList()
)

@Serializable
data class LxLyricResult(
    val lyric: String = ""
)

data class LxInitInfo(
    val sources: Map<String, LxSourceInfo> = emptyMap()
)

/**
 * 用户信息（来自 /user/detail 接口）。
 */
data class NeteaseUserDetail(
    val userId: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String = "",
    val signature: String? = null,
    val description: String? = null
)

/**
 * 单条评论信息。
 */
data class NeteaseComment(
    val commentId: Long = 0L,
    val content: String = "",
    val time: Long = 0L,
    val timeStr: String = "",
    val likedCount: Int = 0,
    val liked: Boolean = false,
    val user: NeteaseCommentUser = NeteaseCommentUser()
)

/**
 * 评论发布者信息。
 */
data class NeteaseCommentUser(
    val userId: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String = ""
)

/**
 * 评论列表响应模型。
 */
data class NeteaseCommentResult(
    val comments: List<NeteaseComment> = emptyList(),
    val hotComments: List<NeteaseComment> = emptyList(),
    val hasMore: Boolean = false,
    val totalCount: Int = 0,
    val cursor: Long = 0L
)
