package com.theveloper.pixelplay.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.plugin.XSPluginRecord
import com.theveloper.pixelplay.data.plugin.XSPluginEvent
import com.theveloper.pixelplay.data.plugin.XSStorePlugin
import com.theveloper.pixelplay.data.plugin.XsItemState
import com.theveloper.pixelplay.data.plugin.XsManifestValidator
import com.theveloper.pixelplay.data.plugin.XsPluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PluginViewModel @Inject constructor(
    private val manager: XsPluginManager,
) : ViewModel() {

    val installed: StateFlow<List<XSPluginRecord>> = manager.installed
    val storePlugins: StateFlow<List<XSStorePlugin>> = manager.storePlugins
    val storeLoading: StateFlow<Boolean> = manager.storeLoading
    val storeError: StateFlow<String?> = manager.storeError
    val itemStates: StateFlow<Map<String, XsItemState>> = manager.itemStates
    val receipts: StateFlow<Map<String, List<XSPluginEvent>>> = manager.receipts
    val storeAgreed: StateFlow<Boolean> = manager.storeAgreed
    val storeSource: StateFlow<String> = manager.storeSource

    fun refreshStore() = manager.refreshStore()

    fun install(plugin: XSStorePlugin) = manager.downloadAndInstall(plugin)

    fun enable(pluginId: String) = manager.enable(pluginId)

    fun disable(pluginId: String) = manager.disable(pluginId)

    fun uninstall(pluginId: String) = manager.uninstall(pluginId)

    fun agree(pluginId: String) = manager.agree(pluginId)

    fun runAction(pluginId: String, actionId: String) = manager.runAction(pluginId, actionId)

    fun clearData(pluginId: String) = manager.clearPrivateData(pluginId)

    fun setStoreAgreed(agreed: Boolean) = manager.setStoreAgreed(agreed)

    fun installFromUri(uri: Uri) = manager.installFromUri(uri)

    fun agreementText(pluginId: String): String? = manager.readAgreement(pluginId)

    fun storeTerms(): String? = manager.readStoreTerms("plugin/terms.md")

    fun storePrivacy(): String? = manager.readStoreTerms("plugin/privacy.md")

    fun localVersion(pluginId: String): String? = installed.value.firstOrNull { it.id == pluginId }?.version

    fun hasUpdate(plugin: XSStorePlugin): Boolean {
        val local = localVersion(plugin.id) ?: return false
        return XsManifestValidator.compareVersion(plugin.version, local) > 0
    }
}
