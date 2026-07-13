package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.dot.DotDisplayMode
import com.theveloper.pixelplay.data.dot.DotScreenRenderer
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.dot.DotApiService
import com.theveloper.pixelplay.data.network.dot.DotApiResponse
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DotImageRepository @Inject constructor(
    private val dotApiService: DotApiService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val imageLoader: ImageLoader,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DotImageRepository"
        private const val DOT_SCREEN_WIDTH = 296
        private const val DOT_SCREEN_HEIGHT = 152
    }

    suspend fun updateCredentials() {
        val apiKey = userPreferencesRepository.getDotApiKey()
        val deviceId = userPreferencesRepository.getDotDeviceId()
        dotApiService.setCredentials(apiKey, deviceId)
        Timber.d("$TAG: Credentials updated from preferences")
    }

    suspend fun pushNowPlayingToDot(
        song: Song,
        progressPercent: Float,
        isPlaying: Boolean,
        refreshNow: Boolean = true
    ): Result<DotApiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!dotApiService.hasCredentials()) {
                    return@withContext Result.failure(IllegalStateException("Dot credentials not configured"))
                }

                val albumArt = loadAlbumArtBitmap(song)
                val bitmap = DotScreenRenderer.createNowPlayingBitmap(
                    songTitle = song.title,
                    artistName = song.displayArtist,
                    albumArt = albumArt,
                    progressPercent = progressPercent,
                    isPlaying = isPlaying
                )
                albumArt?.recycle()

                try {
                    val base64Image = encodeToBase64Png(bitmap)
                    dotApiService.pushImage(
                        base64Image = base64Image,
                        refreshNow = refreshNow
                    )
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to push now playing")
                Result.failure(e)
            }
        }
    }

    suspend fun pushStatsToDot(
        mode: DotDisplayMode,
        refreshNow: Boolean = true
    ): Result<DotApiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!dotApiService.hasCredentials()) {
                    return@withContext Result.failure(IllegalStateException("Dot credentials not configured"))
                }

                val songs = musicRepository.getAllSongsOnce()
                val bitmap = when (mode) {
                    DotDisplayMode.TODAY_STATS -> {
                        val summary = playbackStatsRepository.loadSummary(StatsTimeRange.DAY, songs)
                        DotScreenRenderer.createStatsOverviewBitmap(summary)
                    }
                    DotDisplayMode.MONTH_STATS -> {
                        val summary = playbackStatsRepository.loadSummary(StatsTimeRange.MONTH, songs)
                        DotScreenRenderer.createStatsOverviewBitmap(summary)
                    }
                    DotDisplayMode.ALL_TIME_STATS -> {
                        val summary = playbackStatsRepository.loadSummary(StatsTimeRange.ALL, songs)
                        DotScreenRenderer.createStatsOverviewBitmap(summary)
                    }
                    DotDisplayMode.TOP_SONGS -> {
                        val summary = playbackStatsRepository.loadSummary(StatsTimeRange.WEEK, songs)
                        DotScreenRenderer.createTopSongsBitmap(
                            songs = summary.topSongs,
                            title = "最常听歌曲"
                        )
                    }
                    DotDisplayMode.TOP_ARTISTS -> {
                        val summary = playbackStatsRepository.loadSummary(StatsTimeRange.WEEK, songs)
                        DotScreenRenderer.createTopArtistsBitmap(
                            artists = summary.topArtists,
                            title = "最常听艺术家"
                        )
                    }
                    DotDisplayMode.TIME_DISTRIBUTION -> {
                        val summary = playbackStatsRepository.loadSummary(StatsTimeRange.WEEK, songs)
                        summary.dayListeningDistribution?.let {
                            DotScreenRenderer.createTimeDistributionBitmap(
                                distribution = it,
                                title = "时段分布"
                            )
                        } ?: DotScreenRenderer.createStatsOverviewBitmap(summary)
                    }
                    else -> {
                        val summary = playbackStatsRepository.loadSummary(StatsTimeRange.WEEK, songs)
                        DotScreenRenderer.createStatsOverviewBitmap(summary)
                    }
                }

                try {
                    val base64Image = encodeToBase64Png(bitmap)
                    dotApiService.pushImage(
                        base64Image = base64Image,
                        refreshNow = refreshNow
                    )
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to push stats")
                Result.failure(e)
            }
        }
    }

    suspend fun pushAlbumArtToDot(
        song: Song,
        context: Context,
        refreshNow: Boolean = true,
        border: Int = 0,
        ditherType: String = "DIFFUSION",
        ditherKernel: String = "FLOYD_STEINBERG"
    ): Result<DotApiResponse> {
        return pushNowPlayingToDot(song, 0f, false, refreshNow)
    }

    suspend fun testConnection(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            updateCredentials()
            dotApiService.testConnection()
        }
    }

    suspend fun isDotConfigured(): Boolean {
        return userPreferencesRepository.hasDotCredentials()
    }

    suspend fun isAutoPushEnabled(): Boolean {
        return userPreferencesRepository.dotAutoPushEnabledFlow.first()
    }

    suspend fun getDisplayMode(): DotDisplayMode {
        val modeName = userPreferencesRepository.dotDisplayModeFlow.first()
        return try {
            DotDisplayMode.valueOf(modeName)
        } catch (e: Exception) {
            DotDisplayMode.NOW_PLAYING
        }
    }

    private suspend fun loadAlbumArtBitmap(song: Song): Bitmap? {
        return withContext(Dispatchers.IO) {
            song.albumArtUriString?.takeIf { it.isNotBlank() }?.let { uriString ->
                loadBitmapFromUri(context, uriString)
            } ?: loadBitmapFromFile(song)
        }
    }

    private suspend fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return try {
            val request = ImageRequest.Builder(context)
                .data(uriString)
                .size(DOT_SCREEN_WIDTH, DOT_SCREEN_HEIGHT)
                .allowHardware(false)
                .build()

            val result = imageLoader.execute(request)
            result.drawable?.toBitmap()
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to load bitmap from URI: $uriString")
            null
        }
    }

    private fun loadBitmapFromFile(song: Song): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val embedded = runCatching {
                if (song.path.isNotBlank() && java.io.File(song.path).exists()) {
                    retriever.setDataSource(song.path)
                } else {
                    song.contentUriString?.let {
                        retriever.setDataSource(context, Uri.parse(it))
                    }
                }
                retriever.embeddedPicture
            }.getOrNull()

            embedded?.let { bytes ->
                BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to extract embedded album art")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun encodeToBase64Png(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }
}
