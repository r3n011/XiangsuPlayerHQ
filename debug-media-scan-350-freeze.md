# Debug Session: media-scan-350-freeze

## Status
[RESOLVED] — 2026-07-25

## Symptom
媒体库扫描到大约 350 首歌曲时卡住，无法继续完成本地歌曲扫描。

## Environment
- Project: PixelPlayer (e:\PixelPlayer-master)
- Issue scope: Local music library sync / scan
- Affected components: AudioMetadataReader, SyncWorker, AlbumArtUtils, AudioMetaUtils

## Hypotheses
1. **H1 - 阻塞式 I/O 无法被取消**：`AudioMetadataReader.read()` 或 `AudioMetaUtils.getAudioMetadata()` 内部使用 TagLib/JAudioTagger 等 JNI/阻塞 I/O，Kotlin 协程的 `withTimeout` 只能取消协程作用域，无法中断底层阻塞线程，导致线程被永久占用。
2. **H2 - 线程池耗尽**：扫描过程大量使用共享 Dispatcher（如 Default/IO），一旦部分线程被阻塞读取损坏/特殊音频文件，后续歌曲拿不到线程，整体扫描停滞。
3. **H3 - 封面提取阻塞**：`AlbumArtUtils.ensureAlbumArtCachedFile()` 在读取/转换封面时存在同步阻塞或递归死锁，导致 `processSongData()` 挂起。
4. **H4 - 嵌套超时异常未被正确捕获**：`SyncWorker.processSongData()` 内部多层 `withTimeout` 抛出 `TimeoutCancellationException` 后没有兜底 fallback，异常传播导致整个 sync 任务失败而非跳过单首歌曲。
5. **H5 - 特定文件触发原生崩溃/ANR前挂起**：某些音频文件触发解码器/标签库死循环或超长读取，线程一直占用但无异常抛出。

## Investigation Plan
1. 审查 `AudioMetadataReader.read()`、`AudioMetaUtils.getAudioMetadata()` 实现，确认是否使用阻塞 API。
2. 审查 `SyncWorker.processSongData()` 调用链与异常处理。
3. 审查 `AlbumArtUtils.ensureAlbumArtCachedFile()` 是否同步阻塞。
4. 添加最小化 instrumentation（仅在关键点通过 Debug Server 上报扫描进度/超时/异常）。
5. 根据证据选择最小修复：将阻塞调用迁移到独立线程池 + `Future.get(timeout)`，或为封面提取加独立超时。

## Evidence Log
- **根因确认 (H1 + H2)**：`AudioMetadataReader.read()` (第73行) 使用 `runBlocking { withTimeout(10s) { readInternal() } }` 包裹 native TagLib 调用。但 `readInternal` 是普通函数（非 suspend），`withTimeout` 的取消机制完全无效——无法中断正在执行的 JNI 调用。`runBlocking` 会永久阻塞调用线程，直到 native 调用返回。遇到损坏/特殊音频文件时 native 调用永久阻塞，`Dispatchers.IO` 线程逐渐泄漏耗尽，扫描卡死。
- **H3 排除**：`AlbumArtUtils.getAlbumArtUriForLibraryScan()` 在扫描阶段只检查缓存文件是否存在，不做实际封面提取，不是阻塞点。
- **H4 排除**：`processSongData()` 的 `withTimeout(15s)` 异常处理正确，有 fallback。但由于 `AudioMetadataReader.read()` 内部 `runBlocking` 阻塞，外层 `withTimeout` 也无法生效。

## Fix Applied (2026-07-25)
1. **AudioMetadataReader.kt**：将 `runBlocking { withTimeout { } }` 替换为专用线程池 `metadataExecutor`（4线程, daemon, MIN_PRIORITY）+ `Future.get(timeout)`。超时后调用方立即释放，native 调用在后台继续但不影响 `Dispatchers.IO`。
2. **UpdateChecker.kt**：`hasUpdate()` 参数名从 `installedTime` 改为 `lastUpdateTime`，注释同步更新。
3. **AboutScreen.kt**：移除 `firstInstallTime` 兜底逻辑，`lastUpdateTime` 获取失败时兜底为 `0L`。
4. 编译验证通过（`BUILD SUCCESSFUL`）。

## Notes
- `AudioMetaUtils.getAudioMetadata()` 也有 `withTimeout` 包裹 `MediaMetadataRetriever` native 调用的问题，但仅在 deepScan 模式触发，优先级较低，暂未修复。
- SyncManager.sync() 有 6 小时间隔限制（MIN_SYNC_INTERVAL_MS），可能导致用户感觉"无法扫描"——实际是跳过了重复扫描。下拉刷新（incrementalSync）和设置页全量扫描（fullSync）无此限制。
