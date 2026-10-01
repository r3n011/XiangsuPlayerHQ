# kuromoji 日语注音引擎（按需下载）

kuromoji-ipadic（~12.7MB APK 内占用）已从 APK 依赖中移除。用户首次触发**日语歌词罗马音**时，
`KuromojiEngine` 会下载本目录的 `kuromoji-ipadic-android.jar`（约 12.7MB，一次性），用
`DexClassLoader` 动态加载后经反射桥接给 `LyricsUtils.MultiLangRomanizer`。未下载/下载失败时
功能优雅降级（不显示罗马音），不影响其它任何功能。

## 产物构成

- `classes.dex`：`kuromoji-core-0.9.0.jar` + `kuromoji-ipadic-0.9.0.jar` 经 d8 合并转换
- `com/atilika/kuromoji/ipadic/*.bin`：8 个词典数据文件（随 jar 原样携带）
- 完整性校验：SHA-256 硬编码在 `KuromojiEngine.kt`（下载后校验失败会丢弃重下）

## 托管（需要做一次）

把 `kuromoji-ipadic-android.jar` 发布到 GitHub Release：

```bash
gh release create kuromoji-engine-v1 \
    tools/kuromoji/kuromoji-ipadic-android.jar \
    --repo r3n011/XiangsuPlayerHQ \
    --title "kuromoji-ipadic engine v1 (0.9.0)" \
    --notes "日语罗马音按需下载引擎，由 App 的 KuromojiEngine 在首次使用时拉取"
```

下载地址约定（`KuromojiEngine.ENGINE_URL`）：
`https://github.com/r3n011/XiangsuPlayerHQ/releases/download/kuromoji-engine-v1/kuromoji-ipadic-android.jar`

App 端已复用 `ApkDownloadInstaller` 同款的 ghproxy 镜像前缀（ghproxy.net 等），国内可直连。

## 升级词典版本时

```bash
SDK_BUILD_TOOLS="<SDK>/build-tools/37.0.0" \
SDK_PLATFORM="<SDK>/platforms/android-37" \
./tools/kuromoji/build_engine_jar.sh
```

然后把输出中的 `sha256` 与字节数回填到 `KuromojiEngine.kt` 的
`ENGINE_SHA256` / `ENGINE_SIZE_BYTES`，再发新 Release tag 并同步改 `ENGINE_URL`。
