@file:Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection", "LongParameterList", "CanBeParameter")

package net.moriafly.ncm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * 网易云核心业务模块（Kotlin 移植）
 * 参考：NeteaseCloudMusicApi/module 下的 *.js
 *
 * 模块对照表：
 *   JS 文件名                 | 本文件对应方法（suspend fun）
 *   search_default.js         → searchDefault()
 *   search.js               → search(keyword, type, limit, offset)
 *   song_url_v1.js          → songUrlV1(ids, level)
 *   login_cellphone.js      → loginCellphone(phone, captcha)
 *   sent_sms.js              → sentSmsCaptcha(phone)
 *   captcha_sent.js        → captchaVerify(phone, captcha)
 *   login_refresh.js       → loginRefresh()
 *   logout.js            → logout()
 *   login_qr_key.js / createQR / checkQR → qrLoginKey() / qrLoginCheck(key)
 *   banner.js              → banner(type)
 *   playlist_detail.js     → playlistDetail(id)
 *   check_music.js         → checkMusicPlayable(id, br)
 *   song_detail.js         → songDetail(ids)
 *   user/account           → userAccount()
 */
internal object NcmModules {

    // ============================================================
    // 搜索
    // ============================================================

    /**
     * 默认搜索建议（首屏推荐词 / 搜索框联想前调用）
     * 对应: search_default.js → POST /api/search/defaultkeyword
     */
    suspend fun searchDefault(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = mapOf(
                "scene" to "normal",
            )
            NcmRequest.weapi(
                path = "/api/search/defaultkeyword",
                params = params,
            ).getOrThrow().body
        }
    }

    /**
     * 通用搜索
     * type:
     *   1 = 单曲 (默认)
     *   10 = 专辑
     *   100 = 歌手
     *   1000 = 歌单
     *   1002 = 用户
     *   1004 = MV
     *   1006 = 歌词
     *   1009 = 电台
     *   1014 = 视频
     *   1018 = 综合
     */
    suspend fun search(
        keyword: String,
        type: Int = 1,
        limit: Int = 30,
        offset: Int = 0,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            // 对齐原版 module/search.js：POST /api/search/get，type/limit/offset 为数字
            val params = linkedMapOf(
                "s" to keyword,
                "type" to type,
                "limit" to limit,
                "offset" to offset,
            )
            NcmRequest.weapi(
                path = "/api/search/get",
                params = params,
            ).getOrThrow().body
        }
    }

    // ============================================================
    // 歌曲播放
    // ============================================================

    /**
     * song_url_v1（官方 player URL 接口 —— 带付费灰/非试听 会返回 data[i].url = 302 Location
     * level: standard / higher / exhigh / lossless / hires / jyeffect / jymaster / sky
     * 对应: song_url_v1.js
     */
    suspend fun songUrlV1(
        ids: List<String>,
        level: String = "standard",
        encodeType: String = "flac",
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val idsParam = ids.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
            // 对齐原版 module/song_url_v1.js：默认只传 ids/level/encodeType，level=sky 才加 immerseType
            val params = linkedMapOf(
                "ids" to idsParam,
                "level" to level,
                "encodeType" to encodeType,
            )
            if (level == "sky") params["immerseType"] = "c51"
            NcmRequest.weapi(
                path = "/api/song/enhance/player/url/v1",
                params = params,
            ).getOrThrow().body
        }
    }

    /**
     * 歌曲可播放性检查（VIP 灰歌判断）
     * 对应 check_music.js
     */
    suspend fun checkMusicPlayable(
        id: String,
        br: Int = 320_000,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/song/enhance/player/url",
                params = linkedMapOf(
                    "ids" to "[$id]",
                    "br" to br.toString(),
                ),
            ).getOrThrow().body
        }
    }

    /**
     * 歌曲详情（批量，用于获取封面/歌曲名称/时长等元数据）
     */
    suspend fun songDetail(ids: List<String>): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val c = ids.joinToString(",", "[", "]") { "{\"id\":$it}" }
            NcmRequest.weapi(
                path = "/api/v3/song/detail",
                params = linkedMapOf(
                    "c" to c,
                ),
            ).getOrThrow().body
        }
    }

    // ============================================================
    // 登录系列
    // ============================================================

    /**
     * 发送短信验证码
     * 对应 sent_sms.js
     */
    suspend fun sentSmsCaptcha(
        phone: String,
        ctcode: String = "86",
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = linkedMapOf(
                "ctcode" to ctcode,
                "cellphone" to phone,
            )
            NcmRequest.weapi(
                path = "/api/sms/captcha/sent",
                params = params,
            ).getOrThrow().body
        }
    }

    /** 校验短信验证码（captcha_sent.js */
    suspend fun captchaVerify(
        phone: String,
        captcha: String,
        ctcode: String = "86",
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/sms/captcha/verify",
                params = linkedMapOf(
                    "ctcode" to ctcode,
                    "cellphone" to phone,
                    "captcha" to captcha,
                ),
            ).getOrThrow().body
        }
    }

    /**
     * 手机号登录（短信 / 密码二选一）
     * 对应 login_cellphone.js
     */
    suspend fun loginCellphone(
        phone: String,
        captcha: String? = null,
        password: String? = null,
        ctcode: String = "86",
        countrycode: String? = null,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = linkedMapOf<String, Any?>(
                "cellphone" to phone,
                "countrycode" to (countrycode ?: ctcode),
                "rememberLogin" to "true",
                "ctcode" to ctcode,
            )
            when {
                captcha != null -> params["captcha"] = captcha
                password != null -> params["password"] = passwordMd5(password)
                else -> error("loginCellphone: captcha 和 password 至少传一个")
            }
            val r = NcmRequest.weapi(
                path = "/api/w/login/cellphone",
                params = params,
            ).getOrThrow()

            // 登录成功 — Set-Cookie MUSIC_U 已在 NcmRequest 合并
            // 再把 body.cookie 字段手动合并（登录返回包含 __csrf / 等）
            val bodyCookie = (r.body["cookie"] as? String)?.takeIf { it.isNotBlank() }
            if (bodyCookie != null) {
                val sess = NcmSession.INSTANCE
                if (sess != null) {
                    val map = bodyCookie.split(';')
                        .map { it.trim() }
                        .filter { '=' in it }
                        .associate { val (k, v) = it.split('=', limit = 2); k to v }
                    sess.merge(map)
                }
            }
            r.body
        }
    }

    /** 登录刷新（cookie 续期） —— login_refresh.js */
    suspend fun loginRefresh(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/w/login/status",
                params = emptyMap(),
            ).getOrThrow().body
        }
    }

    /** 退出登录 */
    suspend fun logout(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmSession.INSTANCE?.logout()
            NcmRequest.weapi(
                path = "/api/logout",
                params = emptyMap(),
            ).getOrThrow().body
        }
    }

    // ============================================================
    // 二维码登录（三段式：key → 生成 & 轮询）
    // ============================================================

    data class QrKeyResult(val key: String, val code: Int)

    /** 1) 生成二维码 key（login_qr_key.js） */
    suspend fun qrLoginKey(): Result<QrKeyResult> = withContext(Dispatchers.IO) {
        runCatching {
            val unikey = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            val r = NcmRequest.weapi(
                path = "/api/w/login/qr/unikey",
                params = linkedMapOf(
                    "type" to 1,
                    "unikey" to unikey,
                ),
            ).getOrThrow()
            val body = r.body
            val data = (body["data"] as? Map<*, *>) ?: body
            val key = (data["unikey"] as? String)
                ?: (body["unikey"] as? String)
                ?: error("qr key not found in ${body.keys}")
            QrKeyResult(key, r.code)
        }
    }

    /** 2) 二维码图片（login_qr_create.js）—— key → Base64 PNG 字符串 & 已自动返回 qrimg */
    suspend fun qrLoginImage(key: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = NcmRequest.weapi(
                path = "/api/w/login/qr/create",
                params = linkedMapOf("key" to key, "qrimg" to true),
            ).getOrThrow().body
            val d = resp["data"] as? Map<*, *> ?: resp
            (d["qrimg"] as? String).orEmpty()
        }
    }

    /** 3) 轮询扫码状态：800=超时 / 801=等待扫码 / 802=授权中 / 803=登录成功
     *  803 返回会带 body.cookie，需手动合并 */
    suspend fun qrLoginCheck(key: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val r = NcmRequest.weapi(
                path = "/api/w/login/qr/check",
                params = linkedMapOf("key" to key),
            ).getOrThrow()
            val code = r.body.ncmInt("code", -1)
            if (code == 803) {
                val cookie = r.body.ncmString("cookie")
                if (cookie.isNotBlank()) {
                    val sess = NcmSession.INSTANCE
                    if (sess != null) {
                        val map = cookie.split(';')
                            .map { it.trim() }
                            .filter { '=' in it }
                            .associate { val (k, v) = it.split('=', limit = 2); k to v }
                        sess.merge(map)
                    }
                }
            }
            r.body
        }
    }

    /** 便捷封装：最多 1.5s 间隔轮询直到成功 / 总超时 120s */
    suspend fun qrLoginAwait(key: String, onStatus: suspend (Int, Map<String, Any?>) -> Unit = { _, _ -> }): Result<Map<String, Any?>> = coroutineScope {
        withTimeout(120_000L) {
            var last: Map<String, Any?> = emptyMap()
            while (true) {
                val r = qrLoginCheck(key).getOrThrow()
                last = r
                val code = r.ncmInt("code", -1)
                onStatus(code, r)
                when (code) {
                    803 -> return@withTimeout Result.success(r)
                    800 -> return@withTimeout Result.failure(IllegalStateException("QR 已过期 (code=800)"))
                    801, 802 -> { /* 继续轮询 */ }
                    else -> return@withTimeout Result.failure(IllegalStateException("QR 未知状态 code=$code"))
                }
                delay(1_500L)
            }
            @Suppress("UNREACHABLE_CODE")
            Result.success(last)
        }
    }

    // ============================================================
    // Banner / 歌单 / 用户
    // ============================================================

    /** banner.js —— type: 0=PC / 1=Android / 2=iPhone / 3=iPad */
    suspend fun banner(type: Int = 1): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/banner/get/v3",
                params = linkedMapOf(
                    "type" to type.toString(),
                    "clientType" to "pc",
                    "time" to System.currentTimeMillis().toString(),
                ),
            ).getOrThrow().body
        }
    }

    /** 歌单详情（playlist_detail.js） */
    suspend fun playlistDetail(
        id: String,
        s: Int = 8,
        n: Int = 100000,
        k: Long = System.currentTimeMillis(),
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            // 同时并发请求 detail + trackAll
            coroutineScope {
                val d1 = async {
                    NcmRequest.weapi(
                        path = "/api/v6/playlist/detail",
                        params = linkedMapOf(
                            "id" to id,
                            "n" to n.toString(),
                            "s" to s.toString(),
                            "k" to k.toString(),
                        ),
                    ).getOrThrow().body
                }
                val d2 = async {
                    val ids = d1.await()["playlist"]?.let { p ->
                        (p as? Map<*, *>)?.get("trackIds") as? List<*>
                    }?.take(200)
                        ?.mapNotNull { (it as? Map<*, *>)?.get("id").toString() }
                        ?: emptyList()
                    if (ids.isEmpty()) emptyMap<String, Any?>()
                    else songDetail(ids).getOrDefault(emptyMap())
                }
                val a = d1.await()
                val b = d2.await()
                buildMap<String, Any?>(a.size + b.size + 1) {
                    putAll(a)
                    if (b.isNotEmpty()) put("_songs_detail", b)
                }
            }
        }
    }

    /** 当前登录用户信息 */
    suspend fun userAccount(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/w/nuser/account/get",
                params = emptyMap(),
            ).getOrThrow().body
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    private fun passwordMd5(raw: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
