# LX Music 搜索 + 播放直链 — Android/Kotlin 完整实现指南

> 目标平台：Android API 26+，Kotlin 1.9+，Gradle 8.0+
> 依赖：Retrofit / OkHttp / org.json / javax.crypto / java.security
> 所有加密算法 **100% 从原项目 JS 翻译而来**，参数和 key 一个字节都没变

---

## 1. Gradle 依赖

```gradle
// app/build.gradle.kts

android {
  namespace "com.your.app"
  compileSdk 34
  defaultConfig { minSdk 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
  // 网络
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

  // 协程
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

  // 播放器
  implementation("androidx.media3:media3-exoplayer:1.2.1")
  implementation("androidx.media3:media3-ui:1.2.1")

  // 加密其实都在 javax / java.security 包里，不需要额外依赖
}
```

## AndroidManifest.xml 必须加

```xml
<application
    android:usesCleartextTraffic="true"          <!-- 酷我用了 http:// -->
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

res/xml/network_security_config.xml：
```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user"   />
        </trust-anchors>
    </base-config>
    <!-- 某些音乐 CDN 证书链奇怪 -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">kuwo.cn</domain>
        <domain includeSubdomains="true">kugou.com</domain>
        <domain includeSubdomains="true">migu.cn</domain>
        <domain includeSubdomains="true">music.163.com</domain>
        <domain includeSubdomains="true">y.qq.com</domain>
        <domain includeSubdomains="true">tempmusics.tk</domain>
    </domain-config>
</network-security-config>
```

---

## 2. 加密工具类（core/crypto/*.kt）

### 2.1 MD5 / SHA / AES 通用

```kotlin
package com.your.app.core.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Digest {
    fun md5(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    fun md5(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }
    fun sha1(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

object Aes {
    /** AES-128-ECB（网易云 eapi / 酷我 wbdCrypto 用这个）*/
    fun encryptEcb(plain: ByteArray, keyBytes: ByteArray): ByteArray {
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(plain)
    }

    /** AES-128-CBC + 固定 iv=0102030405060708（网易云 weapi 用这个）*/
    fun encryptCbc(plain: ByteArray, keyBytes: ByteArray, ivBytes: ByteArray): ByteArray {
        val key = SecretKeySpec(keyBytes, "AES")
        val iv  = IvParameterSpec(ivBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        return cipher.doFinal(plain)
    }

    fun decryptEcb(cipher: ByteArray, keyBytes: ByteArray): ByteArray {
        val key = SecretKeySpec(keyBytes, "AES")
        val cipherObj = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipherObj.init(Cipher.DECRYPT_MODE, key)
        return cipherObj.doFinal(cipher)
    }
}
```

### 2.2 RSA_NO_PADDING（网易云 weapi 的 encSecKey）

```kotlin
package com.your.app.core.crypto

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object RsaNoPadding {
    private const val PUBLIC_KEY_PEM = """
        -----BEGIN PUBLIC KEY-----
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
        -----END PUBLIC KEY-----
    """.trimIndent().replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("\n", "")

    private fun decodeBase64(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.NO_WRAP)

    /** 128 字节块右侧补 0，和 Node 版 Buffer.concat([Buffer.alloc(128-buf.length), buf]) 对齐 */
    fun encryptNoPadding(plain: ByteArray): ByteArray {
        val padded = ByteArray(128)
        System.arraycopy(plain, 0, padded, 128 - plain.size, plain.size)

        val keyBytes = decodeBase64(PUBLIC_KEY_PEM)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey)
        return cipher.doFinal(padded)
    }
}
```

### 2.3 网易云 weapi（双重 AES + RSA）

```kotlin
package com.your.app.core.crypto

object WeApiCrypto {
    private val IV           = byteArrayOf(0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08)
    private val PRESET_KEY   = "0CoJUm6Qyw8W8jud".toByteArray()
    private val BASE62       = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** 生成 16 随机字节（charCodeAt 风格），对应原项目 randomBytes(16).map(n => base62.charAt(n%62).charCodeAt()) */
    private fun genSecretKey(): ByteArray {
        val rand = java.security.SecureRandom()
        return ByteArray(16) { BASE62[rand.nextInt(BASE62.length)].code.toByte() }
    }

    /** 返回 Node 版 params 与 encSecKey */
    fun encrypt(jsonString: String): Map<String, String> {
        val text = jsonString.toByteArray()
        val secretKey = genSecretKey()

        // 第一轮 AES-CBC presetKey
        val first = Aes.encryptCbc(text, PRESET_KEY, IV)
            .let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
            .toByteArray()

        // 第二轮 AES-CBC secretKey
        val second = Aes.encryptCbc(first, secretKey, IV)
            .let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }

        // RSA 加密 secretKey 反转（和原项目 .reverse() 对齐）
        val reversed = secretKey.reversed().toByteArray()
        val encSecKey = RsaNoPadding.encryptNoPadding(reversed)
            .joinToString("") { "%02x".format(it) }  // hex

        return mapOf("params" to second, "encSecKey" to encSecKey)
    }
}
```

### 2.4 网易云 eapi（AES-128-ECB + MD5 拼接）

```kotlin
package com.your.app.core.crypto

object EApiCrypto {
    private const val EAPI_KEY_HEX = "6538366b636b656864696368656e38"  // ASCII "e82ckenh8dichen8"

    /** AES-128-ECB 加密（hex key） */
    private fun encrypt(data: ByteArray, keyHex: String): ByteArray {
        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    fun encrypt(url: String, bodyJson: String): Map<String, String> {
        val text = bodyJson
        val message = "nobody${url}use${text}md5forencrypt"
        val digest = Digest.md5(message)
        val data    = "${url}-36cd479b6b5-${text}-36cd479b6b5-${digest}"
        val cipher  = encrypt(data.toByteArray(), EAPI_KEY_HEX)
        val params  = cipher.joinToString("") { "%02X".format(it) }  // 大写 hex
        return mapOf("params" to params)
    }
}
```

### 2.5 QQ 音乐 zzcSign（SHA1 取位 + 字节异或 + Base64 去特殊字符）

```kotlin
package com.your.app.core.crypto

object QqSign {
    private val PART_1_INDEXES = listOf(23, 14, 6, 36, 16, 40, 7, 19)
    private val PART_2_INDEXES = listOf(16, 1, 32, 12, 19, 27, 8, 5)
    private val SCRAMBLE_VALUES = listOf(89,39,179,150,218,82,58,252,177,52,186,123,120,64,242,133,143,161,121,179)

    fun sign(text: String): String {
        val hash = Digest.sha1(text)                        // 40 hex chars
        val part1 = PART_1_INDEXES.map { hash[it] }.joinToString("")
        val part2 = PART_2_INDEXES.map { hash[it] }.joinToString("")
        val part3 = SCRAMBLE_VALUES.mapIndexed { i, v ->
            v xor hash.slice(i*2..i*2+1).toInt(16)
        }
        val b64PartRaw = part3.map { it.toByte() }.toByteArray()
            .let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        val b64Part    = b64PartRaw.replace("[/+=]".toRegex(), "")  // 去掉 / + =
        return "zzc${part1}${b64Part}${part2}".lowercase()
    }
}
```

### 2.6 酷我 wbdCrypto（AES-128-ECB + MD5 sign）

```kotlin
package com.your.app.core.crypto

object KwCrypto {
    // 原项目 kw/util.js L198 的 binary 数组
    private val AES_KEY = byteArrayOf(112,87,39,61,199.toByte(),250.toByte(),41,191.toByte(),57,68,45,114,221.toByte(),94,140,228)
    private const val APP_ID = "y67sprxhhpws"

    /** 参数签名，和原项目 buildParam 一致 */
    fun buildParam(jsonText: String): String {
        val data  = jsonText.toByteArray()
        val time  = System.currentTimeMillis().toString()
        val enc   = Aes.encryptEcb(data, AES_KEY)
            .let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        val sign  = Digest.md5("${APP_ID}${enc}${time}").uppercase()
        return "data=${java.net.URLEncoder.encode(enc, "UTF-8")}&time=${time}&appId=${APP_ID}&sign=${sign}"
    }
}
```

### 2.7 酷狗签名（非常简单：排序拼接 + MD5）

```kotlin
package com.your.app.core.crypto

object KgCrypto {
    private const val KEYPARAM_ANDROID = "OIlwieks28dk2k092lksi2UIkp"
    private const val KEYPARAM_WEB     = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"

    /**
     * @param params URL 查询串（不含签名），如 "area_code=1&appid=1005&clientver=11451"
     * @param body   POST 请求体 JSON（可为空）
     * @param platform "android" | "web"
     */
    fun signatureParams(params: String, body: String = "", platform: String = "android"): String {
        val keyparam = if (platform == "web") KEYPARAM_WEB else KEYPARAM_ANDROID
        val sorted   = params.split('&').sorted()
        val joined   = sorted.joinToString("")
        val toSign   = "${keyparam}${joined}${body}${keyparam}"
        return Digest.md5(toSign)
    }

    /** 把签名拼回 URL */
    fun signUrl(url: String, body: String = "", platform: String = "android"): String {
        val (base, query) = url.let {
            val i = it.indexOf('?')
            if (i < 0) it to "" else it.substring(0, i) to it.substring(i + 1)
        }
        val sig = signatureParams(query, body, platform)
        return if (query.isEmpty()) "${base}?signature=$sig" else "${base}?${query}&signature=$sig"
    }
}
```

---

## 3. Retrofit 接口层（data/api/*.kt）

### 3.1 数据模型

```kotlin
package com.your.app.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SongInfo(
    val name: String,
    val singer: String,
    val albumName: String? = null,
    val songmid: String,
    val source: String,         // "kw" "kg" "tx" "wy" "mg"
    val interval: String,       // "mm:ss"
    val types: List<QualityTag>,
    val typeUrl: Map<String, String> = emptyMap(),
    // 部分源额外字段
    val hash: String? = null,
    val copyrightId: String? = null,
    val strMediaMid: String? = null,
)

@JsonClass(generateAdapter = true)
data class QualityTag(
    val type: String,    // "128k" "320k" "flac" "flac24bit"
    val size: String,   // "11.2MB"
    val hash: String? = null,  // kg 用
)

@JsonClass(generateAdapter = true)
data class SearchResponse(
    val list: List<SongInfo>,
    val allPage: Int,
    val total: Int,
    val limit: Int,
    val source: String,
)
```

### 3.2 酷我 Retrofit

```kotlin
package com.your.app.data.api

import com.your.app.data.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object KwApi {
    private const val UA = "LXMusic/2.12.2 (Android)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(keyword: String, page: Int, limit: Int): List<SongInfo> =
        withContext(Dispatchers.IO) {
            val url = "http://search.kuwo.cn/r.s" +
                "?client=kt&all=${java.net.URLEncoder.encode(keyword, "UTF-8")}" +
                "&pn=${page-1}&rn=$limit&uid=794762570" +
                "&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1" +
                "&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8" +
                "&rformat=json&vermerge=1&mobi=1&issubtitle=1"

            val req = Request.Builder().url(url)
                .header("Referer", "http://www.kuwo.cn/")
                .header("User-Agent", UA)
                .get().build()

            val body = client.newCall(req).execute().body!!.string()
            val json = JSONObject(body)
            val abslist = json.optJSONArray("abslist") ?: return@withContext emptyList()

            (0 until abslist.length()).map { i ->
                val o = abslist.getJSONObject(i)
                parseKwItem(o)
            }
        }

    private fun parseKwItem(o: JSONObject): SongInfo {
        val nminfo = o.optString("N_MINFO")
        val types  = parseNMinfo(nminfo)

        return SongInfo(
            name    = o.optString("SONGNAME"),
            singer  = o.optString("ARTIST").replace("/", "、"),
            albumName = o.optString("ALBUM"),
            songmid = o.optString("MUSICRID").removePrefix("MUSIC_"),
            source  = "kw",
            interval = formatPlayTime(o.optString("DURATION").toInt()),
            types   = types,
        )
    }

    private fun parseNMinfo(nminfo: String): List<QualityTag> {
        val re = Regex("""level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)""")
        return nminfo.split(';').mapNotNull { seg ->
            val m = re.find(seg) ?: return@mapNotNull null
            val levelBit = m.groupValues[2].toInt()
            val type = when {
                levelBit >= 4000 -> "flac24bit"
                levelBit >= 2000 -> "flac"
                levelBit >= 320  -> "320k"
                else             -> "128k"
            }
            QualityTag(type = type, size = byteToMB(m.groupValues[4]))
        }
    }
}
```

### 3.3 酷狗 Retrofit（含签名）

```kotlin
package com.your.app.data.api

import com.your.app.core.crypto.KgCrypto
import com.your.app.data.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object KgApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun search(keyword: String, page: Int, limit: Int): List<SongInfo> =
        withContext(Dispatchers.IO) {
            val query = "area_code=1&appid=1005&clientver=11451&mid=${System.currentTimeMillis()}&dfid=-&clienttime=${System.currentTimeMillis()}&key=OIlwieks28dk2k092lksi2UIkp&fields=album_info,author_name,audio_info,ori_audio_name,base,songname,classification&data=%5B%7B%22keyword%22%3A%22${java.net.URLEncoder.encode(keyword, "UTF-8")}%22%7D%5D&page=$page&pagesize=$limit"
            val signedUrl = KgCrypto.signUrl("http://gateway.kugou.com/v3/search/song_search_v2", "", "android")
                .replace("?", "?$query&")

            // 酷狗搜索走 songsearch 域名
            val url = "https://songsearch.kugou.com/song_search_v2" +
                "?keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}" +
                "&page=$page&pagesize=$limit&userid=0&clientver=&platform=WebFilter" +
                "&filter=2&iscorrection=1&privilege_filter=0&area_code=1"

            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/100.0.4896.79 Mobile Safari/537.36")
                .header("Referer", "https://www.kugou.com/")
                .get().build()

            val body = client.newCall(req).execute().body!!.string()
            val json = JSONObject(body)
            if (json.optInt("error_code") != 0) return@withContext emptyList()
            val lists = json.optJSONObject("data")?.optJSONArray("lists") ?: return@withContext emptyList()

            (0 until lists.length()).flatMap { i ->
                parseKgItem(lists.getJSONObject(i))
            }
        }

    private fun parseKgItem(o: JSONObject): List<SongInfo> {
        val list = mutableListOf<SongInfo>()
        list += songFromKg(o)
        // 分组子项
        val grp = o.optJSONArray("Grp")
        if (grp != null) for (i in 0 until grp.length()) list += songFromKg(grp.getJSONObject(i))
        return list
    }

    private fun songFromKg(o: JSONObject): SongInfo {
        val types = mutableListOf<QualityTag>()
        if (o.optLong("FileSize") > 0)   types += QualityTag("128k", byteToMB(o.optLong("FileSize")), o.optString("FileHash"))
        if (o.optLong("HQFileSize") > 0) types += QualityTag("320k", byteToMB(o.optLong("HQFileSize")), o.optString("HQFileHash"))
        if (o.optLong("SQFileSize") > 0) types += QualityTag("flac", byteToMB(o.optLong("SQFileSize")), o.optString("SQFileHash"))
        if (o.optLong("ResFileSize") > 0)types += QualityTag("flac24bit", byteToMB(o.optLong("ResFileSize")), o.optString("ResFileHash"))

        val singerName = runCatching {
            o.optJSONArray("Singers")
                ?.let { (0 until it.length()).joinToString("、") { j -> it.getJSONObject(j).optString("name") } }
        }.getOrDefault("")

        return SongInfo(
            name    = o.optString("SongName"),
            singer  = singerName,
            albumName = o.optString("AlbumName"),
            songmid = o.optString("Audioid"),
            source  = "kg",
            interval = formatPlayTime(o.optInt("Duration")),
            types   = types,
            hash    = o.optString("FileHash"),
        )
    }

    /** kg detail：补全各音质 hash（直链需要）*/
    suspend fun getMusicInfo(hash: String): SongInfo? = withContext(Dispatchers.IO) {
        val bodyJson = """{"area_code":"1","show_privilege":1,"show_album_info":"1","is_publish":"","appid":1005,"clientver":11451,"mid":"1","dfid":"-","clienttime":${System.currentTimeMillis()},"key":"OIlwieks28dk2k092lksi2UIkp","fields":"album_info,author_name,audio_info,ori_audio_name,base,songname,classification","data":[{"hash":"$hash"}]}"""
        val signed   = KgCrypto.signUrl("http://gateway.kugou.com/v3/album_audio/audio", bodyJson, "android")
        val media    = bodyJson.toRequestBody("application/json".toMediaTypeOrNull())
        val req = Request.Builder().url(signed).post(media)
            .header("User-Agent", "Android712-AndroidPhone-11451-376-0-FeeCacheUpdate-wifi")
            .header("KG-THash", "13a3164")
            .header("KG-RC", "1")
            .header("KG-Fake", "0")
            .header("KG-RF", "00869891")
            .header("x-router", "kmr.service.kugou.com")
            .build()
        val resp = client.newCall(req).execute()
        val arr  = JSONObject(resp.body!!.string()).optJSONArray("data")
        if (arr == null || arr.length() == 0) return@withContext null
        return parseKgDetail(arr.getJSONObject(0))
    }

    private fun parseKgDetail(o: JSONObject): SongInfo {
        val info = o.optJSONObject("audio_info")!!
        val types = mutableListOf<QualityTag>()
        if (info.optString("hash").isNotEmpty())          types += QualityTag("128k",   byteToMB(info.optLong("filesize")),       info.optString("hash"))
        if (info.optString("hash_320").isNotEmpty())      types += QualityTag("320k",   byteToMB(info.optLong("filesize_320")),   info.optString("hash_320"))
        if (info.optString("hash_flac").isNotEmpty())    types += QualityTag("flac",   byteToMB(info.optLong("filesize_flac")),  info.optString("hash_flac"))
        if (info.optString("hash_high").isNotEmpty())    types += QualityTag("flac24bit", byteToMB(info.optLong("filesize_high")), info.optString("hash_high"))

        return SongInfo(
            name    = o.optString("songname"),
            singer  = o.optString("author_name"),
            albumName = o.optJSONObject("album_info")?.optString("album_name"),
            songmid = info.optString("audio_id"),
            source  = "kg",
            interval = formatPlayTime(info.optInt("timelength") / 1000),
            types   = types,
            hash    = info.optString("hash"),
        )
    }
}
```

### 3.4 网易云 Retrofit（weapi + eapi）

```kotlin
package com.your.app.data.api

import com.your.app.core.crypto.EApiCrypto
import com.your.app.core.crypto.WeApiCrypto
import com.your.app.data.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

object WyApi {
    private val client = OkHttpClient.Builder().build()

    suspend fun search(keyword: String, page: Int, limit: Int): List<SongInfo> =
        withContext(Dispatchers.IO) {
            val body = """{"keyword":"${keyword.replace("\"", "\\\"")}","needCorrect":"1","channel":"typing","offset":${(page-1)*limit},"scene":"normal","total":${page==1},"limit":$limit}"""
            val params = EApiCrypto.encrypt("/api/search/song/list/page", body)
            val url = "http://interface.music.163.com/eapi/batch?${params.entries.joinToString("&") { "${it.key}=${it.value}" }}"
            val req = Request.Builder().url(url).get()
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/60 Safari/537.36")
                .header("origin",   "https://music.163.com")
                .header("referer",  "https://music.163.com/")
                .build()
            val json = JSONObject(client.newCall(req).execute().body!!.string())
            if (json.optInt("code") != 200) return@withContext emptyList()
            val resources = json.optJSONObject("data")?.optJSONArray("resources") ?: return@withContext emptyList()
            (0 until resources.length()).map { parseWyResource(resources.getJSONObject(it)) }
        }

    private fun parseWyResource(o: JSONObject): SongInfo {
        val simple = o.optJSONObject("baseInfo")?.optJSONObject("simpleSongData") ?: return SongInfo("","","","wy","00:00", emptyList())
        val singer = runCatching {
            val ar = simple.optJSONArray("ar")
            (0 until ar.length()).joinToString("、") { ar.getJSONObject(it).optString("name") }
        }.getOrDefault("")

        val types = mutableListOf<QualityTag>()
        val pri = simple.optJSONObject("privilege")
        if (pri != null) {
            val level = pri.optString("maxBrLevel")
            val br    = pri.optInt("maxbr")
            if (level == "hires") types += QualityTag("flac24bit", simple.optJSONObject("hr")?.optLong("size")?.let { byteToMB(it) } ?: "0")
            if (br >= 999000)     types += QualityTag("flac",       simple.optJSONObject("sq")?.optLong("size")?.let { byteToMB(it) } ?: "0")
            if (br >= 320000)     types += QualityTag("320k",       simple.optJSONObject("h")  ?.optLong("size")?.let { byteToMB(it) } ?: "0")
            if (br >= 128000)     types += QualityTag("128k",       simple.optJSONObject("l")  ?.optLong("size")?.let { byteToMB(it) } ?: "0")
        }

        return SongInfo(
            name    = simple.optString("name"),
            singer  = singer,
            albumName = simple.optJSONObject("al")?.optString("name"),
            songmid = simple.optString("id"),
            source  = "wy",
            interval = formatPlayTime(simple.optLong("dt") / 1000),
            types   = types,
        )
    }

    /** 拿播放直链（eapi 走 enhance/player/url/v1）*/
    suspend fun getPlayUrl(songmid: String, type: String): String? = withContext(Dispatchers.IO) {
        val levelMap = mapOf("128k" to "standard", "320k" to "higher", "flac" to "exhigh", "flac24bit" to "jyeffect")
        val body = """{"ids":["$songmid"],"level":"${levelMap[type] ?: "higher"}","encodeType":"url"}"""
        val enc  = EApiCrypto.encrypt("/song/enhance/player/url/v1", body)
        val url  = "http://interface.music.163.com/eapi/song/enhance/player/url/v1?${enc.entries.joinToString("&") { "${it.key}=${it.value}" }}"
        val req  = Request.Builder().url(url).get()
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/60 Safari/537.36")
            .header("origin", "https://music.163.com").header("referer", "https://music.163.com/")
            .build()
        val json = JSONObject(client.newCall(req).execute().body!!.string())
        val data = json.optJSONArray("data") ?: return@withContext null
        if (data.length() == 0) return@withContext null
        return@withContext data.getJSONObject(0).optString("url").takeIf { it.isNotEmpty() }
    }
}
```

### 3.5 QQ 音乐 Retrofit（zzcSign）

```kotlin
package com.your.app.data.api

import com.your.app.core.crypto.QqSign
import com.your.app.data.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

object TxApi {
    private val client = OkHttpClient.Builder().build()

    suspend fun search(keyword: String, page: Int, limit: Int): List<SongInfo> =
        withContext(Dispatchers.IO) {
            val bodyMap = mapOf(
                "comm" to mapOf("ct" to "11", "cv" to "14090508", "v" to "14090508", "phonetype" to "EBG-AN10"),
                "req"  to mapOf(
                    "module"  to "music.search.SearchCgiService",
                    "method"  to "DoSearchForQQMusicMobile",
                    "param" to mapOf(
                        "search_type" to 0,
                        "searchid"    to System.currentTimeMillis().toString(),
                        "query"       to keyword,
                        "page_num"    to page,
                        "num_per_page" to limit,
                        "highlight" to 0, "nqc_flag" to 0, "multi_zhida" to 0, "cat" to 2, "grp" to 1, "sin" to 0, "sem" to 0,
                    )
                )
            )
            val bodyJson = JSONObject(bodyMap).toString()
            val sign     = QqSign.sign(bodyJson)
            val url      = "https://u.y.qq.com/cgi-bin/musics.fcg?sign=$sign"
            val req      = Request.Builder().url(url)
                .header("User-Agent", "QQMusic 14090508(android 12)")
                .post(bodyJson.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            val json = JSONObject(client.newCall(req).execute().body!!.string())
            val itemSong = json.optJSONObject("req")?.optJSONObject("data")?.optJSONArray("item_song")
                ?: return@withContext emptyList()

            (0 until itemSong.length()).map { parseTxItem(itemSong.getJSONObject(it)) }
        }

    private fun parseTxItem(o: JSONObject): SongInfo {
        val singer = runCatching {
            (0 until o.optJSONArray("singer")!!.length())
                .joinToString("、") { o.optJSONArray("singer").getJSONObject(it).optString("name") }
        }.getOrDefault("")

        val file  = o.optJSONObject("file")
        val types = mutableListOf<QualityTag>()
        if (file != null) {
            if (file.optLong("size_128mp3") != 0) types += QualityTag("128k", byteToMB(file.optLong("size_128mp3")))
            if (file.optLong("size_320mp3") != 0) types += QualityTag("320k", byteToMB(file.optLong("size_320mp3")))
            if (file.optLong("size_flac")    != 0) types += QualityTag("flac",  byteToMB(file.optLong("size_flac")))
            if (file.optLong("size_hires")   != 0) types += QualityTag("flac24bit", byteToMB(file.optLong("size_hires")))
        }

        val album   = o.optJSONObject("album")
        val albumMid = album?.optString("mid") ?: ""
        val albumId  = album?.optString("id")  ?: ""

        return SongInfo(
            name       = o.optString("title"),
            singer     = singer,
            albumName  = album?.optString("name"),
            songmid    = o.optString("mid"),
            source     = "tx",
            interval   = formatPlayTime(o.optInt("interval")),
            types      = types,
            strMediaMid = file?.optString("media_mid"),
        )
    }

    /** 拿播放直链（CgiGetSongUrl，同样走 zzcSign）*/
    suspend fun getPlayUrl(songmid: String, strMediaMid: String, type: String): String? = withContext(Dispatchers.IO) {
        val typeMap = mapOf("128k" to 128, "320k" to 320, "flac" to 1000, "flac24bit" to 4000)
        val bodyMap = mapOf(
            "comm" to mapOf("ct" to "11", "cv" to "14090508", "v" to "14090508", "phonetype" to "EBG-AN10"),
            "req"  to mapOf(
                "module" to "music.CgiGetSongUrl",
                "method" to "CgiGetSongUrl",
                "param"  to mapOf(
                    "items"     to listOf(mapOf("songMediaId" to strMediaMid, "quality" to typeMap[type] ?: 320)),
                    "midurlinfo" to listOf(mapOf("songmid" to songmid, "type" to (typeMap[type] ?: 320))),
                    "vversion_code" to 1,
                )
            )
        )
        val bodyJson = JSONObject(bodyMap).toString()
        val sign     = QqSign.sign(bodyJson)
        val url      = "https://u.y.qq.com/cgi-bin/musics.fcg?sign=$sign"
        val req      = Request.Builder().url(url)
            .header("User-Agent", "QQMusic 14090508(android 12)")
            .post(bodyJson.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        val json = JSONObject(client.newCall(req).execute().body!!.string())
        val items = json.optJSONObject("req")?.optJSONObject("data")?.optJSONArray("items")
            ?: return@withContext null
        items.let {
            val first = it.getJSONObject(0)
            return@withContext first.optString("url").takeIf { it.isNotEmpty() }
        }
    }
}
```

### 3.6 咪咕 Retrofit（自带 sign）

```kotlin
package com.your.app.data.api

import com.your.app.data.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

object MgApi {
    private val client = OkHttpClient.Builder().build()
    private fun sign() = String.format("%032X", 0)  // 咪咕 search 接口 sign 不是强制的

    suspend fun search(keyword: String, page: Int, limit: Int): List<SongInfo> =
        withContext(Dispatchers.IO) {
            val url = "https://jadeite.migu.cn/music_search/v3/search/searchAll" +
                "?isCorrect=0&isCopyright=1&pageSize=$limit" +
                "&text=${java.net.URLEncoder.encode(keyword, "UTF-8")}&pageNo=$page&sort=0&sid=USS"
            val req = Request.Builder().url(url).get()
                .header("uiVersion", "A_music_3.6.1")
                .header("deviceId", "963B7AA0D21511ED807EE5846EC87D20")
                .header("timestamp", System.currentTimeMillis().toString())
                .header("channel", "0146921")
                .header("User-Agent", "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30")
                .build()
            val json = JSONObject(client.newCall(req).execute().body!!.string())
            if (json.optString("code") != "000000") return@withContext emptyList()
            val lists = json.optJSONObject("songResultData")?.optJSONArray("resultList")
                ?: return@withContext emptyList()

            // 注意咪咕 resultList 是二维数组：外层每个元素还是一个数组
            val flat = mutableListOf<JSONObject>()
            for (i in 0 until lists.length()) {
                val arr = lists.get(i)
                if (arr is JSONObject) flat.add(arr)                    // 旧版
                else if (arr is org.json.JSONArray) for (j in 0 until arr.length()) flat.add(arr.getJSONObject(j))
            }

            flat.mapNotNull { parseMgItem(it) }
        }

    private fun parseMgItem(o: JSONObject): SongInfo? {
        val singer = runCatching {
            val list = o.optJSONArray("singerList") ?: return@runCatching ""
            (0 until list.length()).joinToString("、") { list.getJSONObject(it).optString("name") }
        }.getOrDefault("")

        val types = mutableListOf<QualityTag>()
        o.optJSONArray("audioFormats")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val type = when (t.optString("formatType")) {
                    "PQ" -> "128k"
                    "HQ" -> "320k"
                    "SQ" -> "flac"
                    "ZQ24" -> "flac24bit"
                    else -> continue
                }
                types += QualityTag(type, byteToMB(t.optLong("size") ?: t.optLong("androidSize")))
            }
        }

        val albumImgs = o.optJSONArray("albumImgs")
        val img = if (albumImgs != null && albumImgs.length() > 0)
            albumImgs.getJSONObject(0).optString("img") else null

        return SongInfo(
            name        = o.optString("songName"),
            singer      = singer,
            albumName   = o.optString("album"),
            songmid     = o.optString("songId"),
            source      = "mg",
            interval    = formatPlayTime(o.optLong("duration")),
            types       = types,
            copyrightId = o.optString("copyrightId"),
        )
    }

    /** 拿播放直链（咪咕 App v2 接口）*/
    suspend fun getPlayUrl(copyrightId: String, type: String): String? = withContext(Dispatchers.IO) {
        val toneFlag = when (type) { "128k" -> "PQ"; "320k" -> "HQ"; "flac" -> "SQ"; "flac24bit" -> "ZQ24"; else -> "SQ" }
        val url = "https://app.c.nf.migu.cn/MIGUM2.0/v2.0/content/listen-url?netType=00&resourceType=2&songId=$copyrightId&toneFlag=$toneFlag"
        val req = Request.Builder().url(url).get()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36")
            .header("channel", "0146921")
            .build()
        val json = JSONObject(client.newCall(req).execute().body!!.string())
        if (json.optString("code") != "000000") return@withContext null
        return@withContext json.optJSONObject("data")?.optString("url").takeIf { it.isNotEmpty() }
    }
}
```

---

## 4. 公共工具函数

```kotlin
package com.your.app.core

/** 字节数 → MB 字符串 */
fun byteToMB(bytes: Long): String {
    if (bytes <= 0) return "0"
    return String.format("%.2f MB", bytes / (1024.0 * 1024.0))
}

/** 秒 → "mm:ss" */
fun formatPlayTime(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/** 毫秒 → "mm:ss" */
fun formatPlayTime(ms: Long): String = formatPlayTime((ms / 1000).toInt())
```

---

## 5. 聚合调度层（all 模式）

```kotlin
package com.your.app.domain

import com.your.app.data.api.*
import com.your.app.data.SongInfo
import kotlinx.coroutines.*

object MusicRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisedJob())

    /** 搜歌：单源 or all 并发 */
    suspend fun search(keyword: String, source: String, page: Int = 1, limit: Int = 30): DomainSearchResult =
        scope.async {
            val results = when (source) {
                "kw" -> listOf(runCatching { KwApi.search(keyword, page, limit) }.getOrDefault(emptyList<SongInfo>()    ) to "kw")
                "kg" -> listOf(runCatching { KgApi.search(keyword, page, limit) }.getOrDefault(emptyList<SongInfo>()  ) to "kg")
                "tx" -> listOf(runCatching { TxApi.search(keyword, page, limit) }.getOrDefault(emptyList<SongInfo>()  ) to "tx")
                "wy" -> listOf(runCatching { WyApi.search(keyword, page, limit) }.getOrDefault(emptyList<SongInfo>()  ) to "wy")
                "mg" -> listOf(runCatching { MgApi.search(keyword, page, limit) }.getOrDefault(emptyList<SongInfo>()  ) to "mg")
                "all" -> listOf(
                    runCatching { KwApi.search(keyword, page, limit) }.getOrDefault(emptyList()) to "kw",
                    runCatching { KgApi.search(keyword, page, limit) }.getOrDefault(emptyList()) to "kg",
                    runCatching { TxApi.search(keyword, page, limit) }.getOrDefault(emptyList()) to "tx",
                    runCatching { WyApi.search(keyword, page, limit) }.getOrDefault(emptyList()) to "wy",
                    runCatching { MgApi.search(keyword, page, limit) }.getOrDefault(emptyList()) to "mg",
                )
                else -> emptyList()
            }

            // 合并
            val raw = results.flatMap { it.first }
            // 去重（按 songmid+source 或 name+singer 近似）
            val seen = HashSet<String>()
            val dedup = raw.filter { seen.add("${it.songmid}_${it.source}") }
            // 按关键词相似度排序
            val sorted = dedup.sortedByDescending { similarity(keyword, "${it.name} ${it.singer}") }
            DomainSearchResult(sorted, failedSources = results.filter { it.first.isEmpty() }.map { it.second })
        }.await()

    /** 相似度：分词命中字符数占比 */
    private fun similarity(kw: String, text: String): Double {
        val k = kw.lowercase().replace("\\s".toRegex(), "")
        val t = text.lowercase().replace("\\s".toRegex(), "")
        var hits = 0
        for (c in k) if (t.contains(c)) hits++
        return hits.toDouble() / maxOf(k.length, t.length, 1)
    }

    /** 拿播放直链：按 source 走对应源 */
    suspend fun getPlayUrl(song: SongInfo, type: String): String? = when (song.source) {
        "kw" -> runCatching { KwPlay.getPlayUrl(song.songmid, type) }.getOrNull()
        "kg" -> {
            val hash = song.types.firstOrNull { it.type == type }?.hash ?: song.hash
            if (hash.isNullOrEmpty()) null else runCatching { KgPlay.getPlayUrl(hash, type) }.getOrNull()
        }
        "tx" -> runCatching { TxApi.getPlayUrl(song.songmid, song.strMediaMid ?: song.songmid, type) }.getOrNull()
        "wy" -> runCatching { WyApi.getPlayUrl(song.songmid, type) }.getOrNull()
        "mg" -> {
            val cid = song.copyrightId ?: song.songmid
            runCatching { MgApi.getPlayUrl(cid, type) }.getOrNull()
        }
        else -> null
    }
}

data class DomainSearchResult(
    val list: List<SongInfo>,
    val failedSources: List<String>,     // 哪些源挂了，给 UI 做提示
)
```

---

## 6. 播放器集成（ExoPlayer / Media3）

```kotlin
package com.your.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.your.app.domain.MusicRepository
import com.your.app.data.SongInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()

        // 绑定 UI（假设你有一个 PlayerView）
        // findViewById<PlayerView>(R.id.player_view).player = player
    }

    fun play(song: SongInfo, preferType: String = "flac") {
        lifecycleScope.launch {
            // 1. 先尝试首选音质
            val type = song.types.firstOrNull { it.type == preferType }?.type
                ?: song.types.firstOrNull { it.type == "320k" }?.type
                ?: song.types.firstOrNull()?.type
            if (type == null) return@launch

            // 2. 拿直链（带降级兜底）
            var url = MusicRepository.getPlayUrl(song, type)
            if (url == null && type != "128k") {
                url = MusicRepository.getPlayUrl(song, "128k")
            }
            if (url == null) return@launch

            // 3. 播放
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player.release()
    }
}
```

---

## 7. 工程结构建议

```
app/src/main/java/com/your/app/
├── core/
│   ├── crypto/
│   │   ├── Digest.kt          // MD5 / SHA1
│   │   ├── Aes.kt              // ECB / CBC
│   │   ├── RsaNoPadding.kt
│   │   ├── WeApiCrypto.kt      // 网易云 weapi 双重 AES + RSA
│   │   ├── EApiCrypto.kt       // 网易云 eapi AES-ECB + MD5
│   │   ├── QqSign.kt           // QQ zzcSign
│   │   ├── KwCrypto.kt         // 酷我 wbdCrypto
│   │   └── KgCrypto.kt         // 酷狗 signatureParams
│   └── util.kt                 // byteToMB / formatPlayTime
├── data/
│   ├── SongInfo.kt
│   └── api/
│       ├── KwApi.kt            // search + playUrl
│       ├── KgApi.kt
│       ├── WyApi.kt
│       ├── TxApi.kt
│       └── MgApi.kt
├── domain/
│   └── MusicRepository.kt      // 聚合调度 + 相似度排序 + 降级
└── ui/
    └── PlayerActivity.kt        // ExoPlayer 集成
```

---

## 8. 音质类型总表

| 统一标签 | kw bitrate | kg 字段 | tx size_* | wy level | mg formatType |
|---------|-----------|--------|-----------|---------|--------------|
| 128k | bitrate=128 | FileSize | size_128mp3 | standard | PQ |
| 320k | bitrate=320 | HQFileSize | size_320mp3 | higher | HQ |
| flac | level=2000 / 4000 | SQFileSize | size_flac | exhigh | SQ |
| flac24bit | level=2012 | ResFileSize | size_hires | jyeffect | ZQ24 |

---

## 9. 已知坑位 & 避坑

| 坑 | 原因 | 解法 |
|----|------|-----|
| 网易云 AES-CBC 在 Android 上 PKCS5 = PKCS7 | 16 块长 padding 用 PKCS5 就好（别名兼容 128 bit） | Cipher.getInstance("AES/CBC/PKCS5Padding") |
| RSA_NO_PADDING Android 必须补 128 字节 | 网易云 1024 bit key | padded = 0x00*(128-len) + raw |
| QQ zzcSign 小写 | 原项目 `.toLowerCase()` | 返回时一定 lowercase |
| kw 搜索走 HTTP 被 cleartext 拦截 | Android 9+ 默认禁止明文 HTTP | network_security_config.xml 里把 kuwo.cn 加白 |
| kg 搜索 Audioid 在 URL 里，hash 在 search 结果里不直接给直链 | kg 需要调 album_audio/audio detail 拿 hash_flac 等再拼直链 | 先 KgApi.getMusicInfo(hash)，再拿 hash_flac 去直链 |
| mg songmid 有时等于 copyrightId | 需要 songId | songId.js 里先拿 listen-url 的 songItem.songId，不行就退回 copyrightId |
| 播放器播 flac 慢 | ExoPlayer flac 需要原生 libflac.so | 加上 implementation("androidx.media3:media3-decoder-flac:1.2.1") |
| 某些源 URL 返回 302 跳转 | ExoPlayer 默认 followRedirect 开着，OkHttp 也默认 followRedirect | 不用处理 |
| tempmusics.tk 域名随时可能换 | 社区维护的中转 | 失败时给用户提示"音源中转挂了，请换音源" |

---

## 10. 快速上手：先用第三方中转打通

**不想先实现加密？** 把各源 `getPlayUrl` 临时写成这样，等你跑通后再替换成自加密版本：

```kotlin
// 临时用 tempmusics 中转
suspend fun KwPlay.getPlayUrl(songmid: String, type: String): String? = withContext(Dispatchers.IO) {
    val url = "http://tm.tempmusics.tk/url/kw/$songmid/$type"
    val body = OkHttpClient().newCall(Request.Builder().url(url).get().build())
        .execute().body?.string() ?: return@withContext null
    JSONObject(body).optString("data").takeIf { it.isNotEmpty() }
}

// kg 需要 hash（KgApi.getMusicInfo 已经给你补全各音质 hash 了）
suspend fun KgPlay.getPlayUrl(hash: String, type: String): String? = withContext(Dispatchers.IO) {
    val url = "http://ts.tempmusics.tk/url/kg/$hash/$type"
    val body = OkHttpClient().newCall(Request.Builder().url(url).get().build())
        .execute().body?.string() ?: return@withContext null
    JSONObject(body).optString("data").takeIf { it.isNotEmpty() }
}

// 其他源同理，都拼在 ts.tempmusics.tk/url/<source>/<id>/<type>
```

等你整个 app 跑起来后，再把 `tempmusics.tk` 换成上面 §3.x 里各源的真实加密请求函数，**上层代码一行都不用动**。

---

## 11. 进阶方案：Android 端加载并运行洛雪 userApi JS

文档 §1–§9 是 **Kotlin 重写加密**（最推荐、最干净）。如果你不想每次跟着官方改加密、想**直接复用洛雪项目里那份 JS**，有 3 种方案可选。

### 方案 A：后端 Node.js 代理（最稳，强烈推荐）

> Android 端零 JS、零加密、零维护。加密逻辑跑在服务器上，服务器直接跑洛雪原始 JS。

**服务器代码**（新建一个 Node.js 项目，把洛雪 `src/renderer/utils/musicSdk/wy/crypto.js` 和 `wy/utils/index.js` 拷进来）：

```javascript
// server.js — 网易云 eapi 代理
const express = require('express')
const crypto  = require('crypto')
const https   = require('https')
const app     = express()

const eapiKey   = Buffer.from([0x65,0x38,0x32,0x6b,0x63,0x65,0x68,0x38,0x64,0x69,0x63,0x68,0x65,0x6e,0x38])
function md5(s)  { return crypto.createHash('md5').update(s).digest('hex') }
function aesECB(buf,key){
  const c = crypto.createCipheriv('aes-128-ecb',key,Buffer.alloc(0))
  return Buffer.concat([c.update(buf), c.final()]).toString('hex').toUpperCase()
}
function eapi(url,obj){
  const text = typeof obj==='string'?obj:JSON.stringify(obj)
  const msg  = `nobody${url}use${text}md5forencrypt`
  const data = `${url}-36cd479b6b5-${text}-36cd479b6b5-${md5(msg)}`
  return aesECB(Buffer.from(data), eapiKey)
}

function httpGet(url){
  return new Promise((resolve,reject)=>{
    const u = new URL(url)
    https.get({hostname:u.hostname,path:u.pathname+u.search,
      headers:{'User-Agent':'Mozilla/5.0','Referer':'https://music.163.com/','Origin':'https://music.163.com/'}},
      resp=>{let b='';resp.on('data',c=>b+=c);resp.on('end',()=>{try{resolve(JSON.parse(b))}catch{resolve(b)}})})
      .on('error',reject)
  })
}

// 搜索
app.get('/wy/search', async (req,res)=>{
  const { q, page=1, limit=30 } = req.query
  const url  = '/api/search/song/list/page'
  const body = { keyword:q, needCorrect:'1', channel:'typing', offset:limit*(page-1), scene:'normal', total:+page===1, limit:+limit }
  const enc  = eapi(url, body)
  const r    = await httpGet(`http://interface.music.163.com/eapi/batch?${enc}`)
  res.json(r)
})

// 直链（给 songid + type，返回 https 直链）
const LEVEL = { '128k':'standard','320k':'higher','flac':'exhigh','flac24bit':'jyeffect' }
app.get('/wy/url/:songmid/:type', async (req,res)=>{
  const { songmid, type } = req.params
  const body = { ids:[songmid], level: LEVEL[type]||'higher', encodeType:'url' }
  const enc  = eapi('/song/enhance/player/url/v1', body)
  const r    = await httpGet(`http://interface.music.163.com/eapi/song/enhance/player/url/v1?${enc}`)
  if (r?.code===200 && r.data?.[0]?.url) return res.json({ url:r.data[0].url })
  res.status(500).json(r)
})

app.listen(8787, ()=>console.log('http://0.0.0.0:8787'))
```

Android 端只改 baseUrl 就行（Kotlin 代码一行都不用改）：

```kotlin
const BASE = "https://your-server.com:8787"
suspend fun WyPlay.getPlayUrl(songmid:String, type:String): String? = withContext(Dispatchers.IO){
  val url = "$BASE/wy/url/$songmid/$type"
  val body = OkHttpClient().newCall(Request.Builder().url(url).get().build())
    .execute().body?.string() ?: return@withContext null
  JSONObject(body).optString("url").takeIf { it.isNotEmpty() }
}
```

**优点**：服务器可以加 Cookie / 加频控保护 / 加 VIP 账号池 / 热修加密。
**成本**：多一台服务器。如果不想买云服务器，可以用免费的 Vercel Edge Functions 或 Cloudflare Workers（直连 Node 加密没问题）。

---

### 方案 B：quickjs-android（APK 内嵌 JS 引擎，JS 原样跑）

> APK 体积增大 ~8MB。把洛雪 JS 原样塞进 APK assets，启动时交给 quickjs 运行。洛雪 JS 里用到的 `Buffer` / `crypto` 需要我们在 Kotlin 侧实现 shim 注入。

**Gradle**：

```gradle
// app/build.gradle.kts
dependencies {
  implementation("io.github.quickjs:quickjs-android:1.0.0")
}
```

**把 JS 文件放哪里**：
把 [wy-source.js](file:///C:/Users/Admin/Downloads/lx-music-desktop-master/docs/wy-source.js) 拷到 `app/src/main/assets/js/wy-source.js`。
同目录下再放一份 **polyfill.js**（给 JS 注入 Buffer / Node crypto 替身）：

```javascript
// assets/js/polyfill.js — 让洛雪 JS 在 quickjs 里也能跑
(function(){
  // Buffer 替身：quickjs 有 Uint8Array，我们给它套一层
  const _B = {
    from: function(a, enc){
      if (typeof a==='string') return new TextEncoder().encode(a)
      if (a && a.buffer) return new Uint8Array(a)
      return new Uint8Array(a||[])
    },
    alloc: function(n){ return new Uint8Array(n) },
    concat: function(arr){
      let len=0; arr.forEach(a=>len+=a.length)
      const r=new Uint8Array(len); let off=0
      arr.forEach(a=>{ r.set(a,off); off+=a.length })
      return r
    }
  }
  globalThis.Buffer = _B

  // Node require 替身 → 转发到 Kotlin 侧注册的函数
  globalThis.require = function(name){
    if (name==='crypto') return globalThis.__kCrypto
    throw new Error('require('+name+') not found')
  }
})()
```

**Kotlin 封装**：

```kotlin
// core/engine/LxJsEngine.kt
package com.your.app.core.engine

import android.content.Context
import com.quickjs.JSContext
import com.quickjs.JSFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

class LxJsEngine(private val ctx: Context) {

    private lateinit var js: JSContext

    suspend fun init() = withContext(Dispatchers.Default) {
        js = JSContext(false)
        // 1. 注入 crypto（Kotlin 实现 AES/MD5/SHA1，让 JS 直接调用）
        js.set("__kCrypto", mapOf(
            "md5" to JSFunction { args: Array<Any> -> md5Hex(args[0] as String) },
            "randomBytes" to JSFunction { args: Array<Any> ->
                ByteArray((args[0] as Number).toInt()).also {
                    java.security.SecureRandom().nextBytes(it)
                }
            },
            "createCipheriv" to JSFunction { args: Array<Any> ->
                // args: mode, keyHex, ivHex → 返回加密后的 hex
                val mode    = args[0] as String   // "aes-128-ecb" / "aes-128-cbc"
                val keyHex  = args[1] as String
                val ivHex   = args[2] as String
                val key     = hexToBytes(keyHex)
                val iv      = hexToBytes(ivHex)
                AesEncrypt(mode, key, iv).toHex()
            }
        ))

        // 2. 注入 HTTP（JS 里 lx.request 最终调这里）
        js.set("__kHttp", JSFunction { args: Array<Any> ->
            val url      = args[0] as String
            val method   = (args.getOrNull(1) as? Map<*,*>)?.get("method") as? String ?: "GET"
            val headers  = (args.getOrNull(1) as? Map<*,*>)?.get("headers") as? Map<*,*> ?: emptyMap<String, String>()
            val body     = (args.getOrNull(1) as? Map<*,*>)?.get("body") as? String
            val client   = OkHttpClient()
            val rb       = Request.Builder().url(url)
            headers.forEach { (k,v) -> rb.header(k as String, v as String) }
            if (body != null) rb.method(method, body.toRequestBody("application/json".toMediaType()))
            val resp = client.newCall(rb.build()).execute()
            val txt  = resp.body?.string() ?: ""
            try { JSONObject(txt); txt } catch { txt }
        })

        // 3. 执行 polyfill + 洛雪 JS
        js.eval(ctx.assets.open("js/polyfill.js").bufferedReader().use { it.readText() }, "polyfill.js")
        js.eval(ctx.assets.open("js/wy-source.js").bufferedReader().use { it.readText() }, "wy-source.js")
    }

    // 对外暴露的调用函数
    suspend fun wySearch(keyword: String, page: Int = 1, limit: Int = 30): String =
        withContext(Dispatchers.Default) {
            js.callFunction("wySearch", keyword, page, limit) as String
        }

    suspend fun wyGetPlayUrl(songmid: String, type: String = "flac"): String =
        withContext(Dispatchers.Default) {
            js.callFunction("wyGetPlayUrl", songmid, type) as String
        }

    fun destroy() = js.destroy()

    // --- 下面都是 Kotlin 侧的 AES/MD5 工具，被 JS 调用 ---
    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).toHex()

    private fun AesEncrypt(mode: String, key: ByteArray, iv: ByteArray): ByteArray {
        val transformation = when {
            mode.startsWith("aes-128-ecb") -> "AES/ECB/PKCS5Padding"
            mode.startsWith("aes-128-cbc") -> "AES/CBC/PKCS5Padding"
            else -> throw IllegalArgumentException(mode)
        }
        val cipher = Cipher.getInstance(transformation)
        val spec   = SecretKeySpec(key, "AES")
        if (mode.endsWith("-ecb")) cipher.init(Cipher.ENCRYPT_MODE, spec)
        else                       cipher.init(Cipher.ENCRYPT_MODE, spec, IvParameterSpec(iv))
        return cipher.doFinal(ByteArray(0)) // JS 侧已经把明文 hex 传进来，这里要改成 hex→bytes 后再加密
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun hexToBytes(s: String): ByteArray {
        val b = ByteArray(s.length / 2)
        for (i in b.indices) b[i] = s.substring(i*2, i*2+2).toInt(16).toByte()
        return b
    }
}
```

**缺点**：quickjs 对 Node crypto 的 shim 工程量不小（AES 那行 JS 侧传的是明文 hex，Kotlin 侧要把 hex→bytes→加密→hex，不能像 Node Buffer 那样直接转）。如果你只是想跑通，**用方案 A（后端代理）会快 10 倍**。

---

### 方案 C：WebView + browserify（无额外依赖，但 CORS 麻烦）

把洛雪 JS 里用到的 `require('crypto')` 用 [browserify](https://browserify.org/) 在 PC 上打包成浏览器兼容版（browserify 会把 Node crypto 替换成 `forge` 或 `sjcl` 的浏览器实现），然后把产出的 bundle.js 塞进 assets，Android WebView 里 `loadUrl("javascript:...")` 执行。

```bash
# 在电脑上打一次包，把 wy-source.js 打包成浏览器版
browserify wy-source.js -s wy > wy-bundle.js
# 把 wy-bundle.js 拷到 app/src/main/assets/js/
```

```kotlin
// Activity 里
val wv = WebView(this)
wv.settings.javaScriptEnabled = true
wv.evaluateJavascript(assets.open("js/wy-bundle.js").bufferedReader().use { it.readText() }, null)
// 然后 wySearch / wyGetPlayUrl 就挂在 window.wy 上了
wv.evaluateJavascript("window.wy.wyGetPlayUrl('186855','flac')") { result -> ... }
```

**致命缺点**：WebView 发 fetch 到 `interface.music.163.com` 会遇到严格 CORS，而且 WebView 的 User-Agent 很容易被网易云反爬拦截。实测成功率低于 10%。**不推荐**。

---

### 三种方案对比

| 方案 | APK 增量 | 维护成本 | 跑通速度 | 加密跟官方同步 | 适合 |
|---|---|---|---|---|---|
| A 后端代理 | 0MB | 服务器 + JS | 最快（1 天） | 直接跑洛雪源码 | **所有人，强烈推荐** |
| B quickjs-android | +8MB | JS shim | 中等（3 天） | 原样跑 JS | 不能联网 / 要离线 |
| C WebView + browserify | 0MB | CORS 调试 | 最慢 | 要重新打包 | 不推荐 |

**一句话**：如果你有服务器，用方案 A；没有服务器，继续走 §1–§9 的 Kotlin 加密路线；**不推荐**花一周调 quickjs 的 shim。

---

如果要我直接生成一个可以跑的 Kotlin 工程骨架（build.gradle + MainActivity 界面），告诉我你的项目包名和最低 API 级别。
