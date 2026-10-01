# 网易云私信（聊天）+ 用户搜索关注 + 一起听邀请发送 — 实现设计方案

> 交付目标：主页左上角新增消息入口 → 会话列表/聊天页（收发私信）→ 用户搜索与关注 → 一起听房间邀请一键发给网易云好友。
> 本文档自包含：所有 API 已核验（MeloX 生产实现 + 社区 NeteaseCloudMusicApi 双来源交叉验证），SDK 层的参数错误已修正，可直接照此实现。
> 本文只描述"做什么/怎么调"，不含已完成代码的改动说明之外的历史。

---

## 0. 前置事实（必读）

1. 所有网易云请求走 vendored SDK：`net.moriafly.ncm`（weapi 加密 + cookie 自动注入，App 启动时 `NcmApi.install()` 已在 `PixelPlayApplication` 调用）。
2. 门面调用方式：`NcmApi.FullAccess.xxx(...)`，返回 `Result<Map<String, Any?>>`；用 `JSONObject(map)` 转成 JSON 解析。`FullAccess` 位于 `NcmApi.kt:275-659`。
3. 登录态判定：`net.moriafly.ncm.NcmApi.isLogin`（SDK 侧）或 `NeteaseRepository.isLoggedInFlow`（UI 侧，二者由 `NeteaseApiService.syncNcmSessionCookies()` 同步）。**私信类接口必须登录**，否则返回需登录错误。
4. 自己的 uid：`neteaseRepository.getNeteaseUserId()`（已有，本地缓存 + 在线兜底）。
5. 风控注意：网易云对高频调用返回 405。**禁止后台高频轮询**；搜索输入防抖 ≥300ms；会话刷新用下拉/进页触发。

---

## 1. API 清单（全部已存在于 `NcmModulesFull.kt` 并在 `NcmApi.FullAccess` 暴露）

| # | 功能 | 门面函数 | 端点 | 请求参数（weapi） | 响应关键字段 |
|---|------|----------|------|--------------------|--------------|
| 1 | 会话列表 | `NcmApi.FullAccess.msgPrivate(limit=30, offset=0)` | `/api/msg/private/users` | `{limit, total:true, offset}` | `msgs[]`: `fromUser{userId,nickname,avatarUrl}`、`toUser{...}`、`lastMsg`(JSON字符串)、`lastMsgTime`(缺失时回退`time`)、`newMsgCount`(回退`unreadCount`→`newCount`) |
| 2 | 聊天记录 | `msgPrivateHistory(uid, beforeTime=0, limit=30, total=true)` | `/api/msg/private/history` | `{userId: uid, limit, time: beforeTime>0?beforeTime:"-1", total:"true"}` | `msgs[]`: `id`、`fromUser{userId}`、`toUser{userId}`、`time`、`msg`(JSON字符串，见 §3) |
| 3 | 发文本私信 | `sendText(userIds="[$uid]", msg, type="text")` | `/api/msg/private/send` | `{userIds:"[123]", type:"text", msg:"..."}` | 顶层 `code==200` |
| 4 | 发歌曲私信 | `sendSong(userId, songId, id, msg="")` | `/api/msg/private/send` | `{userIds:"[id]", type:"song", id, msg, songId}` | 同上（`id` 与 `songId` 都传歌曲ID） |
| 5 | 用户搜索 | `NcmApi.search(keyword, type=1002, limit, offset)` | `/api/search/get`（顶层门面） | `{s: keyword, type:1002, limit, offset}` | `result.userprofiles[]`: `userId, nickname, avatarUrl, signature`（注意键名是 **userprofiles**） |
| 6 | 用户主页 | `userDetail(uid)` | `/api/v1/user/detail/{uid}` | 空 body | `profile{nickname, avatarUrl, signature, follows, followeds, playlistCount}` + 顶层 `level, listenSongs` |
| 7 | 关注/取关 | `follow(uid, t=1)` | `/api/follow/{uid}` | `{t: 1关注 / 0取关}` | 顶层 `code==200` |
| 8 | 关注列表 | `userFollows(uid, limit, offset, order=true)` | `/api/user/getfollows/{uid}` | `{limit, offset, order}` | `follow[]`（结构同 userDetail.profile 摘要） |
| 9 | 粉丝列表 | `userFolloweds(uid, limit, offset)` | `/api/user/getfolloweds` | `{userId, limit, offset}` | `followeds[]` |
| 10 | 一起听加入（已有） | `ListenTogetherCoordinator.joinRoom(roomId, inviterId)` | `room/check` + `play/invitation/accept` | — | 已实现，勿重复造 |

**已修正的 SDK 错误（本轮完成，勿回退）**：
- `msgPrivateHistory`：参数从错误的 `{userIds:"[uid]", before}` 改为 `{userId, limit, time, total:"true"}`；
- `sendText`：端点从错误的 `/api/msg/private/send/text` 改为 `/api/msg/private/send`（type=text）。

**消息内容解析（`msg` 字段）**：`msg` 是 JSON **字符串**，可能为：
- 纯文本（非 JSON）→ 当作文本气泡；
- JSON `{"msg": "说明文字", "song": {...}}` / `"playlist"` / `"album"` → 资源卡片。卡片字段：song→`id,name,al{picUrl},ar[]{name}`；playlist→`id,name,coverImgUrl,creator{nickname}`；album→`id,name,picUrl`。参考实现：MeloX `NeteaseMusicOperationsClient.parsePrivateMessagePayload`（`E:\MeloX-Android-main\android\app\src\main\kotlin\com\lladlam\melox\core\network\NeteaseMusicOperationsClient.kt:492-529`）。
- **一起听邀请**：文本里含 `st.music.163.com/listen-together/share/?...&roomId=..&inviterId=..` → 用现成的 `parseListenTogetherInvitation(text)`（`data/listentogether/ListenTogetherModels.kt`）解析出 roomId/inviterId，渲染"加入一起听"按钮 → `ListenTogetherCoordinator.joinRoom(roomId, inviterId)`（已在房间时 coordinator 会报"已在一间一起听房间中"）。

---

## 2. 架构与文件规划

```
data/netease/chat/
  NeteaseChatModels.kt        // ChatContact / ChatMessage / ChatResource / ChatUserSummary
  NeteaseChatRepository.kt    // @Singleton @Inject，包装门面调用 + DTO 解析

presentation/netease/chat/
  MessagesViewModel.kt        // 会话列表 + 用户搜索 + 关注（一个 VM 承载三个 tab 的数据）
  MessagesScreen.kt           // 会话列表 / 搜索用户 两个 tab（或分区）
  ChatViewModel.kt            // 单会话：分页消息 + 发送 + 对方资料
  ChatScreen.kt               // 聊天页（气泡 + 资源卡片 + 邀请按钮）
  FriendPickerSheet.kt        // 一起听"邀请好友"选人面板（关注列表 + 最近会话）
```

**修改的既有文件**：
- `presentation/components/GradientTopBar.kt`：`navigationIcon` 目前为空（约 161 行），加消息 IconButton（`Icons.AutoMirrored.Rounded.Chat`）+ 未读红点角标；新增参数 `onMessagesClick: (() -> Unit)? = null` 与 `unreadCount: Int = 0`。注意横屏分支（同文件 ~982 行）在右上角浮动 Row，按需对齐（可只在竖屏加）。
- `presentation/screens/HomeScreen.kt`：`HomeGradientTopBar(...)` 调用处（~967 行）接线；复用既有"需要登录网易云"AlertDialog 模式（~1085 行）。
- `presentation/navigation/Screen.kt` + `AppNavigation.kt`：新增 `Screen.Messages`（会话列表）与 `Screen.Chat/{uid}`（聊天页，参数：对方 uid + 可选预填昵称头像）。
- `presentation/netease/dashboard/ListenTogetherSheet.kt`：房内卡片加"邀请网易云好友"按钮 → 打开 FriendPickerSheet。
- 字符串：`values/strings_screens.xml` + `values-zh-rCN/strings_screens.xml`（命名 `chat_*`）。

**Repository 设计**（建议签名）：
```kotlin
@Singleton
class NeteaseChatRepository @Inject constructor(
    private val neteaseRepository: NeteaseRepository
) {
    /** 对方=fromUser/toUser 中不等于自己的一方；lastMsg 解析为预览文案（资源卡片→"[歌曲] 标题"） */
    suspend fun getConversations(limit: Int = 50): Result<List<ChatContact>>
    /** beforeTime=0 取最新一页；翻页传上一页最早一条的 time */
    suspend fun getHistory(userId: Long, beforeTime: Long = 0L, limit: Int = 30): Result<List<ChatMessage>>
    suspend fun sendText(userId: Long, text: String): Result<Unit>
    suspend fun sendSong(userId: Long, songId: Long, message: String = ""): Result<Unit>
    suspend fun searchUsers(keyword: String, limit: Int = 20, offset: Int = 0): Result<List<ChatUserSummary>>
    suspend fun getUserDetail(userId: Long): Result<ChatUserDetail>
    suspend fun setFollowed(userId: Long, followed: Boolean): Result<Unit>
    /** 房主快速邀请：关注列表 + 最近会话去重后的候选 */
    suspend fun getInviteCandidates(): Result<List<ChatContact>>
}
```
- `sendText` 成功后**本地乐观插入**一条消息（`fromUserId = 自己`），不要等拉历史。
- 解析一律防御式（`optString/optLong` + 回退键），错误时 `Result.failure(IOException(服务端message))`。

---

## 3. UI 设计

### 3.1 入口（主页左上角）
- `HomeGradientTopBar` 左上角放消息图标 + 未读红点（未读数 = `getConversations()` 各项 `unreadCount` 之和）。
- 未登录点击 → 弹既有样式的"需要登录网易云"对话框 → 跳 `Screen.NeteaseDashboard`。
- 已登录 → `navController.navigateSafely(Screen.Messages.route)`。
- 未读数刷新时机：进入主页时 + 从聊天页返回时；**不要**常驻轮询。

### 3.2 会话列表（MessagesScreen，路由 `/messages`）
- TopAppBar：标题"消息"，右上角"添加好友"图标（切换到搜索 tab）。
- 会话行：头像 + 昵称 + 最后一条预览 + 相对时间 + 未读红点；点击 → `ChatScreen(userId, name, avatar)`。
- 下拉刷新触发 `getConversations()`。
- 第二分区"搜索用户"：搜索框（防抖）→ 用户卡片（头像/昵称/签名）→ 两个按钮：「关注」（乐观切换 `follow(t)`，失败回滚 + Toast）、「发私信」（进聊天页）。
- 点击用户卡片也可展开 `userDetail` 摘要（等级/听歌数/粉丝数）——可选增强。

### 3.3 聊天页（ChatScreen，路由 `/chat/{userId}?name=&avatar=`）
- 垂直 LazyColumn（**反转布局** `reverseLayout=true`，新消息在底部）；气泡按 `fromUserId == 自己uid` 分左右。
- 首屏 `getHistory(uid)`；滚动到顶部时以最早消息 `time` 为 `beforeTime` 加载更早（加载中显示顶部 spinner）。
- 底部输入框 + 发送按钮：`sendText`；发送中禁用；失败保留草稿并 Toast 服务端 message。
- 资源卡片消息：歌曲卡片显示封面/歌名/歌手，点击可走现有播放链路（`showAndPlaySong` 或保存后播放，实现者可从简：仅展示 + 若一起听激活则按门禁拦截）。
- 邀请链接消息：渲染"加入一起听"按钮（解析成功时），点击调 `joinRoom`；自己发的邀请链接同样渲染（方便复制/重发）。
- 标题栏：对方昵称 + 「关注/已关注」快捷按钮（`userDetail.profile.followed` 字段可判关注态，缺失时乐观显示）。

### 3.4 一起听联动（FriendPickerSheet）
- `ListenTogetherSheet` 房内卡片新增「邀请网易云好友」→ BottomSheet：
  - 数据 = `getInviteCandidates()`（关注列表为主，最近会话去重合并）；
  - 每行"发送邀请"→ `sendText(friendUid, inviteText)`，inviteText 用现成 `buildListenTogetherInviteText(songId, room)`（`ListenTogetherCoordinator.inviteText()` 已暴露）；
  - 发送成功 Toast「已发送邀请」，失败显示服务端 message。
- 聊天页收到邀请消息 → 「加入一起听」→ `joinRoom`（复用现成状态流，无需新增同步逻辑）。

---

## 4. 关键陷阱清单（实现者必读）

1. **`total` 参数类型**：`msgPrivate` 传布尔 `true`（现状可用），`msgPrivateHistory` 传字符串 `"true"`（社区/MeloX 均如此）。勿统一改写。
2. **`msgPrivateHistory` 分页**：`time` 语义是"取此时间**之前**"，翻页传上一页最早一条的 `time`；首次请求传 `-1`。
3. **`userIds` 格式**：发私信必须是 JSON 数组字符串 `"[$uid]"`，不是裸数字。
4. **搜索结果键名**：type=1002 时结果在 `result.userprofiles`，不是 `result.songs/users`。
5. **会话参与方判定**：`fromUser.userId == 自己uid` 时对方是 `toUser`，反之亦然；自己 uid 用 `neteaseRepository.getNeteaseUserId()`（勿用 `NcmApi.userId`，那个解析 MUSIC_U cookie 是错的）。
6. **未读数回退链**：`newMsgCount → unreadCount → newCount`；`lastMsgTime → time`。
7. **msg 字段是 JSON 字符串**：先 `JSONObject(msg)` 尝试，失败当纯文本；解析出的 song 对象字段与官方 track 结构一致（`al/ar`）。
8. **风控**：所有请求串行化不必要；搜索防抖；无后台轮询；连续失败提示"网易云接口繁忙"而非崩溃。
9. **登录门禁**：所有入口先查 `isLoggedInFlow`；未登录统一走"去登录"对话框模式。
10. **一起听门禁兼容**：聊天内点歌曲卡片播放要走 `showAndPlaySong`（一起听激活时 PlayerViewModel 门禁会自动拦截非网易云歌曲，无需在聊天层重复实现）。

## 5. 验收清单（真机，两个网易云测试账号）

1. A 登录 → 消息入口可见；未登录账号 B → 点击弹登录引导。
2. A 搜索 B（type=1002）→ 出结果 → 关注/取关往返成功（userDetail.followed 或 followers 数变化）。
3. A 给 B 发文本 → B 会话列表未读 +1、预览正确；B 回复 → A 聊天页下拉/进页可见。
4. 历史翻页：连续上滑能加载更早消息且无重复。
5. A 开一起听房间 → 面板"邀请网易云好友" → 选 B 发送 → B 的会话出现邀请链接消息 → 点击"加入一起听" → B 成功入房并与 A 同步播放（复用现有一起听同步）。
6. 发歌曲卡片消息 → 对方气泡渲染资源卡（可选）。
7. 断网发送 → 显示错误且草稿保留。
