package com.theveloper.pixelplay.data.database

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.atomic.AtomicBoolean

/**
 * songs / lyrics 表「单行过大」防护：审计 + 每进程一次的钳制（clamp）。
 *
 * 背景（线上崩溃：`MusicDao_Impl.getAllSongs` → `IllegalStateException: Couldn't read row 600,
 * col 0 from CursorWindow. Make sure the Cursor is initialized correctly ...`）：
 *
 * Android 把查询结果放进 2MB 的 CursorWindow。当**某一行**超过窗口容量时，
 * `SQLiteCursor.fillWindow()` 一行都填不进去，但 `onMove()` 依旧无条件返回 true，于是
 * `moveToNext()` 报告成功，真正的 `getLong()/getString()` 才抛异常——所以堆栈里的行号只是
 * 「窗口装不下的那一行」，根因是该行本身。`SQLiteQuery.fillWindow()` 还会吞掉
 * `IllegalStateException` 并返回 0，这让崩溃只表现为读取端的异常。
 *
 * 防护分三层：
 *  1. 读侧：[SONG_LIST_PROJECTION] / [SONG_DETAIL_PROJECTION] 对所有 TEXT 列 substr() 截断，
 *     保证主干查询（含崩溃的 getAllSongs）单行永远进得了窗口（见 MusicDao.kt）。
 *  2. 数据侧（本类）：把每一列钳到 32K 字符以内 —— 即使是没走投影的 `SELECT *` 查询
 *     （分页、专辑、歌手、搜索等约 15 处）也不会再读到能撑爆窗口的行。
 *  3. 读侧兜底：[MusicRepositoryImpl] 捕获读取异常后降级为分块读取。
 *
 * 钳制阈值与破坏性说明：
 *  - 每列 32K 字符：`substr()` 按**字符**截断，而 CursorWindow 里存的是 UTF-8 **字节**
 *    （BMP 字符最多 3 字节/字符）。15 个可变长列全部拉满时
 *    15 × 32,768 × 3 ≈ 1.41MB < 2MB 窗口 —— 即使极端情况也被证明装得下。
 *    而正常值（标题/艺人/URI/路径/JSON）远低于 32K 字符，只有解析异常的脏数据
 *    （损坏的 ID3 标签、误存的整段响应体等）才会被截断，且这些值本来就会让 UI 无法使用。
 *  - `songs.lyrics` 超过 32K 字符直接置 NULL 而非截断：歌词已有 `lyrics` 表这个正式副本
 *    （与 MIGRATION_24_25 的动机一致），置空后播放页会回退到歌词表/在线拉取，
 *    比留半截损坏的 LRC 更好。
 *  - `lyrics.content` 钳到 256K 字符，与 [SONG_DETAIL_PROJECTION] 的歌词读取上限一致
 *    （单列最坏 786KB 字节，仍远低于 2MB）。
 *  - `content_uri_string` / `file_path` 只截断到 32K 字符（远高于任何可用 URI/路径的长度），
 *    不清空：清空会让歌直接失效，截断则保留可诊断的现场。
 *
 * 审计日志（tag: [TAG]）会打印 payload 最大的若干行逐列长度，下次崩溃报告可直接指认膨胀列。
 */
object SongPayloadGuard {

    private const val TAG = "SongPayloadGuard"

    /**
     * songs 每个可变长列的字符上限（详见类注释里的字节预算推导）。
     *
     * 32K 而非 64K：`substr()` 按字符截断、窗口按 UTF-8 字节计费（最多 3 字节/字符），
     * 15 × 32,768 × 3 ≈ 1.41MB 才能对 2MB 窗口给出硬保证。
     */
    private const val MAX_SONG_COLUMN_CHARS = 32_768

    /** lyrics 表 content 的上限，与 SONG_DETAIL_PROJECTION 的歌词读取上限一致。 */
    private const val MAX_LYRICS_CONTENT_CHARS = 262_144

    /** 单行各列长度之和超过该值就值得一条告警日志（远低于钳制线，提前预警）。 */
    private const val AUDIT_THRESHOLD_CHARS = 8_192
    private const val AUDIT_LIMIT = 5

    /**
     * 需要钳制的 songs 文本列（均为 Room 实体中的 TEXT/BLOB 列）。
     * `lyrics` 单独处理（超限置 NULL，见类注释），因此不在此列。
     * 列名全部来自本文件的常量，不接受外部输入。
     */
    private val CLAMPABLE_SONG_COLUMNS = listOf(
        "title",
        "artist_name",
        "album_name",
        "album_artist",
        "genre",
        "content_uri_string",
        "album_art_uri_string",
        "file_path",
        "parent_directory_path",
        "mime_type",
        "artists_json",
        "title_sort_key",
        "artist_sort_key",
        "album_sort_key"
    )

    private val ranOnce = AtomicBoolean(false)

    /**
     * 由 Room 的 `onOpen` 回调触发，每进程只执行一次（Room 每个进程只打开一次数据库）。
     * 两次整表扫描，万行规模几十毫秒，且只发生在首次打开数据库时。
     * 自带 try/catch：防护本身绝不能把启动拖崩，失败只降级为「无数据侧钳制」，读侧截断仍在。
     */
    fun runOnce(db: SupportSQLiteDatabase) {
        if (!ranOnce.compareAndSet(false, true)) return
        try {
            audit(db)
            clearOversizedEmbeddedLyrics(db)
            clampOversizedSongColumns(db)
            clampOversizedLyricsContent(db)
        } catch (t: Throwable) {
            Log.e(TAG, "song payload guard failed", t)
        }
    }

    /** 打印 payload 最大的若干行，逐列长度便于直接定位膨胀列。 */
    private fun audit(db: SupportSQLiteDatabase) {
        db.query(AUDIT_SQL, arrayOf<Any>(AUDIT_THRESHOLD_CHARS)).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val title = cursor.getInt(1)
                val artist = cursor.getInt(2)
                val album = cursor.getInt(3)
                val albumArtist = cursor.getInt(4)
                val genre = cursor.getInt(5)
                val contentUri = cursor.getInt(6)
                val albumArt = cursor.getInt(7)
                val filePath = cursor.getInt(8)
                val parentDir = cursor.getInt(9)
                val artistsJson = cursor.getInt(10)
                val lyrics = cursor.getInt(11)
                val titleSortKey = cursor.getInt(12)
                val artistSortKey = cursor.getInt(13)
                val albumSortKey = cursor.getInt(14)
                val total = title + artist + album + albumArtist + genre + contentUri +
                    albumArt + filePath + parentDir + artistsJson + lyrics +
                    titleSortKey + artistSortKey + albumSortKey
                Log.w(
                    TAG,
                    "oversized songs row id=$id total=$total chars/bytes " +
                        "(title=$title, artist=$artist, album=$album, albumArtist=$albumArtist, " +
                        "genre=$genre, contentUri=$contentUri, albumArt=$albumArt, " +
                        "filePath=$filePath, parentDir=$parentDir, artistsJson=$artistsJson, " +
                        "lyrics=$lyrics, titleSortKey=$titleSortKey, " +
                        "artistSortKey=$artistSortKey, albumSortKey=$albumSortKey)"
                )
            }
        }
    }

    /** 超长的 songs.lyrics 副本直接置空（正式歌词在 lyrics 表）。 */
    private fun clearOversizedEmbeddedLyrics(db: SupportSQLiteDatabase) {
        val affected = db.compileStatement(CLEAR_SONGS_LYRICS_SQL).use { it.executeUpdateDelete() }
        if (affected > 0) {
            Log.w(TAG, "cleared songs.lyrics of $affected row(s) longer than $MAX_SONG_COLUMN_CHARS chars")
        }
    }

    /** 把 songs 的可变长列统一钳到 [MAX_SONG_COLUMN_CHARS]，使任意查询都不会读到超大行。 */
    private fun clampOversizedSongColumns(db: SupportSQLiteDatabase) {
        val sets = CLAMPABLE_SONG_COLUMNS.joinToString(",\n    ") { column ->
            "$column = CASE WHEN length($column) > $MAX_SONG_COLUMN_CHARS " +
                "THEN substr($column, 1, $MAX_SONG_COLUMN_CHARS) ELSE $column END"
        }
        val where = CLAMPABLE_SONG_COLUMNS.joinToString(" OR ") { column ->
            "length($column) > $MAX_SONG_COLUMN_CHARS"
        }
        val affected = db.compileStatement("UPDATE songs SET\n    $sets\nWHERE $where")
            .use { it.executeUpdateDelete() }
        if (affected > 0) {
            Log.w(
                TAG,
                "clamped $affected songs row(s): one or more columns longer than " +
                    "$MAX_SONG_COLUMN_CHARS chars (${CLAMPABLE_SONG_COLUMNS.joinToString()})"
            )
        }
    }

    /** lyrics 表 content 与读取上限对齐（异常保存的整段响应体等）。 */
    private fun clampOversizedLyricsContent(db: SupportSQLiteDatabase) {
        val affected = db.compileStatement(
            "UPDATE lyrics SET content = substr(content, 1, $MAX_LYRICS_CONTENT_CHARS) " +
                "WHERE length(content) > $MAX_LYRICS_CONTENT_CHARS"
        ).use { it.executeUpdateDelete() }
        if (affected > 0) {
            Log.w(TAG, "clamped lyrics.content of $affected row(s) longer than $MAX_LYRICS_CONTENT_CHARS chars")
        }
    }

    // 非 const：SQL 里内嵌了 MAX_SONG_COLUMN_CHARS 的字符串插值
    private val CLEAR_SONGS_LYRICS_SQL =
        "UPDATE songs SET lyrics = NULL WHERE length(ifnull(lyrics, '')) > $MAX_SONG_COLUMN_CHARS"

    // 非 const：SQL 里内嵌了 AUDIT_LIMIT 的字符串插值
    // BLOB 排序列用 ifnull(x, x'') —— length() 对 BLOB 返回字节数，对 TEXT 返回字符数，
    // 这样无论膨胀来自文本列还是排序键列，审计都能看到（钳制会对两者都生效）。
    private val AUDIT_SQL = """
        SELECT id,
               length(ifnull(title, '')),
               length(ifnull(artist_name, '')),
               length(ifnull(album_name, '')),
               length(ifnull(album_artist, '')),
               length(ifnull(genre, '')),
               length(ifnull(content_uri_string, '')),
               length(ifnull(album_art_uri_string, '')),
               length(ifnull(file_path, '')),
               length(ifnull(parent_directory_path, '')),
               length(ifnull(artists_json, '')),
               length(ifnull(lyrics, '')),
               length(ifnull(title_sort_key, x'')),
               length(ifnull(artist_sort_key, x'')),
               length(ifnull(album_sort_key, x''))
        FROM songs
        WHERE (length(ifnull(title, '')) + length(ifnull(artist_name, '')) +
               length(ifnull(album_name, '')) + length(ifnull(album_artist, '')) +
               length(ifnull(genre, '')) + length(ifnull(content_uri_string, '')) +
               length(ifnull(album_art_uri_string, '')) + length(ifnull(file_path, '')) +
               length(ifnull(parent_directory_path, '')) + length(ifnull(artists_json, '')) +
               length(ifnull(lyrics, '')) + length(ifnull(title_sort_key, x'')) +
               length(ifnull(artist_sort_key, x'')) + length(ifnull(album_sort_key, x''))) > ?
        ORDER BY (length(ifnull(title, '')) + length(ifnull(artist_name, '')) +
                  length(ifnull(album_name, '')) + length(ifnull(album_artist, '')) +
                  length(ifnull(genre, '')) + length(ifnull(content_uri_string, '')) +
                  length(ifnull(album_art_uri_string, '')) + length(ifnull(file_path, '')) +
                  length(ifnull(parent_directory_path, '')) + length(ifnull(artists_json, '')) +
                  length(ifnull(lyrics, '')) + length(ifnull(title_sort_key, x'')) +
                  length(ifnull(artist_sort_key, x'')) + length(ifnull(album_sort_key, x''))) DESC
        LIMIT $AUDIT_LIMIT
    """
}
