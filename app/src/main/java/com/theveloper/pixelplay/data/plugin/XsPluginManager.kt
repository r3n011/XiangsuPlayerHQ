package com.theveloper.pixelplay.data.plugin

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件中心总入口：商店 / 下载 / 安装 / 启用 / 指令 / 回执。
 */
@Singleton
class XsPluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paths: XsPluginPaths,
    private val registry: XsPluginRegistry,
    private val store: XsStoreClient,
    private val importer: XsZipImporter,
    private val runtime: XsPluginRuntime,
    private val hostBridge: XsHostBridge,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _storePlugins = MutableStateFlow<List<XSStorePlugin>>(emptyList())
    val storePlugins: StateFlow<List<XSStorePlugin>> = _storePlugins.asStateFlow()

    private val _storeLoading = MutableStateFlow(false)
    val storeLoading: StateFlow<Boolean> = _storeLoading.asStateFlow()

    private val _storeError = MutableStateFlow<String?>(null)
    val storeError: StateFlow<String?> = _storeError.asStateFlow()

    private val _itemStates = MutableStateFlow<Map<String, XsItemState>>(emptyMap())
    val itemStates: StateFlow<Map<String, XsItemState>> = _itemStates.asStateFlow()

    private val _receipts = MutableStateFlow<Map<String, List<XSPluginEvent>>>(emptyMap())
    val receipts: StateFlow<Map<String, List<XSPluginEvent>>> = _receipts.asStateFlow()

    private val _storeSource = MutableStateFlow(store.baseUrl)
    val storeSource: StateFlow<String> = _storeSource.asStateFlow()

    val installed: StateFlow<List<XSPluginRecord>> = registry.plugins
    val storeAgreed: StateFlow<Boolean> = registry.storeAgreed

    init {
        scope.launch {
            hostBridge.events.collect { (pluginId, event) ->
                _receipts.update { current ->
                    val list = (current[pluginId].orEmpty() + event).takeLast(40)
                    current + (pluginId to list)
                }
            }
        }
        scope.launch {
            // 启动时拉起已启用插件（失败不阻塞）
            registry.plugins.value.filter { it.enabled }.forEach { record ->
                runCatching { runtime.start(record) }
            }
        }
    }

    // ── 商店 ────────────────────────────────────────────────────────────

    fun refreshStore(force: Boolean = false) {
        if (_storeLoading.value) return
        scope.launch {
            val started = System.currentTimeMillis()
            XsLog.i(XsLog.MANAGER, "refreshStore start (baseUrl=%s)", store.baseUrl)
            _storeLoading.value = true
            _storeError.value = null
            runCatching { store.fetchPlugins() }
                .onSuccess { list ->
                    _storePlugins.value = list
                    XsLog.i(
                        XsLog.MANAGER,
                        "refreshStore ok: %d plugins in %dms",
                        list.size,
                        System.currentTimeMillis() - started,
                    )
                    // 已安装的标记为 Installed
                    _itemStates.update { states ->
                        val next = states.toMutableMap()
                        list.forEach { plugin ->
                            val local = registry.get(plugin.id)
                            if (local != null && next[plugin.id] == null) {
                                next[plugin.id] = XsItemState.Installed(local.version)
                            }
                        }
                        next
                    }
                }
                .onFailure {
                    XsLog.w(XsLog.MANAGER, it, "refreshStore failed after %dms", System.currentTimeMillis() - started)
                    _storeError.value = it.message ?: "无法连接插件商店"
                }
            _storeLoading.value = false
        }
    }

    fun setStoreBaseUrl(url: String) {
        store.baseUrl = url
        _storeSource.value = store.baseUrl
        refreshStore(force = true)
    }

    fun setStoreAgreed(agreed: Boolean) = registry.setStoreAgreed(agreed)

    fun downloadAndInstall(plugin: XSStorePlugin) {
        if (_itemStates.value[plugin.id] is XsItemState.Downloading) return
        scope.launch {
            val started = System.currentTimeMillis()
            XsLog.i(
                XsLog.MANAGER,
                "install start: %s v%s (storeSize=%d, sha=%s)",
                plugin.id,
                plugin.version,
                plugin.size,
                plugin.sha256.take(12),
            )
            setItemState(plugin.id, XsItemState.Downloading(0f))
            var zip: File? = null
            try {
                if (!registry.storeAgreed.value) {
                    throw XsInstallException(XsErrors.PERMISSION_DENIED, "请先同意商店协议")
                }
                var lastLogged = -1
                val downloaded = store.download(plugin) { progress ->
                    setItemState(plugin.id, XsItemState.Downloading(progress))
                    val pct = (progress * 100).toInt() / 25 * 25
                    if (pct != lastLogged && pct > 0) {
                        lastLogged = pct
                        XsLog.d(XsLog.MANAGER, "download %s: %d%%", plugin.id, pct)
                    }
                }
                zip = downloaded
                XsLog.d(XsLog.MANAGER, "download done: %s -> %s (%d bytes)", plugin.id, downloaded.name, downloaded.length())
                setItemState(plugin.id, XsItemState.Verifying)
                val record = importer.import(downloaded, source = "store")
                installRecord(record, plugin)
                XsLog.i(
                    XsLog.MANAGER,
                    "install ok: %s v%s in %dms",
                    record.id,
                    record.version,
                    System.currentTimeMillis() - started,
                )
            } catch (e: Exception) {
                XsLog.e(XsLog.MANAGER, e, "install failed: %s after %dms", plugin.id, System.currentTimeMillis() - started)
                setItemState(plugin.id, XsItemState.Failed(e.message ?: "安装失败"))
            } finally {
                runCatching { zip?.delete() }
            }
        }
    }

    /** 本地 zip 导入（文件选择器 / 分享进入）。 */
    fun installFromUri(uri: Uri) {
        scope.launch {
            val temp = File(paths.downloadDir, "local-${System.currentTimeMillis()}.zip")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法读取文件")
                val record = importer.import(temp, source = "import")
                installRecord(record, null)
            }.onFailure {
                Timber.w(it, "import local plugin failed")
                _storeError.value = it.message ?: "导入失败"
            }
            runCatching { temp.delete() }
        }
    }

    private fun installRecord(record: XSPluginRecord, storePlugin: XSStorePlugin?) {
        val previous = registry.get(record.id)
        val merged = record.copy(
            enabled = previous?.enabled ?: false,
            agreed = previous?.agreed ?: false,
            installedAt = System.currentTimeMillis(),
        )
        registry.upsert(merged)
        XsLog.i(
            XsLog.MANAGER,
            "registry upsert: %s %s -> %s (enabled=%s, agreed=%s, source=%s)",
            record.id,
            previous?.version ?: "-",
            record.version,
            merged.enabled,
            merged.agreed,
            record.source,
        )
        _itemStates.update { it + (record.id to XsItemState.Installed(record.version)) }
        if (storePlugin != null && merged.enabled) {
            scope.launch {
                XsLog.i(XsLog.MANAGER, "restart enabled plugin after update: %s", record.id)
                runtime.stop(record.id)
                runtime.start(merged)
            }
        }
    }

    // ── 生命周期 ────────────────────────────────────────────────────────

    fun enable(pluginId: String) {
        scope.launch {
            val record = registry.get(pluginId) ?: return@launch
            XsLog.i(XsLog.MANAGER, "enable requested: %s v%s", pluginId, record.version)
            val started = runtime.start(record)
            XsLog.i(XsLog.MANAGER, "enable result: %s -> %s (%s)", pluginId, started, registry.get(pluginId)?.lastError ?: "no-error")
            registry.setEnabled(pluginId, started)
        }
    }

    fun disable(pluginId: String) {
        scope.launch {
            XsLog.i(XsLog.MANAGER, "disable requested: %s", pluginId)
            runtime.stop(pluginId)
            registry.setEnabled(pluginId, false)
        }
    }

    fun uninstall(pluginId: String) {
        scope.launch {
            XsLog.i(XsLog.MANAGER, "uninstall: %s", pluginId)
            runtime.stop(pluginId)
            val dir = paths.pluginDir(pluginId)
            val deleted = dir.deleteRecursively()
            XsLog.d(XsLog.MANAGER, "uninstall dir=%s deleted=%s", dir.absolutePath, deleted)
            clearPrivateData(pluginId)
            registry.remove(pluginId)
            _itemStates.update { it - pluginId }
            _receipts.update { it - pluginId }
        }
    }

    fun agree(pluginId: String) {
        registry.setAgreed(pluginId, true)
    }

    fun runAction(pluginId: String, actionId: String) {
        scope.launch {
            val record = registry.get(pluginId) ?: return@launch
            runtime.runAction(record, actionId)
        }
    }

    fun clearPrivateData(pluginId: String) {
        val prefs = context.getSharedPreferences("xs_plugin_kv", Context.MODE_PRIVATE)
        val prefix = "xs.plugin.$pluginId."
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
        }.apply()
        _receipts.update { it - pluginId }
    }

    fun readAgreement(pluginId: String): String? {
        val record = registry.get(pluginId) ?: return null
        val path = record.manifest.agreement ?: return null
        val file = paths.agreementFile(pluginId, path)
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun readStoreTerms(assetName: String): String? = runCatching {
        context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    private fun setItemState(pluginId: String, state: XsItemState) {
        _itemStates.update { it + (pluginId to state) }
    }
}
