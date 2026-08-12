# 快速开始

Monica Steam 是一款专注 Steam 的独立 Android 客户端，当前仍处于公开测试阶段。它把 Steam Guard、移动确认、游戏库、商城、好友聊天、通知和 Steam 账号备份集中在一个应用中。

<div class="badge-row">
  <span class="doc-badge">Android 8.0+</span>
  <span class="doc-badge">公开测试版</span>
  <span class="doc-badge">非 Steam 官方客户端</span>
  <span class="doc-badge">GPL-3.0</span>
</div>

## 安装

1. 前往 [Monica Steam Releases](https://github.com/JoyinJoester/Monica-Steam/releases) 下载与你设备架构匹配的 APK。
2. 在 Android 8.0 或更高版本设备上安装。
3. 首次启动后按需要导入已有 Steam 账号资料，或完成新的登录与会话初始化。
4. 在进行迁移、导入或远程备份前，先保留一份原始 `maFile` / 令牌资料副本。

::: danger 使用前先看
Monica Steam 当前不是稳定版，也不是 Valve / Steam 官方客户端。仓库明确提示存在 Steam 风控导致账号收到红信或限制的风险；如果无法接受该风险，请暂缓使用。
:::

## 可以导入什么

目前账号与令牌相关能力包括：

- `maFile` 导入；
- 仅密钥方式导入；
- 账号凭据与二维码相关流程；
- Steam Guard 动态令牌；
- 登录批准；
- 移动确认；
- 授权设备管理；
- 多账号切换；
- 移除验证器相关流程。

具体可用性仍取决于 Steam 当前接口和账号状态。

## 第一次使用建议

推荐按下面顺序完成初始化：

```text
备份现有 maFile / 令牌资料
          ↓
安装 Monica Steam
          ↓
导入或登录 Steam 账号
          ↓
确认 Steam Guard 能正常生成
          ↓
确认移动确认 / 登录批准状态
          ↓
再开始使用商店、聊天、网络优化等功能
```

不要在没有其他备份的情况下，把测试版应用作为唯一令牌保存位置。

## 页面能力概览

### Steam Guard

用于查看动态令牌、管理多个 Steam 账号，以及处理登录批准和移动确认。

### 游戏库

可查看拥有游戏、家庭共享、游玩时长、成就、最近游玩与统计数据；支持缓存，在断网时仍可查看最近同步结果。

### Steam 商店

可浏览商店、搜索游戏、查看不同版本/DLC/捆绑包、配置要求、截图和评价，并提供愿望单和购物车入口。最终结算仍由 Steam 官方流程完成。

### 好友与聊天

包括好友列表、好友详情、私聊、群聊、图片、贴纸、回应、通知等功能。语音通话仍属于实验性能力，实际体验会受设备与网络环境影响。

### 备份与安全

提供主密码、生物识别、本地加密存储，以及 Steam 专用 `maFile` WebDAV 与 ZIP 导入导出。

### 网络优化

网络优化分为两套机制：

- **动态 DNS / DoH**：无需扫描，按需解析并短期缓存；
- **静态 Hosts**：扫描候选 IP、验证后固定到应用内 Hosts。

两者共享同一套 DNS / DoH 解析来源。详情见 [网络优化说明](../network/overview)。

## Monica Steam 与 Monica Pass

Monica Steam 从 Monica Android 的 Steam 功能中独立出来，拥有独立包名、应用沙箱、发布周期与仓库。

它不会自动读取或管理 Monica Pass 密码库，也不包含 Bitwarden、KeePass、系统自动填充等密码管理流程。

如果你需要的是本地优先密码管理器，请查看 [Monica Pass](https://monica-pass.github.io/Monica/)。

## 下一步

- [账号、Steam Guard 与确认](./account-guard)
- [游戏库与商店](./library-store)
- [备份与本地安全](./backup-security)
- [风险与安全边界](./risk-and-safety)
- [网络优化](../network/overview)
