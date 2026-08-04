# XiangsuPlayer 🎵（中文）

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="应用图标" width="128"/>
</p>

<p align="center">
  <strong>一款美观且功能丰富的 Android 音乐播放器</strong><br>
  基于 Jetpack Compose 与 Material Design 3 构建
</p>

<p align="right">[English](./README.md) | [中文](./README_zh.md)</p>

***

## ‼️ 免责声明

- 本项目的任何分叉（fork）不在官方支持范围内。如使用分叉，请联系分叉者获取支持。

***

## ✨ 功能亮点

### 🎨 现代化 UI/UX

- Material You：基于壁纸的动态配色
- 流畅动画：平滑的过渡与微交互
- 可定制界面：可调圆角、导航栏样式与紧凑模式
- 深色/浅色主题：自动或手动切换
- 专辑封面色彩提取：动态主题色
- 多语言支持：含英文、中文、德语、法语、韩语、挪威语、俄语、土耳其语、西班牙语、意大利语、印尼语等

### 🎵 强大的播放能力

- Media3 ExoPlayer + FFmpeg：专业音频引擎
- miniaudio 集成：通过 JNI 支持高保真格式的 C/C++ 解码器
- DSD/DFF 支持：原生 DSD64/DFF 播放并带自定义转码引擎
- USB DAC 输出：支持外置 USB DAC 获得发烧级音质
- 后台播放：完整的媒体会话集成
- 播放队列管理：支持拖拽重排
- 随机 & 循环：支持所有播放模式
- 无缝播放（Gapless）：曲目间平滑过渡
- 自定义过渡：可配置歌曲间的交叉淡入淡出
- ReplayGain 支持：自动音量归一化

### 📚 媒体库管理

- 多格式支持：MP3、FLAC、AAC、OGG、WAV、ALAC、M4A、DSD/DFF 等
- 浏览方式：按歌曲、专辑、艺术家、流派、文件夹、播放列表浏览
- 智能艺术家解析：可配置多艺术家分隔符
- 专辑艺术家分组：正确组织专辑
- 文件夹过滤：选择要扫描的目录
- 标签编辑器：使用 TagLib 编辑元数据（支持 MP3、FLAC、M4A）

### ☁️ 云端服务集成

- QQ 音乐：可从 QQ 音乐账户流式播放
- 网易云音乐：访问网易云歌单
- Bilibili：播放 B 站音频内容
- Jellyfin：完整的 Jellyfin 媒体服务器集成
- Navidrome/Subsonic：兼容 Subsonic 的服务器支持
- Telegram：从 Telegram 频道浏览并播放音乐
- Google Drive：从 Google 云盘流式播放
- 云端代理流媒体：基于代理的自适应质量流

### 🤖 AI 集成

- AI 播放列表生成：支持 Gemini、Deepseek、OpenAI、Groq、OpenRouter
- AI 元数据生成：自动生成元数据与描述
- AI 播放列表评估器：为生成的歌单给出质量评分
- 智能每日混音：基于听歌习惯的个性化推荐
- 使用分析：跟踪 AI token 使用并进行费用统计

### 🔍 发现与组织

- 全文搜索：跨库搜索并支持智能过滤
- 每日混音：AI 个性化播放列表
- 播放列表：拖拽管理自定义播放列表
- 统计：详细的听歌历史与习惯分析
- 智能播放列表：基于规则的自动生成
- 专注模式：番茄钟（学习/休息循环）

### 🎤 歌词

- 同步歌词：通过 LRCLIB API 与 Kugou 获取 LRC 格式
- 歌词编辑：可修改或添加歌词
- 滚动显示：随音乐高亮当前歌词
- 翻译支持：同步翻译歌词显示
- 本地歌词：从本地存储加载歌词

### 🎚️ 均衡器

- 系统均衡器：完整参数化 EQ 控制
- AutoEq 集成：基于耳机的自动均衡预设
- 自定义预设：保存并加载 EQ 配置
- 风格预设：摇滚、流行、古典等

### 🖼️ 艺术家图片

- Deezer 集成：自动获取艺术家图片
- 智能缓存：内存（LRU）+ 数据库缓存以支持离线
- 后备图标：当图片不可用时的美观占位
- 颜色提取：从专辑封面提取动态颜色

### 📲 连接性

- Chromecast：推流到电视或智能音响
- Android Auto：支持车载播放
- 小部件（Widgets）：Glance 桌面控件（4x1、4x2）
- 蓝牙：支持蓝牙音频设备
- Web 远程控制：通过 HTTP 服务在浏览器中控制播放

### 💾 备份与还原

- 完整备份：导出所有设置、播放列表与媒体库数据
- 选择性还原：选择需要还原的模块
- 云端备份：备份到各类云存储服务

### ⚙️ 高级功能

- 批量操作：队列、多选、分享与喜欢的批量处理
- 格式转换器：音频格式转码（如 DFF→WAV）
- 队列分享：通过 M3U 文件分享播放列表
- 手势控制：基于手势的导航
- 开发者选项：为高级用户提供的高级设置

***

## 🛠️ 技术栈

同英文 README：Kotlin、Jetpack Compose、Media3 ExoPlayer、miniaudio、Hilt、Room、DataStore、Retrofit/OkHttp、Coil、Kotlinx Serialization、协程、WorkManager、TagLib、Glance、AutoEq 等。

***

## 📱 系统要求

- Android 11（API 30）或更高
- 推荐 6GB RAM 以获得流畅体验
- USB OTG：用于 USB DAC 支持

***

## 🚀 快速开始

### 前置环境

- Android Studio Ladybug | 2024.2.1 及以上
- Android SDK 37+
- JDK 21+
- NDK（用于 miniaudio JNI 编译）

### 安装与运行

1. 克隆仓库
```sh
git clone https://github.com/theovilardo/PixelPlayer.git
cd PixelPlayer
```
2. 在 Android Studio 中打开项目并同步 Gradle
3. 编译并运行（注意首次构建可能需要编译 NDK）

***

## ⬇️ 下载

请参考英文 README 中的下载链接。

***

## 🤝 贡献

欢迎贡献！请提交 Pull Request。贡献说明与问题报告请参考英文 README 中的“Contributing”部分。

***

## 🔗 链接

- 官方仓库 Issues、功能请求与 Bug 报告请参考英文 README 中的链接。

***

## 📄 许可证

本项目使用专有许可证（Proprietary License），详情请查看仓库 LICENSE 文件。

***

<p align="center">
  Made with ❤️ by <a href="https://github.com/theovilardo">theovilardo</a>
</p>
