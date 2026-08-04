package com.theveloper.pixelplay.data.netease

import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.lx.LxSearchApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网易云在线艺术家头像解析器。
 *
 * 媒体库（统一库）中的网易云歌手行在同步时 imageUrl 恒为 null，
 * 导致「在线艺术家」列表项不显示头像。本解析器按歌手名调用网易云搜索接口
 * 找到头像 URL，并回写数据库缓存（后续同步的 upsert 会保留已有 imageUrl）。
 */
@Singleton
class NeteaseArtistAvatarResolver @Inject constructor(
    private val lxSearchApi: LxSearchApi,
    private val musicDao: MusicDao
) {

    companion object {
        /** 与 SyncWorker.toUnifiedNeteaseArtistId 保持一致 */
        private const val NETEASE_ARTIST_ID_OFFSET = 5_000_000_000_000L
        private const val NETEASE_ARTIST_ID_MIN = NETEASE_ARTIST_ID_OFFSET + Int.MAX_VALUE

        /** 是否为网易云统一库中的在线艺术家（id 落在网易云负 id 区间） */
        fun isNeteaseCloudArtistId(id: Long): Boolean =
            id < 0L && id > -NETEASE_ARTIST_ID_MIN && id <= -NETEASE_ARTIST_ID_OFFSET
    }

    // 进程内缓存：normalized name -> avatar url（null 表示解析失败，避免重复请求）
    private val avatarCache = HashMap<String, String?>()
    private val cacheLock = Any()

    suspend fun resolveAvatarUrl(artistId: Long, artistName: String): String? {
        if (artistName.isBlank()) return null
        val key = artistName.trim().lowercase()

        synchronized(cacheLock) {
            if (avatarCache.containsKey(key)) return avatarCache[key]
        }

        val url = try {
            val result = lxSearchApi.searchArtists(key, page = 1, pageSize = 5)
            result.list.firstOrNull { it.picUrl.isNotBlank() }
                ?.picUrl
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(::normalizeUrl)
        } catch (t: Throwable) {
            Timber.w(t, "解析网易云歌手头像失败: $artistName")
            null
        }

        synchronized(cacheLock) {
            avatarCache[key] = url
        }

        if (!url.isNullOrBlank() && artistId != 0L) {
            runCatching { musicDao.updateArtistImageUrl(artistId, url) }
                .onFailure { Timber.w(it, "保存网易云歌手头像失败 id=$artistId") }
        }
        return url
    }

    private fun normalizeUrl(raw: String): String = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "https://music.163.com$raw"
        else -> raw
    }
}
