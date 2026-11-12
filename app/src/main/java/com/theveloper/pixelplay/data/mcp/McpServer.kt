package com.theveloper.pixelplay.data.mcp

import com.theveloper.pixelplay.BuildConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStream
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Serializable
private data class RedeemRequest(
    val code: String,
    val client: McpClientInfo,
)

@Serializable
private data class PairRequestBody(
    val code: String,
    val client: McpClientInfo,
    val scopes: List<String> = emptyList(),
)

@Singleton
class McpServer @Inject constructor(
    private val sessions: McpSessionManager,
    private val registry: McpToolRegistry,
    private val pairing: McpPairingManager,
) {
    companion object {
        private const val MAX_BODY_BYTES = 256 * 1024
        private const val SSE_PING_MS = 15_000L
    }

    // Public entry points. The implementations below are member extensions on ApplicationCall,
    // which are only callable from inside this class.
    suspend fun handleRequest(call: ApplicationCall) = call.handle()
    suspend fun openStream(call: ApplicationCall) = call.openEventStream()
    suspend fun closeClientSession(call: ApplicationCall) = call.closeSession()

    private suspend fun ApplicationCall.handle() {
        val session = authenticate()
            ?: return respondRpcError(null, McpErrorCodes.UNAUTHORIZED, "invalid token")
        val body = runCatching { receiveText() }.getOrElse {
            return respondRpcError(null, McpErrorCodes.PARSE, "cannot read request body")
        }
        if (body.length > MAX_BODY_BYTES) {
            return respondText("payload too large", status = HttpStatusCode.PayloadTooLarge)
        }
        val frame = runCatching { McpJson.decodeFromString<McpRpcFrame>(body) }.getOrElse {
            return respondRpcError(null, McpErrorCodes.PARSE, "invalid json")
        }

        // Response to a server-initiated request (reserved for future sampling).
        if (frame.method == null) {
            return respondText("", status = HttpStatusCode.Accepted)
        }

        // Notifications never get a response body.
        if (frame.id == null) {
            return respondText("", status = HttpStatusCode.Accepted)
        }

        val outcome = runCatching { dispatch(session, frame) }.getOrElse { error ->
            Timber.w(error, "MCP dispatch failed: %s", frame.method)
            RpcOutcome.Error(McpErrorCodes.INTERNAL, error.message ?: "internal error")
        }
        respondOutcome(session, frame.id, outcome)
    }

    suspend fun ApplicationCall.openEventStream() {
        val session = authenticate()
            ?: return respondText("unauthorized", status = HttpStatusCode.Unauthorized)
        response.header(HttpHeaders.ContentType, "text/event-stream; charset=utf-8")
        response.header(HttpHeaders.CacheControl, "no-cache")
        response.header("X-Accel-Buffering", "no")
        respondOutputStream(ContentType.Text.EventStream) {
            session.sseConnected = true
            try {
                while (currentCoroutineContext().isActive) {
                    val message = withTimeoutOrNull(SSE_PING_MS) {
                        session.outbound.receive()
                    }
                    writeSse(this, message)
                }
            } finally {
                session.sseConnected = false
            }
        }
    }

    suspend fun ApplicationCall.closeSession() {
        val session = authenticate() ?: return
        runCatching { session.outbound.close() }
        sessions.sessionByTokenId(session.tokenId)?.let { sessions.revoke(session.tokenId) }
        respond(HttpStatusCode.NoContent)
    }

    private fun writeSse(output: OutputStream, message: String?) {
        val payload = if (message == null) ": ping\n\n" else "event: message\ndata: $message\n\n"
        output.write(payload.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private suspend fun ApplicationCall.authenticate(): McpSession? {
        val header = request.headers[HttpHeaders.Authorization] ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        val token = header.substringAfter(' ').trim()
        if (token.isBlank()) return null
        if (!isLanRequest()) return null
        return sessions.authenticate(token)
    }

    private fun ApplicationCall.isLanRequest(): Boolean {
        val host = request.origin.remoteHost
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress
    }

    private suspend fun dispatch(session: McpSession, frame: McpRpcFrame): RpcOutcome {
        val params = frame.params ?: JsonObject(emptyMap())
        return when (frame.method) {
            "initialize" -> RpcOutcome.Result(initializeResult(params))
            "ping" -> RpcOutcome.Result(buildJsonObject { })
            "tools/list" -> RpcOutcome.Result(toolsListResult(session))
            "tools/call" -> {
                val name = params["name"]?.jsonPrimitive?.content
                    ?: return RpcOutcome.Error(McpErrorCodes.INVALID_PARAMS, "missing tool name")
                val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val result = registry.call(name, session.scopes, args)
                RpcOutcome.Result(toolResultJson(result))
            }
            "resources/list" -> RpcOutcome.Result(resourcesListResult())
            "resources/read" -> {
                val uri = params["uri"]?.jsonPrimitive?.content
                    ?: return RpcOutcome.Error(McpErrorCodes.INVALID_PARAMS, "missing uri")
                RpcOutcome.Result(resourcesReadResult(uri, params))
            }
            "prompts/list" -> RpcOutcome.Result(buildJsonObject {
                put("prompts", buildJsonArray { })
            })
            "prompts/get" -> RpcOutcome.Error(McpErrorCodes.METHOD_NOT_FOUND, "no prompts")
            else -> RpcOutcome.Error(
                McpErrorCodes.METHOD_NOT_FOUND,
                "unknown method: ${frame.method}",
            )
        }
    }

    private fun initializeResult(params: JsonObject): JsonObject {
        val clientName = params["clientInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content
        Timber.i("MCP initialize from %s", clientName ?: "unknown")
        return buildJsonObject {
            put("protocolVersion", MCP_PROTOCOL_VERSION)
            putJsonObject("capabilities") {
                putJsonObject("tools") { put("listChanged", true) }
                putJsonObject("resources") {
                    put("subscribe", false)
                    put("listChanged", true)
                }
                putJsonObject("prompts") { put("listChanged", false) }
            }
            putJsonObject("serverInfo") {
                put("name", MCP_SERVER_NAME)
                put("title", "像素播放器")
                put("version", BuildConfig.VERSION_NAME)
            }
            put(
                "instructions",
                "你是像素播放器的控制端。所有播放、曲库、歌单操作都必须通过工具完成；" +
                    "不要臆造歌曲 ID。生成歌单时先调用 ai_get_candidates 或 library_search 获取真实候选。",
            )
        }
    }

    private fun toolsListResult(session: McpSession): JsonObject = buildJsonObject {
        put("tools", buildJsonArray {
            registry.available(session.scopes).forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", tool.inputSchema)
                })
            }
        })
    }

    private fun toolResultJson(result: McpToolResult): JsonObject = buildJsonObject {
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", result.text)
            })
        })
        result.structured?.let { put("structuredContent", it) }
        put("isError", result.isError)
    }

    private fun resourcesListResult(): JsonObject = buildJsonObject {
        put("resources", buildJsonArray {
            add(resource("pixelplayer://now-playing", "当前播放", "application/json"))
            add(resource("pixelplayer://queue", "播放队列", "application/json"))
            add(resource("pixelplayer://library/playlists", "歌单列表", "application/json"))
            add(resource("pixelplayer://profile", "用户画像", "text/plain"))
        })
    }

    private fun resource(uri: String, name: String, mime: String): JsonObject = buildJsonObject {
        put("uri", uri)
        put("name", name)
        put("mimeType", mime)
    }

    private suspend fun resourcesReadResult(uri: String, params: JsonObject): JsonObject {
        val body = when (uri) {
            "pixelplayer://now-playing" -> registry.call(
                "player_get_state",
                setOf(McpScopes.READ_PLAYBACK),
                JsonObject(emptyMap()),
            ).structured ?: buildJsonObject { }

            "pixelplayer://queue" -> registry.call(
                "player_get_state",
                setOf(McpScopes.READ_PLAYBACK),
                JsonObject(emptyMap()),
            ).structured?.get("queue")?.jsonObject ?: buildJsonObject { }

            "pixelplayer://library/playlists" -> registry.call(
                "library_list_playlists",
                setOf(McpScopes.READ_LIBRARY),
                JsonObject(emptyMap()),
            ).structured ?: buildJsonObject { }

            "pixelplayer://profile" -> registry.call(
                "ai_get_profile",
                setOf(McpScopes.READ_PROFILE),
                JsonObject(emptyMap()),
            ).structured ?: buildJsonObject { }

            else -> return ToolMcpJson.emptyResources(uri)
        }
        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("uri", uri)
                    put("mimeType", if (uri.endsWith("profile")) "text/plain" else "application/json")
                    put("text", body.toString())
                })
            })
        }
    }

    private suspend fun ApplicationCall.respondOutcome(
        session: McpSession,
        id: JsonElement?,
        outcome: RpcOutcome,
    ) {
        val payload = when (outcome) {
            is RpcOutcome.Result -> buildJsonObject {
                put("jsonrpc", "2.0")
                id?.let { put("id", it) }
                put("result", outcome.value)
            }
            is RpcOutcome.Error -> buildJsonObject {
                put("jsonrpc", "2.0")
                id?.let { put("id", it) }
                put("error", buildJsonObject {
                    put("code", outcome.code)
                    put("message", outcome.message)
                })
            }
        }
        response.header("Mcp-Session-Id", session.id)
        response.header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
        respondText(payload.toString(), ContentType.Application.Json, HttpStatusCode.OK)
    }

    private suspend fun ApplicationCall.respondRpcError(
        id: JsonElement?,
        code: Int,
        message: String,
    ) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }
        respondText(payload.toString(), ContentType.Application.Json, HttpStatusCode.OK)
    }
}

private sealed interface RpcOutcome {
    data class Result(val value: JsonObject) : RpcOutcome
    data class Error(val code: Int, val message: String) : RpcOutcome
}

private object ToolMcpJson {
    fun emptyResources(uri: String): JsonObject = buildJsonObject {
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("uri", uri)
                put("mimeType", "application/json")
                put("text", "{}")
            })
        })
    }
}

// ---- Pairing routes (mounted by McpServerService on the same Ktor instance) ----

private fun ApplicationCall.isLanOrigin(): Boolean {
    val host = request.origin.remoteHost
    val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
    return address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress
}

fun Route.mcpRoutes(
    server: McpServer,
    pairing: McpPairingManager,
    sessions: McpSessionManager,
) {
    post("/mcp") { server.handleRequest(call) }
    get("/mcp") { server.openStream(call) }
    delete("/mcp") { server.closeClientSession(call) }

    get("/api/mcp/info") {
        if (!call.isLanOrigin()) return@get call.respond(HttpStatusCode.Forbidden)
        call.respond(buildJsonObject {
            put("name", MCP_SERVER_NAME)
            put("device", android.os.Build.MODEL.orEmpty())
            put("version", BuildConfig.VERSION_NAME)
            put("protocol", MCP_PROTOCOL_VERSION)
            put("endpoint", McpServerState.endpoint)
            put("pairing", true)
        })
    }

    post("/api/mcp/pair/redeem") {
        if (!call.isLanOrigin()) return@post call.respond(HttpStatusCode.Forbidden)
        val body = runCatching {
            McpJson.decodeFromString<RedeemRequest>(call.receiveText())
        }.getOrElse {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        when (val result = pairing.redeem(body.code, body.client)) {
            is RedeemResult.Success -> call.respond(buildJsonObject {
                put("token", result.token)
                put("tokenId", result.tokenId)
                put("endpoint", result.endpoint)
                put("scopes", buildJsonArray { result.scopes.forEach { add(it) } })
                putJsonObject("server") {
                    put("name", MCP_SERVER_NAME)
                    put("version", BuildConfig.VERSION_NAME)
                }
            })
            RedeemResult.Pending -> call.respond(
                HttpStatusCode.Accepted,
                buildJsonObject { put("status", "pending") },
            )
            RedeemResult.Expired -> call.respond(
                HttpStatusCode.Gone,
                buildJsonObject { put("error", "expired") },
            )
            RedeemResult.Denied -> call.respond(
                HttpStatusCode.Forbidden,
                buildJsonObject { put("error", "denied") },
            )
            RedeemResult.AlreadyRedeemed -> call.respond(
                HttpStatusCode.Conflict,
                buildJsonObject { put("error", "already_redeemed") },
            )
            RedeemResult.Unknown -> call.respond(
                HttpStatusCode.NotFound,
                buildJsonObject { put("error", "unknown_code") },
            )
        }
    }

    post("/api/mcp/pair/request") {
        if (!call.isLanOrigin()) return@post call.respond(HttpStatusCode.Forbidden)
        val body = runCatching {
            McpJson.decodeFromString<PairRequestBody>(call.receiveText())
        }.getOrElse {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        val view = pairing.request(body.code, body.client, body.scopes)
        call.respond(buildJsonObject {
            put("status", view.status)
            put("endpoint", view.endpoint)
        })
    }

    get("/api/mcp/pair/status") {
        if (!call.isLanOrigin()) return@get call.respond(HttpStatusCode.Forbidden)
        val code = call.request.queryParameters["code"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val view = pairing.currentStatus(code)
        call.respond(buildJsonObject {
            put("status", view.status)
            put("endpoint", view.endpoint)
            put("scopes", buildJsonArray { view.scopes.forEach { add(it) } })
        })
    }

    post("/api/mcp/pair/cancel") {
        if (!call.isLanOrigin()) return@post call.respond(HttpStatusCode.Forbidden)
        val code = call.request.queryParameters["code"]
        if (code != null) pairing.cancel(code)
        call.respond(buildJsonObject { put("cancelled", true) })
    }

    get("/api/mcp/clients") {
        if (!call.isLanOrigin()) return@get call.respond(HttpStatusCode.Forbidden)
        val list = sessions.listClients()
        call.respond(buildJsonObject {
            put("clients", buildJsonArray {
                list.forEach { record ->
                    add(buildJsonObject {
                        put("id", record.id)
                        put("name", record.name)
                        put("clientId", record.clientId)
                        put("platform", record.platform)
                        put("revoked", record.revoked)
                        put("lastUsedAt", record.lastUsedAt)
                        put("scopes", buildJsonArray { record.scopes.forEach { add(it) } })
                    })
                }
            })
        })
    }
}
