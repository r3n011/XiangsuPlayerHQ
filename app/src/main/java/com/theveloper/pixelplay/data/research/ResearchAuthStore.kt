package com.theveloper.pixelplay.data.research

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 「听歌数据研究计划」凭据存储：deviceCode、licenseKey、signKeyId、signKey、
 * 服务端时钟偏移。
 *
 * signKey 只在注册响应出现一次，规范要求存 Keystore 加密存储且不写日志 ——
 * 这里用 [EncryptedSharedPreferences]（失败自动回退明文库，与 KugouRepository
 * 同款处理 + 双库互备）。两个 prefs 文件均已加入 backup_rules / data_extraction_rules
 * 排除清单，凭据不随备份漫游（对应规范 android:allowBackup=false 的意图）。
 */
@Singleton
class ResearchAuthStore @Inject constructor(
    @ApplicationContext context: Context
) {

    val fallbackPrefs: SharedPreferences =
        context.getSharedPreferences("research_prefs_fallback", Context.MODE_PRIVATE)

    private val encryptedPrefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "research_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as SharedPreferences
    }.getOrElse { error ->
        Timber.w(error, "ResearchAuthStore: EncryptedSharedPreferences 不可用，回退明文存储")
        null
    }

    private val prefs: SharedPreferences = (encryptedPrefs ?: fallbackPrefs).also { active ->
        val other = if (active === fallbackPrefs) encryptedPrefs else fallbackPrefs
        if (other != null) {
            val editor = active.edit()
            var changed = false
            for (key in MIRRORED_KEYS) {
                if (active.getString(key, null).isNullOrBlank()) {
                    val value = other.getString(key, null)
                    if (!value.isNullOrBlank()) {
                        editor.putString(key, value)
                        changed = true
                    }
                }
            }
            if (changed) editor.apply()
        }
    }

    private var cachedDeviceCode: String? = null

    /** 设备码：UUIDv4，首次访问生成并持久化；不用 IMEI/OAID/SSAID（规范 §1） */
    @Synchronized
    fun getOrCreateDeviceCode(): String {
        cachedDeviceCode?.let { return it }
        val existing = prefs.getString(KEY_DEVICE_CODE, null)
        val code = if (existing.isNullOrBlank()) {
            val generated = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_CODE, generated).apply()
            generated
        } else {
            existing
        }
        cachedDeviceCode = code
        return code
    }

    var licenseKey: String?
        get() = prefs.getString(KEY_LICENSE_KEY, null)
        set(value) = prefs.edit().putString(KEY_LICENSE_KEY, value).apply()

    var signKeyId: String?
        get() = prefs.getString(KEY_SIGN_KEY_ID, null)
        set(value) = prefs.edit().putString(KEY_SIGN_KEY_ID, value).apply()

    var signKey: String?
        get() = prefs.getString(KEY_SIGN_KEY, null)
        set(value) = prefs.edit().putString(KEY_SIGN_KEY, value).apply()

    /* signKey 说明：服务端 crypto.createHmac('sha256', signKey) 以原串为密钥，
       客户端存储与签名都用原串（UTF-8 字节），不做 Base64 解码（规范 03 §3.1 更新版）。 */

    /** 本地时钟相对服务端的偏移秒（serverTime - localTime），用于请求时间戳校正 */
    var serverTimeOffsetSec: Long
        get() = prefs.getLong(KEY_SERVER_TIME_OFFSET, 0L)
        set(value) = prefs.edit().putLong(KEY_SERVER_TIME_OFFSET, value).apply()

    /** 登录会话是否完整（注册成功且三件套齐全） */
    fun hasSession(): Boolean =
        !licenseKey.isNullOrBlank() && !signKeyId.isNullOrBlank() && !signKey.isNullOrBlank()

    /** 退出 / 密钥失效时清空会话凭据（deviceCode 保留 —— 服务端冷却以它为键） */
    fun clearSession() {
        licenseKey = null
        signKeyId = null
        signKey = null
        serverTimeOffsetSec = 0L
    }

    private companion object {
        const val KEY_DEVICE_CODE = "device_code"
        const val KEY_LICENSE_KEY = "license_key"
        const val KEY_SIGN_KEY_ID = "sign_key_id"
        const val KEY_SIGN_KEY = "sign_key"
        const val KEY_SERVER_TIME_OFFSET = "server_time_offset_sec"

        val MIRRORED_KEYS = listOf(
            KEY_DEVICE_CODE, KEY_LICENSE_KEY, KEY_SIGN_KEY_ID, KEY_SIGN_KEY
        )
    }
}
