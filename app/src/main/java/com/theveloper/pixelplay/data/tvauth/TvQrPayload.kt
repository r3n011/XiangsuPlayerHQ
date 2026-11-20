package com.theveloper.pixelplay.data.tvauth

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * TV 授权页二维码内容：`http://<LAN-IP>:<port>/auth/<48hex nonce>/`
 *
 * 只接受私网地址（RFC1918 / IPv6 ULA），拒绝 loopback、公网、组播、链路本地与
 * 云元数据地址；路径必须严格匹配授权路径，避免把任意 URL 当成上传目标。
 */
data class TvQrPayload(
    val url: String,
    val host: String,
    val port: Int,
    val nonce: String,
) {
    companion object {
        private val AUTH_PATH = Regex("^/auth/([0-9a-f]{48})/?$")

        fun parse(raw: String): TvQrPayload? {
            val trimmed = raw.trim()
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            if (!uri.scheme.equals("http", ignoreCase = true)) return null
            val host = uri.host ?: return null
            val match = AUTH_PATH.matchEntire(uri.path.orEmpty()) ?: return null
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
            if (!isPrivate(address)) return null
            val port = if (uri.port in 1..65535) uri.port else 80
            return TvQrPayload(trimmed, host, port, match.groupValues[1])
        }

        private fun isPrivate(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isAnyLocalAddress ||
                address.isMulticastAddress || address.isLinkLocalAddress
            ) {
                return false
            }
            if (address.isSiteLocalAddress) return true
            if (address is Inet6Address) {
                val bytes = address.address
                return bytes.isNotEmpty() && (bytes[0].toInt() and 0xFE) == 0xFC
            }
            return false
        }
    }
}
