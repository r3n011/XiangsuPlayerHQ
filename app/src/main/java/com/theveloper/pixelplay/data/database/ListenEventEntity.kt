package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「听歌数据研究计划」待上传听歌事件队列（规范 03 §4 listen_events 语义）。
 *
 * 每次播放会话结算后插入一条；上传成功即删除，失败保留待重试。
 * 默认只存哈希（歌名/歌手/专辑按 salt+规范化文本 哈希），明文三列仅在用户
 * 开启明文上传时写入。保留策略：≤5000 条且 ≤90 天，插入时裁剪。
 */
@Entity(
    tableName = "listen_events",
    indices = [
        Index(value = ["played_at"]),
        Index(value = ["batch_id"])
    ]
)
data class ListenEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** sha256(salt + 规范化(歌名))，规范要求的服务端主键哈希 */
    @ColumnInfo(name = "song_key")
    val songKey: String,

    @ColumnInfo(name = "title_hash")
    val titleHash: String,

    @ColumnInfo(name = "artist_hash")
    val artistHash: String,

    @ColumnInfo(name = "album_hash")
    val albumHash: String,

    /** 来源：local / netease / qq / kugou / navidrome / jellyfin / gdrive … */
    @ColumnInfo(name = "source")
    val source: String,

    /** 播放开始时间（Unix 秒） */
    @ColumnInfo(name = "played_at")
    val playedAt: Long,

    /** 歌曲总时长（秒） */
    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,

    /** 实际收听时长（秒） */
    @ColumnInfo(name = "played_sec")
    val playedSec: Int,

    @ColumnInfo(name = "completed")
    val completed: Boolean,

    @ColumnInfo(name = "skipped")
    val skipped: Boolean,

    /** sequential / shuffle / repeat_one */
    @ColumnInfo(name = "play_mode")
    val playMode: String,

    /** flac / mp3 / aac / … */
    @ColumnInfo(name = "quality")
    val quality: String,

    /** 上传批次 id（分批时标记归属；上传成功后整批删除） */
    @ColumnInfo(name = "batch_id")
    val batchId: String? = null,

    @ColumnInfo(name = "plain_title")
    val plainTitle: String? = null,

    @ColumnInfo(name = "plain_artist")
    val plainArtist: String? = null,

    @ColumnInfo(name = "plain_album")
    val plainAlbum: String? = null
)
