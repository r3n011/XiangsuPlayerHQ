package com.theveloper.pixelplay.data.mcp

import android.util.Base64
import com.theveloper.pixelplay.data.preferences.McpPreferencesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Serializable
data class McpClientRecord(
    val id: String,
    val clientId: String,
    val name: String,
    val platform: String = "",
    val salt: String,
    val hash: String,
    val scopes: List<String>,
    val createdAt: Long,
    val lastUsedAt: Long,
    val revoked: Boolean = false,
)

data class McpIssuedToken(
    val plaintext: String,
    val record: McpClientRecord,
)

/**
 * One live client connection. A session owns the SSE outbound queue; server-initiated
 * messages (notifications, future sampling requests) are written here and consumed by
 * the GET /mcp stream.
 */
class McpSession(
    val tokenId: String,
    val clientId: String,
    val clientName: String,
    val scopes: Set<String>,
) {
    val id: String = "sess_" + tokenId.takeLast(10)
    val outbound = Channel<String>(capacity = 64)
    val connectedAt: Long = System.currentTimeMillis()

    @Volatile
    var sseConnected: Boolean = false

    fun hasScope(scope: String): Boolean = scope in scopes

    fun send(message: String): Boolean = outbound.trySend(message).isSuccess
}

@Singleton
class McpSessionManager @Inject constructor(
    private val prefs: McpPreferencesRepository,
) {
    private val writeMutex = Mutex()
    private val sessions = ConcurrentHashMap<String, McpSession>()

    suspend fun issueToken(
        clientId: String,
        name: String,
        platform: String,
        scopes: Collection<String>,
    ): McpIssuedToken {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val tokenBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = "pp_" + Base64.encodeToString(
            tokenBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val record = McpClientRecord(
            id = "tok_" + ByteArray(6).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) },
            clientId = clientId,
            name = name,
            platform = platform,
            salt = salt,
            hash = sha256(token + salt),
            scopes = scopes.filter { it in McpScopes.ALL }.distinct(),
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
        )
        writeMutex.withLock {
            val records = loadRecords().toMutableList()
            records.add(record)
            saveRecords(records)
        }
        sessions.getOrCreate(record)
        return McpIssuedToken(token, record)
    }

    suspend fun authenticate(token: String): McpSession? {
        if (token.isBlank()) return null
        val records = loadRecords()
        for (record in records) {
            if (record.revoked) continue
            if (MessageDigest.isEqual(
                    sha256(token + record.salt).toByteArray(),
                    record.hash.toByteArray(),
                )
            ) {
                val now = System.currentTimeMillis()
                if (now - record.lastUsedAt > 60_000L) {
                    touch(record.id)
                }
                return sessions.getOrCreate(record)
            }
        }
        return null
    }

    suspend fun listClients(): List<McpClientRecord> = loadRecords()

    suspend fun revoke(tokenId: String) {
        writeMutex.withLock {
            val records = loadRecords().map {
                if (it.id == tokenId) it.copy(revoked = true) else it
            }
            saveRecords(records)
        }
        sessions.remove(tokenId)?.outbound?.close()
    }

    fun sessionByTokenId(tokenId: String): McpSession? = sessions[tokenId]

    fun sessionByClientId(clientId: String): McpSession? =
        sessions.values.firstOrNull { it.clientId == clientId }

    fun allSessions(): List<McpSession> = sessions.values.toList()

    fun closeAll() {
        sessions.values.forEach { runCatching { it.outbound.close() } }
        sessions.clear()
    }

    private fun ConcurrentHashMap<String, McpSession>.getOrCreate(
        record: McpClientRecord,
    ): McpSession = getOrPut(record.id) {
        McpSession(
            tokenId = record.id,
            clientId = record.clientId,
            clientName = record.name,
            scopes = record.scopes.toSet(),
        )
    }

    private suspend fun touch(tokenId: String) {
        writeMutex.withLock {
            val records = loadRecords().map {
                if (it.id == tokenId) it.copy(lastUsedAt = System.currentTimeMillis()) else it
            }
            saveRecords(records)
        }
    }

    private suspend fun loadRecords(): List<McpClientRecord> {
        val raw = prefs.clientsJson.first()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            McpJson.decodeFromString<List<McpClientRecord>>(raw)
        }.onFailure { Timber.w(it, "MCP: failed to decode paired clients") }
            .getOrDefault(emptyList())
    }

    private suspend fun saveRecords(records: List<McpClientRecord>) {
        runCatching {
            prefs.setClientsJson(McpJson.encodeToString(records))
        }.onFailure { Timber.w(it, "MCP: failed to persist paired clients") }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
