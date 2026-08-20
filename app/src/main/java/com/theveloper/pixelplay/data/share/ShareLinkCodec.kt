package com.theveloper.pixelplay.data.share

import android.net.Uri
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * 像素播放器 · 音乐分享链接协议编解码
 *
 * 协议格式: xiangsuplayer://share?d={Base64URL(raw deflate(JSON))}
 *
 * 参考规范: G:\音乐分享链接协议规范.md
 */
object ShareLinkCodec {

    private const val TAG = "ShareLinkCodec"
    private const val SCHEME = "xiangsuplayer"
    private const val HOST_SHARE = "share"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ─── 数据模型 ──────────────────────────────────────────────────

    @Serializable
    data class ShareMetadata(
        val v: Int = 1,
        val tp: String,        // "song" | "playlist" | "album" | "artist"
        val nm: String,        // 名称
        val cv: String = "",   // 封面 URL
        val it: List<ShareItem> = emptyList()
    )

    @Serializable
    data class ShareItem(
        val t: String,         // 歌曲标题
        val ar: String,        // 艺人名
        val al: String,        // 专辑名
        val dr: Long,          // 时长（秒）
        val cv: String = "",   // 封面 URL
        val sc: String,        // 音源类型: local/netease/qqMusic/navidrome/jellyfin/cloudLx
        val eid: String = ""   // 外部平台 ID
    )

    // ─── 编码 ─────────────────────────────────────────────────────

    /**
     * 将歌曲列表编码为分享链接
     */
    fun encode(songs: List<Song>, name: String, type: String = "playlist"): String? {
        if (songs.isEmpty()) return null

        val items = songs.map { songToShareItem(it) }
        val metadata = ShareMetadata(
            v = 1,
            tp = if (songs.size == 1) "song" else type,
            nm = name,
            cv = songs.firstOrNull()?.albumArtUriString ?: "",
            it = items
        )

        return try {
            val jsonStr = json.encodeToString(ShareMetadata.serializer(), metadata)
            val compressed = deflateRaw(jsonStr.toByteArray(StandardCharsets.UTF_8))
            val encoded = base64UrlEncode(compressed)
            "$SCHEME://$HOST_SHARE?d=$encoded"
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Encode share link failed")
            null
        }
    }

    /**
     * 将单首歌曲编码为分享链接
     */
    fun encodeSong(song: Song): String? {
        return encode(listOf(song), song.title, "song")
    }

    private fun songToShareItem(song: Song): ShareItem {
        val (sourceType, externalId) = mapSourceAndId(song)
        return ShareItem(
            t = song.title,
            ar = song.displayArtist,
            al = song.album,
            dr = song.duration / 1000,  // 毫秒 → 秒
            cv = song.albumArtUriString ?: "",
            sc = sourceType,
            eid = externalId
        )
    }

    private fun mapSourceAndId(song: Song): Pair<String, String> {
        val uri = song.contentUriString
        return when {
            song.neteaseId != null -> "netease" to song.neteaseId.toString()
            song.qqMusicMid != null -> "qqMusic" to song.qqMusicMid!!
            song.navidromeId != null -> "navidrome" to song.navidromeId!!
            song.jellyfinId != null -> "jellyfin" to song.jellyfinId!!
            uri.startsWith("cloud://lx/") -> "cloudLx" to extractLxId(song)
            else -> "local" to ""
        }
    }

    private fun extractLxId(song: Song): String {
        // cloud://lx/{urlencoded JSON} 中提取 id
        return try {
            val jsonStr = Uri.decode(song.contentUriString.removePrefix("cloud://lx/"))
            val lxData = json.parseToJsonElement(jsonStr)
            lxData.toString()
        } catch (_: Exception) {
            song.id
        }
    }

    // ─── 解码 ─────────────────────────────────────────────────────

    /**
     * 从 URL 解码分享元数据
     */
    fun decode(url: String): ShareMetadata? {
        return try {
            val uri = Uri.parse(url)
            if (uri.scheme != SCHEME || uri.host != HOST_SHARE) return null
            val d = uri.getQueryParameter("d")
            if (d.isNullOrBlank()) return null

            val compressed = base64UrlDecode(d)
            val jsonStr = inflateRaw(compressed)
            if (jsonStr.isBlank()) {
                Timber.tag(TAG).w("Inflate returned empty string")
                return null
            }
            json.decodeFromString(ShareMetadata.serializer(), jsonStr)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Decode share link failed")
            null
        }
    }

    /**
     * 检测文本是否包含分享链接
     */
    fun isShareLink(text: String): Boolean {
        return extractShareLink(text) != null
    }

    /**
     * 从文本中提取分享链接（兼容纯链接和包含链接的分享文案）
     */
    fun extractShareLink(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("$SCHEME://$HOST_SHARE")) return trimmed
        val regex = Regex("xiangsuplayer://share\\?d=[\\w\\-_.~]+")
        return regex.find(trimmed)?.value
    }

    // ─── 压缩/解压 ────────────────────────────────────────────────

    private fun deflateRaw(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true) // nowrap = true
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            out.write(buffer, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflateRaw(data: ByteArray): String {
        // 尝试 raw deflate (nowrap=true)
        try {
            val result = inflateWithMode(data, true)
            if (result.isNotEmpty()) return result
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Raw inflate failed, trying with zlib header")
        }
        // 回退：尝试 zlib 格式 (nowrap=false)
        return inflateWithMode(data, false)
    }

    private fun inflateWithMode(data: ByteArray, raw: Boolean): String {
        val inflater = Inflater(raw)
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(2048)
            var maxIterations = 1000
            while (!inflater.finished() && !inflater.needsInput() && maxIterations-- > 0) {
                val count = inflater.inflate(buffer)
                if (count > 0) {
                    out.write(buffer, 0, count)
                } else if (count == 0 && inflater.needsInput()) {
                    break
                }
            }
            return String(out.toByteArray(), StandardCharsets.UTF_8)
        } finally {
            inflater.end()
        }
    }

    // ─── Base64URL ────────────────────────────────────────────────

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun base64UrlDecode(str: String): ByteArray {
        // 还原 padding
        val padded = str + "====".substring(str.length % 4)
        return Base64.getUrlDecoder().decode(padded)
    }
}
