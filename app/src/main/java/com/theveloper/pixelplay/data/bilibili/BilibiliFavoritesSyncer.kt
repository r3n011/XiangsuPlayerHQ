package com.theveloper.pixelplay.data.bilibili

import android.content.Context
import android.content.SharedPreferences
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.serializeArtistRefs
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * B 站收藏同步器：把用户全部收藏夹（我创建的 + 我收藏的收藏夹）的全部收藏视频
 * 同步到统一媒体库，并按 B 站收藏夹【每个收藏夹一个播放列表】（bilibili_fav_{folderId}）。
 *
 * - [sync] 带 1 小时节流（进入媒体库自动同步用）
 * - [syncNow] 强制全量同步（手动同步按钮 / SyncWorker 周期任务用）
 *
 * 与网易云 autoSyncOnLibraryEntry 同构：登录检查 + 节流 + 全量拉取 + 写库。
 */
@Singleton
class BilibiliFavoritesSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bilibiliRepository: BilibiliRepository,
    private val bilibiliSearchApi: BilibiliSearchApi,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository
) {

    companion object {
        private const val TAG = "BilibiliSyncer"

        /** 进入媒体库自动同步节流：1 小时（对齐网易云 AUTO_SYNC_INTERVAL_MS） */
        private const val AUTO_SYNC_INTERVAL_MS = 60 * 60 * 1000L
        private const val KEY_LAST_AUTO_SYNC = "bilibili_last_auto_sync"

        private const val BILIBILI_SONG_ID_OFFSET = 6_000_000_000_000L
        private const val BILIBILI_ALBUM_ID_OFFSET = 7_000_000_000_000L
        private const val BILIBILI_ARTIST_ID_OFFSET = 8_000_000_000_000L
        private const val BILIBILI_PARENT_DIRECTORY = "/Cloud/Bilibili"
        private const val BILIBILI_GENRE = "Bilibili"

        /** 旧版单一「B站收藏」列表 id（弃用，仅在清理时删除） */
        private const val LEGACY_BILIBILI_PLAYLIST_ID = "bilibili_favorites"

        /** 按收藏夹生成的播放列表 id 前缀 */
        private const val FOLDER_PLAYLIST_PREFIX = "bilibili_fav_"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bilibili_sync_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** 已同步的 B 站收藏数（供 UI 展示，进程内缓存） */
    @Volatile
    var lastSyncedCount: Int = 0
        private set

    /**
     * 节流同步：距上次自动同步不足 1 小时则跳过。返回 true 表示本次实际执行了同步。
     */
    suspend fun sync(): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        val lastSync = prefs.getLong(KEY_LAST_AUTO_SYNC, 0L)
        if (now - lastSync < AUTO_SYNC_INTERVAL_MS) {
            Timber.d("$TAG: auto sync throttled (last ${(now - lastSync) / 1000}s ago)")
            return@withLock false
        }
        val executed = doSync()
        if (executed) {
            prefs.edit().putLong(KEY_LAST_AUTO_SYNC, System.currentTimeMillis()).apply()
        }
        executed
    }

    /** 强制全量同步（手动按钮 / 周期任务），不受节流限制。 */
    suspend fun syncNow(): Boolean = mutex.withLock { doSync() }

    private suspend fun doSync(): Boolean {
        Timber.i("$TAG: Syncing Bilibili favorites (per-folder playlists)...")
        try {
            if (!bilibiliRepository.isLoggedIn || bilibiliRepository.userId <= 0L) {
                val existingBilibiliIds = musicDao.getAllBilibiliSongIds()
                if (existingBilibiliIds.isNotEmpty()) {
                    musicDao.clearAllBilibiliSongs()
                    Timber.d("$TAG: Cleared Bilibili songs (not logged in).")
                }
                clearAllBilibiliPlaylists()
                lastSyncedCount = 0
                return true
            }

            val cookie = bilibiliRepository.getCookieHeader()
            val csrf = bilibiliRepository.getCsrf() ?: ""
            val uid = bilibiliRepository.userId

            // 1) 拉取全部收藏夹（我创建的 + 我收藏的收藏夹）
            val folders = bilibiliSearchApi.getFavoriteFolders(uid, csrf, cookie = cookie)
            Timber.i("$TAG: Bilibili folders fetched: ${folders.size}")

            // 2) 并行拉取每个收藏夹的全部视频（has_more 兼容修复后应完整）
            val folderVideos = coroutineScope {
                folders.filter { it.id > 0L }.map { folder ->
                    async(Dispatchers.IO) {
                        folder to fetchAllResources(folder.id, csrf, cookie)
                    }
                }.awaitAll()
            }.filter { (_, videos) -> videos.isNotEmpty() }

            val allVideos = LinkedHashMap<String, BilibiliFavoriteVideo>()
            folderVideos.forEach { (_, videos) ->
                videos.forEach { video -> allVideos[video.bvid] = video }
            }
            Timber.i("$TAG: Bilibili favorites fetched: ${allVideos.size} videos across ${folderVideos.size} folders")

            val existingUnifiedBilibiliIds = musicDao.getAllBilibiliSongIds()
            if (allVideos.isEmpty()) {
                if (existingUnifiedBilibiliIds.isNotEmpty()) {
                    musicDao.clearAllBilibiliSongs()
                    Timber.d("$TAG: No Bilibili favorites, cleared existing.")
                }
                clearAllBilibiliPlaylists()
                lastSyncedCount = 0
                return true
            }

            val songsToInsert = ArrayList<SongEntity>(allVideos.size)
            val artistsToInsert = LinkedHashMap<Long, ArtistEntity>()
            val albumsToInsert = LinkedHashMap<Long, AlbumEntity>()
            val crossRefsToInsert = mutableListOf<SongArtistCrossRef>()
            val albumId = toUnifiedBilibiliAlbumId()

            allVideos.values.forEach { video ->
                if (video.bvid.isBlank()) return@forEach
                val songId = toUnifiedBilibiliSongId(video.bvid)
                val artistName = video.upName.ifBlank { "Bilibili" }
                val artistId = toUnifiedBilibiliArtistId(artistName)
                artistsToInsert.putIfAbsent(
                    artistId,
                    ArtistEntity(id = artistId, name = artistName, trackCount = 0, imageUrl = null)
                )
                crossRefsToInsert.add(SongArtistCrossRef(songId = songId, artistId = artistId, isPrimary = true))
                albumsToInsert.putIfAbsent(
                    albumId,
                    AlbumEntity(
                        id = albumId,
                        title = "Bilibili",
                        artistName = "Bilibili",
                        artistId = artistId,
                        songCount = 0,
                        dateAdded = System.currentTimeMillis(),
                        year = 0,
                        albumArtUriString = null
                    )
                )
                songsToInsert.add(
                    SongEntity(
                        id = songId,
                        title = video.title.ifBlank { "B 站视频" },
                        artistName = artistName,
                        artistId = artistId,
                        albumArtist = null,
                        albumName = "Bilibili",
                        albumId = albumId,
                        contentUriString = "bilibili://${video.bvid}/${video.cid}/${video.aid}",
                        albumArtUriString = video.cover.takeIf { it.isNotBlank() },
                        duration = video.duration,
                        genre = BILIBILI_GENRE,
                        filePath = "",
                        parentDirectoryPath = BILIBILI_PARENT_DIRECTORY,
                        isFavorite = false,
                        lyrics = null,
                        trackNumber = 0,
                        year = 0,
                        dateAdded = System.currentTimeMillis(),
                        mimeType = null,
                        bitrate = null,
                        sampleRate = null,
                        telegramChatId = null,
                        telegramFileId = null,
                        artistsJson = serializeArtistRefs(
                            listOf(ArtistRef(id = artistId, name = artistName, isPrimary = true))
                        ),
                        sourceType = SourceType.BILIBILI
                    )
                )
            }

            val albumCounts = songsToInsert.groupingBy { it.albumId }.eachCount()
            val finalAlbums = albumsToInsert.values.map { album ->
                album.copy(songCount = albumCounts[album.id] ?: 0)
            }
            val currentUnifiedSongIds = songsToInsert.map { it.id }.toSet()
            val deletedUnifiedSongIds = existingUnifiedBilibiliIds.filter { it !in currentUnifiedSongIds }

            musicDao.incrementalSyncMusicData(
                songs = songsToInsert,
                albums = finalAlbums,
                artists = artistsToInsert.values.toList(),
                crossRefs = crossRefsToInsert,
                deletedSongIds = deletedUnifiedSongIds
            )
            Timber.i("$TAG: Synced ${songsToInsert.size} Bilibili songs with Unified Metadata.")

            // 2) 每个收藏夹同步为一个播放列表（bilibili_fav_{folderId}），并清理失效列表
            val remoteFolderIds = folders.filter { it.id > 0L }.map { it.id }.toSet()
            syncFolderPlaylists(folderVideos, remoteFolderIds)

            lastSyncedCount = songsToInsert.size
            return true
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to sync Bilibili data")
            return false
        }
    }

    /** 拉取某个收藏夹的全部视频（用 hasMore 判断分页，防止只拉第一页丢数据） */
    private suspend fun fetchAllResources(mediaId: Long, csrf: String, cookie: String): List<BilibiliFavoriteVideo> {
        val out = mutableListOf<BilibiliFavoriteVideo>()
        var pn = 1
        while (true) {
            val page = bilibiliSearchApi.getFavoriteResourcesPage(mediaId, pn = pn, csrf = csrf, cookie = cookie)
            if (page.videos.isEmpty()) break
            out.addAll(page.videos)
            if (!page.hasMore) break
            pn++
        }
        return out
    }

    /** 按收藏夹生成/更新播放列表；删除远端已不存在的收藏夹列表和旧版单一「B站收藏」列表 */
    private suspend fun syncFolderPlaylists(
        folderVideos: List<Pair<BilibiliFavoriteFolder, List<BilibiliFavoriteVideo>>>,
        remoteFolderIds: Set<Long>
    ) {
        try {
            val playlists = withContext(Dispatchers.IO) {
                playlistPreferencesRepository.userPlaylistsFlow.first()
            }
            val existingById = playlists.associateBy { it.id }

            folderVideos.forEach { (folder, videos) ->
                val playlistId = FOLDER_PLAYLIST_PREFIX + folder.id
                val songIds = videos.mapNotNull { video ->
                    if (video.bvid.isBlank()) null
                    else toUnifiedBilibiliSongId(video.bvid).toString()
                }.distinct()
                val existing = existingById[playlistId]
                if (existing != null) {
                    playlistPreferencesRepository.updatePlaylist(
                        existing.copy(
                            name = folder.title.ifBlank { "B 站收藏" },
                            songIds = songIds,
                            lastModified = System.currentTimeMillis(),
                            source = "BILIBILI"
                        )
                    )
                } else {
                    playlistPreferencesRepository.createPlaylist(
                        name = folder.title.ifBlank { "B 站收藏" },
                        songIds = songIds,
                        customId = playlistId,
                        source = "BILIBILI"
                    )
                }
            }

            // 清理：旧版单一列表 + 远端已删除的收藏夹列表
            playlists.forEach { pl ->
                if (pl.id == LEGACY_BILIBILI_PLAYLIST_ID) {
                    playlistPreferencesRepository.deletePlaylist(pl.id)
                } else if (pl.id.startsWith(FOLDER_PLAYLIST_PREFIX)) {
                    val fid = pl.id.removePrefix(FOLDER_PLAYLIST_PREFIX).toLongOrNull()
                    if (fid == null || fid !in remoteFolderIds) {
                        playlistPreferencesRepository.deletePlaylist(pl.id)
                    }
                }
            }
            Timber.i("$TAG: Synced ${folderVideos.size} per-folder Bilibili playlists.")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to sync per-folder Bilibili playlists")
        }
    }

    private suspend fun clearAllBilibiliPlaylists() {
        try {
            val playlists = withContext(Dispatchers.IO) {
                playlistPreferencesRepository.userPlaylistsFlow.first()
            }
            playlists.filter { it.id == LEGACY_BILIBILI_PLAYLIST_ID || it.id.startsWith(FOLDER_PLAYLIST_PREFIX) }
                .forEach { pl -> playlistPreferencesRepository.deletePlaylist(pl.id) }
            Timber.i("$TAG: Cleared all Bilibili playlists.")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to clear Bilibili playlists")
        }
    }

    private fun toUnifiedBilibiliSongId(bvid: String): Long {
        return -(BILIBILI_SONG_ID_OFFSET + bvid.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedBilibiliAlbumId(): Long = -BILIBILI_ALBUM_ID_OFFSET

    private fun toUnifiedBilibiliArtistId(artistName: String): Long {
        return -(BILIBILI_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }
}
