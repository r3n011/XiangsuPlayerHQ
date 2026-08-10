# 更新日志 / Changelog

## v1.4.6 (Build 43)

### 重大修复

#### 移除 AAudio 自定义输出，恢复原版 AudioTrack 播放链路
- AAudio 流模型存在 EOS（End-of-Stream）判定缺陷：`nativeStop` 重置 `flush_base_frames` 基准后 `hasPendingData()` 恒为 true，导致 Media3 `DefaultAudioSink` 的 EOS 永不送达，表现为歌曲播放到末尾后秒数回跳 3 秒反复、声音断断续续、无法自动切歌、播放器卡死（`StuckPlayerException`）。
- 彻底移除 AAudio 输出层，恢复原版 `DefaultAudioSink`（AudioTrack），仅保留 USB 独占模式和漫游显示功能。
- 涉及文件：`DualPlayerEngine.kt`、`DeckController.kt`、`SettingsCategoryScreen.kt`（移除 AAudio 开关）。

#### 列表循环模式修复
- 去除 AAudio 后，列表循环（repeatMode）恢复正常：歌曲播放完毕后自动播放下一首，不再暂停。
- `TransitionController` 中的 `simulateNaturalTrackEnd()` 与 `tryRecoverFromError()` 按 repeatMode 正确处理切歌。

---

### 蓝牙歌词广播全面修复

#### 蓝牙歌词"歌名 ↔ 歌词"闪烁彻底消除
- **根因**：此前用 `MediaSession.setMediaMetadata()` 推送歌词会触发 `onMediaItemTransition` 回环，导致标题在"真实歌名"和"歌词"之间来回切换。
- **修复方案**（基于 Media3 1.10.1 源码验证）：
  - 确认 `Player` / `MediaSession` 均无 `setMediaMetadata` API；`setPlaylistMetadata` 仅更新队列标题，无法广播歌词。
  - 改用 `player.replaceMediaItem(index, updatedItem)` 推送歌词。ExoPlayer 在 URI 不变（仅改 metadata）时走 `canUpdateMediaItem` 就地更新路径（`TimelineWithUpdatedMediaItem`），**不重建 MediaSource、不触发 `onMediaItemTransition`、不打断/重缓冲音频**。
  - 新增 `isSelfLyricsTransition` 防回环守卫：兜底 URI 变化等无法就地更新的数据源，避免 `push → transition → setLyrics → push` 无限循环。
  - 空行不推送（避免在"真实歌名 ↔ 无歌词"之间闪烁）。
  - 歌词只刷新到歌名位置，艺术家保持不变。

#### 所有播放入口均可广播歌词
- **问题**：搜索页（QQ 音乐 `qq_xxx`、酷我 `kw_xxx`、B 站 `bili_xxx`）直接播放时 `getSong(songId)` 查不到歌（非 Long id），导致歌词永不加载。
- **修复**：
  - 移除 `MusicService.onMediaItemTransition` 中对 `PLAYLIST_CHANGED` reason 的歌词加载跳过逻辑，确保所有切歌路径都能加载歌词。
  - 新增 `toLyricsFallbackSong()` 兜底：当数据库查不到歌曲时，用 MediaItem 自带元数据（歌名/歌手/封面/时长）临时构造 `Song`，让 `getLyrics` 按歌名+歌手走远程歌词搜索。
  - 涉及文件：`BluetoothLyricsManager.kt`、`MusicService.kt`。

---

### UI 优化

#### 歌词界面挖孔避让
- 歌词界面上方的歌曲信息栏（`LyricsTrackInfo`）增加 `WindowInsets.safeDrawing` 避让，在有挖孔/刘海的手机上自动下移，不再被遮挡。
- 涉及文件：`LyricsSheet.kt`。

#### 播放器界面简化
- 删除歌名右侧的蓝牙设备状态按钮（连接蓝牙设备后出现的那个按钮），仅保留队列按钮。
- 涉及文件：`FullPlayerContent.kt`。

#### 评论图标更换
- 播放器界面的歌曲评论图标更换为 Material `rounded_mode_comment_24`（圆形对话气泡样式）。
- 涉及文件：`FullPlayerContent.kt`、新增 `rounded_mode_comment_24.xml`。

---

### 下载与播放修复

#### 下载歌曲后无法播放
- **问题**：歌曲下载完成后数据库中的 `path` 字段不会自动更新为本地文件路径，播放时仍走网络解析导致失败。
- **修复**：播放时优先查询 `MusicDownloadService` 的内存下载记录，命中本地文件时直接使用本地 mp3 文件 URI 构建 `MediaItem` 播放，绕过网络解析。
- 涉及文件：`PlayerViewModel.kt`（`buildResolvedPlaybackMediaItem`）。

---

### 涉及修改的文件清单

| 文件 | 修改类型 |
|------|----------|
| `DualPlayerEngine.kt` | 移除 AAudio 注入，恢复原版 DefaultAudioSink |
| `DeckController.kt` | 移除 AAudio 注入，清理相关 imports |
| `SettingsCategoryScreen.kt` | 移除 AAudio 开关 |
| `BluetoothLyricsManager.kt` | 歌词推送改用 replaceMediaItem 就地更新，新增防回环守卫 |
| `MusicService.kt` | 蓝牙歌词加载逻辑重构，新增 toLyricsFallbackSong 兜底 |
| `TransitionController.kt` | 切歌逻辑按 repeatMode 正确处理 |
| `LyricsSheet.kt` | 挖孔避让 |
| `FullPlayerContent.kt` | 删除蓝牙按钮，更换评论图标 |
| `PlayerViewModel.kt` | 下载歌曲本地优先播放 |
| `rounded_mode_comment_24.xml` | 新增 Material 评论图标资源 |
| `aaudio_output.c` | 保留但不再被播放链路引用 |
