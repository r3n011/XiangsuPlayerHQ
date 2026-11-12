package com.theveloper.pixelplay.data.mcp

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level state of the MCP bridge. Kept separate from WebRemoteServerService's
 * static fields on purpose: stopping one service must never change the other's UI state.
 */
object McpServerState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Volatile
    var port: Int = 8787
        private set

    @Volatile
    var baseUrl: String? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    val endpoint: String
        get() = baseUrl ?: "http://127.0.0.1:$port"

    fun markStarting(port: Int, host: String) {
        this.port = port
        this.baseUrl = "http://$host:$port"
        this.lastError = null
        _running.value = true
    }

    fun markStopped() {
        _running.value = false
        baseUrl = null
    }

    fun markFailed(message: String) {
        lastError = message
        _running.value = false
        baseUrl = null
    }
}

/** UI commands emitted by MCP tools and consumed by AppNavigation. */
sealed interface McpUiCommand {
    data class OpenSettings(val categoryId: String) : McpUiCommand
    data class OpenScreen(val route: String) : McpUiCommand
}

object McpUiCommandBus {
    private val _pending = MutableStateFlow<McpUiCommand?>(null)

    /** State (not a one-shot event) so a deep link received before Compose starts is not lost. */
    val pending: StateFlow<McpUiCommand?> = _pending.asStateFlow()

    fun post(command: McpUiCommand) {
        _pending.value = command
    }

    fun consume() {
        _pending.value = null
    }
}

/** Deep-link invites (`xiangsuplayer://mcp/connect?p=...`) waiting for the settings UI. */
object McpPairingIntentBus {
    private val _invites = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val invites: SharedFlow<String> = _invites.asSharedFlow()

    fun post(payload: String) {
        _invites.tryEmit(payload)
    }
}
