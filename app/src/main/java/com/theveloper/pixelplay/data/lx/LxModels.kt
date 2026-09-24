package com.theveloper.pixelplay.data.lx

import kotlinx.serialization.Serializable

@Serializable
data class LxSongInfo(
    val id: String = "",
    val songmid: String = "",
    val hash: String = "",
    val name: String = "",
    val singer: String = "",
    /** 多个歌手时逗号分隔的歌手 ID（与 [singer] 按顺序一一对应），用于 JS 引擎的 musicInfo.artists 数组 */
    val artistIds: String = "",
    val albumName: String = "",
    val duration: Long = 0L,
    val pic: String = "",
    /** 播放时成功获取 URL 的音源标识（如 "wy", "tx", "kw" 等）。从媒体库播放时会用它来调用 JS 引擎。 */
    val source: String = "",
    /** 音源自定义持久化 URI，例如 bilibili://bvid/cid/aid。用于从历史记录/收藏播放时动态解析最新地址。 */
    val extra: String = ""
)

@Serializable
data class LxSearchResult(
    val isEnd: Boolean = true,
    val list: List<LxSongInfo> = emptyList(),
    val total: Int = 0
)

/**
 * 网易云歌手搜索结果（/search?type=100）。
 */
@Serializable
data class LxArtistInfo(
    val id: String = "",
    val name: String = "",
    val alias: String = "",
    val picUrl: String = ""
)

@Serializable
data class LxArtistSearchResult(
    val isEnd: Boolean = true,
    val list: List<LxArtistInfo> = emptyList(),
    val total: Int = 0
)

/**
 * 在线歌单搜索结果条目（对齐落雪 ListInfoItem 的统一结构）。
 */
@Serializable
data class LxPlaylistInfo(
    val id: String = "",
    val name: String = "",
    val cover: String = "",
    val author: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0L,
    val description: String = "",
    /** 来源标识：wy / tx / kg / kw / mg */
    val source: String = ""
)

@Serializable
data class LxPlaylistSearchResult(
    val isEnd: Boolean = true,
    val list: List<LxPlaylistInfo> = emptyList(),
    val total: Int = 0
)

@Serializable
data class LxSourceInfo(
    val name: String = "",
    val type: String = "",
    val actions: List<String> = emptyList(),
    val qualitys: List<String> = emptyList()
)

/**
 * 单个 JS 音源脚本的头部简介（从 /*! ... */ 注释块解析）。
 */
data class LxScriptInfo(
    val fileName: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    val lastUpdate: String = "",
    val md5: String = ""
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
    val user: NeteaseCommentUser = NeteaseCommentUser(),
    /** 回复对象的信息（楼中楼子回复里"@"的主评论）；为空表示是主评论 */
    val beReplied: List<NeteaseCommentBeReplied> = emptyList(),
    /** 该主评论下的回复总数（用于显示"查看回复"入口） */
    val subReplyCount: Int = 0
)

/**
 * 评论回复对象（楼中楼）：主评论/被回复的那条评论的信息。
 */
data class NeteaseCommentBeReplied(
    val userId: Long = 0L,
    val nickname: String = "",
    val content: String = "",
    val beRepliedCommentId: Long = 0L
)

/**
 * 楼中楼回复分页结果：服务端（/resource/comment/floor/get）单次最多返回 10 条，
 * 需用 [nextTime] 作为游标继续翻页。
 */
data class NeteaseRepliesPage(
    val replies: List<NeteaseComment> = emptyList(),
    val hasMore: Boolean = false,
    val nextTime: Long = -1L
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
