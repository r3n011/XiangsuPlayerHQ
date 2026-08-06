# 网易云音乐内置 SDK（NCM）使用指南

内置在 App 里的网易云音乐本地 SDK（包 `net.moriafly.ncm`），本质是 **NeteaseCloudMusicApi（NodeJS 原版）的 Kotlin 移植版**——不需要部署任何服务器、不依赖外部代理，直接加密请求网易云官方接口。

## 1. 特性

- **零外部依赖**：只用 JDK 自带 `javax.crypto`（AES/RSA）和 `java.net.HttpURLConnection`，加 `timber` 打日志。
- **三种加密路由**：`weapi` / `eapi` / `linuxapi`，与原版 `util/crypto.js` 一致。
- **300+ 接口**：搜索、播放 URL、歌词、私人FM、评论、红心、歌单、用户、榜单、MV、云盘等全部覆盖。
- **自动处理**：cookie 会话、gzip/deflate 解压、301/302 跟随、Set-Cookie 合并、登录态同步。
- **所有方法返回 `Result<Map<String, Any?>>`**，异常统一包装，不会崩溃。

## 2. 快速接入（3 步）

### 2.1 Application 初始化

```kotlin
class PixelPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NcmApi.install(this)
        // 可选参数：
        // NcmApi.install(this, os = "pc", realIp = null, proxy = null)
    }
}
```

| 参数 | 说明 |
|---|---|
| `os` | 加密平台：`pc`（默认）/ `android` / `ios`，决定 UA 与 appver |
| `realIp` | 注入 `X-Real-IP`（海外 IP 被限制时填一个国内 IP） |
| `proxy` | HTTP 代理，如 `http://127.0.0.1:7890` |

### 2.2 调用一个接口

```kotlin
lifecycleScope.launch {
    NcmApi.search("周杰伦", limit = 10)
        .onSuccess { body ->
            val result = body["result"] as? Map<*, *> ?: return@onSuccess
            val songs = result["songs"] as? List<*> ?: return@onSuccess
            songs.forEach { song ->
                val m = song as Map<*, *>
                Timber.d("歌曲: ${m["name"]} id=${m["id"]}")
            }
        }
        .onFailure { Timber.e(it, "搜索失败") }
}
```

> 所有方法都在内部切到 `Dispatchers.IO`，可以放心在协程里直接调。

### 2.3 登录（私人FM / 红心 / 评论 需要登录）

```kotlin
// 方式一：手机号 + 验证码
NcmApi.loginCellphone(phone = "138xxxx", captcha = "1234")

// 方式二：手机号 + 密码
NcmApi.loginCellphone(phone = "138xxxx", password = md5(password))

// 方式三（推荐）：二维码登录
val (key, pngBase64) = NcmApi.qrLoginPrepare().getOrThrow()
// 把 pngBase64 转 Bitmap 显示给用户
NcmApi.qrLoginAwait(key) { code, _ -> /* 801等待 802已扫 803成功 800过期 */ }
```

登录态由 `NcmSession` 自动持久化（SharedPreferences），重启 App 无需重新登录。判断登录态：

```kotlin
NcmApi.isLogin
NcmApi.userId
```

## 3. 常用接口速查

### 3.1 搜索

```kotlin
NcmApi.search(keyword, type, limit, offset)      // /api/search/get
NcmApi.full.cloudSearch(keyword, type, limit, offset) // /api/cloudsearch/get/web（更全）
NcmApi.full.searchHot()                          // 热搜
NcmApi.full.searchSuggest(keyword)               // 搜索建议
NcmApi.full.searchMultimatch(keyword)            // 多重匹配
```

`type`：1=单曲、10=专辑、100=歌手、1000=歌单、1004=MV、1009=电台、1014=视频、1018=综合。

### 3.2 播放

```kotlin
// 新版音质接口（推荐）
NcmApi.songUrlV1(ids = listOf("4876940"), level = "exhigh", encodeType = "flac")

// level 取值：standard / higher / exhigh / lossless / hires / jyeffect / jymaster / sky
// encodeType：flac / mp3 / aac

// 旧版码率接口
NcmApi.songUrl(id = "4876940", br = 320_000)

// 歌曲详情 / 是否可播
NcmApi.songDetail(listOf("4876940"))
NcmApi.checkMusicPlayable(id = "4876940", br = 320_000)
```

返回的 `data[].url` 才是真正的音频直链。

### 3.3 歌词

```kotlin
val body = NcmApi.full.songLyric(id = "4876940").getOrThrow()
val lrc = body["lrc"] as? Map<*, *>          // 原文歌词
val lyricText = lrc?.get("lyric") as? String
// 可选：nolyric / tlyric(翻译) / romalrc(罗马音)
```

### 3.4 私人 FM

```kotlin
val body = NcmApi.full.personalFm().getOrThrow()  // 需要登录
val songs = body["data"] as? List<*>               // 歌曲数组
```

### 3.5 红心 / 喜欢

```kotlin
NcmApi.full.like(id = "4876940", like = true)      // 红心 / 取消
val body = NcmApi.full.likelist(uid = "123").getOrThrow()
val ids = body["ids"] as? List<*>                  // 已红心的歌曲 id 列表
```

### 3.6 评论

```kotlin
val body = NcmApi.full.commentMusic(id = "4876940", limit = 20).getOrThrow()
val hot = body["hotComments"] as? List<*>           // 热门评论
val normal = body["comments"] as? List<*>           // 最新评论
val total = body["total"]

// 发评论 / 点赞评论
NcmApi.full.commentSend(NcmModulesFull.CmtType.SONG, "4876940", "好听")
NcmApi.full.commentLike("4876940", "评论id", NcmModulesFull.CmtType.SONG)
```

### 3.7 歌单 / 榜单

```kotlin
NcmApi.full.playlistDetail(id = "歌单id")
NcmApi.full.playlistTrackAll(id = "歌单id", limit = 1000)  // 歌单所有歌曲
NcmApi.full.toplist()                                      // 所有榜单
NcmApi.full.toplistDetail(id = "榜单id")                   // 榜单详情
NcmApi.full.playlistTrackAdd(pid, ids)                     // 歌单加歌
```

### 3.8 用户 / 歌手

```kotlin
NcmApi.full.userDetail(uid)
NcmApi.full.userPlaylist(uid)          // 用户的歌单
NcmApi.full.userRecord(uid, type = 1)  // 听歌排行
NcmApi.full.artistSongs(id)            // 歌手热门50首
NcmApi.full.artistAlbum(id)
```

## 4. 全量模块 `NcmApi.full`

`NcmApi.full` 收录了原版 `/module/*.js` 的 300+ 个接口，按类别分组：

- **搜索扩展**：`searchHot` / `searchHotDetail` / `searchSuggest` / `cloudSearch`
- **歌词下载**：`songLyric` / `lyricNew` / `songDownloadUrlV1`
- **相似推荐**：`simiSong` / `simiPlaylist` / `simiArtist` / `simiUser`
- **榜单**：`toplist` / `toplistDetail` / `topSong` / `topPlaylist`
- **歌手**：`artistSongs` / `artistAlbum` / `artistDetail` / `artistTopSong`
- **专辑**：`album` / `albumDetail` / `albumSub` / `albumNew`
- **歌单**：`playlistCreate` / `playlistTrackAdd` / `playlistSubscribe` ...
- **喜欢/评论**：`like` / `likelist` / `commentMusic` / `commentSend` / `commentLike`
- **用户**：`userDetail` / `userPlaylist` / `userFollows` / `userEvent`
- **DJ电台**：`djDetail` / `djProgram` / `djRecommend` ...
- **MV/视频**：`mvUrl` / `mvDetail` / `videoUrl` / `videoDetail`
- **私人FM/日推**：`personalFm` / `fmTrash` / `recommendSongs` / `historyRecommendSongs`
- **签到/VIP/云贝**：`dailySignin` / `vipInfo` / `yunbeiSign` ...
- **云盘**：`userCloud` / `cloudMatch` / `cloudUploadToken`
- **消息/私信**：`msgPrivate` / `sendText` / `msgNotices`

> IDE 输入 `NcmApi.full.` 会自动补全所有方法签名。

### 兜底入口（新增接口不用写代码）

如果某个接口没在 `FullAccess` 里暴露，直接用 raw 方法：

```kotlin
NcmApi.full.rawWeapi("/api/xxx", mapOf("key" to value))
NcmApi.full.rawEapi("/api/xxx", mapOf("key" to value))
NcmApi.full.rawLinuxapi("/api/xxx", mapOf("key" to value))
```

## 5. 返回结构解析

所有方法返回 `Result<Map<String, Any?>>`（对应原版响应的 JSON 对象，`code` 是数字）。

### 5.1 手写解析（Map 版）

```kotlin
val body = NcmApi.songUrlV1(listOf("4876940")).getOrThrow()
val code = body["code"] as? Int ?: -1
val data = body["data"] as? List<*>
```

### 5.2 便捷扩展（推荐）

`NcmJson.kt` 自带扩展函数，自动处理 null / 类型转换：

```kotlin
val map: Map<String, Any?> = ...
val name = map.ncmString("name")          // 无则 ""
val count = map.ncmInt("count")           // 数字/字符串都能转
val total = map.ncmLong("total")
val ok = map.ncmBool("success")
val user = map.ncmObj("user")             // 嵌套 Map
val songs = map.ncmList("songs")          // 嵌套 List
```

## 6. 会话管理

```kotlin
// 运行期切换（不同接口对 pc 更友好）
NcmApi.setOs("pc")
NcmApi.setRealIp("116.25.146.177")
NcmApi.setProxy("http://127.0.0.1:7890")

// 退出登录
NcmApi.logout()

// 调试：打印当前会话
NcmApi.dumpSession()
```

## 7. 常见问题 / 坑

### 7.1 响应是乱码密文？

**根因**：请求头带了 `Accept-Encoding: gzip, deflate, br`，而 Java `HttpURLConnection` 不会解 br。
**已修复**：内置 SDK 只请求 `gzip`，并支持 gzip + deflate 解压。不要再把 `br` 加进 Accept-Encoding。

### 7.2 需要登录的接口返回 301？

私人FM、红心、评论等**必须登录**。先确认 `NcmApi.isLogin == true`；未登录时先走 `loginCellphone` / `qrLoginAwait`。

### 7.3 怎么查看请求详情？

logcat 过滤 `NcmRequest`：

```
NCM /api/search/get -> code=200 bodyLen=2276
NCM /api/search/get -> body={"result":...}
```

第一行看状态码，第二行看实际返回内容，排查接口对没对。

### 7.4 歌曲 ID 要用字符串

搜索/详情接口的 id 字段 JSON 里是数字，解析时统一用 `ncmString` / `toString()` 转字符串，避免类型不匹配。

### 7.5 海外 IP 被风控

`NcmApi.install(this, realIp = "116.25.146.177")` 注入国内 IP，或运行时 `setRealIp`。

## 8. 如何对照原版新增接口

原版 `E:\NeteaseCloudMusicApi-main\module\xxx.js` 每个文件对应一个接口，移植三步：

1. 打开 `NcmModulesFull.kt`，照抄原版路径与参数：

```js
// 原版 xxx.js
const data = { id: query.id, tv: -1 }
return request(`/api/song/xxx`, data, createOption(query))
```

```kotlin
// NcmModulesFull.kt
suspend fun xxx(id: String) = rawWeapi("/api/song/xxx", mapOf("id" to id, "tv" to -1))
```

2. 在 `NcmApi.FullAccess` 里加一行委托：

```kotlin
suspend fun xxx(id: String) = NcmApi.runSafely("xxx") { NcmModulesFull.xxx(id) }
```

3. 编译即可用（返回统一 `Result<Map<String, Any?>>`）。

加密路由对照：`crypto: 'weapi'` → `rawWeapi`；`'eapi'` → `rawEapi`；`'linuxapi'` → `rawLinuxapi`。

## 9. 架构总览

```
调用方 ──► NcmApi / NcmApi.full        （门面：参数 Kotlin 化 + Result 包装）
              │
              ▼
       NcmModules / NcmModulesFull     （业务模块：路径 + 参数，对齐 module/*.js）
              │
              ▼
          NcmRequest                   （请求引擎：weapi/eapi/linuxapi 加密路由、cookie、gzip、3xx 跟随）
              │
              ▼
          NcmCrypto                    （AES-CBC / RSA / eapi 响应解密）
              │
              ▼
       music.163.com                  （官方接口）
```

- `NcmSession`：会话单例，持久化 cookie / os / realIp / proxy / 登录态
- `NcmJson`：零依赖 JSON 解析 + 便捷扩展
- `PixelPlayApplication`：启动时 `NcmApi.install(this)`；登录态由 `NeteaseApiService.setPersistedCookies` 同步进 `NcmSession`
