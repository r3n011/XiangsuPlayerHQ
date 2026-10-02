package com.theveloper.pixelplay.presentation.kugou.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.kugou.KugouPlaylistSyncer
import com.theveloper.pixelplay.data.kugou.KugouRepository
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 酷狗账号面板页（对齐网易云 / QQ音乐的面板页）：账号信息 + 已同步歌单 + 同步/刷新/退出。
 *
 * 数据来源：
 * - 账号信息（昵称/头像/userid）→ [KugouRepository]（登录成功后由签名接口补拉）
 * - 歌单 → [KugouPlaylistSyncer] 同步出来的本地播放列表（source = "KUGOU"）
 */
@HiltViewModel
class KugouDashboardViewModel @Inject constructor(
    private val kugouRepository: KugouRepository,
    private val kugouPlaylistSyncer: KugouPlaylistSyncer,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = kugouRepository.isLoggedInFlow
    val nickname: StateFlow<String?> = kugouRepository.accountNickname
    val avatarUrl: StateFlow<String?> = kugouRepository.accountAvatarUrl

    /** 登录账号的 userid（未登录为 null） */
    val userId: String? get() = kugouRepository.userId

    /** 已同步的酷狗歌单（本地播放列表，按最后修改时间倒序） */
    val playlists: StateFlow<List<Playlist>> = playlistPreferencesRepository.userPlaylistsFlow
        .map { list ->
            list.filter { it.source == "KUGOU" }.sortedByDescending { it.lastModified }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** 同步状态（面板顶部横幅用它显示结果，文案在 UI 层本地化） */
    private val _syncState = MutableStateFlow<KugouSyncUiState>(KugouSyncUiState.Idle)
    val syncState: StateFlow<KugouSyncUiState> = _syncState.asStateFlow()

    init {
        // 进面板时顺手刷新一次昵称/头像（缓存缺失或换过头像时能更新）
        viewModelScope.launch {
            runCatching { kugouRepository.refreshUserInfo() }
        }
    }

    /** 全量同步歌单（不受 1 小时节流限制）。 */
    fun syncNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncState.value = KugouSyncUiState.Running
            val ok = runCatching { kugouPlaylistSyncer.syncNow() }
                .onFailure { Timber.w(it, "KugouDashboard: syncNow failed") }
                .getOrDefault(false)
            _isSyncing.value = false
            _syncState.value = if (ok) {
                KugouSyncUiState.Success(kugouPlaylistSyncer.syncedPlaylistCount.value)
            } else {
                KugouSyncUiState.Failed
            }
        }
    }

    /** 手动刷新昵称/头像。 */
    fun refreshUserInfo() {
        viewModelScope.launch {
            runCatching { kugouRepository.refreshUserInfo() }
        }
    }

    fun clearSyncState() {
        _syncState.value = KugouSyncUiState.Idle
    }

    /** 退出登录：清凭证 + 立刻清掉本地同步出来的酷狗歌单。 */
    fun logout(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            kugouRepository.logout()
            runCatching { kugouPlaylistSyncer.syncNow() }
            onDone()
        }
    }
}

/** 酷狗面板同步状态。 */
sealed interface KugouSyncUiState {
    data object Idle : KugouSyncUiState
    data object Running : KugouSyncUiState
    data class Success(val playlistCount: Int) : KugouSyncUiState
    data object Failed : KugouSyncUiState
}
