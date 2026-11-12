package com.theveloper.pixelplay.data.plugin

import kotlinx.serialization.Serializable

/**
 * 插件平台数据模型（安卓接入文档包 v1.2 / Host API v0.6）。
 *
 * 与 iOS 端逐字对齐：字段名、单位（毫秒）、错误码均不可随意更改。
 */

@Serializable
data class XSAuthor(val name: String? = null, val email: String? = null)

@Serializable
data class XSCapability(
    val id: String,
    val version: Int = 1,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class XSPlayerAction(
    val id: String,
    val icon: String = "",
    val iconAndroid: String? = null,
    val title: String = "",
)

@Serializable
data class XSUi(
    val entry: String = "ui/index.html",
    val title: String? = null,
    val presentation: String? = null,
    val defaultPage: String? = null,
)

@Serializable
data class XSManifest(
    val schemaVersion: Int = 0,
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val type: String = "capability",
    val apiLevel: Int = 0,
    val author: XSAuthor? = null,
    val description: String? = null,
    val icon: String? = null,
    val iconAndroid: String? = null,
    val capabilities: List<XSCapability> = emptyList(),
    val playerActions: List<XSPlayerAction> = emptyList(),
    val agreement: String? = null,
    val ui: XSUi? = null,
    val networkDomains: List<String>? = null,
)

@Serializable
data class XSStorePlugin(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val icon: String = "",
    val iconAndroid: String? = null,
    val type: String = "capability",
    val apiLevel: Int = 3,
    val capabilities: List<XSCapability> = emptyList(),
    val playerActions: List<XSPlayerAction> = emptyList(),
    val agreement: String? = null,
    val zipUrl: String = "",
    val size: Long = 0,
    val sha256: String = "",
)

@Serializable
data class XSStoreEnvelope(
    val code: Int = 0,
    val data: List<XSStorePlugin> = emptyList(),
    val error: String? = null,
)

/** 已安装插件（持久化到 SharedPreferences，不依赖商店可用）。 */
@Serializable
data class XSPluginRecord(
    val manifest: XSManifest,
    val enabled: Boolean = false,
    val agreed: Boolean = false,
    val installedAt: Long = 0L,
    val source: String = "store",
    val lastError: String? = null,
) {
    val id: String get() = manifest.id
    val version: String get() = manifest.version
}

@Serializable
data class XSTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    val artworkUrl: String = "",
    val source: String = "local",
)

@Serializable
data class XSPlaybackState(
    val state: String,
    val position: Long,
    val duration: Long,
)

@Serializable
data class XSAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int = 0,
    val artworkUrl: String = "",
)

@Serializable
data class XSPlaylist(
    val id: String,
    val title: String,
    val songCount: Int,
    val artworkUrl: String = "",
)

@Serializable
data class XSPosterRow(
    val index: String? = null,
    val label: String,
    val value: String,
)

@Serializable
data class XSPosterBlock(
    val type: String,
    val text: String? = null,
    val left: String? = null,
    val right: String? = null,
    val accentText: String? = null,
    val dimText: String? = null,
    val size: Int? = null,
    val maxLines: Int? = null,
    val kern: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val color: String? = null,
    val filled: Int? = null,
    val total: Int? = null,
    val sig: String? = null,
    val rows: List<XSPosterRow> = emptyList(),
)

/** 插件回执（最近若干条，仅内存）。 */
data class XSPluginEvent(
    val ts: Long,
    val kind: String,
    val message: String,
)

/** 商店/安装状态，供 UI 展示。 */
sealed interface XsItemState {
    data object Idle : XsItemState
    data class Downloading(val progress: Float) : XsItemState
    data object Verifying : XsItemState
    data object Installing : XsItemState
    data class Installed(val version: String) : XsItemState
    data class Failed(val message: String) : XsItemState
}

/** 安装期异常（错误码见附录 F）。 */
class XsInstallException(val code: String, message: String) : Exception(message)

object XsErrors {
    const val PERMISSION_DENIED = "permission_denied"
    const val NOT_IMPLEMENTED = "not_implemented"
    const val NO_SUCH_MODULE = "no_such_module"
    const val NO_SUCH_METHOD = "no_such_method"
    const val INVALID_ARGS = "invalid_args"
    const val INVALID_URL = "invalid_url"
    const val INVALID_SCHEME = "invalid_scheme"
    const val INVALID_SONG = "invalid_song"
    const val INVALID_INDEX = "invalid_index"
    const val NOTIFICATION_DENIED = "notification_denied"
    const val UNSUPPORTED_API_LEVEL = "unsupported_api_level"
    const val INVALID_ZIP = "invalid_zip"
    const val MISSING_MANIFEST = "missing_manifest"
    const val INVALID_MANIFEST = "invalid_manifest"
    const val UNSUPPORTED_SCHEMA = "unsupported_schema"
    const val MISSING_MAIN_SCRIPT = "missing_main_script"
    const val SCRIPT_FAILED = "script_failed"
    const val RUNTIME_UNAVAILABLE = "runtime_unavailable"
    const val CHECKSUM_MISMATCH = "checksum_mismatch"
}

/** 已知能力 id（14 项）。 */
val XS_KNOWN_CAPABILITIES = setOf(
    "cast", "music-source", "push", "device-bridge", "theme", "media", "library",
    "storage", "network", "notification", "share", "ai", "clipboard", "navigation",
)

/** 已知权限（22 项）。 */
val XS_KNOWN_PERMISSIONS = setOf(
    "cast.discover", "cast.control", "music.search", "music.play", "push.send",
    "device.connect", "theme.apply", "media.info", "media.control", "queue.manage",
    "library.read", "library.write", "storage.read", "storage.write", "network.request",
    "notification.send", "share.invoke", "lyrics.readwrite", "ai.invoke",
    "clipboard.read", "clipboard.write", "navigation.open",
)

/** 权限中文名（授权弹窗双列 chip 用）。 */
val XS_PERMISSION_LABELS = mapOf(
    "cast.discover" to "发现投屏设备",
    "cast.control" to "控制投屏播放",
    "music.search" to "搜索远程音源",
    "music.play" to "获取播放地址",
    "push.send" to "发送推送消息",
    "device.connect" to "连接穿戴/外设",
    "theme.apply" to "应用主题外观",
    "media.info" to "读取播放信息",
    "media.control" to "控制播放",
    "queue.manage" to "管理播放队列",
    "library.read" to "读取本地曲库",
    "library.write" to "写入本地曲库",
    "storage.read" to "私有存储读取",
    "storage.write" to "私有存储写入",
    "network.request" to "发起网络请求",
    "notification.send" to "发送通知",
    "share.invoke" to "调用系统分享",
    "lyrics.readwrite" to "读取当前歌词",
    "ai.invoke" to "调用像素播放器 AI",
    "clipboard.read" to "读取剪贴板",
    "clipboard.write" to "写入剪贴板",
    "navigation.open" to "打开外部链接",
)

/** 高风险权限（协议正文需单独说明）。 */
val XS_HIGH_RISK_PERMISSIONS = setOf("music.play", "device.connect", "network.request", "ai.invoke")

fun XSPluginRecord.permissions(): Set<String> =
    manifest.capabilities.flatMap { it.permissions }.toSet()
