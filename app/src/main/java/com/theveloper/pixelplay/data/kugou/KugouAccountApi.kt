package com.theveloper.pixelplay.data.kugou

import android.net.Uri
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber

/** 酷狗用户信息（昵称/头像）。 */
data class KugouUserInfo(
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
)

/** 酷狗手机号登录结果；[accounts] 非空表示一号多账号，需要用户选一个再登录一次。 */
data class KugouPhoneLoginResult(
    val token: String?,
    val userId: String?,
    val vipToken: String?,
    val vipType: String?,
    val accounts: List<KugouUserInfo> = emptyList(),
)

/** 酷狗歌单（列表用）。 */
data class KugouPlaylistBrief(
    val listId: String,
    val name: String,
    val songCount: Int,
    val coverUrl: String?,
    val isDefaultLiked: Boolean,
)

/** 酷狗歌单里的一首歌（同步用）。 */
data class KugouSongBrief(
    val hash: String,
    val name: String,
    val singer: String,
    val albumId: String,
    val albumAudioId: String,
    val fileId: String?,
    val durationMs: Long,
)

/** 酷狗登录二维码：key 用于轮询，[contentUrl] 是二维码要编码的内容（本地渲染），[imageUrl] 是接口图（可选兜底）。 */
data class KugouQrCode(
    val key: String,
    val contentUrl: String,
    val imageUrl: String?,
)

/**
 * 酷狗「需要签名」的账号接口：用户信息 / 手机号登录 / 歌单同步。
 *
 * 移植自 md3Music 的 Rust 实现（`login.rs` / `user.rs` / `playlist.rs` + `crypto.rs`），
 * 加密规则逐条对齐：
 * - `md5` 一律小写 hex；
 * - AES 用 `AES/CBC/PKCS5Padding`，**key/iv 是 ASCII 字符串的 UTF-8 字节**（不是 hex 解码）；
 *   登录 `params` 用 16 位随机串派生 md5(32 字符作 key + 后 16 字符作 iv，AES-256)，密文 hex；
 * - `pk` 是**裸 RSA（无 padding）**：明文右补 0 到 128 字节 → modPow → 左补 0 到 128 字节 → 大写 hex；
 * - `p`（歌单/设备流）是 `RSA/ECB/PKCS1Padding`，hex；
 * - 请求签名 `signature = md5(ROUTE + 参数按键排序拼接 + body + ROUTE)`（拼接口无分隔符），写回 query。
 */
@Singleton
class KugouAccountApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    // ─── 对外：用户信息 ─────────────────────────────────────────────────────

    /** `/v3/get_my_info`：拿昵称与头像（原来只有 userid）。 */
    suspend fun fetchUserInfo(
        token: String,
        userId: String,
        device: KugouDeviceIdentity,
    ): Result<KugouUserInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val clientTime = nowSeconds()
            val pk = rawRsaUpper("""{"token":"$token","clienttime":$clientTime}""")
            val body = JSONObject()
                .put("visit_time", clientTime)
                .put("usertype", 1)
                .put("p", pk)
                .put("userid", userId.toLongOrNull() ?: 0L)
            val root = signedPost(
                path = "/v3/get_my_info",
                query = mapOf("plat" to "1"),
                body = body,
                token = token,
                userId = userId,
                device = device,
                xRouter = "usercenter.kugou.com",
            )
            val data = root.optJSONObject("data") ?: root
            val nickname = firstNonBlank(
                data.optString("nickname"),
                data.optString("username"),
                data.optString("name"),
            ).orEmpty()
            val avatar = firstNonBlank(
                data.optString("avatar"),
                data.optString("img"),
                data.optString("pic"),
            )?.replace("http://", "https://")
            KugouUserInfo(
                userId = firstNonBlank(data.optString("userid"), userId).orEmpty(),
                nickname = nickname.ifBlank { "酷狗用户 $userId" },
                avatarUrl = avatar,
            )
        }.onFailure { Timber.w(it, "KugouAccountApi: fetchUserInfo failed") }
    }

    // ─── 对外：扫码登录（web 签名）─────────────────────────────────────────

    /**
     * `/v2/qrcode`：申请登录二维码。
     *
     * ⚡ 这个接口**必须带 web 签名**（ROUTE_WEB），且签名覆盖 dfid/mid/uuid/appid/
     *    clientver/clienttime + 业务参数 —— 少了签名酷狗直接拒绝（这是原来扫码登不上的原因）。
     */
    suspend fun createQrCode(device: KugouDeviceIdentity): Result<KugouQrCode> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = webSignedGet(
                    path = "/v2/qrcode",
                    baseUrl = LOGIN_WEB_BASE,
                    query = mapOf(
                        "appid" to QR_APP_ID,
                        "type" to 1,
                        "plat" to 4,
                        "qrcode_txt" to QRCODE_TXT,
                        "srcappid" to SRC_APP_ID,
                    ),
                    device = device,
                )
                val data = root.optJSONObject("data") ?: error("二维码接口返回异常")
                val key = data.optString("qrcode")
                if (key.isBlank()) error("二维码接口没有返回 key")
                val img = firstNonBlank(
                    data.optString("qrcode_img"),
                    data.optString("qrcode_img_url"),
                )?.let { raw -> if (raw.startsWith("data:")) raw else "data:image/png;base64,$raw" }
                // 参考实现（md3Music / MeloX）都用这个内容 URL 本地渲染二维码，
                // 接口图只是兜底 —— 有的版本不返回 qrcode_img。
                KugouQrCode(
                    key = key,
                    contentUrl = "$QR_CONTENT_BASE$key",
                    imageUrl = img,
                )
            }.onFailure { Timber.w(it, "KugouAccountApi: createQrCode failed") }
        }

    /** `/v2/get_userinfo_qrcode`：轮询扫码状态，返回 data（status/token/userid/vip_token…）。 */
    suspend fun checkQrCode(
        key: String,
        device: KugouDeviceIdentity,
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val root = webSignedGet(
                path = "/v2/get_userinfo_qrcode",
                baseUrl = LOGIN_WEB_BASE,
                query = mapOf(
                    "plat" to 4,
                    "appid" to APP_ID,
                    "srcappid" to SRC_APP_ID,
                    "qrcode" to key,
                ),
                device = device,
            )
            root.optJSONObject("data") ?: JSONObject()
        }.onFailure { Timber.w(it, "KugouAccountApi: checkQrCode failed") }
    }

    // ─── 对外：手机号登录 ───────────────────────────────────────────────────

    /** `/v7/send_mobile_code`：发送短信验证码（android 签名 + 默认参数）。 */
    suspend fun sendSmsCode(
        mobile: String,
        device: KugouDeviceIdentity,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("businessid", 5)
                .put("mobile", mobile)
                .put("plat", 3)
            val root = signedPost(
                path = "/v7/send_mobile_code",
                baseUrl = LOGIN_SMS_BASE,
                query = emptyMap(),
                body = body,
                token = null,
                userId = null,
                device = device,
                // 参考实现：该接口清空 cookie 只留 mid，dfid 因此回落到 "-"
                dfidOverride = "-",
                cookieOverride = "mid=${device.mid}",
            )
            // 成功判定放宽：status==1 或 error_code==0（不同客户端版本返回字段不一致）
            val status = root.optInt("status", -1)
            val errorCode = root.optInt("error_code", -1)
            if (status != 1 && errorCode != 0) {
                error(
                    firstNonBlank(
                        root.optString("error"),
                        root.optString("error_msg"),
                        root.optString("msg"),
                    ) ?: "验证码发送失败"
                )
            }
            true
        }.onFailure { Timber.w(it, "KugouAccountApi: sendSmsCode failed") }
    }

    /**
     * `/login/cellphone`：手机号 + 验证码登录。
     * 一号多账号时返回 [KugouPhoneLoginResult.accounts]，带上 `userId` 再调一次即可登录指定账号。
     */
    suspend fun loginByPhone(
        mobile: String,
        code: String,
        device: KugouDeviceIdentity,
        userId: String? = null,
    ): Result<KugouPhoneLoginResult> = withContext(Dispatchers.IO) {
        runCatching {
            val clientTimeMs = System.currentTimeMillis()
            val tempKey = randomLower16()
            val params = aesCbcHex(
                """{"mobile":"$mobile","code":"$code"}""",
                key = md5Hex(tempKey).take(32),
                iv = md5Hex(tempKey).takeLast(16),
            )
            val pk = rawRsaUpper("""{"clienttime_ms":$clientTimeMs,"key":"$tempKey"}""")
            val maskedMobile = maskMobile(mobile)
            val t2Plain = "${device.guid}|$GUID_SALT|$MAC|${device.dev}|$clientTimeMs"
            val body = JSONObject()
                .put("plat", 1)
                .put("support_multi", 1)
                .put("t1", aesCbcHex("|$clientTimeMs", LITE_T1_KEY, LITE_T1_IV))
                .put("t2", aesCbcHex(t2Plain, LITE_T2_KEY, LITE_T2_IV))
                .put("clienttime_ms", clientTimeMs)
                .put("mobile", maskedMobile)
                .put("key", signParamsKey(clientTimeMs.toString()))
                .put("pk", pk)
                .put("params", params)
            // 多账号二次登录：userid 紧跟 params（与参考实现字段顺序一致，签名体才一致）
            if (!userId.isNullOrBlank()) body.put("userid", userId)
            body.put("dfid", device.dfid)
                .put("dev", device.dev)
                .put("gitversion", GIT_VERSION)

            val root = signedPost(
                path = "/v7/login_by_verifycode",
                baseUrl = "https://loginserviceretry.kugou.com",
                query = emptyMap(),
                body = body,
                token = null,
                userId = null,
                device = device,
                extraHeaders = mapOf("support-calm" to "1", "User-Agent" to LOGIN_UA),
            )
            val data = root.optJSONObject("data") ?: root
            // secu_params 用同一个 tempKey 解，解出对象就并进 data
            val secu = data.optString("secu_params")
            if (secu.isNotBlank()) {
                runCatching {
                    val md = md5Hex(tempKey)
                    val plain = aesCbcDecryptHex(secu, md.take(32), md.takeLast(16))
                    val decoded = JSONObject(plain)
                    decoded.keys().forEach { key -> data.put(key, decoded.get(key)) }
                }.onFailure { Timber.w(it, "KugouAccountApi: secu_params 解析失败") }
            }

            val accounts = parseMultiAccounts(data)
            KugouPhoneLoginResult(
                token = firstNonBlank(data.optString("token"), root.optString("token")),
                userId = firstNonBlank(data.optString("userid"), root.optString("userid")),
                vipToken = firstNonBlank(data.optString("vip_token"), root.optString("vip_token")),
                vipType = firstNonBlank(data.optString("vip_type"), root.optString("vip_type")),
                accounts = accounts,
            )
        }.onFailure { Timber.w(it, "KugouAccountApi: loginByPhone failed") }
    }

    // ─── 对外：歌单同步 ─────────────────────────────────────────────────────

    /** `/user/playlist`：拉账号歌单列表（分页）。 */
    suspend fun fetchPlaylists(
        token: String,
        userId: String,
        device: KugouDeviceIdentity,
        page: Int = 1,
        pageSize: Int = 30,
    ): Result<List<KugouPlaylistBrief>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("userid", userId)
                .put("token", token)
                .put("total_ver", 979)
                .put("type", 2)
                .put("page", page)
                .put("pagesize", pageSize)
            val root = signedPost(
                path = "/v7/get_all_list",
                query = mapOf("plat" to "1", "userid" to userId, "token" to token),
                body = body,
                token = token,
                userId = userId,
                device = device,
                xRouter = "cloudlist.service.kugou.com",
            )
            val info = root.optJSONObject("data")?.optJSONArray("info")
                ?: root.optJSONArray("info")
                ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until info.length()) {
                    val item = info.optJSONObject(i) ?: continue
                    val listId = firstNonBlank(
                        item.optString("listid"),
                        item.optString("list_create_listid"),
                        item.optString("global_collection_id"),
                        item.optString("gid"),
                    ) ?: continue
                    val name = item.optString("name")
                    add(
                        KugouPlaylistBrief(
                            listId = listId,
                            name = name.ifBlank { "未命名歌单" },
                            songCount = item.optInt("songcount", 0),
                            coverUrl = firstNonBlank(
                                item.optString("imgurl"),
                                item.optString("img"),
                                item.optString("pic"),
                            )
                                // ⚡ 酷狗封面 URL 带 {size} 占位符（如 stdmusic/{size}/xxx.jpg），
                                //   不替换的话加载会 404 —— 同步下来的歌单就没封面
                                ?.replace("{size}", "480")
                                ?.replace("http://", "https://"),
                            // 「我喜欢」：名字或 is_def == 2
                            isDefaultLiked = name == LIKED_PLAYLIST_NAME || item.optInt("is_def", 0) == 2,
                        )
                    )
                }
            }
        }.onFailure { Timber.w(it, "KugouAccountApi: fetchPlaylists failed") }
    }

    /** `/playlist/track/all/new`：拉某个歌单的全部歌曲（分页）。 */
    suspend fun fetchPlaylistSongs(
        token: String,
        userId: String,
        listId: String,
        device: KugouDeviceIdentity,
        page: Int = 1,
        pageSize: Int = 200,
    ): Result<List<KugouSongBrief>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("listid", listId)
                .put("userid", userId)
                .put("area_code", 1)
                .put("show_relate_goods", 0)
                .put("pagesize", pageSize)
                .put("allplatform", 1)
                .put("show_cover", 1)
                .put("type", 0)
                .put("token", token)
                .put("page", page)
            val root = signedPost(
                path = "/v4/get_list_all_file",
                query = emptyMap(),
                body = body,
                token = token,
                userId = userId,
                device = device,
                xRouter = "cloudlist.service.kugou.com",
            )
            val data = root.optJSONObject("data") ?: root
            val files = data.optJSONArray("info")
                ?: data.optJSONArray("songs")
                ?: data.optJSONArray("list")
                ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until files.length()) {
                    val item = files.optJSONObject(i) ?: continue
                    val audioInfo = item.optJSONObject("audio_info")
                    val albumInfo = item.optJSONObject("album_info")
                    val hash = firstNonBlank(
                        item.optString("hash"),
                        audioInfo?.optString("hash"),
                        item.optString("file_hash"),
                    ) ?: continue
                    add(
                        KugouSongBrief(
                            hash = hash,
                            name = firstNonBlank(
                                item.optString("filename"),
                                item.optString("songname"),
                                item.optString("name"),
                                item.optString("FileName"),
                            ).orEmpty(),
                            singer = firstNonBlank(
                                item.optString("singername"),
                                item.optString("singer_name"),
                                item.optString("author_name"),
                            ).orEmpty(),
                            albumId = firstNonBlank(
                                item.optString("album_id"),
                                albumInfo?.optString("album_id"),
                            ).orEmpty(),
                            albumAudioId = firstNonBlank(
                                item.optString("album_audio_id"),
                                item.optString("mixsongid"),
                                audioInfo?.optString("album_audio_id"),
                            ).orEmpty(),
                            fileId = firstNonBlank(
                                item.optString("fileid"),
                                item.optString("file_id"),
                            ),
                            durationMs = firstNonBlank(
                                item.optString("timelen"),
                                item.optString("duration"),
                                audioInfo?.optString("duration"),
                            )?.toLongOrNull() ?: 0L,
                        )
                    )
                }
            }
        }.onFailure { Timber.w(it, "KugouAccountApi: fetchPlaylistSongs failed") }
    }

    /** `/playlist/tracks/add`：把歌曲加入「我喜欢」（data 格式 `名称|hash|album_id|mixsongid`）。 */
    suspend fun addSongsToPlaylist(
        token: String,
        userId: String,
        listId: String,
        songs: List<KugouSongBrief>,
        device: KugouDeviceIdentity,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (songs.isEmpty()) return@runCatching true
            val resource = org.json.JSONArray()
            songs.forEach { song ->
                resource.put(
                    JSONObject()
                        .put("number", 1)
                        .put("name", song.name)
                        .put("hash", song.hash)
                        .put("size", 0)
                        .put("sort", 0)
                        .put("timelen", 0)
                        .put("bitrate", 0)
                        .put("album_id", song.albumId.toLongOrNull() ?: 0L)
                        .put("mixsongid", song.albumAudioId.toLongOrNull() ?: 0L)
                )
            }
            val clientTime = nowSeconds()
            val body = JSONObject()
                .put("listid", listId)
                .put("userid", userId.toLongOrNull() ?: 0L)
                .put("token", token)
                .put("list_ver", 0)
                .put("type", 0)
                .put("slow_upload", 1)
                .put("scene", "false;null")
                .put("data", resource)
            val root = signedPost(
                path = "/cloudlist.service/v6/add_song",
                query = mapOf(
                    "last_time" to clientTime.toString(),
                    "last_area" to "gztx",
                    "userid" to userId,
                    "token" to token,
                ),
                body = body,
                token = token,
                userId = userId,
                device = device,
            )
            root.optInt("status", 1) == 1
        }.onFailure { Timber.w(it, "KugouAccountApi: addSongsToPlaylist failed") }
    }

    /** `/playlist/tracks/del`：从歌单移除（fileid 优先，缺失时用 hash）。 */
    suspend fun removeSongsFromPlaylist(
        token: String,
        userId: String,
        listId: String,
        songs: List<KugouSongBrief>,
        device: KugouDeviceIdentity,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (songs.isEmpty()) return@runCatching true
            val resource = org.json.JSONArray()
            songs.forEach { song ->
                val fileId = song.fileId?.toLongOrNull()
                resource.put(
                    if (fileId != null && fileId > 0L) {
                        JSONObject().put("fileid", fileId)
                    } else {
                        JSONObject().put("fileid", 0).put("hash", song.hash)
                    }
                )
            }
            val clientTime = nowSeconds()
            val body = JSONObject()
                .put("listid", listId)
                .put("userid", userId.toLongOrNull() ?: 0L)
                .put("data", resource)
                .put("type", 0)
                .put("token", token)
                .put("list_ver", 0)
            val root = signedPost(
                path = "/cloudlist.service/v4/delete_songs",
                query = mapOf(
                    "last_time" to clientTime.toString(),
                    "last_area" to "gztx",
                    "userid" to userId,
                    "token" to token,
                ),
                body = body,
                token = token,
                userId = userId,
                device = device,
            )
            root.optInt("status", 1) == 1
        }.onFailure { Timber.w(it, "KugouAccountApi: removeSongsFromPlaylist failed") }
    }

    // ─── 签名请求 ───────────────────────────────────────────────────────────

    private fun signedPost(
        path: String,
        baseUrl: String = GATEWAY,
        query: Map<String, Any?>,
        body: JSONObject?,
        token: String?,
        userId: String?,
        device: KugouDeviceIdentity,
        xRouter: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        /** 覆盖 dfid 参数（验证码接口要用 "-"）；null 用设备 dfid */
        dfidOverride: String? = null,
        /** 覆盖 Cookie（验证码接口只带 mid）；null 用默认 token/userid/mid */
        cookieOverride: String? = null,
    ): JSONObject {
        val clientTime = nowSeconds().toString()
        // 参与签名的 query：默认参数 + 模块参数（signature 自身不参与）
        val allParams = LinkedHashMap<String, String>()
        allParams["dfid"] = dfidOverride ?: device.dfid
        allParams["mid"] = device.mid
        allParams["uuid"] = "-"
        allParams["appid"] = APP_ID
        allParams["clientver"] = CLIENT_VER
        allParams["clienttime"] = clientTime
        if (!token.isNullOrBlank()) allParams["token"] = token
        if (!userId.isNullOrBlank() && userId != "0") allParams["userid"] = userId
        query.forEach { (k, v) -> allParams[k] = v?.toString().orEmpty() }

        val bodyText = body?.toString().orEmpty()
        val signature = requestSignature(allParams, bodyText)

        val urlBuilder = Uri.parse("$baseUrl$path").buildUpon()
        allParams.forEach { (k, v) -> urlBuilder.appendQueryParameter(k, v) }
        urlBuilder.appendQueryParameter("signature", signature)

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build().toString())
            .header("User-Agent", DEFAULT_UA)
            .header("dfid", allParams["dfid"].orEmpty())
            .header("clienttime", clientTime)
            .header("mid", device.mid)
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
        val cookie = cookieOverride ?: buildString {
            append("mid=").append(device.mid)
            if (!token.isNullOrBlank()) append("; token=").append(token)
            if (!userId.isNullOrBlank()) append("; userid=").append(userId)
        }
        requestBuilder.header("Cookie", cookie)
        if (xRouter != null) requestBuilder.header("x-router", xRouter)
        extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
        if (body != null) {
            requestBuilder.post(bodyText.toRequestBody(JSON_MEDIA))
        } else {
            requestBuilder.get()
        }
        return JSONObject(execute(requestBuilder.build()))
    }

    /**
     * web 签名 GET（二维码两个接口用）：
     * `signature = md5(ROUTE_WEB + 排序参数拼接 + ROUTE_WEB)`（GET 无 body）。
     */
    private fun webSignedGet(
        path: String,
        baseUrl: String,
        query: Map<String, Any?>,
        device: KugouDeviceIdentity,
    ): JSONObject {
        val clientTime = nowSeconds().toString()
        val allParams = LinkedHashMap<String, String>()
        allParams["dfid"] = device.dfid
        allParams["mid"] = device.mid
        allParams["uuid"] = "-"
        allParams["appid"] = APP_ID
        allParams["clientver"] = CLIENT_VER
        allParams["clienttime"] = clientTime
        query.forEach { (k, v) -> allParams[k] = v?.toString().orEmpty() }

        val joined = allParams.entries
            .sortedBy { it.key }
            .joinToString(separator = "") { (k, v) -> "$k=$v" }
        val signature = md5Hex(ROUTE_WEB + joined + ROUTE_WEB)

        val urlBuilder = Uri.parse("$baseUrl$path").buildUpon()
        allParams.forEach { (k, v) -> urlBuilder.appendQueryParameter(k, v) }
        urlBuilder.appendQueryParameter("signature", signature)

        val request = Request.Builder()
            .url(urlBuilder.build().toString())
            .header("User-Agent", DEFAULT_UA)
            .header("dfid", device.dfid)
            .header("clienttime", clientTime)
            .header("mid", device.mid)
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .header("Cookie", "mid=${device.mid}")
            .get()
            .build()
        return JSONObject(execute(request))
    }

    private fun execute(request: Request): String {
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return text
        }
    }

    /** `signature = md5(ROUTE + 参数按键排序拼接 + body + ROUTE)`，拼接口无分隔符。 */
    private fun requestSignature(params: Map<String, String>, body: String): String {
        val joined = params.entries
            .sortedBy { it.key }
            .joinToString(separator = "") { (k, v) -> "$k=$v" }
        return md5Hex(ROUTE + joined + body + ROUTE)
    }

    /** `key = md5(appid + salt + clientver + time)`。 */
    private fun signParamsKey(time: String): String =
        md5Hex("$APP_ID$SIGN_PARAMS_SALT$CLIENT_VER$time")

    // ─── 加密原语 ───────────────────────────────────────────────────────────

    private fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** AES/CBC/PKCS5，key/iv 按 ASCII 字节使用，输出小写 hex。 */
    private fun aesCbcHex(data: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(), "AES"),
            IvParameterSpec(iv.toByteArray()),
        )
        return cipher.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun aesCbcDecryptHex(hex: String, key: String, iv: String): String {
        val bytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.toByteArray(), "AES"),
            IvParameterSpec(iv.toByteArray()),
        )
        return String(cipher.doFinal(bytes))
    }

    /**
     * 裸 RSA（无 padding，等价 CryptoJS 的 modPow）：
     * 明文右补 0 到 128 字节 → modPow → 左补 0 到 128 字节 → **大写** hex。
     */
    private fun rawRsaUpper(plain: String): String {
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(litePublicKeyDer()))
                as java.security.interfaces.RSAPublicKey
        val modulus: BigInteger = publicKey.modulus
        val exponent: BigInteger = publicKey.publicExponent
        val keyLength = (modulus.bitLength() + 7) / 8
        val input = plain.toByteArray()
        val padded = ByteArray(maxOf(keyLength, input.size))
        input.copyInto(padded)
        val value = BigInteger(1, padded).modPow(exponent, modulus).toByteArray()
        val output = ByteArray(keyLength)
        val start = maxOf(0, keyLength - value.size)
        val trimmed = if (value.size > keyLength) value.copyOfRange(value.size - keyLength, value.size) else value
        trimmed.copyInto(output, start)
        return output.joinToString("") { "%02x".format(it) }.uppercase()
    }

    /** PEM → DER（去掉头尾与换行）。 */
    private fun litePublicKeyDer(): ByteArray {
        val base64 = LITE_PUBLIC_KEY
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .trim()
        return android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
    }

    private fun randomLower16(): String =
        (1..16).map { ALPHANUM.random() }.joinToString("").lowercase()

    private fun maskMobile(mobile: String): String {
        if (mobile.length <= 2) return "$mobile*****"
        val head = mobile.take(2)
        val tail = if (mobile.length > 10) mobile[10].toString() else ""
        return "$head*****$tail"
    }

    private fun parseMultiAccounts(data: JSONObject): List<KugouUserInfo> {
        val array = data.optJSONArray("info_list")
            ?: data.optJSONArray("user_list")
            ?: data.optJSONArray("userList")
            ?: data.optJSONArray("lists")
            ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = firstNonBlank(
                    item.optString("userid"),
                    item.optString("userId"),
                    item.optString("id"),
                    item.optString("user_id"),
                ) ?: continue
                add(
                    KugouUserInfo(
                        userId = id,
                        nickname = firstNonBlank(
                            item.optString("nickname"),
                            item.optString("user_name"),
                            item.optString("name"),
                        ).orEmpty(),
                        avatarUrl = firstNonBlank(
                            item.optString("avatar"),
                            item.optString("pic"),
                            item.optString("img"),
                            item.optString("imgurl"),
                        )?.replace("http://", "https://"),
                    )
                )
            }
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    private fun nowSeconds(): Long = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

    companion object {
        private const val GATEWAY = "https://gateway.kugou.com"
        private const val LOGIN_WEB_BASE = "https://login-user.kugou.com"
        private const val LOGIN_SMS_BASE = "http://login.user.kugou.com"
        private const val APP_ID = "3116"
        /** 二维码申请接口用 appid=1001（参考实现 handle_qr_key） */
        private const val QR_APP_ID = "1001"
        private const val SRC_APP_ID = "2919"
        private const val CLIENT_VER = "11440"
        private const val MAC = "02:00:00:00:00:00"
        private const val GUID_SALT = "0f607264fc6318a92b9e13c65db7cd3c"
        private const val GIT_VERSION = "5f0b7c4"
        private const val ROUTE = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        /** web 签名用的 route（二维码接口） */
        private const val ROUTE_WEB = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
        private const val SIGN_PARAMS_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        private const val LITE_T1_KEY = "5e4ef500e9597fe004bd09a46d8add98"
        private const val LITE_T1_IV = "04bd09a46d8add98"
        private const val LITE_T2_KEY = "fd14b35e3f81af3817a20ae7adae7020"
        private const val LITE_T2_IV = "17a20ae7adae7020"
        private const val LIKED_PLAYLIST_NAME = "我喜欢"
        private const val ALPHANUM = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val QRCODE_TXT =
            "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=3116&"
        /** 二维码内容 URL（酷狗 App 扫这个） */
        private const val QR_CONTENT_BASE =
            "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode="
        private const val DEFAULT_UA =
            "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
        private const val LOGIN_UA = "Android16-1070-11440-130-0-LOGIN-wifi"
        private val JSON_MEDIA = "application/json;charset=utf-8".toMediaType()

        /** 酷狗 Lite 版 RSA 公钥（1024-bit，与参考项目一致）。 */
        private const val LITE_PUBLIC_KEY =
            "-----BEGIN PUBLIC KEY-----\n" +
                "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB\n" +
                "-----END PUBLIC KEY-----"

        /**
         * 酷狗 `mid`：`md5(guid)` 的**十进制大整数串**（参考项目 `calculateMid`）。
         * 注意不是 md5 hex —— 传 hex 会被服务端判定为非法设备。
         */
        fun md5Decimal(text: String): String =
            java.math.BigInteger(md5HexStatic(text), 16).toString()

        private fun md5HexStatic(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

/** 酷狗设备标识（参考项目 `device_info.json`：注册一次后长期复用）。 */
data class KugouDeviceIdentity(
    val dfid: String,
    val guid: String,
    val dev: String,
    /** `md5(guid)` 的十进制串（所有签名请求的 mid 参数/头都用它） */
    val mid: String,
)
