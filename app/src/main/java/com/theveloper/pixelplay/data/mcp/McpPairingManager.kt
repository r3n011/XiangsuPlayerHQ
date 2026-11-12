package com.theveloper.pixelplay.data.mcp

import android.os.Build
import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.data.preferences.McpPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

enum class PairSource { PLAYER_INITIATED, CLIENT_INITIATED }

enum class PairStatus { ISSUED, PENDING_APPROVAL, APPROVED, REDEEMED, DENIED, EXPIRED }

data class PendingPairing(
    val code: String,
    val source: PairSource,
    val clientId: String,
    val clientName: String,
    val clientScheme: String?,
    val requestedScopes: Set<String>,
    val grantedScopes: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val status: PairStatus,
)

data class PlayerPairing(
    val code: String,
    val payload: String,
)

data class PairStatusView(
    val status: String,
    val endpoint: String,
    val scopes: Set<String>,
)

sealed interface RedeemResult {
    data class Success(
        val token: String,
        val tokenId: String,
        val scopes: Set<String>,
        val endpoint: String,
    ) : RedeemResult

    data object Pending : RedeemResult
    data object Expired : RedeemResult
    data object Denied : RedeemResult
    data object AlreadyRedeemed : RedeemResult
    data object Unknown : RedeemResult
}

/**
 * One-tap pairing without QR codes.
 *
 * - Player-initiated: the player creates a code, launches the client with `scheme://mcp/pair`,
 *   and the client redeems the code over HTTP.
 * - Client-initiated: the client launches `xiangsuplayer://mcp/connect`, the player approves,
 *   and the client redeems via the callback (or polls status).
 */
@Singleton
class McpPairingManager @Inject constructor(
    private val sessions: McpSessionManager,
    private val prefs: McpPreferencesRepository,
) {
    companion object {
        private const val CODE_TTL_MS = 5 * 60 * 1000L
        private const val APPROVED_TTL_MS = 2 * 60 * 1000L
    }

    private val pending = ConcurrentHashMap<String, PendingPairing>()

    private val _incomingInvite = MutableStateFlow<McpPairPayload?>(null)
    val incomingInvite: StateFlow<McpPairPayload?> = _incomingInvite.asStateFlow()

    private val _pendingRequest = MutableStateFlow<PendingPairing?>(null)
    val pendingRequest: StateFlow<PendingPairing?> = _pendingRequest.asStateFlow()

    suspend fun preparePlayerInitiated(
        clientId: String,
        clientName: String,
        scheme: String,
        scopes: Set<String> = McpScopes.DEFAULT,
    ): PlayerPairing {
        cleanupExpired()
        val code = newCode()
        val endpoint = McpServerState.endpoint
        val payload = McpPairPayload(
            code = code,
            endpoint = endpoint,
            device = Build.MODEL.orEmpty(),
            deviceId = prefs.deviceId(),
            client = McpClientInfo(
                id = clientId,
                name = clientName,
                version = "",
                platform = "android",
                scheme = scheme,
            ),
            scopes = scopes.toList(),
            exp = System.currentTimeMillis() / 1000 + CODE_TTL_MS / 1000,
        )
        pending[code] = PendingPairing(
            code = code,
            source = PairSource.PLAYER_INITIATED,
            clientId = clientId,
            clientName = clientName,
            clientScheme = scheme,
            requestedScopes = scopes,
            grantedScopes = scopes,
            expiresAt = System.currentTimeMillis() + CODE_TTL_MS,
            status = PairStatus.ISSUED,
        )
        return PlayerPairing(code, McpCodec.encodePair(payload))
    }

    suspend fun redeem(code: String, client: McpClientInfo): RedeemResult {
        cleanupExpired()
        val session = pending[code] ?: return RedeemResult.Unknown
        if (session.expiresAt < System.currentTimeMillis() || session.status == PairStatus.EXPIRED) {
            pending[code] = session.copy(status = PairStatus.EXPIRED)
            return RedeemResult.Expired
        }
        when (session.status) {
            PairStatus.REDEEMED -> return RedeemResult.AlreadyRedeemed
            PairStatus.DENIED -> return RedeemResult.Denied
            PairStatus.PENDING_APPROVAL -> return RedeemResult.Pending
            PairStatus.EXPIRED -> return RedeemResult.Expired
            PairStatus.ISSUED, PairStatus.APPROVED -> Unit
        }

        val scopes = session.grantedScopes.ifEmpty { session.requestedScopes }
        val issued = sessions.issueToken(
            clientId = client.id.ifBlank { session.clientId },
            name = client.name.ifBlank { session.clientName },
            platform = client.platform,
            scopes = scopes,
        )
        pending[code] = session.copy(
            clientId = client.id,
            clientName = client.name,
            status = PairStatus.REDEEMED,
        )
        Timber.i("MCP pair redeemed: code=%s client=%s scopes=%s", code, client.name, scopes)
        return RedeemResult.Success(
            token = issued.plaintext,
            tokenId = issued.record.id,
            scopes = issued.record.scopes.toSet(),
            endpoint = McpServerState.endpoint,
        )
    }

    /** Cross-device / manual path: client asks, player shows a confirmation. */
    fun request(code: String, client: McpClientInfo, scopes: Collection<String>): PairStatusView {
        cleanupExpired()
        val requested = scopes.filter { it in McpScopes.ALL }.toSet().ifEmpty { McpScopes.DEFAULT }
        val session = PendingPairing(
            code = code,
            source = PairSource.CLIENT_INITIATED,
            clientId = client.id,
            clientName = client.name,
            clientScheme = client.scheme.takeIf { it.isNotBlank() },
            requestedScopes = requested,
            expiresAt = System.currentTimeMillis() + CODE_TTL_MS,
            status = PairStatus.PENDING_APPROVAL,
        )
        pending[code] = session
        _pendingRequest.value = session
        return currentStatus(code)
    }

    fun currentStatus(code: String): PairStatusView {
        cleanupExpired()
        val session = pending[code]
        return if (session == null) {
            PairStatusView("unknown", McpServerState.endpoint, emptySet())
        } else {
            PairStatusView(
                status = session.status.name.lowercase(),
                endpoint = McpServerState.endpoint,
                scopes = session.grantedScopes.ifEmpty { session.requestedScopes },
            )
        }
    }

    fun approve(code: String, granted: Set<String> = emptySet()) {
        val session = pending[code] ?: return
        pending[code] = session.copy(
            grantedScopes = granted.ifEmpty { session.requestedScopes },
            expiresAt = System.currentTimeMillis() + APPROVED_TTL_MS,
            status = PairStatus.APPROVED,
        )
        if (_pendingRequest.value?.code == code) _pendingRequest.value = null
    }

    fun deny(code: String) {
        val session = pending[code] ?: return
        pending[code] = session.copy(status = PairStatus.DENIED)
        if (_pendingRequest.value?.code == code) _pendingRequest.value = null
    }

    fun cancel(code: String) {
        pending.remove(code)
        if (_pendingRequest.value?.code == code) _pendingRequest.value = null
    }

    /** Called by MainActivity after parsing `xiangsuplayer://mcp/connect?p=...`. */
    fun receiveInvite(payload: String) {
        val parsed = McpCodec.parsePair(payload) ?: run {
            Timber.w("MCP: invalid invite payload")
            return
        }
        _incomingInvite.value = parsed
    }

    fun dismissInvite() {
        _incomingInvite.value = null
    }

    /**
     * Accepts an invite: marks the code APPROVED and returns the encoded callback payload.
     * The caller launches the client's declared scheme. If that launch fails, the client can
     * still poll /api/mcp/pair/status and redeem.
     */
    fun approveInvite(payload: McpPairPayload, granted: Set<String> = emptySet()): String? {
        val client = payload.client ?: return null
        val scheme = client.scheme.takeIf { it.isNotBlank() } ?: return null
        pending[payload.code] = PendingPairing(
            code = payload.code,
            source = PairSource.CLIENT_INITIATED,
            clientId = client.id,
            clientName = client.name,
            clientScheme = scheme,
            requestedScopes = payload.scopes.toSet().filter { it in McpScopes.ALL }.toSet()
                .ifEmpty { McpScopes.DEFAULT },
            grantedScopes = granted.ifEmpty {
                payload.scopes.toSet().filter { it in McpScopes.ALL }.toSet()
                    .ifEmpty { McpScopes.DEFAULT }
            },
            expiresAt = System.currentTimeMillis() + APPROVED_TTL_MS,
            status = PairStatus.APPROVED,
        )
        _incomingInvite.value = null
        return McpCodec.encodeApproved(
            McpApprovedPayload(
                code = payload.code,
                endpoint = McpServerState.endpoint,
                device = Build.MODEL.orEmpty(),
            )
        )
    }

    fun infoJson(): Map<String, Any?> = mapOf(
        "name" to MCP_SERVER_NAME,
        "device" to Build.MODEL.orEmpty(),
        "version" to BuildConfig.VERSION_NAME,
        "protocol" to MCP_PROTOCOL_VERSION,
        "endpoint" to McpServerState.endpoint,
        "pairing" to true,
    )

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        pending.entries.removeAll { (_, value) ->
            value.expiresAt < now && value.status != PairStatus.REDEEMED
        }
        val request = _pendingRequest.value
        if (request != null && request.expiresAt < now) _pendingRequest.value = null
    }

    private fun newCode(): String {
        val bytes = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val value = ((bytes[0].toInt() and 0x7f) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
        return (value % 1_000_000).toString().padStart(6, '0')
    }
}
