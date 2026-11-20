package com.theveloper.pixelplay.data.tvauth

import com.theveloper.pixelplay.BuildConfig
import org.json.JSONObject

/** 手机上可传给电视的账号凭据集合。 */
data class TvAuthorizeAccounts(
    val netease: Map<String, String>? = null,
    val qq: Map<String, String>? = null,
    val bilibili: Map<String, String>? = null,
    val kugouToken: String? = null,
    val kugouUserId: String? = null,
    val kugouVipToken: String? = null,
) {
    val availableKeys: List<String>
        get() = buildList {
            if (!netease.isNullOrEmpty()) add(KEY_NETEASE)
            if (!qq.isNullOrEmpty()) add(KEY_QQ)
            if (!bilibili.isNullOrEmpty()) add(KEY_BILIBILI)
            if (!kugouToken.isNullOrBlank()) add(KEY_KUGOU)
        }

    val isEmpty: Boolean get() = availableKeys.isEmpty()

    companion object {
        const val KEY_NETEASE = "netease"
        const val KEY_QQ = "qq"
        const val KEY_BILIBILI = "bilibili"
        const val KEY_KUGOU = "kugou"
    }
}

/** 组装电视授权接口需要的 JSON（协议 v1）。 */
object TvAuthPayload {
    fun build(accounts: TvAuthorizeAccounts, selected: Set<String>): String {
        val accountJson = JSONObject()
        if (TvAuthorizeAccounts.KEY_NETEASE in selected) {
            accounts.netease?.takeIf { it.isNotEmpty() }?.let {
                accountJson.put(TvAuthorizeAccounts.KEY_NETEASE, JSONObject().put("cookies", it.toJson()))
            }
        }
        if (TvAuthorizeAccounts.KEY_QQ in selected) {
            accounts.qq?.takeIf { it.isNotEmpty() }?.let {
                accountJson.put(TvAuthorizeAccounts.KEY_QQ, JSONObject().put("cookies", it.toJson()))
            }
        }
        if (TvAuthorizeAccounts.KEY_BILIBILI in selected) {
            accounts.bilibili?.takeIf { it.isNotEmpty() }?.let {
                accountJson.put(TvAuthorizeAccounts.KEY_BILIBILI, JSONObject().put("cookies", it.toJson()))
            }
        }
        if (TvAuthorizeAccounts.KEY_KUGOU in selected) {
            accounts.kugouToken?.takeIf { it.isNotBlank() }?.let { token ->
                accountJson.put(
                    TvAuthorizeAccounts.KEY_KUGOU,
                    JSONObject().apply {
                        put("token", token)
                        put("userid", accounts.kugouUserId.orEmpty())
                        put("vip_token", accounts.kugouVipToken.orEmpty())
                    },
                )
            }
        }
        return JSONObject().apply {
            put("version", 1)
            put("client", "PixelPlayer/${BuildConfig.VERSION_NAME} (Android)")
            put("accounts", accountJson)
        }.toString()
    }

    private fun Map<String, String>.toJson(): JSONObject = JSONObject().apply {
        forEach { (name, value) -> put(name, value) }
    }
}
