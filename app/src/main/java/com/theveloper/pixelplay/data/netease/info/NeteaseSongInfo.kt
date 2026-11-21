package com.theveloper.pixelplay.data.netease.info

/**
 * 网易云歌曲的扩展信息（歌曲信息页用）。
 * 独立于本地文件的 Song 元数据：在线歌曲没有文件大小 / 位深，但有别名、发行时间、
 * 专辑公司、歌手简介、歌曲百科、相似歌曲、评论数等云端信息。
 */
data class NeteaseSongExtra(
    val songId: Long,
    /** 歌曲别名（alia），例如 “Live 版”“伴奏” */
    val alias: List<String> = emptyList(),
    /** 歌词译名（tns） */
    val transNames: List<String> = emptyList(),
    /** 发行时间（毫秒时间戳） */
    val publishTimeMs: Long? = null,
    /** 热度（0~100） */
    val popularity: Int? = null,
    /** 评论总数 */
    val commentCount: Int? = null,
    /** MV id（无 MV 为 null） */
    val mvId: Long? = null,
    /** 付费类型：0 免费 / 1 VIP / 8 低音质免费 */
    val fee: Int? = null,
    /** privilege 中的最大码率（bps） */
    val maxBitrate: Int? = null,
    /** 最高可播放音质（flac24bit / flac / 320k / 128k） */
    val maxQuality: String? = null,
    /** 是否有播放权限（false = 无版权 / 需购买） */
    val playable: Boolean = true,
    val album: NeteaseAlbumExtra? = null,
    val artist: NeteaseArtistExtra? = null,
    /** 歌曲百科摘要 */
    val wikiSummary: String? = null,
    /** 相似歌曲（最多 5 首） */
    val similarSongs: List<NeteaseSimilarSong> = emptyList(),
)

data class NeteaseAlbumExtra(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val company: String? = null,
    val publishTimeMs: Long? = null,
    val trackCount: Int? = null,
)

data class NeteaseArtistExtra(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null,
    /** 歌手简介（briefDesc 或 introduction 摘要） */
    val bio: String? = null,
    val fansCount: Int? = null,
)

data class NeteaseSimilarSong(
    val id: Long,
    val name: String,
    val artist: String,
    val albumName: String? = null,
    val durationMs: Long? = null,
)
