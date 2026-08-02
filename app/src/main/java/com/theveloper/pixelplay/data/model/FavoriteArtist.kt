package com.theveloper.pixelplay.data.model

import kotlinx.serialization.Serializable

/**
 * 收藏的歌手（网易云）
 *
 * @param id 网易云 artistId
 * @param name 歌手名
 * @param avatar 头像 URL
 * @param alias 别名（如 "Jay Chou"）
 * @param addedAt 收藏时间戳（用于排序）
 */
@Serializable
data class FavoriteArtist(
    val id: Long,
    val name: String,
    val avatar: String = "",
    val alias: String = "",
    val addedAt: Long = System.currentTimeMillis()
)
