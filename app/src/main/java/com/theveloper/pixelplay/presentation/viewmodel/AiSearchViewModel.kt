package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.ai.AiOrchestrator
import com.theveloper.pixelplay.data.ai.AiSystemPromptType
import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.lx.LxSearchApi
import com.theveloper.pixelplay.data.lx.LxSearchResult
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.lx.LxSongInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class AiChatRole { USER, AI, OP }

data class AiChatMsg(
    val id: String,
    val role: AiChatRole,
    val text: String,
    val isThinking: Boolean = false,
    val thinking: String = "",      // 思考块正文（Trae 风格折叠块）
    val thinkingDone: Boolean = false, // 思考是否已完成（false 时显示进行中动画）
    val streaming: Boolean = false,  // 是否正在流式输出（显示光标）
    val songs: List<LxSongInfo> = emptyList() // 该轮对话 AI 产出的歌单，内联显示在本次对话下方
)

data class AiSearchUiState(
    val messages: List<AiChatMsg> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val source: String = "kw"
)

/**
 * AI 搜索：用 LLM 理解用户自然语言需求，再把歌手/歌名作为关键词，
 * 调用内置搜索 API（酷我/QQ/酷狗/咪咕）逐首搜索并汇总成结果列表。
 * 整个过程以"对话 + 操作日志"的形式展示在搜索页。
 */
@HiltViewModel
class AiSearchViewModel @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
    private val builtInSourceSearchApi: BuiltInSourceSearchApi,
    private val lxSearchApi: LxSearchApi,
    private val musicRepository: MusicRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSearchUiState())
    val uiState: StateFlow<AiSearchUiState> = _uiState.asStateFlow()

    private var seq = 0
    private fun newId() = UUID.randomUUID().toString()

    companion object {
        private const val TAG = "AiSearch"

        fun sourceLabel(source: String): String = when (source) {
            "wy" -> "网易云"
            "kw" -> "酷我"
            "tx" -> "QQ音乐"
            "kg" -> "酷狗"
            "mg" -> "咪咕"
            else -> "内置音源"
        }

        /** 最多解析/搜索的候选歌曲数量（控制延迟） */
        private const val MAX_CANDIDATES = 8
        /** 每个关键词在对应源取前几条结果 */
        private const val PER_QUERY = 5
        /** AI 决策循环最多轮数（AI 可能觉得候选不够而要求继续搜索） */
        private const val MAX_ROUNDS = 4
        /** 候选池大小上限，达到后强制让 AI 直接产出歌单 */
        private const val MAX_POOL = 30
    }

    fun setSource(source: String) {
        // 网易云也会被纳入 AI 多源搜索；这里记录用户当前偏好的主要音源用于「继续搜索」
        if (source in listOf("wy", "kw", "tx", "kg", "mg")) {
            _uiState.value = _uiState.value.copy(source = source)
        }
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isBlank() || _uiState.value.loading) return

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(newId(), AiChatRole.USER, text)
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(loading = true, error = null)

                // 开启一个 Trae 风格的「AI 思考」折叠块，正文随进度追加
                val thinkId = newId()
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + AiChatMsg(
                        id = thinkId,
                        role = AiChatRole.AI,
                        text = "",
                        isThinking = true,
                        streaming = true
                    )
                )
                appendThinking(thinkId, "正在解析你的需求，提取候选搜索关键词…")

                val userTextForModel = text
                val candidates = parseCandidates(
                    withContext(Dispatchers.IO) {
                        aiOrchestrator.generateContent(
                            prompt = """
请把用户对音乐的描述/请求，拆解成最可能匹配的候选歌曲搜索词。
只输出一个 JSON 数组，不要输出任何其他文字、代码块或注释。
每个元素是一个短字符串，格式为「歌名 - 歌手」（不确定歌手时可只写歌名）。
示例：用户说“推荐几首周杰伦的老歌”，输出：["晴天 - 周杰伦","七里香 - 周杰伦","稻香 - 周杰伦"]
请输出 6~8 个候选。
用户请求：$userTextForModel
""".trimIndent(),
                            type = AiSystemPromptType.GENERAL,
                            temperature = 0.3f,
                            context = "这是一个音乐搜索助手，负责把用户需求转成到底的歌曲搜索关键词。"
                        )
                    }
                )

                if (candidates.isEmpty()) {
                    appendThinking(thinkId, "未能从需求中解析出候选关键词，已结束。\n")
                    setThinkingDone(thinkId)
                    pushAi("抱歉，我没能理解你想找什么样的歌曲，可以换个说法再试试～")
                    return@launch
                }

                // 阶段一：搜索 —— 收集候选池（供 AI 挑选，而非直接展示）
                // 多源并行搜索（网易云/酷我/QQ音乐/酷狗/咪咕），合并去重得到更丰富的候选池
                val sources = listOf("wy", "kw", "tx", "kg", "mg")
                appendThinking(thinkId, "已确定 ${candidates.size} 个候选关键词，开始从网易云/酷我/QQ音乐/酷狗/咪咕多源搜索候选歌曲…")
                val pool = LinkedHashMap<String, LxSongInfo>()
                candidates.forEach { query ->
                    if (_uiState.value.loading.not()) return@launch
                    appendThinking(thinkId, "正在多源搜索「$query」…")
                    for (src in sources) {
                        if (_uiState.value.loading.not()) return@launch
                        val result = searchSource(src, query, 1, PER_QUERY)
                        result.list.forEach { hit ->
                            val key = songKey(hit)
                            if (key.isNotBlank() && !pool.containsKey(key)) {
                                pool[key] = hit
                            }
                        }
                    }
                }
                if (pool.isEmpty()) {
                    appendThinking(thinkId, "没有搜索到候选歌曲，已结束。\n")
                    setThinkingDone(thinkId)
                    pushAi("暂时没有找到合适的歌曲，换个关键词或来源再试试吧。")
                    return@launch
                }
                appendThinking(thinkId, "共搜集到 ${pool.size} 首候选歌曲，正在让 AI 从精选歌单…")

                // 阶段二：AI 决策 —— AI 自主决定补充搜索还是直接输出歌单
                val picked = decideSongs(userTextForModel, pool)
                val pickedSongs = picked.distinctBy { songKey(it) }
                if (pickedSongs.isEmpty()) {
                    appendThinking(thinkId, "AI 未能从候选里挑出合适的歌曲，已结束。\n")
                    setThinkingDone(thinkId)
                    pushAi("AI 还没能挑出合适的歌曲，换个说法或来源再试试吧。")
                    return@launch
                }

                appendThinking(thinkId, "已从 ${pool.size} 首候选中精选出 ${pickedSongs.size} 首歌曲。")
                setThinkingDone(thinkId)

                // 完成时：AI 生成一段推荐语（流式输出），并把本次歌单内联挂到该条 AI 消息下方
                streamRecommendation(thinkId, userTextForModel, pickedSongs)
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "AiSearch failed")
                pushAi("搜索失败了：${t.message ?: t.javaClass.simpleName ?: "未知错误"}")
            } finally {
                _uiState.value = _uiState.value.copy(loading = false)
                val list = _uiState.value.messages
                _uiState.value = _uiState.value.copy(
                    messages = list.map { if (it.streaming) it.copy(streaming = false) else it }
                )
            }
        }
    }

    /**
     * 歌单生成完成后，流式生成一段电台风格的推荐语，并在一个 AI 气泡中逐字流出。
     */
    private suspend fun streamRecommendation(thinkId: String, userText: String, songs: List<LxSongInfo>) {
        val recMsgId = newId()
        val names = songs.take(5).map { it.name }.filter { it.isNotBlank() }.joinToString("、")
        appendThinking(thinkId, "正在为歌单撰写推荐语…")
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(
                id = recMsgId,
                role = AiChatRole.AI,
                text = "",
                streaming = true,
                songs = songs
            )
        )
        val sb = StringBuilder()
        var failed = false
        try {
            aiOrchestrator.generateContentStream(
                prompt = """
请为以下 AI 生成的歌单写一段简短温暖的推荐语（100 字以内）。
结合用户的需求「$userText」，自然地提到其中几首代表作，语气像懂音乐的朋友在推荐，不要用 markdown 标题、列表或引号。
歌单包含：$names
""".trimIndent(),
                type = AiSystemPromptType.GENERAL,
                temperature = 0.9f,
                context = "你是音乐电台主持，负责为 AI 生成的歌单撰写温暖自然的推荐语。"
            ).collect { chunk ->
                val next = sb.toString() + chunk
                sb.append(chunk)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map {
                        if (it.id == recMsgId) it.copy(text = next) else it
                    }
                )
            }
        } catch (e: Exception) {
            // 流式报错：记录，但不中断（下方统一兜底）
            Timber.tag(TAG).w(e, "Recommendation streaming failed")
            failed = true
        }

        // 若流式成功但没有任何内容（空流），或直接报错，就用兜底推荐语，
        // 保证歌单完成后必然会有一条可见的推荐语。
        if (sb.isBlank()) {
            val fallback = if (failed) {
                "推荐语生成遇到点小问题～不过按照你的需求「${userText}」，这 ${songs.size} 首歌组成的歌单已经为你准备好了，点击即可播放。"
            } else {
                "根据你的需求「${userText}」，AI 为你挑选了 ${songs.size} 首歌曲，组成这份专属歌单，点击任意一首即可播放。"
            }
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map {
                    if (it.id == recMsgId) it.copy(text = fallback, streaming = false) else it
                }
            )
        } else {
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map {
                    if (it.id == recMsgId) it.copy(streaming = false) else it
                }
            )
        }
    }

    /** 向指定的思考块追加一行进度/思考文本 */
    private fun appendThinking(id: String, line: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map {
                if (it.id == id) it.copy(thinking = if (it.thinking.isBlank()) line else it.thinking + "\n" + line) else it
            }
        )
    }

    /** 标记思考块已完成 */
    private fun setThinkingDone(id: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map {
                if (it.id == id) it.copy(thinkingDone = true, streaming = false) else it
            }
        )
    }

    /** 清空会话与结果 */
    fun reset() {
        _uiState.value = AiSearchUiState(source = _uiState.value.source)
        seq = 0
    }

    /**
     * 保存当前 AI 生成的歌单到「媒体库 → 播放列表」页面：
     * 先逐首把在线歌曲写入统一媒体库拿到稳定 ID，再创建一个 AI 生成的播放列表并排入全部歌曲。
     * 返回播放列表名称，失败返回 null。
     */
    suspend fun saveCurrentPlaylist(): String? {
        val songs = _uiState.value.messages.lastOrNull { it.songs.isNotEmpty() }?.songs ?: emptyList()
        if (songs.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            // 1) 把在线歌曲写入统一媒体库，按稳定 hash 去重拿到歌曲 ID
            val songIds = songs.mapNotNull { s ->
                runCatching { musicRepository.saveCloudSong(s) }.getOrNull()
                    ?.takeIf { it > 0 }?.toString()
            }.distinct()
            if (songIds.isEmpty()) return@withContext null

            // 2) 在媒体库创建 AI 生成的播放列表
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

    /** 追加"AI 操作"日志消息 */
    private fun pushOp(text: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(newId(), AiChatRole.OP, text)
        )
    }

    /** 追加"AI 助手"文本消息 */
    private fun pushAi(text: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(newId(), AiChatRole.AI, text)
        )
    }

    /**
     * 从 LLM 返回文本中解析候选歌曲列表。
     * 优先按严格 JSON 数组解析，失败时用正则提取歌名片段兜底。
     */
    private fun parseCandidates(raw: String): List<String> {
        val trimmed = raw.trim()
        // 去掉可能的 ```json 围栏
        val json = trimmed
            .removePrefix("```")
            .removePrefix("json")
            .trim()
            .removeSuffix("```")
            .trim()
        // 尝试抽取 JSON 数组
        runCatching {
            val start = json.indexOf('[')
            val end = json.lastIndexOf(']')
            if (start >= 0 && end > start) {
                val arr = JSONArray(json.substring(start, end + 1))
                val out = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val el = arr.optString(i).trim()
                    if (el.isNotBlank()) out += normalize(el)
                }
                return out.distinct().take(MAX_CANDIDATES)
            }
        }
        // 正则兜底：逐行/逗号分割
        return trimmed
            .split('\n', ',', '，', '、')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
            .map { it.trim('"', '[', ']', '\'', ' ', '-') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_CANDIDATES)
    }

    private fun normalize(s: String): String = s.trim('"', '[', ']', '\'', ' ')

    /**
     * 统一多源搜索入口：网易云走 [lxSearchApi]（官方加密接口），
     * 其余走内置源 API；网易云结果标记 source="wy" 以便播放时正确路由。
     */
    private suspend fun searchSource(src: String, query: String, page: Int, size: Int): LxSearchResult {
        val result = if (src == "wy") {
            runCatching { lxSearchApi.search(query, page, size) }.getOrNull() ?: LxSearchResult(isEnd = true)
        } else {
            builtInSourceSearchApi.search(src, query, page, size)
        }
        // 封面统一高清化：网易云缩略图（?param=300y300 等）在播放页会显得模糊，
        // 替换为高清参数；其它源不受影响。网易云结果标记 source="wy" 以便播放时正确路由。
        val list = result.list.map { it.copy(pic = upscalePic(it.pic), source = if (src == "wy") "wy" else it.source) }
        return result.copy(list = list)
    }

    /** 把常见的低清封面缩略图参数提升为高清（1:1），非此类 URL 原样返回 */
    private fun upscalePic(pic: String): String {
        if (pic.isBlank()) return pic
        return pic.replace(Regex("(?i)[?&]param=\\d+y\\d+"), "?param=1000y1000")
    }

    /** 用「歌名 - 歌手」作为歌曲唯一键（去除内部空白，便于与 AI 输出匹配） */
    private fun songKey(s: LxSongInfo): String =
        listOf(s.name, s.singer).filter { it.isNotBlank() }.joinToString(" - ").replace(" ", "")

    /**
     * 阶段二：AI 决策循环。把当前候选清单返回给 AI，AI 可反复决定：
     *  - action="search_more"：候选不够，继续用新关键词搜索补充；
     *  - action="done"：输出从中挑选的歌单。
     * 循环持续到 AI 认为足够、候选池达上限或轮数耗尽为止。
     */
    private suspend fun decideSongs(
        userText: String,
        pool: MutableMap<String, LxSongInfo>
    ): List<LxSongInfo> {
        var rounds = 0
        while (rounds < MAX_ROUNDS) {
            rounds++
            val labels = pool.values.map { "${it.name} - ${it.singer}".replace(" ", "") }
            val raw = withContext(Dispatchers.IO) {
                aiOrchestrator.generateContent(
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
            }
            val decision = parseDecision(raw)
            if (decision.action == "search_more" && decision.items.isNotEmpty() && pool.size < MAX_POOL && rounds < MAX_ROUNDS) {
                // AI 决定继续搜索：用新关键词补充候选池
                pushOp("AI 觉得候选还不够，正在继续补充搜索…")
                decision.items.forEach { query ->
                    if (pool.size >= MAX_POOL) return@forEach
                    pushOp("继续搜索「$query」…")
                    val result = searchSource(sourceLabelForSearchPart(), query, 1, PER_QUERY)
                    result.list.forEach { hit ->
                        val key = songKey(hit)
                        if (key.isNotBlank() && !pool.containsKey(key)) {
                            pool[key] = hit
                        }
                    }
                }
                continue
            }
            // action="done" 或无法继续：返回 AI 挑选的歌曲（若未挑出则以当前候选池兜底）
            val picked = decision.items.mapNotNull { pool[it] }
            val deduped = picked.distinctBy { songKey(it) }
            return if (deduped.isNotEmpty()) deduped else pool.values.toList()
        }
        // 轮数耗尽：让 AI 从当前候选池产出最终歌单（限制数量，取候选池前若干）
        return pool.values.take(MAX_CANDIDATES).toList()
    }

    /** 供 decideSongs 内部补充搜索用的当前音源标识 */
    private fun sourceLabelForSearchPart(): String = _uiState.value.source

    private data class AiPickDecision(val action: String, val items: List<String>)

    /** 解析 AI 决策 JSON：{"action":"done|search_more", "songs" 或 "queries": [...]} */
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
}