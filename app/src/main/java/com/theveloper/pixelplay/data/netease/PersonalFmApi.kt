package com.theveloper.pixelplay.data.netease

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 私人 FM API - 通过第三方 ncmapi 接口获取网易云私人 FM 推荐和歌曲详情
 */
@Singleton
class PersonalFmApi @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private companion object {
        private const val TAG = "PersonalFmApi"
        private const val BASE_URL = "https://ncmapi.btwoa.com"
    }

    /**
     * 获取私人 FM 推荐歌曲列表
     * @param cookie 用户的网易云 cookie 字符串（会放在请求头 Cookie 字段中）
     * @return 推荐歌曲 ID 列表（Long 类型）
     */
    suspend fun fetchPersonalFmRecommendations(cookie: String): Result<List<Long>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Timber.d("$TAG: Fetching personal FM recommendations (cookie via header)")
                val timestamp = System.currentTimeMillis()
                // Cookie 放在标准 HTTP 请求头 Cookie 字段中，而非 URL 参数
                // 这是网易云 NeteaseMusic API 的标准做法，能正确识别用户身份、
                // 听歌偏好以及每日推荐 / VIP 歌曲信息
                val url = "$BASE_URL/personal_fm?timestamp=$timestamp"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                    .addHeader("Cookie", cookie)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(
                        Exception("Empty response")
                    )
                    Timber.d("$TAG: personal_fm response length: ${body.length}")

                    val root = JSONObject(body)
                    if (root.optInt("code", 200) != 200) {
                        return@withContext Result.failure(
                            Exception("API error: code=${root.optInt("code")}")
                        )
                    }

                    val dataArray = root.optJSONArray("data")
                        ?: return@withContext Result.failure(Exception("No data in response"))

                    val songIds = mutableListOf<Long>()
                    for (i in 0 until dataArray.length()) {
                        val songObj = dataArray.optJSONObject(i) ?: continue
                        val songId = songObj.optLong("id", -1L)
                        if (songId > 0) {
                            songIds.add(songId)
                        }
                    }

                    if (songIds.isEmpty()) {
                        return@withContext Result.failure(Exception("No song IDs in response"))
                    }

                    Timber.d("$TAG: Got ${songIds.size} song recommendations")
                    Result.success(songIds)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: fetchPersonalFmRecommendations failed")
                Result.failure(t)
            }
        }
    }

    /**
     * 批量获取歌曲详情
     * @param songIds 网易云歌曲 ID 列表
     * @param cookie 用户的网易云 cookie 字符串（放在请求头 Cookie 字段，可选）
     * @return 歌曲详情列表
     */
    suspend fun fetchSongDetails(songIds: List<Long>, cookie: String? = null): Result<List<PersonalFmSongDetail>> {
        if (songIds.isEmpty()) return Result.success(emptyList())
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Timber.d("$TAG: Fetching song details for ${songIds.size} songs (cookie via header: ${!cookie.isNullOrBlank()})")
                val idsParam = songIds.joinToString(",")
                val timestamp = System.currentTimeMillis()
                val url = "$BASE_URL/song/detail?ids=$idsParam&timestamp=$timestamp"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                // 如果有 cookie，则放在请求头中，让接口能识别用户身份
                // （VIP 歌曲、已收藏标记等信息依赖正确的用户 cookie）
                if (!cookie.isNullOrBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                }
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(
                        Exception("Empty response")
                    )

                    val root = JSONObject(body)
                    if (root.optInt("code", 200) != 200) {
                        return@withContext Result.failure(
                            Exception("API error: code=${root.optInt("code")}")
                        )
                    }

                    val songsArray = root.optJSONArray("songs")
                        ?: return@withContext Result.failure(Exception("No songs in response"))

                    val details = mutableListOf<PersonalFmSongDetail>()
                    for (i in 0 until songsArray.length()) {
                        val songObj = songsArray.optJSONObject(i) ?: continue
                        val name = songObj.optString("name", "Unknown")
                        val id = songObj.optLong("id")

                        val artists = mutableListOf<String>()
                        val artistIds = mutableListOf<Long>()
                        val arArray: JSONArray? = songObj.optJSONArray("ar")
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val ar = arArray.optJSONObject(j) ?: continue
                                ar.optString("name")?.takeIf { it.isNotBlank() }?.let { artists.add(it) }
                                val aid = ar.optLong("id")
                                if (aid > 0) artistIds.add(aid)
                            }
                        }

                        val album: JSONObject? = songObj.optJSONObject("al")
                        val albumName = album?.optString("name", "Unknown Album") ?: "Unknown Album"
                        val albumPic = album?.optString("picUrl") ?: ""

                        val duration = songObj.optLong("dt", 0L)
                        val fee = songObj.optInt("fee", 0)

                        details.add(
                            PersonalFmSongDetail(
                                id = id,
                                name = name,
                                artists = artists,
                                artistIds = artistIds,
                                albumName = albumName,
                                albumPic = albumPic,
                                duration = duration,
                                fee = fee
                            )
                        )
                    }

                    Timber.d("$TAG: Got ${details.size} song details")
                    Result.success(details)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: fetchSongDetails failed")
                Result.failure(t)
            }
        }
    }

    /**
     * 红心/取消红心歌曲
     * 对应网易云官方的 /like 接口
     * @param neteaseSongId 网易云歌曲 ID
     * @param like true=添加红心, false=取消红心
     * @param cookie 用户的网易云 cookie（请求头 Cookie 字段，必须登录才能使用）
     * @return 是否操作成功
     */
    suspend fun likeSong(neteaseSongId: Long, like: Boolean, cookie: String): Result<Boolean> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (cookie.isBlank()) {
                return@withContext Result.failure<Boolean>(IllegalStateException("网易云未登录，无法同步红心"))
            }
            try {
                Timber.d("$TAG: likeSong id=$neteaseSongId like=$like")
                val timestamp = System.currentTimeMillis()
                val url = "$BASE_URL/like?id=$neteaseSongId&like=$like&timestamp=$timestamp"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                    .addHeader("Cookie", cookie)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure<Boolean>(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string()
                        ?: return@withContext Result.failure<Boolean>(Exception("Empty response"))

                    val root = JSONObject(body)
                    val code = root.optInt("code", 200)
                    val success = code == 200
                    Timber.d("$TAG: likeSong id=$neteaseSongId like=$like code=$code success=$success")
                    Result.success(success)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: likeSong failed id=$neteaseSongId")
                Result.failure(t)
            }
        }
    }

    // ——— 评论相关接口 —————————————————————————————————————————————————
    /**
     * 发送评论到歌曲/专辑/歌单等
     * 对应网易云官方的 /comment?t=1 接口
     * @param type 资源类型: 0=歌曲, 1=MV, 2=歌单, 3=专辑, 4=电台, 5=视频
     * @param id 资源 ID
     * @param content 评论内容
     * @param cookie 用户的网易云 cookie（必须登录才能发送评论）
     * @return 是否发送成功
     */
    suspend fun sendComment(type: Int, id: Long, content: String, cookie: String): Result<Boolean> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (cookie.isBlank()) {
                return@withContext Result.failure<Boolean>(IllegalStateException("网易云未登录，无法发送评论"))
            }
            if (content.isBlank()) {
                return@withContext Result.failure<Boolean>(IllegalArgumentException("评论内容不能为空"))
            }
            try {
                Timber.d("$TAG: sendComment type=$type id=$id content=${content.take(20)}")
                val encodedContent = java.net.URLEncoder.encode(content, "UTF-8")
                val url = "$BASE_URL/comment?t=1&type=$type&id=$id&content=$encodedContent"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                    .addHeader("Cookie", cookie)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure<Boolean>(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string()
                        ?: return@withContext Result.failure<Boolean>(Exception("Empty response"))

                    val root = JSONObject(body)
                    val code = root.optInt("code", 200)
                    val success = code == 200
                    Timber.d("$TAG: sendComment type=$type id=$id code=$code success=$success")
                    Result.success(success)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: sendComment failed type=$type id=$id")
                Result.failure(t)
            }
        }
    }

    /**
     * 删除用户自己的评论
     * 对应网易云官方的 /comment?t=0 接口
     * @param type 资源类型: 0=歌曲, 1=MV, 2=歌单, 3=专辑, 4=电台, 5=视频
     * @param id 资源 ID
     * @param commentId 要删除的评论 ID
     * @param cookie 用户的网易云 cookie（必须登录才能删除自己的评论）
     * @return 是否删除成功
     */
    suspend fun deleteComment(type: Int, id: Long, commentId: Long, cookie: String): Result<Boolean> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (cookie.isBlank()) {
                return@withContext Result.failure<Boolean>(IllegalStateException("网易云未登录，无法删除评论"))
            }
            try {
                Timber.d("$TAG: deleteComment type=$type id=$id commentId=$commentId")
                val url = "$BASE_URL/comment?t=0&type=$type&id=$id&commentId=$commentId"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                    .addHeader("Cookie", cookie)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure<Boolean>(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string()
                        ?: return@withContext Result.failure<Boolean>(Exception("Empty response"))

                    val root = JSONObject(body)
                    val code = root.optInt("code", 200)
                    val success = code == 200
                    Timber.d("$TAG: deleteComment type=$type id=$id commentId=$commentId code=$code success=$success")
                    Result.success(success)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: deleteComment failed type=$type id=$id commentId=$commentId")
                Result.failure(t)
            }
        }
    }

    /**
     * 给评论点赞/取消点赞
     * 对应网易云官方的 /comment/like 接口
     * @param type 资源类型: 0=歌曲, 1=MV, 2=歌单, 3=专辑, 4=电台, 5=视频
     * @param id 资源 ID
     * @param cid 评论 ID
     * @param like true=点赞, false=取消点赞
     * @param cookie 用户的网易云 cookie（必须登录才能点赞）
     * @return 是否操作成功
     */
    suspend fun likeComment(type: Int, id: Long, cid: Long, like: Boolean, cookie: String): Result<Boolean> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (cookie.isBlank()) {
                return@withContext Result.failure<Boolean>(IllegalStateException("网易云未登录，无法点赞"))
            }
            try {
                val likeInt = if (like) 1 else 0
                Timber.d("$TAG: likeComment type=$type id=$id cid=$cid like=$like")
                val url = "$BASE_URL/comment/like?id=$id&cid=$cid&t=$likeInt&type=$type"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                    .addHeader("Cookie", cookie)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure<Boolean>(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string()
                        ?: return@withContext Result.failure<Boolean>(Exception("Empty response"))

                    val root = JSONObject(body)
                    val code = root.optInt("code", 200)
                    val success = code == 200
                    Timber.d("$TAG: likeComment type=$type id=$id cid=$cid like=$like code=$code success=$success")
                    Result.success(success)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: likeComment failed type=$type id=$id cid=$cid")
                Result.failure(t)
            }
        }
    }

    // ——— 歌手相关接口 ————————————————————————————————————————————————

    /**
     * 获取歌手的热门歌曲列表
     * 接口: /artist/songs?id={artistId}&order={order}&limit={limit}&offset={offset}
     */
    suspend fun fetchArtistSongs(
        artistId: Long,
        order: String = "hot",
        limit: Int = 50,
        offset: Int = 0,
        cookie: String? = null
    ): Result<Pair<List<NeteaseArtistSong>, Int>> {
        if (artistId <= 0) return Result.success(Pair(emptyList(), 0))
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                Timber.d("$TAG: Fetching artist songs for artistId=$artistId")
                val url = "$BASE_URL/artist/songs?id=$artistId&order=$order&limit=$limit&offset=$offset&timestamp=$timestamp"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                if (!cookie.isNullOrBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                }
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(
                        Exception("Empty response")
                    )

                    val root = JSONObject(body)
                    Timber.d("$TAG: /artist/songs response keys: ${root.keys().asSequence().toList()}")

                    // 尝试从 songs 字段获取，若不存在则尝试 hotSongs
                    val total = root.optInt("total", 0)
                    val songsArray = root.optJSONArray("songs")
                        ?: root.optJSONArray("hotSongs")
                        ?: return@withContext Result.success(Pair(emptyList(), total))

                    val songs = mutableListOf<NeteaseArtistSong>()
                    for (i in 0 until songsArray.length()) {
                        val songObj = songsArray.optJSONObject(i) ?: continue
                        val songId = songObj.optLong("id")
                        if (songId <= 0) {
                            Timber.d("$TAG: Skipping artist song with invalid id=$songId at index $i")
                            continue
                        }
                        val songName = songObj.optString("name", "Unknown")

                        val artists = mutableListOf<String>()
                        val artistIds = mutableListOf<Long>()
                        val arArray: JSONArray? = songObj.optJSONArray("ar")
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val ar = arArray.optJSONObject(j) ?: continue
                                ar.optString("name")?.takeIf { it.isNotBlank() }?.let { artists.add(it) }
                                val aid = ar.optLong("id")
                                if (aid > 0) artistIds.add(aid)
                            }
                        }

                        val albumObj: JSONObject? = songObj.optJSONObject("al")
                        val albumId = albumObj?.optLong("id") ?: 0L
                        val albumName = albumObj?.optString("name", "Unknown Album") ?: "Unknown Album"
                        val albumPic = albumObj?.optString("picUrl") ?: ""

                        val duration = songObj.optLong("dt", 0L)
                        val fee = songObj.optInt("fee", 0)

                        songs.add(
                            NeteaseArtistSong(
                                id = songId,
                                name = songName,
                                artists = artists,
                                artistIds = artistIds,
                                albumId = albumId,
                                albumName = albumName,
                                albumPic = albumPic,
                                duration = duration,
                                fee = fee
                            )
                        )
                    }

                    Timber.d("$TAG: Got ${songs.size} artist songs (total=$total) for artistId=$artistId")
                    Result.success(Pair(songs, total))
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: fetchArtistSongs failed for artistId=$artistId")
                Result.failure(t)
            }
        }
    }

    /**
     * 获取歌手的专辑列表
     * 接口: /artist/album?id={artistId}&limit={limit}&offset={offset}
     */
    suspend fun fetchArtistAlbums(
        artistId: Long,
        limit: Int = 30,
        offset: Int = 0,
        cookie: String? = null
    ): Result<Pair<List<NeteaseArtistAlbum>, Int>> {
        if (artistId <= 0) return Result.success(Pair(emptyList(), 0))
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                Timber.d("$TAG: Fetching artist albums for artistId=$artistId")
                val url = "$BASE_URL/artist/album?id=$artistId&limit=$limit&offset=$offset&timestamp=$timestamp"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                if (!cookie.isNullOrBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                }
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(
                        Exception("Empty response")
                    )

                    val root = JSONObject(body)
                    Timber.d("$TAG: /artist/album response keys: ${root.keys().asSequence().toList()}")

                    val total = root.optInt("hotAlbumsSize",
                        root.optJSONObject("artist")?.optInt("albumSize", 0) ?: 0
                    )

                    val hotAlbumsArray = root.optJSONArray("hotAlbums")
                        ?: root.optJSONArray("albums")
                        ?: return@withContext Result.success(Pair(emptyList(), total))

                    val albums = mutableListOf<NeteaseArtistAlbum>()
                    for (i in 0 until hotAlbumsArray.length()) {
                        val albumObj = hotAlbumsArray.optJSONObject(i) ?: continue
                        val albumId = albumObj.optLong("id")
                        val albumName = albumObj.optString("name", "Unknown Album")
                        val picUrl = albumObj.optString("picUrl") ?: ""
                        val publishTime = albumObj.optLong("publishTime", 0L)
                        val size = albumObj.optInt("size", 0)

                        albums.add(
                            NeteaseArtistAlbum(
                                id = albumId,
                                name = albumName,
                                picUrl = picUrl,
                                publishTime = publishTime,
                                size = size
                            )
                        )
                    }

                    Timber.d("$TAG: Got ${albums.size} artist albums (total=$total) for artistId=$artistId")
                    Result.success(Pair(albums, total))
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: fetchArtistAlbums failed for artistId=$artistId")
                Result.failure(t)
            }
        }
    }

    /**
     * 获取专辑详情（歌曲列表）
     * 接口: /album?id={albumId}
     */
    suspend fun fetchAlbumDetail(
        albumId: Long,
        cookie: String? = null
    ): Result<NeteaseAlbumDetail> {
        if (albumId <= 0) return Result.failure(Exception("Invalid albumId"))
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                Timber.d("$TAG: Fetching album detail for albumId=$albumId")
                val url = "$BASE_URL/album?id=$albumId&timestamp=$timestamp"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                if (!cookie.isNullOrBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                }
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(
                        Exception("Empty response")
                    )

                    val root = JSONObject(body)
                    Timber.d("$TAG: /album response keys: ${root.keys().asSequence().toList()}")

                    // 尝试从 album 字段获取专辑信息
                    val albumObj: JSONObject? = root.optJSONObject("album")
                    val albumName = albumObj?.optString("name", "Unknown Album") ?: "Unknown Album"
                    val albumPic = albumObj?.optString("picUrl") ?: ""
                    val albumPublishTime = albumObj?.optLong("publishTime", 0L) ?: 0L

                    // 歌曲在 songs 数组里
                    val songsArray = root.optJSONArray("songs")
                        ?: albumObj?.optJSONArray("songs")
                        ?: return@withContext Result.success(
                            NeteaseAlbumDetail(
                                id = albumId,
                                name = albumName,
                                picUrl = albumPic,
                                publishTime = albumPublishTime
                            )
                        )

                    val songs = mutableListOf<NeteaseArtistSong>()
                    for (i in 0 until songsArray.length()) {
                        val songObj = songsArray.optJSONObject(i) ?: continue
                        val songId = songObj.optLong("id")
                        if (songId <= 0) {
                            Timber.d("$TAG: Skipping song with invalid id=$songId at index $i")
                            continue
                        }
                        val songName = songObj.optString("name", "Unknown")

                        val artists = mutableListOf<String>()
                        val artistIds = mutableListOf<Long>()
                        val arArray: JSONArray? = songObj.optJSONArray("ar")
                        if (arArray != null) {
                            for (j in 0 until arArray.length()) {
                                val ar = arArray.optJSONObject(j) ?: continue
                                ar.optString("name")?.takeIf { it.isNotBlank() }?.let { artists.add(it) }
                                val aid = ar.optLong("id")
                                if (aid > 0) artistIds.add(aid)
                            }
                        }

                        val innerAlbumObj: JSONObject? = songObj.optJSONObject("al")
                        val innerAlbumId = innerAlbumObj?.optLong("id") ?: albumId
                        val innerAlbumName = innerAlbumObj?.optString("name", albumName) ?: albumName
                        val innerAlbumPic = innerAlbumObj?.optString("picUrl") ?: albumPic

                        val duration = songObj.optLong("dt", 0L)
                        val fee = songObj.optInt("fee", 0)

                        songs.add(
                            NeteaseArtistSong(
                                id = songId,
                                name = songName,
                                artists = artists,
                                artistIds = artistIds,
                                albumId = innerAlbumId,
                                albumName = innerAlbumName,
                                albumPic = innerAlbumPic,
                                duration = duration,
                                fee = fee
                            )
                        )
                    }

                    Timber.d("$TAG: Got ${songs.size} songs for album albumId=$albumId")
                    Result.success(
                        NeteaseAlbumDetail(
                            id = albumId,
                            name = albumName,
                            picUrl = albumPic,
                            publishTime = albumPublishTime,
                            songs = songs
                        )
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: fetchAlbumDetail failed for albumId=$albumId")
                Result.failure(t)
            }
        }
    }

    /**
     * 获取歌手基本信息（名字、头像）
     * 优先使用 /artist/detail?id={artistId} 接口获取歌手信息，
     * 若失败则回退到从 /artist/songs 的响应中提取 artist 字段
     */
    suspend fun fetchArtistInfo(artistId: Long, cookie: String? = null): Result<NeteaseArtistDetail> {
        if (artistId <= 0) return Result.failure(Exception("Invalid artistId"))
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                Timber.d("$TAG: Fetching artist info for artistId=$artistId")

                // 优先尝试 /artist/detail 接口（更专业，信息更丰富）
                val detailUrl = "$BASE_URL/artist/detail?id=$artistId&timestamp=$timestamp"
                val requestBuilder = Request.Builder()
                    .url(detailUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                if (!cookie.isNullOrBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                }
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JSONObject(body)
                            if (root.optInt("code", 200) == 200) {
                                val dataObj = root.optJSONObject("data")
                                val artistObj = dataObj?.optJSONObject("artist")
                                    ?: root.optJSONObject("artist")
                                fun normalizeUrl(raw: String): String {
                                    return when {
                                        raw.startsWith("//") -> "https:$raw"
                                        raw.startsWith("/") -> "https://music.163.com$raw"
                                        raw.isNotBlank() -> raw
                                        else -> ""
                                    }
                                }

                                if (artistObj != null) {
                                    val name = artistObj.optString("name", "Unknown Artist")
                                    val userObj = dataObj?.optJSONObject("user")

                                    // 头像：先从 artist 查找，再从 user 查找
                                    val picUrl = sequenceOf(
                                        artistObj.optString("picUrl"),
                                        artistObj.optString("img1v1Url"),
                                        artistObj.optString("avatarUrl"),
                                        userObj?.optString("avatarUrl") ?: "",
                                        userObj?.optString("img1v1Url") ?: "",
                                        userObj?.optJSONObject("avatarDetail")?.optString("userImageUrl") ?: ""
                                    ).firstOrNull { it.isNotBlank() }
                                        ?.let { normalizeUrl(it) }
                                        ?: ""

                                    // 背景图
                                    val backgroundUrl = sequenceOf(
                                        artistObj.optString("cover"),
                                        dataObj?.optString("cover") ?: "",
                                        artistObj.optString("backgroundUrl"),
                                        artistObj.optString("backgroundPicUrl")
                                    ).firstOrNull { it.isNotBlank() }
                                        ?.let { normalizeUrl(it) }
                                        ?: ""

                                    // 认证信息
                                    val identitiesList = mutableListOf<String>()
                                    dataObj?.optJSONArray("identities")?.let { identArr ->
                                        for (i in 0 until identArr.length()) {
                                            val ident = identArr.optJSONObject(i)
                                            ident?.optString("imageUrl")?.takeIf { it.isNotBlank() }?.let {
                                                identitiesList.add(normalizeUrl(it))
                                            }
                                        }
                                    }
                                    val identifyTag = artistObj.optString("identifyTag")
                                        ?.takeIf { it.isNotBlank() } ?: ""

                                    // 个人简介
                                    val briefDesc = artistObj.optString("briefDesc")
                                        ?: artistObj.optString("brief")?.takeIf { it.isNotBlank() } ?: ""

                                    // 别名 / 标签
                                    val aliasList = mutableListOf<String>()
                                    artistObj.optJSONArray("alias")?.let { arr ->
                                        for (i in 0 until arr.length()) {
                                            val alias = arr.optString(i)
                                            if (alias.isNotBlank()) aliasList.add(alias)
                                        }
                                    }

                                    // 专家标签
                                    val tagsList = mutableListOf<String>()
                                    dataObj?.optJSONArray("secondaryExpertTags")?.let { arr ->
                                        for (i in 0 until arr.length()) {
                                            val tag = arr.optJSONObject(i)
                                            tag?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                                                tagsList.add(it)
                                            }
                                        }
                                    }

                                    val detail = NeteaseArtistDetail(
                                        id = artistId,
                                        name = name,
                                        avatarUrl = picUrl,
                                        backgroundUrl = backgroundUrl,
                                        identifyTag = identifyTag,
                                        identityImages = identitiesList,
                                        briefDesc = briefDesc,
                                        alias = aliasList,
                                        tags = tagsList
                                    )
                                    Timber.d("$TAG: Got artist detail: name=$name avatar=$picUrl bg=$backgroundUrl identities=${identitiesList.size}")
                                    return@withContext Result.success(detail)
                                }
                            }
                        }
                    }
                }

                // 回退：使用 /artist/songs 接口
                Timber.d("$TAG: /artist/detail failed, falling back to /artist/songs")
                val fallbackUrl = "$BASE_URL/artist/songs?id=$artistId&limit=1&timestamp=$timestamp"
                val fallbackRequestBuilder = Request.Builder()
                    .url(fallbackUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; PixelPlayer) AppleWebKit/537.36")
                if (!cookie.isNullOrBlank()) {
                    fallbackRequestBuilder.addHeader("Cookie", cookie)
                }
                val fallbackRequest = fallbackRequestBuilder.build()

                client.newCall(fallbackRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: ${response.message}")
                        )
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(
                        Exception("Empty response")
                    )

                    val root = JSONObject(body)
                    val code = root.optInt("code", 200)
                    if (code != 200) {
                        return@withContext Result.failure(Exception("API error: code=$code"))
                    }

                    // 尝试从 artist 字段获取，若不存在则从第一首歌的 ar 数组提取
                    val artistObj = root.optJSONObject("artist")
                        ?: root.optJSONObject("data")?.optJSONObject("artist")

                    fun normalizeUrlFallback(raw: String): String {
                        return when {
                            raw.startsWith("//") -> "https:$raw"
                            raw.startsWith("/") -> "https://music.163.com$raw"
                            raw.isNotBlank() -> raw
                            else -> ""
                        }
                    }

                    if (artistObj != null) {
                        val name = artistObj.optString("name", "Unknown Artist")
                        val picUrl = sequenceOf(
                            artistObj.optString("picUrl"),
                            artistObj.optString("img1v1Url"),
                            artistObj.optString("avatarUrl")
                        ).firstOrNull { it.isNotBlank() }
                            ?.let { normalizeUrlFallback(it) }
                            ?: ""
                        Result.success(
                            NeteaseArtistDetail(
                                id = artistId,
                                name = name,
                                avatarUrl = picUrl
                            )
                        )
                    } else {
                        // 回退：从 songs 数组的第一首歌曲的 ar 字段提取歌手名
                        val songsArray = root.optJSONArray("songs")
                        if (songsArray != null && songsArray.length() > 0) {
                            val firstSong = songsArray.optJSONObject(0)
                            val arArray = firstSong?.optJSONArray("ar")
                            val arName = if (arArray != null && arArray.length() > 0) {
                                arArray.optJSONObject(0)?.optString("name", "") ?: ""
                            } else ""
                            Result.success(
                                NeteaseArtistDetail(
                                    id = artistId,
                                    name = arName,
                                    avatarUrl = ""
                                )
                            )
                        } else {
                            Result.failure(Exception("No artist info in response"))
                        }
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: fetchArtistInfo failed for artistId=$artistId")
                Result.failure(t)
            }
        }
    }

    /**
     * 通过网易云歌曲 ID 获取主歌手 ID
     * 调用 /song/detail 接口，从 ar 数组的第一个元素中提取 id
     */
    suspend fun fetchNeteaseArtistId(neteaseSongId: Long, cookie: String? = null): Result<Long> {
        if (neteaseSongId <= 0) return Result.failure(Exception("Invalid neteaseSongId"))
        return withContext(Dispatchers.IO) {
            try {
                val detailsResult = fetchSongDetails(listOf(neteaseSongId), cookie)
                detailsResult.mapCatching { details ->
                    val detail = details.firstOrNull()
                        ?: throw Exception("No song detail found for neteaseSongId=$neteaseSongId")
                    detail.artistIds.firstOrNull()
                        ?: throw Exception("No artist id found in song detail")
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: fetchNeteaseArtistId failed for neteaseSongId=$neteaseSongId")
                Result.failure(t)
            }
        }
    }
}

/**
 * 私人 FM 歌曲详情数据模型
 */
data class PersonalFmSongDetail(
    val id: Long,
    val name: String,
    val artists: List<String>,
    val artistIds: List<Long> = emptyList(),
    val albumName: String,
    val albumPic: String,
    val duration: Long,
    val fee: Int = 0
) {
    val artistString: String
        get() = if (artists.isNotEmpty()) artists.joinToString(", ") else "Unknown Artist"

    /** 是否为 VIP/付费歌曲（fee=1 或 fee=8 都需要会员/付费） */
    val isVip: Boolean
        get() = fee == 1 || fee == 8
}

/**
 * 网易云歌手歌曲条目（来自 /artist/songs 接口）
 */
data class NeteaseArtistSong(
    val id: Long,
    val name: String,
    val artists: List<String>,
    val artistIds: List<Long> = emptyList(),
    val albumId: Long,
    val albumName: String,
    val albumPic: String,
    val duration: Long,
    val fee: Int = 0
)

/**
 * 网易云歌手详情（来自 /artist/detail 接口）
 * 包含背景图、头像、认证信息、个人简介、标签等
 */
data class NeteaseArtistDetail(
    val id: Long,
    val name: String,
    val avatarUrl: String = "",
    val backgroundUrl: String = "",
    val identifyTag: String = "",
    val identityImages: List<String> = emptyList(),
    val briefDesc: String = "",
    val alias: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

/**
 * 网易云歌手专辑条目（来自 /artist/album 接口）
 */
data class NeteaseArtistAlbum(
    val id: Long,
    val name: String,
    val picUrl: String,
    val publishTime: Long = 0L,
    val size: Int = 0
)

/**
 * 网易云专辑详情（来自 /album 接口）
 * 包含专辑信息和歌曲列表
 */
data class NeteaseAlbumDetail(
    val id: Long,
    val name: String,
    val picUrl: String = "",
    val publishTime: Long = 0L,
    val songs: List<NeteaseArtistSong> = emptyList()
)
