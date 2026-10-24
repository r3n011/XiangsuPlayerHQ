package com.theveloper.pixelplay.data.playback

import android.net.Uri
import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.lx.LxJsEngine
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.lx.LxSourceInfo
import com.theveloper.pixelplay.data.netease.NeteaseStreamProxy
import com.theveloper.pixelplay.data.preferences.MusicQualityCatalog
import com.theveloper.pixelplay.presentation.viewmodel.ConnectivityStateHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一在线音源解析核心（对齐 lx-music-mobile 的 core/music/{index,online,utils}.ts）。
 *
 * 所有入口（媒体库 cloud://lx、落雪搜索页、手动换源）都应走这里，避免每个入口各自维护
 * 一套“插件优先 / 内置兜底 / 跨源搜索 / 网易云代理”逻辑。
 */
@Singleton
class OnlineSourceResolver @Inject constructor(
    private val lxJsEngine: LxJsEngine,
    private val builtInSourceSearchApi: BuiltInSourceSearchApi,
    private val neteaseStreamProxy: NeteaseStreamProxy,
    private val playbackUrlCache: PlaybackUrlCache,
    private val statusHolder: PlaybackSourceStatusHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
) {
    companion object {
        private const val TAG = "OnlineSourceResolver"

        /** 内置源兜底顺序：先网易云，再 QQ/酷狗/酷我/咪咕。 */
        val BUILT_IN_SOURCE_ORDER = listOf("wy", "tx", "kg", "kw", "mg")

        /** 落雪常见的音源标识。 */
        val KNOWN_SOURCES = setOf("wy", "tx", "kg", "kw", "mg", "qsvip")

        private val ENCRYPTED_EXTENSIONS = listOf(
            ".mflac", ".mflac0", ".mflac2", ".mgg", ".mgg0", ".mgg1", ".mggl", ".mmp4"
        )
    }

    data class Request(
        val song: LxSongInfo,
        val jsSongInfo: Map<String, Any?>,
        val quality: String,
        /** 手动指定音源；非空时优先只试这个源，失败再回退其它源。 */
        val preferredSource: String? = null,
        /** 手动“重新解析/换源”时绕过持久化缓存。 */
        val forceRefresh: Boolean = false,
        val cacheKey: String = song.id.ifBlank { "${song.name}|${song.singer}" },
        val enableRetry: Boolean = true,
    )

    sealed class Outcome {
        data class Success(
            val url: String,
            val source: String,
            val quality: String,
            val fromCache: Boolean,
            val switched: Boolean,
        ) : Outcome()

        data class Failure(
            val kind: FailureKind,
            val message: String,
        ) : Outcome()
    }

    enum class FailureKind {
        OFFLINE,
        NO_SOURCE,
        RATE_LIMITED,
        NETWORK,
        NO_URL,
    }

    suspend fun resolve(request: Request): Outcome {
        if (!connectivityStateHolder.isOnline.value) {
            statusHolder.failed(request.song.name, "当前网络不可用")
            return Outcome.Failure(FailureKind.OFFLINE, "当前网络不可用")
        }

        // 冷启动时 JS 脚本可能还在加载：先等它就绪再构建音源顺序，
        // 否则 getSources() 为空会把插件音源整条漏掉（只剩内置源）。
        runCatching { lxJsEngine.awaitReady(15_000) }
        val sourceOrder = buildSourceOrder(request)
        if (sourceOrder.isEmpty()) {
            statusHolder.failed(request.song.name, "没有可用音源")
            return Outcome.Failure(FailureKind.NO_SOURCE, "没有可用音源")
        }

        statusHolder.resolving(request.song.name, request.preferredSource ?: request.song.source)
        var lastFailure = Outcome.Failure(FailureKind.NO_URL, "所有音源均未返回可用链接")
        val attempts = if (request.enableRetry) 2 else 1

        repeat(attempts) { attempt ->
            if (attempt > 0) {
                // 对齐 lx：限流类失败等更久（2~6s），普通失败 1.8s 后重试一次。
                val waitMs = if (lastFailure.kind == FailureKind.RATE_LIMITED) 3_500L else 1_800L
                delay(waitMs)
            }
            val result = resolveOnce(request, sourceOrder)
            if (result is Outcome.Success) {
                statusHolder.switched(
                    songTitle = request.song.name,
                    from = request.preferredSource ?: request.song.source.takeIf { it.isNotBlank() },
                    to = result.source,
                )
                return result
            }
            lastFailure = result as Outcome.Failure
            if (lastFailure.kind == FailureKind.OFFLINE || lastFailure.kind == FailureKind.NO_SOURCE) {
                return lastFailure
            }
        }

        statusHolder.failed(request.song.name, lastFailure.message)
        return lastFailure
    }

    private suspend fun resolveOnce(request: Request, sourceOrder: List<String>): Outcome {
        val pluginSources = lxJsEngine.getSources()
        val lxReady = if (sourceOrder.any { it in pluginSources }) {
            lxJsEngine.awaitReady(15_000)
        } else {
            lxJsEngine.isReady()
        }

        // ① 当前/指定音源：插件优先 + 内置官方兜底
        for (source in sourceOrder) {
            val chain = qualityChain(request.quality, source)
            for (quality in chain) {
                if (!request.forceRefresh) {
                    playbackUrlCache.get(request.cacheKey, source, quality)?.let { url ->
                        Timber.d("$TAG: cache hit ${request.song.name} source=$source quality=$quality")
                        return Outcome.Success(url, source, quality, fromCache = true, switched = false)
                    }
                }
                val url = resolveFromSource(source, request.song, request.jsSongInfo, quality, lxReady, pluginSources)
                if (url != null) {
                    playbackUrlCache.put(request.cacheKey, source, quality, url)
                    return Outcome.Success(url, source, quality, fromCache = false, switched = false)
                }
            }
        }

        // ② 跨源换源：按歌名/歌手/专辑/时长到其它音源重新搜索同一首歌
        val exclude = request.preferredSource?.takeIf { it.isNotBlank() } ?: request.song.source
        val extraSources = if (lxReady) {
            pluginSources.keys.filter { it != exclude && it !in sourceOrder }
        } else {
            emptyList()
        }
        val candidates = runCatching {
            builtInSourceSearchApi.findOtherSourceCandidates(
                name = request.song.name,
                singer = request.song.singer,
                albumName = request.song.albumName,
                durationMs = request.song.duration,
                excludeSource = exclude,
                extraSources = extraSources,
                extraSearch = { source, keyword, limit ->
                    lxJsEngine.search(keyword, source, page = 1, pagesize = limit).list
                },
            )
        }.getOrElse {
            Timber.w(it, "$TAG: findOtherSourceCandidates failed")
            emptyList()
        }

        for (candidate in candidates) {
            val candidateMap = candidate.toJsSongInfo()
            val chain = qualityChain(request.quality, candidate.source)
            for (quality in chain) {
                val url = resolveFromSource(
                    source = candidate.source,
                    song = candidate,
                    jsSongInfo = candidateMap,
                    quality = quality,
                    lxReady = lxReady,
                    pluginSources = pluginSources,
                ) ?: resolveNeteaseProxyIfPossible(candidate, quality)
                if (url != null) {
                    playbackUrlCache.put(request.cacheKey, candidate.source, quality, url)
                    return Outcome.Success(url, candidate.source, quality, fromCache = false, switched = true)
                }
            }
        }

        // ③ 原曲是网易云时，最后走官方 neteaseStreamProxy
        resolveNeteaseProxyIfPossible(request.song, request.quality)?.let { url ->
            playbackUrlCache.put(request.cacheKey, "wy", request.quality, url)
            return Outcome.Success(url, "wy", request.quality, fromCache = false, switched = false)
        }

        // 区分限流 / 网络超时，供外层选择更长的退避时间。
        if (lxJsEngine.wasRateLimitedRecently()) {
            return Outcome.Failure(FailureKind.RATE_LIMITED, "音源接口限流（429/503）")
        }
        if (lxJsEngine.wasCallTimeoutRecently() || lxJsEngine.wasNetworkErrorRecently()) {
            return Outcome.Failure(FailureKind.NETWORK, "网络错误或请求超时")
        }
        return Outcome.Failure(FailureKind.NO_URL, "所有音源均未返回可用链接")
    }

    private suspend fun resolveFromSource(
        source: String,
        song: LxSongInfo,
        jsSongInfo: Map<String, Any?>,
        quality: String,
        lxReady: Boolean,
        pluginSources: Map<String, LxSourceInfo>,
    ): String? {
        val pluginUrl = if (lxReady && pluginSources.containsKey(source)) {
            runCatching { lxJsEngine.getPlayUrl(source, jsSongInfo, quality) }.getOrNull()
        } else {
            null
        }
        val builtInUrl = if (builtInSourceSearchApi.isSupported(source)) {
            runCatching { builtInSourceSearchApi.resolvePlayUrl(source, song, quality) }.getOrNull()
        } else {
            null
        }
        return listOfNotNull(pluginUrl, builtInUrl).firstOrNull { StreamUrlValidator.isUsable(source, it) }
    }

    private suspend fun resolveNeteaseProxyIfPossible(song: LxSongInfo, quality: String): String? {
        val id = song.id.toLongOrNull() ?: song.songmid.toLongOrNull() ?: return null
        if (id <= 0) return null
        if (!neteaseStreamProxy.ensureReady(3_000L)) return null
        return neteaseStreamProxy.resolveNeteaseUri("netease://$id")
            ?.takeIf { StreamUrlValidator.isUsable("wy", it) }
    }

    private fun buildSourceOrder(request: Request): List<String> {
        val pluginSources = lxJsEngine.getSources().keys
        val available = (pluginSources + BUILT_IN_SOURCE_ORDER)
            .distinct()
            .filter { it in KNOWN_SOURCES && (it in pluginSources || builtInSourceSearchApi.isSupported(it)) }

        val preferred = request.preferredSource?.takeIf { it.isNotBlank() }
        val own = request.song.source.takeIf { it.isNotBlank() }
        val head = preferred ?: own
        val order = (listOfNotNull(head) + available).distinct()
        return if (head != null && head !in available) order.filter { it == head } else order
    }

    private fun qualityChain(target: String, source: String): List<String> {
        val available = lxJsEngine.getSources()[source]?.qualitys.orEmpty()
        return (MusicQualityCatalog.resolveChain(target, available) + listOf("320k", "128k")).distinct()
    }

    private fun LxSongInfo.toJsSongInfo(): Map<String, Any?> {
        val artistNames = com.theveloper.pixelplay.data.stream.CloudMusicUtils.parseArtistNames(singer)
        val ids = artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return linkedMapOf(
            "id" to id,
            "vid" to id,
            "songmid" to songmid,
            "hash" to hash,
            "name" to name,
            "singer" to singer,
            "artists" to artistNames.mapIndexed { index, artist ->
                mapOf("id" to ids.getOrNull(index).orEmpty(), "name" to artist)
            },
            "artistIds" to ids,
            "album" to albumName,
            "albumName" to albumName,
            "duration" to duration,
            "pic" to pic,
            "cover" to pic,
            "source" to source,
        )
    }
}

/** 在线直链可用性校验：只接受 http(s)，并过滤加密容器/跨平台占位流。 */
object StreamUrlValidator {
    private val ENCRYPTED_EXTENSIONS = listOf(
        ".mflac", ".mflac0", ".mflac2", ".mgg", ".mgg0", ".mgg1", ".mggl", ".mmp4"
    )

    fun isUsable(source: String, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false

        val path = trimmed.substringBefore('?').substringBefore('#').lowercase()
        if (ENCRYPTED_EXTENSIONS.any { path.endsWith(it) }) return false

        if (source != "kw") {
            val host = runCatching { Uri.parse(trimmed).host }.getOrNull()?.lowercase().orEmpty()
            if (host == "kuwo.cn" || host.endsWith(".kuwo.cn") ||
                host == "kuwo.com" || host.endsWith(".kuwo.com")
            ) {
                return false
            }
        }
        return true
    }
}
