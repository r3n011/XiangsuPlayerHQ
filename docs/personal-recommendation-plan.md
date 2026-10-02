# 私人推荐（个性化推荐）落地方案

> 参考实现：`MeloX-main`（iOS/Swift）、`MeloX-Android-main`（Kotlin）
> 目标项目：PixelPlay（`XiangsuPlayerHQ`）
> 状态：方案稿（未实现）

---

## 一、参考实现拆解

### 1.1 数据管道

**MeloX（两端一致）** 的私人推荐不是单一接口，而是一条「**服务端个性化瀑布流 + 逐块本地兜底**」的管道：

| 数据源 | 端点 | 说明 |
|---|---|---|
| 首页瀑布流 | `eapi /api/homepage/block/page` | 一次拿到全部个性化区块：推荐歌单、最近常听、**专属推荐（tailoredSongs）**、排行榜、**雷达歌单**、自建歌单、地区热歌、**漫游歌曲**、**相似推荐（likedSongRecommendations）**、播客 |
| 个性化新歌 | `weapi /api/personalized/newsong`（`type=recommend`） | 推荐新歌，weapi 空响应时回退 eapi |
| 每日推荐 | `eapi /api/v3/discovery/recommend/songs`（登录） | 网易云官方「每日 30 首」 |
| 私人 FM | `eapi /api/v1/radio/get`（`mode=FAMILIAR/EXPLORE`） | 双模式：熟悉 / 探索 |
| 心动模式 | `eapi /api/playmode/intelligence/list` | 从种子歌 + 歌单出发的智能连播 |
| 相似歌曲 | 由喜欢的第一首歌取种子 → similar | 喜欢歌曲的相似推荐 |
| 播客推荐 | `weapi /api/program/recommend/v1` | 播客节目 |

**核心设计：`serverOrFallback(server, fallback)`。** 每个区块先取服务端瀑布流结果，
为空或未登录时用**本地接口逐块补齐**（推荐歌单→`/api/personalized/playlist`、雷达歌单→
用户歌单里筛「雷达」、专属推荐→账号自建歌单、漫游→`personalFm(explore=true)`、相似→
喜欢种子歌曲的 similar）。保证任何登录态/风控状态下首页都有内容，不会出现空区块。

**网络层容错**：weapi 空响应/失败 → 同路径同参数回退 eapi（iOS 的
`homeCompatibleWeapi`、Android 的 `catch IOException("空响应") → eapi`）。

### 1.2 UI 呈现

- **首页（Home）**：瀑布流区块列表，区块顺序由 `HomeSectionKind`/`homeSectionOrder`
  配置（QuickActions / Recommendations / Playlists / NewSongs / Rankings / Artists /
  Radio / Podcasts），可排序、可显隐（与 Rhythm 的首页自定义同思路）。
- **发现（Explore）**：独立页，聚合歌单分类、排行榜、精品歌单、播客。
- 区块统一「标题 + trailing 动作 + 横向卡片列表」语言。

### 1.3 播放体验与反馈闭环

- **私人 FM 无限流**：播放过程中持续补充队列，不会「歌荒」。
- **反馈三键**：喜欢（红心）/ **不感兴趣（垃圾桶 → `fmTrash`，服务端算法直接学习）** /
  下一首；不感兴趣的歌曲从推荐池永久剔除。
- **心动模式**：以当前歌或歌单为种子连续播放相关歌曲。

---

## 二、PixelPlay 现状盘点

### 已有能力

| 能力 | 现状 | 位置 |
|---|---|---|
| 漫游模式（≈私人 FM） | **已实现且带无限流**：`fetchPersonalFmRecommendations` 取一批 → 详情补全 → `playSongs`；每次切歌自动追加 1 首（`loadMoreRoamingSongs`）；URL 过期自动刷新；重复歌自动跳过；VIP 歌延迟插队 | `PersonalFmApi`、`PlayerViewModel.startRoamingMode` |
| 每日 Mix | **本地算法**生成（非网易云官方每日推荐） | `DailyMixManager` |
| AI 合辑 | AI 生成歌单 + 首页卡片 + 独立页 | `AiPlaylistGenerator`、`AiMixScreen` |
| 网易云 SDK 端点储备 | `personalFm(mode)`（F/B/S）、**`fmTrash`（不感兴趣）**、`/api/playmode/intelligence/list`（心动模式）均已定义 | `NcmModulesFull` |
| 首页卡片体系 | 发现 / AI 推荐 / 每日 Mix / 收藏歌手 / 最近播放 / 统计，可排序 + 显隐 | `HomeScreen` + `HomeCardOrderSheet` |

### 差距（对照 MeloX）

1. **漫游没有反馈闭环**：无「不感兴趣（垃圾桶）」按钮（`fmTrash` 已就绪未接）；红心入口
   有但漫游场景没有强化。
2. **私人 FM 没有熟悉/探索模式选择**：`personalFm(mode)` 支持但入口固定默认。
3. **没有网易云官方「每日推荐」**：我们的每日内容是本地算法，缺少官方个性化结果。
4. **没有心动模式**（从当前歌连播相似歌曲）。
5. **没有服务端个性化瀑布流**：推荐歌单 / 雷达歌单 / 专属推荐 / 相似推荐全都没接，
   首页个性化主要靠本地算法和 AI。
6. **网络层容错不统一**：新写的关注/会话已做 weapi→eapi 回退，推荐链路还没。

---

## 三、落地方案（分三期）

### 一期：私人 FM 体验补齐（低成本、高价值）

**改动点**

1. **漫游模式反馈三键**（复用现有播放器控制层）：
   - 「不感兴趣」→ `NcmApi.FullAccess.fmTrash(songId)` + 立即切下一首 + 从当前队列移除该歌；
   - 「喜欢」→ 复用现有 `likeSong`；红心状态实时同步；
   - 位置：播放页在漫游模式下的控制区（或省略号菜单加「不感兴趣」项）。
2. **熟悉/探索双模式**：
   - `PersonalFmApi.fetchPersonalFmRecommendations(cookie, mode)` 透传 `mode`（F=熟悉 / E=探索，
     与 MeloX `FAMILIAR/EXPLORE` 对应，`/api/v1/radio/get` 参数 `mode`）；
   - 入口：漫游加载页 / 播放页菜单，选中后重新拉一批推荐；
   - 记住上次选择（DataStore）。
3. **漫游队列来源多样化**（可选项）：`personalFm` 请求失败时回退「喜欢歌曲的相似推荐」，
   避免无网/风控时漫游直接空。

**验收**：漫游播放中点垃圾桶 → 歌曲立即消失且不再出现；切换「探索」模式 → 推荐明显更
多样；红心状态与账号同步。

---

### 二期：网易云官方每日推荐 + 心动模式

**改动点**

1. **每日推荐数据源**：新增 `NeteaseDailyRecommendApi`（或并入 `NeteaseRecommendApi`）：
   - `eapi /api/v3/discovery/recommend/songs`（`limit=100`），登录可用；
   - 返回歌曲 ID → 现有 `fetchSongDetails` 批量补全 → `Song` 列表；
   - 缓存：按「日期」缓存当日结果（DataStore 存 JSON 或 Room 表），当日不重复拉取；
   - 未登录时自动回退本地 `DailyMixManager`。
2. **UI**：
   - 首页新增「每日推荐」卡片（与现有 Daily Mix 卡片并列，纳入排序/显隐体系；
     两者都保留，用户可关掉其一）；
   - 独立列表页（播放全部 / 收藏全部）。
3. **心动模式**：
   - `NcmApi` 已有 `/api/playmode/intelligence/list`，封装为
     `startHeartMode(seedSongId, playlistId)`；
   - 入口：播放页省略号菜单「心动模式」→ 以当前歌 + 当前队列为种子拉取 → 无缝续播；
   - 与漫游模式同样的「切歌自动补充」无限流（复用 `loadMoreRoamingSongs` 的通用化版本）。

**验收**：登录后首页出现「每日推荐」，与网易云 App 当日列表一致；心动模式从任意歌
进入都能连播相似歌且自动续流。

---

### 三期：首页个性化瀑布流（对标 MeloX 的 serverOrFallback）

**改动点**

1. **新增 `HomeRecommendRepository`**：
   ```
   fetchHomeBlocks() = eapi /api/homepage/block/page（登录时）
       → 解析区块：推荐歌单 / 雷达歌单 / 专属推荐 / 相似推荐 / 最近常听
   每块 serverOrFallback 到本地接口：
       推荐歌单 → /api/personalized/playlist
       雷达歌单 → 用户歌单筛「雷达」
       专属推荐 → /api/personalized/newsong (type=recommend)
       相似推荐 → 喜欢种子歌 similar
   ```
   - 网络层统一走已实现的 weapi→eapi 回退模式；
   - 区块结果按日期/会话缓存，下拉刷新时 `refresh=true` 重拉。
2. **首页新卡片**（沿用现有卡片体系，每块一张卡/一块横滑列表）：
   - 「推荐歌单」（横滑歌单卡）
   - 「为你推荐」（专属歌曲横滑卡，可直接播放）
   - 「雷达歌单」（横滑）
   - 均在「自定义首页」里可排序 / 可隐藏。
3. **未登录降级**：全部走本地接口（推荐歌单/新歌不需要登录），只是少了「专属/雷达」。

**验收**：登录账号打开首页能看到与网易云 App 类似的个性化歌单/歌曲区块；断网或
未登录时区块自动降级为本地推荐而非空白。

---

## 四、技术要点与风险

| 项 | 说明 |
|---|---|
| 双通道容错 | 推荐链路统一 `weapi → eapi` 回退（照 `fetchWithEapiFallback`），避免「偶尔空列表」 |
| 登录态 | 官方每日推荐/瀑布流/专属推荐需登录；未登录全程本地兜底，UI 不出现死区块 |
| 风控 | 瀑布流 eapi 对参数敏感（`refresh/cursor` 形状按 NeteaseCloudMusicApi 校验）；批量详情走 `song/detail` 分片（≤100/次） |
| 缓存 | 每日推荐按天缓存；瀑布流按会话缓存；避免频繁请求触发风控 |
| 与现有模式的关系 | 漫游（私人 FM）保留为「无限电台」；每日推荐是「每日定番」；心动模式是「相似连播」——三者互不替代，入口区分 |
| 数据模型 | 新增 `RecommendSection(type, title, songs/playlists)`，UI 卡片由 type 分发渲染 |

## 五、建议实施顺序

1. 一期（1~2 天）：垃圾桶 + 双模式 + 红心强化 —— 全部复用现有端点，改动集中在
   `PlayerViewModel` 漫游逻辑与播放器菜单。
2. 二期（2~3 天）：官方每日推荐 + 心动模式 —— 新 API 封装 + 首页卡片 + 连播。
3. 三期（3~5 天）：瀑布流仓库 + 首页区块 —— 新 Repository + 3 张新卡片 + 排序显隐接入。

每期独立可交付，互不阻塞。
