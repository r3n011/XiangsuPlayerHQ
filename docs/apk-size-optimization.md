# 安装包体积优化调研报告

> 调研日期：2026-10-01 · 基线版本：1.6.3 (versionCode 60) · arm64-v8a release

## 一、现状基线

本次调研实际执行了 `:app:assembleRelease`，产出并逐项拆解了当前代码的 release 包：

| 产物 | 体积 |
|---|---|
| **PixelPlay-1.6.3-60-release-arm64.apk** | **61.87 MB** |
| PixelPlay-1.6.3-60-release-x86.apk | 65.78 MB |
| （参考）6 月的旧包 app-arm64-v8a-release.apk | 84.46 MB（当时 proguard 规则未收窄，DEX 原始 99MB） |

8 月 7 日的 proguard 规则重构（v2.2，去包级全保留）+ R8 fullMode 已把 DEX 从 99MB 压到 30.8MB，这一步的收益已经落地。

### 61.87 MB 的构成（APK 内实际占用 = 压缩后大小）

| 分类 | APK 内实占 | 未压缩体积 | 明细 |
|---|---:|---:|---|
| **lib/（native，不压缩存储）** | **25.71 MB** | 25.7 | libtdjni.so 21.7、libminiaudio_jni.so 3.43、libffmpegJNI.so 1.39、libquickjs 1.27、libtaglib 1.20、libumeng-spy 0.38、其余 ~0.3 |
| **kuromoji 日语词典（com/atilika/**）** | **12.71 MB** | 31.9 | ipadic 8 个 .bin，最大的 tokenInfoDictionary.bin 9.4MB |
| **DEX（R8 后 5 个 dex）** | **12.66 MB** | 30.8 | 其中 tdlib Java 绑定 3183 类、kotlin-reflect 1793 类被 keep 规则整包保留 |
| **字体（res/*.ttf）** | **3.20 MB** | 5.5 | gflex_variable.ttf（Google Sans Flex）3.9MB + genre_variable.ttf 1.77MB |
| **AutoEQ 数据（assets）** | **3.12 MB** | 6.3 | `autoeq/headphone_presets.db` 4.98MB（**代码零引用，死资产**）+ `autoeq_profiles.json` 1.58MB（noCompress 不压缩存储） |
| resources.arsc | 2.16 MB | 2.2 | 已被资源收缩优化过（旧包 5MB） |
| 其它 res + META-INF 等 | 1.79 MB | 3.6 | 含 theveloper_icon.png 1.06MB |
| pinyindb（pinyin4j 词典） | 0.21 MB | 0.7 | |

## 二、优化项（按 收益/成本 排序）

### 第 1 批：立即可做，风险低（预计 -7 ~ -8 MB → 包体 ~54 MB）

**1. 删除死资产 `assets/autoeq/headphone_presets.db`（-3.1 MB）**
全代码库检索无任何引用：运行时只走 `AutoEQManager.loadProfiles()` 读 `autoeq_profiles.json`；`PixelPlayDatabase.kt` 里的 headphone_presets 表是 SQL 建表，不从该 .db 导入（无 createFromAsset）。这是打包进 assets 的孤儿文件。
*验证方式：删除后构建，回归 AutoEQ 均衡器功能的预设加载/搜索即可。*

**2. kuromoji 词典按需化（-12.7 MB，APK 内第二大项）**
用途仅一个：`LyricsUtils.kt` 的 `MultiLangRomanizer.romanizeJapanese()`（日语歌词转罗马音/注音）。且代码已对分词器缺失做了优雅降级（`kuromojiTokenizer ?: return null`），缺失时只是不显示罗马音，不影响播放。
推荐方案：
- **方案 A（推荐）**：把 `com.atilika.kuromoji:kuromoji-ipadic` 从依赖中移除，改为"用户首次使用日语注音时"从自有服务器/GitHub Release 下载词典 + kuromoji jar（DexClassLoader 动态加载工程量较大）或仅下载词典数据并自建轻量读取（kuromoji 的 Tokenizer 支持自定义词典构建，可用 `UserDictionary` 模式离线构建小词典）。
- **方案 B（成本最低）**：新增 flavor（如 `full` / `lite`），lite 渠道不带 kuromoji；官网/群文件默认发 lite。
- **方案 C**：若判定该功能使用率极低，直接移除（已有的韩语/天城文转写都是内置映射表实现，不受影响）。

**3. 字体子集化（-2 ~ -2.5 MB）**
两个 .ttf 已确认只含拉丁区段（cmap format4，无 CJK），3.9MB 的体积来自变量轴/字形复杂度，纯展示用途完全可子集化：
```
pyftsubset gflex_variable.ttf --unicodes="U+0020-007E,U+00A0-00FF,U+2000-206F" --layout-features='*' --output-file=gflex_subset.ttf
```
预计 3.9MB → <1MB。`genre_variable.ttf` 仅 StatsScreen 一处使用，可顺手子集化或改用系统字体。

**4. `autoeq_profiles.json` 的 noCompress 移除（-1.2 MB）**
`androidResources.noCompress.add("json")` 使 1.58MB 的 JSON 以不压缩形式存储（原注释担心"release 下压缩 asset 读取失败"，实际 assets 的 deflate 读取在 release 下是正常的，当时的失败更可能是同名 .db 相关问题）。移除后按 deflate 存储，约 1.58MB → 0.3MB。若第 1 项验证时 AutoEQ 功能正常，此项一并回归即可。

### 第 2 批：中等工程量（再 -3 ~ -5 MB → 包体 ~49 MB）

**5. 排除 kotlin-reflect（-1.5 ~ -2.5 MB DEX）**
`io.ktor:ktor-server-core-jvm:3.5.0` 传递引入了 `org.jetbrains.kotlin:kotlin-reflect:2.3.21`（约 1800 类），同时 `-keep class kotlin.reflect.** { *; }` 阻止 R8 裁剪它。Web 远程控制（ktor-server-cio）实际用不到完整反射。
```kotlin
implementation(libs.ktor.server.core) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
}
```
并把 keep 规则收窄为 `-keepnames class kotlin.reflect.jvm.internal.*`（若运行期报 MissingFieldException 再回退）。需要回归 Web 远程控制功能。

**6. jaudiotagger 去重（-1 ~ -1.5 MB）**
项目同时带了 native TagLib（`io.github.kyant0:taglib`，1.2MB .so，元数据主路径）和纯 Java 的 jaudiotagger 3.0.1（~700 类被 `-keep class org.jaudiotagger.**` 全保留）。若 TagLib 已覆盖 MP3/FLAC/M4A 的读+写（README 是这么声明的），jaudiotagger 只是 `AudioMetadataReader`/`SongMetadataEditor` 里的回退路径，可评估移除或保留一个。
另：`-keep class org.jaudiotagger.** { *; }` 可先收窄成仅保留实际用到的包（tag.id3/tag.flac/mp4），立省一部分。

**7. theveloper_icon.png（-0.7 MB）**
`res/drawable/theveloper_icon.png` 1.06MB，转 WebP 或降采样后预计 <300KB。

**8. libminiaudio_jni.so 编译参数（-1 ~ -1.5 MB）**
3.43MB（stripped）对一个单头文件库偏大。CMakeLists 已有 16KB 对齐 flag，可再叠加：
```cmake
target_compile_options(miniaudio_jni PRIVATE -Oz -fno-exceptions -ffunction-sections -fdata-sections)
target_link_options(miniaudio_jni PRIVATE -Wl,--gc-sections -flto=thin)
```
（需回归 DSD/DFF 播放与 USB DAC 独占输出。）

**9. proguard 规则再收窄（-1 ~ -2 MB）**
还残留的宽规则：
- `-keep class * implements java.io.Serializable { *; }` —— 会把所有 Serializable 的全部成员连进包；
- `-keep class io.ktor.server.engine.** { *; }` / `io.ktor.server.cio.** { *; }` —— CIO 引擎只需 ServiceLoader 级别的 keepnames；
- `-keepattributes *Annotation*` 保留全部注解类，可只保留运行时注解；
- 若接受崩溃日志需 retrace，可去掉 `-keepattributes LineNumberTable`（省 ~5-8% DEX）；
- 可加 `-repackageclasses ''` + `-allowaccessmodification` 改善混淆与裁剪。
每次收窄后必须跑全量功能回归（尤其备份还原、AI、数据模型，因为 `data.**` 多个包是整 keep）。

### 第 3 批：结构性决策（收益最大，需要产品决策）

**10. tdlib / Telegram 功能（21.7MB native + ~4-6MB DEX，APK 内第一大项）**
`org.drinkless.tdlib.**` 因 JNI 反射必须整包保留（3183 类），native 侧 21.7MB 无法被 R8 触及。可选路线：
- **Flavor 拆分**：`lite`（无 Telegram/tdlib）默认分发，`full` 保留全部功能。是 GitHub 直发 APK 场景下唯一无损的方案，预计 lite 版 **~35MB**。
- **运行时动态加载**：Telegram 首次使用时下载 libtdjni.so + dex（App 已有 GitHub 更新器、音源市场这类自更新基建，模式上可行），主包再省 21.7MB → **~15-20MB**。工程量大（so 版本管理、降级、合规），且要注意 16KB 页对齐与签名校验。
- **保持现状**：若 Telegram 功能是核心卖点则不动。

**11. x86 release 包是否还有必要（65.78MB 那个）**
release splits 现为 arm64-v8a + x86。x86 真机在国内市场几乎绝迹，主要服务模拟器。若分发渠道只发 arm64，可从 `splits.include` 中移除 x86（保留 debug 通用包覆盖模拟器），同时降低 CI 产物体积与打包时间。

**12. 分发侧：APK 直发 vs AAB**
官网/GitHub 只能发 APK（用户无法直装 AAB），现有 per-ABI split + resConfigs(11 语言) + 资源收缩已是 APK 形态下的合理做法。若未来上架 Play，AAB 的语言/密度按需下发可再让用户侧下载体积降 ~2-3MB，无需改代码，`bundle {}` 配置已就绪。

**13.（权衡项）extractNativeLibs（legacy packaging）**
当前 native 库以不压缩形式存于 APK（minSdk≥23 默认）。开启 `useLegacyPackaging = true` 可让 25.7MB 的 lib 压缩到 ~11-13MB（**下载体积 -13MB**），代价是安装后磁盘占用增加（so 解压两份）和启动时解压开销。对"群文件/网盘分发、下载体积敏感"的场景值得考虑；上架 Play 则不要开。

## 三、收益汇总

| 阶段 | 措施 | 预计包体 |
|---|---|---:|
| 现状 | — | 61.87 MB |
| 第 1 批 | 死资产 + kuromoji + 字体子集 + JSON 压缩 | **~52-54 MB** |
| 第 2 批 | kotlin-reflect + jaudiotagger + 图标 + miniaudio -Oz + 规则收窄 | **~48-50 MB** |
| 第 3 批 | tdlib flavor 拆分（lite） | **~27-30 MB** |
| 第 3 批+ | tdlib 运行时动态加载 | **~15-20 MB** |

## 四、执行注意

- 每一项改动后重跑一次 `assembleRelease` 并用 `unzip -l` / APK Analyzer 对比构成，防止依赖图变化引入回退。
- kuromoji、jaudiotagger、kotlin-reflect 三项都涉及"优雅降级路径"，删除前先确认对应功能（日语注音、TagLib 之外的标签回退、Web 远程控制）的回归用例。
- miniaudio/USB 相关的 native 编译改动需在 Android 15+（16KB 页）与 USB DAC 真机上验证。
- 死资产删除（headphone_presets.db）置信度高（静态零引用），但建议发版前跑一次 AutoEQ 全流程。
