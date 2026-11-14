package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.ai.AiOrchestrator
import com.theveloper.pixelplay.data.ai.AiSongSearchService
import com.theveloper.pixelplay.data.ai.AiSystemPromptType
import com.theveloper.pixelplay.data.ai.WebSearchService
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * 全能 AI 助手：由 AI 自主决定调用哪个工具（ReAct 工具循环）。
 *
 * 模型层没有原生 function calling，因此用一个「反思式」循环：
 * 把工具清单+JSON 协议写进系统提示，模型每次返回一个 JSON 工具调用或最终文本；
 * 调用方执行工具并把结果回填给模型，循环直至 AI 产出最终回答。
 * 最终回答通过流式接口逐字输出，让用户看清楚 AI 正在生成什么。
 * 工具（复用 [AiSongSearchService]）：
 *  - search_songs：按描述在线搜歌
 *  - generate_playlist：按描述生成并保存歌单
 *  - open_settings：跳到对应设置分类
 */
@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
    private val songSearchService: AiSongSearchService,
    private val preferencesRepo: AiPreferencesRepository,
    private val playbackStateHolder: PlaybackStateHolder,
    private val webSearchService: WebSearchService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private fun newId() = UUID.randomUUID().toString()

    companion object {
        private const val TAG = "AiAssistant"
        private const val MAX_STEPS = 8
    }

    init {
        // 启动时恢复上次的聊天记录
        viewModelScope.launch(Dispatchers.IO) {
            val history = runCatching { preferencesRepo.aiAssistantChatHistory.first() }.getOrNull()
            val restored = history?.let { runCatching { decodeMessages(it) }.getOrNull() }
            if (!restored.isNullOrEmpty()) {
                _uiState.value = AiAssistantUiState(messages = restored)
            }
        }
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isBlank() || _uiState.value.thinking) return

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(newId(), AiChatRole.USER, text)
        )

        viewModelScope.launch(Dispatchers.IO) {
            var answered = false
            try {
                _uiState.value = _uiState.value.copy(thinking = true, error = null)

                // 开启一个「AI 思考」折叠块，进度随步骤追加
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
                appendThinking(thinkId, AppLocaleManager.getString(R.string.ui_understanding_your_request))

                // 每次 send 的独立 transcript，推进 ReAct 循环
                val transcript = StringBuilder()
                transcript.append("用户：$text")
                // ⚡ 跨轮累积的模型真实思考文本，逐字写入思考块
                val thinkingSb = StringBuilder()
                var allSongs = emptyList<LxSongInfo>()
                var step = 0
                var hasToolResult = true

                while (step < MAX_STEPS && hasToolResult) {
                    step++
                    // 流式生成本轮内容：最终回答实时逐字输出，工具调用则隐藏（它本来就是 JSON）
                    val sb = StringBuilder()
                    var answerId: String? = null
                    var genError: String? = null
                    val streamSucceeded = try {
                        aiOrchestrator.generateContentStreamWithReasoning(
                            prompt = buildAgentPrompt(transcript.toString()),
                            type = AiSystemPromptType.GENERAL,
                            temperature = 0.4f
                        ).collect { chunk ->
                            if (chunk.isThinking) {
                                // ⚡ 模型真实思考：逐字追加到思考块（不换行拼接）
                                thinkingSb.append(chunk.text)
                                updateMessage(thinkId) { it.copy(thinking = thinkingSb.toString()) }
                                return@collect
                            }
                            sb.append(chunk.text)
                            val isProse = !sb.toString().trimStart().startsWith("{")
                            if (isProse && answerId == null) {
                                answerId = newId()
                                _uiState.value = _uiState.value.copy(
                                    messages = _uiState.value.messages + AiChatMsg(
                                        id = answerId,
                                        role = AiChatRole.AI,
                                        text = "",
                                        streaming = true
                                    )
                                )
                            }
                            if (answerId != null) {
                                val textSoFar = sb.toString()
                                updateMessage(answerId) { it.copy(text = textSoFar) }
                            }
                        }
                        true
                    } catch (t: Throwable) {
                        Timber.tag(TAG).w(t, "Stream failed at step $step")
                        genError = t.message ?: t.javaClass.simpleName ?: AppLocaleManager.getString(R.string.ui_streaming_response_failed)
                        false
                    }

                    // 兜底：流式失败或异步产不出内容时，回退到一次性（非流式）生成，
                    // 兼容不支持流式或流式异常返回空的 provider，避免整轮空转。
                    if (!streamSucceeded || sb.isEmpty()) {
                        runCatching {
                            aiOrchestrator.generateContent(
                                prompt = buildAgentPrompt(transcript.toString()),
                                type = AiSystemPromptType.GENERAL,
                                temperature = 0.4f
                            )
                        }.onSuccess { oneShot ->
                            if (oneShot.isNotBlank()) {
                                sb.setLength(0)
                                sb.append(oneShot)
                                genError = null
                            }
                        }
                    }

                    val call = parseToolCall(sb.toString())
                    if (call != null && genError == null) {
                        // 工具调用：不应显示内容气泡；若期间误显示了碎text则移除，避免残留垃圾内容
                        if (answerId != null) {
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages.filterNot { it.id == answerId }
                            )
                        }
                        // 工具调用：不会创建可见气泡（它以 { 开头），执行工具并记录进度
                        when (call.tool) {
                            "search_songs" -> {
                                appendThinking(thinkId, AppLocaleManager.getString(R.string.ui_searching_online_for, (call.q).toString()))
                                val songs = songSearchService.searchSynth(call.q)
                                if (songs.isEmpty()) {
                                    pushOp(AppLocaleManager.getString(R.string.ui_no_suitable_songs_found_online_for, (call.q).toString()))
                                    transcript.append("\n工具结果(search_songs,q=${call.q})：无结果")
                                } else {
                                    allSongs = songs
                                    pushOp(AppLocaleManager.getString(R.string.ui_found_songs_for, (call.q).toString(), (songs.size).toString()))
                                    transcript.append(
                                        "\n工具结果(search_songs,q=${call.q})：找到 " +
                                            songs.take(5).joinToString("、") { "${it.name} - ${it.singer}" }
                                    )
                                }
                            }
                            "generate_playlist" -> {
                                appendThinking(thinkId, AppLocaleManager.getString(R.string.ui_creating_a_playlist_for, (call.description).toString()))
                                val songs = songSearchService.searchSynth(call.description)
                                if (songs.isEmpty()) {
                                    pushOp(AppLocaleManager.getString(R.string.ui_could_not_create_a_playlist_for_no_suitable_songs_found, (call.description).toString()))
                                    transcript.append("\n工具结果(generate_playlist)：无合适歌曲，生成失败")
                                } else {
                                    allSongs = songs
                                    val name = songSearchService.saveAsPlaylist(songs)
                                    pushOp(if (name != null) AppLocaleManager.getString(R.string.ui_created_playlist_with_songs, (name).toString(), (songs.size).toString()) else AppLocaleManager.getString(R.string.ui_playlist_created_but_could_not_be_saved_to_the_library))
                                    transcript.append(
                                        "\n工具结果(generate_playlist)：已生成 ${songs.size} 首歌单，歌名 " +
                                            songs.take(8).joinToString("、") { it.name }
                                    )
                                }
                            }
                            "open_settings" -> {
                                val category = SettingsCategory.fromId(call.category)
                                if (category != null) {
                                    _uiState.value = _uiState.value.copy(navToSettings = category)
                                    pushOp(AppLocaleManager.getString(R.string.ui_opened_settings, (call.category).toString()))
                                    transcript.append("\n工具结果(open_settings)：已打开 ${call.category}")
                                } else {
                                    pushOp(AppLocaleManager.getString(R.string.ui_no_settings_found_for_try_a_different_description, (call.category).toString()))
                                    transcript.append("\n工具结果(open_settings)：分类 ${call.category} 无效")
                                }
                            }
                            "get_current_song" -> {
                                appendThinking(thinkId, AppLocaleManager.getString(R.string.ui_reading_the_current_song))
                                val song = playbackStateHolder.stablePlayerState.value.currentSong
                                if (song == null) {
                                    pushOp(AppLocaleManager.getString(R.string.ui_no_song_is_playing))
                                    transcript.append("\n工具结果(get_current_song)：无正在播放的歌曲")
                                } else {
                                    val pos = playbackStateHolder.currentPosition.value
                                    val genreText = song.genre?.takeIf { it.isNotBlank() }?.let { AppLocaleManager.getString(R.string.ui_genre, (it).toString()) } ?: ""
                                    val yearText = if (song.year > 0) AppLocaleManager.getString(R.string.ui_released_in, (song.year).toString()) else ""
                                    val durText = if (song.duration > 0) AppLocaleManager.getString(R.string.ui_duration_min_s, (song.duration / 60000).toString(), (song.duration % 60000 / 1000).toString()) else ""
                                    val posText = if (pos > 0) AppLocaleManager.getString(R.string.ui_playback_position_min_s, (pos / 1000 / 60).toString(), (pos / 1000 % 60).toString()) else ""
                                    val desc = AppLocaleManager.getString(R.string.ui_by_album, (song.title).toString(), (song.displayArtist).toString(), (song.album).toString()) +
                                        "$genreText$yearText$durText$posText"
                                    pushOp(AppLocaleManager.getString(R.string.ui_now_playing_4c0a5b8, (desc).toString()))
                                    transcript.append("\n工具结果(get_current_song)：$desc")
                                }
                            }
                            "web_search" -> {
                                appendThinking(thinkId, AppLocaleManager.getString(R.string.ui_searching_the_web_for, (call.q).toString()))
                                val results = webSearchService.search(call.q, 4)
                                if (results.isEmpty()) {
                                    pushOp(AppLocaleManager.getString(R.string.ui_no_useful_web_results_for, (call.q).toString()))
                                    transcript.append("\n工具结果(web_search,q=${call.q})：无结果")
                                } else {
                                    pushOp(AppLocaleManager.getString(R.string.ui_found_web_results_for, (results.size).toString(), (call.q).toString()))
                                    val joined = results.take(4).joinToString("\n") {
                                        "- ${it.title}（${it.url}）：${it.snippet.take(120)}"
                                    }
                                    transcript.append("\n工具结果(web_search,q=${call.q})：\n$joined")
                                }
                            }
                            else -> {
                                pushOp(AppLocaleManager.getString(R.string.ui_ai_requested_unknown_tool_skipped, (call.tool).toString()))
                                transcript.append("\n工具结果：未知工具 ${call.tool}")
                            }
                        }
                        continue
                    }

                    // 生成过程报错仍无内容：把真实原因回报给用户，不再用误导性的“没找到答案”
                    if (genError != null) {
                        if (answerId != null) {
                            updateMessage(answerId) { it.copy(streaming = false, songs = allSongs) }
                        } else {
                            pushAi(AppLocaleManager.getString(R.string.ui_something_went_wrong, (genError?.take(200)).toString()))
                        }
                        answered = true
                        hasToolResult = false
                        continue
                    }

                    // 到达最终回答：若泡已存在则已流式输出，否则兜底为静态文本
                    if (answerId != null) {
                        updateMessage(answerId) {
                            val t = sb.toString().trim()
                            it.copy(text = if (t.isBlank()) it.text else t, streaming = false, songs = allSongs)
                        }
                    } else {
                        val reply = sb.toString().trim().ifBlank {
                            if (allSongs.isNotEmpty()) AppLocaleManager.getString(R.string.ui_here_is_what_i_prepared_for_you) else AppLocaleManager.getString(R.string.ui_i_could_not_find_a_suitable_answer_try_asking_another)
                        }
                        pushAiWithSongs(reply, allSongs)
                    }
                    answered = true
                    hasToolResult = false
                }

                // MAX_STEPS 用尽仍未产出最终回答时兜底
                if (!answered) {
                    if (allSongs.isNotEmpty()) pushAi(AppLocaleManager.getString(R.string.ui_here_are_your_recommendations))
                    else pushAi(AppLocaleManager.getString(R.string.ui_i_could_not_find_a_suitable_answer_try_asking_another))
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "AiAssistant failed")
                pushAi(AppLocaleManager.getString(R.string.ui_something_went_wrong, (t.message ?: t.javaClass.simpleName ?: AppLocaleManager.getString(R.string.ui_residue_error_unknown)).toString()))
            } finally {
                _uiState.value = _uiState.value.copy(thinking = false)
                val list = _uiState.value.messages
                _uiState.value = _uiState.value.copy(
                    messages = list.map {
                        it.copy(streaming = false, thinkingDone = if (it.isThinking) true else it.thinkingDone)
                    }
                )
                persistChatHistory(_uiState.value.messages)
            }
        }
    }

    /** 处理完导航后清除 [navToSettings]，避免重复跳转。 */
    fun consumeNavigation() {
        _uiState.value = _uiState.value.copy(navToSettings = null)
    }

    fun reset() {
        _uiState.value = AiAssistantUiState()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { preferencesRepo.setAiAssistantChatHistory("") }
        }
    }

    private fun buildAgentPrompt(transcript: String): String = """
你是一个全能音乐与 App 助手。根据上面的对话记录，自主决定要调用哪个工具，或直接回答。
可用工具（只输出一个 JSON 对象）：
1. 在线搜索歌曲：{"tool":"search_songs","q":"搜索关键词"}
2. 生成歌单：{"tool":"generate_playlist","description":"对歌单的描述/风格需求"}
3. 打开设置：{"tool":"open_settings","category":"设置分类","reason":"为什么"}，category 必须是下面任意一个：
   playback=播放与音质、appearance=外观、library=媒体库、ai_integration=AI 设置、backup_restore=备份恢复、developer=开发者、equalizer=均衡器、device_capabilities=设备能力、glyph_matrix=Glyph 矩阵、about=关于。
4. 读取当前播放的歌曲：{"tool":"get_current_song"}，用户问“现在正在放什么歌/这首歌是什么/解读/科普这首”时调用；也可配合搜索继续讲解。
5. 联网搜索（必应）：{"tool":"web_search","q":"搜索关键词"}，用户需要最新资讯、歌曲背景介绍、歌手资料、歌词含义、事件等需要联网信息时调用，返回网页摘要。
当用户只是聊天（如打招呼）、或已不需要再调用工具时，直接输出纯文本回答（不要 JSON）。
如果用户要求搜歌或生成歌单，必须先调用上述工具，不能凭空给歌单。
需要解读/科普正在播放的歌曲时，先调用 get_current_song 拿到歌曲信息，若需要外部资料再用 web_search，最后综合成完整回答。
注意：每个作品名请用「歌名 - 歌手」描述。

对话记录：
$transcript

${if (AppLocaleManager.currentLocale().language == "en") "Write user-facing replies in English. Preserve JSON keys and tool identifiers." else ""}
""".trimIndent()

    private data class ToolCall(
        val tool: String,
        val q: String,
        val description: String,
        val category: String
    )

    /** 解析单次模型输出为工具调用；若输出不是 JSON 工具调用则返回 null（视为最终回答）。 */
    private fun parseToolCall(raw: String): ToolCall? {
        val t = raw.trim()
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        val json = if (start >= 0 && end > start) t.substring(start, end + 1) else null
        if (json != null) {
            runCatching {
                val obj = JSONObject(json)
                val tool = obj.optString("tool", "").trim()
                if (tool.isNotEmpty()) {
                    return ToolCall(
                        tool = tool,
                        q = obj.optString("q", "").trim(),
                        description = obj.optString("description", "").trim(),
                        category = obj.optString("category", "").trim()
                    )
                }
            }
        }
        return null
    }

    private fun updateMessage(id: String, transform: (AiChatMsg) -> AiChatMsg) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { if (it.id == id) transform(it) else it }
        )
    }

    private fun appendThinking(id: String, line: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map {
                if (it.id == id) it.copy(thinking = if (it.thinking.isBlank()) line else it.thinking + "\n" + line) else it
            }
        )
    }

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

    private fun pushAiWithSongs(text: String, songs: List<LxSongInfo>) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(newId(), AiChatRole.AI, text, songs = songs)
        )
    }

    // ---- 聊天记录持久化 ----

    @kotlinx.serialization.Serializable
    private data class SavedAiMsg(
        val id: String,
        val role: String,
        val text: String,
        val isThinking: Boolean = false,
        val thinking: String = "",
        val thinkingDone: Boolean = false,
        val songs: List<LxSongInfo> = emptyList()
    )

    private fun AiChatMsg.toSaved() = SavedAiMsg(
        id = id,
        role = if (role == AiChatRole.USER) "user" else if (role == AiChatRole.OP) "op" else "ai",
        text = text,
        isThinking = isThinking,
        thinking = thinking,
        thinkingDone = thinkingDone || isThinking,
        songs = songs
    )

    private fun SavedAiMsg.toMsg() = AiChatMsg(
        id = id,
        role = when (role) {
            "user" -> AiChatRole.USER
            "op" -> AiChatRole.OP
            else -> AiChatRole.AI
        },
        text = text,
        isThinking = isThinking,
        thinking = thinking,
        thinkingDone = thinkingDone,
        songs = songs
    )

    private fun encodeMessages(messages: List<AiChatMsg>): String =
        runCatching { Json.encodeToString(messages.map { it.toSaved() }) }.getOrElse { "" }

    private fun decodeMessages(json: String): List<AiChatMsg> =
        Json.decodeFromString<List<SavedAiMsg>>(json).map { it.toMsg() }

    private fun persistChatHistory(messages: List<AiChatMsg>) {
        val json = encodeMessages(messages)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { preferencesRepo.setAiAssistantChatHistory(json) }
                .onFailure { Timber.tag(TAG).w(it, "Failed to persist chat history") }
        }
    }
}

data class AiAssistantUiState(
    val messages: List<AiChatMsg> = emptyList(),
    val thinking: Boolean = false,
    val error: String? = null,
    val navToSettings: SettingsCategory? = null
)