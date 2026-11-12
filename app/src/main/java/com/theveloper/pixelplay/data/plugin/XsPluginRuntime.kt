package com.theveloper.pixelplay.data.plugin

import android.content.Context
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每插件一个独立 QuickJS 实例 + 独立执行线程 + `xs-runtime.js`。
 *
 * 稳定性约束（对齐文档 01 §4）：
 *  - 堆上限 32MB（setMemoryLimit），单次启动/钩子执行有超时兜底；
 *  - 每插件独立线程：一个插件死循环不会阻塞其它插件与宿主；
 *  - destroy 前置 destroyed 标记，避免在已销毁的上下文上回写 Promise 触发 native 崩溃。
 */
@Singleton
class XsPluginRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paths: XsPluginPaths,
    private val registry: XsPluginRegistry,
    private val hostBridge: XsHostBridge,
) {
    companion object {
        private const val MEMORY_LIMIT_BYTES = 32 * 1024 * 1024
        private const val START_TIMEOUT_MS = 8_000L
        private const val HOOK_TIMEOUT_MS = 5_000L
    }

    private class Instance(
        val pluginId: String,
        val record: XSPluginRecord,
        val ctx: QuickJSContext,
        val executor: ExecutorService,
        val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        val timerIds: ConcurrentHashMap<String, ScheduledFuture<*>> = ConcurrentHashMap(),
    ) {
        @Volatile var destroyed = false
        @Volatile var hung = false
    }

    private val timerExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "xs-timer").apply { isDaemon = true }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    @Volatile
    private var loaderInited = false

    private val instances = ConcurrentHashMap<String, Instance>()

    fun isRunning(pluginId: String): Boolean = instances[pluginId]?.let { !it.destroyed && !it.hung } == true

    fun runningIds(): Set<String> = instances.filterValues { !it.destroyed && !it.hung }.keys.toSet()

    fun lastError(pluginId: String): String? = registry.get(pluginId)?.lastError

    /** 启动插件脚本；失败写入 registry.lastError 并返回 false。 */
    suspend fun start(record: XSPluginRecord): Boolean = mutex.withLock {
        val startedAt = System.currentTimeMillis()
        val existing = instances[record.id]
        if (existing != null && !existing.destroyed && !existing.hung) return@withLock true
        if (existing != null) {
            XsLog.w(XsLog.RUNTIME, "start %s: replace dead instance (destroyed=%s hung=%s)", record.id, existing.destroyed, existing.hung)
            instances.remove(record.id)
            existing.destroyed = true
            runCatching { existing.executor.shutdownNow() }
        }
        XsLog.i(XsLog.RUNTIME, "start %s v%s", record.id, record.version)

        val mainFile = paths.mainScript(record.id)
        if (!mainFile.isFile) {
            registry.setLastError(record.id, "main.js 不存在，请重新安装")
            return@withLock false
        }
        val runtimeJs = readAsset("xs-runtime.js")
        if (runtimeJs.isNullOrBlank()) {
            registry.setLastError(record.id, "宿主缺少 xs-runtime.js")
            return@withLock false
        }
        val mainJs = runCatching { mainFile.readText(Charsets.UTF_8) }.getOrElse {
            registry.setLastError(record.id, "读取 main.js 失败：${it.message}")
            return@withLock false
        }

        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "xs-qjs-${record.id}").apply { isDaemon = true }
        }
        val dispatcher = executor.asCoroutineDispatcher()

        val instance = try {
            withTimeout(START_TIMEOUT_MS) {
                withContext(dispatcher) {
                    if (!loaderInited) {
                        QuickJSLoader.init()
                        loaderInited = true
                    }
                    val ctx = QuickJSContext.create()
                    runCatching {
                        ctx.setMemoryLimit(MEMORY_LIMIT_BYTES)
                        ctx.setEnableStackTrace(true)
                    }
                    val inst = Instance(record.id, record, ctx, executor, dispatcher)
                    installNatives(inst)
                    ctx.evaluate(XsBridgeScript.QUICKJS, "xs-bridge.js")
                    ctx.evaluate(runtimeJs, "xs-runtime.js")
                    ctx.evaluate(mainJs, "main.js")
                    inst
                }
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w(e, "plugin ${record.id} start timeout")
            registry.setLastError(record.id, "脚本启动超时（>${START_TIMEOUT_MS / 1000}s），已跳过")
            runCatching { executor.shutdownNow() }
            null
        } catch (e: Throwable) {
            Timber.e(e, "plugin ${record.id} eval failed")
            registry.setLastError(record.id, "脚本执行失败：${e.message}")
            runCatching { executor.shutdownNow() }
            null
        }

        if (instance == null) {
            XsLog.e(XsLog.RUNTIME, "start %s failed in %dms: %s", record.id, System.currentTimeMillis() - startedAt, registry.get(record.id)?.lastError ?: "unknown")
            return@withLock false
        }
        instances[record.id] = instance
        registry.setLastError(record.id, null)
        XsLog.i(
            XsLog.RUNTIME,
            "start %s ok in %dms (main.js=%d bytes, memory=%dKB)",
            record.id,
            System.currentTimeMillis() - startedAt,
            mainJs.length,
            runCatching { instance.ctx.getMemoryUsedSize() / 1024 }.getOrDefault(0L),
        )
        dispatchHookSafe(record.id, "enable", null)
        true
    }

    suspend fun stop(pluginId: String) {
        val instance = instances[pluginId] ?: return
        if (!instance.destroyed && !instance.hung) {
            dispatchHookSafe(pluginId, "disable", null)
        }
        instances.remove(pluginId)
        instance.destroyed = true
        instance.timerIds.values.forEach { runCatching { it.cancel(false) } }
        instance.timerIds.clear()
        delay(30)
        withTimeoutOrNull(1_500L) {
            withContext(instance.dispatcher) {
                runCatching { instance.ctx.destroy() }
            }
        }
        runCatching { instance.executor.shutdownNow() }
    }

    suspend fun runAction(record: XSPluginRecord, actionId: String) {
        if (instances[record.id] == null) start(record)
        dispatchHookSafe(record.id, "action", actionId)
    }

    private suspend fun dispatchHookSafe(pluginId: String, kind: String, arg: String?) {
        val instance = instances[pluginId] ?: return
        if (instance.destroyed || instance.hung) return
        val startedAt = System.currentTimeMillis()
        try {
            withTimeout(HOOK_TIMEOUT_MS) {
                withContext(instance.dispatcher) {
                    if (instance.destroyed || instance.hung) return@withContext
                    instance.ctx.globalObject.setProperty("__xs_arg_kind", kind)
                    instance.ctx.globalObject.setProperty("__xs_arg_value", arg ?: "")
                    instance.ctx.evaluate("__xsDispatchHook(__xs_arg_kind, __xs_arg_value)", "dispatch-$kind")
                }
            }
            XsLog.d(XsLog.RUNTIME, "hook %s.%s done in %dms", pluginId, kind, System.currentTimeMillis() - startedAt)
        } catch (e: TimeoutCancellationException) {
            XsLog.w(XsLog.RUNTIME, "hook %s.%s TIMEOUT after %dms (limit=%dms), marking hung", pluginId, kind, System.currentTimeMillis() - startedAt, HOOK_TIMEOUT_MS)
            instance.hung = true
            registry.setLastError(pluginId, "执行超时（>${HOOK_TIMEOUT_MS / 1000}s），已自动停用")
            registry.setEnabled(pluginId, false)
            instances.remove(pluginId)
            runCatching { instance.executor.shutdownNow() }
        } catch (e: Throwable) {
            XsLog.w(XsLog.RUNTIME, e, "hook %s.%s failed in %dms", pluginId, kind, System.currentTimeMillis() - startedAt)
            registry.setLastError(pluginId, "钩子执行失败：${e.message}")
        }
    }

    private fun installNatives(instance: Instance) {
        val ctx = instance.ctx
        val global = ctx.globalObject

        global.setProperty("__xsNativeInvoke", JSCallFunction { args ->
            val id = args.getOrNull(0)?.toString() ?: return@JSCallFunction null
            val module = args.getOrNull(1)?.toString().orEmpty()
            val method = args.getOrNull(2)?.toString().orEmpty()
            val argsJson = args.getOrNull(3)?.toString().orEmpty()
            scope.launch {
                val result = try {
                    hostBridge.invoke(instance.record, module, method, argsJson)
                } catch (e: Throwable) {
                    Timber.w(e, "host invoke $module.$method failed")
                    JSONObject().put("error", "host_error").toString()
                }
                deliver(instance, id, result)
            }
            null
        })

        global.setProperty("__xsNativeSetTimeout", JSCallFunction { args ->
            val id = args.getOrNull(0)?.toString() ?: return@JSCallFunction null
            val ms = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            if (instance.destroyed || instance.hung) return@JSCallFunction null
            val future = timerExecutor.schedule({
                if (instance.destroyed || instance.hung) return@schedule
                if (instances[instance.pluginId] !== instance) return@schedule
                scope.launch {
                    runCatching {
                        withContext(instance.dispatcher) {
                            if (instance.destroyed || instance.hung) return@withContext
                            instance.ctx.evaluate("__xsFireTimer(${JSONObject.quote(id)})", "timer")
                        }
                    }
                }
            }, ms.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            instance.timerIds[id]?.cancel(false)
            instance.timerIds[id] = future
            null
        })

        global.setProperty("__xsNativeClearTimeout", JSCallFunction { args ->
            val id = args.getOrNull(0)?.toString() ?: return@JSCallFunction null
            instance.timerIds.remove(id)?.cancel(false)
            null
        })

        global.setProperty("__xsNativeLog", JSCallFunction { args ->
            val level = args.getOrNull(0)?.toString().orEmpty()
            val message = args.getOrNull(1)?.toString().orEmpty().take(2000)
            when (level) {
                "error" -> Timber.e("[xs:%s] %s", instance.pluginId, message)
                "warn" -> Timber.w("[xs:%s] %s", instance.pluginId, message)
                else -> Timber.d("[xs:%s] %s", instance.pluginId, message)
            }
            null
        })
    }

    private suspend fun deliver(instance: Instance, id: String, resultJson: String) {
        if (instance.destroyed || instance.hung) return
        if (instances[instance.pluginId] !== instance) return
        runCatching {
            withContext(instance.dispatcher) {
                if (instance.destroyed || instance.hung) return@withContext
                instance.ctx.evaluate(
                    "__xsResolve(${JSONObject.quote(id)}, ${JSONObject.quote(resultJson)})",
                    "resolve",
                )
            }
        }.onFailure { Timber.w(it, "resolve to ${instance.pluginId} failed") }
    }

    private fun readAsset(name: String): String? = runCatching {
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()
}
