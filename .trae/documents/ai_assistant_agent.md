# 全能 AI 助手（ReAct 工具循环）

## Context

用户要求**重写**「发现」菜单里的 AI 助手入口，**不再跳转 AI Mix**。目标是一个真正的全能助手：由 AI 自主决定调用哪个能力（相当于 MCP 里的工具），第一版支持：
- 生成歌单（按描述自动多源在线搜索并产出专属歌单）
- 在线搜索歌曲（按描述搜歌并展示结果）
- 找到/打开设置（按需求直接跳到对应设置分类）

入口为**独立全屏聊天页**，外观需符合现有 App 的 UI 设计语言（Material3、圆角 20~28dp、`surfaceContainer*` 分层、`primaryContainer` 图标胶囊、内联歌单卡等）。

技术约束：现有 [`AiClient`](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/data/ai/provider/AiClient.kt) 是纯文本输入/输出，**无原生 function calling**（Gemini 走普通 REST `generateContent` 端点）。因此采用**反射式 ReAct 工具循环**：模型输出「JSON 工具调用」→ 应用执行工具、把结果回填给模型 → 循环，直到产出最终回答。该方案复用 `AiOrchestrator`/`AiClient`，对所有 Provider 通用。

## 可复用积木（已核实）

- `AiOrchestrator.generateContent(prompt, type, temperature, context)`（非流式，返回文本）与 `generateContentStream(...)`（`Flow<String>`）—— [`AiOrchestrator.kt`](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/data/ai/AiOrchestrator.kt)。
- `BuiltInSourceSearchApi.search(source, keyword, page, pageSize)` 与 `LxSearchApi.search(query, page, size)` —— 多源在线搜索。
- 聊天消息模型 `AiChatMsg`/`AiChatRole`（USER/AI/OP、thinking 折叠块、streaming、内联 `songs`）已存在于 [`AiSearchViewModel.kt`](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/AiSearchViewModel.kt)（顶层公有，可直接同包复用）。
- 多源搜索经典流程（parseCandidates → 并行搜索 → AI 决策 done/search_more）在 `AiSearchViewModel.kt` 中，可作为参考/抽离。
- 设置分类：`SettingsCategory` 枚举（`presentation/model/SettingsCategory.kt`）提供合法 `categoryId`；跳转用 `Screen.SettingsCategory.createRoute(id)`。
- 在线歌曲写入媒体库成歌单：`MusicRepository.saveCloudSong(song)` + `PlaylistPreferencesRepository.createPlaylist(name, songIds, isAiGenerated=true, source="AI")`。

## 实现步骤

### 1. 导航接入
- `presentation/navigation/Screen.kt`：新增 `object AiAssistant : Screen("ai_assistant")`。
- `presentation/navigation/AppNavigation.kt`：注册 `Screen.AiAssistant.route` → `AiAssistantScreen(onNavigateToSettings = { navController.navigateSafely(...) })`。
- `MainActivity.kt` 发现弹窗：把「AI 助手」选项的 `onClick` 由 `Screen.AiMixScreen.route` 改为 `Screen.AiAssistant.route`（保留文案）。

### 2. 新共享服务：`data/ai/AiSongSearchService.kt`（@Singleton、@Inject）
把「AI 理解→多源搜索→AI 决策产出歌单」抽成可复用的 suspend API（借鉴 `AiSearchViewModel` 逻辑，保持其不变，避免回归）：
- `suspend fun searchSynth(userText: String): List<LxSongInfo>` —— 解析候选词 → 网易云/酷我/QQ/酷狗/咪咕并行搜索合并去重 → AI 决策（search_more/done）→ 返回精选 `LxSongInfo` 列表（为空表示无结果）。
- `suspend fun saveAsPlaylist(songs: List<LxSongInfo>): String?` —— 写库成歌单，返回歌单名（复用 MusicRepository + PlaylistPreferencesRepository）。
依赖：`AiOrchestrator`、`BuiltInSourceSearchApi`、`LxSearchApi`、`MusicRepository`、`PlaylistPreferencesRepository`。

### 3. 新 ViewModel：`presentation/viewmodel/AiAssistantViewModel.kt`（@HiltViewModel）
- 状态：复用顶层 `AiChatMsg`/`AiChatRole` 的 `messages: List<AiChatMsg>` + `thinking: Boolean` + `error` + 导航命令 `navToSettings: SettingsCategory?`（StateFlow）。
- `fun send(userText)`：进入 ReAct 循环，最多 `MAX_STEPS=8` 轮：
  1. append 用户消息 + OP「AI 正在思考/决定调用…」 + 「AI 思考」折叠块。
  2. 组装 **transcript**（角色内容串）：系统协议 + 用户请求 + 历史 + 各工具结果；调 `aiOrchestrator.generateContent(..., type=GENERAL, temperature=0.3f)`。
  3. 解析返回：若为工具调用 JSON → 执行工具、append OP 日志、把结果作为下一条内容回填 transcript，`continue`；若为最终文本 → `setThinkingDone` 并流式 `generateContentStream` 输出到 AI 消息，结束循环。
- **工具清单（模型可见，JSON 协议）**：
  - `{"tool":"search_songs","q":"..."}` → `AiSongSearchService.searchSynth`；OP 日志「已搜索到 N 首」，结果回填；并把命中歌曲挂到该轮 AI 消息 `songs`（内联展示）。
  - `{"tool":"generate_playlist","description":"..."}` → `searchSynth(description)` + `saveAsPlaylist`；OP 日志「已生成歌单：<名>」。
  - `{"tool":"open_settings","category":"playback|appearance|library|ai_integration|...","reason":"..."}` → 若 category 合法则置 `navToSettings`（由 Screen 收集后导航），OP 日志「已为你打开设置」。
- 系统提示中**给出一份「功能↔categoryId」索引**（如 播放入口/音质→playback，外观→appearance，媒体库→library，AI→ai_integration，备份→backup_restore，开发者→developer，均衡器→equalizer），并对 JSON 输出格式给出**few-shot**，要求“只输出一个 JSON 对象或纯文本”。

### 4. 新界面：`presentation/screens/AiAssistantScreen.kt`
- 结构沿用现有 App 设计语言：顶部栏（返回 + 标题「AI 助手」）、可滚消息列表（`LazyColumn`）、底部输入框（`TextField` + 发送按钮），`imePadding()`/`navigationBarsPadding()`。
- 消息气泡用 `Surface` + `RoundedCornerShape(20~28dp)`、`surfaceContainer*` 分层；思考块为 `surfaceContainerLow` 可折叠卡片（Trae 风格），流式输出显示光标；内联歌单用现有 `SongRow`/列表卡组件展示（含播放触发）。
- 空状态：居中引导语 + 3 个建议示例（“给一份开车听的歌”、“打开音质设置”、“搜周杰伦的老歌”），点击直接填入。
- 权限/API 未配置提示：检测 `AiPreferencesRepository` API key 为空时给出跳转 `ai_integration` 的引导。

### 5. 资源
- `res/values/strings_screens.xml` + `values-zh-rCN/strings_screens.xml`：新增 `ai_assistant_title`、`ai_assistant_input_hint`、`ai_assistant_empty_title`、示例文案、思考块占位等。

## 关键文件

新建：
- `app/src/main/java/com/theveloper/pixelplay/data/ai/AiSongSearchService.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/AiAssistantViewModel.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/screens/AiAssistantScreen.kt`

修改：
- `presentation/navigation/Screen.kt`、`presentation/navigation/AppNavigation.kt`
- `MainActivity.kt`（发现弹窗跳转改到 `Screen.AiAssistant`）
- `deps`/注入：确认 Hilt 模块能提供 `BuiltInSourceSearchApi`/`LxSearchApi`（`AiSearchViewModel` 已注入，故可复用）

## 验证
1. 构建：`./gradlew assembleDebug`（或对应 Debug 任务）通过。
2. 手动验证（需已在设置配置 AI API Key）：
   - 主界面点底部「发现」→ 弹窗内「AI 助手」→ 进入全屏聊天页。
   - 输入“搜周杰伦的老歌” → 应触发 `search_songs`，OP 日志 + 内联歌单出现，可点击播放。
   - 输入“给我生成一份跑步听的歌单” → 触发 `generate_playlist`，日志显示已生成歌单，可保存。
   - 输入“打开音质设置” → 触发 `open_settings` → 自动跳转到 settings playback 分类。
   - 输入寒暄（如“你好”） → 不调用工具，仅流式回复。
3. 视觉：气泡圆角/分层/surface 色与 App 其余页一致；底部输入框随键盘避开；思考折叠块可展开收起。