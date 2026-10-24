package com.theveloper.pixelplay.data.netease

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主页推荐卡片的**数据编排**（对齐 lx-music `homeContent` 的合并规则）。
 *
 * 职责：
 *  1. 拉取 server blocks（登录才有）与各兜底接口；
 *  2. 按「server 非空则用 server，否则用兜底」逐栏目合并；
 *  3. 做**去重保护**：`今日云村热播` 与 `全部今日热门` 在未登录时兜底是**同一个接口**，
 *     必须避免首页出现两张一模一样的卡片；
 *  4. 当天结果缓存（内存），避免反复打接口。
 *
 * 每日推荐不在这里 —— 它复用 `PlayerViewModel.dailyRecommendState` 既有链路。
 */
@Singleton
class HomeFeedRepository @Inject constructor(
    private val api: NeteaseHomeFeedApi,
    private val neteaseRepository: NeteaseRepository,
) {

    private companion object {
        private const val TAG = "HomeFeedRepository"

        /** 每个栏目取多少首（对齐参照实现的 limit=12） */
        private const val SECTION_LIMIT = 12

        /** 全部今日热门：既不与云村热播重复、也保留足够多条目 */
        private const val HOT_LIMIT = 20
    }

    private data class CacheEntry(
        val dayKey: Int,
        val area: String,
        val sections: HomeFeedSections,
    )

    private val mutex = Mutex()
    private var cache: CacheEntry? = null

    private fun todayKey(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 取主页在线推荐卡片的数据。
     *
     * @param forceRefresh 用户下拉刷新/主动刷新时传 true，忽略当天缓存
     * @param area 地区偏好（华语/日本/韩国/欧美/其它=全部），决定「全部今日热门」的 areaId
     */
    suspend fun loadSections(forceRefresh: Boolean = false, area: String = "全部"): HomeFeedSections =
        mutex.withLock {
            val dayKey = todayKey()
            cache
                ?.takeIf { !forceRefresh && it.dayKey == dayKey && it.area == area }
                ?.let { return@withLock it.sections }

            val sections = runCatching { fetch(forceRefresh, area) }
                .onFailure { Timber.w(it, "$TAG: loadSections failed") }
                .getOrDefault(HomeFeedSections())

            if (!sections.isEmpty) {
                cache = CacheEntry(dayKey = dayKey, area = area, sections = sections)
            }
            sections
        }

    /** 清空缓存（登录状态变化、地区设置变化时调用）。 */
    fun invalidate() {
        cache = null
    }

    // ─── 内部 ────────────────────────────────────────────────────────────

    private suspend fun fetch(forceRefresh: Boolean, area: String): HomeFeedSections {
        val cookie = neteaseRepository.getCookieString()
        val loggedIn = neteaseRepository.isLoggedIn
        val areaId = NeteaseHomeFeedApi.areaId(area)

        // ① server blocks（未登录时跳过，接口必然没有个性化内容）
        val blocks = if (loggedIn) {
            runCatching { api.fetchHomeBlocks(refresh = forceRefresh, cookie = cookie).getOrThrow() }
                .onFailure { Timber.w(it, "$TAG: 主页 blocks 拉取失败，全部走兜底") }
                .getOrNull()
                ?.let { root -> runCatching { api.parseHomeBlocks(root) }.getOrDefault(emptyList()) }
                .orEmpty()
        } else {
            emptyList()
        }

        fun blockOf(kind: HomeBlockKind): List<HomeFeedTrack> =
            blocks.firstOrNull { it.kind == kind }?.tracks.orEmpty()

        // ② 匿名/无个性化时也准备一份「全部今日热门」池子：
        //    personalized/newsong 与 v1/discovery/new/songs 都是完整歌曲对象，
        //    解析修复后这里通常都能拿到数据；两个卡片用不同切片，避免完全重复。
        val discoveryPool = runCatching {
            api.fetchDiscoveryNewSongs(
                areaId = 0,
                limit = SECTION_LIMIT + HOT_LIMIT,
                cookie = cookie,
            ).getOrThrow()
        }.getOrDefault(emptyList())

        // ③ 为你推荐：个性化新歌优先；接口无数据时用今日热门池兜底，保证卡片不消失
        val forYou = runCatching {
            api.fetchPersonalizedNewSongs(areaId = areaId, limit = SECTION_LIMIT, cookie = cookie).getOrThrow()
        }.getOrDefault(emptyList())
            .ifEmpty { discoveryPool.take(SECTION_LIMIT) }

        // ④ 今日云村热播：server block 优先，兜底今日热门(全部)前 12 首
        val blockTrending = blockOf(HomeBlockKind.RECENTLY_TRENDING)
        val todayTrending = blockTrending.ifEmpty {
            discoveryPool.take(SECTION_LIMIT)
        }

        // ⑤ 全部今日热门：server block 优先；兜底使用同一个池子的后续条目，
        //    不足时退回首段（宁可内容有重叠，也不要卡片永远不显示）。
        val blockHot = blockOf(HomeBlockKind.REGIONAL_SONGS)
        val todayHot = blockHot.ifEmpty {
            if (areaId == 0) {
                discoveryPool.drop(SECTION_LIMIT).take(HOT_LIMIT)
                    .ifEmpty { discoveryPool.take(HOT_LIMIT) }
            } else {
                runCatching {
                    api.fetchDiscoveryNewSongs(areaId = areaId, limit = HOT_LIMIT, cookie = cookie).getOrThrow()
                }.getOrDefault(emptyList())
            }
        }

        Timber.d(
            "$TAG: loaded forYou=${forYou.size} trending=${todayTrending.size} hot=${todayHot.size} " +
                "(loggedIn=$loggedIn, area=$area)"
        )

        return HomeFeedSections(
            forYou = forYou,
            todayTrending = todayTrending,
            todayHot = todayHot,
        )
    }
}
