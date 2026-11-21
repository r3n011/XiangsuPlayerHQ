package com.theveloper.pixelplay.data.netease.info

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.NcmJson
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网易云歌曲扩展信息聚合（歌曲信息页 P0/P1）。
 *
 * 数据来源全部为 NcmApi 公开接口，单曲一次聚合：
 *  - [NcmApi.songDetail]      主信息（别名/译名/发行时间/热度/MV/版权/音质上限/专辑/歌手）
 *  - `full.commentMusic`      评论总数
 *  - `full.songWikiSummary`   歌曲百科摘要
 *  - `full.album`             专辑公司 / 发行日期 / 曲目数
 *  - `full.artistDetail`      歌手头像 / 简介
 *  - `full.simiSong`          相似歌曲（最多 5 首）
 *
 * 设计要点：
 *  - 打开信息页才请求，关闭即取消（协程作用域由调用方控制）；
 *  - 单请求 4s、整次聚合 6s 预算，任一子请求失败只降级对应区块；
 *  - LruCache 10 分钟 + 同曲并发去重，重复打开零请求。
 */
@Singleton
class NeteaseSongInfoRepository @Inject constructor() {

    companion object {
        private const val TAG = "NeteaseSongInfo"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private const val REQUEST_TIMEOUT_MS = 4_000L
        private const val TOTAL_TIMEOUT_MS = 6_000L
        private const val SIMILAR_LIMIT = 5
    }

    private data class CacheEntry(val data: NeteaseSongExtra, val savedAt: Long)

    private val cache = object : LruCache<Long, CacheEntry>(32) {}

    /** 同一首歌的并发加载串行化（重复打开信息页时只发一轮请求） */
    private val loadLock = Mutex()

    suspend fun load(songId: Long): NeteaseSongExtra? = withContext(Dispatchers.IO) {
        if (songId <= 0L) return@withContext null
        synchronized(cache) { cache.get(songId) }?.takeIf {
            System.currentTimeMillis() - it.savedAt < CACHE_TTL_MS
        }?.let { return@withContext it.data }

        loadLock.withLock {
            // 等锁期间可能已被其它协程填充
            synchronized(cache) { cache.get(songId) }?.takeIf {
                System.currentTimeMillis() - it.savedAt < CACHE_TTL_MS
            }?.let { return@withContext it.data }

            val data = withTimeoutOrNull(TOTAL_TIMEOUT_MS) { fetch(songId) }
            if (data != null) {
                synchronized(cache) {
                    cache.put(songId, CacheEntry(data, System.currentTimeMillis()))
                }
            }
            data
        }
    }

    private suspend fun fetch(songId: Long): NeteaseSongExtra? = coroutineScope {
        val detailDeferred = async { fetchDetail(songId) }
        val commentDeferred = async { fetchCommentCount(songId) }
        val wikiDeferred = async { fetchWikiSummary(songId) }
        val detail = detailDeferred.await() ?: return@coroutineScope null

        val albumDeferred = async { detail.album?.id?.let { fetchAlbumExtra(it) } }
        val artistDeferred = async { detail.artist?.id?.let { fetchArtistExtra(it) } }
        val similarDeferred = async { fetchSimilarSongs(songId) }

        detail.copy(
            commentCount = commentDeferred.await(),
            wikiSummary = wikiDeferred.await(),
            album = albumDeferred.await() ?: detail.album,
            artist = artistDeferred.await() ?: detail.artist,
            similarSongs = similarDeferred.await(),
        )
    }

    // ── 主信息 ────────────────────────────────────────────────────────────

    private suspend fun fetchDetail(songId: Long): NeteaseSongExtra? =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                val map = NcmApi.songDetail(listOf(songId.toString())).getOrThrow()
                val root = JSONObject(NcmJson.toJsonString(map))
                val song = root.optJSONArray("songs")?.optJSONObject(0) ?: return@runCatching null
                val privilege = song.optJSONObject("privilege")
                    ?: root.optJSONArray("privileges")?.optJSONObject(0)

                val albumObj = song.optJSONObject("al")
                val artistsObj = song.optJSONArray("ar")
                val firstArtist = artistsObj?.optJSONObject(0)

                val maxBitrate = privilege?.optInt("maxbr", 0)?.takeIf { it > 0 }
                val st = privilege?.optInt("st", 0) ?: 0

                NeteaseSongExtra(
                    songId = songId,
                    alias = song.optJSONArray("alia").toStringList(),
                    transNames = song.optJSONArray("tns").toStringList(),
                    publishTimeMs = song.optLong("publishTime", 0L).takeIf { it > 0L },
                    popularity = song.optInt("popularity", -1).takeIf { it >= 0 },
                    commentCount = null,
                    mvId = song.optLong("mv", 0L).takeIf { it > 0L },
                    fee = song.optInt("fee", -1).takeIf { it >= 0 },
                    maxBitrate = maxBitrate,
                    maxQuality = qualityLabelFor(maxBitrate),
                    playable = st >= 0,
                    album = albumObj?.let {
                        NeteaseAlbumExtra(
                            id = it.optLong("id", 0L),
                            name = it.optString("name"),
                            coverUrl = it.optString("picUrl").takeIf { pic -> pic.isNotBlank() },
                        )
                    }?.takeIf { it.id > 0L },
                    artist = firstArtist?.let {
                        NeteaseArtistExtra(
                            id = it.optLong("id", 0L),
                            name = it.optString("name"),
                            avatarUrl = (it.optString("img1v1Url").ifBlank { it.optString("picUrl") })
                                .takeIf { url -> url.isNotBlank() },
                        )
                    }?.takeIf { it.id > 0L && it.name.isNotBlank() },
                )
            }.onFailure { Timber.w(it, "$TAG: fetchDetail failed for $songId") }.getOrNull()
        }

    // ── 评论数 / 百科 / 专辑 / 歌手 / 相似歌曲 ─────────────────────────────

    private suspend fun fetchCommentCount(songId: Long): Int? =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                val map = NcmApi.full.commentMusic(songId.toString(), limit = 0).getOrThrow()
                JSONObject(NcmJson.toJsonString(map)).optInt("total", -1).takeIf { it >= 0 }
            }.getOrNull()
        }

    private suspend fun fetchWikiSummary(songId: Long): String? =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                val map = NcmApi.full.songWikiSummary(songId.toString()).getOrThrow()
                val root: Any = JSONObject(NcmJson.toJsonString(map))
                (findString(root, "summary") ?: findString(root, "content"))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    private suspend fun fetchAlbumExtra(albumId: Long): NeteaseAlbumExtra? =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                val map = NcmApi.full.album(albumId.toString()).getOrThrow()
                val root = JSONObject(NcmJson.toJsonString(map))
                val album = root.optJSONObject("album")
                    ?: root.optJSONObject("data")?.optJSONObject("album")
                    ?: return@runCatching null
                NeteaseAlbumExtra(
                    id = album.optLong("id", albumId),
                    name = album.optString("name"),
                    coverUrl = album.optString("picUrl").takeIf { it.isNotBlank() },
                    company = album.optString("company").takeIf { it.isNotBlank() },
                    publishTimeMs = album.optLong("publishTime", 0L).takeIf { it > 0L },
                    trackCount = album.optInt("size", -1).takeIf { it > 0 },
                )
            }.getOrNull()
        }

    private suspend fun fetchArtistExtra(artistId: Long): NeteaseArtistExtra? =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                val map = NcmApi.full.artistDetail(artistId.toString()).getOrThrow()
                val root = JSONObject(NcmJson.toJsonString(map))
                val artist = root.optJSONObject("data")?.optJSONObject("artist")
                    ?: root.optJSONObject("artist")
                    ?: return@runCatching null
                val brief = artist.optString("briefDesc").takeIf { it.isNotBlank() }
                    ?: runCatching {
                        val descMap = NcmApi.full.artistDesc(artistId.toString()).getOrThrow()
                        JSONObject(NcmJson.toJsonString(descMap)).optString("briefDesc")
                            .takeIf { it.isNotBlank() }
                    }.getOrNull()
                NeteaseArtistExtra(
                    id = artist.optLong("id", artistId),
                    name = artist.optString("name"),
                    avatarUrl = artist.optString("avatar").takeIf { it.isNotBlank() },
                    bio = brief,
                    fansCount = artist.optInt("fansCount", -1).takeIf { it >= 0 },
                ).takeIf { it.name.isNotBlank() }
            }.getOrNull()
        }

    private suspend fun fetchSimilarSongs(songId: Long): List<NeteaseSimilarSong> =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                val map = NcmApi.full.simiSong(songId.toString(), limit = SIMILAR_LIMIT).getOrThrow()
                val root = JSONObject(NcmJson.toJsonString(map))
                val songs = root.optJSONArray("songs") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until songs.length()) {
                        val obj = songs.optJSONObject(i) ?: continue
                        val id = obj.optLong("id", 0L)
                        val name = obj.optString("name")
                        if (id <= 0L || name.isBlank()) continue
                        val artist = obj.optJSONArray("ar")?.let { arr ->
                            (0 until arr.length()).mapNotNull { idx ->
                                arr.optJSONObject(idx)?.optString("name")?.takeIf { it.isNotBlank() }
                            }.joinToString("/")
                        }.orEmpty()
                        add(
                            NeteaseSimilarSong(
                                id = id,
                                name = name,
                                artist = artist,
                                albumName = obj.optJSONObject("al")?.optString("name")
                                    ?.takeIf { it.isNotBlank() },
                                durationMs = obj.optLong("dt", 0L).takeIf { it > 0L },
                            )
                        )
                        if (size >= SIMILAR_LIMIT) break
                    }
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()

    // ── 工具 ──────────────────────────────────────────────────────────────

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    /** maxbr → 展示用音质标签（与现有音质档位对齐） */
    private fun qualityLabelFor(maxBitrate: Int?): String? = when {
        maxBitrate == null -> null
        maxBitrate >= 900_000 -> "flac24bit"
        maxBitrate >= 320_000 -> "flac"
        maxBitrate >= 192_000 -> "320k"
        else -> "128k"
    }

    /** 在任意层级的 JSON 中找第一个非空字符串字段（网易云百科/简介结构经常变） */
    private fun findString(node: Any?, key: String): String? = when (node) {
        is JSONObject -> {
            node.optString(key).takeIf { it.isNotBlank() }
                ?: node.keys().asSequence()
                    .map { k -> findString(node.opt(k), key) }
                    .firstOrNull { it != null }
        }
        is JSONArray -> (0 until node.length())
            .asSequence()
            .map { i -> findString(node.opt(i), key) }
            .firstOrNull { it != null }
        else -> null
    }
}
