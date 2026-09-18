package com.theveloper.pixelplay.data.backup.module

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.BackupSection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份/恢复在线音源账号的 Cookie 登录信息。
 * 包括网易云音乐、QQ 音乐、B站三个来源的 SharedPreferences cookie。
 */
@Singleton
class CookiesModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : BackupModuleHandler {

    override val section: BackupSection = BackupSection.COOKIES

    private data class CookieSnapshot(
        val netease: Map<String, String> = emptyMap(),
        val qqmusic: Map<String, String> = emptyMap(),
        val bilibili: Map<String, String> = emptyMap()
    )

    override suspend fun export(): String {
        val snapshot = CookieSnapshot(
            netease = readPrefs("netease_prefs_plain"),
            qqmusic = readPrefs("qqmusic_prefs_plain"),
            bilibili = readPrefs("bilibili_prefs_plain")
        )
        return gson.toJson(snapshot)
    }

    override suspend fun countEntries(): Int {
        var count = 0
        if (readPrefs("netease_prefs_plain").isNotEmpty()) count++
        if (readPrefs("qqmusic_prefs_plain").isNotEmpty()) count++
        if (readPrefs("bilibili_prefs_plain").isNotEmpty()) count++
        return count
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) {
        val type = object : TypeToken<CookieSnapshot>() {}.type
        val snapshot: CookieSnapshot = gson.fromJson(payload, type)
        if (snapshot.netease.isNotEmpty()) writePrefs("netease_prefs_plain", snapshot.netease)
        if (snapshot.qqmusic.isNotEmpty()) writePrefs("qqmusic_prefs_plain", snapshot.qqmusic)
        if (snapshot.bilibili.isNotEmpty()) writePrefs("bilibili_prefs_plain", snapshot.bilibili)
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    private fun readPrefs(name: String): Map<String, String> {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val all = prefs.all ?: return emptyMap()
        return all.mapNotNull { (key, value) ->
            if (value is String) key to value else null
        }.toMap()
    }

    private fun writePrefs(name: String, data: Map<String, String>) {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().apply {
            data.forEach { (key, value) -> putString(key, value) }
            apply()
        }
    }
}
