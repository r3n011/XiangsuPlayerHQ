package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 听歌事件队列 DAO：插入（播放结算时）、待传查询、上传成功删除、容量/保留期裁剪。
 */
@Dao
interface ResearchEventDao {

    @Insert
    suspend fun insert(event: ListenEventEntity): Long

    @Query("SELECT COUNT(*) FROM listen_events")
    suspend fun countPending(): Int

    /** 本地已采集条数（用于达标卡「历史记录 12 / 20」进度；区分来源可选） */
    @Query("SELECT COUNT(*) FROM listen_events")
    fun observeCount(): Flow<Int>

    /** 最旧一条的 playedAt（用于 90 天保留期裁剪） */
    @Query("SELECT MIN(played_at) FROM listen_events")
    suspend fun oldestPlayedAt(): Long?

    @Query("SELECT * FROM listen_events ORDER BY played_at ASC, id ASC LIMIT :limit")
    suspend fun pendingBatch(limit: Int): List<ListenEventEntity>

    @Query("DELETE FROM listen_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 失败批次重试前，解除占用（换新 batchId 或清空标记） */
    @Query("UPDATE listen_events SET batch_id = NULL WHERE batch_id = :batchId")
    suspend fun clearBatchId(batchId: String)

    /** 保留期裁剪：删除 90 天前的旧事件 */
    @Query("DELETE FROM listen_events WHERE played_at < :cutoffEpochSec")
    suspend fun deleteOlderThan(cutoffEpochSec: Long): Int

    /** 容量裁剪：只保留最新 maxKeep 条 */
    @Query(
        "DELETE FROM listen_events WHERE id NOT IN " +
            "(SELECT id FROM listen_events ORDER BY played_at DESC, id DESC LIMIT :maxKeep)"
    )
    suspend fun trimToMaxKeep(maxKeep: Int): Int
}
