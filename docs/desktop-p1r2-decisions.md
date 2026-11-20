# 桌面端 P1R2 架构选型决议（2026-11-20）

> 对应立项截图「请您确认的三项关键选型决策」。经核实确认后，按推荐项（各方案 A）拍板，
> 落入 P1R2 排板执行。本文档只记录决议与理由，实施细节见各自任务。

## 决议 1：F-028 桌面 JS 运行时 与 F-025 可选 JS 签名执行引擎

**选定 A：GraalJS 独立 JVM 运行时**（`org.graalvm.polyglot:polyglot` + `js-community`）

- 零 Windows 原生 C++/DLL 分发风险；
- 完整 ES2022 + Promise + 沙盒权限隔离；
- 已知代价（接受）：发包体积增加约 35–45 MB；首次初始化预热约 0.5–1.5 s
  （实施时在启动后台线程预热，避免阻塞首帧）。

社区 QuickJS JNI 封装（方案 B）作为备选保留：若预热开销或体积在实测中不可接受再重启评估。

## 决议 2：F-024 / F-025 / F-026 在线平台登录策略（桌面侧）

**选定 A：原生 PC/TV 扫码为主 + WebView 验证码 + Cookie 导入兜底**

- 网易云、B 站：优先原生二维码轮询（与手机端同一套链路）；
- 少数触发风控/滑块（Geetest）或仅支持网页扫码的场景：引导默认浏览器或粘贴 Cookie，
  不强行在应用内嵌浏览器；
- 已知代价（接受）：极少数场景需人工粘贴 Cookie；QQ 搜歌/常规播放若依赖无法走
  WebView 的 `zzeSign` + `getKeyUrlUnsigned` 等 Kotlin 链路，失败时走换源兜底；
- 方案 B（内嵌 JCEF/KCEF Chromium）作为备选：仅当 P2 启用 F-044 后再按需评估，
  代价为 120–180 MB 安装增量与 200+ MB 运行内存。

## 决议 3：桌面本地敏感凭据保护方式（KI-007 与网易/QQ/B 站/AI Key）

**选定 A：Windows DPAPI 加密保护，`.pxpl` 导出默认脱敏**

- 已核实现状：桌面端网易云 Cookie 已用 `ProtectedData`（DPAPI CurrentUser）加密
  （`NeteaseAccountService.cs:62/79`），本决议将其推广为全平台统一标准：
  网易 / QQ / B 站 Cookie 与 AI Key 全部 DPAPI 加密落盘；
- 零第三方依赖，仅当前 Windows 登录会话可解；
- 已知代价（接受）：直接拷贝配置目录到另一台电脑/用户后凭据失效，需重新扫码
  （安全性更优；便携迁移需求由 `.pxpl` 导出/导入承担，导出默认脱敏）。

## 关联问题与前置修复（已随本次一并处理）

- KI-047 ZIP 条目名编码：手机端与桌面端均已增加 GB18030 回退
  （`ZipNameCharsets.kt` / `LxSourceMarket.cs::OpenZipWithFallback`），
  V261115/V261117 等老打包器 GBK 文件名 zip 可正常解析安装；
- KI-010 开源许可页资源缺失、KI-011 占位 UI、KI-012 统计 SDK 与隐私声明冲突：
  见 CHANGELOG 与本次提交。
