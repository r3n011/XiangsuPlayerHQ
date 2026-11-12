package com.theveloper.pixelplay.data.plugin

import kotlinx.serialization.json.Json

/**
 * manifest 校验（对齐 02/03 文档）。
 *
 * 安装期必须拒绝：schemaVersion != 3、type != capability、apiLevel > 3、
 * capabilities 含未知 id、id 非法、networkDomains 与 network 能力不配套。
 */
object XsManifestValidator {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val ID_PATTERN = Regex("^[a-z0-9]+(\\.[a-z0-9]+)+$")
    private val VERSION_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+$")

    fun parse(raw: String): XSManifest {
        val manifest = try {
            json.decodeFromString(XSManifest.serializer(), raw)
        } catch (e: Exception) {
            throw XsInstallException(XsErrors.INVALID_MANIFEST, "manifest.json 解析失败：${e.message}")
        }
        validate(manifest)
        return manifest
    }

    fun validate(manifest: XSManifest) {
        if (manifest.schemaVersion != 3) {
            throw XsInstallException(
                XsErrors.UNSUPPORTED_SCHEMA,
                "manifest.schemaVersion=${manifest.schemaVersion}，只支持 3"
            )
        }
        if (manifest.id.isBlank() || !ID_PATTERN.matches(manifest.id)) {
            throw XsInstallException(XsErrors.INVALID_MANIFEST, "插件 id 非法：${manifest.id}")
        }
        if (manifest.name.isBlank() || manifest.name.length > 32) {
            throw XsInstallException(XsErrors.INVALID_MANIFEST, "插件 name 缺失或超过 32 字符")
        }
        if (!VERSION_PATTERN.matches(manifest.version)) {
            throw XsInstallException(XsErrors.INVALID_MANIFEST, "插件 version 非法：${manifest.version}")
        }
        if (manifest.type != "capability") {
            throw XsInstallException(XsErrors.INVALID_MANIFEST, "type 只能是 capability（当前 ${manifest.type}）")
        }
        if (manifest.apiLevel > 3) {
            throw XsInstallException(
                XsErrors.UNSUPPORTED_API_LEVEL,
                "插件要求 apiLevel ${manifest.apiLevel}，当前宿主支持 3，请升级 App"
            )
        }
        if (manifest.capabilities.isEmpty()) {
            throw XsInstallException(XsErrors.INVALID_MANIFEST, "capabilities 不能为空")
        }
        val unknown = manifest.capabilities.map { it.id }.filter { it !in XS_KNOWN_CAPABILITIES }
        if (unknown.isNotEmpty()) {
            throw XsInstallException(XsErrors.INVALID_MANIFEST, "未知能力：${unknown.joinToString()}")
        }
        val badPermissions = manifest.capabilities
            .flatMap { it.permissions }
            .filter { it !in XS_KNOWN_PERMISSIONS }
        if (badPermissions.isNotEmpty()) {
            throw XsInstallException(XsErrors.INVALID_MANIFEST, "未知权限：${badPermissions.distinct().joinToString()}")
        }
        val domains = manifest.networkDomains.orEmpty()
        if (domains.isNotEmpty()) {
            if (manifest.capabilities.none { it.id == "network" }) {
                throw XsInstallException(XsErrors.INVALID_MANIFEST, "声明 networkDomains 时必须包含 network 能力")
            }
            val illegal = domains.filter { it.isBlank() || !it.matches(Regex("^[a-zA-Z0-9.-]+(:\\d+)?$")) }
            if (illegal.isNotEmpty()) {
                throw XsInstallException(
                    XsErrors.INVALID_MANIFEST,
                    "networkDomains 只允许精确主机名（禁止通配符/IP 段）：${illegal.joinToString()}"
                )
            }
        }
        manifest.ui?.let { ui ->
            val entry = ui.entry
            if (entry.isBlank() || !entry.startsWith("ui/") || !entry.endsWith(".html") ||
                entry.contains("..") || entry.contains("\\")
            ) {
                throw XsInstallException(XsErrors.INVALID_MANIFEST, "ui.entry 非法：$entry")
            }
        }
        manifest.agreement?.let { path ->
            if (path.contains("..") || path.startsWith("/") || path.contains("\\")) {
                throw XsInstallException(XsErrors.INVALID_MANIFEST, "agreement 路径非法：$path")
            }
        }
    }

    /** 语义化三段式比较：1.10.0 > 1.9.9。非法版本按 0 处理。 */
    fun compareVersion(a: String, b: String): Int {
        fun parse(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }.let {
            listOf(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }, it.getOrElse(2) { 0 })
        }
        val pa = parse(a)
        val pb = parse(b)
        for (i in 0..2) {
            if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        }
        return 0
    }
}
