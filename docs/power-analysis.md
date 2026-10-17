# 功耗分析报告：亮屏浏览场景

> 测量日期：2026-10-17 · 基线版本：1.7.1 (versionCode 68) · 被测包 `com.r3n011.pixelplay` release
> 设备：Xiaomi 25097RP43C · Android 17 (SDK 37) · arm64 · 9200 mAh · 2136×3200 @120Hz（支持 144Hz）
> 采集脚本：`tools/adb_power_bench.py`（本文所有数字均由它那一套命令复现）

本报告只回答一个问题：**这个播放器在「亮屏浏览乐库」时，电到底被谁吃掉了，哪些是代码能改的。**

---

## 一、方法论前提：为什么不能靠 `--reset`

MIUI / HyperOS 会拒绝 shell 的清零请求：

```
$ adb shell dumpsys batterystats --reset
Security exception: Package android does not belong to 2000
```

所以"清零统计 → 跑场景 → 读数"这条路走不通。本报告改用**快照差值法**，两路并行：

| 通路 | 来源 | 性质 |
|---|---|---|
| **真实耗电** | `dumpsys battery` 的 `Charge counter`（µAh） | 硬件库仑计，绝对可信 |
| **分项归因** | `dumpsys batterystats --charged` 里目标 UID 的 `(on battery, …)` 分组 | 模型估算，用于拆 screen / cpu / audio / video / wakelock |

两个关键细节：

- `--charged` 只有 **37 KB**（完整 dump 是 40 MB），可以高频抓；且 `(on battery, …)` 分组天然只统计"本次放电窗口"，不会混进充电期间的历史数据。
- **库仑计刷新率约 30–60 秒**，所以单个阶段不要短于 3 分钟，否则差值会失真（实测 40 秒的阶段直接测出 0 mAh）。

## 二、测量条件

| 项 | 值 |
|---|---|
| 包 / 版本 | `com.r3n011.pixelplay` · 1.7.1 (68) release（非 debuggable，代表真实用户） |
| 场景 | 乐库 → **歌曲** 标签页 |
| 屏幕 | 常亮，亮度固定 128/255，自动亮度关闭 |
| 音量 | 媒体音量固定 index 8（70/160） |
| 网络 | Wi-Fi（歌单里混有网易在线曲，测得该 App 期间收 8.8 MB） |
| 干扰 | `com.r3n011.pixelplay.debug`、`com.theveloper.pixelplayer` 已 force-stop |
| 供电 | **USB 已拔线**（`adb tcpip 5555` 走无线），`dumpsys battery` → `status: 3` 放电中 |
| 起始状态 | level 64%，battery 31.0 °C，`thermal status 0` |

## 三、核心结果

| 阶段 | 时长 | 库仑计 Δ | **平均电流** | 折算功率<sup>*</sup> | 电池温度 | 备注 |
|---|---:|---:|---:|---:|---:|---|
| P0 亮屏 + 静止 + 播放 | 182 s | 140.0 mAh | **2769 mA** | ~10.8 W | 31.0 → 31.0 °C | janky 帧 ±0 |
| P1 亮屏 + 滑动 512 次 + 播放 | 386 s | 331.0 mAh | **3087 mA** | ~12.0 W | 31.0 → 34.0 °C | 90th 帧 14→17 ms |
| P2 **息屏** + 播放 | 388 s | 11.0 mAh | **102 mA** | ~0.4 W | 34.0 → 29.0 °C | 音频未中断 |

<sup>*</sup> 按 3.9 V 折算，仅用于直观量级；绝对电流会随机型/亮度/温度变化，**不可跨设备引用**。

**结论 1：息屏播放 102 mA vs 亮屏浏览 3087 mA，差 30 倍 —— 屏幕及其带动的合成渲染占了整机功耗的 96.7%。**

**结论 2：亮屏浏览的滚动增量只有 +318 mA（+11.5%）**，远小于屏幕本身的成本。

**结论 3：续航推算**（9200 mAh）

- 亮屏浏览 ≈ 3000 mA ⇒ **约 3.1 小时**
- 息屏播放 ≈ 102 mA ⇒ **约 90 小时**（理论上限，未计网络与后台唤醒）

交叉验证：这段时间 `level` 从 64% → 59%（5% × 9200 = 460 mAh），库仑计累计 482 mAh，吻合。

## 四、App 自身归因（拆分 screen / cpu / audio）

单位 mAh，仅统计放电窗口：

| 阶段 | 状态 | screen | cpu | audio | video | wakelock |
|---|---|---:|---:|---:|---:|---:|
| P0 (182 s) | screen on | 19.79 | 5.25 | 1.62 | 0 | 0 |
| P1 (386 s) | screen on | 42.30 | 22.61 | 3.49 | 0 | 0 |
| P2 (388 s) | screen off/doze | — | 2.29 | 3.35 | 0 | 0.29 |

归一化到每分钟，趋势就非常清楚了：

| 阶段 | screen | cpu | audio |
|---|---:|---:|---:|
| P0 静止 | 6.52 | **1.73** | 0.53 |
| P1 滚动 | 6.57 | **3.51** | 0.54 |
| P2 息屏 | — | 0.35 | 0.52 |

- **滚动让 CPU 翻倍（1.73 → 3.51 mAh/min，+103%）**，而 screen 几乎不动（6.52 → 6.57）。
  也就是说：**滚动的代价几乎全部落在 CPU 上**，这正是 `app/performance_analysis.md` 里那批发现（无效重组、position ticker 高频更新）在电表上的体现。
- **音频恒定 0.52–0.54 mAh/min**，在亮屏场景只占 5~6%。它不是大头。
- 息屏时 screen 归因彻底归零，剩下的只有 audio 0.52 + cpu 0.35。

模型与库仑计的偏差（供判读模型数据时参考）：

| 阶段 | 库仑计 | 模型 Computed drain | 偏差 |
|---|---:|---:|---:|
| P0 | 140.0 mAh | 123.2 mAh | −12% |
| P1 | 331.0 mAh | 271.0 mAh | −18% |
| P2 | 11.0 mAh | 29.0 mAh | **+164%** |

> 息屏阶段模型高估 2.6 倍：因为模型是在"屏幕开着"的功耗常量上外推的，屏幕一关它就失准。**息屏场景只信库仑计。**

## 五、音频通路：结论修正

### 实测事实

播放全程**没有走 hardware offload**：

```
Output thread ... name AudioOut_15, tid 3022, type 0 (MIXER):   ← 只有 MIXER，无 Offload/Direct 输出线程
  Standby: no
  HAL format: AUDIO_FORMAT_PCM_32_BIT      Processing format: PCM_32_BIT
  Sample rate: 48000 Hz                    HAL frame count: 1920 (40 ms 深缓冲)

$ adb shell dumpsys audio | grep state:started
  ... type:android.media.AudioTrack u/pid:1010320/13065 state:started
      flags=0xA00(FLAG_DEEP_BUFFER+FLAG_MUTE_HAPTIC) ... sampleRate=44100
```

三点旁证：

1. 40 MB 完整 `batterystats` dump 中检索 `AudioOffload` / `AudioOffloadMix` —— **零命中**，该通路从未被使用；
2. 源文件 44.1 kHz → 输出线程 48 kHz，存在重采样，且处理格式是 PCM_FLOAT/32bit（AudioFlinger 全程参与混音）；
3. `audioserver` 代为持有 `AudioMix` PARTIAL_WAKE_LOCK：
   ```
   PARTIAL_WAKE_LOCK 'AudioMix' ACQ=-16m48s LONG
     (uid=1041 pkg=audioserver, ws=WorkSource{ chains=WorkChain{(1010320), (1041)}})
   ```

### 但要修正一个判断

我最初把"无 offload"列为头号功耗杀手，**实测不支持这个排序**：息屏播放整机只有 102 mA。

原因在于 `FLAG_DEEP_BUFFER` —— 它是 **40 ms 深缓冲**，AudioFlinger 每秒只需唤醒 25 次去喂 PCM；如果是 5 ms 的 fast track，唤醒次数是它的 8 倍，代价才会显著。

> **修正结论：offload 属于「中等收益」优化**，乐观估计把息屏播放从 ~102 mA 压到 ~70–80 mA（−20~30%）。它值得做，但不是救命稻草，优先级低于"别在亮屏时白烧 CPU"。

## 六、待验证：屏幕可见时约 2× vsync 的持续重绘

| 区间 | 帧数增量 | 覆盖时长 | 等效帧率 | 期间操作 |
|---|---:|---:|---:|---|
| P0 | +43,402 | 182 s | **238 fps** | 完全静止 |
| P1 | +91,568 | 386 s | **237 fps** | 512 次滑动 |
| P2 | +3,384 | ~430 s | 7.9 fps | 息屏 |

- 屏幕最高 144 Hz，却测出 **237 fps ≈ 2 × 120 Hz**，物理上不可能来自正常渲染节奏。
- P0 完全静止时 **janky 帧一帧未涨（674 → 674）**，说明这些帧都很快完成 —— 是"白烧"的，不是卡顿。
- 帧率与是否交互无关，恒定在 237 fps ⇒ 典型的**无节流失效动画 / 无限无效化循环**（某个 Composable 每个 vsync 被反复 invalidate，一次 vsync 触发多次绘制）。

> ⚠️ **这条结论目前只有两段正面证据，尚待复核。** 我试图做"A/B 对照"时用 `KEYCODE_SLEEP` 灭屏，设备进入 MIUI 锁屏黑屏态（`mDreamingLockscreen=true`，screencap 输出全黑），那次测到的 0 帧属**无效数据**，不能当反例。
>
> **复核方式**：解锁手机并保持 App 在前台，跑一次 60 秒静止 + `dumpsys gfxinfo`，若仍是 ~230 fps 则确认；再用 Perfetto 抓 10 秒 trace，看 `Choreographer#doFrame` 与 Compose 的 invalidate 来源。

### 定位线索（静态代码排查，未验证）

`rememberInfiniteTransition` 每帧都会请求一次无效化，只要宿主可见就永不停止 —— 与"静止 238 fps、janky 0 增长"的现象吻合。按"常驻可见 + 无限动画"筛选：

| 文件 | 位置 | 动画 | 乐库页是否可见 |
|---|---|---|---|
| `presentation/components/player/StyledPlayerSeekBar.kt` | L346 | `rememberInfiniteTransition("thumbRotate")`：滑块 0→360° 循环，`tween(4000, LinearEasing)` | **是** —— 折叠播放条就贴在列表底部，其进度条滑块常驻 |
| `presentation/components/subcomps/SineWaveLine.kt` | L50 | `rememberInfiniteTransition("SineWaveAnimation")`：正弦相位无限循环 | 待确认调用点 |
| `presentation/components/ShimmerBox.kt` | L27 | shimmer 循环 | 仅加载占位时 |
| `presentation/components/WavyMusicSlider.kt` | L389 | 波形相位无限动画 | **已被注释**，非活跃 |

已排除：`MainActivity.kt` 的 L621 / L630 / L631 三处 `withFrameNanos { }` 是冷启动一次性对齐（非循环）；`AppleMusicRotatingBackground.kt` 内无无限动画。

**建议的单点实验**：把 `StyledPlayerSeekBar` 的 `thumbRotate` 换成静态滑块（或仅在展开播放器时启用），复测静止帧率是否归零。若归零，则该动画在"亮屏浏览"场景下的代价约为 §4 中 P0 的 CPU 份额。

若成立，这是**亮屏场景最大的省电收益点**：静止时本应 0 帧（实测熄屏/无内容时确实是 0 帧），现在却在满速重绘。

## 七、附带发现

| # | 问题 | 证据 |
|---|---|---|
| 1 | **debug 包 force-stop 后 20 秒内自行复活**，并会进入 `video` 电源状态 | `am force-stop` 后 `ps -A` 立即出现新 pid；历史含 `16:10:02 +video +state=1010288:"video"`。这也解释了旧数据里 debug 包 46 分钟的 `video` 功耗桶 |
| 2 | 同设备并存 3 个同源包，互相污染统计 | `com.r3n011.pixelplay` / `com.r3n011.pixelplay.debug` / `com.theveloper.pixelplayer`，各自独立 UID |
| 3 | AudioTrack 高频建销（track churn） | 14:33–14:56 期间每分钟新建/销毁一次 AudioTrack；另有 `0x200`（`TRACK_DIRECT`）轨反复创建又销毁、`-inf` dB、寿命仅 7 秒 |
| 4 | 后台定时任务持续唤醒 | `SessionWorker` / `SyncWorker` / `NavidromeSyncWorker` 每 30 秒级触发一次 `jobscheduler` |
| 5 | 在线曲带来网络功耗 | 测量期间该 App Wi-Fi 收 8.8 MB |

## 八、优化优先级

| 优先级 | 事项 | 依据 | 预期收益 |
|---|---|---|---|
| **P0** | 消除屏幕可见时的无节流重绘 | §6：静止 238 fps、janky 0 增长 | 亮屏场景 CPU 大头；乐观 −20~30% 整机电流 |
| **P0** | 滚动期重组优化（`performance_analysis.md` 的 Fase 1.1 state slicing / position ticker） | §4：cpu 1.73 → 3.51 mAh/min | 滚动场景 −10% 左右整机 |
| P1 | 音频 offload 通路 | §5：全程 MIXER + deep buffer | 息屏播放 −20~30%（~20–30 mA） |
| P1 | debug 包自启与 `video` 电源状态 | §7-1 | 开发机功耗与统计噪声，也可能影响正式包 |
| P2 | 降低后台 worker 频率 | §7-4 | 待机功耗 |
| P2 | 排查 AudioTrack churn | §7-3 | 切换/接续播放时的抖功耗 |

## 九、复现步骤

```powershell
# 1) 建立无线 adb（关键：USB 充电会毁掉放电数据）
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb tcpip 5555
& $adb connect <手机IP>:5555
# 2) 拔掉数据线，确认 status: 3
& $adb -s <手机IP>:5555 shell "dumpsys battery | grep -e status -e Charge"

# 3) 手机解锁、App 打开乐库->歌曲列表、开始播放并保持前台
# 4) 一键采集（--prepare 会固定亮度/超时并停掉干扰包）
python tools/adb_power_bench.py `
    --serial <手机IP>:5555 `
    --package com.r3n011.pixelplay `
    --stop-packages com.r3n011.pixelplay.debug,com.theveloper.pixelplayer `
    --prepare `
    --phase idle:180 --phase scroll:360 --phase screenoff:360 `
    --restore
```

脚本会按阶段打印：库仑计差值 / 平均电流 / 模型差值 / 帧增量，以及目标 UID 的 screen·cpu·audio·video·wakelock 分项差值。

## 十、测量坑清单（避免重踩）

| 坑 | 现象 | 对策 |
|---|---|---|
| `batterystats --reset` 被拒 | `Security exception: Package android does not belong to 2000` | 用快照差值法，别清零 |
| USB 在充电 | `status: 2`、`Time on battery` 只有几分钟 | **无线 adb + 拔线**，直到 `status: 3` |
| MIUI 锁屏黑屏 | `dumpsys display` 报 `state ON`，但 screencap 全黑、gfxinfo 恒 0 帧 | 先截图确认；脚本已内置护栏（截图 <120 KB 即告警） |
| 阶段太短 | 40 秒阶段测出 0 mAh | 单阶段 ≥180 s（库仑计刷新率 30–60 s） |
| 同源包污染 | 三个 `pixelplay*` 包各自记账 | 测量前 `am force-stop` 并核对 `pm list packages -U` 的 UID |
| debug 包自行复活 | force-stop 后 20 秒又出现在 `ps` | 每阶段前重新 force-stop，或直接测 release |
| 音量/亮度漂移 | 扬声器功放与屏幕功耗随音量/亮度线性变化 | 固定 `screen_brightness` + 关自动亮度 + 固定媒体音量 |
| 误信模型 | 息屏阶段模型高估 2.6 倍 | 息屏场景只信库仑计 |
| 忘记恢复设置 | 亮度/超时/常亮被改 | `--prepare` 存原值到 state.json，`--restore` 还原 |

## 附录 A：原始快照

| 快照 | 时间 | Charge counter | level | Computed drain | screen-on (screen/cpu/audio) | screen-off (cpu/audio/wakelock) |
|---|---|---:|---:|---:|---|---|
| S0 | 16:11:26 | 5,668,000 µAh | 64% | 11.8 | 4.31 / 1.74 / 0.327 | — |
| S1 | 16:14:28 | 5,528,000 µAh | 62% | 135 | 24.1 / 6.99 / 1.95 | — |
| S2 | 16:20:54 | 5,197,000 µAh | 59% | 406 | 66.4 / 29.6 / 5.44 | — |
| S3 | 16:27:22 | 5,186,000 µAh | 59% | 435 | 68.0 / 30.2 / 5.60 (+wa 0.315) | 2.29 / 3.35 / 0.294 |

## 附录 B：本轮测量中的无效/噪声片段（勿引用）

- 16:20 之后用 `KEYCODE_SLEEP` 做的"0 帧"对照 —— 设备实际停在 MIUI 锁屏黑屏，无效。
- 16:09:49–16:10:33 期间 `com.miui.home`、`com.xiaomi.market` 短暂抢占前台，S0 基线含少量这类噪声。
- 音频输出线程 `AudioOut_D`（与播放无关的 primary 通路）累计 `underruns=82/overruns=423`，这是设备常驻计数，**不归因于本 App**。
- P2 期间 `com.tencent.mobileqq` 等应用多次申请 `AudioMix`（通知音），已计入"息屏 102 mA"内，量级很小但存在。
