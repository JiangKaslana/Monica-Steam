# 架构与项目边界

Monica Steam 源自 Monica Android 中的 Steam 功能，但现在作为独立 Android 应用维护。

## 来源

源码快照记录：

- Source project：`Monica for Android`；
- Baseline commit：`7a5dd96228f11e0adcb75692b0f18c83350471f7`；
- Extraction date：`2026-07-18`。

在该快照之后，Monica Steam 逐步加入独立应用身份、Launcher、Manifest、文档、Steam 专用页面和网络优化等改动。

## 应用身份

Monica Steam 使用独立应用 ID：

```text
takagi.ru.monica.steamapp
```

这意味着它拥有独立：

- Android 应用沙箱；
- 数据库；
- 偏好设置；
- 发布周期；
- 安装包；
- 仓库维护节奏。

## 与 Monica Pass 的边界

Monica Steam 可以复用 Monica 系列的 Material 3 设计、导航、安全、存储和部分 Steam 组件，但不会把密码库能力带入本应用。

它不包含：

- Bitwarden 密码库；
- KeePass 密码库；
- Android 自动填充；
- 通用密码记录管理。

本应用中的 `maFile`、Steam ZIP、MDBX 与 WebDAV 仅服务 Steam 账号数据。

## 代码分层

仓库 README 当前给出的主要分层包括：

### `takagi/ru/monica/steam`

Steam 业务层：

- 账号；
- Steam Guard；
- 移动确认；
- 游戏库；
- 商店；
- 好友；
- 聊天；
- 通知；
- Steam Web；
- 网络优化等。

### `takagi/ru/monica/ui`

Compose 页面、导航和共享设置 UI。

### `takagi/ru/monica/data` / `repository` / `security`

本地数据访问、Repository 抽象与安全相关能力。

### `takagi/ru/monica/webdav` / `workers`

远程备份和后台任务。

## UI 技术栈

当前界面主要基于：

- Jetpack Compose；
- Material 3；
- Material 3 Expressive；
- Coroutines / Flow；
- WorkManager；
- Android BiometricPrompt。

## 网络栈

原生 Steam 请求主要通过 OkHttp 等网络组件完成。

网络优化在此基础上增加应用内 DNS / Hosts 层：

```text
业务请求
   ↓
Steam HTTP Client
   ↓
应用内 Hosts（存在时优先）
   ↓
动态 DNS / DoH
   ↓
系统网络
```

这套路径不等于 Android 系统 VPN，也不会全局修改设备网络。

## WebView

Steam 内置浏览器已经拆分为独立 `steam/web` 模块，让商店、礼物、个人游戏数据、截图等网页入口复用同一浏览器内核。

WebView 具有自己的生命周期、权限和网络行为，因此不能简单假定原生 OkHttp 的 DNS 规则会自动覆盖 WebView。

## 网络优化的设计原则

当前网络优化遵循几条边界：

1. **动态解析与静态 Hosts 分离**；
2. **解析来源统一管理**；
3. **静态节点必须验证，而不是盲写 Hosts**；
4. **解析失败要能回退，而不是让应用整体断网**；
5. **不实现的能力不做假开关**；
6. **仅处理公开 Steam 域名，不向 DNS 服务发送账号敏感数据**。

## 文档站架构

文档站使用 VitePress + Teek 主题，放在 `docs-site/` 中，与 Android 工程隔离。

这样 Android 构建不会因为文档依赖 Node.js，而文档也可以独立部署到 GitHub Pages 或 Cloudflare Pages。
