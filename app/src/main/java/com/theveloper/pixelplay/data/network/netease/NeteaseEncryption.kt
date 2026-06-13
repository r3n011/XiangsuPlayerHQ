package com.theveloper.pixelplay.data.network.netease

import android.annotation.SuppressLint
import android.util.Base64
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Netease Cloud Music crypto utility.
 * Supports all 4 encryption modes used by the API.
 *
 * Reference: NeriPlayer's NeteaseCrypto.kt (GPL-3.0)
 */
object NeteaseEncryption {

    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val LINUX_KEY = "rFgB&h#%2?^eDg:Q"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_FORMAT = "%s-36cd479b6b5-%s-36cd479b6b5-%s"
    private const val EAPI_SALT = "nobody%suse%smd5forencrypt"
    private const val PUBLIC_KEY_PEM = """
        -----BEGIN PUBLIC KEY-----
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFb
        t7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZ
        MldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
        -----END PUBLIC KEY-----
    """

    private val secureRandom = SecureRandom()

    private fun randomKey(): String {
        return buildString { repeat(16) { append(BASE62[secureRandom.nextInt(BASE62.length)]) } }
    }

    // ─── AES ───────────────────────────────────────────────────────────

    @SuppressLint("GetInstance")
    private fun aesEncrypt(text: String, key: String, iv: String, mode: String, format: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        val cipher = when (mode.lowercase(Locale.ROOT)) {
            "cbc" -> {
                // CBC with PKCS#7 is required for compatibility with the Netease API
                // lgtm[java/weak-cryptographic-algorithm] Netease's public API requires this wire format.
                Cipher.getInstance("AES/CBC/PKCS7Padding").apply {
                    init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv.toByteArray(StandardCharsets.UTF_8)))
                }
            }
            "ecb" -> {
                // ECB with PKCS#7 is required for compatibility with the Netease API
                // lgtm[java/weak-cryptographic-algorithm] Netease's public API requires this wire format.
                Cipher.getInstance("AES/ECB/PKCS7Padding").apply {
                    init(Cipher.ENCRYPT_MODE, secretKey)
                }
            }
            else -> throw IllegalArgumentException("Unknown AES mode: $mode")
        }
        val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        return when (format.lowercase(Locale.ROOT)) {
            "base64" -> Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "hex" -> encrypted.joinToString("") { "%02x".format(it) }
            else -> throw IllegalArgumentException("Unknown format: $format")
        }
    }

    // ─── RSA ───────────────────────────────────────────────────────────

    private fun rsaEncrypt(text: String): String {
        return try {
            val cleanedKey = PUBLIC_KEY_PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            val keyBytes = Base64.decode(cleanedKey, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val pubKey = KeyFactory.getInstance("RSA")
                .generatePublic(keySpec) as java.security.interfaces.RSAPublicKey

            val message = BigInteger(1, text.toByteArray(StandardCharsets.UTF_8))
            val result = message.modPow(pubKey.publicExponent, pubKey.modulus)

            var bytes = result.toByteArray()
            if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
                bytes = bytes.copyOfRange(1, bytes.size)
            }
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            throw RuntimeException("RSA encryption failed", e)
        }
    }

    // ─── MD5 ───────────────────────────────────────────────────────────

    fun md5Hex(data: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(data.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // ─── Public Encrypt Methods ────────────────────────────────────────

    /**
     * WeAPI encryption: double AES-CBC + RSA.
     * Used for search, playlists, lyrics, etc.
     */
    fun weApiEncrypt(payload: Map<String, Any>): Map<String, String> {
        val json = toJson(payload)
        val secretKey = randomKey()
        val enc1 = aesEncrypt(json, PRESET_KEY, IV, "cbc", "base64")
        val params = aesEncrypt(enc1, secretKey, IV, "cbc", "base64")
        val encSecKey = rsaEncrypt(secretKey.reversed())
        return mapOf("params" to params, "encSecKey" to encSecKey)
    }

    /**
     * EAPI encryption: AES-ECB with MD5 digest.
     * Used for login, song URL resolution.
     */
    fun eApiEncrypt(url: String, payload: Map<String, Any>): Map<String, String> {
        val data = toJson(payload)
        val apiUrl = url.replace("/eapi", "/api")
        val message = String.format(
            EAPI_FORMAT,
            apiUrl, data,
            md5Hex(String.format(EAPI_SALT, apiUrl, data))
        )
        val cipher = aesEncrypt(message, EAPI_KEY, "", "ecb", "hex")
            .uppercase(Locale.ROOT)
        return mapOf("params" to cipher)
    }

    /**
     * Linux API encryption: AES-ECB with fixed key.
     */
    fun linuxApiEncrypt(payload: Map<String, Any>): Map<String, String> {
        return mapOf("eparams" to aesEncrypt(toJson(payload), LINUX_KEY, "", "ecb", "hex"))
    }

    // ─── JSON Utility ──────────────────────────────────────────────────

    /** Simple JSON serializer (no external deps) matching NeriPlayer's approach */
    private fun toJson(map: Map<String, Any>): String {
        val sb = StringBuilder("{")
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val (k, v) = it.next()
            sb.append("\"").append(k).append("\":")
            sb.append(toJsonValue(v))
            if (it.hasNext()) sb.append(",")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun toJsonValue(v: Any?): String = when (v) {
        null -> "null"
        is String -> jsonQuote(v)
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            toJson(v as Map<String, Any>)
        }
        is List<*> -> v.joinToString(prefix = "[", postfix = "]") { toJsonValue(it) }
        else -> jsonQuote(v.toString())
    }

    private fun jsonQuote(s: String): String {
        val sb = StringBuilder(s.length + 16)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"'  -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
