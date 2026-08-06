package com.theveloper.pixelplay.data.lx

import android.content.Context
import android.util.Base64
import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 落雪 userApi JS 引擎（支持同时加载多个 JS）。
 *
 * 对齐 lx-music-mobile 的 user-api-preload.js 协议：
 *  - 每个 JS 脚本运行在独立的 QuickJS 上下文中（多实例隔离，可同时加载）
 *  - lx.request 为异步非阻塞（脚本发请求 -> 原生异步 HTTP -> response 事件回传），
 *    因此脚本内部的 Promise.allSettled / 并发请求可以真正并行，不再串行阻塞 JS 线程
 *  - setTimeout / clearTimeout 由原生定时器驱动
 *  - 完整 utils.crypto（aesEncrypt / rsaEncrypt / md5 / randomBytes）与
 *    utils.buffer（from / bufToString）加密工具
 *  - EVENT_NAMES / version / env / currentScriptInfo 等字段
 *  - search / getPlayUrl 按音源路由到注册了该音源的具体脚本实例；
 *    多个脚本注册同名音源时按加载顺序逐个尝试，直到成功
 */
@Singleton
class LxJsEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val fileStore: LxFileStore,
) {
    /** 单个 JS 脚本的独立运行实例（QuickJS 上下文 + 音源 + 异步状态） */
    private class EngineInstance(
        val fileName: String,
        val ctx: QuickJSContext,
        val sources: Map<String, LxSourceInfo>,
        val scriptInfo: LxScriptInfo,
        val versionName: String,
        val pendingCalls: ConcurrentHashMap<String, CompletableDeferred<String>>,
        val scriptRequests: ConcurrentHashMap<String, Job>,
        val timeoutIds: ConcurrentHashMap<Long, ScheduledFuture<*>>,
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val qjsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val timerExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "lx-timer").apply { isDaemon = true }
    }

    @Volatile private var loaderInited = false
    /** 引擎是否至少成功加载了一个脚本 */
    @Volatile private var inited: Boolean = false
    /** 已加载的脚本实例：fileName -> EngineInstance */
    @Volatile private var instances: Map<String, EngineInstance> = emptyMap()
    /** 音源 -> 注册该音源的 JS 文件名列表（按加载顺序） */
    @Volatile private var sourceIndex: Map<String, List<String>> = emptyMap()
    /** 聚合后的音源信息（多脚本同名源后者覆盖，仅用于 UI 展示） */
    @Volatile private var sources: Map<String, LxSourceInfo> = emptyMap()
    /** 当前存活的所有 QuickJS 上下文（销毁时移除，避免对已销毁上下文求值） */
    private val liveCtxs = ConcurrentHashMap.newKeySet<QuickJSContext>()
    /** 当前正在执行的 JS 上下文（用于原生回调定位所属实例） */
    private val currentCtx = ThreadLocal<QuickJSContext>()

    @Volatile var lastError: String? = null
        private set

    private val callCounter = AtomicLong(0)

    // ── 生命周期 ──────────────────────────────────────────────────────────

    suspend fun ready(): Boolean = mutex.withLock {
        lastError = null
        if (inited && instances.isNotEmpty()) return@withLock true
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

        val files = fileStore.listFiles()
        if (files.isEmpty()) {
            lastError = "没有导入 JS 文件"
            return@withLock false
        }

        val loaded = LinkedHashMap<String, EngineInstance>()
        val index = LinkedHashMap<String, MutableList<String>>()
        val errors = mutableListOf<String>()
        var anySuccess = false

        for (file in files) {
            val js = fileStore.content(file)
            if (js.isNullOrBlank()) {
                errors += "${file.name}: 文件为空或读取失败"
                continue
            }
            val c = runCatching {
                withContext(qjsDispatcher) { QuickJSContext.create() }
            }.getOrNull()
            if (c == null) {
                errors += "${file.name}: QuickJS native create 失败(设备 ABI 可能不支持)"
                continue
            }
            liveCtxs.add(c)

            val srcs = runCatching {
                withContext(qjsDispatcher) {
                    injectLxShim(c)
                    val evalRes = runCatching { evalCtx(c, js, file.name) }
                    if (evalRes.isFailure) {
                        val t = evalRes.exceptionOrNull()!!
                        Log.e(TAG, "${file.name} eval FAILED", t)
                        lastError = "${file.name}: JS 解析/执行失败: ${t.javaClass.simpleName}: ${t.message}"
                        emptyMap()
                    } else {
                        Log.d(TAG, "${file.name} eval OK")
                        repeat(20) { runCatching { evalCtx(c, "null", "tick$it") } }
                        // 允许 init 阶段用 setTimeout 异步注册音源的脚本完成初始化（最多等 2s）
                        val deadline = System.currentTimeMillis() + 2000
                        while (System.currentTimeMillis() < deadline) {
                            val done = evalCtx(c, "__pixelplay_get_done_js()", "done")
                            if (done is Number && done.toInt() == 1) break
                            runCatching { evalCtx(c, "null", "tick") }
                            delay(10)
                        }
                        readSourcesFromJs(c)
                    }
                }
            }.getOrNull() ?: emptyMap()

            if (srcs.isEmpty()) {
                runCatching { withContext(qjsDispatcher) { c.destroy() } }
                liveCtxs.remove(c)
                errors += "${file.name}: 初始化了但没有注册任何音源，或执行失败"
                continue
            }

            loaded[file.name] = EngineInstance(
                fileName = file.name,
                ctx = c,
                sources = srcs,
                scriptInfo = parseScriptInfo(js, file.name),
                versionName = extractVersion(js),
                pendingCalls = ConcurrentHashMap(),
                scriptRequests = ConcurrentHashMap(),
                timeoutIds = ConcurrentHashMap(),
            )
            anySuccess = true
            srcs.keys.forEach { index.getOrPut(it) { mutableListOf() }.add(file.name) }
            Log.d(TAG, "${file.name} sources=${srcs.keys}")
        }

        instances = loaded
        sourceIndex = index.mapValues { it.value.toList() }
        sources = LinkedHashMap<String, LxSourceInfo>().apply {
            loaded.values.forEach { putAll(it.sources) }
        }
        inited = anySuccess
        if (!anySuccess) {
            lastError = errors.joinToString("；").ifBlank { "JS 加载失败" }
        } else if (errors.isNotEmpty()) {
            Log.w(TAG, "部分 JS 加载失败: ${errors.joinToString("；")}")
        }
        anySuccess
    }

    fun isReady(): Boolean = inited && instances.isNotEmpty()
    fun getSources(): Map<String, LxSourceInfo> = sources

    /** 聚合版本显示：单脚本返回脚本名/版本，多脚本用 " + " 连接 */
    fun versionName(): String {
        if (instances.isEmpty()) return "none"
        val infos = instances.values.map { it.scriptInfo }
        val names = infos.map { it.name }.filter { it.isNotBlank() }.distinct()
        return if (names.size > 1) names.joinToString(" + ")
        else names.firstOrNull()
            ?: infos.map { it.version }.filter { it.isNotBlank() }.distinct().joinToString(" + ").ifBlank { "custom" }
    }

    fun close() {
        runCatching {
            instances.values.forEach { inst ->
                inst.pendingCalls.values.forEach { it.completeExceptionally(CancellationException("engine closed")) }
                inst.scriptRequests.values.forEach { it.cancel() }
                inst.timeoutIds.values.forEach { it.cancel(false) }
            }
            val ctxs = instances.values.map { it.ctx }
            runBlocking(qjsDispatcher) {
                ctxs.forEach { c ->
                    runCatching { c.destroy() }
                    liveCtxs.remove(c)
                }
            }
        }
        instances = emptyMap()
        sourceIndex = emptyMap()
        sources = emptyMap()
        inited = false
    }

    suspend fun reload(): Boolean {
        close()
        return ready()
    }

    // ── 多 JS 管理 API ────────────────────────────────────────────────────

    /** 所有已导入脚本的头部简介（未成功加载的脚本也解析其磁盘内容） */
    suspend fun scriptInfos(): List<LxScriptInfo> = withContext(Dispatchers.IO) {
        fileStore.listFiles().map { f ->
            instances[f.name]?.scriptInfo ?: run {
                val js = fileStore.content(f)
                if (js == null) LxScriptInfo(fileName = f.name, name = f.name)
                else parseScriptInfo(js, f.name)
            }
        }
    }

    /** 已成功加载的脚本文件名列表 */
    fun loadedFileNames(): List<String> = instances.keys.toList()

    /** 指定脚本注册的音源（未加载时返回空） */
    fun instanceSources(fileName: String): Map<String, LxSourceInfo> =
        instances[fileName]?.sources ?: emptyMap()

    // ── 对外 API ──────────────────────────────────────────────────────────

    suspend fun search(keyword: String, source: String, page: Int = 1, pagesize: Int = 30): LxSearchResult {
        ensureReady()
        val info = mapOf(
            "keyword" to keyword,
            "page" to page,
            "pagesize" to pagesize,
            "type" to "music"
        )
        val targets = sourceIndex[source] ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        for (fileName in targets) {
            val inst = instances[fileName] ?: continue
            val raw = withContext(qjsDispatcher) { callJs(inst, "musicSearch", source, info, timeoutMs = 30000) }
            val result = parseSearchResult(raw)
            if (result.list.isNotEmpty()) return result
        }
        return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
    }

    suspend fun getPlayUrl(source: String, songInfo: Map<String, Any?>, quality: String = "128k"): String? {
        ensureReady()
        val info = mapOf("musicInfo" to songInfo, "type" to quality)
        val targets = sourceIndex[source] ?: return null
        val startTime = System.currentTimeMillis()
        for (fileName in targets) {
            val inst = instances[fileName] ?: continue
            val raw = try {
                withContext(qjsDispatcher) { callJs(inst, "musicUrl", source, info, timeoutMs = 60000) }
            } catch (t: Throwable) {
                Log.e(TAG, "getPlayUrl $source failed (${inst.fileName})", t)
                null
            }
            val result = processUrlResult(raw)
            if (result != null) {
                Log.d(TAG, "getPlayUrl $source/$quality via ${inst.fileName} -> ${result.take(120)} (${System.currentTimeMillis() - startTime}ms)")
                return result
            }
        }
        Log.d(TAG, "getPlayUrl $source/$quality all scripts failed")
        return null
    }

    private fun ensureReady() {
        if (!inited || instances.isEmpty()) throw IllegalStateException("LxJsEngine not ready. Call ready() first.")
    }

    // ── 引擎调用（非阻塞：evaluate 触发后挂起等待，JS 线程空闲可处理其它请求） ──

    private suspend fun callJs(
        inst: EngineInstance,
        action: String,
        source: String,
        info: Map<String, Any?>,
        timeoutMs: Long
    ): Any? {
        val callId = "c${System.nanoTime()}_${callCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<String>()
        inst.pendingCalls[callId] = deferred
        try {
            val infoJson = JSONObject(info).toString()
            withCtx(inst.ctx) {
                val global = inst.ctx.globalObject
                global.setProperty("__px_arg_action", action)
                global.setProperty("__px_arg_source", source)
                global.setProperty("__px_arg_info", infoJson)
                inst.ctx.evaluate(
                    "__pixelplay_call_js(${jsStr(callId)}, __px_arg_action, __px_arg_source, JSON.parse(__px_arg_info));",
                    "call"
                )
            }
            val raw = try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "callJs $action/$source timeout after ${timeoutMs}ms")
                null
            }
            if (raw == null) return null
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
            if (obj.has("error")) {
                Log.e(TAG, "callJs $action/$source JS error: ${obj.optString("error")}")
                return null
            }
            return obj.opt("value")
        } finally {
            inst.pendingCalls.remove(callId)
        }
    }

    private fun processUrlResult(raw: Any?): String? {
        if (raw == null) return null
        return when (raw) {
            is JSONObject -> raw.optString("url").takeIf { it.isNotBlank() }
                ?: raw.optString("data").takeIf { it.isNotBlank() }
                ?: raw.optString("src").takeIf { it.isNotBlank() }
            is JSONArray -> (0 until raw.length()).asSequence()
                .map { raw.optString(it) }
                .firstOrNull { it.isNotBlank() }
            is String -> {
                val s = raw.trim()
                when {
                    s.startsWith('"') && s.endsWith('"') && s.length >= 2 -> s.substring(1, s.length - 1)
                    s.startsWith("{") -> runCatching { JSONObject(s) }
                        .map { it.optString("url").takeIf { u -> u.isNotBlank() } }
                        .getOrNull() ?: s
                    s.startsWith("[") -> runCatching { JSONArray(s) }
                        .map { arr -> (0 until arr.length()).asSequence().map { arr.optString(it) }.firstOrNull { it.isNotBlank() } }
                        .getOrNull() ?: s
                    else -> s
                }
            }
            else -> raw.toString()
        }
    }

    // ── 原生注入与 shim ───────────────────────────────────────────────────

    /** 在指定上下文上求值，并确保该上下文是"当前上下文"（原生回调据此定位实例） */
    private fun evalCtx(c: QuickJSContext, script: String, name: String): Any? {
        return withCtx(c) {
            c.evaluate(script, name)
        }
    }

    private fun <T> withCtx(c: QuickJSContext, block: () -> T): T {
        val prev = currentCtx.get()
        currentCtx.set(c)
        try {
            return block()
        } finally {
            currentCtx.set(prev)
        }
    }

    private fun instanceOf(c: QuickJSContext?): EngineInstance? {
        if (c == null) return null
        return instances.values.firstOrNull { it.ctx === c }
    }

    private fun injectLxShim(c: QuickJSContext) {
        val global = c.globalObject

        // 脚本 -> 原生 通用调用（request / cancelRequest）
        global.setProperty("__pixelplay_native_call__", JSCallFunction { args ->
            val action = args.getOrNull(0) as? String ?: return@JSCallFunction null
            val dataJson = args.getOrNull(1) as? String
            val c2 = currentCtx.get()
            when (action) {
                "request" -> if (c2 != null) handleScriptRequest(c2, dataJson)
                "cancelRequest" -> {
                    val key = dataJson ?: return@JSCallFunction null
                    instanceOf(c2)?.scriptRequests?.remove(key)?.cancel()
                }
            }
            null
        })

        // 原生定时器：setTimeout / clearTimeout
        global.setProperty("__pixelplay_set_timeout", JSCallFunction { args ->
            val id = (args.getOrNull(0) as? Number)?.toLong() ?: return@JSCallFunction null
            val ms = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            val owner = currentCtx.get()
            val future = timerExecutor.schedule({
                if (owner == null || owner !in liveCtxs) return@schedule
                scope.launch(qjsDispatcher) {
                    if (owner !in liveCtxs) return@launch
                    runCatching { evalCtx(owner, "__pixelplay_fire_timeout($id)", "timeout") }
                }
            }, ms.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            instanceOf(owner)?.timeoutIds?.put(id, future)
            null
        })
        global.setProperty("__pixelplay_clear_timeout", JSCallFunction { args ->
            val id = (args.getOrNull(0) as? Number)?.toLong() ?: return@JSCallFunction null
            instanceOf(currentCtx.get())?.timeoutIds?.remove(id)?.cancel(false)
            null
        })
        global.setProperty("__pixelplay_cancel_request", JSCallFunction { args ->
            val key = args.getOrNull(0) as? String ?: return@JSCallFunction null
            instanceOf(currentCtx.get())?.scriptRequests?.remove(key)?.cancel()
            null
        })

        // 加密工具：Base64 / MD5 / AES / RSA
        global.setProperty("__pixelplay_str2b64", JSCallFunction { args ->
            try {
                val s = args.getOrNull(0) as? String ?: ""
                Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            } catch (t: Throwable) { "" }
        })
        global.setProperty("__pixelplay_b642buf", JSCallFunction { args ->
            try {
                val b64 = args.getOrNull(0) as? String ?: ""
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                val arr = JSONArray()
                for (b in bytes) arr.put(b.toInt())
                arr.toString()
            } catch (t: Throwable) { "[]" }
        })
        global.setProperty("__pixelplay_md5", JSCallFunction { args ->
            try {
                // 对齐参考项目：先 URL 解码（encodeURIComponent 的逆操作），再取 MD5
                val encoded = args.getOrNull(0) as? String ?: ""
                val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
                val digest = MessageDigest.getInstance("MD5").digest(decoded.toByteArray(Charsets.UTF_8))
                digest.joinToString("") { String.format("%02x", it) }
            } catch (t: Throwable) { "" }
        })
        global.setProperty("__pixelplay_aes_encrypt", JSCallFunction { args ->
            try {
                val data = Base64.decode(args.getOrNull(0) as? String ?: "", Base64.NO_WRAP)
                val key = Base64.decode(args.getOrNull(1) as? String ?: "", Base64.NO_WRAP)
                val ivStr = args.getOrNull(2) as? String ?: ""
                val mode = args.getOrNull(3) as? String ?: ""
                val cipher = if (mode == "CBC_128_PKCS7Padding") {
                    Cipher.getInstance("AES/CBC/PKCS7Padding")
                } else {
                    Cipher.getInstance("AES") // ECB 模式（对齐参考项目 AES_MODE.ECB_128_NoPadding = "AES"）
                }
                if (mode == "CBC_128_PKCS7Padding") {
                    val ivRaw = Base64.decode(ivStr, Base64.NO_WRAP)
                    val iv = ByteArray(16)
                    System.arraycopy(ivRaw, 0, iv, 0, minOf(ivRaw.size, 16))
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                } else {
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
                }
                Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
            } catch (t: Throwable) {
                Log.w(TAG, "aes_encrypt failed: ${t.message}")
                ""
            }
        })
        global.setProperty("__pixelplay_rsa_encrypt", JSCallFunction { args ->
            try {
                val data = Base64.decode(args.getOrNull(0) as? String ?: "", Base64.NO_WRAP)
                val key = (args.getOrNull(1) as? String ?: "").trim()
                val padding = args.getOrNull(2) as? String ?: ""
                val mode = if (padding == "OAEPWithSHA1AndMGF1Padding") {
                    "RSA/ECB/OAEPWithSHA1AndMGF1Padding"
                } else {
                    "RSA/ECB/NoPadding"
                }
                val keySpec = X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT))
                val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
                val cipher = Cipher.getInstance(mode)
                cipher.init(Cipher.ENCRYPT_MODE, publicKey)
                Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
            } catch (t: Throwable) {
                Log.w(TAG, "rsa_encrypt failed: ${t.message}")
                ""
            }
        })

        // JS 侧异步结果回传（__pixelplay_pending_response -> 这里完成 deferred）
        global.setProperty("__pixelplay_native_pending_response", JSCallFunction { args ->
            val callId = args.getOrNull(0) as? String
            val json = args.getOrNull(1) as? String
            if (callId != null) {
                instanceOf(currentCtx.get())?.pendingCalls?.remove(callId)?.complete(json ?: "null")
            }
            null
        })

        // 日志
        global.setProperty("__pixelplay_console_log", JSCallFunction { args ->
            try { Log.d("LxJs", args.joinToString(" ")) } catch (_: Throwable) {}
            null
        })
        global.setProperty("__pixelplay_console_err", JSCallFunction { args ->
            try { Log.e("LxJs", args.joinToString(" ")) } catch (_: Throwable) {}
            null
        })

        try { evalCtx(c, shim, "pixelplay-shim.js") } catch (t: Throwable) { Log.e(TAG, "shim eval", t) }
    }

    // ── 脚本 HTTP 请求处理（原生侧异步执行，完成后回传 JS） ────────────────

    private fun handleScriptRequest(c: QuickJSContext, dataJson: String?) {
        if (dataJson == null) return
        val data = runCatching { JSONObject(dataJson) }.getOrNull() ?: return
        val requestKey = data.optString("requestKey")
        if (requestKey.isBlank()) return
        val url = data.optString("url")
        val options = data.optJSONObject("options")
        val method = options?.optString("method") ?: "GET"
        val timeout = options?.optLong("timeout") ?: 0L
        val binary = options?.optBoolean("binary") ?: false

        val job = scope.launch(Dispatchers.IO) {
            try {
                val (headers, body, form) = buildScriptOptions(options)
                val resp = LxHttpFetcher.request(
                    url = url,
                    method = method,
                    headers = headers,
                    body = body,
                    form = form,
                    timeoutMs = if (timeout > 0) timeout else 15000L
                )
                deliverScriptResponse(c, requestKey, resp, binary)
            } catch (t: Throwable) {
                deliverScriptError(c, requestKey, t.message ?: t.javaClass.simpleName)
            }
        }
        instanceOf(c)?.scriptRequests?.put(requestKey, job)
    }

    /** 对齐参考项目 request.js 的 handleRequestData：form/JSON body/Content-Type 处理 */
    private fun buildScriptOptions(options: JSONObject?): Triple<Map<String, String>, Any?, Map<String, String>?> {
        val headers = LinkedHashMap<String, String>()
        options?.optJSONObject("headers")?.let { h ->
            val it = h.keys()
            while (it.hasNext()) {
                val k = it.next()
                headers[k] = h.optString(k)
            }
        }
        var body: Any? = options?.opt("body")
        var form: Map<String, String>? = null
        val method = options?.optString("method", "get")?.lowercase() ?: "get"
        val hasContentType = headers.keys.any { it.equals("Content-Type", ignoreCase = true) }

        if (method == "post" && !hasContentType) {
            val formObj = options?.optJSONObject("form")
            val formDataObj = options?.optJSONObject("formData")
            when {
                formObj != null -> {
                    headers["Content-Type"] = "application/x-www-form-urlencoded"
                    val m = LinkedHashMap<String, String>()
                    val it = formObj.keys()
                    while (it.hasNext()) { val k = it.next(); m[k] = formObj.optString(k) }
                    form = m
                    body = null
                }
                formDataObj != null -> {
                    headers["Content-Type"] = "multipart/form-data"
                    body = formDataObj.toString()
                }
                else -> {
                    headers["Content-Type"] = "application/json"
                }
            }
        }
        body = when {
            body == null -> null
            body is String -> body
            body is JSONObject -> body.toString()
            body is JSONArray -> body.toString()
            else -> body.toString()
        }
        return Triple(headers, body, form)
    }

    private fun deliverScriptResponse(c: QuickJSContext, requestKey: String, resp: LxHttpResponse, binary: Boolean) {
        val payload = JSONObject()
        payload.put("requestKey", requestKey)
        payload.put("error", JSONObject.NULL)
        val r = JSONObject()
        r.put("statusCode", resp.statusCode)
        r.put("statusMessage", resp.statusMessage)
        r.put("headers", JSONObject(resp.headers))
        r.put("url", resp.url)
        r.put("ok", resp.ok)
        // 对齐参考项目 fetchData：非 binary 时尝试把 body 解析为 JSON
        val parsedBody = if (!binary) tryParseJsonBody(resp.body) else resp.body
        r.put("body", parsedBody ?: JSONObject.NULL)
        payload.put("response", r)
        deliverToJs(c, "__pixelplay_http_response(${jsStr(payload.toString())});")
    }

    private fun deliverScriptError(c: QuickJSContext, requestKey: String, message: String) {
        val payload = JSONObject()
        payload.put("requestKey", requestKey)
        payload.put("error", message)
        payload.put("response", JSONObject.NULL)
        deliverToJs(c, "__pixelplay_http_response(${jsStr(payload.toString())});")
    }

    /** 模拟 JSON.parse：成功返回解析后的值，失败保留原始字符串 */
    private fun tryParseJsonBody(body: String): Any? {
        if (body.isBlank()) return null
        return try {
            JSONTokener(body).nextValue()
        } catch (t: Throwable) {
            body
        }
    }

    private fun deliverToJs(c: QuickJSContext, jsCode: String) {
        scope.launch(qjsDispatcher) {
            if (c !in liveCtxs) return@launch
            runCatching { evalCtx(c, jsCode, "native_event") }
                .onFailure { Log.w(TAG, "deliverToJs failed: ${it.message}") }
        }
    }

    // ── 音源信息解析 ──────────────────────────────────────────────────────

    private fun readSourcesFromJs(c: QuickJSContext): Map<String, LxSourceInfo> {
        return runCatching {
            val rawJson = evalCtx(c, "__pixelplay_get_inited_js()", "get_inited")?.toString()
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

    // ── 搜索结果解析 ──────────────────────────────────────────────────────

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

    /** 解析 JS 头部 /*! ... */ 简介块：@name / @description / @version / @author / @homepage / @lastUpdate / @md5 */
    private fun parseScriptInfo(js: String, fileName: String): LxScriptInfo {
        val block = Regex("/\\*!([\\s\\S]*?)\\*/").find(js)?.groupValues?.getOrNull(1)
            ?: Regex("/\\*([\\s\\S]*?)\\*/").find(js)?.groupValues?.getOrNull(1)
            ?: ""
        val fields = linkedMapOf<String, String>()
        Regex("@([\\w\\-]+)\\s+([^@\\r\\n*]+)").findAll(block).forEach { m ->
            val k = m.groupValues[1]
            val v = m.groupValues[2].trim()
            if (v.isNotEmpty()) fields[k] = v
        }
        fun field(key: String): String = fields[key].orEmpty()
        val name = field("name").ifBlank {
            Regex("""name\s*[:=]\s*['"]([^'"]+)['"]""").find(js)?.groupValues?.getOrNull(1).orEmpty()
        }
        return LxScriptInfo(
            fileName = fileName,
            name = name,
            description = field("description"),
            version = field("version"),
            author = field("author"),
            homepage = field("homepage"),
            lastUpdate = field("lastUpdate"),
            md5 = field("md5")
        )
    }

    private fun jsStr(s: String): String = JSONObject.quote(s)

    companion object { private const val TAG = "LxJsEngine" }

    // ── shim：对齐 lx-music-mobile 的 user-api-preload.js ─────────────────

    private val shim: String = """
    (function () {
        var _initedJson = null;
        var _initedDone = false;

        // ── console ──
        function _pxStr(a) {
            if (a === null) return 'null';
            if (a === undefined) return 'undefined';
            if (typeof a === 'object') {
                try { return JSON.stringify(a); } catch (e) { return String(a); }
            }
            return String(a);
        }
        function _pxLog() {
            try { __pixelplay_console_log(Array.prototype.slice.call(arguments).map(_pxStr).join(' ')); } catch (e) {}
        }
        function _pxErr() {
            try { __pixelplay_console_err(Array.prototype.slice.call(arguments).map(_pxStr).join(' ')); } catch (e) {}
        }
        try {
            if (!globalThis.console) globalThis.console = {};
            console.log = _pxLog;
            console.error = _pxErr;
            console.warn = _pxLog;
            console.info = _pxLog;
            console.debug = _pxLog;
        } catch (e) {}

        // ── Promise.allSettled polyfill（部分 QuickJS 版本缺失） ──
        if (typeof Promise.allSettled !== 'function') {
            Promise.allSettled = function (iterable) {
                var promises = Array.prototype.slice.call(iterable);
                return new Promise(function (resolve) {
                    if (promises.length === 0) { resolve([]); return; }
                    var results = new Array(promises.length);
                    var remaining = promises.length;
                    for (var i = 0; i < promises.length; i++) {
                        (function (idx, p) {
                            var wrapped = (p && typeof p.then === 'function') ? p : Promise.resolve(p);
                            wrapped.then(function (value) {
                                results[idx] = { status: 'fulfilled', value: value };
                                if (--remaining === 0) resolve(results);
                            }, function (reason) {
                                results[idx] = { status: 'rejected', reason: reason };
                                if (--remaining === 0) resolve(results);
                            });
                        })(i, promises[i]);
                    }
                });
            };
        }

        // ── setTimeout / clearTimeout（原生定时器） ──
        var _timeoutCallbacks = new Map();
        var _timeoutId = 0;
        function _setTimeout(callback, timeout) {
            if (typeof callback !== 'function') throw new Error('callback required a function');
            var ms = Number(timeout) || 0;
            if (ms < 0) ms = 0;
            var id = _timeoutId++;
            _timeoutCallbacks.set(id, { callback: callback, params: Array.prototype.slice.call(arguments, 2) });
            __pixelplay_set_timeout(id, Math.floor(ms));
            return id;
        }
        function _clearTimeout(id) {
            _timeoutCallbacks.delete(id);
            try { __pixelplay_clear_timeout(id); } catch (e) {}
        }
        globalThis.setTimeout = _setTimeout;
        globalThis.clearTimeout = _clearTimeout;
        globalThis.__pixelplay_fire_timeout = function (id) {
            var target = _timeoutCallbacks.get(id);
            if (!target) return;
            _timeoutCallbacks.delete(id);
            target.callback.apply(null, target.params);
        };

        // ── 请求队列（脚本 -> 原生 异步 HTTP） ──
        var _requestQueue = new Map();
        function _sendNativeRequest(url, options, callback) {
            var requestKey = 'r' + Date.now() + '_' + Math.random().toString().substring(2);
            var requestInfo = {
                aborted: false,
                abort: function () {
                    try { __pixelplay_cancel_request(requestKey); } catch (e) {}
                }
            };
            _requestQueue.set(requestKey, { callback: callback, requestInfo: requestInfo });
            try {
                __pixelplay_native_call__('request', JSON.stringify({ requestKey: requestKey, url: url, options: options || {} }));
            } catch (e) {
                _requestQueue.delete(requestKey);
                callback(new Error('native request failed: ' + (e && e.message ? e.message : e)), null);
            }
            return requestInfo;
        }
        globalThis.__pixelplay_http_response = function (jsonStr) {
            var data;
            try { data = JSON.parse(jsonStr); } catch (e) { return; }
            var target = _requestQueue.get(data.requestKey);
            if (!target) return;
            _requestQueue.delete(data.requestKey);
            target.requestInfo.aborted = true;
            if (data.error == null && data.response != null) target.callback(null, data.response);
            else target.callback(new Error(data.error || 'request failed'), null);
        };

        // ── 字节 <-> 字符串 ──
        function bytesToString(bytes) {
            var result = '';
            var i = 0;
            while (i < bytes.length) {
                var byte = bytes[i];
                if (byte < 128) {
                    result += String.fromCharCode(byte);
                    i++;
                } else if (byte >= 192 && byte < 224) {
                    result += String.fromCharCode(((byte & 31) << 6) | (bytes[i + 1] & 63));
                    i += 2;
                } else {
                    result += String.fromCharCode(((byte & 15) << 12) | ((bytes[i + 1] & 63) << 6) | (bytes[i + 2] & 63));
                    i += 3;
                }
            }
            return result;
        }
        function stringToBytes(str) {
            var bytes = [];
            for (var i = 0; i < str.length; i++) {
                var charCode = str.charCodeAt(i);
                if (charCode < 128) {
                    bytes.push(charCode);
                } else if (charCode < 2048) {
                    bytes.push((charCode >> 6) | 192);
                    bytes.push((charCode & 63) | 128);
                } else {
                    bytes.push((charCode >> 12) | 224);
                    bytes.push(((charCode >> 6) & 63) | 128);
                    bytes.push((charCode & 63) | 128);
                }
            }
            return bytes;
        }
        function dataToB64(data) {
            if (typeof data === 'string') return __pixelplay_str2b64(data);
            if (Array.isArray(data) || ArrayBuffer.isView(data)) return utils.buffer.bufToString(data, 'base64');
            throw new Error('data type error: ' + typeof data);
        }

        // ── utils（crypto / buffer） ──
        var utils = {
            crypto: {
                aesEncrypt: function (buffer, mode, key, iv) {
                    switch (mode) {
                        case 'aes-128-cbc':
                            return utils.buffer.from(__pixelplay_aes_encrypt(dataToB64(buffer), dataToB64(key), dataToB64(iv), 'CBC_128_PKCS7Padding'), 'base64');
                        case 'aes-128-ecb':
                            return utils.buffer.from(__pixelplay_aes_encrypt(dataToB64(buffer), dataToB64(key), '', 'ECB_128_NoPadding'), 'base64');
                        default:
                            throw new Error('Binary encoding is not supported for input strings');
                    }
                },
                rsaEncrypt: function (buffer, key) {
                    if (typeof key !== 'string') throw new Error('Invalid RSA key');
                    key = key.replace('-----BEGIN PUBLIC KEY-----', '')
                        .replace('-----END PUBLIC KEY-----', '');
                    return utils.buffer.from(__pixelplay_rsa_encrypt(dataToB64(buffer), key, 'NoPadding'), 'base64');
                },
                randomBytes: function (size) {
                    var byteArray = new Uint8Array(size);
                    for (var i = 0; i < size; i++) byteArray[i] = Math.floor(Math.random() * 256);
                    return byteArray;
                },
                md5: function (str) {
                    if (typeof str !== 'string') throw new Error('param required a string');
                    return __pixelplay_md5(encodeURIComponent(str));
                }
            },
            buffer: {
                from: function (input, encoding) {
                    if (typeof input === 'string') {
                        switch (encoding) {
                            case 'binary':
                                throw new Error('Binary encoding is not supported for input strings');
                            case 'base64':
                                return new Uint8Array(JSON.parse(__pixelplay_b642buf(input)));
                            case 'hex':
                                return new Uint8Array(input.match(/.{1,2}/g).map(function (b) { return parseInt(b, 16); }));
                            default:
                                return new Uint8Array(stringToBytes(input));
                        }
                    } else if (Array.isArray(input)) {
                        return new Uint8Array(input);
                    } else {
                        throw new Error('Unsupported input type: ' + input + ' encoding: ' + encoding);
                    }
                },
                bufToString: function (buf, format) {
                    if (Array.isArray(buf) || ArrayBuffer.isView(buf)) {
                        switch (format) {
                            case 'binary':
                                return buf;
                            case 'hex':
                                return new Uint8Array(buf).reduce(function (str, byte) { return str + byte.toString(16).padStart(2, '0'); }, '');
                            case 'base64':
                                return __pixelplay_str2b64(bytesToString(Array.from(buf)));
                            case 'utf8':
                            case 'utf-8':
                            default:
                                return bytesToString(Array.from(buf));
                        }
                    } else {
                        throw new Error('Input is not a valid buffer: ' + buf + ' format: ' + format);
                    }
                }
            }
        };

        // ── lx 对象 ──
        var EVENT_NAMES = { request: 'request', inited: 'inited', updateAlert: 'updateAlert' };
        var _requestHandler = null;
        var lx = {
            EVENT_NAMES: EVENT_NAMES,
            request: function (url, options, callback) {
                var opts = options || {};
                var sendOpts = { method: opts.method || 'get', binary: opts.binary === true };
                if (typeof opts.timeout === 'number' && opts.timeout > 0) sendOpts.timeout = Math.min(opts.timeout, 60000);
                if (opts.headers && typeof opts.headers === 'object') sendOpts.headers = opts.headers;
                if (opts.body != null) sendOpts.body = opts.body;
                if (opts.form) sendOpts.form = opts.form;
                if (opts.formData != null) sendOpts.formData = opts.formData;
                var requestInfo = _sendNativeRequest(url, sendOpts, function (err, resp) {
                    if (err) callback(err, null, null);
                    else callback(err, resp, resp && resp.body != null ? resp.body : null);
                });
                return function () {
                    if (!requestInfo.aborted) requestInfo.abort();
                    requestInfo = null;
                };
            },
            send: function (eventName, data) {
                return new Promise(function (resolve, reject) {
                    if (eventName === EVENT_NAMES.inited) {
                        try { _initedJson = JSON.stringify(data || { sources: {} }); }
                        catch (e) { _initedJson = JSON.stringify({ error: String(e && e.message ? e.message : e) }); }
                        _initedDone = true;
                        resolve();
                    } else if (eventName === EVENT_NAMES.updateAlert) {
                        reject(new Error('updateAlert is not supported'));
                    } else {
                        reject(new Error('The event is not supported: ' + eventName));
                    }
                });
            },
            on: function (eventName, handler) {
                if (eventName === EVENT_NAMES.request) {
                    _requestHandler = handler;
                    return Promise.resolve();
                }
                return Promise.reject(new Error('The event is not supported: ' + eventName));
            },
            utils: utils,
            currentScriptInfo: {
                name: '',
                description: '',
                version: '',
                author: '',
                homepage: '',
                rawScript: ''
            },
            version: '2.0.0',
            env: 'mobile'
        };
        globalThis.lx = lx;

        // ── 引擎调用入口（Kotlin 侧触发，callId 唯一标识一次调用） ──
        globalThis.__pixelplay_call_js = function (callId, action, source, info) {
            var h = _requestHandler;
            if (!h) {
                globalThis.__pixelplay_native_pending_response(callId, JSON.stringify({ error: 'no request handler registered' }));
                return;
            }
            try {
                var reqData = { action: action, source: source, info: info };
                var res = h(reqData);
                if (res && typeof res.then === 'function') {
                    res.then(function (v) {
                        var json;
                        try { json = JSON.stringify({ value: v }); }
                        catch (e) { json = JSON.stringify({ error: String(e && e.message ? e.message : e) }); }
                        globalThis.__pixelplay_native_pending_response(callId, json);
                    }, function (e) {
                        globalThis.__pixelplay_native_pending_response(callId, JSON.stringify({ error: e ? (e.message || String(e)) : 'rejected' }));
                    });
                    return;
                }
                globalThis.__pixelplay_native_pending_response(callId, JSON.stringify({ value: res }));
            } catch (e) {
                globalThis.__pixelplay_native_pending_response(callId, JSON.stringify({ error: e ? (e.message || String(e)) : 'thrown' }));
            }
        };
        globalThis.__pixelplay_pending_response = function (callId, json) {
            globalThis.__pixelplay_native_pending_response(callId, json);
        };
        globalThis.__pixelplay_get_inited_js = function () { return _initedJson; };
        globalThis.__pixelplay_get_done_js = function () { return _initedDone ? 1 : 0; };

        // ── 安全加固（冻结 lx，禁用 eval） ──
        try {
            (function freezeObject(obj) {
                if (typeof obj !== 'object') return;
                Object.freeze(obj);
                for (var k in obj) {
                    try { freezeObject(obj[k]); } catch (e) {}
                }
            })(lx);
            globalThis.eval = function () { throw new Error('eval is not available'); };
        } catch (e) {}
    })();
    """.trimIndent()
}
