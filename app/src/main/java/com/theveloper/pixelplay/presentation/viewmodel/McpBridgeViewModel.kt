package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.mcp.CompatibleClient
import com.theveloper.pixelplay.data.mcp.McpClientDiscovery
import com.theveloper.pixelplay.data.mcp.McpClientRecord
import com.theveloper.pixelplay.data.mcp.McpPairPayload
import com.theveloper.pixelplay.data.mcp.McpPairingLauncher
import com.theveloper.pixelplay.data.mcp.McpPairingManager
import com.theveloper.pixelplay.data.mcp.McpScopes
import com.theveloper.pixelplay.data.mcp.McpServerState
import com.theveloper.pixelplay.data.mcp.McpSessionManager
import com.theveloper.pixelplay.data.mcp.PendingPairing
import com.theveloper.pixelplay.data.preferences.McpPreferencesRepository
import com.theveloper.pixelplay.data.service.mcp.McpServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

data class McpBridgeUiState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val endpoint: String? = null,
    val port: Int = McpPreferencesRepository.DEFAULT_PORT,
    val availableClients: List<CompatibleClient> = emptyList(),
    val pairedClients: List<McpClientRecord> = emptyList(),
    val pendingInvite: McpPairPayload? = null,
    val pendingRequest: PendingPairing? = null,
    val message: String? = null,
)

@HiltViewModel
class McpBridgeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: McpPreferencesRepository,
    private val pairing: McpPairingManager,
    private val discovery: McpClientDiscovery,
    private val launcher: McpPairingLauncher,
    private val sessions: McpSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpBridgeUiState())
    val uiState: StateFlow<McpBridgeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.enabled, prefs.port) { enabled, port -> enabled to port }
                .collect { (enabled, port) ->
                    _uiState.value = _uiState.value.copy(enabled = enabled, port = port)
                }
        }
        viewModelScope.launch {
            McpServerState.running.collect { running ->
                _uiState.value = _uiState.value.copy(
                    running = running,
                    endpoint = if (running) McpServerState.baseUrl else null,
                )
            }
        }
        viewModelScope.launch {
            pairing.incomingInvite.collect { invite ->
                _uiState.value = _uiState.value.copy(pendingInvite = invite)
            }
        }
        viewModelScope.launch {
            pairing.pendingRequest.collect { request ->
                _uiState.value = _uiState.value.copy(pendingRequest = request)
            }
        }
        // Also consume deep-link invites here: the service may be stopped while the app
        // is open, and the settings page is where the user confirms.
        viewModelScope.launch {
            com.theveloper.pixelplay.data.mcp.McpPairingIntentBus.invites.collect {
                pairing.receiveInvite(it)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val clients = withContextIo { discovery.listCompatible() }
            val paired = runCatching { sessions.listClients() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                availableClients = clients,
                pairedClients = paired,
                endpoint = if (McpServerState.running.value) McpServerState.baseUrl else null,
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setEnabled(enabled)
            if (enabled) {
                McpServerService.start(context, _uiState.value.port)
                _uiState.value = _uiState.value.copy(message = "正在启动 AI 外接服务…")
            } else {
                McpServerService.stop(context)
                _uiState.value = _uiState.value.copy(message = "已关闭 AI 外接服务")
            }
        }
    }

    fun connect(client: CompatibleClient) {
        viewModelScope.launch {
            if (!McpServerState.running.value) {
                McpServerService.start(context, _uiState.value.port)
                // Wait until the port is actually bound so the payload carries the LAN endpoint.
                val ready = withTimeoutOrNull(3_000) { McpServerState.running.first { it } }
                if (ready != true) {
                    _uiState.value = _uiState.value.copy(message = "AI 外接服务启动失败，请重试")
                    return@launch
                }
            }
            val prepared = runCatching {
                pairing.preparePlayerInitiated(
                    clientId = client.packageName,
                    clientName = client.label,
                    scheme = client.scheme,
                    scopes = McpScopes.DEFAULT,
                )
            }.getOrElse {
                Timber.w(it, "MCP: prepare pairing failed")
                _uiState.value = _uiState.value.copy(message = "无法创建配对，请重试")
                return@launch
            }
            val launched = launcher.launchClient(client.packageName, client.scheme, prepared.payload)
            _uiState.value = _uiState.value.copy(
                message = if (launched) {
                    "已打开 ${client.label}，请在对方 App 中确认连接"
                } else {
                    "无法打开 ${client.label}，请确认已安装"
                },
            )
        }
    }

    fun approveInvite(granted: Set<String> = McpScopes.DEFAULT) {
        val invite = _uiState.value.pendingInvite ?: return
        val scheme = invite.client?.scheme.orEmpty()
        val payload = pairing.approveInvite(invite, granted)
        if (payload == null) {
            _uiState.value = _uiState.value.copy(message = "配对信息无效")
            return
        }
        val launched = scheme.isNotBlank() && launcher.launchCallback(scheme, payload)
        _uiState.value = _uiState.value.copy(
            pendingInvite = null,
            message = if (launched) {
                "已允许 ${invite.client?.name ?: "客户端"} 连接"
            } else {
                "已允许连接；请回到客户端完成配对"
            },
        )
        refresh()
    }

    fun dismissInvite() {
        pairing.dismissInvite()
        _uiState.value = _uiState.value.copy(pendingInvite = null)
    }

    fun approveRequest(granted: Set<String> = emptySet()) {
        val request = _uiState.value.pendingRequest ?: return
        pairing.approve(request.code, granted)
        _uiState.value = _uiState.value.copy(
            pendingRequest = null,
            message = "已允许 ${request.clientName} 连接",
        )
        refresh()
    }

    fun denyRequest() {
        val request = _uiState.value.pendingRequest ?: return
        pairing.deny(request.code)
        _uiState.value = _uiState.value.copy(
            pendingRequest = null,
            message = "已拒绝 ${request.clientName} 的连接请求",
        )
    }

    fun revoke(tokenId: String) {
        viewModelScope.launch {
            sessions.revoke(tokenId)
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }
}
