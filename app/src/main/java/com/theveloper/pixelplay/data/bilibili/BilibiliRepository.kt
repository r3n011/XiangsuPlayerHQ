@file:Suppress("DEPRECATION")
package com.theveloper.pixelplay.data.bilibili

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BilibiliRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bilibili_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "BilibiliRepository: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("bilibili_prefs_plain", Context.MODE_PRIVATE)
    }

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    init {
        initFromSavedCookies()
        _isLoggedInFlow.value = hasLogin()
        Timber.d("BilibiliRepository init: isLoggedIn=${hasLogin()}")
    }

    val isLoggedIn: Boolean
        get() = hasLogin()

    fun getCookieString(): String = prefs.getString("bilibili_cookies", "") ?: ""

    /**
     * 将保存的 JSON cookie 转换为 HTTP Cookie header 格式。
     * 返回空字符串表示无有效 cookie。
     */
    fun getCookieHeader(): String {
        val json = getCookieString()
        if (json.isBlank()) return ""
        return try {
            jsonToMap(json)
                .map { "${it.key}=${it.value}" }
                .joinToString("; ")
        } catch (e: Exception) {
            Timber.w(e, "Failed to build Bilibili cookie header")
            ""
        }
    }

    /** 从已保存 cookie 中取 bili_jct（评论发布/点赞/举报等写操作的 csrf 参数） */
    fun getCsrf(): String? {
        val json = getCookieString()
        if (json.isBlank()) return null
        return try {
            jsonToMap(json)["bili_jct"]?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read Bilibili csrf")
            null
        }
    }

    val userId: Long
        get() {
            val saved = prefs.getLong("bilibili_user_id", -1L)
            if (saved > 0L) return saved
            // 兜底：nav 未成功时从 cookie 的 DedeUserID 解析 uid（收藏夹列表接口必须携带）
            return try {
                jsonToMap(getCookieString())["DedeUserID"]?.toLongOrNull() ?: -1L
            } catch (e: Exception) {
                -1L
            }
        }

    val userNickname: String?
        get() = prefs.getString("bilibili_nickname", null)

    val userAvatar: String?
        get() = prefs.getString("bilibili_avatar", null)

    // —— 评论过滤设置（对齐 PiliPlus Pref.banWordForReply / Pref.antiGoodsReply）——
    /** 关键词过滤开关（默认关闭，与 PiliPlus banWordForReply 默认空串一致） */
    var commentFilterEnabled: Boolean
        get() = prefs.getBoolean("bilibili_comment_filter_enabled", false)
        set(value) { prefs.edit().putBoolean("bilibili_comment_filter_enabled", value).apply() }

    /** 广告评论过滤开关（默认关闭，与 PiliPlus antiGoodsReply 默认 false 一致） */
    var antiGoodsFilterEnabled: Boolean
        get() = prefs.getBoolean("bilibili_anti_goods_filter_enabled", false)
        set(value) { prefs.edit().putBoolean("bilibili_anti_goods_filter_enabled", value).apply() }

    /** 评论关键词正则（为空表示不过滤） */
    var commentBanWords: String
        get() = prefs.getString("bilibili_comment_ban_words", "") ?: ""
        set(value) { prefs.edit().putString("bilibili_comment_ban_words", value).apply() }

    private fun initFromSavedCookies() {
        val cookieJson = prefs.getString("bilibili_cookies", null) ?: return
        try {
            val map = jsonToMap(cookieJson)
            if (map.isNotEmpty()) {
                Timber.d("BilibiliRepository: Restored ${map.size} cookies")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to restore Bilibili cookies")
        }
    }

    suspend fun loginWithCookies(cookieJson: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val cookies = jsonToMap(cookieJson)

                if (!cookies.containsKey("SESSDATA") && !cookies.containsKey("bili_jct")) {
                    Timber.w("loginWithCookies: required session cookies not found")
                    return@withContext Result.failure(Exception("SESSDATA or bili_jct cookie not found"))
                }

                prefs.edit().putString("bilibili_cookies", cookieJson).apply()

                _isLoggedInFlow.value = true
                // DedeUserID 即用户 uid；昵称后续由 /x/web-interface/nav 拉取并刷新
                val uid = cookies["DedeUserID"]?.toLongOrNull() ?: -1L
                val nickname = cookies["DedeUserID__ckMd5"] ?: "Bilibili User"
                saveUserInfo(uid, nickname, null)

                Timber.d("Bilibili login successful, uid=$uid")
                Result.success(nickname)
            } catch (e: Exception) {
                Timber.e(e, "loginWithCookies: failed")
                Result.failure(e)
            }
        }
    }

    /** 覆盖保存 cookie（如扫码登录成功后拿到完整会话） */
    fun updateCookies(cookieMap: Map<String, String>) {
        prefs.edit().putString("bilibili_cookies", mapToJson(cookieMap)).apply()
        _isLoggedInFlow.value = hasLogin()
    }

    /** 刷新用户资料（扫码登录后由 nav 接口拉取真实昵称/头像/uid） */
    fun updateUserInfo(userId: Long, nickname: String, avatarUrl: String?) {
        saveUserInfo(userId, nickname, avatarUrl)
        _isLoggedInFlow.value = true
    }

    suspend fun logout() {
        clearLoginState()
        _isLoggedInFlow.value = false
        Timber.d("Bilibili logout successful")
    }

    private fun hasLogin(): Boolean {
        val cookies = getCookieString()
        return cookies.isNotBlank() && (cookies.contains("SESSDATA") || cookies.contains("bili_jct"))
    }

    private fun saveUserInfo(userId: Long, nickname: String, avatarUrl: String?) {
        prefs.edit()
            .putLong("bilibili_user_id", userId)
            .putString("bilibili_nickname", nickname)
            .putString("bilibili_avatar", avatarUrl)
            .apply()
    }

    private fun clearLoginState() {
        prefs.edit().clear().apply()
    }

    fun getPlaylists(): Flow<List<Nothing>> {
        return kotlinx.coroutines.flow.flowOf(emptyList())
    }

    private fun mapToJson(map: Map<String, String>): String {
        return try {
            val obj = JSONObject()
            map.forEach { (k, v) -> obj.put(k, v) }
            obj.toString()
        } catch (e: Exception) {
            Timber.w(e, "Failed to serialize cookie map")
            "{}"
        }
    }

    private fun jsonToMap(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                obj.optString(key)?.let { value ->
                    map[key] = value
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse cookie JSON")
        }
        return map
    }
}