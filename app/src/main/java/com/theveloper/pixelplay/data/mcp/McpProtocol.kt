package com.theveloper.pixelplay.data.mcp

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Shared JSON parser for the MCP endpoint. */
val McpJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

const val MCP_PROTOCOL_VERSION = "2025-06-18"
const val MCP_SERVER_NAME = "pixelplayer"

object McpErrorCodes {
    const val PARSE = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL = -32603
    const val UNAUTHORIZED = -32001
    const val FORBIDDEN_SCOPE = -32002
    const val RATE_LIMITED = -32003
    const val NOT_READY = -32004
    const val DEVICE_BUSY = -32005
}

@Serializable
data class McpRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

/**
 * One inbound JSON-RPC frame. It may be:
 *  - a request: [method] != null
 *  - a notification: [method] != null && [id] == null
 *  - a response to a server-initiated request: [method] == null && ([result] != null || [error] != null)
 */
@Serializable
data class McpRpcFrame(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String? = null,
    val params: JsonObject? = null,
    val result: JsonObject? = null,
    val error: McpRpcError? = null,
)

data class McpToolResult(
    val text: String,
    val structured: JsonObject? = null,
    val isError: Boolean = false,
) {
    companion object {
        fun ok(text: String, structured: JsonObject? = null) = McpToolResult(text, structured)
        fun fail(text: String) = McpToolResult(text, isError = true)
    }
}

/** Scopes are intentionally coarse; a token can only call tools inside its granted set. */
object McpScopes {
    const val READ_PLAYBACK = "read.playback"
    const val CONTROL_PLAYBACK = "control.playback"
    const val READ_LIBRARY = "read.library"
    const val WRITE_PLAYLIST = "write.playlist"
    const val WRITE_FAVORITE = "write.favorite"
    const val READ_LYRICS = "read.lyrics"
    const val READ_PROFILE = "read.profile"
    const val CONTROL_SETTINGS = "control.settings"

    val ALL = setOf(
        READ_PLAYBACK,
        CONTROL_PLAYBACK,
        READ_LIBRARY,
        WRITE_PLAYLIST,
        WRITE_FAVORITE,
        READ_LYRICS,
        READ_PROFILE,
        CONTROL_SETTINGS,
    )

    /** Minimal useful set granted by the one-tap pairing flow. */
    val DEFAULT = setOf(
        READ_PLAYBACK,
        CONTROL_PLAYBACK,
        READ_LIBRARY,
        WRITE_PLAYLIST,
        WRITE_FAVORITE,
        READ_LYRICS,
    )
}

@Serializable
data class McpClientInfo(
    val id: String,
    val name: String,
    val version: String = "",
    val platform: String = "",
    /** Deep-link scheme used by the player to bring the client back after approval. */
    val scheme: String = "",
)

@Serializable
data class McpPairPayload(
    val v: Int = 1,
    val type: String = "mcp.pair",
    val code: String,
    /** Base URL, e.g. http://192.168.1.23:8787 (without /mcp). */
    val endpoint: String,
    val device: String = "",
    val deviceId: String = "",
    val client: McpClientInfo? = null,
    val scopes: List<String> = emptyList(),
    /** Unix seconds. */
    val exp: Long,
)

@Serializable
data class McpApprovedPayload(
    val v: Int = 1,
    val type: String = "mcp.pair.approved",
    val code: String,
    val endpoint: String,
    val device: String = "",
)

object McpCodec {
    fun encodePair(payload: McpPairPayload): String = encode(McpJson.encodeToString(payload))
    fun encodeApproved(payload: McpApprovedPayload): String = encode(McpJson.encodeToString(payload))

    fun parsePair(encoded: String): McpPairPayload? = runCatching {
        val payload = McpJson.decodeFromString<McpPairPayload>(decode(encoded))
        if (payload.v != 1 || payload.type != "mcp.pair") return null
        if (payload.code.length != 6 || payload.exp * 1000L < System.currentTimeMillis()) return null
        if (!payload.client?.scheme.isNullOrBlank() &&
            !payload.client.scheme.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*$"))
        ) return null
        payload
    }.getOrNull()

    fun parseApproved(encoded: String): McpApprovedPayload? = runCatching {
        val payload = McpJson.decodeFromString<McpApprovedPayload>(decode(encoded))
        if (payload.v != 1 || payload.type != "mcp.pair.approved") return null
        payload
    }.getOrNull()

    private fun encode(raw: String): String = Base64.encodeToString(
        raw.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun decode(encoded: String): String = String(
        Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        Charsets.UTF_8,
    )
}

// ---- Small JsonObject helpers shared by tool handlers ----

fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

fun JsonObject.stringList(key: String): List<String> =
    this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

fun jsonString(value: String): JsonPrimitive = JsonPrimitive(value)

fun jsonInt(value: Int): JsonPrimitive = JsonPrimitive(value)

fun jsonBool(value: Boolean): JsonPrimitive = JsonPrimitive(value)
