package com.theveloper.pixelplay.data.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件注册表：安装/启用/协议同意状态持久化。
 *
 * 使用 SharedPreferences（插件数量少，读放大可忽略）；对外暴露 StateFlow 供 Compose 订阅。
 */
@Singleton
class XsPluginRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS = "xs_plugin_registry"
        private const val KEY_PLUGINS = "plugins"
        private const val KEY_STORE_AGREED = "store_agreed"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val lock = Any()

    private val _plugins = MutableStateFlow<List<XSPluginRecord>>(emptyList())
    val plugins: StateFlow<List<XSPluginRecord>> = _plugins.asStateFlow()

    private val _storeAgreed = MutableStateFlow(false)
    val storeAgreed: StateFlow<Boolean> = _storeAgreed.asStateFlow()

    init {
        synchronized(lock) {
            _plugins.value = load()
            _storeAgreed.value = prefs.getBoolean(KEY_STORE_AGREED, false)
            XsLog.i(XsLog.REGISTRY, "loaded %d installed plugins (storeAgreed=%s)", _plugins.value.size, _storeAgreed.value)
        }
    }

    fun get(pluginId: String): XSPluginRecord? = _plugins.value.firstOrNull { it.id == pluginId }

    fun upsert(record: XSPluginRecord) {
        synchronized(lock) {
            val list = _plugins.value.toMutableList()
            val index = list.indexOfFirst { it.id == record.id }
            if (index >= 0) list[index] = record else list.add(record)
            commit(list)
        }
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        update(pluginId) { it.copy(enabled = enabled) }
    }

    fun setAgreed(pluginId: String, agreed: Boolean) {
        update(pluginId) { it.copy(agreed = agreed) }
    }

    fun setLastError(pluginId: String, error: String?) {
        update(pluginId) { it.copy(lastError = error) }
    }

    fun remove(pluginId: String) {
        synchronized(lock) {
            commit(_plugins.value.filterNot { it.id == pluginId })
        }
    }

    fun setStoreAgreed(agreed: Boolean) {
        prefs.edit().putBoolean(KEY_STORE_AGREED, agreed).apply()
        _storeAgreed.value = agreed
    }

    private fun update(pluginId: String, block: (XSPluginRecord) -> XSPluginRecord) {
        synchronized(lock) {
            val list = _plugins.value.map { if (it.id == pluginId) block(it) else it }
            commit(list)
        }
    }

    private fun commit(list: List<XSPluginRecord>) {
        val normalized = list.sortedBy { it.manifest.name.lowercase() }
        _plugins.value = normalized
        XsLog.d(XsLog.REGISTRY, "commit %d plugins: %s", normalized.size, normalized.joinToString { "${it.id}@${it.version}" })
        runCatching {
            prefs.edit()
                .putString(KEY_PLUGINS, json.encodeToString(ListSerializer(XSPluginRecord.serializer()), normalized))
                .apply()
        }.onFailure { Timber.e(it, "persist plugin registry failed") }
    }

    private fun load(): List<XSPluginRecord> = runCatching {
        val raw = prefs.getString(KEY_PLUGINS, null) ?: return emptyList()
        json.decodeFromString(ListSerializer(XSPluginRecord.serializer()), raw)
    }.onFailure { Timber.e(it, "load plugin registry failed") }.getOrDefault(emptyList())
}
