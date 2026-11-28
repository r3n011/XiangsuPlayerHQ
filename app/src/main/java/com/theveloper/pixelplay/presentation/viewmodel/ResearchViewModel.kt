package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.research.ResearchLegalDocument
import com.theveloper.pixelplay.data.research.ResearchLegalSummary
import com.theveloper.pixelplay.data.research.ResearchNoticePage
import com.theveloper.pixelplay.data.research.ResearchNoticeSummary
import com.theveloper.pixelplay.data.research.ResearchRepository
import com.theveloper.pixelplay.data.research.ResearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 听歌数据研究计划 · 活动页 / 协议页 / 公告页共用 ViewModel。
 */
@HiltViewModel
class ResearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ResearchRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<ResearchUiState> = repository.uiState

    val wifiOnlyFlow: StateFlow<Boolean> =
        userPreferencesRepository.researchWifiOnlyFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _notices = MutableStateFlow<ResearchNoticePage?>(null)
    val notices: StateFlow<ResearchNoticePage?> = _notices.asStateFlow()

    private val _noticesLoading = MutableStateFlow(false)
    val noticesLoading: StateFlow<Boolean> = _noticesLoading.asStateFlow()

    private val _noticesOffline = MutableStateFlow(false)
    val noticesOffline: StateFlow<Boolean> = _noticesOffline.asStateFlow()

    private val _legalAgreement = MutableStateFlow<ResearchLegalDocument?>(null)
    val legalAgreement: StateFlow<ResearchLegalDocument?> = _legalAgreement.asStateFlow()

    private val _legalPrivacy = MutableStateFlow<ResearchLegalDocument?>(null)
    val legalPrivacy: StateFlow<ResearchLegalDocument?> = _legalPrivacy.asStateFlow()

    private val _legalLoading = MutableStateFlow(false)
    val legalLoading: StateFlow<Boolean> = _legalLoading.asStateFlow()

    /** 协议全文加载失败（网络异常且无本地缓存）：界面显示重试入口 */
    private val _legalLoadFailed = MutableStateFlow(false)
    val legalLoadFailed: StateFlow<Boolean> = _legalLoadFailed.asStateFlow()

    /** 屏幕诊断行：实时显示协议拉取状态（加载中 / 已加载字数 / 失败原因） */
    private val _legalDiag = MutableStateFlow("")
    val legalDiag: StateFlow<String> = _legalDiag.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch { repository.refresh() }

    /** 进入协议页：拉两份全文（网络优先 / 缓存兜底）。任何异常都落到失败态，绝不静默空白 */
    fun loadLegalDocuments() = viewModelScope.launch {
        _legalLoading.value = true
        _legalLoadFailed.value = false
        try {
            _legalDiag.value = "正在拉取协议摘要…"
            repository.loadLegalSummary()
            _legalDiag.value = "拉取活动用户协议…"
            _legalAgreement.value = repository.loadLegalDocument("agreement")
            _legalDiag.value = "拉取隐私条款…（协议 ${_legalAgreement.value?.content?.length ?: 0} 字）"
            _legalPrivacy.value = repository.loadLegalDocument("privacy")
            val failed = _legalAgreement.value == null || _legalPrivacy.value == null
            _legalLoadFailed.value = failed
            val sources = repository.lastLegalSource.value
            _legalDiag.value = if (failed) {
                "加载失败（网络异常且无本地缓存）"
            } else {
                "已加载：协议 ${_legalAgreement.value?.content?.length ?: 0} 字（${sources["agreement"]}）/ " +
                    "隐私 ${_legalPrivacy.value?.content?.length ?: 0} 字（${sources["privacy"]}）"
            }
            Timber.tag("ResearchVM").d("legal diag: %s", _legalDiag.value)
        } catch (t: Throwable) {
            Timber.tag("ResearchVM").w(t, "loadLegalDocuments unexpected error")
            _legalLoadFailed.value = true
            _legalDiag.value = "加载异常：" + (t.message ?: t.javaClass.simpleName)
        } finally {
            _legalLoading.value = false
        }
    }

    /** 同意并注册；返回是否成功（失败时 uiState.uploadError 有提示，协议页保持打开） */
    fun consentAndEnroll(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = repository.consentAndEnroll()
        onResult(ok)
    }

    fun uploadNow(onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val ok = repository.uploadNow()
        onResult(ok)
    }

    fun setAutoDaily(enabled: Boolean) = viewModelScope.launch { repository.setAutoDaily(enabled) }

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { repository.setWifiOnly(enabled) }

    fun setPlaintextEnabled(enabled: Boolean) =
        viewModelScope.launch { repository.setPlaintextEnabled(enabled) }

    fun withdraw(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repository.withdraw())
    }

    fun exportRecords(uri: android.net.Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        runCatching {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(repository.exportRawJson().toByteArray(Charsets.UTF_8))
            } ?: error("cannot open output")
        }.onSuccess { onResult(true) }.onFailure { onResult(false) }
    }

    /** 公告列表：首页 / 加载更多 */
    fun loadNotices(cursor: String? = null) = viewModelScope.launch {
        _noticesLoading.value = true
        val page = repository.loadNoticesPage(cursor)
        if (cursor == null) {
            _notices.value = page
        } else {
            val current = _notices.value
            _notices.value = current?.copy(
                notices = current.notices + page.notices,
                nextCursor = page.nextCursor,
                total = page.total
            )
        }
        _noticesOffline.value = page.notices.isEmpty() && cursor == null
        _noticesLoading.value = false
    }

    fun markNoticeRead(notice: ResearchNoticeSummary) =
        viewModelScope.launch { repository.markNoticeRead(notice.id) }
}
