package com.theveloper.pixelplay.data.github

import android.util.Base64
import com.theveloper.pixelplay.BuildConfig

/**
 * GitHub PAT（Personal Access Token）的运行时访问入口。
 *
 * 构建期由 app/build.gradle.kts 的 obfuscateSecret() 把 local.properties 中的 `github.token`
 * 以 XOR + Base64 混淆后写入 [BuildConfig.GITHUB_TOKEN_OBF]，明文不会再出现在 DEX 字符串常量池里，
 * 因此无法用 `strings` 之类的工具从 APK 中直接提取。此处用同一密钥解码还原。
 *
 * 说明：这是混淆而非加密，密钥随包内置，仅用于提高静态提取门槛，不能抵御逆向分析。
 * 未在 local.properties 配置 `github.token` 时 [value] 为空串，调用方应跳过 Authorization 头。
 */
internal object GitHubToken {

    /** 必须与 app/build.gradle.kts 中 obfuscateSecret() 的默认 key 保持一致 */
    private const val XOR_KEY = "pixelplay-gh-token-v1"

    /** 解码后的 PAT；空串表示未配置 */
    val value: String by lazy { decode(BuildConfig.GITHUB_TOKEN_OBF) }

    private fun decode(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val key = XOR_KEY.toByteArray(Charsets.UTF_8)
            val raw = ByteArray(bytes.size) { i ->
                (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            String(raw, Charsets.UTF_8)
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "Failed to decode GitHub token: ${t.message}")
            ""
        }
    }
}
