@file:Suppress("DEPRECATION")
package com.theveloper.pixelplay.data.netease

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.NeteaseDao
import com.theveloper.pixelplay.data.database.NeteasePlaylistEntity
import com.theveloper.pixelplay.data.database.NeteaseSongEntity
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.lx.LxJsEngine
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.netease.NeteaseApiService
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.stream.BulkSyncResult
import com.theveloper.pixelplay.data.stream.CloudMusicUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.absoluteValue
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("DEPRECATION")
@Singleton
class NeteaseRepository @Inject constructor(
    private val api: NeteaseApiService,
    private val dao: NeteaseDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val lxJsEngine: LxJsEngine,
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val NETEASE_SONG_ID_OFFSET = 3_000_000_000_000L
        private const val NETEASE_ALBUM_ID_OFFSET = 4_000_000_000_000L
        private const val NETEASE_ARTIST_ID_OFFSET = 5_000_000_000_000L
        private const val NETEASE_PARENT_DIRECTORY = "/Cloud/Netease"
        private const val NETEASE_GENRE = "Netease Cloud"
        private const val NETEASE_PLAYLIST_PREFIX = "netease_playlist:"
        private const val NETEASE_PLAYLIST_PAGE_SIZE = 50
        private const val NETEASE_SONG_DETAIL_BATCH_SIZE = 500
        private const val NETEASE_MAX_PLAYLIST_PAGES = 200

        // 进入媒体库自动同步的节流间隔：1 小时内不重复全量同步，避免触发网易云 405 风控
        private const val AUTO_SYNC_INTERVAL_MS = 60 * 60 * 1000L
        private const val KEY_LAST_AUTO_SYNC = "netease_last_auto_sync_time"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "netease_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "NeteaseRepository: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("netease_prefs_plain", Context.MODE_PRIVATE)
    }

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    private val inFlightSongUrlRequests = java.util.concurrent.ConcurrentHashMap<Long, CompletableDeferred<Result<String>>>()
    private val lastSongUrlAttemptAtMs = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val songUrlRequestCooldownMs = 1500L
    private val neteaseSongUrlRequestMutex = Mutex()
    @Volatile
    private var lastGlobalSongUrlRequestAtMs = 0L
    private val globalSongUrlRequestIntervalMs = 1100L

    // 媒体库自动同步互斥锁：防止快速进出媒体库时并发全量同步重复写库
    private val autoSyncMutex = Mutex()

    init {
        // Auto-load saved cookies on creation so API client is ready
        initFromSavedCookies()
        _isLoggedInFlow.value = api.hasLogin()
        Timber.d("NeteaseRepository init: isLoggedIn=${api.hasLogin()}")
    }

    // ─── Auth State ────────────────────────────────────────────────────

    val isLoggedIn: Boolean
        get() = api.hasLogin()

    /** Get the cookie string for third-party API calls */
    fun getCookieString(): String = api.getCookieString()

    val userId: Long
        get() = prefs.getLong("netease_user_id", -1L)

    val userNickname: String?
        get() = prefs.getString("netease_nickname", null)

    val userAvatar: String?
        get() = prefs.getString("netease_avatar", null)

    // ─── Cookie-Based Authentication ──────────────────────────────────

    /**
     * Initialize from saved cookies on app start.
     */
    fun initFromSavedCookies() {
        val cookieJson = prefs.getString("netease_cookies", null) ?: return
        try {
            val map = jsonToMap(cookieJson)
            if (map.isNotEmpty()) {
                api.setPersistedCookies(map)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to restore Netease cookies")
        }
    }

    /**
     * Save cookies from WebView login result and initialize the API client.
     * Returns the user's nickname on success.
     */
    suspend fun loginWithCookies(cookieJson: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val cookies = jsonToMap(cookieJson)

                if (!cookies.containsKey("MUSIC_U")) {
                    Timber.w("loginWithCookies: required session cookie not found")
                    return@withContext Result.failure(Exception("MUSIC_U cookie not found"))
                }

                // Persist cookies
                prefs.edit().putString("netease_cookies", cookieJson).apply()

                // Initialize API client with cookies
                api.setPersistedCookies(cookies)

                // Fetch user info
                val userAccountRaw = api.getCurrentUserAccount()
                val root = JSONObject(userAccountRaw)
                val code = root.optInt("code", -1)
                val profile = root.optJSONObject("profile")

                if (profile != null) {
                    val uid = profile.optLong("userId")
                    val nickname = profile.optString("nickname", "User")
                    val avatarUrl = profile.optString("avatarUrl", "")
                    Timber.d("loginWithCookies: login successful")

                    saveUserInfo(uid, nickname, avatarUrl)
                    _isLoggedInFlow.value = true
                    Result.success(nickname)
                } else {
                    Timber.w("loginWithCookies: No profile in response (code=$code)")
                    Result.failure(Exception("Failed to fetch user profile (code=$code)"))
                }
            } catch (e: Exception) {
                Timber.e(e, "loginWithCookies: failed")
                Result.failure(e)
            }
        }
    }

    suspend fun logout() {
        api.logout()
        clearLoginState()
        
        // Delete all Netease playlists from the database
        val neteasePlaylistsToDelete = dao.getAllPlaylistsList()
        neteasePlaylistsToDelete.forEach { playlist ->
            dao.deleteSongsByPlaylist(playlist.id)
            dao.deletePlaylist(playlist.id)
            deleteAppPlaylistForNeteasePlaylist(playlist.id)
        }
        
        musicDao.clearAllNeteaseSongs()
        _isLoggedInFlow.value = false
    }

    private fun saveUserInfo(userId: Long, nickname: String, avatarUrl: String?) {
        prefs.edit()
            .putLong("netease_user_id", userId)
            .putString("netease_nickname", nickname)
            .putString("netease_avatar", avatarUrl)
            .apply()
    }

    private fun clearLoginState() {
        prefs.edit().clear().apply()
    }

    // ─── Content ───────────────────────────────────────────────────────

    suspend fun syncUserPlaylists(): Result<List<NeteasePlaylistEntity>> {
        Timber.d("syncUserPlaylists called, isLoggedIn=${isLoggedIn}, userId=$userId")
        if (!isLoggedIn) {
            Timber.w("syncUserPlaylists: Not logged in, aborting")
            return Result.failure(Exception("Not logged in"))
        }
        return withContext(Dispatchers.IO) {
            try {
                val uid = if (userId != -1L) userId else api.getCurrentUserId()
                Timber.d("syncUserPlaylists: fetching playlists for uid=$uid")
                val entitiesById = LinkedHashMap<Long, NeteasePlaylistEntity>()
                var offset = 0
                var page = 0
                var hasMore = true

                while (hasMore) {
                    val raw = api.getUserPlaylists(
                        userId = uid,
                        offset = offset,
                        limit = NETEASE_PLAYLIST_PAGE_SIZE
                    )
                    Timber.d("syncUserPlaylists: page=$page offset=$offset response length=${raw.length}")
                    val root = JSONObject(raw)

                    if (root.optInt("code", -1) != 200) {
                        Timber.e("syncUserPlaylists: API error code=${root.optInt("code")}")
                        return@withContext Result.failure(Exception("API error: code ${root.optInt("code")}"))
                    }

                    val playlistArray = root.optJSONArray("playlist") ?: break
                    val fetchedCount = playlistArray.length()
                    if (fetchedCount == 0) break

                    for (i in 0 until fetchedCount) {
                        val pl = playlistArray.optJSONObject(i) ?: continue
                        val id = pl.optLong("id")
                        if (id <= 0L) continue
                        entitiesById[id] = NeteasePlaylistEntity(
                            id = id,
                            name = pl.optString("name", ""),
                            coverUrl = pl.optString("coverImgUrl", ""),
                            songCount = pl.optInt("trackCount", 0),
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }

                    offset += fetchedCount
                    val totalCount = root.optInt("count", -1)
                    val moreFlag = root.optBoolean("more", false)
                    hasMore = when {
                        moreFlag -> true
                        totalCount > 0 -> offset < totalCount
                        else -> fetchedCount >= NETEASE_PLAYLIST_PAGE_SIZE
                    }

                    page += 1
                    if (page >= NETEASE_MAX_PLAYLIST_PAGES) {
                        Timber.w("syncUserPlaylists: reached max page guard ($NETEASE_MAX_PLAYLIST_PAGES), stopping pagination")
                        hasMore = false
                    }
                }

                val entities = entitiesById.values.toList()

                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
                val stalePlaylists = localPlaylists.filter { it.id !in remoteIds }

                stalePlaylists.forEach { stale ->
                    dao.deleteSongsByPlaylist(stale.id)
                    dao.deletePlaylist(stale.id)
                    // Delete corresponding app playlists for removed Netease playlists
                    deleteAppPlaylistForNeteasePlaylist(stale.id)
                }

                entities.forEach { dao.insertPlaylist(it) }
                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromNetease()
                }
                Result.success(entities)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync user playlists")
                Result.failure(e)
            }
        }
    }

    suspend fun syncPlaylistSongs(playlistId: Long): Result<Int> {
        return syncPlaylistSongs(playlistId, syncUnifiedLibrary = true)
    }

    suspend fun syncPlaylistSongs(
        playlistId: Long,
        syncUnifiedLibrary: Boolean
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = api.getPlaylistDetail(playlistId)
                val root = JSONObject(raw)

                if (root.optInt("code", -1) != 200) {
                    return@withContext Result.failure(Exception("API error"))
                }

                val playlist = root.optJSONObject("playlist")
                    ?: return@withContext Result.failure(Exception("No playlist data"))
                val embeddedTracks = playlist.optJSONArray("tracks")
                val trackIds = playlist.optJSONArray("trackIds")
                val playlistName = playlist.optString("name", "")

                val entitiesBySongId = LinkedHashMap<Long, NeteaseSongEntity>()
                val orderedTrackIds = mutableListOf<Long>()

                for (i in 0 until (embeddedTracks?.length() ?: 0)) {
                    val track = embeddedTracks?.optJSONObject(i) ?: continue
                    val entity = parseTrackToEntity(track, playlistId)
                    entitiesBySongId[entity.neteaseId] = entity
                }

                for (i in 0 until (trackIds?.length() ?: 0)) {
                    val id = trackIds?.optJSONObject(i)?.optLong("id") ?: 0L
                    if (id > 0L) {
                        orderedTrackIds.add(id)
                    }
                }

                val existingTrackIds = entitiesBySongId.keys.toSet()
                val missingTrackIds = if (orderedTrackIds.isNotEmpty()) {
                    orderedTrackIds.filterNot(existingTrackIds::contains)
                } else {
                    emptyList()
                }

                if (missingTrackIds.isNotEmpty()) {
                    Timber.d(
                        "syncPlaylistSongs: playlistId=$playlistId needs ${missingTrackIds.size} additional tracks beyond embedded detail"
                    )
                    missingTrackIds.chunked(NETEASE_SONG_DETAIL_BATCH_SIZE).forEach { chunk ->
                        val detailRaw = api.getSongDetails(chunk)
                        val detailRoot = JSONObject(detailRaw)
                        if (detailRoot.optInt("code", -1) != 200) {
                            Timber.w(
                                "syncPlaylistSongs: getSongDetails failed for chunk size=${chunk.size}, code=${detailRoot.optInt("code", -1)}"
                            )
                            return@forEach
                        }
                        val detailSongs = detailRoot.optJSONArray("songs") ?: return@forEach
                        for (i in 0 until detailSongs.length()) {
                            val track = detailSongs.optJSONObject(i) ?: continue
                            val entity = parseTrackToEntity(track, playlistId)
                            entitiesBySongId[entity.neteaseId] = entity
                        }
                    }
                }

                val entities = if (orderedTrackIds.isNotEmpty()) {
                    val ordered = orderedTrackIds.mapNotNull { entitiesBySongId[it] }
                    if (ordered.size < entitiesBySongId.size) {
                        val orderedSet = orderedTrackIds.toSet()
                        ordered + entitiesBySongId.values.filterNot { it.neteaseId in orderedSet }
                    } else {
                        ordered
                    }
                } else {
                    entitiesBySongId.values.toList()
                }

                val expectedTrackCount = playlist.optInt("trackCount", orderedTrackIds.size)
                if (expectedTrackCount > 0 && entities.size < expectedTrackCount) {
                    Timber.w(
                        "syncPlaylistSongs: playlistId=$playlistId expected=$expectedTrackCount synced=${entities.size} (API may still be limiting some tracks)"
                    )
                }

                dao.deleteSongsByPlaylist(playlistId)
                dao.insertSongs(entities)
                
                // Create or update the corresponding app playlist
                updateAppPlaylistForNeteasePlaylist(playlistId, playlistName, entities)
                
                if (syncUnifiedLibrary) {
                    syncUnifiedLibrarySongsFromNetease()
                }

                Timber.d("Synced ${entities.size} songs for playlist $playlistId")
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync playlist $playlistId")
                Result.failure(e)
            }
        }
    }

    suspend fun syncAllPlaylistsAndSongs(): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            val playlistResult = syncUserPlaylists().getOrElse { return@withContext Result.failure(it) }
            if (playlistResult.isEmpty()) {
                syncUnifiedLibrarySongsFromNetease()
                return@withContext Result.success(
                    BulkSyncResult(
                        playlistCount = 0,
                        syncedSongCount = 0,
                        failedPlaylistCount = 0
                    )
                )
            }

            var syncedSongCount = 0
            var failedPlaylistCount = 0

            playlistResult.forEach { playlist ->
                val songSyncResult = syncPlaylistSongs(
                    playlistId = playlist.id,
                    syncUnifiedLibrary = false
                )
                songSyncResult.fold(
                    onSuccess = { count -> syncedSongCount += count },
                    onFailure = {
                        failedPlaylistCount += 1
                        Timber.w(it, "Failed syncing playlistId=${playlist.id}")
                    }
                )
            }

            syncUnifiedLibrarySongsFromNetease()

            Result.success(
                BulkSyncResult(
                    playlistCount = playlistResult.size,
                    syncedSongCount = syncedSongCount,
                    failedPlaylistCount = failedPlaylistCount
                )
            )
        }
    }

    /**
     * 进入媒体库时自动同步网易云歌单（含歌曲）。
     *
     * - 未登录 → 跳过（返回 success(null)）
     * - 距上次成功全量同步 < AUTO_SYNC_INTERVAL_MS（1 小时）→ 跳过，避免频繁请求触发 405 风控
     * - 否则 → 全量同步：歌单列表 + 每个歌单的歌曲，并生成媒体库 NETEASE 源播放列表
     *
     * @return success(null) 表示跳过；success(BulkSyncResult) 表示本次同步结果；failure 表示同步失败
     */
    suspend fun autoSyncOnLibraryEntry(): Result<BulkSyncResult?> {
        if (!isLoggedIn) {
            Timber.d("autoSyncOnLibraryEntry: not logged in, skipping")
            return Result.success(null)
        }
        return autoSyncMutex.withLock {
            val now = System.currentTimeMillis()
            val lastSync = prefs.getLong(KEY_LAST_AUTO_SYNC, 0L)
            if (now - lastSync < AUTO_SYNC_INTERVAL_MS) {
                Timber.d("autoSyncOnLibraryEntry: throttled (last sync ${(now - lastSync) / 1000}s ago)")
                return@withLock Result.success(null)
            }
            Timber.d("autoSyncOnLibraryEntry: starting full sync")
            syncAllPlaylistsAndSongs().also { result ->
                // 仅成功同步后记录时间，失败则下次进入媒体库时重试
                if (result.isSuccess) {
                    prefs.edit().putLong(KEY_LAST_AUTO_SYNC, System.currentTimeMillis()).apply()
                }
            }
        }
    }

    fun getPlaylists(): Flow<List<NeteasePlaylistEntity>> = dao.getAllPlaylists()

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return dao.getSongsByPlaylist(playlistId).map { entities ->
            entities.map { it.toSong() }
        }
    }

    fun getAllSongs(): Flow<List<Song>> {
        return dao.getAllNeteaseSongs().map { entities ->
            entities.map { it.toSong() }
        }
    }

    fun searchLocalSongs(query: String): Flow<List<Song>> {
        return dao.searchSongs(query).map { entities ->
            entities.map { it.toSong() }
        }
    }

    // ─── Online Search ─────────────────────────────────────────────────

    suspend fun searchOnline(keywords: String, limit: Int = 30): Result<List<Song>> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = api.searchSongs(keywords, limit)
                val root = JSONObject(raw)
                val result = root.optJSONObject("result")
                val songs = result?.optJSONArray("songs")

                if (songs != null) {
                    val songList = mutableListOf<Song>()
                    for (i in 0 until songs.length()) {
                        val track = songs.optJSONObject(i) ?: continue
                        songList.add(parseTrackToSong(track))
                    }
                    Result.success(songList)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                Timber.e(e, "Online search failed for: $keywords")
                Result.failure(e)
            }
        }
    }

    // ─── Song URL Resolution ───────────────────────────────────────────

    suspend fun getSongUrl(songId: Long, quality: String = "exhigh"): Result<String> {
        val now = System.currentTimeMillis()
        val lastAttempt = lastSongUrlAttemptAtMs[songId]
        if (lastAttempt != null && now - lastAttempt < songUrlRequestCooldownMs) {
            Timber.d("Skip Netease song URL retry due to cooldown: songId=$songId")
            return Result.failure(IllegalStateException("Netease song URL request throttled"))
        }
        lastSongUrlAttemptAtMs[songId] = now

        inFlightSongUrlRequests[songId]?.let {
            return it.await()
        }

        val requestDeferred = CompletableDeferred<Result<String>>()
        val existing = inFlightSongUrlRequests.putIfAbsent(songId, requestDeferred)
        if (existing != null) {
            return existing.await()
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                // 首选音质失败按等级阶梯回退：hires → lossless → exhigh → higher → standard
                // （如首选 hires 失败会先尝试 lossless，而不是直接跳级到 exhigh 320k）
                val qualityLadder = listOf("hires", "lossless", "exhigh", "higher", "standard")
                val startIndex = qualityLadder.indexOf(quality)
                val qualityFallbacks = if (startIndex >= 0) {
                    linkedSetOf<String>().apply { addAll(qualityLadder.drop(startIndex)) }
                } else {
                    // 未知 level（如自定义脚本值）按首选优先，再接标准回退链
                    linkedSetOf(quality, "exhigh", "higher", "standard")
                }

                // 各音质级别的最低期望比特率（bps）。
                // 服务端返回的 br 低于此值时视为"被降级"（典型场景：非会员/未登录账号
                // 无论请求什么 level 都只返回 128k 完整 URL），此时不能直接接受，
                // 否则全链路音质会被悄悄压到 128k。
                val minBrForLevel = mapOf(
                    "standard" to 128_000,
                    "higher" to 192_000,
                    "exhigh" to 320_000,
                    "lossless" to 320_000,
                    "hires" to 320_000
                )
                var lastFailure: String? = null
                // 记录被服务端降级的 URL，作为最终兜底（至少能播放）
                var degradedUrl: String? = null

                for (level in qualityFallbacks) {
                    val raw = requestSongUrl(songId, level)
                    val root = JSONObject(raw)
                    val code = root.optInt("code", -1)
                    if (code != 200) {
                        lastFailure = "API code=$code for level=$level"
                        continue
                    }

                    val data = root.optJSONArray("data")
                    val urlObj = data?.optJSONObject(0)
                    val url = urlObj?.optString("url", "")
                    if (!url.isNullOrBlank() && url != "null") {
                        // 检查是否为完整歌曲（非 30 秒预览）：如果歌曲时长小于 45 秒，
                        // 或者有 freeTrialInfo 标记，则视为预览，继续尝试其他方案
                        val br = urlObj.optInt("br", 0)
                        val size = urlObj.optLong("size", 0L)
                        val reportedDuration = urlObj.optLong("time", 0L)
                        val hasFreeTrial = urlObj.opt("freeTrialInfo") != null &&
                                urlObj.optString("freeTrialInfo") != "null"
                        val isPreviewUrl = hasFreeTrial || (reportedDuration > 0 && reportedDuration < 45000) ||
                                (br > 0 && size > 0 && size < br * 30 / 8) // 简单估算：如果大小 <30秒音频，视为预览
                        if (!isPreviewUrl) {
                            // ⚡ br 校验：返回比特率低于该音质最低期望 → 视为被服务端降级，
                            // 不直接接受，继续下一级 / 最终交给 LxJsEngine 尝试高音质
                            val minBr = minBrForLevel[level]
                            if (minBr != null && br > 0 && br < minBr) {
                                Timber.w("Netease songId=$songId level=$level downgraded to br=$br (<$minBr)")
                                lastFailure = "Downgraded to $br at level=$level"
                                if (degradedUrl == null) degradedUrl = url
                                continue
                            }
                            // standard 且此前已发生降级：128k 也匹配但说明账号拿不到高音质，
                            // 不再直接接受，交由 LxJsEngine 尝试（音源可能提供 320k/无损）
                            if (degradedUrl != null && level == "standard") {
                                lastFailure = "All preferred levels downgraded"
                                break
                            }
                            Timber.d("Resolved Netease URL for songId=$songId with level=$level")
                            return@runCatching url
                        }
                        Timber.d("Netease songId=$songId level=$level appears to be a preview URL, will try Lx engine")
                        lastFailure = "Preview URL at level=$level"
                        continue
                    }

                    val freeTrialInfo = urlObj?.opt("freeTrialInfo")
                    lastFailure = "Empty URL at level=$level, freeTrialInfo=$freeTrialInfo"
                }

                // 官方 API 全部失败或只有预览，尝试落雪 JS 引擎
                Timber.d("Netease official URLs exhausted for songId=$songId, trying LxJsEngine")
                val lxUrl = tryGetLxEngineUrl(songId, quality)
                if (lxUrl != null) {
                    Timber.d("LxJsEngine resolved full URL for songId=$songId")
                    return@runCatching lxUrl
                }

                // Lx 引擎不可用时，退回之前被服务端降级的 URL（至少能播放）
                degradedUrl?.let {
                    Timber.w("Netease songId=$songId falling back to downgraded URL")
                    return@runCatching it
                }

                throw Exception("No URL available for song $songId ($lastFailure)")
            }
        }

        requestDeferred.complete(result)
        inFlightSongUrlRequests.remove(songId, requestDeferred)
        return result
    }

    /**
     * Make a single song URL request with global rate-limit guard.
     */
    private suspend fun requestSongUrl(songId: Long, level: String): String {
        neteaseSongUrlRequestMutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = globalSongUrlRequestIntervalMs - (now - lastGlobalSongUrlRequestAtMs)
            if (waitMs > 0) delay(waitMs)
            lastGlobalSongUrlRequestAtMs = System.currentTimeMillis()
        }
        return api.getSongDownloadUrl(songId, level)
    }

    /**
     * Fallback: try to resolve a streaming URL using the LxJsEngine (落雪 JS 音源).
     * This handles VIP / copyright-protected songs where the official API only returns
     * a 30-second preview. Returns null if the JS engine is unavailable or fails.
     */
    private suspend fun tryGetLxEngineUrl(songId: Long, quality: String): String? {
        return try {
            // 从本地数据库获取歌曲元信息（用于传给 JS 引擎）
            val localSong = dao.getAllNeteaseSongsList().firstOrNull { it.neteaseId == songId }
            val songTitle = localSong?.title ?: ""
            val songArtist = localSong?.artist ?: ""
            val songAlbum = localSong?.album ?: ""
            val songCover = localSong?.albumArtUrl ?: ""
            val songIdStr = songId.toString()
            val songInfo: Map<String, Any?> = mapOf(
                "id" to songIdStr,
                "vid" to songIdStr,
                "songmid" to songIdStr,
                "hash" to songIdStr,
                "name" to songTitle,
                "singer" to songArtist,
                "artists" to songArtist,
                "album" to songAlbum,
                "albumName" to songAlbum,
                "duration" to (localSong?.duration ?: 0),
                "pic" to songCover,
                "cover" to songCover
            )

            // 质量映射：根据用户首选音质选择脚本支持的音质参数。
            // 脚本（落雪 userApi）支持 24bit / flac / 320k / 192k / 128k，
            // 且脚本内部会按 selectQuality 自动选择最接近的可用音质并多链路回退，
            // 所以只需按顺序尝试：首选 → 320k → 192k → 128k
            val lxQualities = when (quality) {
                "lossless", "hires", "flac", "24bit" -> listOf("flac", "24bit", "320k", "192k", "128k")
                "exhigh", "higher" -> listOf("320k", "192k", "128k")
                else -> listOf("128k")
            }

            // 依次尝试各音质级别，命中即返回
            for (lxQuality in lxQualities) {
                val url = runCatching { lxJsEngine.getPlayUrl("wy", songInfo, lxQuality) }.getOrNull()
                if (!url.isNullOrBlank()) {
                    Timber.d("LxJsEngine resolved full URL for songId=$songId at quality=$lxQuality")
                    return url
                }
            }

            Timber.d("LxJsEngine returned null for songId=$songId")
            null
        } catch (t: Throwable) {
            Timber.w(t, "LxJsEngine failed for songId=$songId")
            null
        }
    }

    // ─── Lyrics ────────────────────────────────────────────────────────

    suspend fun getLyrics(songId: Long): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = api.getLyrics(songId)
                val root = JSONObject(raw)

                val lrcText = root.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
                val tlyricText = root.optJSONObject("tlyric")?.optString("lyric")?.takeIf { it.isNotBlank() }

                // 原文 + 翻译都存在时：按时间戳合并
                if (lrcText != null && tlyricText != null) {
                    Result.success(mergeLrcWithTranslation(lrcText, tlyricText))
                }

                // 只有原文
                if (lrcText != null) {
                    Result.success(lrcText)
                }

                // 只有翻译（作为兜底）
                if (tlyricText != null) {
                    Result.success(tlyricText)
                }

                Result.failure(Exception("No lyrics for song $songId"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to get lyrics for song $songId")
                Result.failure(e)
            }
        }
    }

    /**
     * 合并原文 LRC 与翻译 LRC：按时间戳排序后，原文行之后紧跟相同时间戳的翻译行。
     * LyricsUtils.pairTranslationLines() 会自动根据相同时间戳配对翻译。
     */
    private fun mergeLrcWithTranslation(lrcText: String, tlyricText: String): String {
        val originalLines = lrcText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val translationLines = tlyricText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

        val timestampLineRegex = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)")

        data class TimedLine(val timestampMs: Long, val text: String, val hasFraction: Boolean, val fractionDigits: Int)

        fun parseTimedLines(lines: List<String>): List<TimedLine> {
            val result = mutableListOf<TimedLine>()
            for (rawLine in lines) {
                val matchResult = timestampLineRegex.find(rawLine) ?: continue
                val minutes = matchResult.groupValues[1].toLong()
                val seconds = matchResult.groupValues[2].toLong()
                val fracStr = matchResult.groupValues[3].ifBlank { "0" }
                val fractionDigits = fracStr.length
                val frac = when (fractionDigits) {
                    1 -> fracStr.toLong() * 100L
                    2 -> fracStr.toLong() * 10L
                    3 -> fracStr.toLong()
                    else -> fracStr.padEnd(3, '0').take(3).toLong()
                }
                val timestampMs = minutes * 60_000L + seconds * 1_000L + frac
                val text = matchResult.groupValues[4].trim()
                if (text.isNotBlank()) {
                    result.add(TimedLine(timestampMs, text, fractionDigits > 0, fractionDigits))
                }
            }
            return result
        }

        val originalTimed = parseTimedLines(originalLines)
        val translationTimed = parseTimedLines(translationLines)

        if (originalTimed.isEmpty()) return originalLines.joinToString("\n")
        if (translationTimed.isEmpty()) return originalLines.joinToString("\n")

        val translationByTs = translationTimed.associate { it.timestampMs to it.text }

        // Determine if we should use 3-digit fraction precision
        val useThreeDigitFraction = originalTimed.any { it.hasFraction && it.fractionDigits == 3 }

        val output = mutableListOf<String>()
        val metaLineRegex = Regex("^\\[(by|ti|ar|al|au|re|ve|offset|length):.*]", RegexOption.IGNORE_CASE)
        originalLines.forEach { raw ->
            if (metaLineRegex.matches(raw)) {
                output.add(raw)
            }
        }

        fun formatTimestamp(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (useThreeDigitFraction) {
                val millisPart = ms % 1000
                String.format("%02d:%02d.%03d", minutes, seconds, millisPart)
            } else {
                val hundredths = (ms % 1000) / 10
                String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
            }
        }

        for (orig in originalTimed) {
            // Use unified timestamp format for BOTH original and translation
            val ts = formatTimestamp(orig.timestampMs)
            output.add("[${ts}]${orig.text}")

            val exactMatch = translationByTs[orig.timestampMs]
            if (exactMatch != null) {
                output.add("[${ts}]${exactMatch}")
                continue
            }

            val tolerance = 500L
            val closeMatch = translationTimed
                .firstOrNull { Math.abs(it.timestampMs - orig.timestampMs) <= tolerance }
                ?.text
            if (closeMatch != null) {
                output.add("[${ts}]${closeMatch}")
            }
        }

        return output.joinToString("\n")
    }

    // ─── JSON Parsing Helpers ──────────────────────────────────────────

    private fun parseTrackToEntity(track: JSONObject, playlistId: Long): NeteaseSongEntity {
        val artists = track.optJSONArray("ar")
        val artistName = buildString {
            if (artists != null) {
                for (j in 0 until artists.length()) {
                    if (j > 0) append(", ")
                    append(artists.optJSONObject(j)?.optString("name", "") ?: "")
                }
            } else {
                append("Unknown Artist")
            }
        }
        val album = track.optJSONObject("al")

        return NeteaseSongEntity(
            id = "${playlistId}_${track.optLong("id")}",
            neteaseId = track.optLong("id"),
            playlistId = playlistId,
            title = track.optString("name", ""),
            artist = artistName,
            album = album?.optString("name", "Unknown Album") ?: "Unknown Album",
            albumId = album?.optLong("id") ?: -1L,
            duration = track.optLong("dt"),
            albumArtUrl = normalizeRemoteImageUrl(album?.optString("picUrl")),
            mimeType = "audio/mpeg",
            bitrate = null,
            dateAdded = track.optLong("publishTime", System.currentTimeMillis())
        )
    }

    private fun parseTrackToSong(track: JSONObject): Song {
        val artists = track.optJSONArray("ar")
        val artistName = buildString {
            if (artists != null) {
                for (j in 0 until artists.length()) {
                    if (j > 0) append(", ")
                    append(artists.optJSONObject(j)?.optString("name", "") ?: "")
                }
            } else {
                append("Unknown Artist")
            }
        }
        val album = track.optJSONObject("al")
        val neteaseId = track.optLong("id")

        return Song(
            id = "netease_$neteaseId",
            title = track.optString("name", ""),
            artist = artistName,
            artistId = artists?.optJSONObject(0)?.optLong("id") ?: -1L,
            album = album?.optString("name", "Unknown Album") ?: "Unknown Album",
            albumId = album?.optLong("id") ?: -1L,
            path = "",
            contentUriString = "netease://$neteaseId",
            albumArtUriString = normalizeRemoteImageUrl(album?.optString("picUrl")),
            duration = track.optLong("dt"),
            mimeType = "audio/mpeg",
            bitrate = null,
            sampleRate = null,
            year = 0,
            trackNumber = 0,
            dateAdded = track.optLong("publishTime", System.currentTimeMillis()),
            isFavorite = false,
            neteaseId = neteaseId
        )
    }

    private suspend fun syncUnifiedLibrarySongsFromNetease() {
        val neteaseSongs = dao.getAllNeteaseSongsList()
        val existingUnifiedNeteaseIds = musicDao.getAllNeteaseSongIds()

        if (neteaseSongs.isEmpty()) {
            if (existingUnifiedNeteaseIds.isNotEmpty()) {
                musicDao.clearAllNeteaseSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(neteaseSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        neteaseSongs.forEach { neteaseSong ->
            val songId = toUnifiedSongId(neteaseSong.neteaseId)
            val artistNames = parseArtistNames(neteaseSong.artist)
            val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
            val primaryArtistId = toUnifiedArtistId(primaryArtistName)

            artistNames.forEachIndexed { index, artistName ->
                val artistId = toUnifiedArtistId(artistName)
                artists.putIfAbsent(
                    artistId,
                    ArtistEntity(
                        id = artistId,
                        name = artistName,
                        trackCount = 0,
                        imageUrl = null
                    )
                )
                crossRefs.add(
                    SongArtistCrossRef(
                        songId = songId,
                        artistId = artistId,
                        isPrimary = index == 0
                    )
                )
            }

            val albumId = toUnifiedAlbumId(neteaseSong.albumId, neteaseSong.album)
            val albumName = neteaseSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = neteaseSong.dateAdded,
                    year = 0,
                    albumArtUriString = neteaseSong.albumArtUrl
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = neteaseSong.title,
                    artistName = neteaseSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "netease://${neteaseSong.neteaseId}",
                    albumArtUriString = neteaseSong.albumArtUrl,
                    duration = neteaseSong.duration,
                    genre = NETEASE_GENRE,
                    filePath = "",
                    parentDirectoryPath = NETEASE_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = 0,
                    year = 0,
                    dateAdded = neteaseSong.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = neteaseSong.mimeType,
                    bitrate = neteaseSong.bitrate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.NETEASE
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { album ->
            album.copy(songCount = albumCounts[album.id] ?: 0)
        }

        val currentUnifiedSongIds = songs.map { it.id }.toSet()
        val deletedUnifiedSongIds = existingUnifiedNeteaseIds.filter { it !in currentUnifiedSongIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedUnifiedSongIds
        )
    }

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    private fun toUnifiedSongId(neteaseId: Long): Long {
        return -(NETEASE_SONG_ID_OFFSET + neteaseId.absoluteValue)
    }

    private fun toUnifiedAlbumId(albumId: Long, albumName: String): Long {
        val normalized = if (albumId > 0L) albumId.absoluteValue else albumName.lowercase().hashCode().toLong().absoluteValue
        return -(NETEASE_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(NETEASE_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    // ─── Delete ────────────────────────────────────────────────────────

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deleteSongsByPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
        deleteAppPlaylistForNeteasePlaylist(playlistId)
        syncUnifiedLibrarySongsFromNetease()
    }

    // ─── App Playlist Management ────────────────────────────────────────

    private suspend fun getAppPlaylistIdForNetease(neteasePlaylistId: Long): String {
        return "$NETEASE_PLAYLIST_PREFIX$neteasePlaylistId"
    }

    private suspend fun updateAppPlaylistForNeteasePlaylist(
        neteasePlaylistId: Long,
        playlistName: String,
        neteaseEntities: List<NeteaseSongEntity>
    ) {
        try {
            // Convert Netease song entities to unified song IDs (Long format, stored as String)
            // These must match the IDs generated in syncUnifiedLibrarySongsFromNetease
            val unifiedSongIds = neteaseEntities.map { entity ->
                toUnifiedSongId(entity.neteaseId).toString()
            }

            val appPlaylistId = getAppPlaylistIdForNetease(neteasePlaylistId)
            
            // Get all current app playlists
            val allPlaylists = playlistPreferencesRepository.userPlaylistsFlow
            val existingPlaylist = withContext(Dispatchers.IO) {
                allPlaylists.map { playlists ->
                    playlists.find { it.id == appPlaylistId }
                }.first()
            }

            if (existingPlaylist != null) {
                // Update the existing playlist
                playlistPreferencesRepository.updatePlaylist(
                    existingPlaylist.copy(
                        name = playlistName,
                        songIds = unifiedSongIds,
                        lastModified = System.currentTimeMillis(),
                        source = "NETEASE" // Mark as NetEase source
                    )
                )
                Timber.d("Updated app playlist for Netease playlist $neteasePlaylistId: $playlistName")
            } else {
                // Create a new playlist with custom ID to prevent duplicates
                playlistPreferencesRepository.createPlaylist(
                    name = playlistName,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,  // Use NetEase prefix ID for matching on next sync
                    source = "NETEASE"         // Mark as NetEase source
                )
                Timber.d("Created new app playlist for Netease playlist $neteasePlaylistId: $playlistName with ID: $appPlaylistId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update/create app playlist for Netease playlist $neteasePlaylistId")
        }
    }

    private suspend fun deleteAppPlaylistForNeteasePlaylist(neteasePlaylistId: Long) {
        try {
            val appPlaylistId = getAppPlaylistIdForNetease(neteasePlaylistId)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
            Timber.d("Deleted app playlist for Netease playlist $neteasePlaylistId")
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for Netease playlist $neteasePlaylistId")
        }
    }

    // ─── Utility ───────────────────────────────────────────────────────

    private fun jsonToMap(json: String): Map<String, String> =
        CloudMusicUtils.jsonToMap(json)
}

/**
 * 清洗远程图片 URL（与搜索页 LxSongInfo.pic 的清洗逻辑保持一致）：
 * - 去除首尾空白与反引号
 * - 协议相对地址 // 补全为 https://
 * - 明文 http:// 升级为 https://（Android 默认禁止明文流量，网易云 CDN 均支持 https）
 * 非 HTTP(S) 地址（content://、navidrome_cover:// 等）原样返回。
 */
fun normalizeRemoteImageUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().replace("`", "")
    if (cleaned.isBlank()) return null
    return when {
        cleaned.startsWith("//") -> "https:$cleaned"
        cleaned.startsWith("http://") -> "https:" + cleaned.removePrefix("http:")
        else -> cleaned
    }
}
