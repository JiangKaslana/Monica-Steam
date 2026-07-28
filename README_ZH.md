# Monica Steam

**简体中文** | [English](README.md)

[![发行版](https://img.shields.io/github/v/release/JoyinJoester/Monica-Steam?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/releases)
[![下载量](https://img.shields.io/github/downloads/JoyinJoester/Monica-Steam/total?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/releases)
[![最近提交](https://img.shields.io/github/last-commit/JoyinJoester/Monica-Steam?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/commits/main)
[![许可证：GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

> **当前状态：公开测试版。** Monica Steam 仍在持续开发中，不是正式版，也不是 Steam 官方客户端。

Monica Steam 是一款专注 Steam 的独立 Android 客户端，源自 Monica Android 中的 Steam 功能。它把 Steam 令牌、账号管理、移动确认、游戏库、商城、好友、聊天、通知和 Steam 账号备份集中到一个应用中。

## Monica Steam 与 Monica Pass 的关系

[Monica Pass](https://github.com/Monica-Pass/Monica) 是 Monica 生态及本地优先密码库项目；[Monica Android](https://github.com/Monica-Pass/Monica/tree/main/Monica%20for%20Android) 是其中的完整 Android 客户端，原本同时包含密码管理功能和 Steam 页面。

Monica Steam 从 Monica Android 的 Steam 功能中独立出来，作为单独产品维护：

| 项目 | 定位 | 链接 |
| --- | --- | --- |
| Monica Pass | 本地优先密码库与 Monica 生态 | [GitHub 仓库](https://github.com/Monica-Pass/Monica) · [官方网站](https://monica-pass.github.io/Monica/) |
| Monica Android | Monica 完整 Android 客户端，也是 Steam 模块的来源 | [Android 项目](https://github.com/Monica-Pass/Monica/tree/main/Monica%20for%20Android) |
| Monica Steam | 独立的 Steam 专用 Android 客户端 | [本仓库](https://github.com/JoyinJoester/Monica-Steam) |

- Monica Steam 使用独立的软件包、应用沙箱、发布周期和代码仓库：`takagi.ru.monica.steamapp`。
- 它会在合适的地方复用 Monica 的 Material 3 设计、导航、安全、存储和 Steam 组件，但不会修改 Monica Android。
- 它**不包含** Monica Pass 密码库、Bitwarden、KeePass、自动填充和密码管理流程。
- Monica Steam 不是 Monica Pass 的替代品，也不能打开或管理 Monica Pass 的密码库记录。
- 本应用中的 `maFile`、Steam 账号 ZIP 备份、MDBX 和 WebDAV 仅用于 Steam 账号数据，不等同于 Monica Pass 密码库同步。

源码提取基线和项目关系见 [`SOURCE.md`](./SOURCE.md)。

## 功能概览

### Steam 账号与令牌

- Steam Guard 动态令牌和多账号管理。
- `maFile`、仅密钥、账号凭据和二维码导入。
- 登录批准、移动确认和授权设备管理。
- 移除验证器与切换 Steam 账号。
- 加密本地账号存储及可选 MDBX 存储。

### 游戏库与游戏数据

- 查看 Steam 游戏库、家庭共享、游玩时间、成就和拥有状态。
- 统计账号游戏数量、游玩时间和估算价值。
- 最近游玩时间、完成状态、游戏数/价格分布和游玩热力图筛选。
- 游戏库缓存：断网时显示上次同步的数据，有网时再同步。

### Steam 商城

- 商城浏览、搜索、多区价格、汇率换算和账号地区筛选。
- 查看购买选项、版本、DLC、捆绑包、配置要求、截图和玩家评价。
- 原生购物车与愿望单，最终结算仍由 Steam 官方流程完成。
- 在 Steam 提供兼容数据时显示活动内容和点数商城。

### 好友、聊天与通知

- 好友列表、好友详情、私聊和群聊统一会话列表及群组管理。
- 在支持的消息类型中使用文字、Steam 表情、贴纸、图片、复制、回应、举报和聊天记录搜索。
- 独立 Steam 通知页、未读状态、礼物/确认相关操作和通知详情。

### 外观与备份

- Monica 全部配色方案，包括 Monica Plus 配色。
- Material 3 Expressive 布局、悬浮 Dock 导航、Dock 排序和界面缩放。
- Steam 专用 `maFile` WebDAV 备份/恢复，以及 ZIP 导入导出。
- 主密码与生物识别保护、日志查看/清除/分享。
- 账号和最近游玩的桌面小组件。

## 数据与安全边界

- 应用 ID 为 `takagi.ru.monica.steamapp`，数据库和偏好设置由 Android 应用沙箱隔离。
- Monica Steam 与 Monica Android 可以同时安装，但不会自动共享数据。
- 导入、迁移或启用远程备份前，请先备份现有 `maFile` 文件。
- Steam 网页和移动接口可能随时变化；商城价格、礼物、通知和聊天操作可能受账号地区、登录状态或接口可用性影响。
- 测试版不应作为 Steam 令牌或账号数据的唯一备份。

## 开发与构建

### 环境要求

- Android Studio 稳定版。
- JDK 17 或更高版本。
- Android SDK 35；当前应用配置支持 Android 8.0 及以上设备。

### 常用命令

只运行 JVM 测试，不生成 APK 或 AAB：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

在明确需要安装包时构建开发版或发布版：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release 签名通过 `keystore.properties` 或 `MONICA_STEAM_RELEASE_*` 环境变量从外部提供。请勿提交签名文件和凭据。

## 仓库导航

- [`README.md`](./README.md) — English project overview。
- [`RELEASE_NOTES.md`](./RELEASE_NOTES.md) — 首个公开测试版本与已知限制。
- [`SOURCE.md`](./SOURCE.md) — 提取基线及与 Monica Android 的关系。
- [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) — 第三方组件和许可证声明。

## 第三方服务与官方关系声明

Monica Steam 是非官方第三方客户端，与 Valve Corporation 没有隶属、授权或赞助关系。Steam、Steam Guard 及相关商标归其各自权利人所有。

部分功能依赖 Steam 网页或非公开移动接口。涉及购买、礼物、账号安全或最终确认时，应以 Steam 官方页面和官方结果为准。

## 反馈与支持

问题反馈和功能建议请提交到 [Monica Steam Issue 区](https://github.com/JoyinJoester/Monica-Steam/issues)。如果 Monica 项目对你有帮助，可以通过 [爱发电](https://afdian.com/a/JoyinJoester) 或 [Ko-fi](https://ko-fi.com/joyinjoester) 支持持续开发。

## 许可证

Copyright (c) 2025 JoyinJoester。

Monica Steam 采用 [GNU General Public License v3.0](LICENSE) 发布。其他第三方组件的版权和许可证见 [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md)。
