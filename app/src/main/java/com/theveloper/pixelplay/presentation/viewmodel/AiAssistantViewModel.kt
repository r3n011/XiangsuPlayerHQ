package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.ai.AiOrchestrator
import com.theveloper.pixelplay.data.ai.AiSongSearchService
import com.theveloper.pixelplay.data.ai.AiSystemPromptType
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * 全能 AI 助手：由 AI 自主决定调用哪个工具（ReAct 工具循环）。
 *
 * 模型层没有原生 function calling，因此用一个「反射式」循环：
 * 把工具清单+JSON 协议写进系统提示，模型每次返回一个 JSON 工具调用或最终文本；
 * 调用方执行工具并把结果回填给模型，循环直至 AI 产出最终回答。
 * 工具（复用 [AiSongSearchService]）：
 *  - search_songs：按描述在线搜歌
 *  - generate_playlist：按描述生成并保存歌单
 *  - open_settings：跳到对应设置分类
 */
@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
    private val songSearchService: AiSongSearchService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private fun newId() = UUID.randomUUID().toString()

    companion object {
        private const val TAG = "AiAssistant"
        private const val MAX_STEPS = 8
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isBlank() || _uiState.value.thinking) return

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMsg(newId(), AiChatRole.USER, text)
        )

        viewModelScope.launch(Dispatchers.IO) {
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
                appendThinking(thinkId, "正在理解你的需求…")

                // 每次 send 的独立 transcript，推进 ReAct 循环
                val transcript = StringBuilder()
                transcript.append("用户：$text")
                var allSongs = emptyList<LxSongInfo>()
                var step = 0
                var finalText: String? = null
                var hasToolResult = true

                while (step < MAX_STEPS && hasToolResult) {
                    step++
                    val raw = withContext(Dispatchers.IO) {
                        aiOrchestrator.generateContent(
                            prompt = buildAgentPrompt(transcript.toString()),
                            type = AiSystemPromptType.GENERAL,
                            temperature = 0.4f
                        )
                    }
                    val call = parseToolCall(raw)
                    if (call == null) {
                        finalText = raw.trim()
                        hasToolResult = false
                        continue
                    }

                    when (call.tool) {
                        "search_songs" -> {
                            appendThinking(thinkId, "正在在线搜索「${call.q}」…")
                            val songs = songSearchService.searchSynth(call.q)
                            if (songs.isEmpty()) {
                                pushOp("在线搜索「${call.q}」没有找到合适的歌曲。")
                                transcript.append("\n工具结果(search_songs,q=${call.q})：无结果")
                            } else {
                                allSongs = songs
                                pushOp("已按「${call.q}」搜索到 ${songs.size} 首歌曲。")
                                transcript.append(
                                    "\n工具结果(search_songs,q=${call.q})：找到 " +
                                        songs.take(5).joinToString("、") { "${it.name} - ${it.singer}" }
                                )
                            }
                        }
                        "generate_playlist" -> {
                            appendThinking(thinkId, "正在按「${call.description}」生成歌单…")
                            val songs = songSearchService.searchSynth(call.description)
                            if (songs.isEmpty()) {
                                pushOp("未能为「${call.description}」生成歌单，未找到合适歌曲。")
                                transcript.append("\n工具结果(generate_playlist)：无合适歌曲，生成失败")
                            } else {
                                allSongs = songs
                                val name = songSearchService.saveAsPlaylist(songs)
                                pushOp(if (name != null) "已生成歌单「$name」，共 ${songs.size} 首。" else "歌单已生成（保存到媒体库时遇到问题）。")
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
                                pushOp("已为你打开「${call.category}」设置。")
                                transcript.append("\n工具结果(open_settings)：已打开 ${call.category}")
                            } else {
                                pushOp("没有找到「${call.category}」对应的设置入口，请换一个说法。")
                                transcript.append("\n工具结果(open_settings)：分类 ${call.category} 无效")
                            }
                        }
                        else -> {
                            pushOp("AI 尝试调用未知工具「${call.tool}」，已跳过。")
                            transcript.append("\n工具结果：未知工具 ${call.tool}")
                        }
                    }
                }

                // 有最终文本则以 AI 消息呈现；否则给出兜底说明
                val reply = finalText ?: if (allSongs.isNotEmpty()) "已为你准备好以上内容～" else "我暂时没找到合适的答案，换个说法再试试吧。"
                setThinkingDone(thinkId)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + AiChatMsg(
                        id = newId(),
                        role = AiChatRole.AI,
                        text = reply,
                        songs = allSongs
                    )
                )
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "AiAssistant failed")
                pushAi("出错了：${t.message ?: t.javaClass.simpleName ?: "未知错误"}")
            } finally {
                _uiState.value = _uiState.value.copy(thinking = false)
                val list = _uiState.value.messages
                _uiState.value = _uiState.value.copy(
                    messages = list.map { if (it.streaming) it.copy(streaming = false) else it }
                )
            }
        }
    }

    /** 处理完导航后清除 [navToSettings]，避免重复跳转。 */
    fun consumeNavigation() {
        _uiState.value = _uiState.value.copy(navToSettings = null)
    }

    fun reset() {
        _uiState.value = AiAssistantUiState()
    }

    private fun buildAgentPrompt(transcript: String): String = """
你是一个全能音乐与 App 助手。根据上面的对话记录，自主决定要调用哪个工具，或直接回答。
可用工具（只输出一个 JSON 对象）：
1. 在线搜索歌曲：{"tool":"search_songs","q":"搜索关键词"}
2. 生成歌单：{"tool":"generate_playlist","description":"对歌单的描述/风格需求"}
3. 打开设置：{"tool":"open_settings","category":"设置分类","reason":"为什么"}，category 必须是下面任意一个：
   playback=播放与音质、appearance=外观、library=媒体库、ai_integration=AI 设置、backup_restore=备份恢复、developer=开发者、equalizer=均衡器、device_capabilities=设备能力、glyph_matrix=Glyph 矩阵、about=关于。
当用户只是聊天（如打招呼）、或已不需要再调用工具时，直接输出纯文本回答（不要 JSON）。
如果用户要求搜歌或生成歌单，必须先调用上述工具，不能凭空给歌单。
注意：每个作品名请用「歌名 - 歌手」描述。

对话记录：
$transcript
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

    private fun appendThinking(id: String, line: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map {
                if (it.id == id) it.copy(thinking = if (it.thinking.isBlank()) line else it.thinking + "\n" + line) else it
            }
        )
    }

    private fun setThinkingDone(id: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map {
                if (it.id == id) it.copy(thinkingDone = true, streaming = false) else it
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
}

data class AiAssistantUiState(
    val messages: List<AiChatMsg> = emptyList(),
    val thinking: Boolean = false,
    val error: String? = null,
    val navToSettings: SettingsCategory? = null
)