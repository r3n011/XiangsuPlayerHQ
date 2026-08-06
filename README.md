# 像素播放器 🎵

<p align="center">
  <img src="https://pixel.grammx.asia/res/pic/logo.png" alt="App Icon" width="128"/>
</p>

<p align="center">
  <strong> 本项目是 <a href="https://github.com/theovilardo/PixelPlayer/">这个项目</a> 的魔改版 </strong><br>
  加入了多平台支持，以及更多适合中国用户的功能，更有更多有趣好玩的功能等你来玩

</p>

<p align="center">
  <img src="https://pixel.grammx.asia/res/pic/%E6%92%AD%E6%94%BE%E9%A1%B5.png" alt="Screenshot 1" width="200" style="border-radius:26px;"/>
  <img src="https://pixel.grammx.asia/res/pic/%E6%AD%8C%E8%AF%8D%E6%90%9C%E7%B4%A2.png" alt="Screenshot 2" width="200" style="border-radius:26px;"/>
  <img src="https://pixel.grammx.asia/res/pic/JS%20%E5%BC%95%E6%93%8E.png" alt="Screenshot 3" width="200" style="border-radius:26px;"/>
  <img src="https://pixel.grammx.asia/res/pic/%E8%B4%A6%E6%88%B7%E7%AE%A1%E7%90%86.png" alt="Screenshot 4" width="200" style="border-radius:26px;"/>
</p>

<p align="center">
    <a href="https://github.com/r3n011/XiangsuPlayerHQ/release">
        <img src="https://img.shields.io/github/v/release/r3n011/XiangsuPlayerHQ?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest Release">
    </a>
    <a href="https://github.com/r3n011/PixelPlayerHQ/releases">
      <img src="https://img.shields.io/badge/Android-9%2B-green?style=for-the-badge&logo=android" alt="Android 9+">
    </a>
    

</p>

---

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

### 📲 多设备

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

---
## 📱 设备要求

- **Android 9** (API 28) 或更高
- **6GB 运存** 更顺滑的体验
- **USB OTG** 用于 USB DAC 支持

---

## ⬇️ 下载

<p align="center">
   安卓版 ⬇️
   <br>
   <a href="https://github.com/r3n011/PixelPlayerHQ/releases/latest">
      <img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="60">
   </a>


<p align="center">
   前往我们的官网获取更多平台安装包 ⬇️
   <br> 
   <a href="https://pixel.grammx.asia">
      <img src="https://pixel.grammx.asia/res/pic/logo.png" alt="Get it on offcial website" height="60">
   </a>
</p>

---


## 📄 许可证

原项目 [许可证](LICENSE) \
本项目使用 GPL_V3 协议

---

## ☎️ 联系我们

QQ群：<a href="https://qm.qq.com/q/31XTTiZqg0">907852088</a>

<p align="center">
   像素播放器团队 制作❤️
   <br>
   <a href="https://github.com/r3n011">r3n_011</a>
   <br>
   <a href="https://github.com/yzrbz">yzrbz</a>
</p>
