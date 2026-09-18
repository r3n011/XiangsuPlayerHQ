# 像素音源市场（Source Market）实现计划

## Context

当前落雪 JS 引擎内置的两个"全豆要"音源脚本（`assets/lx_user_js/` 下）因内部聚合 API 失效已全部无法使用，且 `LxFileStore.ensureBundledSources()` 的"内置内容比对 → 强制覆盖"逻辑会把失效旧版反复同步给用户，没有在线更新通道。

本功能：
1. **删除内置 JS**（assets 目录 + 老用户 filesDir 残留自动清理）
2. 新增**像素音源市场**：从固定 GitHub 仓库 `guoyue2010/lxmusic-` 的 releases 拉取音源 zip 包，下载 zip → 解压列出其中的 .js → **用户勾选单个 js 安装** → 重载引擎
3. 入口放在**在线音源设置页**（CloudMusicSettingsScreen）

## 已确认决策

- 安装粒度：zip 解压后列出 js，用户勾选单个安装
- 入口：CloudMusicSettingsScreen（HeroSourceCard 之后、"已安装音源"标题之前）
- 旧内置：升级后首次启动自动清理（3 个文件名 + bundled.flag）
- 数据源：固定 `guoyue2010/lxmusic-`

## 可复用现状（已核实）

- `GitHubRelease`/`GitHubReleaseAsset` 数据类在 `data/github/UpdateChecker.kt:14-30`，字段含 `tag_name/name/body/published_at/assets[].browser_download_url/size`，直接复用
- GitHub API 范式：`UpdateChecker.fetchReleases()`(:111) 的 HttpURLConnection + `Json { ignoreUnknownKeys = true }` + kotlinx.serialization
- `LxFileStore`（data/lx/LxFileStore.kt）：`uniqueName()`(:139) 防冲突、`listFiles()`(:118)、`deleteByName()`(:226)、`decodeJsBytes()`(:208)
- `LxJsEngine.scriptInfos()`(:270)、`parseScriptInfo()`(:823，解析 @name/@version/@author/@lastUpdate/@md5)、`reload()`(:262)
- `LxMusicViewModel.init`(:78-88) 是 `ensureBundledSources()` 唯一调用点
- zip 解压参考：`data/backup/format/BackupReader.kt` ZipInputStream 用法
- 下载进度范式：`data/github/ApkDownloadInstaller.kt` Flow<DownloadState>；镜像前缀列表在该文件 :47 私有常量，需复制
- 推入式路由范式：`Screen.Accounts` / `ScreenWrapper`（AppNavigation.kt），`navigateSafely`（NavControllerExtensions.kt:19）
- material-icons-extended 已依赖，可用 `Icons.Rounded.Storefront/Download/CheckCircle`

---

## 实施步骤

### 步骤 A：删除内置 JS + 清理改造

**A1. 删除 assets 目录**：`app/src/main/assets/lx_user_js/` 整个目录（2 个失效 js）

**A2. LxFileStore.kt 改造**：
- 删除 `ensureBundledSources()`(:44-91)、`bundledAssetDir`、`bundledDoneFlag`、`copyBundledToUser()`(:94)
- `cleanupLegacyBundledSources()` 改 public，名单扩为 3 个旧文件名：
  `"lx音源快速.js"`、`"(推荐)全豆要-聚合音源 v4.1.js"`、`"全豆要-聚合音源 v9.3 93网易云音质修复版.js"`，并删除过期的 `lx_user_js_bundled.flag`
- 新增 `suspend fun writeBytes(bytes: ByteArray, baseName: String): String?`（uniqueName + 写文件 + length>0 校验，同 writeFromUri 模式）
- `decodeJsBytes()` 改 `internal`

**A3. LxJsEngine.kt**：新增 `suspend fun scriptInfoFromBytes(bytes: ByteArray, fileName: String): LxScriptInfo`（调 `decodeJsBytes` + 现有 `parseScriptInfo`）

**A4. LxMusicViewModel.kt init**（:78-88）：删除 `ensureBundledSources()` 调用与"imported 非空 reload"分支，改为：
```kotlin
init {
    viewModelScope.launch(Dispatchers.IO) {
        store.cleanupLegacyBundledSources()  // 内置已下线，仅清理旧残留
        autoInitIfPresent()
    }
    // 音质同步逻辑不动
}
```

### 步骤 B：数据层（新包 data/sourcemarket/）

**B1. `MarketModels.kt`**：
```kotlin
data class MarketJsEntry(val fileName: String, val size: Long, val bytes: ByteArray)
sealed interface ZipDownloadState {
    data class Downloading(val progress: Float) : ZipDownloadState
    data class Downloaded(val file: File) : ZipDownloadState
    data class Error(val message: String) : ZipDownloadState
}
```

**B2. `SourceMarketRepository.kt`**（@Singleton，注入 @ApplicationContext）：
- 常量：`REPO_OWNER="guoyue2010"`、`REPO_NAME="lxmusic-"`、`MAX_ENTRY_BYTES=4MB`（防 zip bomb）、镜像前缀列表（复制 ApkDownloadInstaller）
- `fetchReleases(): Result<List<GitHubRelease>>`：GET `api.github.com/repos/guoyue2010/lxmusic-/releases?per_page=100`（复用 UpdateChecker 的 HttpURLConnection + json 模式）
- `zipAssetOf(release): GitHubReleaseAsset?`：assets 里第一个 `.zip`
- `cachedZipFile(tag): File`：`cacheDir/source_market/{tag}.zip`
- `downloadZip(asset, tag): Flow<ZipDownloadState>`：镜像优先+官方兜底 → 校验 2xx、content-type、`PK\x03\x04` 魔数 → 写缓存
- `inspectZip(zipFile): List<MarketJsEntry>`：ZipInputStream UTF-8 读取；只收 `*.js`；取 basename（`substringAfterLast('/').substringAfterLast('\\')`）防目录穿越；条目字节上限 4MB；同名去重

### 步骤 C：SourceMarketViewModel

`@HiltViewModel`，注入 repo + LxFileStore + LxJsEngine。
- UiState：`releases / loadingReleases / releasesError / expandedTag / releaseStates: Map<String, ReleaseState> / installing / installError / installedNames`
- `ReleaseState`：`Idle / Downloading(progress) / Inspecting / Inspected(entries: List<MarketJsEntryUi>) / Error(msg)`；`MarketJsEntryUi` = entry + 头部解析的 name/version/author + installed 标记
- 方法：`loadReleases()`、`toggleExpand(tag)`（缓存命中→inspectZip，否则 downloadZip 流式→inspectZip）、`installEntry(tag, entry)`（fileStore.writeBytes → engine.reload() → 刷新 installedNames）、`refreshInstalled()`
- Inspecting 阶段对每个 entry 调 `engine.scriptInfoFromBytes()` 解析头部信息

### 步骤 D：SourceMarketScreen

对齐 CloudMusicSettingsScreen 视觉（折叠顶栏 `CollapsibleCommonTopBar` + LazyColumn + haze）：
- 顶部说明卡（Surface + AbsoluteSmoothCornerShape + Storefront 图标 + repo 名 + 刷新按钮）
- ReleaseCard：tag_name + 日期 + body 摘要（可展开）+ zip asset 名/大小 + 展开指示
- 展开区：下载进度（LinearProgressIndicator）→ js 条目行（文件名 + @name/@version/@author + 右侧"已安装"徽标 / "安装"按钮 / 安装中进度圈）
- 空态/错误态卡片 + Snackbar 提示安装结果

### 步骤 E：导航与入口

- `Screen.kt`：新增 `object SourceMarket : Screen("source_market")`
- `AppNavigation.kt`：注册 `composable(Screen.SourceMarket.route)` + `ScreenWrapper`（仿 Screen.Accounts）
- `MainActivity.kt`：`routesWithHiddenNavigationBar` 集合追加路由
- `CloudMusicSettingsScreen.kt`：签名加 `onOpenMarket: () -> Unit`；HeroSourceCard item 之后插入 `MarketEntryCard`（Storefront 图标 + "像素音源市场" + 副标题 + 箭头），`clickable(onOpenMarket)`
- `TabContentHost.kt:137`：调用处补 `onOpenMarket = { navController.navigateSafely(Screen.SourceMarket.route) }`

---

## 涉及文件清单

**新增**：
1. `data/sourcemarket/SourceMarketRepository.kt`
2. `data/sourcemarket/MarketModels.kt`
3. `presentation/viewmodel/SourceMarketViewModel.kt`
4. `presentation/screens/SourceMarketScreen.kt`

**修改**：
5. `data/lx/LxFileStore.kt`
6. `data/lx/LxJsEngine.kt`
7. `presentation/viewmodel/LxMusicViewModel.kt`
8. `presentation/screens/CloudMusicSettingsScreen.kt`
9. `presentation/navigation/Screen.kt`
10. `presentation/navigation/AppNavigation.kt`
11. `presentation/navigation/TabContentHost.kt`
12. `MainActivity.kt`

**删除**：
13. `app/src/main/assets/lx_user_js/` 目录

## 验证方式

- 编译：`.\gradlew.bat compileDebugKotlin`（在 e:\PixelPlayer-master 下）
- 旧安装升级清理：确认 filesDir/lx_user_js 下 3 个旧文件 + flag 被清掉，用户手动导入的 js 不受影响
- 新装：files/lx_user_js 为空，设置页显示"尚未导入任何 JS"
- 市场：设置页 → 像素音源市场 → release 列表（含 V260917）→ 点开 → 下载进度 → 列出 js（中文不乱码）→ 勾选安装 → "已安装"徽标 → 返回设置页 scriptInfos 出现新脚本
- 边界：断网错误态可重试；zip 含子目录/非 js/超大文件安全跳过；重复安装同名生成 `_1.js` 变体且引擎正常加载

## 风险

- GitHub API 未认证限额 60 次/小时（可复用 BuildConfig.GITHUB_TOKEN）
- zip 内条目编码：GitHub 打包为 UTF-8，如遇 GBK 乱码在 inspectZip 加回退
- 镜像列表是 ApkDownloadInstaller 私有常量，在 Repository 复制一份（5 行），不改动既有类
