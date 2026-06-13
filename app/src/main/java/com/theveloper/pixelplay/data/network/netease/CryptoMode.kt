package com.theveloper.pixelplay.data.network.netease

/**
 * Encryption mode for Netease Cloud Music API.
 * Different endpoints require different encryption methods.
 */
enum class CryptoMode {
    /** WeAPI — double AES-CBC + RSA. Used for search, playlists. */
    WEAPI,
    /** EAPI — AES-ECB with MD5 digest. Used for login, song URLs. */
    EAPI,
    /** Linux API — AES-ECB with fixed key. */
    LINUX,
    /** Plain API — no encryption, pass params directly. */
    API
}
