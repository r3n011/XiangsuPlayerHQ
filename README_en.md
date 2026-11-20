<div align="center">

<img src="assets/icon.png" alt="Pixel Player" width="120" />

# Pixel Player 🎵

**A customized fork of [PixelPlayer](https://github.com/theovilardo/PixelPlayer/)**

Multi-platform online sources · Polished for Chinese users · Pixel aesthetics with refined motion

[![Latest Release](https://img.shields.io/github/v/release/r3n011/XiangsuPlayerHQ?include_prereleases&style=for-the-badge&logo=github&label=Release)](https://github.com/r3n011/XiangsuPlayerHQ/releases)
[![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/r3n011/XiangsuPlayerHQ/releases)
[![License](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)](LICENSE)
[![QQ Group](https://img.shields.io/badge/QQ%E7%BE%A4-907852088-12B7F5?style=for-the-badge&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/31XTTiZqg0)

**🌐 语言 / Languages / 言語 / Языки / Idiomas**

[简体中文](README.md) · [繁體中文](README_zh-TW.md) · [**English**](README_en.md) · [日本語](README_ja.md) · [Русский](README_ru.md) · [Español](README_es.md)

</div>

---

## 🖼️ Screenshots

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

## 📖 Table of Contents

- [✨ Features](#-features)
- [📱 Requirements](#-requirements)
- [⬇️ Download](#️-download)
- [📚 Documentation](#-documentation)
- [❤️ Donate](#️-donate)
- [📄 License](#-license)
- [☎️ Contact](#️-contact)

> **Compatibility note**: Android 9 and above are fully supported; Android 7 / 8 are not guaranteed; full blur effects require Android 12+.

---

## ✨ Features

### 🎨 Modern UI/UX

- Material You: dynamic colors based on your wallpaper
- Fluid animations: smooth transitions and micro-interactions
- Customizable interface: adjustable corner radius, navigation bar style and compact mode
- Dark / light theme: automatic or manual
- Album art color extraction: dynamic accent colors
- **App font settings**: change the global UI font freely — built-in fonts, on-demand downloads from the [pixel font market](https://github.com/zispace/hanzi-fonts) (MiSans / Source Han / Alibaba PuHuiTi and other open-source & free-commercial fonts), or import your own `.ttf/.otf` files; lyrics font is configured independently
- Localization: 11 languages — Chinese, English, German, Spanish, French, Indonesian, Italian, Korean, Norwegian, Russian, Turkish

### 🎵 Playback

- Media3 ExoPlayer + FFmpeg: professional audio engine
- miniaudio integration: C/C++ decoders for hi-fi formats via JNI
- DSD/DFF support: native DSD64/DFF playback with a custom transcoding engine
- USB DAC output: external USB DAC for audiophile-grade sound
- Background playback: full media session integration
- Queue management: drag to reorder
- Shuffle & repeat: all playback modes
- Gapless playback: seamless track transitions
- Custom transitions: configurable crossfade between tracks
- ReplayGain support: automatic volume normalization

### 📚 Library Management

- Broad format support: MP3, FLAC, AAC, OGG, WAV, ALAC, M4A, DSD/DFF and more
- Browse by song, album, artist, genre, folder or playlist
- Smart artist parsing: configurable multi-artist separators
- Album artist grouping: proper album organization
- Folder filtering: choose which directories to scan
- Tag editor: edit metadata with TagLib (MP3, FLAC, M4A)

### ☁️ Cloud Services

- QQ Music: stream from your QQ Music account
- NetEase Cloud Music: browse playlists, with message center and Listen Together
- Bilibili: play Bilibili audio content
- Jellyfin: full Jellyfin media server integration
- Navidrome / Subsonic: Subsonic-compatible server support
- Telegram: browse and play music from Telegram channels
- Cloud proxy streaming: proxy-based adaptive-quality streams

### 🤖 AI Integration

- AI playlist generation: Gemini, Deepseek, OpenAI, Groq, OpenRouter
- AI metadata generation: automatic metadata and descriptions
- AI playlist evaluator: quality scoring for generated playlists
- Smart daily mix: personalized recommendations from your listening habits
- **AI bridge (MCP)**: built-in MCP server — compatible clients can pair and directly control playback, search the library and create playlists
- Usage analytics: track AI token usage and costs

### 🔍 Discovery & Organization

- Full-text search: cross-library search with smart filters
- Daily mix: AI-personalized playlists
- Playlists: drag & drop management
- Statistics: detailed listening history and habits
- Smart playlists: rule-based auto generation
- Focus mode: Pomodoro (study / break cycles)

### 🎤 Lyrics

- Synced lyrics: LRC from LRCLIB API and Kugou
- Word-by-word lyrics: AMLL karaoke-style highlighting
- RTL lyrics: automatic layout direction and alignment for Arabic / Hebrew and more
- Lyrics editing: modify or add lyrics
- Scrolling display: current line highlighted with the music
- Translation support: synced translations and romanization
- Lyrics font: independent font settings, multiple downloadable CJK fonts
- Lyrics sharing: generate beautiful share cards
- Local lyrics: load lyrics from local storage

### 🎚️ Equalizer

- System equalizer: full parametric EQ control
- AutoEq integration: automatic headphone-based presets
- Custom presets: save and load EQ configurations
- Genre presets: rock, pop, classical and more

### 🖼️ Artist Artwork

- Deezer integration: automatic artist images
- Smart caching: memory (LRU) + database cache for offline use
- Fallback icons: elegant placeholders when artwork is unavailable
- Color extraction: dynamic colors from album art

### 📲 Multi-device

- Chromecast: cast to TVs and smart speakers
- Android Auto: in-car playback
- Widgets: Glance home screen widgets (4x1, 4x2)
- Bluetooth: Bluetooth audio devices
- Web remote control: control playback from a browser via HTTP

### 💾 Backup & Restore

- Full backup: export all settings, playlists and library data
- Selective restore: choose which modules to restore
- Cloud backup: back up to various cloud storage services

### ⚙️ Advanced

- Plugin center: QuickJS sandboxed plugins (store / local import / permission isolation / built-in UI panels)
- Song recognition: identify songs by recording ambient audio
- Hearing protection: headphone volume monitoring and limit reminders
- Batch operations: queue, multi-select, share and favorite in bulk
- Format converter: audio transcoding (e.g. DFF→WAV)
- Queue sharing: share playlists via M3U files
- Gesture controls: gesture-based navigation
- Developer options: advanced settings for power users

---

## 📱 Requirements

| Item | Requirement |
|------|-------------|
| **OS version** | Android 9 (API 28) or higher |
| **RAM** | 6GB or more recommended for the smoothest experience |
| **USB OTG** | For external USB DAC support |

---

## ⬇️ Download

<div align="center">

### Get the latest builds from GitHub Releases

| Variant | Description | Splits |
|:-------:|-------------|--------|
| **Full** | Complete build with Telegram streaming | arm64-v8a / armeabi-v7a / x86_64 |
| **Lite** | Minimal build without Telegram & GMS (smaller) | arm64-v8a / armeabi-v7a / x86_64 |

Every split ships with a `.sha256` checksum file; most phones should pick **arm64-v8a**.

<br>

[![GitHub Releases](https://img.shields.io/badge/Download-GitHub%20Releases-181717?style=for-the-badge&logo=github)](https://github.com/r3n011/XiangsuPlayerHQ/releases)
[![Official website](https://img.shields.io/badge/Website-pixel.grammx.asia-4CAF50?style=for-the-badge&logo=googlechrome&logoColor=white)](https://pixel.grammx.asia)

<sub>Builds for other platforms (Windows / TV, etc.) are available on the official website</sub>

</div>

---

## 📚 Documentation

- 📋 [Changelog](CHANGELOG.md)
- ⚠️ [Platform API & stability risk notes](docs/reverse-api-risk-notes.md)
- 📦 [Third-party open-source notices](THIRD_PARTY_NOTICES.md)
- 🖥️ [Desktop P1R2 architecture decisions](docs/desktop-p1r2-decisions.md)

---

## ❤️ Donate

Every donation helps us build more fun and interesting features.

**[Click here to donate](https://pixel.grammx.asia/donate.html)**

---

## 📄 License

Original project [license](LICENSE) · This project is licensed under **GPL_V3**

---

## ☎️ Contact

QQ Group: [907852088](https://qm.qq.com/q/31XTTiZqg0)

<p align="center">
  <b>Made with ❤️ by the Pixel Player team</b>
  <br><br>
  <a href="https://github.com/r3n011">r3n_011</a> ·
  <a href="https://github.com/yzrbz">yzrbz</a> ·
  <a href="https://github.com/hzx0831">HZX0831</a>
</p>
