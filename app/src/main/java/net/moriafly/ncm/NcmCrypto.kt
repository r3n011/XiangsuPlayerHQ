@file:Suppress("FunctionName", "MemberVisibilityCanBePrivate", "unused")

package net.moriafly.ncm

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
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
    const val EAPI_KEY = "e82ckenh8dichen8"

    /** xeapi 静态 key（hex 32 字节，对应 crypto.js xeapiStaticKey） */
    private const val XEAPI_STATIC_KEY_HEX =
        "ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84"

    /** xeapi HMAC-SHA256 签名 key（base64） */
    private const val XEAPI_SIGN_KEY =
        "mUHCwVNWJbunMqAHf5MImuirT6plvs6VSFW62MGHstFQxhBGdEoIhLItH3djc4+FB/OKty3+lL2rGeoFBpVe5g=="

    /** X25519 SPKI DER 头（RFC 8410 id-X25519），用于把 32 字节裸公钥包装成 SPKI */
    private val X25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00)

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

    // ════════════════════════════════════════════════════════════
    // xeapi（参考 api-enhanced/util/crypto.js 的 xeapi 系列）
    // 新版安卓接口（如 /api/song/enhance/player/url/v1）已改用 xeapi 加密，
    // 替代旧的 weapi/eapi，避免被风控返回 405「操作频繁」。
    // ════════════════════════════════════════════════════════════

    data class XeapiResult(val B: String, val S: String, val R: String) {
        fun toFormBody(): String = "B=${uenc(B)}&S=${uenc(S)}&R=${uenc(R)}"
    }

    /** 从 register_xeapikey 接口返回的 encryptedData(base64) 解出公钥状态 {version, publicKey, sk} */
    fun xeapiDecryptPublicKey(encryptedDataBase64: String): Map<String, Any?> {
        val staticKey = XEAPI_STATIC_KEY_HEX.hexToBytes()
        val raw = aesEcbDecryptBytes(staticKey, Base64.getDecoder().decode(encryptedDataBase64))
        return runCatching {
            NcmJson.parseAny(String(raw, Charsets.UTF_8)) as? Map<String, Any?> ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    /** xeapiSign：HMAC-SHA256(timestamp+nonce, signKey) → base64 */
    fun xeapiSign(timestamp: Long, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Base64.getDecoder().decode(XEAPI_SIGN_KEY), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(
            mac.doFinal("$timestamp$nonce".toByteArray(Charsets.UTF_8))
        )
    }

    /**
     * xeapi 加密：返回请求体 {B, S, R}。
     * @param uri            原始 eapi 路径（如 /api/song/enhance/player/url/v1）
     * @param data           form 参数（会自动剔除 e_r）
     * @param publicKeyState 由 [xeapiDecryptPublicKey] 得到的 {version, publicKey, sk}
     * @param sessionKey     可选：请求成功后响应头 x-encr-sskey（base64），用于会话内复用
     */
    fun xeapi(
        uri: String,
        data: Map<String, Any?>,
        publicKeyState: Map<String, Any?>,
        sessionKey: String? = null,
        sessionId: String = "",
        os: String = "android",
    ): XeapiResult {
        val staticKey = XEAPI_STATIC_KEY_HEX.hexToBytes()
        val activeSessionKey = sessionKey?.takeIf { it.isNotBlank() }?.toByteArray(Charsets.UTF_8)
        val dynamicKey = activeSessionKey ?: randomBytes(16)
        val plaintext = buildXeapiPlaintext(uri, data)
        val b = aesEcbEncryptBytes(dynamicKey, xeapiMidTransform(aesEcbEncryptBytes(staticKey, plaintext)))
        val s = xeapiEncryptS(dynamicKey, publicKeyState, os)
        val r = aesEcbEncryptBytes(
            staticKey,
            "${publicKeyState["version"] ?: ""}|${if (activeSessionKey != null) sessionId else ""}"
                .toByteArray(Charsets.UTF_8),
        )
        return XeapiResult(
            B = Base64.getEncoder().encodeToString(b),
            S = Base64.getEncoder().encodeToString(s),
            R = Base64.getEncoder().encodeToString(r),
        )
    }

    /** xeapi 响应解密：AES-ECB(eapiKey) → 可选 gzip → JSON */
    fun xeapiResDecrypt(body: ByteArray): Any? {
        // 注意：eapiKey 是 16 字节 ASCII 字面量（非 hex 字符串），直接用其 UTF-8 字节作密钥
        val decrypted = runCatching {
            aesEcbDecryptBytes(EAPI_KEY.toByteArray(Charsets.UTF_8), body)
        }.getOrNull() ?: return null
        val plain = if (decrypted.size >= 2 && decrypted[0] == 0x1F.toByte() && decrypted[1] == 0x8B.toByte()) {
            runCatching { GZIPInputStream(decrypted.inputStream()).readBytes() }.getOrDefault(decrypted)
        } else decrypted
        return runCatching { NcmJson.parseAny(String(plain, Charsets.UTF_8)) }.getOrNull()
    }

    /** xeapi 加密原始字节层（供底层调度器在对齐时复用） */
    fun xeapiEncryptBytes(
        uri: String,
        data: Map<String, Any?>,
        publicKeyState: Map<String, Any?>,
        sessionKey: String? = null,
        sessionId: String = "",
        os: String = "android",
    ): ByteArray {
        val result = xeapi(uri, data, publicKeyState, sessionKey, sessionId, os)
        return result.toFormBody().toByteArray(Charsets.UTF_8)
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

    // ─── xeapi 内部原语（对齐 api-enhanced/util/crypto.js） ───────────

    /** AES/ECB/PKCS5Padding 字节级加密 */
    private fun aesEcbEncryptBytes(key: ByteArray, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return c.doFinal(plain)
    }

    /** AES/ECB/PKCS5Padding 字节级解密 */
    private fun aesEcbDecryptBytes(key: ByteArray, cipher: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return c.doFinal(cipher)
    }

    private fun randomBytes(len: Int): ByteArray = ByteArray(len).also { SecureRandom().nextBytes(it) }

    /** xeapi 请求体构建：{contentType?, method?, queryString, body(base64)} → JSON 字节 */
    private fun buildXeapiPlaintext(
        uri: String,
        data: Map<String, Any?>,
        method: String = "POST",
        contentType: String = "application/x-www-form-urlencoded;charset=utf-8",
    ): ByteArray {
        val fields = LinkedHashMap<String, String>()
        val mediaType = contentType.substringBefore(';').lowercase()
        if (mediaType != "application/x-www-form-urlencoded") fields["contentType"] = contentType
        if (method.uppercase() != "POST") fields["method"] = method.uppercase()

        val qIdx = uri.indexOf('?')
        if (qIdx >= 0) fields["queryString"] = uri.substring(qIdx + 1)

        if (data.isNotEmpty()) {
            val bodyData = data.toMutableMap()
            bodyData.remove("e_r")
            val body = bodyData.entries.joinToString("&") { (k, v) ->
                uenc(k) + "=" + uenc(v?.toString() ?: "")
            }
            fields["body"] = Base64.getEncoder().encodeToString(body.toByteArray(Charsets.UTF_8))
        }

        fields["queryString"] = fields["queryString"]?.let { "$it&e_r=true" } ?: "e_r=true"
        return NcmJson.toJsonString(fields).toByteArray(Charsets.UTF_8)
    }

    /** xeapiMidTransform：异或随机滚转 + base64 轮转 */
    private fun xeapiMidTransform(ciphertext: ByteArray): ByteArray {
        val random = randomBytes(16)
        val xored = ByteArray(ciphertext.size) { (ciphertext[it].toInt() xor random[it and 0x0F].toInt()).toByte() }
        val b64 = Base64.getEncoder().encode(xored)
        val rot = if (b64.isNotEmpty()) (random[0].toInt() and 0x0F) % b64.size else 0
        return random + b64.copyOfRange(rot, b64.size) + b64.copyOfRange(0, rot)
    }

    /** deriveX25519AesKey：HKDF-like 派生 16 字节 AES key */
    private fun deriveX25519AesKey(sharedSecret: ByteArray, ephemeralRaw: ByteArray): ByteArray {
        val zeros = ByteArray(32)
        val prk = hmacSha256(zeros, if (sharedSecret.isEmpty()) ByteArray(32) else sharedSecret)
        val okm = hmacSha256(prk, ephemeralRaw + byteArrayOf(1))
        return okm.copyOfRange(0, 16)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * X25519 ECDH：用临时密钥对与服务器公钥做 ECDH，派生 AES-GCM 加密
     * 动态密钥/平台/sk，返回 [临时公钥原始32字节 + iv + 密文 + tag]。
     */
    private fun xeapiEncryptS(dynamicKey: ByteArray, publicKeyState: Map<String, Any?>, os: String): ByteArray {
        val peerRaw = Base64.getDecoder().decode(publicKeyState["publicKey"] as? String ?: "")
        val peerSpki = X25519_SPKI_PREFIX + peerRaw
        val kpg = KeyPairGenerator.getInstance("X25519")
        val kp = kpg.generateKeyPair()
        val ephemeralDer = kp.public.encoded
        val ephemeralRaw = ephemeralDer.copyOfRange(ephemeralDer.size - 32, ephemeralDer.size)
        val ka = KeyAgreement.getInstance("X25519")
        ka.init(kp.private)
        val peerPub = KeyFactory.getInstance("X25519").generatePublic(X509EncodedKeySpec(peerSpki))
        ka.doPhase(peerPub, true)
        val sharedSecret = ka.generateSecret()
        val aesKey = deriveX25519AesKey(sharedSecret, ephemeralRaw)
        val iv = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val sk = publicKeyState["sk"]?.toString() ?: ""
        val plaintext = "${Base64.getEncoder().encodeToString(dynamicKey)}|$os|$sk"
            .toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(plaintext)
        // GCM：doFinal 返回的密文尾部已追加 16 字节认证标签，无需再额外取 tag
        return ephemeralRaw + iv + encrypted
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
