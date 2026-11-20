<div align="center">

<img src="assets/icon.png" alt="像素播放器" width="120" />

# 像素播放器 🎵

**基於 [PixelPlayer](https://github.com/theovilardo/PixelPlayer/) 的改造版**

多平台線上音源 · 為中文使用者精心打造 · 像素美學與精緻動效

[![Latest Release](https://img.shields.io/github/v/release/r3n011/XiangsuPlayerHQ?include_prereleases&style=for-the-badge&logo=github&label=Release)](https://github.com/r3n011/XiangsuPlayerHQ/releases)
[![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/r3n011/XiangsuPlayerHQ/releases)
[![License](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)](LICENSE)
[![QQ Group](https://img.shields.io/badge/QQ%E7%BE%A4-907852088-12B7F5?style=for-the-badge&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/31XTTiZqg0)

**🌐 语言 / Languages / 言語 / Языки / Idiomas**

[简体中文](README.md) · [**繁體中文**](README_zh-TW.md) · [English](README_en.md) · [日本語](README_ja.md) · [Русский](README_ru.md) · [Español](README_es.md)

</div>

---

## 🖼️ 介面預覽

<p align="center">
  <img src="assets/screenshot1.jpg" width="19%" />
  &nbsp;
  <img src="assets/screenshot2.jpg" width="19%" />
  &nbsp;
  <img src="assets/screenshot3.jpg" width="19%" />
  &nbsp;
  <img src="assets/screenshot4.jpg" width="19%" />
</p>

---

## 📖 目錄

- [✨ 功能亮點](#-功能亮點)
- [📱 裝置需求](#-裝置需求)
- [⬇️ 下載](#️-下載)
- [📚 相關文件](#-相關文件)
- [❤️ 贊助](#️-贊助)
- [📄 授權](#-授權)
- [☎️ 聯絡我們](#️-聯絡我們)

> **相容性提示**：Android 9 以上可確保正常使用；Android 7 / 8 不保證穩定性；Android 12 以上才能獲得完整的模糊效果。

---

## ✨ 功能亮點

### 🎨 現代化 UI/UX

- Material You：依桌布動態配色
- 流暢動畫：平滑的過場與微互動
- 可自訂介面：可調圓角、導覽列樣式與精簡模式
- 深色 / 淺色主題：自動或手動切換
- 專輯封面取色：動態主題色
- **應用程式字型設定**：全域介面字型可自由更換 —— 內建字型、從[像素字型市集](https://github.com/zispace/hanzi-fonts)按需下載（MiSans / 思源 / 阿里普惠體等開源與免費商用字型），或匯入自己的 `.ttf/.otf` 字型檔；歌詞字型獨立設定互不影響
- 多語言支援：中文、英文、德文、西班牙文、法文、印尼文、義大利文、韓文、挪威文、俄文、土耳其文共 11 種

### 🎵 強大的播放能力

- Media3 ExoPlayer + FFmpeg：專業音訊引擎
- miniaudio 整合：透過 JNI 支援高保真格式的 C/C++ 解碼器
- DSD/DFF 支援：原生 DSD64/DFF 播放並附自訂轉碼引擎
- USB DAC 輸出：支援外接 USB DAC，獲得發燒級音質
- 背景播放：完整的媒體工作階段整合
- 播放佇列管理：支援拖曳重新排序
- 隨機 & 循環：支援所有播放模式
- 無縫播放（Gapless）：曲目間平滑過渡
- 自訂過場：可設定歌曲間的交叉淡入淡出
- ReplayGain 支援：自動音量正規化

### 📚 音樂庫管理

- 多格式支援：MP3、FLAC、AAC、OGG、WAV、ALAC、M4A、DSD/DFF 等
- 瀏覽方式：依歌曲、專輯、演出者、曲風、資料夾、播放清單瀏覽
- 智慧演出者解析：可設定多演出者分隔符
- 專輯演出者分組：正確組織專輯
- 資料夾篩選：選擇要掃描的目錄
- 標籤編輯器：使用 TagLib 編輯中繼資料（支援 MP3、FLAC、M4A）

### ☁️ 雲端服務整合

- QQ 音樂：可從 QQ 音樂帳戶串流播放
- 網易雲音樂：瀏覽網易雲歌單，支援訊息中心與「一起聽」
- Bilibili：播放 B 站音訊內容
- Jellyfin：完整的 Jellyfin 媒體伺服器整合
- Navidrome / Subsonic：相容 Subsonic 的伺服器支援
- Telegram：從 Telegram 頻道瀏覽並播放音樂
- 雲端代理串流：基於代理的自適應品質串流

### 🤖 AI 整合

- AI 播放清單生成：支援 Gemini、Deepseek、OpenAI、Groq、OpenRouter
- AI 中繼資料生成：自動產生中繼資料與描述
- AI 播放清單評估器：為生成的歌單給出品質評分
- 智慧每日混音：依聆聽習慣的個人化推薦
- **AI 外接（MCP）**：內建 MCP 伺服器，相容用戶端配對後可直接控制播放、檢索音樂庫、建立播放清單
- 使用分析：追蹤 AI token 使用量與費用統計

### 🔍 探索與整理

- 全文搜尋：跨庫搜尋並支援智慧篩選
- 每日混音：AI 個人化播放清單
- 播放清單：拖曳管理自訂播放清單
- 統計：詳細的聆聽歷史與習慣分析
- 智慧播放清單：基於規則的自動生成
- 專注模式：番茄鐘（學習 / 休息循環）

### 🎤 歌詞

- 同步歌詞：透過 LRCLIB API 與 Kugou 取得 LRC 格式
- 逐字歌詞：支援 AMLL 逐字卡拉OK高亮
- RTL 歌詞：阿拉伯文 / 希伯來文等歌詞自動切換版面方向與對齊
- 歌詞編輯：可修改或新增歌詞
- 捲動顯示：隨音樂高亮目前歌詞
- 翻譯支援：同步翻譯與羅馬拼音顯示
- 歌詞字型：獨立字型設定，支援多種可下載中文字型
- 歌詞分享：產生精美分享卡片
- 本機歌詞：從本機儲存空間載入歌詞

### 🎚️ 等化器

- 系統等化器：完整參數化 EQ 控制
- AutoEq 整合：依耳機自動套用預設
- 自訂預設：儲存並載入 EQ 設定
- 風格預設：搖滾、流行、古典等

### 🖼️ 演出者圖片

- Deezer 整合：自動取得演出者圖片
- 智慧快取：記憶體（LRU）+ 資料庫快取，支援離線
- 備援圖示：圖片不可用時的精美替代圖示
- 取色：從專輯封面擷取動態色彩

### 📲 多裝置

- Chromecast：投放至電視或智慧音箱
- Android Auto：支援車機播放
- 小工具（Widgets）：Glance 桌面小工具（4x1、4x2）
- 藍牙：支援藍牙音訊裝置
- Web 遠端控制：透過 HTTP 服務在瀏覽器中控制播放

### 💾 備份與還原

- 完整備份：匯出所有設定、播放清單與音樂庫資料
- 選擇性還原：選擇要還原的模組
- 雲端備份：備份到各類雲端儲存服務

### ⚙️ 進階功能

- 外掛中心：QuickJS 沙箱外掛（商店 / 本機匯入 / 權限隔離 / 自帶 UI 面板）
- 聽歌辨識：錄製環境音辨識歌曲
- 聽力保護：耳機音量監測與超限提醒
- 批次操作：佇列、多選、分享與收藏的批次處理
- 格式轉換器：音訊格式轉碼（如 DFF→WAV）
- 佇列分享：透過 M3U 檔案分享播放清單
- 手勢控制：基於手勢的導覽
- 開發者選項：為進階使用者提供的高級設定

---

## 📱 裝置需求

| 項目 | 需求 |
|------|------|
| **系統版本** | Android 9 (API 28) 或以上 |
| **記憶體** | 建議 6GB 以上，體驗更順暢 |
| **USB OTG** | 用於 USB DAC 外接音效卡 |

---

## ⬇️ 下載

<div align="center">

### 從 GitHub Releases 取得最新安裝檔

| 版本 | 說明 | 分包 |
|:----:|------|------|
| **Full** | 完整版，含 Telegram 線上播放 | arm64-v8a / armeabi-v7a / x86_64 |
| **Lite** | 精簡版，不含 Telegram 與 GMS（體積更小） | arm64-v8a / armeabi-v7a / x86_64 |

每個分包均附帶 `.sha256` 校驗檔；手機使用者通常選擇 **arm64-v8a** 即可。

<br>

[![GitHub Releases](https://img.shields.io/badge/%E4%B8%8B%E8%BC%89-GitHub%20Releases-181717?style=for-the-badge&logo=github)](https://github.com/r3n011/XiangsuPlayerHQ/releases)
[![官網](https://img.shields.io/badge/%E5%AE%98%E7%B6%B2-pixel.grammx.asia-4CAF50?style=for-the-badge&logo=googlechrome&logoColor=white)](https://pixel.grammx.asia)

<sub>更多平台安裝檔（Windows / TV 等）請前往官網取得</sub>

</div>

---

## 📚 相關文件

- 📋 [更新日誌](CHANGELOG.md)
- ⚠️ [平台介面與穩定性風險說明](docs/reverse-api-risk-notes.md)
- 📦 [第三方開源元件授權](THIRD_PARTY_NOTICES.md)
- 🖥️ [桌面端 P1R2 架構選型決議](docs/desktop-p1r2-decisions.md)

---

## ❤️ 贊助

你的每一筆贊助，都能讓我們做出更好玩、更有趣的功能。

**[點此進入贊助頁面](https://pixel.grammx.asia/donate.html)**

---

## 📄 授權

原專案 [授權](LICENSE) · 本專案使用 **GPL_V3** 協議

---

## ☎️ 聯絡我們

QQ 群：[907852088](https://qm.qq.com/q/31XTTiZqg0)

<p align="center">
  <b>像素播放器團隊 製作 ❤️</b>
  <br><br>
  <a href="https://github.com/r3n011">r3n_011</a> ·
  <a href="https://github.com/yzrbz">yzrbz</a> ·
  <a href="https://github.com/hzx0831">HZX0831</a>
</p>
