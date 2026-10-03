package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.kugou.KugouRecognitionApi
import com.theveloper.pixelplay.data.kugou.KugouRecognitionHit
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.netease.SongRecognitionClient
import com.theveloper.pixelplay.data.repository.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 听歌识曲聚合：**同时**请求网易云与酷狗，两家结果合并后返回。
 *
 * 参照项目 md3Music 只有酷狗一条链路；本项目保留原有的网易云指纹匹配，再并行加一路酷狗，
 * 覆盖面更广（网易云拿不到的冷门/翻唱，酷狗常有；反之亦然）。
 *
 * 关键点：**只录一次音**。两条链路共用同一段 8 kHz 单声道录音 —— 网易云要浮点采样生成
 * shazam_v2 指纹，酷狗要 16bit 小端 PCM 作二进制 body。若各录各的会抢麦克风。
 * 网易云指纹依赖 WebView，生成失败也不影响酷狗那条线（用 runCatching 各自兜底）。
 */
@Singleton
class SongRecognitionRepository @Inject constructor(
    private val neteaseClient: SongRecognitionClient,
    private val kugouRecognitionApi: KugouRecognitionApi,
    private val neteaseRepository: NeteaseRepository,
    private val builtInSourceSearchApi: BuiltInSourceSearchApi,
    private val musicRepository: MusicRepository,
) {

    /**
     * 录一段并识别，返回合并去重后的可播放歌曲列表（网易云优先，其次酷狗）。
     * 调用前需已获得 RECORD_AUDIO 权限。任一链路失败不影响另一条。
     */
    suspend fun recognize(durationSeconds: Int): List<Song> = withContext(Dispatchers.IO) {
        val captured = neteaseClient.captureAudio(durationSeconds)

        // 网易云指纹必须在主线程的 WebView 里生成；失败则放弃网易云这条线
        val fingerprint = runCatching { neteaseClient.generateFingerprint(captured.samples) }
            .onFailure { Timber.w(it, "Netease fingerprint generation failed") }
            .getOrNull()

        val neteaseSongs: List<Song>
        val kugouSongs: List<Song>
        coroutineScope {
            val neteaseDeferred = async { resolveNetease(fingerprint, durationSeconds) }
            val kugouDeferred = async {
                val hits: List<KugouRecognitionHit> =
                    kugouRecognitionApi.match(captured.pcm16).getOrDefault(emptyList())
                resolveKugou(hits)
            }
            neteaseSongs = neteaseDeferred.await()
            kugouSongs = kugouDeferred.await()
        }
        mergeSongs(neteaseSongs, kugouSongs)
    }

    private suspend fun resolveNetease(fingerprint: String?, durationSeconds: Int): List<Song> {
        if (fingerprint.isNullOrBlank()) return emptyList()
        return runCatching {
            val matches = neteaseClient.matchNetease(fingerprint, durationSeconds)
            if (matches.isEmpty()) return emptyList()
            neteaseRepository.getNeteaseSongsByIds(matches.map { it.neteaseId })
        }.onFailure { Timber.w(it, "Netease recognition resolve failed") }
            .getOrDefault(emptyList())
    }

    /**
     * 酷狗识别结果 → 可播放 Song：拿「歌手 + 歌名」去内置音源搜索，
     * 优先按 hash / album_audio_id 精确匹配，否则取第一条，再落库（cloud://lx）取回统一模型。
     */
    private suspend fun resolveKugou(hits: List<KugouRecognitionHit>): List<Song> {
        if (hits.isEmpty()) return emptyList()
        val songs = ArrayList<Song>()
        val seen = HashSet<String>()
        for (hit in hits.take(MAX_KUGOU_RESOLVE)) {
            val keyword = listOf(hit.artist, hit.name)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (keyword.isBlank()) continue
            val candidates = runCatching { builtInSourceSearchApi.search(SOURCE_KG, keyword, 1, 5) }
                .onFailure { Timber.w(it, "Kugou recognition search failed") }
                .getOrNull()?.list.orEmpty()
            if (candidates.isEmpty()) continue
            val best = hit.hash?.takeIf { it.isNotBlank() }?.let { hash ->
                candidates.firstOrNull { it.hash.equals(hash, ignoreCase = true) }
            } ?: hit.albumAudioId?.takeIf { it.isNotBlank() }?.let { id ->
                candidates.firstOrNull { it.id == id }
            } ?: candidates.first()

            val key = "${best.name.lowercase()}|${best.singer.lowercase()}"
            if (!seen.add(key)) continue
            val songId = runCatching { musicRepository.saveCloudSong(best) }.getOrNull() ?: continue
            val song = musicRepository.getSong(songId.toString()).firstOrNull() ?: continue
            songs += song
        }
        return songs
    }

    /**
     * 网易云结果在前、酷狗结果在后，**各自内部**按 id 去重。
     *
     * ⚡ 不做跨平台去重：同一首歌被两个平台同时识别出来时，两条都保留 —— 两个平台的
     * 可用性 / 音质 / 播放链路不同，用户可以在结果里挑一条能放的。此前按「歌名+歌手」
     * 跨平台合并，会导致「只显示一首」，与「显示两个平台返回的所有歌」的预期不符。
     */
    private fun mergeSongs(neteaseSongs: List<Song>, kugouSongs: List<Song>): List<Song> =
        neteaseSongs.distinctBy { it.id } + kugouSongs.distinctBy { it.id }

    private companion object {
        const val SOURCE_KG = "kg"
        /** 最多把几条酷狗候选解析成歌曲：每条都要搜索 + 落库，太多会拖慢整体返回。 */
        const val MAX_KUGOU_RESOLVE = 5
    }
}
