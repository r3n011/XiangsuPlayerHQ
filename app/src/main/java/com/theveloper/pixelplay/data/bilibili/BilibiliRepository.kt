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

    val userId: Long
        get() = prefs.getLong("bilibili_user_id", -1L)

    val userNickname: String?
        get() = prefs.getString("bilibili_nickname", null)

    val userAvatar: String?
        get() = prefs.getString("bilibili_avatar", null)

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
                val nickname = cookies["DedeUserID__ckMd5"] ?: "Bilibili User"
                saveUserInfo(-1L, nickname, null)

                Timber.d("Bilibili login successful")
                Result.success(nickname)
            } catch (e: Exception) {
                Timber.e(e, "loginWithCookies: failed")
                Result.failure(e)
            }
        }
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