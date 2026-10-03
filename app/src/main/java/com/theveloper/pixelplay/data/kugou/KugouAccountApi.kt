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
    /** 歌曲封面（`sizable_cover` / `img` / `trans_param.union_cover` 等，已替换 {size} 并升级 https）。 */
    val coverUrl: String? = null,
    /** 专辑名（媒体库按专辑归类用）。 */
    val albumName: String? = null,
    /** 数字 songid（酷狗 songmid 本义；落雪 kg 音源解析播放链接用）。 */
    val songId: String? = null,
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

    // ─── 对外：设备注册 ─────────────────────────────────────────────────────

    /**
     * `/risk/v2/r_register_dev`：注册设备，返回服务端下发的 dfid。
     *
     * ⚡ 本机随机生成的 dfid 未在酷狗注册过，长音频章节列表 / `/v5/url` 会被上游拒绝
     * （`error_code 20028`）—— 参照项目能播、我们放不出来就是这个差异。它靠这个接口
     * 拿服务端 dfid 并落盘复用（只在首次安装注册一次，重复注册会被风控当成一堆设备）。
     *
     * 请求体是 AES-128-CBC（key/iv 由 6 位随机串的 md5 前后各 16 字符派生）加密后的 base64，
     * `p` 参数是 RSA PKCS#1 v1.5 加密的 `{aes, uid, token}` hex；响应体是 AES 密文，需解密后取 `data.dfid`。
     */
    suspend fun registerDevice(
        device: KugouDeviceIdentity,
        userId: String?,
        token: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val deviceJson = JSONObject().apply {
                put("availableRamSize", 4_983_533_568L)
                put("availableRomSize", 48_114_719L)
                put("availableSDSize", 48_114_717L)
                put("basebandVer", "")
                put("batteryLevel", 100)
                put("batteryStatus", 3)
                put("brand", android.os.Build.BRAND.ifBlank { "Xiaomi" })
                put("buildSerial", "unknown")
                put("device", android.os.Build.DEVICE.ifBlank { "marble" })
                put("manufacturer", android.os.Build.MANUFACTURER.ifBlank { "Xiaomi" })
                put("imsi", "")
                put("accelerometer", false)
                put("accelerometerValue", "")
                put("gravity", false)
                put("gravityValue", "")
                put("gyroscope", false)
                put("gyroscopeValue", "")
                put("light", false)
                put("lightValue", "")
                put("magnetic", false)
                put("magneticValue", "")
                put("orientation", false)
                put("orientationValue", "")
                put("pressure", false)
                put("pressureValue", "")
                put("step_counter", false)
                put("step_counterValue", "")
                put("temperature", false)
                put("temperatureValue", "")
                put("imei", device.guid)
                put("uuid", device.guid)
            }.toString()

            val aesKey = randomLower(6)
            val md = md5Hex(aesKey)
            val encKey = md.substring(0, 16)
            val iv = md.substring(16, 32)
            val aesStr = aesCbcBase64(deviceJson, encKey, iv)
            val p = rsaPkcs1Hex(
                """{"aes":"$aesKey","uid":${userId?.toLongOrNull() ?: 0L},"token":"${token.orEmpty()}"}"""
            )

            val clientTime = nowSeconds().toString()
            val allParams = LinkedHashMap<String, String>()
            allParams["dfid"] = device.dfid
            allParams["mid"] = device.mid
            allParams["uuid"] = "-"
            allParams["appid"] = APP_ID
            allParams["clientver"] = CLIENT_VER
            allParams["clienttime"] = clientTime
            if (!token.isNullOrBlank()) allParams["token"] = token
            if (!userId.isNullOrBlank() && userId != "0") allParams["userid"] = userId
            allParams["part"] = "1"
            allParams["platid"] = "1"
            allParams["p"] = p

            // 签名覆盖的 body 是 base64 密文本身（string_body）
            val signature = requestSignature(allParams, aesStr)
            val urlBuilder = Uri.parse("$REGISTER_BASE/risk/v2/r_register_dev").buildUpon()
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
                .header("Content-Type", "text/plain; charset=utf-8")
                .header(
                    "Cookie",
                    buildString {
                        append("mid=").append(device.mid)
                        if (!token.isNullOrBlank()) append("; token=").append(token)
                        if (!userId.isNullOrBlank()) append("; userid=").append(userId)
                    },
                )
                .post(aesStr.toRequestBody(TEXT_MEDIA))
                .build()

            val raw = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.bytes() ?: ByteArray(0)
            }
            // 响应是 AES 密文（arraybuffer）；个别情况下直接回明文 JSON，两种都兜住
            val text = runCatching { aesCbcDecrypt(raw, encKey, iv) }
                .getOrElse { String(raw) }
            val root = JSONObject(text)
            val dfid = root.optJSONObject("data")?.optString("dfid")
            require(!dfid.isNullOrBlank()) { "注册设备未返回 dfid" }
            dfid
        }.onFailure { Timber.w(it, "KugouAccountApi: registerDevice failed") }
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
                    val name = firstNonBlank(
                        item.optString("specialname"),
                        item.optString("name"),
                    ).orEmpty()
                    add(
                        KugouPlaylistBrief(
                            listId = listId,
                            name = name.ifBlank { "未命名歌单" },
                            songCount = item.optInt("songcount", 0),
                            // 封面候选与参照项目 KugouPlaylistBrief 一致：sizable_cover 是部分接口
                            // 实际返回的字段；带 {size} 占位要替换、http 升 https，否则媒体库没封面。
                            coverUrl = normalizeCoverUrl(
                                firstNonBlank(
                                    item.optString("sizable_cover"),
                                    item.optString("imgurl"),
                                    item.optString("img"),
                                    item.optString("pic"),
                                    item.optString("cover_url"),
                                    item.optString("cover"),
                                    item.optJSONObject("trans_param")?.optString("union_cover"),
                                )
                            ),
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
                    parsePlaylistSong(item)?.let(::add)
                }
            }
        }.onFailure { Timber.w(it, "KugouAccountApi: fetchPlaylistSongs failed") }
    }

    /**
     * 解析歌单里的一首歌。
     *
     * ⚡ 对齐参照项目 `KugouSongDetail.fromJson`（此前同步下来没封面 / 没歌名就是这里漏了）：
     * - 先展开 `album_info / albuminfo / audio_info / song_info / base` 嵌套结构（顶层优先），
     *   歌名 / 封面 / 时长 / hash 都可能只存在嵌套里；
     * - 歌名优先 `songname` 这类干净字段；`filename` / `audio_name` 是「歌手 - 歌名.mp3」
     *   的兜底形态，取到后要拆掉歌手前缀和扩展名；
     * - 封面字段很多（`sizable_cover` / `img` / `trans_param.union_cover`…），
     *   带 `{size}` 占位要替换、http 升 https。
     */
    private fun parsePlaylistSong(rawItem: JSONObject): KugouSongBrief? {
        val item = mergeNestedSongFields(rawItem)
        val hash = item.optFirstNonBlank(
            "hash", "FileHash", "Hash128", "SQFileHash", "HQFileHash", "sd_hash", "file_hash",
        ) ?: return null

        // 歌名：干净字段优先；只有文件名类字段时按「歌手 - 歌名」拆分。
        // ⚡ 扩展名（.mp3 / .flac…）无条件剥掉：酷狗部分接口的 songname / name 字段
        //   本身就带文件名后缀，同步到媒体库后会显示成「歌名.mp3」。
        val cleanName = item.optFirstNonBlank("songname", "SongName", "name", "ori_audio_name")
        val fileishName = item.optFirstNonBlank("FileName", "filename", "audio_name")
        var name = cleanName ?: fileishName.orEmpty()
        var singer = item.optFirstNonBlank(
            "singername", "SingerName", "artist_name", "author_name", "singer_name",
        ).orEmpty().ifBlank { parseSingerNames(item) }
        if (name.isNotBlank()) {
            name = splitMergedName(name) { prefix -> if (singer.isBlank()) singer = prefix }
            name = name.stripAudioExtension()
        }

        return KugouSongBrief(
            hash = hash,
            name = name.ifBlank { "未知歌曲" },
            singer = singer,
            albumId = item.optFirstNonBlank("album_id", "AlbumID", "albumid")
                ?: rawItem.optJSONObject("album_info")?.optString("id")?.takeIf { it.isNotBlank() }
                ?: rawItem.optJSONObject("base")?.optString("album_id")?.takeIf { it.isNotBlank() }
                ?: "",
            albumAudioId = item.optFirstNonBlank(
                "album_audio_id", "AlbumAudioID", "MixSongID", "mixsongid",
                "add_mixsongid", "Audioid", "audio_id",
            ).orEmpty(),
            fileId = item.optFirstNonBlank("fileid", "file_id"),
            durationMs = normalizeDurationToMs(item),
            coverUrl = normalizeCoverUrl(
                item.optFirstNonBlank(
                    "sizable_cover", "album_sizable_cover", "Image", "ImgUrl", "img", "pic",
                    "cover", "cover_pic", "union_cover", "imgurl",
                )
            ),
            albumName = item.optFirstNonBlank("album_name", "AlbumName", "albumname")
                ?.takeIf { it.isNotBlank() },
            songId = item.optFirstNonBlank("songid", "song_id", "SongId", "SongID"),
        )
    }

    /**
     * 展开歌曲 JSON 的嵌套结构：`album_info / albuminfo / audio_info / song_info / base`
     * 里的字段浅展开为顶层字段的**兜底**（顶层优先，避免 `album_info.name` 覆盖歌曲名）。
     */
    private fun mergeNestedSongFields(item: JSONObject): JSONObject {
        val merged = JSONObject()
        for (key in NESTED_SONG_KEYS) {
            val nested = item.optJSONObject(key) ?: continue
            val nestedKeys = nested.keys()
            while (nestedKeys.hasNext()) {
                val k = nestedKeys.next()
                if (!merged.has(k)) merged.put(k, nested.opt(k))
            }
        }
        val topKeys = item.keys()
        while (topKeys.hasNext()) {
            val k = topKeys.next()
            merged.put(k, item.opt(k))
        }
        return merged
    }

    /** singerinfo / Singers / authors 数组 → 「、」连接的歌手名。 */
    private fun parseSingerNames(item: JSONObject): String {
        for (key in listOf("singerinfo", "Singers", "singers", "authors")) {
            val array = item.optJSONArray(key) ?: continue
            val names = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    firstNonBlank(obj.optString("name"), obj.optString("author_name"))
                        ?.let(::add)
                }
            }
            if (names.isNotEmpty()) return names.joinToString("、")
        }
        return ""
    }

    /**
     * 「歌手 - 歌名」合并格式（filename / audio_name）拆出歌名；
     * 前缀歌手通过 [onArtist] 回填（歌手字段为空时用）。
     * 只用 " - "（空格-破折号-空格）作分隔，避免误剥含连字符的歌名。
     */
    private fun splitMergedName(name: String, onArtist: (String) -> Unit): String {
        if (!name.contains(" - ")) return name
        val parts = name.split(" - ")
        val rest = parts.drop(1).joinToString(" - ").trim()
        if (rest.isEmpty()) return name
        val prefix = parts.first().trim()
        if (prefix.isNotEmpty()) onArtist(prefix)
        return rest
    }

    /**
     * 去掉歌名末尾的音频扩展名：容忍全角句点、扩展名里多出的点（`.mp.3`）与前后空格，
     * 并循环剥离「xxx.mp3.mp3」这类重复后缀。
     */
    private fun String.stripAudioExtension(): String {
        var result = trim()
        while (true) {
            val stripped = result.replace('．', '.').replace(AUDIO_EXTENSION_REGEX, "").trim()
            if (stripped == result || stripped.isEmpty()) return result
            result = stripped
        }
    }

    /**
     * 时长归一化成**毫秒**（不同接口秒 / 毫秒混用）：
     * `time_length` 等 > 10000 视为毫秒、否则视为秒；都没有再退 `timelen`（部分接口是毫秒）。
     */
    private fun normalizeDurationToMs(item: JSONObject): Long {
        val raw = firstNonBlank(
            item.optString("time_length"),
            item.optString("HQDuration"),
            item.optString("Duration"),
            item.optString("duration"),
            item.optString("SuperDuration"),
            item.optString("timelength"),
        )?.toLongOrNull()
        if (raw != null && raw > 0L) return if (raw > 10_000L) raw else raw * 1000L
        return item.optString("timelen").toLongOrNull()?.takeIf { it > 0L } ?: 0L
    }

    /** 酷狗封面 URL：替换 `{size}` 占位并升级 https（老链接是 http，不替换会 404）。 */
    private fun normalizeCoverUrl(raw: String?): String? = raw
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?.replace("`", "")
        ?.replace("{size}", "480")
        ?.replace("http://", "https://")

    /** 依次取第一个非空字段值（配合嵌套展开后的顶层键使用）。 */
    private fun JSONObject.optFirstNonBlank(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            optString(key).takeIf { it.isNotBlank() && it != "null" }
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

    /** AES/CBC/PKCS5，key/iv 按 ASCII 字节使用，输出 base64（设备注册用）。 */
    private fun aesCbcBase64(data: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(), "AES"),
            IvParameterSpec(iv.toByteArray()),
        )
        return android.util.Base64.encodeToString(cipher.doFinal(data.toByteArray()), android.util.Base64.NO_WRAP)
    }

    /** AES/CBC/PKCS5 解密原始字节（设备注册响应体）。 */
    private fun aesCbcDecrypt(data: ByteArray, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.toByteArray(), "AES"),
            IvParameterSpec(iv.toByteArray()),
        )
        return String(cipher.doFinal(data))
    }

    /** RSA/ECB/PKCS1Padding（lite 公钥），输出小写 hex（设备注册的 `p` 参数）。 */
    private fun rsaPkcs1Hex(plain: String): String {
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(litePublicKeyDer()))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(plain.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun randomLower(length: Int): String =
        (1..length).map { ALPHANUM.random() }.joinToString("").lowercase()

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
        /** 设备注册走用户服务域名（参照项目 `/register/dev` → `/risk/v2/r_register_dev`）。 */
        private const val REGISTER_BASE = "https://userservice.kugou.com"
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

        /** 歌单歌曲 JSON 里可能藏着字段的嵌套对象（展开时作为顶层字段的兜底）。 */
        private val NESTED_SONG_KEYS = listOf("album_info", "albuminfo", "audio_info", "song_info", "base")

        /** 歌名末尾要剥掉的音频扩展名（容忍 `mp.3` 这类误写与多余空格）。 */
        private val AUDIO_EXTENSION_REGEX = Regex(
            "\\.\\s*(mp3|mp\\.?3|flac|m4a|aac|wav|ape|ogg|wma|mp4|opus)\\s*$",
            RegexOption.IGNORE_CASE
        )
        private const val QRCODE_TXT =
            "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=3116&"
        /** 二维码内容 URL（酷狗 App 扫这个） */
        private const val QR_CONTENT_BASE =
            "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode="
        private const val DEFAULT_UA =
            "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
        private const val LOGIN_UA = "Android16-1070-11440-130-0-LOGIN-wifi"
        private val JSON_MEDIA = "application/json;charset=utf-8".toMediaType()
        private val TEXT_MEDIA = "text/plain; charset=utf-8".toMediaType()

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
