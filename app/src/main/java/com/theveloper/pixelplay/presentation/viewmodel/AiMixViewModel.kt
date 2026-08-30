package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.ai.AiOrchestrator
import com.theveloper.pixelplay.data.ai.AiSystemPromptType
import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** AI Mix 历史条目：一条已生成、可回放/重开歌单。 */
data class AiMixHistoryEntry(
    val title: String,
    val fromLocal: Boolean,
    val createdAt: Long,
    val songs: List<AiMixViewModel.AiMixSongItem>
)

/** 历史条目持久化（在线歌曲复用 [LxSongInfo] 序列化，本地歌曲存最小字段以便重建） */
@Serializable
private data class SavedMixItem(
    val kind: String, // "local" | "online"
    val stableId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    val local: SavedLocalSong?,
    val online: LxSongInfo?
)

@Serializable
private data class SavedLocalSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val path: String,
    val contentUriString: String,
    val albumArtUriString: String?,
    val duration: Long
)

@Serializable
private data class SavedMix(
    val title: String,
    val fromLocal: Boolean,
    val createdAt: Long,
    val items: List<SavedMixItem>
)

/**
 * AI Mix：根据用户输入的「需求描述的文案 + 情绪 + 歌曲数量」，
 * 让 AI 自主决定是直接从本地曲库挑选，还是调用内置在线搜索凑齐歌单。
 * 整个过程以「对话 + 操作日志」的形式展示在首页下弹卡片里（与 AI 搜索一致）。
 */
@HiltViewModel
class AiMixViewModel @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
    private val builtInSourceSearchApi: BuiltInSourceSearchApi,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    /** 一条可播放的歌单结果：本地 Song 与在线 LxSongInfo 二选一（可同时为空仅做占位）。 */
    data class AiMixSongItem(
        val stableId: String,
        val title: String,
        val subtitle: String,
        val coverUrl: String?,
        val sourceLabel: String,
        val localSong: Song? = null,
        val onlineInfo: LxSongInfo? = null
    )

    enum class Phase { IDLE, GENERATING, READY }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val messages: List<AiChatMsg> = emptyList(),
        val loading: Boolean = false,
        val results: List<AiMixSongItem> = emptyList(),
        val playlistTitle: String = "",
        val fromLocal: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── 历史记录 ──
    private val _history = MutableStateFlow<List<AiMixHistoryEntry>>(emptyList())
    val history: StateFlow<List<AiMixHistoryEntry>> = _history.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { loadHistory() }
    }

    private fun newId() = UUID.randomUUID().toString()

    companion object {
        private const val TAG = "AiMix"
        private const val MAX_CANDIDATES = 12
        private const val PER_QUERY = 5
        private const val MAX_POOL = 20
        private const val MAX_HISTORY = 20
        private val EMOTIONS = listOf("平静", "开心", "难过", "焦虑", "专注", "元气", "浪漫", "治愈")

        fun emotions(): List<String> = EMOTIONS
    }

    fun reset() {
        _uiState.value = UiState()
    }

    /** 从持久化 JSON 载入历史（跨会话保留）。 */
    private suspend fun loadHistory() {
        val raw = runCatching { userPreferencesRepository.getAiMixHistoryOnce() }.getOrNull() ?: return
        val saved = runCatching { json.decodeFromString<List<SavedMix>>(raw) }.getOrNull() ?: return
        _history.value = saved.map { it.toEntry() }
    }

    /** 新增一条历史并入持久化。 */
    private fun addHistory(entry: AiMixHistoryEntry) {
        val updated = (listOf(entry) + _history.value).take(MAX_HISTORY)
        _history.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            val jsonString = runCatching {
                json.encodeToString(updated.map { it.toSaved() })
            }.getOrNull()
            if (jsonString != null) {
                userPreferencesRepository.setAiMixHistory(jsonString)
            }
        }
    }

    /** 打开一条历史：把它还原成 READY 对话，可点击播放。 */
    fun restoreHistory(entry: AiMixHistoryEntry) {
        _uiState.value = UiState(
            phase = Phase.READY,
            results = entry.songs,
            playlistTitle = entry.title,
            fromLocal = entry.fromLocal,
            messages = listOf(
                AiChatMsg(newId(), AiChatRole.OP, "已从历史找回：${entry.title}")
            )
        )
    }

    /** 生成歌单。request 为需求描述，emotion 为情绪标签，count 为目标歌曲数量。 */
    fun build(request: String, emotion: String, count: Int) {
        val text = request.trim()
        val target = count.coerceIn(1, 50)
        if (text.isBlank() || _uiState.value.loading) return

        _uiState.value = _uiState.value.copy(
            phase = Phase.GENERATING,
            loading = true,
            error = null,
            results = emptyList()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                pushOp("AI 正在理解你的需求…")
                val mood = emotion.ifBlank { "无特定情绪" }
                pushOp("已接收需求：「$text」，情绪：$mood，目标 $target 首")

                // 1) 读取本地曲库概览，供 AI 判断是本地够用还是需要在线补足
                pushOp("正在扫描本地曲库…")
                val localAll = withContext(Dispatchers.IO) {
                    runCatching { musicRepository.getAllSongsOnce() }.getOrDefault(emptyList())
                }
                val localSample = localAll.take(120)
                pushOp("本地曲库共有 ${localAll.size} 首可播放歌曲")

                // 2) 让 AI 决策：用本地曲库 or 在线搜索（分别给出候选关键词）
                val plan = decidePlan(text, mood, target, localSample)
                val useLocal = plan.source == "local"
                if (useLocal) {
                    pushOp("AI 决定优先从本地曲库为你挑选")
                } else {
                    pushOp("AI 决定调用在线搜索来凑齐歌单")
                }

                val picked: List<AiMixSongItem>
                if (useLocal) {
                    picked = pickLocal(plan.queries, target, localAll)
                    if (picked.isEmpty()) {
                        pushOp("本地曲库匹配较少，改用在线搜索补充…")
                        val onlinePicked = pickOnline(plan.fallbackQueries, target)
                        if (onlinePicked.isNotEmpty()) {
                            pushOp("在线搜索已为你精选出 ${onlinePicked.size} 首～")
                            finishPlan(mood, onlinePicked, fromLocal = false)
                            return@launch
                        }
                    } else {
                        pushOp("已从本地曲库精选出 ${picked.size} 首～")
                        finishPlan(mood, picked, fromLocal = true)
                        return@launch
                    }
                }

                // 3) 在线搜索并挑选
                val onlinePicked = pickOnline(plan.queries, target)
                if (onlinePicked.isNotEmpty()) {
                    finishPlan(mood, onlinePicked, fromLocal = false)
                } else {
                    pushAi("抱歉，暂时没能为你的需求凑齐歌单，换个需求或情绪再试试吧。")
                    _uiState.value = _uiState.value.copy(
                        phase = Phase.READY,
                        loading = false,
                        error = "没有找到合适的歌曲"
                    )
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "AiMix failed")
                pushAi("AI Mix 出错了：${t.message ?: t.javaClass.simpleName ?: "未知错误"}")
                _uiState.value = _uiState.value.copy(
                    phase = Phase.READY,
                    loading = false,
                    error = t.message
                )
            } finally {
                // 若未在分支内复位 loading
                if (_uiState.value.phase == Phase.GENERATING) {
                    _uiState.value = _uiState.value.copy(loading = false)
                }
            }
        }
    }

    private suspend fun finishPlan(mood: String, songs: List<AiMixSongItem>, fromLocal: Boolean) {
        val title = buildPlaylistTitle(mood, songs, fromLocal)
        _uiState.value = _uiState.value.copy(
            phase = Phase.READY,
            loading = false,
            results = songs,
            playlistTitle = title,
            fromLocal = fromLocal
        )
        // 记入历史（跨会话可找回）
        addHistory(
            AiMixHistoryEntry(
                title = title,
                fromLocal = fromLocal,
                createdAt = System.currentTimeMillis(),
                songs = songs
            )
        )
        pushAi("歌单「${title}」已生成，共 ${songs.size} 首，点击即可播放～")
    }

    // ── AI 决策来源 ─────────────────────────────────────────────
    private data class MixPlan(
        val source: String = "online",
        val queries: List<String> = emptyList(),
        val fallbackQueries: List<String> = emptyList()
    )

    private suspend fun decidePlan(
        request: String,
        mood: String,
        target: Int,
        localSample: List<Song>
    ): MixPlan {
        val localText = if (localSample.isEmpty()) {
            "（本地曲库为空）"
        } else {
            localSample.take(60)
                .map { "${it.title} - ${it.artist}" }
                .joinToString("\n") { s -> "· $s" }
        }
        val raw = withContext(Dispatchers.IO) {
            aiOrchestrator.generateContent(
                prompt = """
用户需求：$request
当前情绪：$mood
目标歌曲数量：$target

本地曲库部分歌单（歌名 - 歌手，仅作你是否足够的参考）：
$localText

请决策下一步：
- 若本地曲库能找到满足用户需求/情绪的歌曲，返回 {"source":"local","queries":["本地搜索关键词"...],"fallback_queries":[]}
- 若需要在线搜索，返回 {"source":"online","queries":["歌名 - 歌手"...],"fallback_queries":[]}
queries 提供 8~12 个候选搜索词，能最贴合用户需求与情绪风格。
只输出一个 JSON 对象，不要输出任何其他文字、代码块或注释。
""".trimIndent(),
                type = AiSystemPromptType.GENERAL,
                temperature = 0.4f,
                context = "你是音乐歌单生成助手，根据用户需求与情绪决定从本地曲库还是在线搜索凑齐歌单。"
            )
        }
        return parsePlan(raw)
    }

    private fun parsePlan(raw: String): MixPlan {
        val t = raw.trim()
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching {
                val obj = JSONObject(t.substring(start, end + 1))
                val source = obj.optString("source", "online")
                return MixPlan(
                    source = source,
                    queries = optStringArray(obj, "queries"),
                    fallbackQueries = optStringArray(obj, "fallback_queries")
                )
            }
        }
        return MixPlan()
    }

    // ── 本地挑选 ────────────────────────────────────────────────
    private fun pickLocal(
        queries: List<String>,
        target: Int,
        localAll: List<Song>
    ): List<AiMixSongItem> {
        val keysToMatch = queries.map { normalizeKey(it) }
        val picked = LinkedHashMap<String, Song>()
        localAll.forEach { song ->
            val key = normalizeKey("${song.title} - ${song.artist}")
            val hit = keysToMatch.any { q ->
                key.contains(q) && !q.isBlank()
            }
            if (hit && !picked.containsKey(key)) {
                picked[key] = song
            }
        }
        // 若关键词命中的不足，从全库补足到 target（保证歌单数量）
        if (picked.size < target) {
            localAll.forEach { song ->
                val key = normalizeKey("${song.title} - ${song.artist}")
                if (!picked.containsKey(key) && picked.size < target) {
                    picked[key] = song
                }
            }
        }
        return picked.values.take(target).map { localToItem(it) }
    }

    private fun localToItem(song: Song): AiMixSongItem = AiMixSongItem(
        stableId = song.id,
        title = song.title,
        subtitle = song.artist.ifBlank { "本地歌曲" },
        coverUrl = song.albumArtUriString?.takeIf { it.isNotBlank() },
        sourceLabel = "本地",
        localSong = song
    )

    // ── 在线挑选 ────────────────────────────────────────────────
    private suspend fun pickOnline(
        queries: List<String>,
        target: Int
    ): List<AiMixSongItem> {
        if (queries.isEmpty()) return emptyList()
        // 多源搜索（酷我/QQ音乐/酷狗/咪咕），合并去重
        val sources = listOf("kw", "tx", "kg", "mg")
        val sourceLabels = "酷我/QQ音乐/酷狗/咪咕"
        val pool = LinkedHashMap<String, LxSongInfo>()
        queries.forEach { query ->
            if (pool.size >= MAX_POOL) return@forEach
            pushOp("正在$sourceLabels 搜索「${query.trim()}」…")
            for (src in sources) {
                if (pool.size >= MAX_POOL) break
                val result = builtInSourceSearchApi.search(src, query.trim(), 1, PER_QUERY)
                result.list.forEach { hit ->
                    val key = normalizeKey("${hit.name} - ${hit.singer}")
                    if (key.isNotBlank() && !pool.containsKey(key)) {
                        pool[key] = hit
                    }
                }
            }
        }
        if (pool.isEmpty()) return emptyList()
        pushOp("共找到 ${pool.size} 首候选，正在交给 AI 挑选…")
        return pickFromPoolOnline(target, pool)
    }

    private suspend fun pickFromPoolOnline(
        target: Int,
        pool: Map<String, LxSongInfo>
    ): List<AiMixSongItem> {
        val labels = pool.values.map { "${it.name} - ${it.singer}".replace(" ", "") }
        val raw = withContext(Dispatchers.IO) {
            aiOrchestrator.generateContent(
                prompt = """
根据用户需求，从下面的候选歌曲清单中挑选最适合的 ${target} 首组成歌单。
候选清单（歌名 - 歌手，已去掉多余空格）：
${labels.mapIndexed { i, l -> "${i + 1}. $l" }.joinToString("\n")}
只输出一个 JSON 数组，每个元素必须原样引用上方清单中的「歌名 - 歌手」字符串（去掉内部空格），最多 $target 首。
不要新增清单外的歌曲，不要输出任何其他文字、代码块或注释。
""".trimIndent(),
                type = AiSystemPromptType.GENERAL,
                temperature = 0.4f,
                context = "你是音乐歌单生成助手，从候选歌曲中挑选最贴合用户需求的歌单。"
            )
        }
        val picked = parseCandidates(raw)
            .mapNotNull { label -> pool[label.replace(" ", "")] }
            .distinctBy { normalizeKey("${it.name} - ${it.singer}") }
        if (picked.isNotEmpty()) return picked.take(target).map { onlineToItem(it) }
        return emptyList()
    }

    private fun onlineToItem(info: LxSongInfo): AiMixSongItem = AiMixSongItem(
        stableId = info.id.ifBlank { "${info.songmid}|${info.hash}" },
        title = info.name,
        subtitle = info.singer.ifBlank { "在线歌曲" },
        coverUrl = info.pic.takeIf { it.isNotBlank() },
        sourceLabel = "在线",
        onlineInfo = info
    )

    // ── 歌单标题 ────────────────────────────────────────────────
    private suspend fun buildPlaylistTitle(
        mood: String,
        songs: List<AiMixSongItem>,
        fromLocal: Boolean
    ): String {
        val names = songs.take(5).map { it.title }.joinToString("、")
        val raw = runCatching {
            withContext(Dispatchers.IO) {
                aiOrchestrator.generateContent(
                    prompt = """
用户需求情绪：$mood
歌单包含：$names
为这个歌单起一个简短好听的名字（不超过 10 个字，不要引号，不要其他文字）。
""".trimIndent(),
                    type = AiSystemPromptType.GENERAL,
                    temperature = 0.7f,
                    context = "你是歌单命名助手。"
                )
            }
        }.getOrNull()?.trim()
        if (raw.isNullOrBlank() || raw.length > 12) {
            return if (fromLocal) "本地精选 · $mood" else "云端精选 · $mood"
        }
        return raw.trim('"')
    }

    // ── 工具 ────────────────────────────────────────────────────
    private fun normalizeKey(s: String): String = s.replace(" ", "").lowercase()

    private fun optStringArray(obj: JSONObject, key: String): List<String> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val el = arr.optString(i).trim()
            if (el.isNotBlank()) out += el
        }
        return out
    }

    private fun parseCandidates(raw: String): List<String> {
        val cleaned = raw
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val results = ArrayList<String>()
        val arr = runCatching {
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start >= 0 && end > start) {
                org.json.JSONArray(cleaned.substring(start, end + 1))
            } else null
        }.getOrNull()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim().trim('"')
                if (s.isNotBlank()) results += s
            }
        }
        return results
    }

    // ── 消息辅助 ────────────────────────────────────────────────
    private fun pushOp(text: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(newId(), AiChatRole.OP, text)
        )
    }

    private fun pushAi(text: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(newId(), AiChatRole.AI, text)
        )
    }
}

// ── 历史条目 <-> 持久化模型 映射 ───────────────────────────────

private fun AiMixHistoryEntry.toSaved(): SavedMix = SavedMix(
    title = title,
    fromLocal = fromLocal,
    createdAt = createdAt,
    items = songs.map { it.toSavedItem() }
)

private fun SavedMix.toEntry(): AiMixHistoryEntry = AiMixHistoryEntry(
    title = title,
    fromLocal = fromLocal,
    createdAt = createdAt,
    songs = items.mapNotNull { it.toItem() }
)

private fun AiMixViewModel.AiMixSongItem.toSavedItem(): SavedMixItem = SavedMixItem(
    kind = if (localSong != null) "local" else "online",
    stableId = stableId,
    title = title,
    subtitle = subtitle,
    coverUrl = coverUrl,
    local = localSong?.let { SavedLocalSong(
        id = it.id,
        title = it.title,
        artist = it.artist,
        album = it.album,
        albumId = it.albumId,
        path = it.path,
        contentUriString = it.contentUriString,
        albumArtUriString = it.albumArtUriString,
        duration = it.duration
    ) },
    online = onlineInfo
)

private fun SavedMixItem.toItem(): AiMixViewModel.AiMixSongItem? = when (kind) {
    "local" -> local?.let { s ->
        AiMixViewModel.AiMixSongItem(
            stableId = stableId,
            title = title,
            subtitle = subtitle,
            coverUrl = coverUrl,
            sourceLabel = "本地",
            localSong = Song(
                id = s.id,
                title = s.title,
                artist = s.artist,
                artistId = 0L,
                album = s.album,
                albumId = s.albumId,
                path = s.path,
                contentUriString = s.contentUriString,
                albumArtUriString = s.albumArtUriString,
                duration = s.duration,
                mimeType = null,
                bitrate = null,
                sampleRate = null
            )
        )
    }
    else -> online?.let { info ->
        AiMixViewModel.AiMixSongItem(
            stableId = stableId,
            title = title,
            subtitle = subtitle,
            coverUrl = coverUrl,
            sourceLabel = "在线",
            onlineInfo = info
        )
    }
}