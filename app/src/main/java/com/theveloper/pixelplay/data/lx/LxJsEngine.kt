package com.theveloper.pixelplay.data.lx

import android.content.Context
import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxJsEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val fileStore: LxFileStore,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val qjsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Volatile private var loaderInited = false
    @Volatile private var ctx: QuickJSContext? = null
    @Volatile private var inited: Boolean = false
    @Volatile private var sources: Map<String, LxSourceInfo> = emptyMap()
    @Volatile private var jsVersionName: String = "unknown"
    @Volatile var lastError: String? = null
        private set

    suspend fun ready(): Boolean = mutex.withLock {
        lastError = null
        if (inited && ctx != null) return@withLock true
        if (!loaderInited) {
            try {
                QuickJSLoader.init()
                loaderInited = true
                Log.d(TAG, "QuickJSLoader.init() OK")
            } catch (t: Throwable) {
                Log.e(TAG, "QuickJSLoader.init() FAILED", t)
                lastError = "QuickJSLoader.init() 失败: ${t.message}"
                return@withLock false
            }
        }

        val js = try {
            fileStore.content()
        } catch (t: Throwable) {
            Log.e(TAG, "read js file failed", t)
            lastError = "读取 JS 文件失败: ${t.message}"
            null
        }
        if (js == null) {
            lastError = "没有导入 JS 文件"
            return@withLock false
        }
        if (js.isBlank()) {
            lastError = "JS 文件为空"
            return@withLock false
        }

        val c = runCatching {
            withContext(qjsDispatcher) { QuickJSContext.create() }
        }.onFailure {
            Log.e(TAG, "QuickJSContext.create() FAILED", it)
            lastError = "QuickJS native create 失败(设备 ABI 可能不支持): ${it.message}"
        }.getOrNull() ?: return@withLock false

        try {
            val success = withContext(qjsDispatcher) {
                injectLxShim(c)
                val version = extractVersion(js)
                Log.d(TAG, "about to eval userapi.js, size=${js.length}, version=$version")
                val evalRes = runCatching { c.evaluate(js, "userapi.js") }
                if (evalRes.isFailure) {
                    val t = evalRes.exceptionOrNull()!!
                    Log.e(TAG, "userapi.js eval FAILED", t)
                    lastError = "JS 解析/执行失败: ${t.javaClass.simpleName}: ${t.message}"
                    return@withContext false
                }
                jsVersionName = version

                Log.d(TAG, "userapi.js eval OK, about to run pending jobs")
                repeat(100) {
                    runCatching { c.evaluate("null", "tick$it") }
                }

                sources = readSourcesFromJs(c)
                Log.d(TAG, "sources.size=${sources.size}, sources.keys=${sources.keys}")

                if (sources.isEmpty()) {
                    if (lastError == null) {
                        lastError = "JS 初始化了但没有注册任何音源。请确认文件是否是落雪 userApi 格式。"
                    }
                    return@withContext false
                }
                true
            }

            if (!success) {
                runCatching { withContext(qjsDispatcher) { c.destroy() } }
                return@withLock false
            }

            ctx = c
            inited = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            lastError = "初始化异常: ${t.javaClass.simpleName}: ${t.message}"
            runCatching { withContext(qjsDispatcher) { c.destroy() } }
            false
        }
    }

    fun isReady(): Boolean = inited && ctx != null
    fun getSources(): Map<String, LxSourceInfo> = sources
    fun versionName(): String = jsVersionName

    fun close() {
        runCatching {
            val c = ctx ?: return@runCatching
            runBlocking(qjsDispatcher) { c.destroy() }
        }
        ctx = null; inited = false; sources = emptyMap(); jsVersionName = "unknown"
    }

    suspend fun reload(): Boolean {
        close()
        return ready()
    }

    suspend fun search(keyword: String, source: String, page: Int = 1, pagesize: Int = 30): LxSearchResult {
        ensureReady()
        val c = ctx!!
        val info = mapOf(
            "keyword" to keyword,
            "page" to page,
            "pagesize" to pagesize,
            "type" to "music"
        )
        val raw = withContext(qjsDispatcher) { callDispatcher(c, "musicSearch", source, info, timeoutMs = 30000) }
        return parseSearchResult(raw)
    }

    suspend fun getPlayUrl(source: String, songInfo: Map<String, Any?>, quality: String = "128k"): String? {
        ensureReady()
        val c = ctx!!
        return withContext(qjsDispatcher) {
            val startTime = System.currentTimeMillis()
            try {
                // 构造 info 对象的 JSON 字符串：{ musicInfo: {id,name,singer,...}, type: "128k" }
                val infoObj = JSONObject()
                infoObj.put("musicInfo", JSONObject(songInfo))
                infoObj.put("type", quality)
                val infoJson = infoObj.toString()

                Log.d(TAG, "=== getPlayUrl START ===")
                Log.d(TAG, "  source: $source, quality: $quality")
                Log.d(TAG, "  infoJson len=${infoJson.length}")

                val global = c.globalObject
                global.setProperty("__px_arg_action", "musicUrl")
                global.setProperty("__px_arg_source", source)
                global.setProperty("__px_arg_info", infoJson)

                c.evaluate("__pixelplay_call_js(__px_arg_action, __px_arg_source, JSON.parse(__px_arg_info));", "call")

                val deadline = System.currentTimeMillis() + 60000
                var loopCount = 0
                var doneInt: Int
                while (System.currentTimeMillis() < deadline) {
                    loopCount++
                    val doneRaw = c.evaluate("__pixelplay_pending_done()", "done")
                    doneInt = when (doneRaw) {
                        is Number -> doneRaw.toInt()
                        is String -> doneRaw.toIntOrNull() ?: 0
                        else -> 0
                    }
                    if (doneInt == 1) break
                    runCatching { c.evaluate("null", "tick") }
                    delay(20)
                }
                Log.d(TAG, "  loop=$loopCount, elapsed=${System.currentTimeMillis() - startTime}ms")

                val errMsg = c.evaluate("__pixelplay_pending_err()", "err")?.toString()
                if (errMsg != null) {
                    Log.e(TAG, "  JS error: $errMsg")
                    Log.e(TAG, "=== getPlayUrl FAILED (JS error) ===")
                    return@withContext null
                }

                val raw = c.evaluate("__pixelplay_pending_value()", "value")?.toString()
                Log.d(TAG, "  raw first 200 chars: ${raw?.take(200)}")
                if (raw != null) {
                    val rawBytes = raw.toByteArray(Charsets.UTF_8)
                    val hexStr = rawBytes.take(40).joinToString(" ") { String.format("%02x", it) }
                    Log.d(TAG, "  raw first 40 bytes hex: $hexStr")
                    Log.d(TAG, "  raw length: ${raw.length}, first char code: ${raw.firstOrNull()?.code}")
                }

                val result = when {
                    raw == null || raw.isBlank() -> null
                    raw.startsWith('"') && raw.endsWith('"') && raw.length >= 2 -> {
                        // JS returned a JSON string literal: "https://..."
                        val stripped = raw.substring(1, raw.length - 1)
                        Log.d(TAG, "  JSON string literal detected, stripped: ${stripped.take(100)}")
                        stripped
                    }
                    raw.startsWith("{") -> {
                        runCatching { JSONObject(raw) }
                            .map { it.optString("url").takeIf { it.isNotBlank() } }
                            .getOrNull()
                            ?: raw
                    }
                    raw.startsWith("[") -> {
                        runCatching { org.json.JSONArray(raw) }
                            .map { arr ->
                                (0 until arr.length()).asSequence()
                                    .map { i -> arr.optString(i) }
                                    .firstOrNull { it.isNotBlank() }
                            }
                            .getOrNull()
                            ?: raw
                    }
                    else -> {
                        Log.d(TAG, "  raw does NOT start with { or \" or [, using as-is")
                        raw
                    }
                }

                if (result != null) {
                    Log.d(TAG, "=== getPlayUrl SUCCESS: ${result.take(120)} ===")
                    Log.d(TAG, "  result length: ${result.length}, first char code: ${result.firstOrNull()?.code}")
                    val resBytes = result.toByteArray(Charsets.UTF_8)
                    val resHex = resBytes.take(40).joinToString(" ") { String.format("%02x", it) }
                    Log.d(TAG, "  result first 40 bytes hex: $resHex")
                } else {
                    Log.e(TAG, "=== getPlayUrl FAILED (no result) ===")
                }
                result
            } catch (t: Throwable) {
                Log.e(TAG, "getPlayUrl failed", t)
                null
            }
        }
    }

    private fun ensureReady() {
        if (!inited || ctx == null) throw IllegalStateException("LxJsEngine not ready. Call ready() first.")
    }

    private fun injectLxShim(c: QuickJSContext) {
        val shim = """
        (function(){
            var _handlers = {};
            var _initedJson = null;
            var _done = false;
            var __px_pending_done = false;
            var __px_pending_value = null;
            var __px_pending_err = null;

            function httpSync(url, options) {
                var method = 'GET', timeout = 15000, headers = {}, body = null;
                if (options) {
                    if (options.method) method = String(options.method).toUpperCase();
                    if (options.timeout != null) timeout = Number(options.timeout);
                    if (options.headers && typeof options.headers === 'object') headers = options.headers;
                    if (options.body != null) body = (typeof options.body === 'string') ? options.body : JSON.stringify(options.body);
                }
                try {
                    var resJson = __pixelplay_okhttp_sync(url, method, timeout, JSON.stringify(headers), body);
                    if (typeof resJson !== 'string') return { statusCode: 0, headers: {}, body: "", error: "invalid native response" };
                    var res = JSON.parse(resJson);
                    if (!res || typeof res !== 'object') return { statusCode: 0, headers: {}, body: "", error: "invalid native response" };
                    return { statusCode: res.statusCode || 0, headers: res.headers || {}, body: res.body || "", error: res.error || null };
                } catch (e) {
                    return { statusCode: 0, headers: {}, body: "", error: String(e && e.message ? e.message : e) };
                }
            }

            var _api = {
                EVENT_NAMES: { request: 'request', inited: 'inited' },
                request: function(url, options, cb) {
                    try {
                        var res = httpSync(url, options || {});
                        if (res && res.error) { cb(new Error(String(res.error)), null); return; }
                        cb(null, { statusCode: res.statusCode || 0, headers: res.headers || {}, body: res.body || "" });
                    } catch (e) { cb(new Error(String(e && e.message ? e.message : e)), null); }
                },
                on: function(name, handler) { if (name === 'request') _handlers.request = handler; },
                send: function(name, payload) {
                    if (name === 'inited') {
                        try { _initedJson = JSON.stringify(payload || { sources: {} }); }
                        catch (e) { _initedJson = JSON.stringify({ error: String(e && e.message ? e.message : e) }); }
                        _done = true;
                    }
                }
            };

            if (typeof globalThis !== 'undefined') globalThis.lx = _api;
            else if (typeof global !== 'undefined') global.lx = _api;

            __pixelplay_get_inited_js = function() { return _initedJson; };
            __pixelplay_get_done_js = function() { return _done; };
            __pixelplay_call_js = function(action, source, info) {
                __px_pending_done = false;
                __px_pending_value = null;
                __px_pending_err = null;
                var h = _handlers.request;
                if (!h) { 
                    __px_pending_err = "no request handler registered";
                    __px_pending_done = true; 
                    return; 
                }
                try {
                    var reqData = { action: action, source: source, info: info };
                    var res = h(reqData);
                    if (res && typeof res.then === 'function') {
                        res.then(function(v){
                            try { __px_pending_value = JSON.stringify(v); } catch(e) { __px_pending_value = JSON.stringify({error: String(e && e.message)}); }
                            __px_pending_done = true;
                        }, function(e){
                            __px_pending_err = e ? (e.message || String(e)) : "rejected";
                            __px_pending_done = true;
                        });
                        return;
                    }
                    try { __px_pending_value = JSON.stringify(res); } catch(e) { __px_pending_value = JSON.stringify({error: String(e && e.message)}); }
                    __px_pending_done = true;
                } catch (e) {
                    __px_pending_err = e ? (e.message || String(e)) : "thrown";
                    __px_pending_done = true;
                }
            };
            __pixelplay_pending_done = function() { return __px_pending_done ? 1 : 0; };
            __pixelplay_pending_value = function() { return __px_pending_value; };
            __pixelplay_pending_err = function() { return __px_pending_err ? String(__px_pending_err) : null; };
        })();
        """.trimIndent()

        val global = c.globalObject

        global.setProperty("__pixelplay_okhttp_sync", JSCallFunction { args ->
            try {
                val url = (args[0] as? String).orEmpty()
                val method = (args[1] as? String).orEmpty()
                val timeoutMs = (args[2] as? Number)?.toInt() ?: 15000
                val headersJson = (args[3] as? String).orEmpty()
                val body = args[4] as? String
                runLxOkHttp(url, method, timeoutMs, headersJson, body)  // returns JSON String
            } catch (t: Throwable) {
                val err = JSONObject()
                err.put("statusCode", 0)
                err.put("headers", JSONObject())
                err.put("body", "")
                err.put("error", t.message ?: t.javaClass.simpleName)
                err.toString()
            }
        })

        global.setProperty("__pixelplay_console_log", JSCallFunction { args ->
            try { Log.d("LxJs", args.joinToString(" ")) } catch (_: Throwable) {}
            null
        })
        global.setProperty("__pixelplay_console_err", JSCallFunction { args ->
            try { Log.e("LxJs", args.joinToString(" ")) } catch (_: Throwable) {}
            null
        })

        try { c.evaluate(shim, "pixelplay-shim.js") } catch (t: Throwable) { Log.e(TAG, "shim eval", t) }
    }

    private fun runLxOkHttp(url: String, method: String, timeoutMs: Int, headersJson: String, body: String?): String {
        val headers = runCatching {
            if (headersJson.isBlank()) emptyMap()
            else JSONObject(headersJson).toMap().mapValues { it.value?.toString().orEmpty() }
        }.getOrDefault(emptyMap<String, String>())

        val resp = runBlocking(Dispatchers.IO) {
            LxHttpFetcher.request(
                url = url,
                method = method,
                headers = headers,
                body = body,
                timeoutMs = timeoutMs.toLong().coerceAtLeast(1000L)
            )
        }
        val obj = JSONObject()
        obj.put("statusCode", resp.statusCode)
        obj.put("body", resp.body)
        obj.put("headers", JSONObject(resp.headers))
        if (resp.error != null) obj.put("error", resp.error)
        return obj.toString()
    }

    private fun readSourcesFromJs(c: QuickJSContext): Map<String, LxSourceInfo> {
        return runCatching {
            val rawJson = c.evaluate("__pixelplay_get_inited_js()", "get_inited")?.toString()
            Log.d(TAG, "raw inited json len=${rawJson?.length}, preview=${rawJson?.take(200)}")
            if (rawJson.isNullOrBlank()) return@runCatching emptyMap()
            val obj = JSONObject(rawJson)
            val sourcesObj = obj.optJSONObject("sources") ?: return@runCatching emptyMap()
            val keys = sourcesObj.keys()
            val result = linkedMapOf<String, LxSourceInfo>()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = sourcesObj.optJSONObject(k) ?: continue
                result[k] = parseSourceInfo(v)
            }
            Log.d(TAG, "parsed sources: ${result.keys}")
            result
        }.getOrDefault(emptyMap())
    }

    private fun parseSourceInfo(obj: JSONObject): LxSourceInfo {
        val name = obj.optString("name")
        val type = obj.optString("type")
        val actionsArr = obj.optJSONArray("actions")
        val qualitysArr = obj.optJSONArray("qualitys")
        return LxSourceInfo(
            name = name,
            type = type,
            actions = actionsArr?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList(),
            qualitys = qualitysArr?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun callDispatcher(
        c: QuickJSContext,
        action: String,
        source: String,
        info: Map<String, Any?>,
        timeoutMs: Long = 30000L
    ): Any? = withContext(qjsDispatcher) {
        val startTime = System.currentTimeMillis()
        try {
            val infoJson = JSONObject(info).toString()
            val global = c.globalObject
            global.setProperty("__px_arg_action", action)
            global.setProperty("__px_arg_source", source)
            global.setProperty("__px_arg_info", infoJson)

            Log.d(TAG, "callDispatcher $action/$source infoJson len=${infoJson.length}")

            val callCode = "__pixelplay_call_js(__px_arg_action, __px_arg_source, JSON.parse(__px_arg_info));"
            c.evaluate(callCode, "call")

            val deadline = System.currentTimeMillis() + timeoutMs
            var doneInt: Int
            var loopCount = 0
            while (System.currentTimeMillis() < deadline) {
                loopCount++
                val doneRaw = c.evaluate("__pixelplay_pending_done()", "done")
                doneInt = when (doneRaw) {
                    is Number -> doneRaw.toInt()
                    is String -> doneRaw.toIntOrNull() ?: 0
                    else -> 0
                }
                if (doneInt == 1) break
                runCatching { c.evaluate("null", "tick") }
                delay(20)
            }
            Log.d(TAG, "callDispatcher $action/$source done, loops=$loopCount, elapsed=${System.currentTimeMillis() - startTime}ms")

            val errMsg = c.evaluate("__pixelplay_pending_err()", "err")?.toString()
            if (errMsg != null) {
                Log.e(TAG, "callDispatcher $action/$source js error: $errMsg")
                return@withContext null
            }
            val json = c.evaluate("__pixelplay_pending_value()", "value")?.toString()
            Log.d(TAG, "callDispatcher $action/$source result len=${json?.length}, preview=${json?.take(200)}")
            if (json.isNullOrBlank()) return@withContext null
            val parsed = runCatching { JSONObject(json) }.getOrNull()
            if (parsed != null) return@withContext parsed
            json
        } catch (t: Throwable) {
            Log.e(TAG, "callDispatcher $action/$source failed", t)
            null
        }
    }

    private fun parseSearchResult(raw: Any?): LxSearchResult {
        val obj = when (raw) {
            null -> return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            is JSONObject -> raw
            is String -> {
                val s = raw.trim()
                if (s.startsWith("{")) runCatching { JSONObject(s) }.getOrNull() else null
            }
            else -> null
        } ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)

        val listArr = obj.optJSONArray("list")
        val total = obj.optInt("total", 0)
        val isEnd = obj.optBoolean("isEnd", listArr == null || listArr.length() < 30)
        val list = ArrayList<LxSongInfo>(listArr?.length() ?: 0)
        if (listArr != null) {
            for (i in 0 until listArr.length()) {
                val it = listArr.opt(i)
                list += parseSongInfoFromAny(it)
            }
        }
        return LxSearchResult(list = list, isEnd = isEnd, total = total)
    }

    private fun parseSongInfoFromAny(v: Any?): LxSongInfo {
        val obj = when (v) {
            null -> return LxSongInfo()
            is JSONObject -> v
            is String -> runCatching { JSONObject(v) }.getOrNull() ?: return LxSongInfo()
            else -> return LxSongInfo()
        }
        return LxSongInfo(
            id = obj.optString("id").ifBlank { obj.optString("vid") }.ifBlank { obj.optString("songmid") },
            songmid = obj.optString("songmid"),
            hash = obj.optString("hash"),
            name = obj.optString("name"),
            singer = runCatching {
                val s = obj.opt("singer")
                when (s) {
                    is String -> s
                    is JSONObject -> {
                        val names = s.optJSONArray("name")
                        if (names != null) (0 until names.length()).joinToString("、") { names.optString(it) } else s.optString("name")
                    }
                    else -> {
                        val artists = obj.optJSONArray("artists")
                        if (artists != null) {
                            (0 until artists.length()).joinToString("、") { i ->
                                val a = artists.optJSONObject(i)
                                a?.optString("name").orEmpty()
                            }
                        } else ""
                    }
                }
            }.getOrDefault(""),
            albumName = runCatching {
                val a = obj.opt("album")
                when (a) {
                    is String -> a
                    is JSONObject -> a.optString("name")
                    else -> obj.optString("albumName")
                }
            }.getOrDefault(""),
            duration = runCatching { obj.optLong("duration") }.getOrDefault(0L),
            pic = runCatching {
                obj.optString("pic").ifBlank {
                    val al = obj.optJSONObject("al")
                    al?.optString("picUrl").orEmpty()
                }
            }.getOrDefault("")
        )
    }

    private fun extractVersion(js: String): String {
        val m = Regex("version\\s*[:=]\\s*['\"]?([0-9a-zA-Z._\\-]+)").find(js)
            ?: Regex("@version\\s+([\\d.]+)").find(js)
        return m?.groupValues?.getOrNull(1) ?: "custom"
    }

    private fun escapeJsString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>()
        val it = keys()
        while (it.hasNext()) {
            val k = it.next()
            m[k] = opt(k)
        }
        return m
    }

    companion object { private const val TAG = "LxJsEngine" }
}
