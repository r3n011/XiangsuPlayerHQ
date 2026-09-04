package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.lx.LxSearchApi
import com.theveloper.pixelplay.data.lx.LxSearchResult
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 可复用的「AI 理解需求 → 多源在线搜索 → AI 决策产出歌单」流水线。
 *
 * 供全能 AI 助手（[com.theveloper.pixelplay.presentation.viewmodel.AiAssistantViewModel]）
 * 的「生成歌单 / 在线搜索」工具使用。逻辑与 AiSearchViewModel 保持一致，但返回纯数据，
 * 由调用方决定如何展示。
 */
@Singleton
class AiSongSearchService @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
    private val builtInSourceSearchApi: BuiltInSourceSearchApi,
    private val lxSearchApi: LxSearchApi,
    private val musicRepository: MusicRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository
) {

    companion object {
        private const val TAG = "AiSongSearchService"
        /** 最多解析/搜索的候选歌曲数量 */
        private const val MAX_CANDIDATES = 8
        /** 每个关键词在对应源取前几条结果 */
        private const val PER_QUERY = 5
        /** AI 决策循环最多轮数 */
        private const val MAX_ROUNDS = 4
        /** 候选池大小上限 */
        private const val MAX_POOL = 30
        private val SOURCES = listOf("wy", "kw", "tx", "kg", "mg")
    }

    /**
     * 把用户的自然语言需求转成一组精选在线歌曲。
     * 返回空列表表示未能产出任何歌曲。
     */
    suspend fun searchSynth(userText: String): List<LxSongInfo> = withContext(Dispatchers.IO) {
        val candidates = parseCandidates(
            aiOrchestrator.generateContent(
                prompt = """
请把用户对音乐的描述/请求，拆解成最可能匹配的候选歌曲搜索词。
只输出一个 JSON 数组，不要输出任何其他文字、代码块或注释。
每个元素是一个短字符串，格式为「歌名 - 歌手」（不确定歌手时可只写歌名）。
示例：用户说“推荐几首周杰伦的老歌”，输出：["晴天 - 周杰伦","七里香 - 周杰伦","稻香 - 周杰伦"]
请输出 6~8 个候选。
用户请求：$userText
""".trimIndent(),
                type = AiSystemPromptType.GENERAL,
                temperature = 0.3f,
                context = "这是一个音乐搜索助手，负责把用户需求转成到底的歌曲搜索关键词。"
            )
        )
        if (candidates.isEmpty()) return@withContext emptyList()

        // 阶段一：多源并行搜索，合并去重得到候选池
        val pool = LinkedHashMap<String, LxSongInfo>()
        candidates.forEach { query ->
            for (src in SOURCES) {
                if (pool.size >= MAX_POOL) break
                val result = searchSource(src, query, 1, PER_QUERY)
                result.list.forEach { hit ->
                    val key = songKey(hit)
                    if (key.isNotBlank() && !pool.containsKey(key)) pool[key] = hit
                }
            }
        }
        if (pool.isEmpty()) return@withContext emptyList()

        // 阶段二：AI 决策 — 自主决定补充搜索或直接产出歌单
        val picked = decideSongs(userText, pool)
        val deduped = picked.distinctBy { songKey(it) }
        return@withContext deduped
    }

    /**
     * 把一组在线歌曲写入统一媒体库并创建一个「AI 智能歌单」，返回歌单名；失败返回 null。
     */
    suspend fun saveAsPlaylist(songs: List<LxSongInfo>): String? {
        if (songs.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val songIds = songs.mapNotNull { s ->
                runCatching { musicRepository.saveCloudSong(s) }.getOrNull()
                    ?.takeIf { it > 0 }?.toString()
            }.distinct()
            if (songIds.isEmpty()) return@withContext null

            val name = "AI 智能歌单 ${System.currentTimeMillis().toString().takeLast(6)}"
            runCatching {
                playlistPreferencesRepository.createPlaylist(
                    name = name,
                    songIds = songIds,
                    isAiGenerated = true,
                    source = "AI"
                )
                name
            }.getOrNull()
        }
    }

    private suspend fun searchSource(src: String, query: String, page: Int, size: Int): LxSearchResult {
        val result = if (src == "wy") {
            runCatching { lxSearchApi.search(query, page, size) }.getOrNull() ?: LxSearchResult(isEnd = true)
        } else {
            runCatching { builtInSourceSearchApi.search(src, query, page, size) }
                .getOrElse { Timber.tag(TAG).w(it, "search %s failed", src); LxSearchResult(isEnd = true) }
        }
        val list = result.list.map { it.copy(pic = upscalePic(it.pic), source = if (src == "wy") "wy" else it.source) }
        return result.copy(list = list)
    }

    private fun upscalePic(pic: String): String {
        if (pic.isBlank()) return pic
        return pic.replace(Regex("(?i)[?&]param=\\d+y\\d+"), "?param=1000y1000")
    }

    private fun songKey(s: LxSongInfo): String =
        listOf(s.name, s.singer).filter { it.isNotBlank() }.joinToString(" - ").replace(" ", "")

    private data class AiPickDecision(val action: String, val items: List<String>)

    /** 阶段二：AI 决策循环。AI 可反复决定 search_more 或 done。 */
    private suspend fun decideSongs(
        userText: String,
        pool: MutableMap<String, LxSongInfo>
    ): List<LxSongInfo> {
        var rounds = 0
        while (rounds < MAX_ROUNDS) {
            rounds++
            val labels = pool.values.map { "${it.name} - ${it.singer}".replace(" ", "") }
            val raw = aiOrchestrator.generateContent(
                prompt = """
用户需求：$userText
当前已收集到的候选歌曲清单（歌名 - 歌手，已去掉多余空格）：
${labels.mapIndexed { i, l -> "${i + 1}. $l" }.joinToString("\n")}
根据用户需求决策下一步：
- 若候选已足够或能从中组成一个好歌单，返回 {"action":"done","songs":["歌名 - 歌手"...]}，songs 里的每个元素必须原样引用上方清单中的「歌名 - 歌手」字符串（去掉内部空格）。
- 若候选还不够（例如缺少用户想要的风格/类型），返回 {"action":"search_more","queries":["新搜索词"...]}，queries 是需要补充搜索的新关键词（歌名或歌名 - 歌手）。
只输出一个 JSON 对象，不要输出任何其他文字、代码块或注释。
""".trimIndent(),
                type = AiSystemPromptType.GENERAL,
                temperature = 0.4f,
                context = "你是音乐推荐助手，自主决定是否补充搜索，最终为候选池挑选歌单。"
            )
            val decision = parseDecision(raw)
            if (decision.action == "search_more" && decision.items.isNotEmpty() && pool.size < MAX_POOL && rounds < MAX_ROUNDS) {
                decision.items.forEach { query ->
                    if (pool.size >= MAX_POOL) return@forEach
                    val result = searchSource("kw", query, 1, PER_QUERY)
                    result.list.forEach { hit ->
                        val key = songKey(hit)
                        if (key.isNotBlank() && !pool.containsKey(key)) pool[key] = hit
                    }
                }
                continue
            }
            val picked = decision.items.mapNotNull { pool[it] }
            val deduped = picked.distinctBy { songKey(it) }
            return if (deduped.isNotEmpty()) deduped else pool.values.toList()
        }
        return pool.values.take(MAX_CANDIDATES).toList()
    }

    private fun parseDecision(raw: String): AiPickDecision {
        val t = raw.trim()
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        val json = if (start >= 0 && end > start) t.substring(start, end + 1) else null
        if (json != null) {
            runCatching {
                val obj = JSONObject(json)
                val action = obj.optString("action", "done")
                val items = ArrayList<String>()
                val arr = obj.optJSONArray("songs") ?: obj.optJSONArray("queries")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val el = arr.optString(i).trim()
                        if (el.isNotBlank()) items += el.replace(" ", "")
                    }
                }
                return AiPickDecision(action, items)
            }
        }
        return AiPickDecision("done", emptyList())
    }

    private fun parseCandidates(raw: String): List<String> {
        val trimmed = raw.trim()
        val json = trimmed
            .removePrefix("```")
            .removePrefix("json")
            .trim()
            .removeSuffix("```")
            .trim()
        runCatching {
            val start = json.indexOf('[')
            val end = json.lastIndexOf(']')
            if (start >= 0 && end > start) {
                val arr = JSONArray(json.substring(start, end + 1))
                val out = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val el = arr.optString(i).trim()
                    if (el.isNotBlank()) out += el.trim('"', '[', ']', '\'', ' ')
                }
                return out.distinct().take(MAX_CANDIDATES)
            }
        }
        return trimmed
            .split('\n', ',', '，', '、')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
            .map { it.trim('"', '[', ']', '\'', ' ', '-') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_CANDIDATES)
    }
}