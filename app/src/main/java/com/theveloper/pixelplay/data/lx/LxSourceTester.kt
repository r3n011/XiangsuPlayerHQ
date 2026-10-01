package com.theveloper.pixelplay.data.lx

import android.os.SystemClock
import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** 一个可测试的音源 */
data class LxSourceOption(
    val key: String,
    val displayName: String,
    /** true = 由已安装的 JS 脚本提供；false = Kotlin 内置源 */
    val jsDriven: Boolean
)

/**
 * 音源连通性测试：拿一首样歌，向该音源真实取一次播放地址。
 *
 * ⚡ 落雪音源脚本只声明 `actions: ['musicUrl']`，**不实现搜索**（搜索由宿主 App 的内置
 *   SDK 完成，脚本只负责把 songInfo 换成播放直链）。所以样歌的获取顺序是：
 *   先用脚本自身的搜索（支持搜索的源），拿不到再按平台用官方/内置搜索兜底。
 *
 * 结果分为三档：
 * - [Status.SUCCESS]：取到播放地址
 * - [Status.SEARCH_ONLY]：仅内置源（无 JS 插件）可搜到样歌、且不测取链
 * - [Status.FAILED]：连样歌都拿不到，或取播放地址失败
 */
@Singleton
class LxSourceTester @Inject constructor(
    private val engine: LxJsEngine,
    private val builtInSourceSearchApi: BuiltInSourceSearchApi,
    private val lxSearchApi: LxSearchApi
) {
    enum class Status { SUCCESS, SEARCH_ONLY, FAILED }

    data class Result(
        val source: String,
        val status: Status,
        val latencyMs: Long,
        val message: String,
        val sample: String = ""
    )

    companion object {
        /** Kotlin 内置源（无需安装 JS 脚本） */
        val BUILT_IN_SOURCES = listOf("tx", "kg", "kw", "mg")

        /** 测试用的固定搜索词（流行度高，各平台都有结果） */
        private const val TEST_KEYWORD = "周杰伦"

        private const val TEST_QUALITY = "128k"

        /** 测试前等待脚本引擎就绪的上限（含首次加载脚本） */
        private const val ENGINE_READY_TIMEOUT_MS = 15_000L

        /** 脚本不支持搜索时，用官方/内置搜索取"样歌"的超时上限 */
        private const val SEARCH_PROBE_TIMEOUT_MS = 20_000L

        fun builtInDisplayName(key: String): String = when (key) {
            "tx" -> "QQ 音乐"
            "kg" -> "酷狗音乐"
            "kw" -> "酷我音乐"
            "mg" -> "咪咕音乐"
            "wy" -> "网易云音乐"
            "qsvip" -> "企鹅音乐"
            else -> key
        }
    }

    /**
     * 可选音源列表。
     *
     * @param installedOnly true = 只返回**已安装 JS 脚本**提供的音源（音源设置页用），
     *                      false = 额外补上 Kotlin 内置源（市场页的完整清单）。
     */
    suspend fun availableSources(installedOnly: Boolean = false): List<LxSourceOption> {
        // 首次进入时引擎可能尚未加载脚本，先触发一次加载（无脚本时立即返回）
        runCatching { engine.awaitReady(ENGINE_READY_TIMEOUT_MS) }
        val jsSources = runCatching { engine.getSources() }.getOrDefault(emptyMap())
        val out = LinkedHashMap<String, LxSourceOption>()
        jsSources.forEach { (key, info) ->
            out[key] = LxSourceOption(
                key = key,
                displayName = info.name.ifBlank { builtInDisplayName(key) },
                jsDriven = true
            )
        }
        if (!installedOnly) {
            BUILT_IN_SOURCES.forEach { key ->
                out.putIfAbsent(
                    key,
                    LxSourceOption(key, builtInDisplayName(key), jsDriven = false)
                )
            }
        }
        return out.values.toList()
    }

    /** 测试单个音源的连通性 */
    suspend fun test(source: String): Result = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - startedAt

        // ⚡ 脚本引擎需要一点时间加载脚本，"刚进页面就点测试"时常因 getSources() 还是空的
        //   而把已安装的音源判成不可用。这里先等引擎就绪再取音源清单。
        runCatching { engine.awaitReady(ENGINE_READY_TIMEOUT_MS) }
        val jsSources = runCatching { engine.getSources() }.getOrDefault(emptyMap())
        val viaJs = jsSources.containsKey(source)

        // ⚡ 落雪音源脚本只声明 actions: ['musicUrl']，**不实现搜索**——搜索由宿主 App 的内置 SDK
        //    完成，脚本只负责把 songInfo 换成播放直链；脚本对非 musicUrl 的 action 一律
        //    reject('action not support')，所以"先搜索、再取链"的旧测法在第一步就必然拿到空结果，
        //    表现为任何音源都"永远测试失败"。
        //    这里改为：能搜就先用脚本自身搜索拿样歌；搜不到（脚本不支持搜索，属正常）就按平台用
        //    官方/内置搜索取一首样歌，真正验证「这个音源能不能取到播放链接」。
        var scriptSearchHit = false
        var probeSong: LxSongInfo? = null
        if (viaJs) {
            runCatching { engine.search(TEST_KEYWORD, source, page = 1, pagesize = 5) }
                .onFailure { Timber.w(it, "LxSourceTester: JS search failed for $source") }
                .getOrNull()
                ?.list
                ?.firstOrNull()
                ?.let { song ->
                    scriptSearchHit = true
                    probeSong = song
                }
        }
        if (probeSong == null) {
            probeSong = withTimeoutOrNull(SEARCH_PROBE_TIMEOUT_MS) {
                runCatching { builtInSearch(source) }
                    .onFailure { Timber.w(it, "LxSourceTester: probe search failed for $source") }
                    .getOrNull()
                    ?.list
                    ?.firstOrNull()
            }
        }

        val first = probeSong
            ?: return@withContext Result(
                source = source,
                status = Status.FAILED,
                latencyMs = elapsed(),
                message = "搜索无结果（接口不可用或超时）"
            )

        if (!viaJs) {
            return@withContext Result(
                source = source,
                status = Status.SEARCH_ONLY,
                latencyMs = elapsed(),
                message = "搜索可用（内置源，未安装 JS 插件时不测取链）",
                sample = first.name
            )
        }

        // ⚡ 音质不写死：各脚本声明的档位不同（有的只有 320k / flac / hires），
        //   写死 "128k" 会让本来可用的音源一律判成"取播放地址失败"
        val declaredQuality = jsSources[source]?.qualitys
            ?.firstOrNull { it.equals(TEST_QUALITY, ignoreCase = true) }
            ?: jsSources[source]?.qualitys?.firstOrNull()
            ?: TEST_QUALITY
        var playError: String? = null
        val playUrl = runCatching {
            // 与真实播放链路共用同一份 musicInfo 映射，避免"能播放但测试失败"
            engine.getPlayUrl(source, first.toLxMusicInfoMap(), declaredQuality)
        }.onFailure {
            playError = it.message ?: it.javaClass.simpleName
        }.getOrNull()

        return@withContext Result(
            source = source,
            status = if (!playUrl.isNullOrBlank()) Status.SUCCESS else Status.FAILED,
            latencyMs = elapsed(),
            message = if (!playUrl.isNullOrBlank()) {
                "可用"
            } else {
                buildString {
                    append(if (scriptSearchHit) "搜索可用，取播放地址失败" else "取播放地址失败")
                    append("（$declaredQuality）")
                    if (!playError.isNullOrBlank()) append("：$playError")
                }
            },
            sample = first.name
        )
    }

    /**
     * 脚本自身不支持搜索时，按平台用官方/内置搜索取一首"样歌"，仅用于给 musicUrl 提供入参。
     * （落雪脚本只实现 musicUrl，搜索由宿主 App 的内置搜索能力完成。）
     */
    private suspend fun builtInSearch(source: String): LxSearchResult = when {
        source == "wy" -> lxSearchApi.search(TEST_KEYWORD, page = 1, pageSize = 5)
        builtInSourceSearchApi.isSupported(source) ->
            builtInSourceSearchApi.search(source, TEST_KEYWORD, 1, 5)
        else -> LxSearchResult()
    }
}
