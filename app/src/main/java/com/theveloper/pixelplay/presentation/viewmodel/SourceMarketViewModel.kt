package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.github.GitHubRelease
import com.theveloper.pixelplay.data.lx.LxFileStore
import com.theveloper.pixelplay.data.lx.LxJsEngine
import com.theveloper.pixelplay.data.lx.LxScriptInfo
import com.theveloper.pixelplay.data.sourcemarket.MarketJsEntry
import com.theveloper.pixelplay.data.sourcemarket.SourceMarketRepository
import com.theveloper.pixelplay.data.sourcemarket.ZipDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** 市场页中一个可安装的 js 条目（含解析出的头部信息与已安装状态） */
data class MarketJsEntryUi(
    val entry: MarketJsEntry,
    val name: String,
    val version: String,
    val author: String,
    val installed: Boolean
)

sealed interface ReleaseState {
    data object Idle : ReleaseState
    data class Downloading(val progress: Float) : ReleaseState
    data object Inspecting : ReleaseState
    data class Inspected(val entries: List<MarketJsEntryUi>) : ReleaseState
    data class Error(val message: String) : ReleaseState
}

data class SourceMarketUiState(
    val releases: List<GitHubRelease> = emptyList(),
    val loadingReleases: Boolean = false,
    val releasesError: String? = null,
    val expandedTag: String? = null,
    val releaseStates: Map<String, ReleaseState> = emptyMap(),
    val installingEntry: String? = null,   // 正在安装的条目文件名
    val installError: String? = null,
    val installSuccess: String? = null
)

@HiltViewModel
class SourceMarketViewModel @Inject constructor(
    private val repo: SourceMarketRepository,
    private val fileStore: LxFileStore,
    private val engine: LxJsEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceMarketUiState())
    val uiState: StateFlow<SourceMarketUiState> = _uiState.asStateFlow()

    init {
        loadReleases()
    }

    fun loadReleases() {
        if (_uiState.value.loadingReleases) return
        _uiState.update { it.copy(loadingReleases = true, releasesError = null) }
        viewModelScope.launch {
            repo.fetchReleases().onSuccess { releases ->
                _uiState.update { it.copy(releases = releases, loadingReleases = false) }
            }.onFailure { t ->
                Timber.e(t, "SourceMarket: loadReleases failed")
                _uiState.update {
                    it.copy(loadingReleases = false, releasesError = t.message ?: "加载失败")
                }
            }
        }
    }

    fun toggleExpand(tag: String) {
        val current = _uiState.value
        if (current.expandedTag == tag) {
            _uiState.update { it.copy(expandedTag = null) }
            return
        }
        _uiState.update { it.copy(expandedTag = tag) }

        val release = current.releases.firstOrNull { it.tag_name == tag } ?: return
        val zipAsset = repo.zipAssetOf(release) ?: run {
            _uiState.update {
                it.copy(releaseStates = it.releaseStates + (tag to ReleaseState.Error("该版本没有 zip 附件")))
            }
            return
        }

        viewModelScope.launch {
            // 缓存命中直接解析
            val cached = repo.cachedZipFile(tag)
            if (cached.exists() && cached.length() > 4) {
                inspectAndShow(tag)
                return@launch
            }
            // 否则流式下载
            repo.downloadZip(zipAsset, tag).collect { state ->
                when (state) {
                    is ZipDownloadState.Downloading ->
                        _uiState.update {
                            it.copy(releaseStates = it.releaseStates + (tag to ReleaseState.Downloading(state.progress)))
                        }
                    is ZipDownloadState.Downloaded -> inspectAndShow(tag)
                    is ZipDownloadState.Error ->
                        _uiState.update {
                            it.copy(releaseStates = it.releaseStates + (tag to ReleaseState.Error(state.message)))
                        }
                }
            }
        }
    }

    private suspend fun inspectAndShow(tag: String) {
        _uiState.update {
            it.copy(releaseStates = it.releaseStates + (tag to ReleaseState.Inspecting))
        }
        val cached = repo.cachedZipFile(tag)
        val entries = runCatching { repo.inspectZip(cached) }.getOrNull().orEmpty()
        if (entries.isEmpty()) {
            _uiState.update {
                it.copy(releaseStates = it.releaseStates + (tag to ReleaseState.Error("zip 中没有找到 JS 音源脚本")))
            }
            return
        }
        val installedNames = fileStore.listFiles().map { it.name }.toSet()
        val entriesUi = withContext(Dispatchers.IO) {
            entries.map { e ->
                val info = runCatching { engine.scriptInfoFromBytes(e.bytes, e.fileName) }
                    .getOrDefault(LxScriptInfo(fileName = e.fileName, name = e.fileName))
                MarketJsEntryUi(
                    entry = e,
                    name = info.name.ifBlank { e.fileName },
                    version = info.version,
                    author = info.author,
                    installed = e.fileName in installedNames
                )
            }
        }
        _uiState.update {
            it.copy(releaseStates = it.releaseStates + (tag to ReleaseState.Inspected(entriesUi)))
        }
    }

    fun installEntry(tag: String, entryUi: MarketJsEntryUi) {
        if (_uiState.value.installingEntry != null) return
        _uiState.update { it.copy(installingEntry = entryUi.entry.fileName, installError = null, installSuccess = null) }
        viewModelScope.launch {
            val written = fileStore.writeBytes(entryUi.entry.bytes, entryUi.entry.fileName)
            if (written == null) {
                _uiState.update {
                    it.copy(installingEntry = null, installError = "安装失败：无法写入音源目录")
                }
                return@launch
            }
            // 重载引擎使新脚本生效
            try {
                engine.reload()
            } catch (t: Throwable) {
                Timber.w(t, "SourceMarket: reload engine failed")
            }
            // 刷新已安装状态
            val currentState = _uiState.value
            val updatedEntries = (currentState.releaseStates[tag] as? ReleaseState.Inspected)
                ?.entries?.map { ui ->
                    if (ui.entry.fileName == entryUi.entry.fileName) ui.copy(installed = true) else ui
                }
            _uiState.update {
                it.copy(
                    installingEntry = null,
                    installSuccess = "已安装：${entryUi.name}",
                    releaseStates = if (updatedEntries != null) {
                        it.releaseStates + (tag to ReleaseState.Inspected(updatedEntries))
                    } else it.releaseStates
                )
            }
            // 更新 installedNames 无需全局维护，列表已按 Inspected.entries 渲染
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(installError = null, installSuccess = null) }
    }

    private fun MutableStateFlow<SourceMarketUiState>.update(block: (SourceMarketUiState) -> SourceMarketUiState) {
        this.value = block(this.value)
    }
}
