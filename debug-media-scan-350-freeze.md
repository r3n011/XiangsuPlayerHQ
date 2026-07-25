# Debug Session: media-scan-350-freeze

## Status
[OPEN]

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
- TBD

## Notes
- 不修改业务逻辑代码，直到收集到运行时证据。
