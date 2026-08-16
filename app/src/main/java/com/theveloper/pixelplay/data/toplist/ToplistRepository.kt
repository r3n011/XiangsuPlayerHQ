package com.theveloper.pixelplay.data.toplist

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多平台排行榜歌曲仓库：
 * - 全量歌曲内存缓存（ConcurrentHashMap<entryId, List<Song>>）
 * - inFlight 防重复请求（同一榜单并发只发一次请求，Deferred 共享）
 * - wy 走 NeteaseRepository（官方歌单明文 API），tx/kg/kw/mg 走 ToplistFetchApi
 */
@Singleton
class ToplistRepository @Inject constructor(
    private val neteaseRepository: NeteaseRepository,
    private val toplistFetchApi: ToplistFetchApi
) {
    private val cache = ConcurrentHashMap<String, List<Song>>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<List<Song>>>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cached(entryId: String): List<Song>? = cache[entryId]

    fun evict(entryId: String) {
        cache.remove(entryId)
    }

    suspend fun getToplistSongs(entryId: String, refresh: Boolean = false): Result<List<Song>> {
        if (!refresh) {
            cache[entryId]?.let { return Result.success(it) }
        }
        val existing = inFlight[entryId]
        if (existing != null && !refresh) return existing.await()

        val entry = ToplistCatalog.findEntry(entryId)
            ?: return Result.failure(Exception("Unknown toplist: $entryId"))

        val deferred = scope.async {
            if (entry.platform == ToplistCatalog.Platform.WY) {
                // 网易云官方歌单：一次全量拉取
                neteaseRepository.getToplistSongs(entry.bangid.toLongOrNull() ?: 0L)
            } else {
                toplistFetchApi.fetch(entry.platform, entry)
            }
        }
        inFlight[entryId] = deferred
        return try {
            val result = deferred.await()
            if (result.isSuccess) cache[entryId] = result.getOrThrow()
            result
        } finally {
            inFlight.remove(entryId)
        }
    }

    fun clearAll() {
        cache.clear()
    }
}
