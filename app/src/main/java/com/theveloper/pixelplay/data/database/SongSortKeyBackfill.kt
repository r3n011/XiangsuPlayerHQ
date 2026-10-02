package com.theveloper.pixelplay.data.database

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.theveloper.pixelplay.utils.PinyinSortKey
import timber.log.Timber

/**
 * 拼音排序键回填。
 *
 * 46→47 迁移只给 songs 表加了三个排序键列（毫秒级、无锁表风险），历史数据没有键；
 * 这里在应用启动后于 IO 线程分批补齐（每批 [BATCH_SIZE] 行），补齐前排序 SQL 用
 * `IFNULL(key, X'')` 兜底，不会报错。新入库的歌由 [SongEntity] 构造时自动带上键，
 * 所以正常情况下只有升级后的首次启动需要跑一次。
 */
@Singleton
class SongSortKeyBackfill @Inject constructor(
    private val musicDao: MusicDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** 幂等触发：已在跑时直接返回。 */
    fun ensureBackfilledAsync() {
        scope.launch {
            mutex.withLock {
                try {
                    val total = musicDao.countSongsMissingSortKeys()
                    if (total <= 0) return@withLock
                    Timber.i("SongSortKeyBackfill: %d songs missing pinyin sort keys", total)
                    var filled = 0
                    while (true) {
                        val rows = musicDao.getSongsMissingSortKeys(BATCH_SIZE)
                        if (rows.isEmpty()) break
                        for (row in rows) {
                            musicDao.updateSongSortKeys(
                                id = row.id,
                                titleKey = PinyinSortKey.of(row.title),
                                artistKey = PinyinSortKey.of(row.artistName),
                                albumKey = PinyinSortKey.of(row.albumName),
                            )
                        }
                        filled += rows.size
                    }
                    Timber.i("SongSortKeyBackfill: filled %d songs", filled)
                } catch (t: Throwable) {
                    // 失败不致命：排序 SQL 有 IFNULL 兜底，下次启动会重试
                    Timber.w(t, "SongSortKeyBackfill failed; will retry on next launch")
                }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 300
    }
}
