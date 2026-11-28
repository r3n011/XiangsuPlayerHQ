package com.theveloper.pixelplay.data.research

import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 请求签名（规范 03 §3.1）：除 enroll / notice / notices 外所有请求必须携带
 *
 * ```
 * X-License-Key / X-SignKey-Id / X-Timestamp / X-Nonce / X-Signature / X-Platform / X-App-Version
 * ```
 *
 * canonical 六行（\n 连接，末尾不加换行）：
 * METHOD \n PATH_WITH_QUERY \n sha256_hex(rawBody) \n licenseKey \n timestamp \n nonce
 *
 * 注意：body 哈希必须对**实际发送的字节**计算，调用方要把同一 ByteArray 同时用于
 * 签名与 RequestBody（OkHttp 侧由 [ResearchApi] 统一保证）。
 */
object ResearchSigner {

    /** 服务端允许的时间偏差 ±300s；本地时钟漂移用 serverTimeOffset 校正 */
    const val TIMESTAMP_SKEW_SECONDS = 300L

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun hmacBase64(key: ByteArray, text: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return java.util.Base64.getEncoder().encodeToString(
            mac.doFinal(text.toByteArray(Charsets.UTF_8))
        )
    }

    data class SignedHeaders(
        val licenseKey: String,
        val signKeyId: String,
        val timestamp: Long,
        val nonce: String,
        val signature: String
    ) {
        fun toHeaderMap(appVersion: String): Map<String, String> = mapOf(
            "X-License-Key" to licenseKey,
            "X-SignKey-Id" to signKeyId,
            "X-Timestamp" to timestamp.toString(),
            "X-Nonce" to nonce,
            "X-Signature" to signature,
            "X-Platform" to "android",
            "X-App-Version" to appVersion
        )
    }

    /**
     * 计算 signature：HMAC-SHA256(**signKey 原串的 UTF-8 字节**, canonical)。
     *
     * ⚠️ 服务端是 `crypto.createHmac('sha256', signKey)`——signKey 原串就是密钥，
     * 不要做 Base64 解码（解码成 32 字节后签出来是 SIGNATURE_INVALID）。
     */
    fun computeSignature(
        signKeyRaw: String,
        method: String,
        pathWithQuery: String,
        bodyHashHex: String,
        licenseKey: String,
        timestampSec: Long,
        nonce: String
    ): String {
        val canonical = listOf(
            method.uppercase(),
            pathWithQuery,
            bodyHashHex,
            licenseKey,
            timestampSec.toString(),
            nonce
        ).joinToString("\n")
        return hmacBase64(signKeyRaw.toByteArray(Charsets.UTF_8), canonical)
    }

    /**
     * 生成签名头。
     *
     * @param method HTTP 方法（大写）
     * @param pathWithQuery **必须带 /activity/v1 前缀的全路径**（服务端按 req.originalUrl
     *        校验），例如 "/activity/v1/me"、"/activity/v1/notices?limit=20"（带 query），
     *        不含域名
     * @param rawBody 实际发送的请求体字节；GET 传空数组（空体的哈希）
     * @param serverTimeOffsetSec 本地时钟相对服务端的偏移秒（serverTime - localTime），
     *        请求时间戳加上该偏移，避免 TIMESTAMP_SKEW
     */
    fun sign(
        method: String,
        pathWithQuery: String,
        rawBody: ByteArray,
        licenseKey: String,
        signKeyId: String,
        signKeyRaw: String,
        appVersion: String,
        serverTimeOffsetSec: Long = 0L
    ): SignedHeaders {
        val ts = System.currentTimeMillis() / 1000 + serverTimeOffsetSec
        val nonce = UUID.randomUUID().toString()
        val signature = computeSignature(
            signKeyRaw = signKeyRaw,
            method = method,
            pathWithQuery = pathWithQuery,
            bodyHashHex = sha256Hex(rawBody),
            licenseKey = licenseKey,
            timestampSec = ts,
            nonce = nonce
        )
        return SignedHeaders(
            licenseKey = licenseKey,
            signKeyId = signKeyId,
            timestamp = ts,
            nonce = nonce,
            signature = signature
        )
    }
}
