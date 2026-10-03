package com.theveloper.pixelplay.data.remote.qqmusic

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber

/**
 * QQ 音乐「手机号 + 短信验证码」登录（对齐参考实现 MeloX 的 QQMusicPhoneAuthClient）。
 *
 * 走官方 `musicu.fcg` 的 `music.login.LoginServer` 模块：
 * - 发送验证码：`method = SendPhoneAuthCode`，param `{tmeAppid, phoneNo, areaCode}`；
 * - 验证码登录：`method = Login`，param `{code, phoneNo, loginMode = 1}`。
 *
 * 登录成功时响应里带 `Set-Cookie`（`qqmusic_uin` / `qm_keyst` 等）以及 `musickey` 字段，
 * 这里统一合并成一份 cookie 表交给 [com.theveloper.pixelplay.data.qqmusic.QqMusicRepository] 落盘，
 * 与网页登录（WebView 抓 cookie）走同一套后续逻辑。
 *
 * 注意：官方接口可能要求「安全验证」（业务码 20276 或返回 securityURL），
 * 这种情况只能让用户改用网页登录完成验证，这里会抛出带说明的异常。
 */
@Singleton
class QqMusicPhoneAuthClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {

    private companion object {
        const val AUTH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        const val LOGIN_MODULE = "music.login.LoginServer"
        const val AREA_CODE = "86"
    }

    /** 上一次请求携带的 cookie（发送验证码时服务端会下发临时 cookie，登录时要带上） */
    private var requestCookie = ""

    /** 发送短信验证码 */
    suspend fun sendCode(phone: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = post(buildSendCodePayload(phone))
            requestCookie = mergeResponseCookies(requestCookie, response.setCookies)
            parseAuthResponse(response.body, requestCookie, requireSession = false)
            Timber.d("QqMusicPhoneAuthClient: 验证码已发送")
        }
    }

    /** 验证码登录：成功返回可直接落盘的 cookie 表 */
    suspend fun login(phone: String, code: String): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = post(buildLoginPayload(phone, code))
                requestCookie = mergeResponseCookies(requestCookie, response.setCookies)
                val normalized = parseAuthResponse(response.body, requestCookie, requireSession = true)
                requestCookie = normalized
                parseCookieHeader(normalized)
            }
        }

    private fun post(payload: JSONObject): PhoneHttpResponse {
        val request = Request.Builder()
            .url(AUTH_URL)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .apply { if (requestCookie.isNotBlank()) header("Cookie", requestCookie) }
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("QQ 音乐请求失败：HTTP ${response.code}")
            val bodyText = response.body?.string().orEmpty()
            val body = runCatching { JSONObject(bodyText) }
                .getOrElse { throw IOException("QQ 音乐返回了无法解析的响应") }
            PhoneHttpResponse(body, response.headers.values("Set-Cookie"))
        }
    }

    private fun buildSendCodePayload(phone: String): JSONObject = buildPayload(
        method = "SendPhoneAuthCode",
        param = JSONObject()
            .put("tmeAppid", "qqmusic")
            .put("phoneNo", phone)
            .put("areaCode", AREA_CODE),
    )

    private fun buildLoginPayload(phone: String, code: String): JSONObject = buildPayload(
        method = "Login",
        param = JSONObject()
            .put("code", code)
            .put("phoneNo", phone)
            .put("loginMode", 1),
    )

    private fun buildPayload(method: String, param: JSONObject): JSONObject = JSONObject()
        .put(
            "comm",
            JSONObject()
                .put("ct", 24)
                .put("cv", 20050009)
                .put("v", 20050009)
                .put("format", "json"),
        )
        .put(
            LOGIN_MODULE,
            JSONObject()
                .put("module", LOGIN_MODULE)
                .put("method", method)
                .put("param", param),
        )

    private data class PhoneHttpResponse(
        val body: JSONObject,
        val setCookies: List<String>,
    )

    /**
     * 解析登录接口响应，返回合并后的 cookie 头。
     * [requireSession] = true 时要求响应里必须带账号标识与音乐密钥。
     */
    private fun parseAuthResponse(
        response: JSONObject,
        existingCookie: String,
        requireSession: Boolean,
    ): String {
        val merged = mergeResponseCookies(existingCookie, emptyList())
        val outerCode = response.intValue("code")
            ?: throw IOException("QQ 音乐响应缺少状态码")
        if (outerCode != 0) {
            throw IOException(response.errorMessage("QQ 音乐请求失败（$outerCode）"))
        }

        val business = response.findBusinessObject()
            ?: throw IOException("QQ 音乐响应缺少登录结果")
        val businessCode = business.intValue("code")
            ?: throw IOException("QQ 音乐登录结果缺少状态码")
        val data = business.objectValue("data") ?: JSONObject()
        if (businessCode != 0) {
            val securityUrl = data.stringValue("securityURL")
                .ifBlank { business.stringValue("securityURL") }
            if (businessCode == 20276 || securityUrl.isNotBlank()) {
                throw IOException("QQ 音乐要求安全验证，请改用「网页登录」完成验证后再试")
            }
            throw IOException(
                business.errorMessage(response.errorMessage("QQ 音乐请求失败（$businessCode）"), data)
            )
        }
        if (!requireSession) return merged

        val credentialObjects = buildList {
            add(data)
            listOf("data", "result").forEach { name -> data.objectValue(name)?.let(::add) }
        }
        val cookies = parseCookieHeader(merged).toMutableMap()
        val uin = cookies.firstMatchingValue(::isUsableUin, "qqmusic_uin", "uin")
            ?: credentialObjects.firstMatchingValue(::isUsableUin, "musicid", "str_musicid", "encryptUin")
            ?: throw IOException("登录响应缺少有效账号标识，请改用网页登录")
        val musicKey = cookies.valueFor("qm_keyst", "qqmusic_key", "musickey")
            ?.takeIf(String::isNotBlank)
            ?: credentialObjects.firstValue("musickey", "qm_keyst", "qqmusic_key").takeIf(String::isNotBlank)
            ?: throw IOException("登录响应缺少音乐密钥，请改用网页登录")

        cookies.putCanonical("qqmusic_uin", uin)
        cookies.putCanonical("qm_keyst", musicKey)
        // 兼容 QqMusicApiService.hasLogin() 里对 uin / qqmusic_key 的判定
        cookies.putCanonical("uin", uin)
        cookies.putCanonical("qqmusic_key", musicKey)
        listOf("token", "refresh_key").forEach { name ->
            credentialObjects.firstValue(name).takeIf(String::isNotBlank)?.let { cookies.putCanonical(name, it) }
        }
        return normalizeCookieHeader(cookies)
    }

    private fun mergeResponseCookies(
        cookieHeader: String,
        setCookieHeaders: List<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val values = parseCookieHeader(cookieHeader).toMutableMap()
        val url = AUTH_URL.toHttpUrl()
        setCookieHeaders.forEach { header ->
            val firstPair = header.substringBefore(';').split('=', limit = 2)
            if (firstPair.size != 2 || firstPair[0].isBlank()) return@forEach
            val name = firstPair[0].trim()
            val parsed = Cookie.parse(url, header)
            if (parsed == null || parsed.value.isEmpty() || parsed.expiresAt <= nowMillis) {
                values.removeCaseInsensitive(name)
            } else {
                values.putCanonical(parsed.name, parsed.value)
            }
        }
        return normalizeCookieHeader(values)
    }

    private fun JSONObject.findBusinessObject(): JSONObject? {
        val firstLevel = listOf(this) + listOf("data", "response", "result").mapNotNull(::objectValue)
        firstLevel.forEach { root -> root.objectValue(LOGIN_MODULE)?.let { return it } }
        firstLevel.drop(1).forEach { wrapper ->
            listOf("data", "response", "result").forEach { name ->
                wrapper.objectValue(name)?.objectValue(LOGIN_MODULE)?.let { return it }
            }
        }
        return null
    }

    private fun JSONObject.errorMessage(fallback: String, extra: JSONObject? = null): String =
        listOfNotNull(extra, this).firstValue("errMsg", "errTip", "message").ifBlank { fallback }

    private fun List<JSONObject>.firstValue(vararg names: String): String {
        for (value in this) {
            for (name in names) value.stringValue(name).takeIf(String::isNotBlank)?.let { return it }
        }
        return ""
    }

    private fun List<JSONObject>.firstMatchingValue(
        predicate: (String) -> Boolean,
        vararg names: String,
    ): String? {
        for (value in this) {
            for (name in names) value.stringValue(name).takeIf(predicate)?.let { return it }
        }
        return null
    }

    private fun JSONObject.stringValue(name: String): String {
        val key = keys().asSequence().firstOrNull { it.equals(name, ignoreCase = true) } ?: return ""
        val value = opt(key)
        return if (value == null || value == JSONObject.NULL || value is JSONObject) "" else value.toString().trim()
    }

    private fun JSONObject.intValue(name: String): Int? = stringValue(name).toIntOrNull()

    private fun JSONObject.objectValue(name: String): JSONObject? {
        val key = keys().asSequence().firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
        return optJSONObject(key)
    }

    private fun parseCookieHeader(cookie: String): Map<String, String> = buildMap {
        cookie.split(';').forEach { item ->
            val pair = item.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[0].isNotBlank() && pair[1].isNotBlank()) {
                put(pair[0].trim(), pair[1].trim())
            }
        }
    }

    private fun MutableMap<String, String>.putCanonical(name: String, value: String) {
        removeCaseInsensitive(name)
        put(name, value)
    }

    private fun MutableMap<String, String>.removeCaseInsensitive(name: String) {
        keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
    }

    private fun Map<String, String>.valueFor(vararg names: String): String? {
        for (name in names) {
            entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.let { return it }
        }
        return null
    }

    private fun Map<String, String>.firstMatchingValue(
        predicate: (String) -> Boolean,
        vararg names: String,
    ): String? {
        for (name in names) {
            entries.firstOrNull { it.key.equals(name, ignoreCase = true) && predicate(it.value) }
                ?.value?.let { return it }
        }
        return null
    }

    private fun normalizeCookieHeader(values: Map<String, String>): String = values
        .filterValues(String::isNotBlank)
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .entries
        .joinToString("; ") { (key, value) -> "$key=$value" }

    private fun isUsableUin(value: String): Boolean =
        value.isNotBlank() && value.all(Char::isDigit) && value.any { it != '0' }
}
