package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.radio.RadioBrowserApi
import com.theveloper.pixelplay.data.radio.RadioCountry
import com.theveloper.pixelplay.data.radio.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class RadioUiState(
    val stations: List<RadioStation> = emptyList(),
    // 始终保留的热门电台列表（与当前列表模式无关），供播放器推荐区使用
    val topStations: List<RadioStation> = emptyList(),
    val countries: List<RadioCountry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val mode: RadioMode = RadioMode.TOP,
    val query: String = "",
    val countryCode: String? = null
)

enum class RadioMode { TOP, SEARCH, COUNTRY }

/**
 * 网络广播（radio-browser.info）ViewModel
 */
@HiltViewModel
class RadioViewModel @Inject constructor(
    private val api: RadioBrowserApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadTopStations()
        loadCountries()
    }

    /** 加载国家列表（用于国家筛选 chips），失败不阻塞主流程 */
    fun loadCountries() {
        viewModelScope.launch {
            try {
                val countries = api.getCountries(limit = 30)
                _uiState.value = _uiState.value.copy(countries = countries)
            } catch (e: Exception) {
                Timber.w(e, "RadioViewModel: 加载国家列表失败")
            }
        }
    }

    fun loadTopStations() {
        loadJob?.cancel()
        // 清除国家筛选状态，确保“热门”chip 恢复选中（选中条件为 countryCode == null）
        _uiState.value = _uiState.value.copy(
            loading = true, error = null, mode = RadioMode.TOP, query = "", countryCode = null
        )
        loadJob = viewModelScope.launch {
            try {
                val stations = api.getTopStations(limit = 60)
                _uiState.value = _uiState.value.copy(
                    stations = stations,
                    topStations = stations,
                    loading = false,
                    error = if (stations.isEmpty()) "无法连接到广播服务器" else null
                )
            } catch (e: Exception) {
                Timber.e(e, "RadioViewModel: 加载热门电台失败")
                _uiState.value = _uiState.value.copy(loading = false, error = "加载失败：${e.message}")
            }
        }
    }

    fun search(keyword: String) {
        loadJob?.cancel()
        val query = keyword.trim()
        _uiState.value = _uiState.value.copy(
            loading = true, error = null, mode = RadioMode.SEARCH,
            query = query, countryCode = null
        )
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(stations = emptyList(), loading = false)
            return
        }
        loadJob = viewModelScope.launch {
            try {
                val stations = api.searchStations(query, limit = 60)
                _uiState.value = _uiState.value.copy(
                    stations = stations,
                    loading = false,
                    error = if (stations.isEmpty()) "没有找到相关电台" else null
                )
            } catch (e: Exception) {
                Timber.e(e, "RadioViewModel: 搜索电台失败")
                _uiState.value = _uiState.value.copy(loading = false, error = "搜索失败：${e.message}")
            }
        }
    }

    fun loadCountry(countryCode: String, countryName: String? = null) {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(
            loading = true, error = null, mode = RadioMode.COUNTRY,
            query = countryName ?: countryCode, countryCode = countryCode
        )
        loadJob = viewModelScope.launch {
            try {
                val stations = api.getStationsByCountry(countryCode, limit = 60)
                _uiState.value = _uiState.value.copy(
                    stations = stations,
                    loading = false,
                    error = if (stations.isEmpty()) "该国家暂无电台" else null
                )
            } catch (e: Exception) {
                Timber.e(e, "RadioViewModel: 加载国家电台失败")
                _uiState.value = _uiState.value.copy(loading = false, error = "加载失败：${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** 播放电台时上报点击计数（后台执行，不阻塞 UI） */
    fun reportClick(stationUuid: String) {
        viewModelScope.launch {
            try {
                api.countClick(stationUuid)
            } catch (e: Exception) {
                Timber.w(e, "RadioViewModel: 点击计数上报失败")
            }
        }
    }
}
