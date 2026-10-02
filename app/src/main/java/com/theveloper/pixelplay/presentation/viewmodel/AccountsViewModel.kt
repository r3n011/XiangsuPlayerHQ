package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.bilibili.BilibiliFavoritesSyncer
import com.theveloper.pixelplay.data.bilibili.BilibiliRepository
import com.theveloper.pixelplay.data.jellyfin.JellyfinRepository
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

enum class ExternalServiceAccount {
    TELEGRAM,
    GOOGLE_DRIVE,
    NETEASE,
    QQ_MUSIC,
    NAVIDROME,
    JELLYFIN,
    BILIBILI,

    /** 酷狗账号（扫码登录，用于内置酷狗音源的会员/无损） */
    KUGOU
}

data class ExternalAccountUiModel(
    val service: ExternalServiceAccount,
    val title: String,
    val accountLabel: String,
    val syncedContentLabel: String,
    val isLoggingOut: Boolean,
    val authCookie: String? = null,
    /** 头像 URL（目前只有酷狗用，账号页有头像时替代服务图标展示） */
    val avatarUrl: String? = null
)

data class AccountsUiState(
    val connectedAccounts: List<ExternalAccountUiModel> = emptyList(),
    val disconnectedServices: List<ExternalServiceAccount> = emptyList()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository,
    private val gDriveRepository: GDriveRepository,
    private val neteaseRepository: NeteaseRepository,
    private val qqMusicRepository: QqMusicRepository,
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val bilibiliRepository: BilibiliRepository,
    private val kugouRepository: com.theveloper.pixelplay.data.kugou.KugouRepository,
    private val kugouPlaylistSyncer: com.theveloper.pixelplay.data.kugou.KugouPlaylistSyncer,
    private val bilibiliFavoritesSyncer: BilibiliFavoritesSyncer
) : ViewModel() {

    private val loggingOutServices = MutableStateFlow<Set<ExternalServiceAccount>>(emptySet())

    /** 正在执行手动同步的第三方服务（用于按钮 loading 状态） */
    private val syncingServices = MutableStateFlow<Set<ExternalServiceAccount>>(emptySet())
    val syncingServicesFlow: StateFlow<Set<ExternalServiceAccount>> = syncingServices.asStateFlow()

    private val telegramStateFlow = combine(
        telegramRepository.authorizationState
            .map { it is TdApi.AuthorizationStateReady }
            .distinctUntilChanged(),
        musicRepository.getAllTelegramChannels().map { it.size }
    ) { connected, channelCount ->
        connected to channelCount
    }

    private val gDriveStateFlow = combine(
        gDriveRepository.isLoggedInFlow,
        gDriveRepository.getFolders().map { it.size }
    ) { connected, folderCount ->
        connected to folderCount
    }

    private val neteaseStateFlow = combine(
        neteaseRepository.isLoggedInFlow,
        neteaseRepository.getPlaylists().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val qqMusicStateFlow = combine(
        qqMusicRepository.isLoggedInFlow,
        qqMusicRepository.getPlaylists().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val navidromeStateFlow = combine(
        navidromeRepository.isLoggedInFlow,
        navidromeRepository.getPlaylists().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val jellyfinStateFlow = combine(
        jellyfinRepository.isLoggedInFlow,
        jellyfinRepository.getPlaylists().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val kugouStateFlow = combine(
        kugouRepository.isLoggedInFlow,
        kugouRepository.accountLabel,
        kugouRepository.accountAvatarUrl,
        kugouPlaylistSyncer.syncedPlaylistCount
    ) { connected, label, avatar, playlistCount ->
        KugouAccountState(
            connected = connected,
            label = label.orEmpty(),
            avatarUrl = avatar,
            playlistCount = playlistCount
        )
    }

    private data class KugouAccountState(
        val connected: Boolean,
        val label: String,
        val avatarUrl: String?,
        val playlistCount: Int
    )

    private val bilibiliStateFlow = bilibiliRepository.isLoggedInFlow
        .map { connected ->
            connected to (if (connected) bilibiliRepository.userNickname?.takeIf { it.isNotBlank() } ?: "" else "")
        }
        .distinctUntilChanged()

    val uiState: StateFlow<AccountsUiState> = combine(
        combine(
            listOf(
                telegramStateFlow,
                gDriveStateFlow,
                neteaseStateFlow,
                qqMusicStateFlow,
                navidromeStateFlow,
                jellyfinStateFlow,
                bilibiliStateFlow,
                kugouStateFlow
            )
        ) { it.toList() },
        loggingOutServices
    ) { states, activeLogouts ->
        val (telegramConnected, telegramChannelCount) = states[0] as Pair<Boolean, Int>
        val (gDriveConnected, gDriveFolderCount) = states[1] as Pair<Boolean, Int>
        val (neteaseConnected, neteasePlaylistCount) = states[2] as Pair<Boolean, Int>
        val (qqConnected, qqPlaylistCount) = states[3] as Pair<Boolean, Int>
        val (navidromeConnected, navidromePlaylistCount) = states[4] as Pair<Boolean, Int>
        val (jellyfinConnected, jellyfinPlaylistCount) = states[5] as Pair<Boolean, Int>
        val (bilibiliConnected, bilibiliNickname) = states[6] as Pair<Boolean, String>
        val kugouState = states[7] as KugouAccountState
        val kugouConnected = kugouState.connected
        val kugouLabel = kugouState.label
        val kugouAvatar = kugouState.avatarUrl
        val kugouPlaylistCount = kugouState.playlistCount

        val connectedAccounts = buildList {
            if (telegramConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.TELEGRAM,
                        title = "Telegram",
                        accountLabel = "Active Telegram session",
                        syncedContentLabel = formatCount(
                            count = telegramChannelCount,
                            singular = "synced channel",
                            plural = "synced channels"
                        ),
                        isLoggingOut = ExternalServiceAccount.TELEGRAM in activeLogouts
                    )
                )
            }
            if (gDriveConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.GOOGLE_DRIVE,
                        title = "Google Drive",
                        accountLabel = gDriveRepository.userDisplayName
                            ?.takeIf { it.isNotBlank() }
                            ?: gDriveRepository.userEmail
                                ?.takeIf { it.isNotBlank() }
                            ?: "Google account connected",
                        syncedContentLabel = formatCount(
                            count = gDriveFolderCount,
                            singular = "synced folder",
                            plural = "synced folders"
                        ),
                        isLoggingOut = ExternalServiceAccount.GOOGLE_DRIVE in activeLogouts
                    )
                )
            }
            if (neteaseConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.NETEASE,
                        title = "Netease Music",
                        accountLabel = neteaseRepository.userNickname
                            ?.takeIf { it.isNotBlank() }
                            ?: "Netease account connected",
                        syncedContentLabel = formatCount(
                            count = neteasePlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.NETEASE in activeLogouts,
                        authCookie = neteaseRepository.getCookieString().takeIf { it.isNotBlank() }
                    )
                )
            }
            if (qqConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.QQ_MUSIC,
                        title = "QQ Music",
                        accountLabel = qqMusicRepository.userNickname
                            ?.takeIf { it.isNotBlank() }
                            ?: "QQ Music account connected",
                        syncedContentLabel = formatCount(
                            count = qqPlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.QQ_MUSIC in activeLogouts
                    )
                )
            }
            if (navidromeConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.NAVIDROME,
                        title = "Subsonic",
                        accountLabel = navidromeRepository.username
                            ?.takeIf { it.isNotBlank() }
                            ?: "Subsonic account connected",
                        syncedContentLabel = formatCount(
                            count = navidromePlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.NAVIDROME in activeLogouts
                    )
                )
            }
            if (jellyfinConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.JELLYFIN,
                        title = "Jellyfin",
                        accountLabel = jellyfinRepository.username
                            ?.takeIf { it.isNotBlank() }
                            ?: "Jellyfin account connected",
                        syncedContentLabel = formatCount(
                            count = jellyfinPlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.JELLYFIN in activeLogouts
                    )
                )
            }
            if (bilibiliConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.BILIBILI,
                        title = "Bilibili",
                        accountLabel = bilibiliNickname.ifBlank { "Bilibili account connected" },
                        syncedContentLabel = "已同步 B 站收藏",
                        isLoggingOut = ExternalServiceAccount.BILIBILI in activeLogouts
                    )
                )
            }
            if (kugouConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.KUGOU,
                        title = "酷狗音乐",
                        accountLabel = kugouLabel.ifBlank { "酷狗账号已登录" },
                        syncedContentLabel = if (kugouPlaylistCount > 0) {
                            "已同步 $kugouPlaylistCount 个歌单"
                        } else {
                            "还没有同步歌单"
                        },
                        isLoggingOut = ExternalServiceAccount.KUGOU in activeLogouts,
                        avatarUrl = kugouAvatar
                    )
                )
            }
        }

        val disconnectedServices = buildList {
            if (!telegramConnected) add(ExternalServiceAccount.TELEGRAM)
            if (!gDriveConnected) add(ExternalServiceAccount.GOOGLE_DRIVE)
            if (!neteaseConnected) add(ExternalServiceAccount.NETEASE)
            if (!qqConnected) add(ExternalServiceAccount.QQ_MUSIC)
            if (!navidromeConnected) add(ExternalServiceAccount.NAVIDROME)
            if (!jellyfinConnected) add(ExternalServiceAccount.JELLYFIN)
            if (!bilibiliConnected) add(ExternalServiceAccount.BILIBILI)
            if (!kugouConnected) add(ExternalServiceAccount.KUGOU)
        }

        AccountsUiState(
            connectedAccounts = connectedAccounts,
            disconnectedServices = disconnectedServices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun logout(service: ExternalServiceAccount) {
        if (service in loggingOutServices.value) return

        viewModelScope.launch {
            loggingOutServices.update { it + service }
            try {
                runCatching {
                    when (service) {
                        ExternalServiceAccount.TELEGRAM -> {
                            telegramRepository.logout()
                            telegramRepository.clearMemoryCache()
                            musicRepository.clearTelegramData()
                        }
                        ExternalServiceAccount.GOOGLE_DRIVE -> gDriveRepository.logout()
                        ExternalServiceAccount.NETEASE -> neteaseRepository.logout()
                        ExternalServiceAccount.QQ_MUSIC -> qqMusicRepository.logout()
                        ExternalServiceAccount.NAVIDROME -> navidromeRepository.logout()
                        ExternalServiceAccount.JELLYFIN -> jellyfinRepository.logout()
                        ExternalServiceAccount.BILIBILI -> bilibiliRepository.logout()
                        ExternalServiceAccount.KUGOU -> {
                            kugouRepository.logout()
                            // 退出后立即清掉本地同步出来的酷狗歌单（syncer 未登录分支会清理）
                            kugouPlaylistSyncer.syncNow()
                        }
                    }
                }
            } finally {
                loggingOutServices.update { it - service }
            }
        }
    }

    /**
     * 酷狗凭证是否真的存在（直接读本地凭证，不依赖内存 Flow）。
     *
     * 内存 Flow 与磁盘凭证理论上一致，但凭证存储偶发不可用（加密库创建失败换用明文库）
     * 时会出现「凭证在、Flow 还是 false」的错位，表现为卡片显示未连接、点开变登录页。
     */
    fun isKugouLoggedIn(): Boolean = kugouRepository.isLoggedIn

    /** 按本地凭证重新恢复酷狗登录态（账号页点击前自愈一次，Flow 会随之纠正）。 */
    fun restoreKugouSession() = kugouRepository.restoreSession()

    private fun formatCount(count: Int, singular: String, plural: String): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }

    /**
     * 手动同步指定第三方账户的数据到媒体库（第三方账户管理页「立即同步」按钮）。
     * B 站走 [BilibiliFavoritesSyncer.syncNow]（不受节流限制），其余走各仓库的全量同步。
     */
    fun manualSync(service: ExternalServiceAccount) {
        if (service in syncingServices.value) return
        viewModelScope.launch {
            syncingServices.update { it + service }
            try {
                runCatching {
                    when (service) {
                        ExternalServiceAccount.BILIBILI -> bilibiliFavoritesSyncer.syncNow()
                        ExternalServiceAccount.KUGOU -> kugouPlaylistSyncer.syncNow()
                        ExternalServiceAccount.NETEASE -> neteaseRepository.autoSyncOnLibraryEntry()
                        ExternalServiceAccount.QQ_MUSIC -> qqMusicRepository.autoSyncOnLibraryEntry()
                        ExternalServiceAccount.NAVIDROME -> navidromeRepository.syncAllPlaylistsAndSongs()
                        ExternalServiceAccount.JELLYFIN -> jellyfinRepository.syncAllPlaylistsAndSongs()
                        else -> Unit
                    }
                }
            } finally {
                syncingServices.update { it - service }
            }
        }
    }

    /**
     * 进入媒体库时自动同步 B 站收藏（模仿网易云 autoSyncOnLibraryEntry，内部 1 小时节流）。
     */
    fun autoSyncBilibili() {
        viewModelScope.launch {
            runCatching { bilibiliFavoritesSyncer.sync() }
        }
    }

    /**
     * 进入媒体库时自动同步酷狗歌单（KugouPlaylistSyncer 内部 1 小时节流 + 登录检查）。
     */
    fun autoSyncKugou() {
        viewModelScope.launch {
            runCatching { kugouPlaylistSyncer.sync() }
        }
    }
}
