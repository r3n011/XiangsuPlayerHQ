@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package net.moriafly.ncm

import android.content.Context
import android.content.SharedPreferences

/**
 * 网易云 Session / Cookie 存储（Android SharedPreferences 落地）
 *
 * 对应原 util/request.js 里 Cookie 处理 + util/song_list_from_url.js 里 cookie string 的生命周期：
 * - MUSIC_U     : 登录态 Cookie（最关键，从 `/weapi/login/cellphone` Set-Cookie 里取）
 * - MUSIC_A     : 登录令牌 cookie（/eapi/batch 返回 Set-Cookie）
 * - os          : 'pc' / 'ios' / 'android'（决定用哪种 UA + appver）
 * - NMTID       : 游客态短 ID（不登录很多接口仍然可用）
 * - __csrf      : CSRF token，部分 POST 接口要用
 *
 * ⚠️ 登录态属于敏感信息 —— SharedPreferences 本身可被 root 读取，
 * 生产中建议在 Application 中注入 EncryptedSharedPreferences。
 */
class NcmSession internal constructor(
    private val sp: SharedPreferences,
) {
    companion object {
        private const val PREFS_NAME = "ncm_session"

        /** Android App 入口调用一次即可：NcmSession.install(this) */
        fun install(appContext: Context): NcmSession {
            val sp = appContext.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val instance = NcmSession(sp)
            INSTANCE = instance
            return instance
        }

        /** 单例，后续 NcmApi 等模块默认从此取值 */
        @Volatile
        internal var INSTANCE: NcmSession? = null
            private set

        fun requireInstance(): NcmSession = INSTANCE
            ?: error("NcmSession not installed! 请先在 Application.onCreate 调用 NcmSession.install(this)")
    }

    // ============================================================
    // 字段
    // ============================================================

    var cookies: MutableMap<String, String>
        get() {
            val raw = sp.getString("cookies", "")?.takeIf { it.isNotBlank() }
                ?: return LinkedHashMap()
            val m = LinkedHashMap<String, String>()
            raw.split('&').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) m[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
            return m
        }
        private set(value) {
            val s = value.entries.joinToString("&") { (k, v) -> "$k=$v" }
            sp.edit().putString("cookies", s).apply()
        }

    var os: String
        get() = sp.getString("os", "pc") ?: "pc"
        set(value) { sp.edit().putString("os", value).apply() }

    var realIp: String?
        get() = sp.getString("realIp", null)?.takeIf { it.isNotBlank() }
        set(value) { sp.edit().putString("realIp", value).apply() }

    var proxy: String?
        get() = sp.getString("proxy", null)?.takeIf { it.isNotBlank() }
        set(value) { sp.edit().putString("proxy", value).apply() }

    val isLogin: Boolean get() = cookies["MUSIC_U"]?.isNotBlank() == true

    val userId: Long? get() = cookies["MUSIC_U"]?.toLongOrNull()

    // ============================================================
    // 操作
    // ============================================================

    /** 合并入参 cookies（来自 Set-Cookie header 或 /login 响应 body 的 cookie 字段） */
    fun merge(rawSetCookies: List<String>) {
        val cur = cookies
        for (raw in rawSetCookies) {
            val head = raw.split(';').firstOrNull()?.trim() ?: continue
            val idx = head.indexOf('=')
            if (idx <= 0) continue
            val k = head.substring(0, idx)
            val v = head.substring(idx + 1)
            // 只覆盖 NCM 关心的字段，忽略 max-age/path 等
            if (v.isBlank()) {
                // e.g. MUSIC_U=; Expires=... 表示删除
                cur.remove(k)
            } else cur[k] = v
        }
        cookies = cur
    }

    /** 以 Map 形式手动合并（登录用） */
    fun merge(map: Map<String, String?>) {
        val cur = cookies
        for ((k, v) in map) {
            if (v == null || v.isBlank()) cur.remove(k)
            else cur[k] = v
        }
        cookies = cur
    }

    /** 序列化成请求头里的 `Cookie: a=1; b=2` 字符串 */
    fun toCookieHeader(): String {
        val c = cookies
        if (c.isEmpty()) return ""
        return c.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    fun set(key: String, value: String?) {
        val cur = cookies
        if (value == null || value.isBlank()) cur.remove(key)
        else cur[key] = value
        cookies = cur
    }

    fun clear() {
        sp.edit().remove("cookies").apply()
    }

    fun logout() {
        val cur = cookies
        cur.remove("MUSIC_U")
        cur.remove("MUSIC_A")
        cur.remove("__csrf")
        cookies = cur
    }
}
