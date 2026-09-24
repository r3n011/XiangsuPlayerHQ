package com.theveloper.pixelplay.data.qqmusic

import android.net.Uri
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QqMusicStreamProxy @Inject constructor(
    private val repository: QqMusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes = setOf(
        "qqmusic.qq.com",
        "stream.qqmusic.qq.com",
        "dl.stream.qqmusic.qq.com",
        "qq.com"
    )
    override val cacheExpirationMs = 10L * 60 * 1000
    override val proxyTag = "QqMusicStreamProxy"
    override val routePath = "/qqmusic/{songMid}"
    override val routeParamName = "songMid"
    override val uriScheme = "qqmusic"
    override val routePrefix = "/qqmusic"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateQqMusicSongMid(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        // 使用用户设置的首选音质（无损/320k 等），无对应权限时按档位向下回退
        val quality = try {
            userPreferencesRepository.musicQualityValueFlow.first()
        } catch (_: Exception) {
            null
        }
        return repository.getSongUrl(id, quality).getOrNull()
    }

    // QQ Music URIs may use host or path: qqmusic://songMid or qqmusic:///songMid
    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    fun resolveQqMusicUri(uriString: String): String? = resolveUri(uriString)

    /**
     * Pre-fetches and caches the real stream URL for a song so the proxy can
     * serve it instantly when ExoPlayer makes its HTTP request.
     */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "qqmusic") return
        val songMid = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateQqMusicSongMid(songMid)) return
        try {
            getOrFetchStreamUrl(songMid)
        } catch (e: Exception) {
            Timber.w(e, "warmUpStreamUrl failed for $songMid")
        }
    }
}
