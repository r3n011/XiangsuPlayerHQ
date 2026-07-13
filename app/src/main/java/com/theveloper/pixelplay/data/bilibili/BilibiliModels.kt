package com.theveloper.pixelplay.data.bilibili

import kotlinx.serialization.Serializable

@Serializable
data class BilibiliSongInfo(
    val id: String = "",
    val bvid: String = "",
    val aid: Long = 0L,
    val cid: Long = 0L,
    val name: String = "",
    val singer: String = "",
    val albumName: String = "",
    val duration: Long = 0L,
    val pic: String = "",
    val playUrl: String = ""
)

@Serializable
data class BilibiliSearchResult(
    val isEnd: Boolean = true,
    val list: List<BilibiliSongInfo> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

@Serializable
data class BilibiliVideoDetail(
    val aid: Long = 0L,
    val bvid: String = "",
    val title: String = "",
    val duration: Long = 0L,
    val pic: String = "",
    val cid: Long = 0L
)