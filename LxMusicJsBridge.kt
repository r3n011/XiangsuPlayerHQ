/**
 * 落雪音乐 JS 音源加载机制 - Kotlin 演示代码
 *
 * 本文件模拟 LX Music Desktop 中 **User API（用户自定义 JS 音源）** 的加载流程：
 * 1. 创建隐藏 WebView，加载空白 HTML
 * 2. 通过 addJavascriptInterface 暴露 lx 对象给 JS（等价于 contextBridge.exposeInMainWorld）
 * 3. 通过 evaluateJavascript 注入用户脚本（等价于 webFrame.executeJavaScript）
 * 4. JS 脚本调用 lx.send("inited", {...}) 注册能力
 * 5. 主端通过 evaluateJavascript 触发 JS 的 request 回调
 * 6. JS 处理后通过 lx 对象回传结果
 *
 * 对应 LX Music 源码文件：
 *   - preload.js: 暴露 lx 对象 + 执行用户脚本
 *   - rendererEvent.ts: 请求队列管理 + 转发
 *   - useInitUserApi.ts: 渲染侧调用封装
 */

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// ====================================================================
// 一、数据模型（对应 LX Music 的类型定义）
// ====================================================================

/** 音乐信息 —— 等价于传给 JS 的 musicInfo */
data class MusicInfo(
    val name: String,           // 歌曲名称
    val singer: String,         // 歌手
    val source: String,         // 音源: kw/kg/tx/wy/mg
    val songmid: String,        // 歌曲唯一 ID
    val interval: String,       // 时长 (如 "04:30")
    val albumName: String = "", // 专辑名
    val img: String = "",       // 封面 URL
    val albumId: String = "",
    val types: List<QualityInfo> = emptyList(),   // [{ type: "320k", size: "10.5M" }]
    val _types: Map<String, QualityDetail> = emptyMap(),
    // 各源特有字段
    val hash: String? = null,          // kg
    val strMediaMid: String? = null,   // tx
    val songId: String? = null,        // tx
    val albumMid: String? = null,      // tx
    val copyrightId: String? = null,   // mg
    val lrcUrl: String? = null,        // mg
    val mrcUrl: String? = null,        // mg
    val trcUrl: String? = null,        // mg
)

data class QualityInfo(val type: String, val size: String)
data class QualityDetail(val size: String)

/** 请求动作类型 */
enum class ActionType {
    musicUrl, lyric, pic
}

/** 请求参数 —— 等价于 useInitUserApi 中 sendUserApiRequest 的 data */
data class UserApiRequest(
    val requestKey: String,    // 请求唯一标识
    val source: String,        // 音源
    val action: ActionType,    // 动作
    val type: String,          // 音质 "128k"|"320k"|"flac"|"flac24bit"
    val musicInfo: MusicInfo,  // 歌曲信息
)

/** 初始化时 JS 声明的能力 */
data class SourceCapability(
    val type: String = "music",
    val actions: List<String> = emptyList(),   // ["musicUrl", "lyric", "pic"]
    val qualitys: List<String> = emptyList(),  // ["128k", "320k", "flac"]
)

// ====================================================================
// 二、向 JS 暴露的接口（等价于 contextBridge.exposeInMainWorld('lx', {...})）
//    对应 preload.js 第 192 行
// ====================================================================

/**
 * 暴露给 JS 的 lx 对象。
 * JS 脚本通过 window.lx 调用这些方法。
 * 等价于 preload.js 中的 contextBridge.exposeInMainWorld('lx', {...})
 */
class LxBridge(
    /** 当 JS 调用 lx.send("inited", data) 时触发 */
    private val onInited: (sourceCapabilities: Map<String, SourceCapability>) -> Unit,
    /** 当 JS 调用 lx.send("updateAlert", data) 时触发 */
    private val onUpdateAlert: (log: String, updateUrl: String?) -> Unit,
    /** 当 JS 脚本执行出错时触发 */
    private val onError: (String) -> Unit,
) {
    companion object {
        private const val TAG = "LxBridge"
        // 等价于 preload.js 中的 EVENT_NAMES
        val EVENT_NAMES = listOf("request", "inited", "updateAlert")
    }

    // 用户脚本注册的 request 回调 —— 等价于 preload.js 第 26 行 const events = { request: null }
    private var requestHandler: ((request: JSONObject) -> Unit)? = null

    private var isInited = false
    private var isShowedUpdateAlert = false

    // ========== 以下是 JS 可调用的方法 ==========

    /**
     * 发起 HTTP 请求 —— 等价于 preload.js 第 194 行 lx.request()
     *
     * 在真实场景中这里会调用 OkHttp 等网络库。
     * 这里简化为回调模拟。
     */
    @JavascriptInterface
    fun request(url: String, options: String, callback: String) {
        Log.d(TAG, "lx.request called: url=$url")
        // 实际实现中：解析 options JSON，调用 OkHttp，回调通过 evaluateJavascript 执行
        // 这里只做演示
    }

    /**
     * 发送事件 —— 等价于 preload.js 第 243 行 lx.send()
     *
     * 支持的事件：
     *   - "inited": 脚本初始化完成，声明支持的源和动作
     *   - "updateAlert": 更新提示
     */
    @JavascriptInterface
    fun send(eventName: String, data: String) {
        Log.d(TAG, "lx.send: eventName=$eventName, data=$data")
        if (eventName !in EVENT_NAMES) {
            Log.e(TAG, "Unsupported event: $eventName")
            return
        }
        when (eventName) {
            "inited" -> {
                if (isInited) {
                    Log.e(TAG, "Script is already inited")
                    return
                }
                isInited = true
                handleInit(data)
            }
            "updateAlert" -> {
                if (isShowedUpdateAlert) {
                    Log.e(TAG, "Update alert can only be called once")
                    return
                }
                isShowedUpdateAlert = true
                handleUpdateAlert(data)
            }
        }
    }

    /**
     * 注册事件监听 —— 等价于 preload.js 第 263 行 lx.on()
     *
     * 目前只支持 "request" 事件，用于接收音源请求。
     */
    @JavascriptInterface
    fun on(eventName: String, handlerId: String) {
        Log.d(TAG, "lx.on: eventName=$eventName")
        if (eventName !in EVENT_NAMES) {
            Log.e(TAG, "Unsupported event: $eventName")
            return
        }
        when (eventName) {
            "request" -> {
                // 将 handlerId 存起来，后续通过 evaluateJavascript 调用
                // 等价于 preload.js 第 267 行 events.request = handler
                requestHandler = { requestData ->
                    webView?.post {
                        webView?.evaluateJavascript(
                            "$handlerId(${requestData.toString().replace("'", "\\'")})",
                            null
                        )
                    }
                }
            }
        }
    }

    /** 获取加密工具 —— 等价于 preload.js 第 274 行 lx.utils.crypto */
    @JavascriptInterface
    fun crypto(): String = """{
        "aesEncrypt": "function(buffer, mode, key, iv) { /* AES加密 */ }",
        "rsaEncrypt": "function(buffer, key) { /* RSA加密 */ }",
        "md5": "function(str) { /* MD5 */ }",
        "randomBytes": "function(size) { /* 随机字节 */ }"
    }"""

    /** 获取当前脚本信息 —— 等价于 preload.js 第 318 行 */
    @JavascriptInterface
    fun currentScriptInfo(): String = """{
        "name": "示例音源",
        "description": "一个演示用的音源脚本",
        "version": "1.0.0",
        "author": "demo",
        "homepage": "https://example.com",
        "rawScript": "..."
    }"""

    @JavascriptInterface
    fun version(): String = "2.0.0"

    @JavascriptInterface
    fun env(): String = "android" // LX Music 中这里是 "desktop"

    // ========== 内部处理 ==========

    private fun handleInit(data: String) {
        try {
            val json = JSONObject(data)
            val sources = json.getJSONObject("sources")
            val capabilities = mutableMapOf<String, SourceCapability>()

            val allSources = listOf("kw", "kg", "tx", "wy", "mg", "local")
            val supportQualitys = listOf("128k", "320k", "flac", "flac24bit")
            val supportActions = mapOf(
                "kw" to listOf("musicUrl"),
                "kg" to listOf("musicUrl"),
                "tx" to listOf("musicUrl"),
                "wy" to listOf("musicUrl"),
                "mg" to listOf("musicUrl"),
                "local" to listOf("musicUrl", "lyric", "pic"),
            )

            for (source in allSources) {
                val userSource = sources.optJSONObject(source) ?: continue
                if (userSource.optString("type") != "music") continue

                val qualitys = supportQualitys.filter { q ->
                    userSource.optJSONArray("qualitys")?.let { arr ->
                        (0 until arr.length()).any { i -> arr.getString(i) == q }
                    } ?: false
                }
                val actions = (supportActions[source] ?: emptyList()).filter { a ->
                    userSource.optJSONArray("actions")?.let { arr ->
                        (0 until arr.length()).any { i -> arr.getString(i) == a }
                    } ?: false
                }
                capabilities[source] = SourceCapability("music", actions, qualitys)
            }

            Log.d(TAG, "User API inited with capabilities: $capabilities")
            onInited(capabilities)
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}")
            onError(e.message ?: "Unknown error")
        }
    }

    private fun handleUpdateAlert(data: String) {
        try {
            val json = JSONObject(data)
            val log = json.getString("log").take(1024)
            val updateUrl = json.optString("updateUrl").takeIf { it.isNotEmpty() && it.length < 1024 }
            onUpdateAlert(log, updateUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Update alert error: ${e.message}")
        }
    }

    /** 关联的 WebView 引用，用于 evaluateJavascript */
    var webView: WebView? = null

    /**
     * 触发用户脚本的 request 回调 —— 等价于 preload.js 第 164 行
     * 主端调用此方法将请求数据传给 JS 脚本
     */
    fun triggerRequest(request: UserApiRequest) {
        if (requestHandler == null) {
            Log.e(TAG, "Request handler not registered")
            return
        }
        val requestData = JSONObject().apply {
            put("source", request.source)
            put("action", request.action.name)
            put("info", JSONObject().apply {
                put("type", request.type)
                put("musicInfo", musicInfoToJson(request.musicInfo))
            })
        }
        Log.d(TAG, "Triggering request: $requestData")
        requestHandler?.invoke(requestData)
    }

    private fun musicInfoToJson(info: MusicInfo): JSONObject = JSONObject().apply {
        put("name", info.name)
        put("singer", info.singer)
        put("source", info.source)
        put("songmid", info.songmid)
        put("interval", info.interval)
        put("albumName", info.albumName)
        put("img", info.img)
        put("albumId", info.albumId)
        // ... 其他字段按需添加
    }
}

// ====================================================================
// 三、UserApiManager —— 管理 WebView 生命周期 + 请求队列
//    对应 LX Music 的 userApi/main.ts + rendererEvent.ts
// ====================================================================

class UserApiManager {

    companion object {
        private const val TAG = "UserApiManager"
        private const val REQUEST_TIMEOUT_MS = 20_000L
    }

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 请求队列 —— 等价于 rendererEvent.ts 第 10 行 requestQueue */
    private val requestQueue = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /** 超时管理 —— 等价于 rendererEvent.ts 第 11 行 timeouts */
    private val timeouts = ConcurrentHashMap<String, Job>()

    /** 当前音源能力 */
    var capabilities: Map<String, SourceCapability> = emptyMap()
        private set

    var apiStatus: Boolean = false
        private set

    /**
     * 初始化 WebView 并加载用户脚本
     * 等价于 createWindow() + initEnv 流程
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun init(webView: WebView, userScript: String) {
        this.webView = webView

        // 1. 创建 LxBridge 并注入 —— 等价于 contextBridge.exposeInMainWorld('lx', {...})
        val bridge = LxBridge(
            onInited = { caps ->
                Log.d(TAG, "Script inited: $caps")
                capabilities = caps
                apiStatus = true
            },
            onUpdateAlert = { log, url ->
                Log.d(TAG, "Update alert: $log, url=$url")
            },
            onError = { msg ->
                Log.e(TAG, "Script error: $msg")
                apiStatus = false
            }
        )
        bridge.webView = webView
        webView.addJavascriptInterface(bridge, "lx")

        // 2. 配置 WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // 3. 注入错误捕获 —— 等价于 preload.js 第 355-364 行
                webView.evaluateJavascript("""
                    (() => {
                        window.addEventListener('error', (event) => {
                            if (event.isTrusted) {
                                window.lx.send('inited', JSON.stringify({
                                    status: false,
                                    message: (event.message || '').replace(/^Uncaught\s*Error:\s*/, '')
                                }));
                            }
                        });
                        window.addEventListener('unhandledrejection', (event) => {
                            if (!event.isTrusted) return;
                            const message = typeof event.reason === 'string' 
                                ? event.reason 
                                : (event.reason?.message || String(event.reason));
                            window.lx.send('inited', JSON.stringify({
                                status: false,
                                message: message.replace(/^Error:\s*/, '')
                            }));
                        });
                    })();
                """.trimIndent(), null)

                // 4. 执行用户脚本 —— 等价于 preload.js 第 366 行 webFrame.executeJavaScript(userApi.script)
                webView.evaluateJavascript(userScript) { result ->
                    Log.d(TAG, "Script executed: $result")
                }
            }
        }

        // 加载空白页面
        webView.loadUrl("about:blank")
    }

    /**
     * 发送请求到 JS 脚本 —— 等价于 rendererEvent.ts 第 124 行 request()
     *
     * @return 请求结果（JSON 字符串）
     */
    suspend fun sendRequest(request: UserApiRequest): String {
        if (!apiStatus) throw IllegalStateException("User API is not initialized")

        val deferred = CompletableDeferred<String>()

        // 设置超时 —— 等价于 rendererEvent.ts 第 137-139 行
        val timeoutJob = scope.launch {
            delay(REQUEST_TIMEOUT_MS)
            if (deferred.isActive) {
                deferred.completeExceptionally(TimeoutException("Request timeout: ${request.requestKey}"))
            }
        }
        timeouts[request.requestKey] = timeoutJob

        // 存入队列 —— 等价于 rendererEvent.ts 第 141 行
        requestQueue[request.requestKey] = deferred

        // 触发 JS 的 request 回调 —— 等价于 sendRequest() → sendEvent() → mainSend()
        handler.post {
            val bridge = getBridge() ?: return@post
            bridge.triggerRequest(request)
        }

        return try {
            deferred.await()
        } finally {
            timeouts.remove(request.requestKey)
            requestQueue.remove(request.requestKey)
        }
    }

    /**
     * 取消请求 —— 等价于 rendererEvent.ts 第 116 行 cancelRequest()
     */
    fun cancelRequest(requestKey: String) {
        timeouts.remove(requestKey)?.cancel()
        requestQueue.remove(requestKey)?.completeExceptionally(CancellationException("Cancel request: $requestKey"))
    }

    /**
     * 当 JS 脚本通过 lx 对象回传结果时调用此方法
     * 等价于 rendererEvent.ts 第 51 行 handleResponse()
     */
    fun onResponse(requestKey: String, status: Boolean, result: String?, message: String?) {
        val deferred = requestQueue.remove(requestKey) ?: return
        timeouts.remove(requestKey)?.cancel()

        if (status && result != null) {
            deferred.complete(result)
        } else {
            deferred.completeExceptionally(RuntimeException(message ?: "Unknown error"))
        }
    }

    private fun getBridge(): LxBridge? {
        // 从 WebView 获取已注入的 bridge
        // 实际实现中可能需要另外保存引用
        return null // 简化处理
    }

    fun destroy() {
        scope.cancel()
        requestQueue.forEach { (key, deferred) ->
            deferred.completeExceptionally(CancellationException("Manager destroyed"))
        }
        requestQueue.clear()
        timeouts.clear()
        webView?.destroy()
        webView = null
    }
}

// ====================================================================
// 四、使用示例 —— 对应 LX Music 的 useInitUserApi.ts 调用流程
// ====================================================================

class MusicPlayerDemo(
    private val webView: WebView,
    private val userApiManager: UserApiManager = UserApiManager(),
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 用户编写的 JS 音源脚本（示例） */
    private val userScript = """
        // ========================================
        // 用户 JS 音源脚本 —— 等价于 LX Music 中的用户脚本
        // 此脚本通过 window.lx 与主端通信
        // ========================================

        // 1. 注册 request 事件处理器
        // 等价于 preload.js 中 events.request = handler
        window.lx.on('request', async function(requestData) {
            console.log('Received request:', JSON.stringify(requestData));

            const { source, action, info } = requestData;

            switch (action) {
                case 'musicUrl': {
                    // 获取音乐URL
                    const url = await fetchMusicUrl(source, info.musicInfo, info.type);
                    // 返回结果 —— 等价于 preload.js 第 68-86 行
                    return url;
                }
                case 'lyric': {
                    // 获取歌词
                    const lyricInfo = await fetchLyric(source, info.musicInfo);
                    return lyricInfo;
                }
                case 'pic': {
                    // 获取封面
                    const picUrl = await fetchPic(source, info.musicInfo);
                    return picUrl;
                }
            }
        });

        // 模拟获取音乐 URL
        async function fetchMusicUrl(source, musicInfo, type) {
            // 实际场景中，这里会调用第三方音乐 API 获取播放链接
            // 可用 window.lx.request() 发起 HTTP 请求
            return 'https://example.com/music/' + musicInfo.songmid + '/' + type;
        }

        async function fetchLyric(source, musicInfo) {
            return {
                lyric: '[00:00.00]歌词内容',
                tlyric: null,
                rlyric: null,
                lxlyric: null
            };
        }

        async function fetchPic(source, musicInfo) {
            return 'https://example.com/pic/' + musicInfo.songmid + '.jpg';
        }

        // 2. 初始化声明 —— 告诉主端此脚本支持哪些源和动作
        // 等价于 preload.js 第 142-162 行
        window.lx.send('inited', JSON.stringify({
            sources: {
                kw: {
                    type: 'music',
                    actions: ['musicUrl'],
                    qualitys: ['128k', '320k', 'flac', 'flac24bit']
                },
                kg: {
                    type: 'music',
                    actions: ['musicUrl'],
                    qualitys: ['128k', '320k', 'flac', 'flac24bit']
                },
                tx: {
                    type: 'music',
                    actions: ['musicUrl'],
                    qualitys: ['128k', '320k', 'flac', 'flac24bit']
                },
                wy: {
                    type: 'music',
                    actions: ['musicUrl'],
                    qualitys: ['128k', '320k', 'flac', 'flac24bit']
                },
                mg: {
                    type: 'music',
                    actions: ['musicUrl'],
                    qualitys: ['128k', '320k', 'flac', 'flac24bit']
                }
            }
        }));
    """.trimIndent()

    /**
     * 初始化 —— 加载 JS 脚本到 WebView
     */
    fun init() {
        userApiManager.init(webView, userScript)
    }

    /**
     * 获取音乐 URL —— 等价于 LX Music 的 online.ts: getMusicUrl()
     *
     * 对应 useInitUserApi.ts 第 41-66 行的 getMusicUrl 方法
     */
    suspend fun getMusicUrl(source: String, musicInfo: MusicInfo, quality: String): String {
        val requestKey = "request__${System.currentTimeMillis()}_${Math.random()}"

        val request = UserApiRequest(
            requestKey = requestKey,
            source = source,
            action = ActionType.musicUrl,
            type = quality,
            musicInfo = musicInfo,
        )

        return try {
            val result = userApiManager.sendRequest(request)
            // 解析返回结果 —— 等价于 useInitUserApi.ts 第 58-60 行
            // 返回格式: { type: "320k", url: "https://..." }
            val json = JSONObject(result)
            val url = json.getString("url")
            Log.d("MusicPlayer", "Got music URL: $url for ${musicInfo.name}")
            url
        } catch (e: CancellationException) {
            Log.d("MusicPlayer", "Request cancelled: $requestKey")
            throw e
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Failed to get music URL: ${e.message}")
            throw e
        }
    }

    /**
     * 获取歌词 —— 等价于 useInitUserApi.ts 第 69-93 行
     */
    suspend fun getLyric(source: String, musicInfo: MusicInfo): JSONObject {
        val requestKey = "request__${System.currentTimeMillis()}_${Math.random()}"
        val request = UserApiRequest(
            requestKey = requestKey,
            source = source,
            action = ActionType.lyric,
            type = "128k",
            musicInfo = musicInfo,
        )
        return JSONObject(userApiManager.sendRequest(request))
    }

    /**
     * 获取封面 —— 等价于 useInitUserApi.ts 第 97-122 行
     */
    suspend fun getPic(source: String, musicInfo: MusicInfo): String {
        val requestKey = "request__${System.currentTimeMillis()}_${Math.random()}"
        val request = UserApiRequest(
            requestKey = requestKey,
            source = source,
            action = ActionType.pic,
            type = "128k",
            musicInfo = musicInfo,
        )
        return userApiManager.sendRequest(request)
    }

    fun destroy() {
        scope.cancel()
        userApiManager.destroy()
    }
}

// ====================================================================
// 五、Activity 中的使用示例
// ====================================================================

/**
 * 在 Activity 中使用的完整示例
 */
class DemoActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var musicPlayer: MusicPlayerDemo

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 创建隐藏 WebView（LX Music 中也是创建隐藏 BrowserWindow）
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
        }

        // 2. 创建播放器
        musicPlayer = MusicPlayerDemo(webView)

        // 3. 初始化 —— 加载 JS 脚本
        musicPlayer.init()

        // 4. 模拟播放一首酷我音乐
        CoroutineScope(Dispatchers.Main).launch {
            // 构建歌曲信息 —— 等价于 toOldMusicInfo 转换后的数据
            val songInfo = MusicInfo(
                name = "夜曲",
                singer = "周杰伦",
                source = "kw",
                songmid = "62355680",
                interval = "03:46",
                albumName = "十一月的萧邦",
                types = listOf(
                    QualityInfo("128k", "3.4M"),
                    QualityInfo("320k", "8.5M"),
                    QualityInfo("flac", "22.1M"),
                ),
                _types = mapOf(
                    "128k" to QualityDetail("3.4M"),
                    "320k" to QualityDetail("8.5M"),
                    "flac" to QualityDetail("22.1M"),
                ),
            )

            try {
                // 获取音乐 URL —— 到此即完成整个 LX Music 风格的 JS 音源调用
                val url = musicPlayer.getMusicUrl("kw", songInfo, "320k")
                Log.d("DemoActivity", "播放URL: $url")
                // 拿到 url 后就可以用 MediaPlayer 播放了
            } catch (e: Exception) {
                Log.e("DemoActivity", "获取音乐URL失败: ${e.message}")
                // 换源重试 —— 等价于 LX Music 的 getOtherSource 换源逻辑
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        musicPlayer.destroy()
    }
}

// ====================================================================
// 六、数据流对照总结
// ====================================================================

/*
 * LX Music Desktop (Electron)          →  本 Kotlin 实现
 * ─────────────────────────────────────────────────────
 * BrowserWindow (contextIsolation)     →  WebView (addJavascriptInterface)
 * contextBridge.exposeInMainWorld('lx') →  webView.addJavascriptInterface(bridge, "lx")
 * webFrame.executeJavaScript(script)   →  webView.evaluateJavascript(script)
 * ipcRenderer.send()                   →  lx.send() → @JavascriptInterface 回调
 * ipcRenderer.on()                     →  lx.on() → 存储 handler 引用
 * mainSend(window, name, data)         →  webView.evaluateJavascript("handler(data)")
 * requestQueue                         →  ConcurrentHashMap<String, CompletableDeferred>
 * timeouts                             →  Coroutine delay + Job
 *
 * 传给 JS 的数据结构完全一致：
 * {
 *   source: "kw",
 *   action: "musicUrl",
 *   info: {
 *     type: "320k",
 *     musicInfo: { name, singer, songmid, interval, albumName, img, ... }
 *   }
 * }
 */