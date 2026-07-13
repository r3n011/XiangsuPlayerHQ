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
                val qualityFallbacks = linkedSetOf(quality, "higher", "standard")
                var lastFailure: String? = null

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

            // 质量映射：exhigh/higher -> "320k"，standard -> "128k"
            val lxQuality = when (quality) {
                "exhigh", "higher" -> "320k"
                else -> "128k"
            }

            // 先尝试 320k，失败回落到 128k
            val url320 = runCatching { lxJsEngine.getPlayUrl("wy", songInfo, "320k") }.getOrNull()
            if (!url320.isNullOrBlank()) return url320

            val url128 = runCatching { lxJsEngine.getPlayUrl("wy", songInfo, "128k") }.getOrNull()
            if (!url128.isNullOrBlank()) return url128

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
            albumArtUrl = album?.optString("picUrl"),
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
            albumArtUriString = album?.optString("picUrl"),
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
