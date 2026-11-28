package com.theveloper.pixelplay.data.research

import com.theveloper.pixelplay.data.database.ListenEventEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.ResearchEventDao
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 听歌事件采集：播放会话结算后把一条事件写入 Room 待传队列。
 *
 * - 哈希：sha256(salt + 规范化文本)，规范化 = 去首尾空白 + 全小写 + 合并连续空格（规范 §4）
 * - 默认只存哈希；「明文上传」开关开启时额外落明文三列
 * - 保留策略：≤90 天、≤5000 条，插入后裁剪（规范 §7.2 保留期）
 * - 无法在统一歌曲表解析的歌（songId 非数字或已下架）直接跳过，不产脏数据
 */
@Singleton
class ResearchEventRecorder @Inject constructor(
    private val musicDao: MusicDao,
    private val researchEventDao: ResearchEventDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    /**
     * 记录一次播放会话。
     *
     * @param songId 域模型 Song.id（统一歌曲表 Long id 的字符串形式）
     * @param listenedMs 本会话实际收听毫秒（结算值，≥5 秒）
     * @param playedAtSec 播放开始时间（Unix 秒；结算时间兜底）
     */
    suspend fun recordSession(
        songId: String,
        listenedMs: Long,
        playedAtSec: Long
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val entity = songId.toLongOrNull()?.let { musicDao.getSongByIdOnce(it) }
                ?: return@runCatching
            val plaintext = userPreferencesRepository.researchPlaintextEnabledFlow.first()
            val nowSec = System.currentTimeMillis() / 1000
            val playedAt = if (playedAtSec in 1 until nowSec) playedAtSec else nowSec
            val durationSec = ((entity.duration / 1000).coerceAtLeast(0)).toInt()
            val playedSec = ((listenedMs / 1000).coerceAtLeast(0)).toInt()
            // 自然播完判定：实际收听 ≥ 总时长 - 5 秒（与 MusicService STATE_ENDED 语义一致）
            val completed = durationSec > 0 && playedSec >= (durationSec - 5).coerceAtLeast(0)

            val event = ListenEventEntity(
                songKey = hash(entity.title),
                titleHash = hash(entity.title),
                artistHash = hash(entity.artistName),
                albumHash = hash(entity.albumName),
                source = mapSource(entity),
                playedAt = playedAt,
                durationSec = durationSec,
                playedSec = playedSec.coerceAtMost((durationSec + 5).coerceAtLeast(0)),
                completed = completed,
                skipped = !completed,
                playMode = "sequential",
                quality = mapQuality(entity),
                plainTitle = entity.title.takeIf { plaintext },
                plainArtist = entity.artistName.takeIf { plaintext },
                plainAlbum = entity.albumName.takeIf { plaintext }
            )
            researchEventDao.insert(event)
            trimRetention(nowSec)
        }.onFailure { error ->
            Timber.w(error, "ResearchEventRecorder: 记录听歌事件失败 songId=%s", songId)
        }
    }

    /** 当前待传条数（达标卡「历史记录」进度） */
    suspend fun pendingCount(): Int = researchEventDao.countPending()

    private suspend fun trimRetention(nowSec: Long) {
        val cutoff = nowSec - TimeUnit.DAYS.toSeconds(RETENTION_DAYS)
        researchEventDao.deleteOlderThan(cutoff)
        researchEventDao.trimToMaxKeep(MAX_KEEP_EVENTS)
    }

    private fun mapSource(entity: SongEntity): String = when (entity.sourceType) {
        SourceType.LOCAL -> "local"
        SourceType.TELEGRAM -> "telegram"
        SourceType.NETEASE -> "netease"
        SourceType.GDRIVE -> "gdrive"
        SourceType.QQMUSIC -> "qq"
        SourceType.NAVIDROME -> "navidrome"
        SourceType.JELLYFIN -> "jellyfin"
        SourceType.CLOUD_LX -> "lx"
        SourceType.BILIBILI -> "bilibili"
        else -> "local"
    }

    private fun mapQuality(entity: SongEntity): String {
        // 从 mimeType / 文件名扩展粗分；规范对 quality 是描述性字段
        val mime = entity.mimeType?.lowercase() ?: return "unknown"
        return when {
            "flac" in mime -> "flac"
            "ape" in mime -> "ape"
            "wav" in mime -> "wav"
            "dsf" in mime || "dff" in mime -> "dsd"
            "aac" in mime || "m4a" in mime -> "aac"
            "ogg" in mime || "opus" in mime -> "ogg"
            "mp3" in mime -> "mp3"
            else -> mime.substringAfterLast('/').substringAfterLast('.').take(12).ifBlank { "unknown" }
        }
    }

    companion object {
        private const val RETENTION_DAYS = 90L
        private const val MAX_KEEP_EVENTS = 5000

        /**
         * 哈希 salt：客户端内置默认值；/me 返回 hashSaltId 时由仓库层存档，预留轮换。
         * 哈希目的是去标识化——不可逆还原歌名（规范 §4/§7.2）。
         */
        const val DEFAULT_HASH_SALT = "xs_research_v1"

        /** 规范化 = 去首尾空白 + 全小写 + 合并连续空格 */
        fun normalize(text: String): String =
            text.trim().lowercase().replace(Regex("\\s+"), " ")

        fun hash(text: String, salt: String = DEFAULT_HASH_SALT): String =
            ResearchSigner.sha256Hex(salt + normalize(text))
    }
}
