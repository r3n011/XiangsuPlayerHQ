package com.theveloper.pixelplay.data.network.netease

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/**
 * 网易云「手机号 + 短信验证码」登录，对齐参考项目 MeloX 的 NeteasePhoneAuthClient：
 *
 * - 发码与登录都走 EAPI（`/api/sms/captcha/sent`、`/api/w/login/cellphone`）；
 * - **手动维护请求 Cookie**：合并响应 `Set-Cookie` 与 body 里的 `cookie`，再带入下一次请求。
 *   网易云登录会话依赖这条链路，只取 body cookie 会在部分网络/风控下偶发失败；
 * - 登录后强制校验 `MUSIC_U`，没有会话直接给出可读错误；
 * - 网络错误/5xx 自动重试一次；风控挑战不重试，直接提示改用网页登录。
 */
@Singleton
class NeteasePhoneAuthClient @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
) {
    private companion object {
        const val TAG = "NeteasePhoneAuth"
        const val EAPI_BASE = "https://interface.music.163.com/eapi"
        const val EAPI_UA = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
        const val PREFS = "netease_phone_auth"
        const val KEY_DEVICE_ID = "device_id"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    /** 发码时服务端下发的临时 Cookie，登录时必须带上。 */
    @Volatile
    private var requestCookie: String = ""

    @Synchronized
    private fun mergeCookie(setCookies: List<String>, bodyCookie: String?) {
        val map = parseCookies(requestCookie).toMutableMap()
        setCookies.forEach { header ->
            val first = header.substringBefore(';')
            val pair = first.split('=', limit = 2)
            if (pair.size != 2 || pair[0].isBlank()) return@forEach
            val name = pair[0].trim()
            val value = pair[1].trim()
            val expired = value.isEmpty() ||
                header.split(';').any { it.trim().equals("Max-Age=0", ignoreCase = true) }
            if (expired) map.remove(name) else map[name] = value
        }
        bodyCookie?.split(';')?.forEach { raw ->
            val pair = raw.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[0].isNotBlank() && pair[1].isNotBlank()) {
                map[pair[0].trim()] = pair[1].trim()
            }
        }
        requestCookie = map.toSortedMap().entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    suspend fun sendCode(countryCode: String, phone: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = executeWithRetry {
                    postEapi(
                        path = "/api/sms/captcha/sent",
                        params = mapOf(
                            "cellphone" to phone,
                            "ctcode" to countryCode,
                        ),
                    )
                }
                mergeCookie(response.setCookies, null)
                val code = response.json.optInt("code", -1)
                if (code !in 200..299) throw phoneAuthError(code, response.json)
            }.onFailure { Timber.w(it, "$TAG: sendCode failed") }
        }

    /**
     * 返回可直接交给 repository 落盘的 Cookie 头（必须包含 MUSIC_U）。
     */
    suspend fun login(countryCode: String, phone: String, code: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = executeWithRetry {
                    postEapi(
                        path = "/api/w/login/cellphone",
                        params = mapOf(
                            "phone" to phone,
                            "countrycode" to countryCode,
                            "remember" to "true",
                            "type" to "1",
                            "captcha" to code,
                        ),
                    )
                }
                mergeCookie(
                    response.setCookies,
                    response.json.optString("cookie").takeIf { it.isNotBlank() },
                )
                val resultCode = response.json.optInt("code", -1)
                if (resultCode !in 200..299) throw phoneAuthError(resultCode, response.json)
                if (!requestCookie.contains("MUSIC_U=")) {
                    throw IOException("网易云登录响应未包含有效会话，请改用网页登录或稍后重试")
                }
                requestCookie
            }.onFailure { Timber.w(it, "$TAG: login failed") }
        }

    private suspend fun executeWithRetry(block: () -> EapiResponse): EapiResponse {
        var last: Throwable? = null
        for (attempt in 0..1) {
            try {
                return block()
            } catch (e: NeteaseAuthException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (attempt == 0) delay(450)
            }
        }
        throw last ?: IOException("网易云请求失败")
    }

    private fun postEapi(path: String, params: Map<String, String>): EapiResponse {
        val url = "$EAPI_BASE$path".toHttpUrl()
        val header = buildEapiHeader()
        val payload = LinkedHashMap<String, Any>(params).apply {
            put("header", header)
            put("e_r", false)
        }
        val encrypted = NeteaseEncryption.eApiEncrypt(url.encodedPath, payload)
        val form = FormBody.Builder(StandardCharsets.UTF_8).apply {
            encrypted.forEach { (key, value) -> add(key, value) }
        }.build()
        val cookieHeader = header.entries.joinToString("; ") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", EAPI_UA)
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
            .header("Cookie", cookieHeader)
            .post(form)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("网易云请求失败：HTTP ${response.code}")
            }
            val json = runCatching { JSONObject(body) }.getOrElse {
                throw IOException("网易云返回了无法解析的响应")
            }
            return EapiResponse(json, response.headers.values("Set-Cookie"))
        }
    }

    private fun buildEapiHeader(): Map<String, String> {
        val now = System.currentTimeMillis()
        return linkedMapOf(
            "osver" to "Microsoft-Windows-10-Professional-build-19045-64bit",
            "deviceId" to deviceId(),
            "os" to "pc",
            "appver" to "8.10.35",
            "versioncode" to "140",
            "mobilename" to "",
            "buildver" to now.toString().substring(0, 10),
            "resolution" to "1920x1080",
            "__csrf" to parseCookies(requestCookie)["__csrf"].orEmpty(),
            "channel" to "netease",
            "requestId" to "${now}_${random.nextInt(1000).toString().padStart(4, '0')}",
        )
    }

    private fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.length == 52 }?.let { return it }
        val chars = "0123456789ABCDEF"
        val id = buildString(52) { repeat(52) { append(chars[random.nextInt(chars.length)]) } }
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun phoneAuthError(code: Int, body: JSONObject): NeteaseAuthException {
        val data = body.optJSONObject("data")
        val loginExt = body.optJSONObject("loginExtData")
        val hasChallenge = listOfNotNull(body, data, loginExt).any {
            it.optString("redirectUrl").isNotBlank() || it.optString("checkToken").isNotBlank()
        }
        val isChallenge = code in setOf(702, 8810, 8820, 8830, 8860) ||
            code in 10001..10004 ||
            hasChallenge
        val message = if (isChallenge) {
            "网易云要求额外安全验证，请改用网页登录完成验证"
        } else {
            body.optString("message")
                .ifBlank { body.optString("msg") }
                .ifBlank { "网易云请求失败（$code）" }
        }
        return NeteaseAuthException(message, isChallenge, code)
    }

    private fun parseCookies(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(';').mapNotNull { part ->
            val pair = part.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[0].isNotBlank()) pair[0].trim() to pair[1].trim() else null
        }.toMap()
    }

    private data class EapiResponse(
        val json: JSONObject,
        val setCookies: List<String>,
    )
}

class NeteaseAuthException(
    override val message: String,
    val isChallenge: Boolean,
    val code: Int = -1,
) : IOException(message)
