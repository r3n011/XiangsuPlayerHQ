package com.theveloper.pixelplay.data.kugou

import android.content.Context
import android.content.SharedPreferences
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 酷狗歌单同步器：把登录账号的全部酷狗歌单（含「我喜欢」）同步进统一媒体库，
 * 每个酷狗歌单对应一个本地播放列表（`kugou_pl_{listId}`，source = "KUGOU"）。
 *
 * 歌曲走 [MusicRepository.saveCloudSong] 落库（source = "kg"，contentUri 为 `cloud://lx/...`），
 * 播放时由现成的内置酷狗音源解析直链（`BuiltInSourceSearchApi.resolvePlayUrl`），
 * 因此这里不预先取 URL（会过期，且会员/无损要靠登录态）。
 *
 * - [sync] 带 1 小时节流（进入媒体库自动同步用）
 * - [syncNow] 强制全量同步（账号页「立即同步」按钮用）
 *
 * 与 [com.theveloper.pixelplay.data.bilibili.BilibiliFavoritesSyncer] 同构：
 * 登录检查 + 节流 + 全量拉取 + 写库 + 清理远端已删除的歌单。
 */
@Singleton
class KugouPlaylistSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kugouRepository: KugouRepository,
    private val musicRepository: MusicRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
) {

    companion object {
        private const val TAG = "KugouSyncer"

        /** 进入媒体库自动同步节流：1 小时（对齐网易云/B 站） */
        private const val AUTO_SYNC_INTERVAL_MS = 60 * 60 * 1000L
        private const val KEY_LAST_AUTO_SYNC = "kugou_last_auto_sync"

        /** 本地播放列表 id 前缀（按酷狗歌单 listid 生成） */
        const val PLAYLIST_PREFIX = "kugou_pl_"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kugou_sync_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 最近一次成功同步的歌单数 / 歌曲数（账号页展示用） */
    private val _syncedPlaylistCount = MutableStateFlow(0)
    val syncedPlaylistCount: StateFlow<Int> = _syncedPlaylistCount.asStateFlow()

    private val _syncedSongCount = MutableStateFlow(0)
    val syncedSongCount: StateFlow<Int> = _syncedSongCount.asStateFlow()

    init {
        // 冷启动时先按本地已有的酷狗歌单回填数量，避免账号页在首次同步前显示「还没有同步歌单」
        scope.launch {
            runCatching {
                val count = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .count { it.id.startsWith(PLAYLIST_PREFIX) }
                _syncedPlaylistCount.value = count
            }
        }
    }

    /** 节流同步：距上次成功同步不足 1 小时则跳过。返回 true 表示本次实际执行了同步。 */
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

    /** 强制全量同步（账号页「立即同步」），不受节流限制。 */
    suspend fun syncNow(): Boolean = mutex.withLock {
        val executed = doSync()
        if (executed) {
            prefs.edit().putLong(KEY_LAST_AUTO_SYNC, System.currentTimeMillis()).apply()
        }
        executed
    }

    private suspend fun doSync(): Boolean {
        Timber.i("$TAG: Syncing Kugou playlists...")
        return try {
            if (!kugouRepository.isLoggedIn) {
                clearAllKugouPlaylists()
                _syncedPlaylistCount.value = 0
                _syncedSongCount.value = 0
                return true
            }

            val playlists = kugouRepository.fetchAllPlaylists()
                .getOrElse { throw it }
            if (playlists.isEmpty()) {
                clearAllKugouPlaylists()
                _syncedPlaylistCount.value = 0
                _syncedSongCount.value = 0
                return true
            }

            var totalSongs = 0
            val remotePlaylistIds = HashSet<String>(playlists.size)
            playlists.forEach { playlist ->
                remotePlaylistIds.add(playlist.listId)
                val songs = kugouRepository.fetchAllPlaylistSongs(playlist.listId)
                    .getOrElse {
                        Timber.w(it, "$TAG: failed to fetch songs for ${playlist.listId}")
                        return@forEach
                    }
                val songIds = songs.mapNotNull { brief ->
                    runCatching {
                        musicRepository.saveCloudSong(brief.toLxSongInfo(playlist.coverUrl))
                    }.getOrNull()?.takeIf { it > 0 }?.toString()
                }.distinct()
                totalSongs += songIds.size
                upsertPlaylist(
                    listId = playlist.listId,
                    name = playlist.name,
                    songIds = songIds,
                    coverUrl = playlist.coverUrl,
                )
            }

            removeStalePlaylists(remotePlaylistIds)
            _syncedPlaylistCount.value = remotePlaylistIds.size
            _syncedSongCount.value = totalSongs
            Timber.i("$TAG: Synced ${remotePlaylistIds.size} playlists / $totalSongs songs")
            true
        } catch (t: Throwable) {
            Timber.e(t, "$TAG: Failed to sync Kugou playlists")
            false
        }
    }

    private suspend fun upsertPlaylist(
        listId: String,
        name: String,
        songIds: List<String>,
        coverUrl: String?,
    ) {
        val localId = PLAYLIST_PREFIX + listId
        val existing = withContext(Dispatchers.IO) {
            playlistPreferencesRepository.userPlaylistsFlow.first()
        }.find { it.id == localId }
        if (existing != null) {
            playlistPreferencesRepository.updatePlaylist(
                existing.copy(
                    name = name,
                    songIds = songIds,
                    // 远程封面存下来，面板页 / 媒体库都能直接显示
                    coverImageUri = coverUrl?.takeIf { it.isNotBlank() } ?: existing.coverImageUri,
                    source = "KUGOU",
                )
            )
        } else {
            playlistPreferencesRepository.createPlaylist(
                name = name,
                songIds = songIds,
                coverImageUri = coverUrl?.takeIf { it.isNotBlank() },
                customId = localId,
                source = "KUGOU",
            )
        }
    }

    /** 远端已删除的歌单：本地一并清掉 */
    private suspend fun removeStalePlaylists(remotePlaylistIds: Set<String>) {
        val playlists = withContext(Dispatchers.IO) {
            playlistPreferencesRepository.userPlaylistsFlow.first()
        }
        playlists.forEach { pl ->
            if (pl.id.startsWith(PLAYLIST_PREFIX)) {
                val listId = pl.id.removePrefix(PLAYLIST_PREFIX)
                if (listId !in remotePlaylistIds) {
                    playlistPreferencesRepository.deletePlaylist(pl.id)
                }
            }
        }
    }

    private suspend fun clearAllKugouPlaylists() {
        val playlists = withContext(Dispatchers.IO) {
            playlistPreferencesRepository.userPlaylistsFlow.first()
        }
        playlists.filter { it.id.startsWith(PLAYLIST_PREFIX) }
            .forEach { pl -> playlistPreferencesRepository.deletePlaylist(pl.id) }
    }

    /** 酷狗歌单歌曲 → 落雪统一结构（播放时按 source="kg" + hash 解析直链）。 */
    private fun KugouSongBrief.toLxSongInfo(fallbackPic: String?): LxSongInfo = LxSongInfo(
        id = hash,
        songmid = hash,
        hash = hash,
        name = name.ifBlank { "未知歌曲" },
        singer = singer,
        albumName = "",
        // LxSongInfo.duration 对内置源（tx/kg/mg/kw）统一是秒
        duration = durationMs / 1000L,
        pic = fallbackPic.orEmpty(),
        source = "kg",
    )
}
