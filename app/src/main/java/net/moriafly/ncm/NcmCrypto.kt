@file:Suppress("FunctionName", "MemberVisibilityCanBePrivate", "unused")

package net.moriafly.ncm

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 网易云音乐加密核心 —— Kotlin 移植
 * 参考: NeteaseCloudMusicApi-main/util/crypto.js
 *
 * 对应 JS 三个加密方法：
 * - weapi   : (JSONObject) → { params, encSecKey } （POST form）
 * - linuxapi: (JSONObject) → { eparams }  （POST form，AES-ECB hex）
 * - eapi    : (url, obj)   → { params }   （POST form，AES-ECB hex + MD5 摘要）
 *
 * 依赖：JDK javax.crypto（AES）、JDK java.security（RSA X509 key）、JDK Base64/MessageDigest
 * 不需要 BouncyCastle，纯 JDK 可运行。
 */
object NcmCrypto {

    // ============================================================
    // 常量（与 crypto.js 一字不差）
    // ============================================================

    /** AES IV（与 JS 完全一致） */
    private const val IV = "0102030405060708"

    /** weapi 预设 AES key（第一轮 CBC 加密用） */
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"

    /** linuxapi AES-ECB key */
    private const val LINUXAPI_KEY = "rFgB&h#%2?^eDg:Q"

    /** eapi AES-ECB key（16 字节） */
    private const val EAPI_KEY = "e82ckenh8dichen8"

    /** weapi 的随机 16 字节 secretKey 从 base62 生成 */
    private const val BASE62 =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** weapi RSA 公钥（PEM 原文抄自 crypto.js） */
    private const val PUBLIC_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
-----END PUBLIC KEY-----"""

    /** RSA 公钥解析成的 Java RSAPublicKey（懒加载，只解析一次） */
    private val rsaPublicKey: RSAPublicKey by lazy {
        val pem = PUBLIC_KEY_PEM
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("-----") }
            .joinToString("")
        val der = Base64.getDecoder().decode(pem)
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der)) as RSAPublicKey
    }

    private val secureRandom: SecureRandom by lazy { SecureRandom() }

    // ============================================================
    // 公开 API（与原 crypto.js 同名）
    // ============================================================

    /**
     * weapi 加密（PC 端）：
     *   params = AES-CBC(AES-CBC(JSON, presetKey, iv), randomKey, iv).toBase64
     *   encSecKey = RSA_NO_PADDING(randomKey 倒序字符串, publicKey).toUpperCaseHex
     */
    fun weapi(obj: Map<String, Any?>): WeapiResult {
        val text = NcmJson.toJsonString(obj)
        val secretKey = randomBase62(16)
        val encFirst = aesCbcEncryptToBase64(text, PRESET_KEY, IV)
        val encSecond = aesCbcEncryptToBase64(encFirst, secretKey, IV)
        val encSecKey = rsaEncryptNoPadding(secretKey.reversed())
        return WeapiResult(params = encSecond, encSecKey = encSecKey)
    }

    /**
     * linuxapi 加密（Linux 客户端）：
     *   eparams = AES-ECB(JSON, linuxapiKey).toUpperCaseHex
     */
    fun linuxapi(obj: Map<String, Any?>): LinuxapiResult {
        val text = NcmJson.toJsonString(obj)
        val hex = aesEcbEncryptToHex(text, LINUXAPI_KEY)
        return LinuxapiResult(eparams = hex)
    }

    /**
     * eapi 加密（Android 客户端）：
     *   digest = md5("nobody${url}use${json}md5forencrypt")
     *   data   = "${url}-36cd479b6b5-${json}-36cd479b6b5-${digest}"
     *   params = AES-ECB(data, eapiKey).toUpperCaseHex
     */
    fun eapi(url: String, obj: Any?): EapiResult {
        val json = when (obj) {
            null -> ""
            is Map<*, *> -> NcmJson.toJsonString(obj as Map<String, Any?>)
            is String -> obj
            else -> obj.toString()
        }
        val message = "nobody${url}use${json}md5forencrypt"
        val digest = md5(message)
        val data = "${url}-36cd479b6b5-${json}-36cd479b6b5-${digest}"
        return EapiResult(params = aesEcbEncryptToHex(data, EAPI_KEY))
    }

    /** eapi 响应解密：AES-ECB hex → UTF-8 JSON */
    fun eapiResDecrypt(encryptedHex: String): Any? {
        val json = aesEcbDecryptFromHex(encryptedHex, EAPI_KEY)
        return runCatching { NcmJson.parseAny(json) }.getOrNull()
    }

    /** eapi 请求体解密（调试用） */
    fun eapiReqDecrypt(encryptedHex: String): Pair<String, Any?>? {
        val raw = aesEcbDecryptFromHex(encryptedHex, EAPI_KEY)
        val match = Regex("""(.*?)-36cd479b6b5-(.*?)-36cd479b6b5-(.*)""").find(raw) ?: return null
        val (_, url, data) = match.groupValues
        val parsed = runCatching { NcmJson.parseAny(data) }.getOrNull() ?: data
        return url to parsed
    }

    data class WeapiResult(val params: String, val encSecKey: String) {
        fun toFormBody(): String = "params=${uenc(params)}&encSecKey=${uenc(encSecKey)}"
    }

    data class LinuxapiResult(val eparams: String) {
        fun toFormBody(): String = "eparams=${uenc(eparams)}"
    }

    data class EapiResult(val params: String) {
        fun toFormBody(): String = "params=${uenc(params)}"
    }

    // ============================================================
    // 内部原语
    // ============================================================

    private fun randomBase62(len: Int): String {
        val sb = StringBuilder(len)
        for (i in 0 until len) sb.append(BASE62[Random.nextInt(62)])
        return sb.toString()
    }

    /** AES/CBC/PKCS5Padding → Base64（与 CryptoJS 默认完全一致） */
    private fun aesCbcEncryptToBase64(plain: String, key: String, iv: String): String {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        val out = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(out)
    }

    /** AES/ECB/PKCS5Padding → 大写 HEX（CryptoJS.enc.Hex） */
    private fun aesEcbEncryptToHex(plain: String, key: String): String {
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return c.doFinal(plain.toByteArray(Charsets.UTF_8)).toHexUpper()
    }

    /** AES/ECB/PKCS5Padding 大写 HEX → UTF-8 明文（eapiResDecrypt 用） */
    private fun aesEcbDecryptFromHex(hex: String, key: String): String {
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        val bytes = c.doFinal(hex.hexToBytes())
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * RSA ECB NO_PADDING 加密（对应 forge.pki.publicKey.encrypt(str, 'NONE')）：
     * - 输入长度必须 <= modulusLen - 0 （没有填充，需要前置 00 直到等于 modulusLen）
     * - 网易云实际：将 secretKey(16 字节) 左侧补 00 到 128 字节 = RSA 1024 modulusLen
     * - 输出小写 hex（对齐原版 crypto.js 的 forge.util.bytesToHex）
     */
    private fun rsaEncryptNoPadding(plain: String): String {
        val modulusBytes = (rsaPublicKey.modulus.bitLength() + 7) / 8   // 1024/8 = 128
        val plainBytes = plain.toByteArray(Charsets.UTF_8)
        require(plainBytes.size <= modulusBytes) {
            "RSA plain longer than modulus ($modulusBytes)"
        }
        // NONE padding: 左填充 0x00 到 modulus 字节
        val padded = ByteArray(modulusBytes)
        System.arraycopy(
            plainBytes, 0,
            padded, modulusBytes - plainBytes.size,
            plainBytes.size,
        )
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
        return cipher.doFinal(padded).toHexLower()
    }

    private fun md5(text: String): String {
        val d = MessageDigest.getInstance("MD5")
        return d.digest(text.toByteArray(Charsets.UTF_8)).toHexLower()
    }

    // ============================================================
    // 扩展
    // ============================================================

    private fun ByteArray.toHexUpper(): String = joinToString("") { "%02X".format(it) }
    private fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "hex string length must be even" }
        return chunkedSequence(2).map { it.toInt(16).toByte() }.toList().toByteArray()
    }

    private fun uenc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
}
