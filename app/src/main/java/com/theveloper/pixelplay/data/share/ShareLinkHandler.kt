package com.theveloper.pixelplay.data.share

import com.theveloper.pixelplay.data.lx.LxJsEngine
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 处理分享链接的匹配和在线补全
 *
 * 匹配优先级（规范第七章）：
 * 1. eid + sc 精确匹配本地
 * 2. t + ar + al 三者匹配本地
 * 3. t + ar 宽松匹配本地
 * 4. 未匹配 → 通过 eid + sc 在线搜索补全
 */
@Singleton
class ShareLinkHandler @Inject constructor(
    private val musicRepository: MusicRepository,
    private val lxJsEngine: LxJsEngine
) {
    companion object {
        private const val TAG = "ShareLinkHandler"
    }

    suspend fun resolve(url: String): ShareResult {
        val metadata = ShareLinkCodec.decode(url)
            ?: return ShareResult.Error("Invalid share link")

        val allSongs = musicRepository.getAllSongsOnce()
        val matchedSongs = mutableListOf<Song>()
        val unresolvedItems = mutableListOf<ShareLinkCodec.ShareItem>()

        for (item in metadata.it) {
            // 先本地匹配
            val localMatch = matchLocal(item, allSongs)
            if (localMatch != null) {
                matchedSongs.add(localMatch)
            } else {
                unresolvedItems.add(item)
            }
        }

        // 本地未匹配的 → 在线补全
        val onlineImportResults = mutableListOf<OnlineImportResult>()
        for (item in unresolvedItems) {
            val result = tryImportOnline(item)
            onlineImportResults.add(result)
            if (result.song != null) {
                matchedSongs.add(result.song)
            }
        }

        val onlineSuccess = onlineImportResults.count { it.song != null }
        val onlineFailed = onlineImportResults.count { it.song == null }

        return ShareResult.Success(
            name = metadata.nm,
            type = metadata.tp,
            matchedSongs = matchedSongs,
            unmatchedCount = onlineFailed,
            totalCount = metadata.it.size,
            onlineImported = onlineSuccess,
            importDetails = onlineImportResults
        )
    }

    // ─── 本地匹配 ────────────────────────────────────────────────

    private fun matchLocal(item: ShareLinkCodec.ShareItem, allSongs: List<Song>): Song? {
        // 优先级1：eid + sc
        if (item.eid.isNotBlank()) {
            val byId = allSongs.find { matchByExternalId(it, item.sc, item.eid) }
            if (byId != null) return byId
        }
        // 优先级2：t + ar + al
        val byFull = allSongs.find {
            it.title.equals(item.t, true) &&
            it.displayArtist.equals(item.ar, true) &&
            it.album.equals(item.al, true)
        }
        if (byFull != null) return byFull
        // 优先级3：t + ar
        return allSongs.find {
            it.title.equals(item.t, true) && it.displayArtist.equals(item.ar, true)
        }
    }

    private fun matchByExternalId(song: Song, source: String, eid: String): Boolean {
        return when (source) {
            "netease" -> song.neteaseId?.toString() == eid
            "qqMusic" -> song.qqMusicMid == eid
            "navidrome" -> song.navidromeId == eid
            "jellyfin" -> song.jellyfinId == eid
            else -> false
        }
    }

    // ─── 在线补全 ────────────────────────────────────────────────

    private suspend fun tryImportOnline(item: ShareLinkCodec.ShareItem): OnlineImportResult {
        return try {
            when (item.sc) {
                "netease" -> importFromNetease(item)
                "qqMusic" -> importFromQQMusic(item)
                "navidrome", "jellyfin" -> {
                    // 这些需要服务器连接，仅本地匹配
                    OnlineImportResult(item, null, "需要连接到 ${item.sc} 服务器")
                }
                "cloudLx" -> importBySearch(item, "wy")
                "local" -> OnlineImportResult(item, null, "本地歌曲无法跨设备导入")
                else -> importBySearch(item, null)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Online import failed for ${item.t}")
            OnlineImportResult(item, null, e.message ?: "导入失败")
        }
    }

    /** 网易云：通过 eid 直接获取歌曲详情并保存 */
    private suspend fun importFromNetease(item: ShareLinkCodec.ShareItem): OnlineImportResult {
        val eid = item.eid.toLongOrNull()
        if (eid == null) {
            return importBySearch(item, "wy")
        }

        // 先检查是否已保存
        val existingId = musicRepository.getCloudSongIdByTitleArtist(item.t, item.ar)
        if (existingId != null) {
            val existing = musicRepository.getSong(existingId).first()
            if (existing != null) return OnlineImportResult(item, existing)
        }

        // 通过 ID 直接保存（播放时由 neteaseStreamProxy 解析 URL）
        val songId = musicRepository.saveCloudSongBasic(
            title = item.t,
            artist = item.ar,
            album = item.al,
            albumArt = item.cv.ifBlank { null },
            duration = item.dr * 1000,
            neteaseIdRaw = eid
        )

        val saved = musicRepository.getSong(songId.toString()).first()
        return if (saved != null) {
            OnlineImportResult(item, saved)
        } else {
            OnlineImportResult(item, null, "保存失败")
        }
    }

    /** QQ 音乐：通过 eid (songmid) 搜索并保存 */
    private suspend fun importFromQQMusic(item: ShareLinkCodec.ShareItem): OnlineImportResult {
        // 用标题+歌手搜索 QQ 音乐，然后匹配 songmid
        val searchResults = lxJsEngine.search("${item.t} ${item.ar}", "tx", 1, 10)
        val matched = searchResults.list.find { it.songmid == item.eid }
            ?: searchResults.list.find { it.name.equals(item.t, true) && it.singer.contains(item.ar, true) }
            ?: searchResults.list.find { it.name.equals(item.t, true) }

        if (matched != null) {
            val songId = musicRepository.saveCloudSong(matched)
            val saved = musicRepository.getSong(songId.toString()).first()
            return OnlineImportResult(item, saved)
        }

        // fallback：直接用标题搜索
        return importBySearch(item, "tx")
    }

    /** 通用：通过关键词搜索并保存第一首匹配的 */
    private suspend fun importBySearch(item: ShareLinkCodec.ShareItem, source: String?): OnlineImportResult {
        val query = "${item.t} ${item.ar}"
        val src = source ?: "wy"
        val results = lxJsEngine.search(query, src, 1, 10)

        // 按标题+歌手匹配最佳结果
        val best = results.list.find { r ->
            r.name.equals(item.t, true) && r.singer.contains(item.ar, true)
        } ?: results.list.firstOrNull()

        if (best != null) {
            val songId = musicRepository.saveCloudSong(best)
            val saved = musicRepository.getSong(songId.toString()).first()
            return OnlineImportResult(item, saved)
        }

        return OnlineImportResult(item, null, "未找到匹配的在线歌曲")
    }
}

data class OnlineImportResult(
    val item: ShareLinkCodec.ShareItem,
    val song: Song?,
    val error: String? = null
)

sealed class ShareResult {
    data class Success(
        val name: String,
        val type: String,
        val matchedSongs: List<Song>,
        val unmatchedCount: Int,
        val totalCount: Int,
        val onlineImported: Int = 0,
        val importDetails: List<OnlineImportResult> = emptyList()
    ) : ShareResult()

    data class Error(val message: String) : ShareResult()
}
