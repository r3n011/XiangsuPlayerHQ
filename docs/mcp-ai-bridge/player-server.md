# 播放器侧开发文档：把像素播放器做成 MCP Server

> 目标读者：PixelPlayer 客户端开发者
> 前置阅读：[总体方案](./README.md)
> 基线代码：`versionName 1.7.4` / `minSdk 23` / Ktor 3.5 / kotlinx.serialization / Hilt

---

## 1. 范围

本期在播放器内新增一个 **MCP Server**，对外提供工具、资源与提示模板；**播放器不直连任何模型**。

**它与 Web Remote 完全隔离**：独立的前台服务 `McpServerService`、独立端口（默认 `8787`）、独立认证（Token/Scope）、独立开关、独立通知与网络锁。**不修改 `WebRemoteServerService` 的任何代码**，也不在其路由上挂载 `/mcp`。两者可以同时运行，也可以只开其一。

本期同时实现 **模型托管模式**：用户可一键把播放器内部全部 AI 请求转给已配对客户端处理，覆盖内部 provider / API Key / model 设置。实现方式是 MCP 标准的 `sampling/createMessage` + 客户端已建立的 SSE 反向通道（§6），播放器仍然不发起任何出站连接。

交付物：

| 模块 | 说明 |
|------|------|
| `McpServerService` | 独立 `LifecycleService` + 独立 `embeddedServer(CIO)`，端口 `8787` 起 |
| `POST /mcp` | JSON-RPC 2.0 分发（initialize / tools / resources / prompts） |
| `GET /mcp` | SSE 反向通道（承载通知与 `sampling/createMessage`） |
| 配对 API | `/api/mcp/info`、`/pair/request`、`/pair/status`（都在 MCP 端口上） |
| 工具执行层 | 6 组 ~30 个工具，映射到现有 Kotlin 接口 |
| 模型托管 | `McpInferenceRouter` + `SamplingBridge`，接管 `AiOrchestrator` / `AiHandler` |
| 设置页 | 开关、客户端发现与一键拉起、已连接客户端、模型托管、审计、吊销 |
| 深链 | `xiangsuplayer://mcp/connect` 处理 |
| 安全 | Token/Scope/限流/审计/出站校验/托管确认 |

不在本期：mDNS 发现、元数据写回、TLS、远程（非局域网）访问。

---

## 2. 新增文件

```
app/src/main/java/com/theveloper/pixelplay/data/mcp/
  McpJsonRpc.kt              JSON-RPC 模型与错误码
  McpServer.kt               Ktor 路由安装 + 请求分发
  McpSessionManager.kt       Token / Scope / 会话 / 限流
  McpPairingManager.kt       配对码、审批、状态机
  McpPairingIntentBus.kt     深链 → UI 的一次性事件
  McpToolRegistry.kt         工具注册与分发
  McpTool.kt                 工具接口、上下文、结果模型
  McpEventBridge.kt          now-playing / queue 变更通知
  McpAuditLog.kt             本地环形审计
  McpPairingLauncher.kt      拉起客户端 / 回调客户端（深链）
  McpClientDiscovery.kt      查询已安装的兼容客户端（PackageManager + queries）
  McpPairingNotifier.kt      跨设备配对的通知/前台弹层
  McpFeature.kt              ASSISTANT / MIX / … 功能枚举
  McpInferenceRouter.kt      托管模式判断与路由（AI → 客户端）
  SamplingBridge.kt          sampling/createMessage 请求/响应/超时
  McpSseChannel.kt           每会话 SSE 出站队列 + 心跳
  tools/PlayerTools.kt
  tools/QueueTools.kt
  tools/LibraryTools.kt
  tools/OnlineTools.kt
  tools/LyricsTools.kt
  tools/AiDataTools.kt
  tools/AppTools.kt
  PlaybackCommandBus.kt      App 级播放命令总线（见 §4.3）

app/src/main/java/com/theveloper/pixelplay/data/service/mcp/
  McpServerService.kt        独立前台服务（Ktor CIO，默认 :8787）
  McpServerState.kt          进程级状态（running / port / address / lastError）
  McpNetLocks.kt             自己的 WakeLock + Wi-Fi 锁（不碰 Web Remote 的）
  McpPortResolver.kt         8787–8799 端口探测（纯函数，可复用）

app/src/main/java/com/theveloper/pixelplay/presentation/mcp/
  McpSettingsSection.kt      设置页卡片（Compose）
  McpPairingSheet.kt         客户端选择 / 确认页 / 配对状态
  McpClientList.kt           已连接客户端与吊销
  McpAuditScreen.kt          审计日志（可并入设置分类）

app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/
  McpSettingsViewModel.kt
```

修改：

| 文件 | 改动 |
|------|------|
| `presentation/viewmodel/PlayerViewModel.kt` | 初始化/清理 `PlaybackCommandBus` 的 UI handler |
| `presentation/model/SettingsCategory.kt` | 新增 `ai_bridge`（"AI 外接"）分类 |
| `presentation/screens/SettingsCategoryScreen.kt` | 渲染 `McpSettingsSection` |
| `presentation/navigation/AppNavigation.kt` | 可选的审计页路由 |
| `MainActivity.kt` | `handleIntent()` 处理 `xiangsuplayer://mcp/...` |
| `AndroidManifest.xml` | 新增 `McpServerService` 声明；新增 `xiangsuplayer` + `mcp` host 的 intent-filter |
| `data/preferences/AiPreferencesRepository.kt` | 新增 MCP 相关 key（或独立 `McpPreferencesRepository`） |
| `data/ai/AiOrchestrator.kt` | `generateContent` / `generateContentStream` / `generateContentStreamWithReasoning` / `generateWithPrimaryProvider` 四个入口加托管路由判断 |
| `data/ai/AiHandler.kt` | `generateContent` 入口加托管路由判断 |
| `data/ai/AiCompanionManager.kt` | 调用时传 `AiFeature.DJ`；托管时跳过本地 TTS 预生成 |
| `data/worker/AiWorker.kt` | 调用时传 `AiFeature.AUTO` |
| `presentation/viewmodel/AiStateHolder.kt` | `explainLyrics` 传 `AiFeature.LYRICS`；失败时区分「客户端拒绝/离线」 |
| `presentation/screens/SettingsCategoryScreen.kt` | AI 设置区显示「已被外部客户端接管」banner |
| `app/src/main/res/values*/strings*.xml` | 新增文案（10 种语言） |

**不要修改**：

| 文件 | 原因 |
|------|------|
| `data/service/http/WebRemoteServerService.kt` | 保持零侵入；MCP 不挂在它的路由上 |
| `data/service/http/*` 的静态资源 / PIN / `/api/*` | 与 MCP 无交集 |
| Web Remote 的偏好 key / 端口 / 通知通道 | 避免用户关闭 MCP 时误停遥控 |

依赖：**无需新增**。Ktor server / kotlinx.serialization / zxing core 均已在依赖中（`libs.zxing.core`，`zxing 3.5.3`）。`FOREGROUND_SERVICE_DATA_SYNC` 权限已在 manifest 中声明。

### 2.1 Manifest 与深链

在 `<application>` 内、`WebRemoteServerService` 声明旁边新增（**不要改动原有 service**）：

```xml
<service
    android:name=".data.service.mcp.McpServerService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

在 `MainActivity` 的 intent-filter 中追加（与现有 `xiangsuplayer://share` 并列）：

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="xiangsuplayer" android:host="mcp" />
</intent-filter>
```

为了能「列出已安装的 AI 客户端」并拉起，还需要在 `<manifest>` 顶层声明可见性：

```xml
<queries>
    <intent>
        <action android:name="com.pixelplayer.mcp.CLIENT" />
    </intent>
</queries>
```

> Android 11+ 的包可见性限制：不声明 `<queries>` 时 `queryIntentActivities` 只能看到自己，导致「找不到客户端」。不要用 `QUERY_ALL_PACKAGES` 绕过，应用商店会拒。

`MainActivity.handleIntent()`（`:807`）里增加分支：

```kotlin
if (uri?.scheme == "xiangsuplayer" && uri.host == "mcp") {
    when (uri.pathSegments.firstOrNull()) {
        "connect" -> uri.getQueryParameter("p")?.let {
            McpPairingIntentBus.post(McpPairingIntent.Invite(it))   // 客户端 → 播放器
        }
    }
    intent.data = null
}
```

播放器 → 客户端不需要给自己加 scheme，而是用 `McpPairingLauncher` 启动对方的 scheme：

```kotlin
fun launchClient(pkg: String, scheme: String, payload: String): Boolean {
    val uri = Uri.parse("$scheme://mcp/pair?p=$payload")
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .setPackage(pkg)                 // 必须显式指定包，防止被浏览器/其他 App 截获
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}
```

> 两个服务同在默认进程，但各自持有独立的 Ktor `EmbeddedServer`、独立端口、独立 `Application` 模块。不要用 `stopService` 互相停止。

---

## 3. 协议层

### 3.1 JSON-RPC 模型

```kotlin
package com.theveloper.pixelplay.data.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class RpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String = "",
    val params: JsonObject? = null,
    // 客户端对 sampling/createMessage 的响应：没有 method，只有 result 或 error
    val result: JsonObject? = null,
    val error: RpcError? = null,
)

@Serializable
data class RpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class RpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: RpcError? = null,
)
```

错误码常量：

```kotlin
object McpErrors {
    const val PARSE = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL = -32603
    const val UNAUTHORIZED = -32001
    const val FORBIDDEN_SCOPE = -32002
    const val RATE_LIMITED = -32003
    const val NOT_READY = -32004
    const val DEVICE_BUSY = -32005
}
```

### 3.2 路由安装

新建独立服务 `McpServerService`，**不碰 `WebRemoteServerService`**：

```kotlin
package com.theveloper.pixelplay.data.service.mcp

@AndroidEntryPoint
class McpServerService : LifecycleService() {

    @Inject lateinit var mcpServer: McpServer
    @Inject lateinit var pairingManager: McpPairingManager
    @Inject lateinit var mcpPrefs: McpPreferencesRepository
    @Inject lateinit var netLocks: McpNetLocks

    private var engine: EmbeddedServer<*, *>? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { stopSelfSafely(); return START_NOT_STICKY }
        }
        val preferred = intent?.getIntExtra(EXTRA_PORT, McpPortResolver.DEFAULT_PORT)
            ?: McpPortResolver.DEFAULT_PORT
        lifecycleScope.launch { startServer(preferred) }
        return START_STICKY
    }

    private suspend fun startServer(preferredPort: Int) {
        if (McpServerState.isRunning) return
        val port = McpPortResolver.resolve(preferredPort) ?: run {
            McpServerState.lastError = "no free port in ${McpPortResolver.RANGE}"
            stopSelfSafely()
            return
        }
        McpServerState.update(running = true, port = port, address = "${localIp()}:$port")
        pairingManager.attachEndpoint("http://${localIp()}:$port")
        netLocks.acquire()
        startForeground(NOTIFICATION_ID, buildNotification(port))

        val module: suspend Application.() -> Unit = {
            install(DefaultHeaders)
            install(ContentNegotiation) { json(this@McpServerService.json) }
            install(CORS) {
                // 只开放 MCP 需要的方法/头，不服务静态资源
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Delete)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                allowHeader("Mcp-Session-Id")
                allowHeader("MCP-Protocol-Version")
                anyHost()
            }
            routing {
                mcpRoutes(mcpServer, pairingManager, this@McpServerService.json)
            }
        }

        engine = embeddedServer(CIO, configure = {
            connector { this.port = port; host = "0.0.0.0" }
        }, rootConfig = serverConfig { module(module) }).start(wait = false)
    }

    private fun stopSelfSafely() {
        lifecycleScope.launch {
            engine?.stop(1_000, 3_000)
            engine = null
            netLocks.release()
            McpServerState.update(running = false)
            withContext(Dispatchers.Main) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
    }

    override fun onDestroy() {
        netLocks.release()          // 同步释放，避免协程来不及执行
        engine?.stop(0, 0)
        McpServerState.update(running = false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.r3n011.pixelplay.mcp.START"
        const val ACTION_STOP = "com.r3n011.pixelplay.mcp.STOP"
        const val EXTRA_PORT = "port"
        private const val NOTIFICATION_ID = 0x4D43   // 'MC'
    }
}
```

> `McpServerState` 必须是 **MCP 自己的** 进程级状态对象；不要再读写 `WebRemoteServerService` 的静态字段（`serverAddress` / `currentPin` / `isServerRunning`）。

`mcpRoutes` 定义：

```kotlin
fun Route.mcpRoutes(
    mcpServer: McpServer,
    pairing: McpPairingManager,
    json: Json,
) {
    post("/mcp") { mcpServer.handle(call, json) }

    // SSE 反向通道：托管模式的 sampling 请求、进度通知、资源更新都从这里下发
    get("/mcp") { mcpServer.openEventStream(call) }
    delete("/mcp") {
        mcpServer.closeSession(call)
        call.respond(HttpStatusCode.NoContent)
    }

    // ---- 配对（无需 Bearer，仅局域网） ----
    get("/api/mcp/info") { pairing.info(call) }

    // A / B 方向主路径：用 code 换 token
    post("/api/mcp/pair/redeem") { pairing.redeem(call, json) }

    // C 方向（跨设备）：客户端发起，播放器通知确认
    post("/api/mcp/pair/request") { pairing.request(call, json) }
    get("/api/mcp/pair/status") { pairing.status(call) }
    post("/api/mcp/pair/cancel") { pairing.cancel(call) }
}
```

> 配对接口也必须校验来源地址：只允许局域网/本机（见 §9.2）。`/api/mcp/info` 不返回任何密钥，只返回设备名、版本、协议版本、是否可配对。

### 3.3 请求分发

```kotlin
@Singleton
class McpServer @Inject constructor(
    private val sessions: McpSessionManager,
    private val registry: McpToolRegistry,
    private val events: McpEventBridge,
    private val audit: McpAuditLog,
    private val sampling: SamplingBridge,
) {
    suspend fun ApplicationCall.handle(json: Json) {
        // 1) 认证
        val token = request.headers[HttpHeaders.Authorization]
            ?.removePrefix("Bearer ")?.trim()
        val session = token?.let { sessions.authenticate(it) }
            ?: return respondRpc(json, id = null, error = McpErrors.UNAUTHORIZED, "invalid token")

        // 2) body 大小与内容类型
        val text = request.receiveText()
        if (text.length > 256 * 1024) {
            return respondStatus(HttpStatusCode.PayloadTooLarge)
        }

        // 3) 解析
        val req = runCatching { json.decodeFromString<RpcRequest>(text) }.getOrElse {
            return respondRpc(json, id = null, error = McpErrors.PARSE, "invalid json")
        }

        // 4) 通知：202
        if (req.id == null) {
            runCatching { handleNotification(session, req) }
            return respondStatus(HttpStatusCode.Accepted)
        }

        // 4.5) 客户端对 sampling/createMessage 的响应（无 method，只有 id + result/error）
        if (req.method.isBlank()) {
            sampling.onClientResponse(session, req)
            return respondStatus(HttpStatusCode.Accepted)
        }

        // 5) 分发
        val result = runCatching { dispatch(session, req) }
            .getOrElse { t ->
                audit.error(session, req.method, t)
                RpcOutcome.Error(McpErrors.INTERNAL, t.message ?: "internal error")
            }

        respondRpc(json, req.id, result)
    }
}
```

`dispatch` 的方法表：

| method | 处理 |
|--------|------|
| `initialize` | 返回协议版本、能力、serverInfo、instructions；创建 session；响应头写 `Mcp-Session-Id` |
| `ping` | `{}` |
| `tools/list` | `registry.list(cursor, session.scopes)` |
| `tools/call` | 取 `params.name`、`params.arguments`，校验 scope，执行 |
| `resources/list` | 静态列表 + 动态歌曲资源（可选） |
| `resources/read` | 按 URI 读取 |
| `resources/subscribe` | 仅允许 `now-playing`、`queue` |
| `prompts/list` / `prompts/get` | 内置提示模板 |
| 无 `method` 的响应（`id` + `result`/`error`） | 交给 `SamplingBridge.onClientResponse`，HTTP 返回 `202` |
| 其他 | `-32601` |

> `initialize` 允许在无 Bearer 时调用吗？**不允许**。配对完成后客户端先拿到 token，再 initialize。这样 `initialize` 本身也能审计到具体客户端。

### 3.4 响应格式

- 有 `id`：`200` + JSON 响应（`Content-Type: application/json`）。
- 通知：`202` + 空 body。
- `initialize` 额外写响应头：

```kotlin
call.response.header("Mcp-Session-Id", session.id)
call.response.header("MCP-Protocol-Version", PROTOCOL_VERSION)
```

### 3.5 SSE 反向通道

不引入 `ktor-server-sse` 依赖，直接用 `respondOutputStream` 写 `text/event-stream`（CIO 支持）：

```kotlin
suspend fun ApplicationCall.openEventStream() {
    val session = authenticate(this) ?: return respond(HttpStatusCode.Unauthorized)
    response.header(HttpHeaders.ContentType, "text/event-stream; charset=utf-8")
    response.header(HttpHeaders.CacheControl, "no-cache")
    response.header("X-Accel-Buffering", "no")
    respondOutputStream(ContentType.Text.EventStream) {
        session.attachSse(this)              // 标记在线 + 注册出站 Channel
        try {
            // 心跳：15s 一次，避免 NAT/路由器掐断
            while (session.isOpen) {
                val msg = withTimeoutOrNull(15_000) { session.outbound.receive() }
                if (msg == null) {
                    write(": ping\n\n".toByteArray()); flush()
                } else {
                    write("event: message\ndata: $msg\n\n".toByteArray()); flush()
                }
            }
        } finally {
            session.detachSse()
            sampling.failAllPending(session, "sse closed")  // 断线不悬挂
        }
    }
}
```

`McpSession` 需要新增：

```kotlin
@Volatile var sseConnected: Boolean = false
val outbound = Channel<String>(capacity = 64)
val caps = ClientCapabilities(sampling = false, streaming = false)
val pendingSampling = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
```

> SSE 是**单连接**：同一 Token 只保留最新一条流；旧流被新流顶掉时，向旧流发 `event: close` 并关闭。托管模式要求 SSE 在线，否则 `SamplingBridge` 直接报 `-32004`。

---

## 4. 工具执行层

### 4.1 工具接口

```kotlin
interface McpTool {
    val name: String
    val title: String
    val description: String
    val inputSchema: JsonObject
    val outputSchema: JsonObject?
    val scope: String
    val annotations: ToolAnnotations

    suspend fun invoke(ctx: ToolContext, args: JsonObject): ToolResult
}

data class ToolContext(
    val session: McpSession,
    val progress: (Double, String?) -> Unit,
)

data class ToolResult(
    val text: String,
    val structured: JsonObject? = null,
    val isError: Boolean = false,
    val content: List<ContentBlock> = emptyList(),
) {
    companion object {
        fun ok(text: String, structured: JsonObject? = null) = ToolResult(text, structured)
        fun fail(text: String) = ToolResult(text, isError = true)
    }
}
```

### 4.2 注册表

```kotlin
@Singleton
class McpToolRegistry @Inject constructor(
    playerTools: PlayerTools,
    queueTools: QueueTools,
    libraryTools: LibraryTools,
    onlineTools: OnlineTools,
    lyricsTools: LyricsTools,
    aiDataTools: AiDataTools,
    appTools: AppTools,
) {
    private val tools: Map<String, McpTool> = buildList {
        addAll(playerTools.all())
        addAll(queueTools.all())
        addAll(libraryTools.all())
        addAll(onlineTools.all())
        addAll(lyricsTools.all())
        addAll(aiDataTools.all())
        addAll(appTools.all())
    }.associateBy { it.name }

    fun list(cursor: String?, scopes: Set<String>): ListResult { /* 分页 + 按 scope 过滤 */ }

    suspend fun call(name: String, scopes: Set<String>, ctx: ToolContext, args: JsonObject): ToolResult {
        val tool = tools[name] ?: return ToolResult.fail("unknown tool: $name")
        if (tool.scope !in scopes) return ToolResult.fail("scope denied: ${tool.scope}")
        return tool.invoke(ctx, args)
    }
}
```

> `tools/list` 只返回当前 Token 有权限的工具，客户端界面不会出现「点了才报 403」的体验。

### 4.3 播放命令总线（关键改造）

问题：`PlaybackDispatchStateHolder` 是 `@ViewModelScoped`，Service 注入不到；如果 MCP 直接 `masterPlayer.setMediaItems()`，App 的队列 UI、统计、过渡动画都不会更新。

方案：新增 App 级单例 `PlaybackCommandBus`，由 `PlayerViewModel` 注册 UI handler；MCP 工具只调用总线。

```kotlin
@Singleton
class PlaybackCommandBus @Inject constructor(
    private val repository: MusicRepository,
    private val engine: DualPlayerEngine,
    private val stateHolder: PlaybackStateHolder,
    @ApplicationContext private val context: Context,
) {
    interface UiHandler {
        suspend fun playLocal(songIds: List<String>, startIndex: Int, queueName: String, shuffle: Boolean): Result<Unit>
        suspend fun addToQueueLocal(songIds: List<String>): Result<Unit>
        suspend fun clearQueue(): Result<Unit>
    }

    @Volatile private var ui: UiHandler? = null

    fun bind(handler: UiHandler) { ui = handler }
    fun unbind(handler: UiHandler) { if (ui === handler) ui = null }

    suspend fun playLocal(songIds: List<String>, startIndex: Int, queueName: String, shuffle: Boolean) =
        ui?.playLocal(songIds, startIndex, queueName, shuffle) ?: headlessPlay(songIds, startIndex)

    private suspend fun headlessPlay(songIds: List<String>, startIndex: Int): Result<Unit> =
        withContext(Dispatchers.Main) {
            runCatching {
                val songs = repository.getSongsByIds(songIds).first()
                if (songs.isEmpty()) error("no playable songs")
                val items = songs.map { MediaItemBuilder.build(it) }
                engine.masterPlayer.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
                engine.masterPlayer.prepare()
                engine.masterPlayer.play()
            }
        }
}
```

`PlayerViewModel` 初始化时：

```kotlin
private val playbackCommandHandler = object : PlaybackCommandBus.UiHandler {
    override suspend fun playLocal(...) = withContext(Dispatchers.Main) {
        // 复用现有 playSongs(...) 的完整链路
        playSongs(songs, songs[startIndex], queueName, playlistId = null)
        Result.success(Unit)
    }
    // ...
}

init {
    commandBus.bind(playbackCommandHandler)
}

override fun onCleared() {
    commandBus.unbind(playbackCommandHandler)
    super.onCleared()
}
```

> 无 UI（服务重启、App 被系统回收）时走 `headlessPlay`，保证 MCP 仍能播放；回到 App 后 `PlayerViewModel` 会重新绑定，队列状态由 MediaSession 同步。

### 4.4 工具实现示例

#### `player_get_state`

```kotlin
@Singleton
class PlayerTools @Inject constructor(
    private val stateHolder: PlaybackStateHolder,
    private val engine: DualPlayerEngine,
) {
    fun all(): List<McpTool> = listOf(getState, play, pause, toggle, next, previous, seek, setVolume, setRepeat, setShuffle, setFavorite)

    private val getState = object : McpTool {
        override val name = "player_get_state"
        override val title = "读取播放状态"
        override val description = "返回当前歌曲、播放进度、时长、播放/暂停、循环模式、随机模式、音量与队列位置。"
        override val scope = "read.playback"
        override val inputSchema = emptyObjectSchema()
        override val outputSchema = null
        override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

        override suspend fun invoke(ctx: ToolContext, args: JsonObject): ToolResult {
            val s = stateHolder.stablePlayerState.value
            val pos = stateHolder.currentPosition.value
            val song = s.currentSong
            val structured = buildJsonObject {
                song?.let {
                    putJsonObject("song") {
                        put("id", it.id)
                        put("title", it.title)
                        put("artist", it.displayArtist)
                        put("album", it.album)
                        put("durationMs", it.duration)
                        put("genre", it.genre)
                        put("year", it.year)
                        put("favorite", it.isFavorite)
                        put("source", if (it.contentUriString.startsWith("http")) "cloud" else "local")
                    }
                }
                put("positionMs", pos)
                put("isPlaying", s.isPlaying)
                put("repeatMode", when (s.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "one"
                    Player.REPEAT_MODE_ALL -> "all"
                    else -> "off"
                })
                put("shuffle", s.isShuffleEnabled)
                put("volume", engine.currentVolume)   // 需在 DualPlayerEngine 暴露公开 getter，见下
                putJsonObject("queue") {
                    put("index", s.currentMediaItemIndex)
                    put("size", engine.masterPlayer.mediaItemCount)
                }
            }
            val text = if (song == null) "当前没有正在播放的歌曲。"
            else "当前播放：《${song.title}》- ${song.displayArtist}，位置 ${formatMs(pos)} / ${formatMs(song.duration)}"
            return ToolResult.ok(text, structured)
        }
    }
}
```

`DualPlayerEngine` 目前只有私有的 `companionBaseVolume`（`:1087`），需要在引擎里补一个只读 getter：

```kotlin
val currentVolume: Float
    get() = companionBaseVolume
```

> 注意：`Song.path` **绝不进入** structuredContent。

#### `queue_play`

```kotlin
override suspend fun invoke(ctx: ToolContext, args: JsonObject): ToolResult {
    val kind = args["kind"]?.jsonPrimitive?.content ?: return ToolResult.fail("kind is required")
    val startIndex = args["startIndex"]?.jsonPrimitive?.intOrNull ?: 0
    val queueName = args["queueName"]?.jsonPrimitive?.contentOrNull ?: "AI 播放"
    val shuffle = args["shuffle"]?.jsonPrimitive?.booleanOrNull ?: false

    return when (kind) {
        "local" -> {
            val ids = args["songIds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            if (ids.isEmpty()) return ToolResult.fail("songIds is required when kind=local")
            playbackBus.playLocal(ids, startIndex, queueName, shuffle).fold(
                onSuccess = { ToolResult.ok("已开始播放 ${ids.size} 首歌曲。", buildJsonObject { put("accepted", true) }) },
                onFailure = { ToolResult.fail("播放失败：${it.message}") }
            )
        }
        "online" -> {
            val songs = args["songs"]?.jsonArray?.map { parseOnlineSong(it.jsonObject) }.orEmpty()
            if (songs.isEmpty()) return ToolResult.fail("songs is required when kind=online")
            onlinePlayer.play(songs, startIndex, queueName).fold(
                onSuccess = { ToolResult.ok("已开始在线播放。", buildJsonObject { put("accepted", true) }) },
                onFailure = { ToolResult.fail("在线播放失败：${it.message}") }
            )
        }
        else -> ToolResult.fail("kind must be local or online")
    }
}
```

#### `ai_get_candidates`

```kotlin
val candidates = dailyMixManager.getTopCandidatesForAi(
    allSongs = musicRepository.getAllSongsOnce(),
    favoriteSongIds = emptySet(),
    limit = limit.coerceIn(1, 100),
)
val items = candidates.map { song ->
    buildJsonObject {
        put("id", song.id)
        put("title", song.title)
        put("artist", song.displayArtist)
        put("album", song.album)
        put("genre", song.genre)
        put("year", song.year)
        put("durationMs", song.duration)
        put("favorite", song.isFavorite)
        put("score", dailyMixManager.getScore(song.id))
    }
}
```

可选：`mood` 只做轻量加权（按 genre 关键词命中 +score），不要为它调用 LLM。

#### `online_search`

```kotlin
val sources = args["sources"]?.jsonArray?.map { it.jsonPrimitive.content }
    ?: listOf("wy", "kw", "tx")
val page = args["page"]?.jsonPrimitive?.intOrNull ?: 1
val pageSize = (args["pageSize"]?.jsonPrimitive?.intOrNull ?: 15).coerceIn(1, 30)
val query = args["query"]!!.jsonPrimitive.content

val results = withContext(Dispatchers.IO) {
    sources.map { src ->
        async {
            runCatching {
                builtInSourceSearchApi.search(src, query, page, pageSize)
            }.getOrElse { LxSearchResult() }
        }
    }.awaitAll()
}

val items = results.flatMap { r -> r.list.map { it.toPublicJson() } }.distinctBy { "${it.source}:${it.songId}" }
```

单源超时 3s，总超时 6s；返回 `total`、`isEnd`；**不返回播放 URL**。

### 4.5 长任务与进度

- `online_search`：多源并发时按完成数发 `notifications/progress`（有 SSE 时）；v1 在 `_meta.progressToken` 存在时附加 `progress` 字段即可。
- `library_create_playlist`：>100 首时分批写入，进度回调。
- 客户端 `notifications/cancelled` 到达时，用 `requestId` 取消协程（`McpServer` 维护 `inFlight: Map<JsonElement, Job>`）。

---

## 5. 配对与会话

### 5.1 数据模型

```kotlin
@Serializable
data class McpClientRecord(
    val id: String,              // tok_xxx
    val clientId: String,        // 客户端自报 id
    val name: String,
    val platform: String?,
    val tokenHash: String,       // SHA-256(token + salt)
    val salt: String,
    val scopes: Set<String>,
    val createdAt: Long,
    val lastUsedAt: Long,
    val revoked: Boolean = false,
)

enum class PairSource { PLAYER_INITIATED, CLIENT_INITIATED }
enum class PairStatus { ISSUED, PENDING_APPROVAL, APPROVED, REDEEMED, DENIED, EXPIRED }

@Serializable
data class PendingPairing(
    val code: String,
    val source: PairSource,
    val clientId: String? = null,
    val clientName: String? = null,
    val clientScheme: String? = null,   // 播放器回调客户端用
    val requestedScopes: Set<String>,
    val grantedScopes: Set<String> = emptySet(),
    val createdAt: Long,
    val expiresAt: Long,
    val status: PairStatus,
)
```

> 注意：**配对会话里不存 token**。Token 只在 `redeem` 成功的那一刻生成并返回一次；这样 DataStore 里永远只有哈希。

持久化：DataStore（key `mcp_clients_json`、`mcp_audit_json`）。Token 只存哈希，泄露 DataStore 也无法反推。

### 5.2 生成配对码

```kotlin
private fun newCode(): String {
    val bytes = ByteArray(4).also { SecureRandom().nextBytes(it) }
    val n = (ByteArrayToInt(bytes) and 0x7fffffff) % 1_000_000
    return n.toString().padStart(6, '0')
}
```

- 同时只允许 1 个未过期配对会话（新会话顶掉旧的）。
- 5 分钟过期；`approved` 后 2 分钟内必须 `redeem`，否则作废。

### 5.3 方向 A：播放器拉起客户端

**客户端发现**（`McpClientDiscovery`）：

```kotlin
data class CompatibleClient(val pkg: String, val label: String, val scheme: String)

fun listCompatible(): List<CompatibleClient> {
    val intent = Intent(ACTION_MCP_CLIENT).addCategory(Intent.CATEGORY_DEFAULT)
    return pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
        val pkg = ri.activityInfo.packageName
        val meta = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA).metaData
        val scheme = meta?.getString(META_PAIR_SCHEME) ?: return@mapNotNull null
        CompatibleClient(pkg, ri.loadLabel(pm).toString(), scheme)
    }.sortedBy { it.label }
}

const val ACTION_MCP_CLIENT = "com.pixelplayer.mcp.CLIENT"
const val META_PAIR_SCHEME = "com.pixelplayer.mcp.PAIR_SCHEME"
```

**发起配对**：

```kotlin
suspend fun startPairingWith(client: CompatibleClient): Boolean {
    ensureServiceRunning()                       // 必须先把 8787 起起来
    val code = newCode()                         // 6 位，5 分钟
    val payload = buildPairPayload(
        code = code,
        endpoint = McpServerState.baseUrl,       // http://ip:8787
        device = Build.MODEL,
        deviceId = deviceId(),
        client = PairClientInfo(id = client.pkg, name = client.label, scheme = client.scheme),
        scopes = DEFAULT_SCOPES,                 // read.* + control.playback + write.playlist
        exp = nowSeconds() + 300,
    )
    pending[code] = PendingPairing(
        code = code,
        source = PairSource.PLAYER_INITIATED,
        clientId = client.pkg,
        clientName = client.label,
        clientScheme = client.scheme,
        requestedScopes = DEFAULT_SCOPES,
        createdAt = now(),
        expiresAt = now() + 300_000,
        status = PairStatus.ISSUED,
    )
    return launcher.launchClient(client.pkg, client.scheme, payload)   // setPackage + ACTION_VIEW
}
```

**兑换**（A / B 共用）：

```kotlin
suspend fun redeem(call: ApplicationCall, json: Json) {
    guardLan(call) ?: return
    rateLimit.pair(call) ?: return

    val body = json.decodeFromString<RedeemRequest>(call.receiveText())
    val session = pending[body.code] ?: return call.respond(HttpStatusCode.NotFound, PairError("unknown_code"))
    if (session.expiresAt < now()) {
        session.status = PairStatus.EXPIRED
        return call.respond(HttpStatusCode.Gone, PairError("expired"))
    }
    if (session.status == PairStatus.REDEEMED) {
        return call.respond(HttpStatusCode.Conflict, PairError("already_redeemed"))
    }
    if (session.status == PairStatus.PENDING_APPROVAL) {
        return call.respond(HttpStatusCode.Accepted, PairStatusResponse("pending"))
    }
    if (session.status == PairStatus.DENIED) {
        return call.respond(HttpStatusCode.Forbidden, PairError("denied"))
    }

    // A 方向：ISSUED 直接兑换；B 方向：APPROVED 才能兑换
    if (session.source == PairSource.CLIENT_INITIATED && session.status != PairStatus.APPROVED) {
        return call.respond(HttpStatusCode.Accepted, PairStatusResponse("pending"))
    }

    val scopes = session.grantedScopes.ifEmpty { session.requestedScopes }
    val issued = sessions.issueToken(
        clientId = body.client.id,
        name = body.client.name,
        platform = body.client.platform,
        scopes = scopes,
    )
    session.status = PairStatus.REDEEMED
    call.respond(RedeemResponse(
        token = issued.plaintext,
        tokenId = issued.id,
        scopes = scopes,
        server = ServerInfo(name = "pixelplayer", version = BuildConfig.VERSION_NAME),
    ))
    audit.pair(session, body.client)
}
```

> `issueToken` 里生成 32 字节随机 Token，只把 `SHA-256(token + salt)` 写 DataStore，明文只在这次响应里出现。

### 5.4 方向 B：客户端拉起播放器

`MainActivity.handleIntent()` 收到 `xiangsuplayer://mcp/connect?p=...` 后：

```kotlin
suspend fun onInvite(payload: String) {
    val p = parsePairPayload(payload) ?: return
    if (p.exp * 1000 < now()) return
    intentBus.requestApproval(p)     // 弹确认页
}

suspend fun approveInvite(p: PairPayload, granted: Set<String>) {
    val session = PendingPairing(
        code = p.code,
        source = PairSource.CLIENT_INITIATED,
        clientId = p.client.id,
        clientName = p.client.name,
        clientScheme = p.client.scheme,
        requestedScopes = p.scopes,
        grantedScopes = granted,
        createdAt = now(),
        expiresAt = now() + 120_000,     // 批准后 2 分钟内必须兑换
        status = PairStatus.APPROVED,
    )
    pending[p.code] = session

    // 回调客户端；结果里只有 code + endpoint，没有 token
    val result = buildApprovedPayload(p.code, McpServerState.baseUrl, Build.MODEL)
    val ok = launcher.launchCallback(p.client.scheme, result)
    if (!ok) {
        // 回调失败：保留 APPROVED 2 分钟，客户端可轮询 status 后自行 redeem
        showFallbackHint(p.client.name)
    }
}
```

`McpPairingLauncher.launchCallback`：

```kotlin
fun launchCallback(scheme: String, payload: String): Boolean {
    val uri = Uri.parse("$scheme://mcp/paired?p=$payload")
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}
```

确认页（`McpPairingSheet`）：

```
「Cherry Studio」请求连接
本机：Pixel 8 · 192.168.1.23:8787
权限：
  ☑ 读取播放状态   ☑ 控制播放   ☑ 读取曲库   ☑ 创建歌单
  ☐ 修改设置       ☐ AI DJ 语音
[拒绝]                      [允许]
```

- 默认勾选：`read.*`、`control.playback`、`write.playlist`。
- 只接受 `type == "mcp.pair"`、`v == 1`、未过期、`client.scheme` 合法的 payload。
- 不允许从 payload 里读任意 URL 让播放器去请求；播放器只回调 `client.scheme`，不访问客户端地址。

### 5.5 跨设备：请求 + 通知确认

```kotlin
suspend fun request(call: ApplicationCall, json: Json) {
    guardLan(call) ?: return
    rateLimit.pair(call) ?: return
    val body = json.decodeFromString<PairRequest>(call.receiveText())
    if (body.code.length != 6) return call.respond(HttpStatusCode.BadRequest)

    pending[body.code] = PendingPairing(
        code = body.code,
        source = PairSource.CLIENT_INITIATED,
        clientId = body.client.id,
        clientName = body.client.name,
        requestedScopes = body.scopes.intersect(ALL_SCOPES),
        createdAt = now(),
        expiresAt = now() + 300_000,
        status = PairStatus.PENDING_APPROVAL,
    )
    notifier.showPairRequest(body.code, body.client.name, body.scopes)  // 通知带允许/拒绝
    call.respond(PairResponse(status = "pending"))
}

fun status(code: String): PairStatusResponse {
    val s = pending[code] ?: return PairStatusResponse("expired")
    return PairStatusResponse(
        status = s.status.name.lowercase(),
        endpoint = McpServerState.baseUrl,
        scopes = s.grantedScopes.ifEmpty { s.requestedScopes },
    )
}
```

`McpPairingNotifier` 用 `NotificationCompat` + `PendingIntent`（BroadcastReceiver 或 Service action）实现「允许 / 拒绝」，不依赖 App 在前台。

> 播放器**不向客户端地址发请求**；跨设备也只是客户端来请求、播放器在本机确认。

### 5.6 认证与 Scope 校验

```kotlin
fun authenticate(token: String): McpSession? {
    val hash = sha256(token + saltFor(token))  // salt 按 tokenId 查
    val record = store.findByHash(hash) ?: return null
    if (record.revoked) return null
    store.touch(record.id)
    return McpSession(id = sessionId(token), client = record)
}
```

- 比较使用 `MessageDigest.isEqual`（常量时间）。
- `tools/call` 前校验 `tool.scope in session.scopes`。
- 每次调用写审计：`clientId, tool, argsDigest, argsBytes, durationMs, ok/errCode`。参数摘要只保留工具名 + 前 200 字符的脱敏 JSON，避免歌词/搜索词全文落盘。

### 5.7 限流

简单令牌桶（内存）：

```kotlin
class RateLimiter(val perSecond: Int, val burst: Int) {
    private val buckets = ConcurrentHashMap<String, Bucket>()
    fun tryAcquire(key: String): Boolean { ... }
}
```

- 全局：20 req/s；单 Token：10 req/s；单工具：`online_search` 2 req/s、`queue_play` 2 req/s。
- 超限返回 `-32003`，附 `retryAfterMs`。

---

## 6. 模型托管模式（反向推理）

目标：用户开启后，播放器内部所有 AI 调用（`AiOrchestrator` / `AiHandler` 的入口）改由已配对客户端生成，**覆盖内部 provider / API Key / model**；播放器不直连模型、不发起到客户端的出站连接。

### 6.1 配置与开关

新增偏好 key（放在 `McpPreferencesRepository`）：

```kotlin
enum class ProxyMode { OFF, ALL, CUSTOM }
enum class ProxyFallback { NONE, INTERNAL }

data class ProxyPolicy(
    val mode: ProxyMode = ProxyMode.OFF,
    val features: Set<AiFeature> = AiFeature.entries.toSet(),
    val clientId: String? = null,
    val fallback: ProxyFallback = ProxyFallback.NONE,
    val maxTokens: Int = 4096,
)
```

- `mode=OFF`：完全走原来的内置 provider。
- `mode=ALL`：所有功能走客户端；内部设置只作为 `INTERNAL` 回退时使用。
- `mode=CUSTOM`：按 `features` 勾选。
- `clientId`：首选推理客户端；为空时选「最近一次建立 SSE 且声明 sampling」的客户端。

> 托管只接管**文本生成**。AI DJ 的语音合成仍走播放器内置 `MiMoTtsClient`（独立 Key，不受托管影响）；若用户希望语音也由客户端出，走 MCP 工具 `companion_say` 由客户端自己调 TTS，不走托管路径。

### 6.2 功能枚举

```kotlin
enum class AiFeature {
    ASSISTANT, MIX, SEARCH, PLAYLIST, PLAYLIST_EVAL,
    METADATA, DAILY_MIX, LYRICS, DJ, AUTO;

    companion object {
        fun from(type: AiSystemPromptType): AiFeature = when (type) {
            AiSystemPromptType.PLAYLIST -> PLAYLIST
            AiSystemPromptType.METADATA, AiSystemPromptType.TAGGING -> METADATA
            AiSystemPromptType.DAILY_MIX -> DAILY_MIX
            AiSystemPromptType.PLAYLIST_EVALUATION -> PLAYLIST_EVAL
            AiSystemPromptType.MOOD_ANALYSIS -> MIX
            AiSystemPromptType.GENERAL, AiSystemPromptType.PERSONA -> ASSISTANT
        }
    }
}
```

`GENERAL` 同时被助手、歌词解读、DJ 使用，**这三个调用点必须显式传 `feature`**（见 6.5）。

### 6.3 能力协商

`initialize` 时解析客户端能力并写入会话：

```kotlin
@Serializable
data class ClientCapabilities(
    val sampling: Boolean = false,
    val streaming: Boolean = false,
)

fun parseCaps(params: JsonObject): ClientCapabilities {
    val caps = params["capabilities"]?.jsonObject
    return ClientCapabilities(
        sampling = caps?.containsKey("sampling") == true,
        streaming = caps?.get("experimental")?.jsonObject
            ?.containsKey("pixelplayer/streaming") == true,
    )
}
```

同时持久化到 `McpClientRecord.supportsSampling / supportsStreaming`，用于：

- 设置页判断托管开关是否可用；
- 多客户端时筛选「可推理」的客户端；
- 客户端重连后能力变化时更新。

### 6.4 `SamplingBridge`

```kotlin
@Singleton
class SamplingBridge @Inject constructor(
    private val sessions: McpSessionManager,
    private val audit: McpAuditLog,
) {
    private val seq = AtomicLong(0)
    private val semaphore = Semaphore(2)          // 全局并发 2

    suspend fun complete(
        session: McpSession,
        feature: AiFeature,
        systemPrompt: String,
        prompt: String,
        temperature: Float,
        maxTokens: Int,
        stream: Boolean = false,
        onChunk: ((String, Boolean) -> Unit)? = null,
    ): InferenceResult = semaphore.withPermit {
        require(session.sseConnected) { "client offline" }
        val id = "pp-s-${seq.incrementAndGet().toString(16)}"
        val deferred = CompletableDeferred<JsonObject>()
        session.pendingSampling[id] = deferred
        if (onChunk != null) session.progressHandlers[id.removePrefix("pp-s-")] = onChunk

        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "sampling/createMessage")
            putJsonObject("params") {
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        putJsonObject("content") {
                            put("type", "text")
                            put("text", prompt)
                        }
                    }
                }
                put("systemPrompt", systemPrompt)
                put("maxTokens", maxTokens)
                put("temperature", temperature)
                put("includeContext", "none")
                putJsonObject("_meta") {
                    put("pixelplayer/feature", feature.name)
                    put("pixelplayer/stream", stream)
                    if (stream) put("progressToken", "prog-${id.removePrefix("pp-s-")}")
                }
            }
        }

        val started = System.currentTimeMillis()
        try {
            session.send(payload.toString())                 // 写 SSE 通道
            val result = withTimeout(timeoutFor(feature)) { deferred.await() }
            audit.sampling(session, feature, prompt.length, System.currentTimeMillis() - started, "ok")
            return InferenceResult.from(result)
        } catch (e: TimeoutCancellationException) {
            session.send(cancelNotification(id))
            audit.sampling(session, feature, prompt.length, System.currentTimeMillis() - started, "timeout")
            throw McpInferenceException("客户端响应超时", McpErrors.DEVICE_BUSY)
        } catch (e: Throwable) {
            audit.sampling(session, feature, prompt.length, System.currentTimeMillis() - started, "error")
            throw McpInferenceException(e.message ?: "采样失败", McpErrors.INTERNAL)
        } finally {
            session.pendingSampling.remove(id)
            session.progressHandlers.remove(id.removePrefix("pp-s-"))
        }
    }

    fun onClientResponse(session: McpSession, req: RpcRequest) {
        val id = req.id?.jsonPrimitive?.content ?: return
        val deferred = session.pendingSampling[id] ?: return
        when {
            req.error != null -> deferred.completeExceptionally(
                McpInferenceException(req.error.message, req.error.code)
            )
            req.result != null -> deferred.complete(req.result!!)
        }
    }

    fun failAllPending(session: McpSession, reason: String) {
        session.pendingSampling.values.forEach {
            it.completeExceptionally(McpInferenceException(reason, McpErrors.NOT_READY))
        }
        session.pendingSampling.clear()
    }

    private fun timeoutFor(feature: AiFeature) = when (feature) {
        AiFeature.DJ -> 30.seconds
        AiFeature.METADATA -> 60.seconds
        else -> 120.seconds
    }
}
```

`InferenceResult.from` 负责把 MCP 的 `content` 转成文本：

```kotlin
fun from(result: JsonObject): InferenceResult {
    val content = result["content"]
    val text = when {
        content is JsonObject && content["type"]?.jsonPrimitive?.content == "text" ->
            content["text"]!!.jsonPrimitive.content
        content is JsonArray -> content.joinToString("") { part ->
            val obj = part.jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "text") obj["text"]!!.jsonPrimitive.content else ""
        }
        else -> ""
    }
    return InferenceResult(
        text = text,
        model = result["model"]?.jsonPrimitive?.contentOrNull,
        stopReason = result["stopReason"]?.jsonPrimitive?.contentOrNull,
    )
}
```

### 6.5 `McpInferenceRouter`

```kotlin
@Singleton
class McpInferenceRouter @Inject constructor(
    private val prefs: McpPreferencesRepository,
    private val sessions: McpSessionManager,
    private val bridge: SamplingBridge,
) {
    suspend fun shouldProxy(feature: AiFeature): Boolean {
        val p = prefs.proxyPolicy.first()
        if (p.mode == ProxyMode.OFF) return false
        if (p.mode == ProxyMode.CUSTOM && feature !in p.features) return false
        return sessions.preferredInferenceSession(p.clientId) != null
    }

    suspend fun complete(
        feature: AiFeature,
        systemPrompt: String,
        prompt: String,
        temperature: Float,
    ): String {
        val p = prefs.proxyPolicy.first()
        val session = sessions.preferredInferenceSession(p.clientId)
            ?: throw McpInferenceException("AI 客户端已离线", McpErrors.NOT_READY)
        return try {
            bridge.complete(
                session = session,
                feature = feature,
                systemPrompt = systemPrompt,
                prompt = prompt,
                temperature = temperature,
                maxTokens = p.maxTokens,
            ).text
        } catch (e: McpInferenceException) {
            if (p.fallback == ProxyFallback.INTERNAL) {
                // 调用原来的内置 provider 路径（复用 AiOrchestrator 的 legacy 方法）
                return legacyComplete(feature, systemPrompt, prompt, temperature)
            }
            throw e
        }
    }

    fun stream(feature: AiFeature, systemPrompt: String, prompt: String, temperature: Float): Flow<AiStreamChunk> = ...
}
```

> `shouldProxy` 每次调用都重新读 policy，保证设置页关闭后立即生效；不要缓存到进程变量里。

### 6.6 改造 `AiOrchestrator` / `AiHandler`

在两个入口各加一个判断，**不动原有 provider 逻辑**：

```kotlin
// AiOrchestrator.kt
suspend fun generateContent(
    prompt: String,
    type: AiSystemPromptType = AiSystemPromptType.GENERAL,
    temperature: Float = 0.7f,
    context: String = "",
    feature: AiFeature = AiFeature.from(type),
): String {
    val resolvedTemperature = /* 原有温度逻辑 */
    val systemPrompt = /* 原有 buildPrompt */

    if (inferenceRouter.shouldProxy(feature)) {
        return inferenceRouter.complete(feature, systemPrompt, prompt, resolvedTemperature)
    }
    // ... 原有缓存 / provider / 失败恢复
}
```

流式入口同理：

```kotlin
suspend fun generateContentStreamWithReasoning(
    prompt: String,
    type: AiSystemPromptType = AiSystemPromptType.GENERAL,
    temperature: Float = 0.7f,
    feature: AiFeature = AiFeature.from(type),
): Flow<AiStreamChunk> {
    if (inferenceRouter.shouldProxy(feature)) {
        return inferenceRouter.stream(feature, systemPrompt, prompt, temperature)
    }
    return flow { /* 原逻辑 */ }
}
```

`generateContentStream`（被 `AiSearchViewModel` 使用）用同样的判断，返回 `inferenceRouter.stream(...).map { it.text }`。

`AiHandler.generateContent` 做同样的判断（它被 `AiPlaylistGenerator` / `AiWorker` 使用）。

`AiCompanionManager` 走的是另一个入口，也要加：

```kotlin
suspend fun generateWithPrimaryProvider(
    prompt: String,
    type: AiSystemPromptType = AiSystemPromptType.GENERAL,
    temperature: Float = 0.7f,
    timeoutMs: Long = 15_000L,
    feature: AiFeature = AiFeature.from(type),
): String {
    if (inferenceRouter.shouldProxy(feature)) {
        return inferenceRouter.complete(feature, systemPrompt, prompt, temperature)
    }
    // ... 原有 primary provider 逻辑
}
```

> 托管路径的超时由 `SamplingBridge` 按 feature 控制（DJ 30s），不再用这里的 15s `timeoutMs`。

调用点需要显式传 `feature` 的位置：

| 调用点 | 传值 |
|--------|------|
| `AiAssistantViewModel.send` | `ASSISTANT` |
| `AiMixViewModel.build` | `MIX` |
| `AiSearchViewModel` | `SEARCH` |
| `AiStateHolder.explainLyrics` | `LYRICS` |
| `AiCompanionManager` | `DJ` |
| `AiWorker` | `AUTO` |
| 其余（`AiPlaylistGenerator` / `AiMetadataGenerator` / `DailyMixManager` / `AiPlaylistEvaluator`） | 默认 `from(type)` 即可 |

> 托管模式下**跳过 `AiCacheDao` 缓存**（缓存 key 原本按 provider + prompt，客户端换模型后不再成立）；用量表只记请求次数和字符数，不记 token。

### 6.7 流式

```kotlin
fun stream(feature: AiFeature, system: String, prompt: String, temperature: Float): Flow<AiStreamChunk> =
    if (!session.caps.streaming) {
        // 无扩展：先拿完整结果，再本地打字机回放
        flow {
            val text = complete(feature, system, prompt, temperature, maxTokens).text
            var i = 0
            while (i < text.length) {
                val end = (i + 18).coerceAtMost(text.length)
                emit(AiStreamChunk(text.substring(i, end)))
                delay(16)
                i = end
            }
        }
    } else {
        // 有扩展：progress 分片 + 最终结果
        channelFlow {
            val result = complete(
                session = session,
                feature = feature,
                systemPrompt = system,
                prompt = prompt,
                temperature = temperature,
                maxTokens = maxTokens,
                stream = true,
                onChunk = { chunk, thinking -> trySend(AiStreamChunk(chunk, thinking)) },
            )
            // 分片已覆盖正文时不重复；否则用最终文本补齐
            if (result.text.isNotBlank() && !result.streamedFully) {
                send(AiStreamChunk(result.text))
            }
        }
    }
```

`McpSession` 增加 `progressHandlers: Map<String, (String, Boolean) -> Unit>`；收到 `notifications/progress` 时按 `progressToken` 分发：

```kotlin
if (params["_meta"]?.jsonObject?.get("pixelplayer/chunk")?.jsonPrimitive?.booleanOrNull == true) {
    val thinking = params["_meta"]?.jsonObject?.get("pixelplayer/thinking")?.jsonPrimitive?.booleanOrNull ?: false
    session.progressHandlers[token]?.invoke(params["message"]?.jsonPrimitive?.content.orEmpty(), thinking)
}
```

### 6.8 超时、取消、并发

- 超时：DJ 30s / 元数据 60s / 其余 120s。
- 并发：全局 `Semaphore(2)`；助手类请求可以先 `tryAcquire`，满则排队。v1 不做优先级。
- 取消：`SamplingBridge` 在协程取消时发送：

```json
{ "jsonrpc": "2.0", "method": "notifications/cancelled",
  "params": { "requestId": "pp-s-7f3a21", "reason": "user navigated away" } }
```

- SSE 断开：`failAllPending` 立即失败；不自动重发（避免重复计费）。

### 6.9 回退与错误

```kotlin
class McpInferenceException(message: String, val code: Int) : Exception(message)
```

`McpInferenceRouter.complete` 捕获后按 `fallback` 处理：

```kotlin
try {
    return bridge.complete(...)
} catch (e: McpInferenceException) {
    if (policy.fallback == ProxyFallback.INTERNAL) {
        return legacyProviderCall(...)   // 原 AiOrchestrator 路径
    }
    throw e
}
```

UI 文案映射：

| code | 文案 |
|------|------|
| `-32004` | AI 客户端已离线，请重新连接 |
| `-32002` | 客户端未授权该功能的 AI 请求 |
| `-32800` | 请求已取消 |
| 其他 | 客户端返回错误：<message> |

### 6.10 设置页与状态

在「AI 外接」页新增「模型托管」卡片：

```
模型托管
├─ [开关] 所有 AI 请求交给 Cherry Studio
│    当前状态：已接管（Claude Sonnet 4.5）
│    客户端离线时：○ 失败并提示   ○ 回退内置模型
├─ 高级
│    ├─ 按功能选择（CUSTOM 时展开）
│    │    ☑ 助手  ☑ 歌单  ☑ 歌词解读  ☑ 每日混音  ☑ AI DJ  ☑ 自动任务
│    │    ☐ AI 搜索  ☐ 元数据  ☐ 歌单评估
│    ├─ 首选推理客户端：[Cherry Studio ▾]
│    └─ 单次最大 token：4096
└─ [查看将发送的内容示例]
```

同时修改「AI 设置」页：

- 顶部 banner：「AI 请求当前由 Cherry Studio 处理，以下模型设置仅用于回退」。
- provider / API Key / model 输入框 `enabled = false`，保留值。
- 保存按钮仍可用（写回内部设置，供回退使用）。

### 6.11 隐私确认与审计

首次开启托管必须走一次 `AlertDialog`：

```
开启后，以下内容会发送给「Cherry Studio」：
· 你的对话与当前歌曲信息
· 生成歌单时的候选歌曲（歌名/歌手/流派/年份/收藏/评分）
· 歌词解读时的歌词全文
· 每日混音时的听歌统计摘要
不会发送：文件路径、账号、API Key、播放直链。
这些内容可能由客户端再发送给其模型供应商。
[取消] [同意并开启]
```

审计表新增列：`feature / clientId / promptChars / durationMs / result`，默认不保存 prompt 正文。

### 6.12 测试

| 测试 | 内容 |
|------|------|
| `SamplingCapabilityTest` | initialize 声明/未声明 sampling；离线；多客户端选择 |
| `SamplingBridgeTest` | 正常返回、错误返回、超时、取消、SSE 断开、并发上限 |
| `InferenceRouterTest` | OFF / ALL / CUSTOM 各功能路由；回退 NONE/INTERNAL |
| `AiOrchestratorProxyTest` | 清空内部 Key 后，`generateContent` 仍返回客户端文本；缓存被跳过 |
| `ProxyStreamTest` | 无 streaming 能力的打字机回放；有扩展的 progress 分片 |
| `ProxyEndToEndTest` | 假客户端保持 SSE，对 assistant / playlist / lyrics 三个功能返回固定文本，验证 UI 状态 |

手工验证：

```bash
# 1. 客户端保持 SSE
curl -N http://192.168.1.23:8787/mcp \
  -H "Authorization: Bearer $T" \
  -H "Accept: text/event-stream"
# 2. 在播放器开启模型托管，触发歌词解读
# 3. 观察 SSE 上出现 sampling/createMessage
# 4. 用 curl POST 回一个 result，播放器 UI 应显示该文本
```

---

## 7. 资源与事件

### 7.1 资源实现

```kotlin
resources = listOf(
    Resource("pixelplayer://now-playing", "当前播放", "application/json"),
    Resource("pixelplayer://queue", "播放队列", "application/json"),
    Resource("pixelplayer://library/playlists", "歌单列表", "application/json"),
    Resource("pixelplayer://profile", "用户画像", "text/plain"),
)
```

`resources/read` 动态拼接；`pixelplayer://song/{id}/cover` 用 `AlbumArtUtils` 读封面，限制 512 KB，超过则返回 `blob` 前 N 字节或改用 URL 提示。

### 7.2 事件桥

```kotlin
@Singleton
class McpEventBridge @Inject constructor(
    private val state: PlaybackStateHolder,
    @AppScope private val scope: CoroutineScope,
) {
    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val updates: SharedFlow<String> = _updates

    init {
        scope.launch {
            combine(
                state.stablePlayerState.map { it.currentSong?.id to it.isPlaying },
                state.currentPosition,
            ) { a, _ -> a }
                .distinctUntilChanged()
                .debounce(300)
                .collect { _updates.emit("pixelplayer://now-playing") }
        }
    }
}
```

有 SSE 时，`McpEventBridge` 把更新写进 `session.outbound`，由 §3.5 的流直接下发；客户端没保持 SSE 时，更新写进 `McpSession.lastEventSeq`，它在轮询 `resources/read` 时比较 `_meta.seq` 兜底。

---

## 8. 设置页

### 8.1 新增分类

`SettingsCategory` 是 **enum**（`presentation/model/SettingsCategory.kt:31-37`），按「使用频率」插入，不要直接追加到末尾：

```kotlin
enum class SettingsCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
) {
    // ... AI_INTEGRATION 之后
    AI_BRIDGE(
        id = "ai_bridge",
        titleRes = R.string.settings_category_ai_bridge_title,
        subtitleRes = R.string.settings_category_ai_bridge_subtitle,
        icon = Icons.Rounded.Hub,
    ),
}
```

放在 `ai_integration` 之后。需要补 10 种语言文案。

### 8.2 页面结构

```
AI 外接
├─ [开关] 启用 MCP 服务
├─ 状态：运行中 · http://192.168.1.23:8787/mcp · 协议 2025-06-18
├─ [连接 AI 客户端]  → 底部弹层
│    ├─ 已安装的兼容客户端列表（图标 + 名称）
│    │    · Cherry Studio      [连接]
│    │    · Kimi               [连接]
│    ├─ 已配置首选客户端时：主按钮直接「打开 Cherry Studio」
│    ├─ 未检测到客户端：显示「去安装」/「手动输入 IP + 配对码」
│    └─ 说明：点一下会打开对方 App，在对方 App 里点「允许」即完成配对
├─ 已连接客户端
│    ├─ Cherry Studio · desktop · 4 项权限 · 支持模型托管 · 2 分钟前 · [吊销]
│    └─ ...
├─ 模型托管（详见 §6）
│    ├─ [开关] 所有 AI 请求交给 Cherry Studio
│    ├─ 状态：已接管（Claude Sonnet 4.5）/ 客户端离线 / 未声明 sampling
│    ├─ 断线时：[失败并提示] [回退内置模型]
│    └─ 高级：按功能选择、首选推理客户端、单次最大 token
├─ [审计日志] → 最近 200 条（含托管请求）
└─ 高级
     ├─ 端口（默认 8787，与 Web Remote 的 8080 无关）
     ├─ 高风险工具需确认（默认开）
     └─ 允许 AI DJ 语音（默认关）
```

### 8.3 启动/停止

**只操作 MCP 自己的服务**，不要调用 `SettingsViewModel` 里的 Web Remote 启停逻辑：

```kotlin
fun setMcpEnabled(enabled: Boolean) {
    viewModelScope.launch {
        prefs.setMcpServerEnabled(enabled)
        val intent = Intent(context, McpServerService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(context, intent.apply {
                action = McpServerService.ACTION_START
                putExtra(McpServerService.EXTRA_PORT, prefs.mcpPort())
            })
        } else {
            context.startService(intent.apply { action = McpServerService.ACTION_STOP })
        }
    }
}
```

规则：

- MCP 开关 **不影响** Web Remote；Web Remote 开关也 **不影响** MCP。
- 两个服务各自 `startForeground()`，各自持锁；系统杀进程时一起被杀，但重启互不依赖。
- 设置页状态分别读取：`McpServerState.isRunning` vs `WebRemoteServerService.isServerRunning`。
- 卸载/关闭 Web Remote 时，绝不 `stopService(McpServerService)`，反之亦然。

### 8.4 客户端发现与拉起

不生成二维码。播放器读取 `PackageManager` 里的兼容客户端并直接启动：

```kotlin
fun bindClients() {
    val clients = discovery.listCompatible()
    _uiState.update { it.copy(compatibleClients = clients) }
}

fun connect(client: CompatibleClient) {
    viewModelScope.launch {
        val ok = pairing.startPairingWith(client)
        _uiState.update {
            it.copy(pairingHint = if (ok) "正在打开 ${client.label}…" else "无法打开 ${client.label}")
        }
    }
}
```

`startPairingWith` 会先确保 `McpServerService` 已启动（否则客户端连不上），再拉起对方：

```kotlin
private suspend fun ensureServiceRunning() {
    if (McpServerState.isRunning) return
    ContextCompat.startForegroundService(context, Intent(context, McpServerService::class.java).apply {
        action = McpServerService.ACTION_START
    })
    withTimeout(3_000) { McpServerState.running.first { it } }
}
```

设置页不显示配对码、不显示二维码；只在「高级 → 手动连接」里保留「显示 6 位配对码 + IP」的调试入口（给没有注册 scheme 的客户端用）。

深色主题不影响：没有二维码需要对比度处理。

---

## 9. 安全实现要点

### 9.1 来源地址校验

```kotlin
private fun ApplicationCall.isLanClient(): Boolean {
    val remote = request.origin.remoteHost
    val addr = runCatching { InetAddress.getByName(remote) }.getOrNull() ?: return false
    return addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress
}
```

配对接口与 `/mcp` 都校验；非局域网直接 `403`。

### 9.1.1 采样请求的授权边界

- 只有 `initialize` 声明了 `sampling` 且仍在线的 Token 才能收到 `sampling/createMessage`。
- 每个采样请求都带 `pixelplayer/feature`；客户端可据此拒绝某类功能。
- 播放器不把内部 API Key、文件路径、账号信息放进 `systemPrompt` / `messages`。
- 单次 prompt 上限 32,000 字符；超限直接失败，防止把整库数据误发出去。
- 审计只记录字符数、功能、耗时、结果码，不落盘正文。
- 托管开关的关闭必须立即生效：`McpInferenceRouter` 每次调用都读当前 policy，不做进程内缓存。

### 9.1.2 深链与配对安全

- **校验 payload**：`type == "mcp.pair"`、`v == 1`、`exp` 未过期、`code` 6 位数字、`client.scheme` 只允许 `[a-zA-Z][a-zA-Z0-9+.-]*`，非法直接丢弃。
- **显式启动**：播放器拉起客户端时 `Intent(...).setPackage(pkg)`，避免被浏览器或其他 App 截获；不要用裸 `ACTION_VIEW`。
- **回调只回声明过的 scheme**：`client.scheme` 只用于构造 `scheme://mcp/paired`，不访问任何 URL；绝不把 payload 里的地址当作请求目标。
- **一次性 code**：`redeem` 成功后立即作废；重复兑换 `409`；5 分钟过期；单 IP 每分钟最多 10 次尝试，连续失败锁定 1 分钟。
- **Token 不在深链里**：回调 payload 只有 `code + endpoint`；token 只通过 `redeem` 的 HTTP 响应返回。
- **PendingIntent**：通知里的「允许/拒绝」使用 `FLAG_IMMUTABLE`，并绑定明确的 action + code，避免被第三方伪造。
- **包可见性**：只用 `<queries>` 声明 `com.pixelplayer.mcp.CLIENT`，不要申请 `QUERY_ALL_PACKAGES`。
- **卸载/重装**：客户端卸载后其 Token 仍有效但无法连接；设置页可手动吊销。

### 9.2 出站请求白名单（SSRF 约束）

播放器在 MCP 路径上**不提供任意 URL 抓取**。所有出站请求只发生在既有音源/歌词/封面逻辑中，目标域名固定。新增或修改任何出站请求时必须：

1. 只允许 `http` / `https`；
2. 请求前解析 host，拒绝 `localhost`、`127.0.0.0/8`、`::1`、`10/8`、`172.16/12`、`192.168/16`、`169.254/16`、`0.0.0.0`、组播/保留地址；
3. 不跟随跨域重定向；重定向后重新校验；
4. 超时 ≤ 10s，响应体 ≤ 5MB。

```kotlin
fun requirePublicHttpUrl(raw: String): URL {
    val url = URL(raw)
    require(url.protocol in setOf("http", "https")) { "scheme not allowed" }
    val addr = InetAddress.getByName(url.host)
    require(!(addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
        addr.isSiteLocalAddress || addr.isMulticastAddress)) { "private address blocked" }
    return url
}
```

> `online_search` / `resolvePlayUrl` 的目标是固定音源域名，参数是搜索词而非 URL，天然规避 SSRF；但仍需对音源返回的 `pic` 字段做展示前的 scheme 校验（只允许 https 图片）。

### 9.3 返回值脱敏

| 字段 | 处理 |
|------|------|
| `Song.path` | 永不返回 |
| `Song.contentUriString` | 永不返回 |
| `LxSongInfo` 的播放直链 | 不返回；只返回 `source + songId` |
| API Key / Token | 永不返回 |
| 账号 Cookie / 用户 ID | 不返回 |
| 歌词全文 | 允许，但截断 64 KB |
| 封面 | 以 `blob` 返回，≤ 512 KB |

### 9.4 审计与可见性

- 设置页展示每个客户端最近调用时间、次数、被拒次数。
- 「高风险工具需确认」开启时，`queue_clear`、`library_remove_from_playlist`、`app_open_settings(developer)` 会先在播放器弹确认框；客户端收到 `-32005`（`confirmation_required`）并提示用户。
- 一键「断开全部客户端」。

---

## 10. 测试

### 10.1 单元测试

| 测试 | 内容 |
|------|------|
| `McpJsonRpcTest` | 合法/非法 JSON、通知无 id、批量（应拒绝）、超长 body |
| `McpSessionManagerTest` | Token 哈希、常量时间比较、吊销、Scope 交集 |
| `McpPairingManagerTest` | 过期、一次性、错误码 5 次锁定、方向 A/B 状态机 |
| `McpToolRegistryTest` | `tools/list` 按 Scope 过滤、未知工具、参数缺失 |
| `McpToolSchemaTest` | 每个 `inputSchema` 能被 JSON Schema 校验器解析；必填字段与 handler 一致 |
| `PlaybackCommandBusTest` | 有/无 UI handler 两条路径 |
| `SamplingBridgeTest` | 正常/错误/超时/取消/断线/并发上限；迟到响应丢弃 |
| `InferenceRouterTest` | OFF/ALL/CUSTOM 路由；NONE/INTERNAL 回退；`GENERAL` 显式 feature 覆盖 |
| `McpProxyPolicyTest` | 托管开关立即生效；内部 Key 清空仍可调用 |

### 10.2 集成测试

用 `embeddedServer(CIO, port = 0)` 起随机端口 + OkHttp：

```kotlin
@Test fun `initialize then call player_get_state`() = runBlocking {
    val token = pairing.approveForTest(scopes = setOf("read.playback"))
    val init = postMcp(token, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{...}}""")
    assertTrue(init.contains("pixelplayer"))
    val call = postMcp(token, """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"player_get_state","arguments":{}}}""")
    assertTrue(call.contains("isPlaying"))
}
```

### 10.3 手工联调

```bash
# 列出工具
curl -s http://127.0.0.1:8787/mcp -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | jq '.result.tools[].name'

# 播放控制
curl -s http://127.0.0.1:8787/mcp -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"player_play","arguments":{}}}'
```

推荐用官方 **MCP Inspector**：

```bash
npx @modelcontextprotocol/inspector
# Transport: Streamable HTTP
# URL: http://192.168.1.23:8787/mcp
# Header: Authorization: Bearer <token>
```

### 10.4 真机验收矩阵

| 场景 | Android 10 | Android 13 | Android 15 |
|------|-----------|-----------|------------|
| 服务前台运行 30 分钟 | | | |
| 锁屏后仍可调用 | | | |
| 切换 Wi-Fi 后重连 | | | |
| 双客户端并发 | | | |
| 服务被杀后重启（Token 仍有效） | | | |

---

## 11. 实施顺序（建议按 PR 拆）

| PR | 内容 | 依赖 |
|----|------|------|
| #1 | `McpServerService` + `McpPortResolver` + `McpServerState` + `McpJsonRpc` + `/mcp` 路由 + `initialize`/`ping`/`tools/list`（空目录） | 无 |
| #2 | `McpSessionManager` + 配对 API + 设置页开关（先手动插入 Token） | #1 |
| #3 | `PlaybackCommandBus` + `PlayerTools` + `QueueTools` | #1 |
| #4 | `McpPairingManager` + `McpPairingLauncher` + `McpClientDiscovery` + 确认 UI | #2 |
| #5 | `LibraryTools` + `OnlineTools` + `LyricsTools` | #3 |
| #6 | `AiDataTools` + 资源 + 事件桥 | #5 |
| #7 | `AppTools` + 审计 + 限流 + 多语言 | #4 |
| #8 | `GET /mcp` SSE + `McpSseChannel` + 能力协商 | #2 |
| #9 | `SamplingBridge` + `McpInferenceRouter` + `AiOrchestrator`/`AiHandler` 路由 | #8 |
| #10 | 模型托管设置页 + 隐私确认 + 回退策略 | #9 |
| #11 | mDNS / 流式分片 / 文档收尾 | 全部 |

> PR #1 的第一件事是**验证服务隔离**：只启动 `McpServerService`（Web Remote 保持关闭），`8787` 可访问而 `8080` 未监听；再打开 Web Remote，确认两个端口同时可用、互不干扰。

---

## 12. 验收清单（播放器侧）

- [ ] 设置页可开/关 MCP，开关状态持久化
- [ ] 关闭 MCP 时 `8787` 不监听；`8080` 的 Web Remote 不受影响
- [ ] 只开 Web Remote 时 `8787` 不监听；只开 MCP 时 `8080` 不监听
- [ ] 两个服务同时运行时，各自的通知、锁、审计互不干扰
- [ ] 杀掉 MCP 服务不会停止 Web Remote，反之亦然
- [ ] 播放器能列出已安装的兼容客户端，点「连接」能拉起对方 App
- [ ] 客户端点「连接像素播放器」能拉起播放器并弹确认页
- [ ] 全流程不出现二维码/扫码/手输 IP
- [ ] 配对确认框展示客户端名、IP、权限，可拒绝
- [ ] Token 持久化，进程重启后仍有效
- [ ] 吊销后立即 401
- [ ] `tools/list` 按 Scope 过滤，未授权工具不可见
- [ ] `player_get_state` 在播放/暂停/切歌后 100ms 内返回新状态
- [ ] `queue_play` 后 App 队列 UI 与播放一致（有 UI 时）
- [ ] 无 UI 时 `queue_play` 仍能出声
- [ ] `library_create_playlist` 创建的歌单出现在媒体库
- [ ] `online_search` 单源失败不影响其他源
- [ ] 返回值无本地路径、无直链、无密钥
- [ ] 审计日志可查、可清空
- [ ] 单客户端 10 req/s 限流生效
- [ ] 深链 `xiangsuplayer://mcp/connect?p=...` 能唤起确认框
- [ ] 10 种语言文案齐全
- [ ] 单测覆盖协议、会话、配对、Schema
- [ ] 客户端声明 sampling 后，设置页「模型托管」开关可用
- [ ] 开启托管并清空内部 API Key 后，助手/歌单/歌词/每日混音仍可用
- [ ] 客户端未声明 sampling 时开关置灰并提示原因
- [ ] 托管期间 SSE 断开，进行中的请求 3 秒内失败，UI 提示离线
- [ ] 客户端拒绝采样，UI 显示「客户端拒绝」而非网络错误
- [ ] 开启 `INTERNAL` 回退时，客户端离线后自动用内置模型并标注
- [ ] `notifications/cancelled` 发出后，客户端停止生成且播放器忽略迟到结果
- [ ] 托管期间播放器无任何到模型供应商的直连请求（抓包）
- [ ] 关闭托管后 1 秒内恢复内部模型，无需重启
- [ ] 托管审计记录包含 feature/字符数/耗时/结果码，不含 prompt 正文

---

## 13. 已知坑位

1. **Ktor 3.5 的 `embeddedServer` 签名**：端口必须通过 `configure { connector { port = ... } }`，模块通过 `rootConfig = serverConfig { module { } }`（`WebRemoteServerService` 已处理过同样的问题，可参考但不要改它）。MCP 自己起一个 `EmbeddedServer`，不要复用 Web Remote 的实例。
2. **服务隔离**：不要把 MCP 依赖注入 `WebRemoteServerService`，也不要让 MCP 使用 `WebRemoteServerService.currentPin` / `serverAddress` / `isServerRunning`。两边共享静态状态是后续「关一个把另一个带崩」的根源。
3. **端口不要交叉**：MCP 只扫 `8787–8799`，Web Remote 只扫 `8080–8099`；不要写「从 8080 开始找空端口」的通用逻辑，否则两个服务会抢同一端口。
4. **SSE 用 `respondOutputStream` 手写**：不引入 `ktor-server-sse` 也能跑（`text/event-stream` + `write/flush`）。不要依赖 Netty 专有特性；CIO 下注意每次 `flush()`，否则事件会攒在缓冲区里。
5. **`PlaybackStateHolder` 的 `stablePlayerState` 是去抖后的状态**：做「下一首」后立刻读可能还是旧值，工具实现里 `delay(150)` 或等待 `currentMediaItemIndex` 变化。
6. **在线播放走 `MediaItemBuilder`**：`LxSongInfo` 需要先 `saveCloudSong()` 或走 `XsHostBridge` 的在线播放路径，否则队列里没有可用 URI。
7. **`SettingsCategory` 新增会触发 10 语言检查**：CI 里可能有字符串完整性校验，先补 `values-*`。
8. **ProGuard**：`data/mcp/**` 的 `@Serializable` 类需要保留；`app/proguard-rules.pro` 已对 kotlinx.serialization 做通用保留，确认新增包未被裁掉。
9. **Token 与备份**：`mcp_clients_json` 不要进入备份白名单，避免跨设备恢复后旧 Token 仍可用。
10. **前台服务通知**：使用独立的 channel id（如 `mcp_server`），不要复用 Web Remote 的 `CHANNEL_ID`；否则用户单独隐藏/关闭其中一个通知时会误伤另一个。
11. **锁的命名**：`McpNetLocks` 使用独立的 WakeLock tag / Wi-Fi lock tag；不要复用 Web Remote 的静态锁对象。
12. **SSE 是每 Token 一条**：`McpSession.outbound` 不能做成全局广播，否则采样响应会串到别的客户端。
13. **托管模式别走缓存**：`AiCacheDao` 的 key 只含 provider + prompt，托管时客户端换模型会返回旧结果；入口判断要先于缓存。
14. **`GENERAL` 无法区分助手/歌词/DJ**：这三个调用点必须显式传 `AiFeature`，否则 CUSTOM 模式下会误路由。
15. **迟到响应**：请求超时/取消后客户端仍可能回包；`onClientResponse` 找不到 pending id 时直接丢弃并返回 `202`，不要报错。
16. **不要在采样路径里重试**：客户端可能已经计费；重试由用户触发。回退策略也只在失败当下执行一次。
17. **SSE 断线时不要重放 pending 请求**：会造成客户端重复生成、重复计费；直接失败，调用方重新发起。
18. **Android 11+ 包可见性**：不写 `<queries>` 就 `queryIntentActivities` 不到客户端；写 `QUERY_ALL_PACKAGES` 会被应用商店拒。
19. **客户端接收深链的 Activity 必须 `exported=true`**：否则 `setPackage` 启动会被 `SecurityException` 拒绝；在对方 App 的文档里明确要求。
20. **不要用裸 `ACTION_VIEW`**：`scheme://` 可能被浏览器注册并截获，导致「点连接打开的是浏览器」。必须 `setPackage`。
21. **回调会把客户端拉到前台**：这是预期行为（用户刚点完配对）；不要在播放器里再弹「已发送」并阻塞。
