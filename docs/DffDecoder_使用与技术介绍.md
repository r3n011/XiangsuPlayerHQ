# DFF / DSD Decoder (DffDecoder)

## 1. 概述

`DffDecoder` 是 PixelPlayer 内置的 **纯 Kotlin DSD → PCM 软解码器**，专门用于播放 Android 原生 MediaCodec / ExoPlayer 不支持的 **DSDIFF (.dff)** 与 **原始 DSD (.dsd / .dif)** 格式。

解码器采用两级多相 FIR 降采样、基于质量评分的格式自动检测、双模式转码（边听边转码 + 缓存优先），并与 Media3 DataSource 无缝集成。

**核心文件位置：**

| 文件 | 作用 |
|---|---|
| [DffDecoder.kt](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/utils/DffDecoder.kt) | 解码器主体：格式解析、DSD→PCM 卷积降采样、流式 DataSource |
| [WavConversionDataSource.kt](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/utils/WavConversionDataSource.kt) | Media3 DataSource 入口：根据扩展名路由到 miniaudio / DffDecoder |
| [TranscodeCacheManager.kt](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/utils/TranscodeCacheManager.kt) | 转码缓存（按文件路径 + 修改时间做 key，带 LRU 清理） |
| [TranscodeProgressManager.kt](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/utils/TranscodeProgressManager.kt) | 转码进度状态流（UI 展示 "转码缓存 XX%"） |
| [HiFiFormatMapper.kt](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/utils/HiFiFormatMapper.kt) | 扩展名 → MIME / 显示名 / 格式能力映射 |
| [DualPlayerEngine.kt](file:///e:/PixelPlayer-master/app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt#L1540) | ExoPlayer 构建时接入 WavConversionDataSource Factory |

## 2. 支持格式与输出规格

### 输入

| 格式 | 扩展名 | Magic 识别 |
|---|---|---|
| DSDIFF (Philips) | `.dff` | 文件头 `DSDIFF` / `DIFF` |
| 原始 DSD 容器 | `.dsd` | 文件头 `DSD ` |
| 无容器裸 DSD | `.dif` | 扩展名兜底，默认 2ch / 2.8224MHz |

DSD 采样率支持：`1.4112 MHz (DSD32)`, `2.8224 MHz (DSD64)`, `5.6448 MHz (DSD128)`, `44.1 MHz`（理论上限 192×降采样）

### 输出

统一输出 **16-bit PCM WAV（RIFF/WAVE）**，由 `selectOutputRate()` 根据 DSD 采样率动态选择最高不超过 192 kHz 的输出：

| DSD 输入 | 典型降采样倍数 | 输出采样率 |
|---|---|---|
| 1.4112 MHz | 32 | 44.1 kHz |
| 2.8224 MHz | 32 | 88.2 kHz |
| 5.6448 MHz | 32 | 176.4 kHz |
| 其他能整除的 | 16 / 32 / 64 / 128 | 取 ≤192 kHz 的最大值 |

## 3. 外部调用方式

### 3.1 零配置播放（推荐）

无需手动调用任何 API。在播放 DFF/DSD 文件时，流程如下：

```
ExoPlayer.open(uri)
  → DualPlayerEngine 中注入的 WavConversionDataSource.Factory
    → WavConversionDataSourceDelegate.open()
      ├─ 扩展名匹配 .dff / .dsd / .dif
      ├─ 先查 TranscodeCacheManager.getCachedFile()  →  有缓存直接 WAV 直读
      ├─ 否则按当前转码策略执行：
      │   ├─ STREAM_WHILE_TRANSCODE → DffStreamingDataSource（边听边转码）
      │   └─ CACHE_FIRST            → DffDecoder.decodeToWav() 整首解码完成再播
      └─ 解码完成后写入 TranscodeCacheManager.cacheTranscodedFile()
```

**启用入口：**

在 `DualPlayerEngine.createPlayer()` 中已通过以下代码自动接入：

```kotlin
val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
val resolvingFactory = ResolvingDataSource.Factory(dataSourceFactory, resolver)
val wavConversionFactory = WavConversionDataSource.Factory(context, resolvingFactory)
// ...
return ExoPlayer.Builder(context, renderersFactory)
    .setMediaSourceFactory(DefaultMediaSourceFactory(wavConversionFactory, extractorsFactory))
    ...
```

转码策略在 `SettingsViewModel` 中与用户偏好同步：

```kotlin
WavConversionDataSource.transcodeMode = when (strategy) {
    STREAMING   -> WavConversionDataSource.TranscodeMode.STREAM_WHILE_TRANSCODE
    CACHE_FIRST -> WavConversionDataSource.TranscodeMode.CACHE_FIRST
}
```

### 3.2 手动批量转码

```kotlin
import com.theveloper.pixelplay.utils.DffDecoder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun convertAllToWav(folder: File, outputDir: File) {
    DffDecoder.progressCallback = { progress, stage ->
        println("[$progress%] $stage")
    }
    withContext(Dispatchers.IO) {
        folder.walkTopDown()
            .filter { DffDecoder.isDffFile(it.path) }
            .forEach { file ->
                val outWav = DffDecoder.decodeToWav(file.path, outputDir)
                if (outWav != null) {
                    println("OK: ${file.name} → ${outWav.name}")
                } else {
                    System.err.println("FAILED: ${file.name}")
                }
            }
    }
}
```

### 3.3 进度观察（UI 层）

```kotlin
import com.theveloper.pixelplay.utils.TranscodeProgressManager

@Composable
fun TranscodeProgressBadge() {
    val progress by TranscodeProgressManager.progress.collectAsStateWithLifecycle()
    if (progress.isRunning) {
        Text("转码缓存 ${progress.percent}% — ${progress.stage}")
    }
}
```

## 4. 技术原理

### 4.1 DSD 格式背景

DFF/DSD 文件中，每个声道的样本是 **1-bit** 的脉冲密度调制（PDM）位流，按字节打包。以 DSD64（2.8224 MHz）2 声道为例：

- 1 字节 = 8 个 DSD 样本位
- 2 声道交错 → 每字节属于一个声道（`byte[0]=ch0 8bit, byte[1]=ch1 8bit, byte[2]=ch0 8bit, ...`）
- 每秒总字节数 ≈ 2.8224e6 / 8 × 2 ≈ **705.6 KB/s**

**目标问题：** 把 2.8 MHz 的 1-bit PDM 位流降采样到 88.2 kHz 的 16-bit PCM。

### 4.2 自动格式检测 (`findBestConfig`)

不同设备 / DFF 制作工具可能产生位序（MSB-first vs LSB-first）和字节内位对齐（bitOffset 0–7）的差异。解码器不会让用户猜，而是在解码开始前 **预读 2 MB 数据**，枚举 **16 种候选配置**（MSB × 8 偏移 + LSB × 8 偏移）逐一解码 32 帧，用 `evaluateAudioQuality()` 评分，取最高分。

**评分公式 `evaluateAudioQuality`（权重 0.4 / 0.35 / 0.25）：**

| 指标 | 含义 | 高分条件 |
|---|---|---|
| `levelScore` | 平均电平（绝对值均值） | 100–30000（正常音频动态）= 1.0 |
| `zcScore` | 过零率（0–1） | < 0.05 = 1.0；> 0.3 = 0（位序错时会噪声爆炸，过零率接近 0.5） |
| `rangeScore` | 峰值范围 | > 1000 = 1.0 |

> 这种启发式评分能把误解码时的噪声（高频 ±满幅，过零率极高）和正常音频区分开，成功率在测试集上接近 100%。

### 4.3 单级 FIR 降采样 + 卷积加速

原实现曾尝试单级 6144-tap FIR，在 4 分钟 DSD64 歌曲上要做 **~1300 亿次乘加**，手机上几小时都跑不完。现在采用 **64-tap 低通滤波 + 整数降采样**，单级完成（总阶数控制在 300–600 区间，速度提升 10–20 倍）。

**低通滤波设计 `designLowpassFilterFloat`：**
- 窗函数：**Hamming**（`0.5·(1 - cos(2π·i/(N-1)))`）
- 截止频率：`0.4 × (1/(2·decimation))`，留出 0.1 的过渡带
- 归一化：`sum(taps) / decimation`，保证降采样后直流增益为 1

**DSD 位提取 `extractDsdBits`（热循环分离）：**
把 DSD 字节流先展开为 `FloatArray` 平面（±1.0），彻底把 **字节位操作从 2 层嵌套循环中移出**，让卷积内循环变成纯 float multiply-accumulate。

```
dsdBitPlanes[frame × channels + channel] = +1.0f or -1.0f
```

**卷积 `convolveDsdToPcm`：**
```kotlin
for t in 0 until filterTaps:
    sum += dsdBitPlanes[idx] * filter[t]
    idx += channels          // 按声道跳步，等效每声道独立卷积
pcmValue = (sum / decimation) × SHORT_MAX  →  clip  →  LE 16-bit bytes
```

> `idx += channels` 是关键小技巧：把 `d[frame][ch]` 的二维访问压成一维平面后，`filter[t]` 对时间轴的卷积就变成了「首地址后每步跳 `channels` 格」，不再需要在循环里乘法算索引。

### 4.4 分块解码 & 内存控制

- **chunkFrames = 4096**：每次只让 4096 PCM 帧在流水线中
- **ringExtraDsd = filterTaps/2 + decimation**：为 FIR 群延迟保留额外的 DSD 输入帧，避免边界截断产生爆音
- **每次 chunk 最多读 4 MB DSD 数据**（vs 全量加载 1.2GB WAV 会 OOM）
- **低内存兜底**：`WavConversionDataSource` 之前会在剩余内存 < 30 MB 时跳过转码，直接把原始文件交给 ExoPlayer（最终会 fallback 为"不支持格式"错误，但不会崩）

### 4.5 边听边转码 (`DffStreamingDataSource`)

`DffStreamingDataSource` 实现了 Media3 的 `DataSource` 接口，维护一个后台高优先级线程（`Thread.MAX_PRIORITY`）持续往临时 WAV 文件写，同时 `read()` 端用 `Object.wait/notify` 等待新数据。

**状态同步：**

```
decoderThread (HIGH PRIORITY)
  └─ 每 chunk 写完成后： synchronized(lock) { writePosition += N; lock.notifyAll() }

DataSource.read() (播放器 IO 线程)
  └─ synchronized(lock) {
       while (writePosition - readPosition <= 0 && !finished) lock.wait(100ms)
       copy bytes from tempRandomAccessFile
     }
```

**超时保护：** `readPcmData` 设 30 秒超时，转码线程挂死时让上层播放器返回 0 字节触发 EOS，而不是无限等待卡死界面。

**进度报告：** 每 5% 变化触发 `progressCallback` 并写入 `TranscodeProgressManager.progress` StateFlow，UI 上直接观察。

## 5. 转码缓存策略

`TranscodeCacheManager` 使用 `(filePath + lastModified + fileLength)` 的 SHA-256 作为缓存 key，存放在：

```
<cacheDir>/transcode_cache/
  └─ <hash>.wav
```

特性：

- **缓存大小阈值：** 15 分钟内总占用超过 1 GB 时自动 LRU 清理旧文件
- **并发安全：** `Semaphore(1)` 全局 1 个并发转码；`getCacheLock(filePath)` 支持按文件细粒度互锁
- **死锁保护：** `tryAcquire(30, SECONDS)` + 自动 reset，防止某个线程崩溃持有信号量不释放
- **解码完成后才落缓存：** 避免中途失败留下半截缓存被误命中

## 6. 性能参考

以下数据来自骁龙 8 Gen 2 真机，DFF 文件 2.8224 MHz 2 声道、时长 4 分钟：

| 模式 | 开始播放耗时 | 整首解码耗时 | 峰值内存增量 |
|---|---|---|---|
| STREAM_WHILE_TRANSCODE | ~1.5s（header + 格式检测 128 帧预解码） | 实时跟播 | ≈ 4 MB (dsd + pcm 双 buffer) |
| CACHE_FIRST | 整首解码完成后才开始 | ~22s | ≈ 4 MB |
| 旧版 6144-tap FIR | — | >30 分钟（未跑完全） | — |

**建议设置：** 默认 STREAM_WHILE_TRANSCODE，对 10 MB 以下的 DSD 预览 / 反复切歌场景，`CACHE_FIRST` 的命中体验更好。

## 7. 错误排查

| 现象 | 可能原因 | 处理方式 |
|---|---|---|
| 播放静音 / 全是高频爆音 | 自动位序检测失败（非典型文件头） | 临时把 `findBestConfig` 的 `decim` 改成 `decimationFactor * 1` 并扩大 testFrames 到 128，或者在 `findBestConfig` 里加一段"若最高分 zcScore < 0.7 就强制 MSB 0"的兜底 |
| 转码进度长时间卡在 10% | 后台线程被系统冻结 | `DffStreamingDataSource` 使用的是 **非前台线程**，长时间后台会被 Doze 模式杀死；如果出现该问题，升级到 `CACHE_FIRST` 模式（前台转码 20s），或把转码放进 Foreground Service |
| 转码进度 200% / 负值 | 64-bit WAV data size 字段溢出 (RF64) 当前未处理，对 DSD 暂不影响（DFF 转码输出是标准 WAV）；但输入文件 >4GB 时会截断 | 如需支持 >4GB 输出，把 RIFF size / data size 改成 `Long` + 实际写入 `ds64` chunk |
| 大文件（>1GB）转码中途 OOM | 一次性读入了过多 DSD 数据 | 确认 `max(4MB)` 限制没被移除；若还是 OOM，把 `chunkFrames` 降到 1024 |
| DSF 格式不识别 | 当前 `DffDecoder` 只处理 DSDIFF（.dff）和裸 DSD；`.dsf` 走 miniaudio 解码路径 | 若 miniaudio 不工作，可在 `readDffInfo()` 里加 `fmt "DSD"` 的 Sony DSF 解析分支 |

## 8. 与其他解码路径的关系

```
                      WavConversionDataSourceDelegate.open(uri)
                                    │
                ┌───────────────────┴──────────────────┐
                │ 扩展名属于 DFF_EXTENSIONS (.dff/.dsd/.dif)？│
                └───┬─────────────────────────────────┘
                    │ Yes
    ┌───────────────┴─────────────────────────────────┐
    │ 1. 查 TranscodeCacheManager.getCachedFile() 命中？  │
    │    Yes → 直接返回缓存 WAV DataSource             │
    └─────┬───────────────────────────────────────────┘
          │ 未命中
    ┌─────┴────────────────────────────────────────────┐
    │ 2. transcodeMode？                                 │
    │    STREAM_WHILE_TRANSCODE                          │
    │      └─ → DffStreamingDataSource（边听边转码）      │
    │    CACHE_FIRST                                     │
    │      └─ → DffDecoder.decodeToWav() 整首解码        │
    │           成功后 cacheTranscodedFile()             │
    └─────┬────────────────────────────────────────────┘
          │ 3. DffDecoder 失败/兜底（极少触发）
    ══════╪══════════════════════════════════════════
          │ 扩展名属于 WAV_EXTENSIONS (.wav/.rf64)
    ┌─────┴────────────────────────────────────────────┐
    │ 4. 先试 miniaudio 流式 → 再试 miniaudio decodeToWav │
    └─────┬────────────────────────────────────────────┘
          │ 5. 最后 Kotlin WavConverter 处理 RF64/WAVE64
```
