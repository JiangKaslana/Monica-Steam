# 风险与安全边界

这页不是为了吓人，而是把 Monica Steam 当前的边界写清楚。公开测试版可以很好用，但涉及 Steam Guard、移动确认和账号会话时，任何第三方客户端都应该把风险提示放在显眼位置。

## 1. Steam 风控风险

::: danger 重要
Monica Steam 仓库当前明确提示：**可能因为 Steam 风控问题导致账号收到红信或限制。** 如果介意，请先不要使用。
:::

Steam 的风控规则并不完全公开，也会随时间变化，因此文档不会承诺“绝对不会触发风控”。

建议：

- 不要在主账号上无备份地做首次实验；
- 不要频繁切换异常网络、设备环境和账号状态；
- 涉及购买、礼物、交易、账号安全与最终确认时，以 Steam 官方结果为准；
- Steam 接口发生变化时，先查看项目更新和已知问题。

## 2. 令牌与 maFile 风险

`maFile`、Steam Guard 密钥和账号会话都属于高价值敏感数据。

在迁移、导入、恢复、删除验证器或开启远程备份前，至少保留一份独立备份。

推荐形成这样的备份习惯：

```text
原始 maFile / 密钥
       ↓
离线备份一份
       ↓
再导入 Monica Steam
       ↓
确认令牌与账号状态正常
       ↓
最后再清理旧环境
```

测试版不应作为唯一备份。

## 3. 应用数据边界

Monica Steam 使用独立应用 ID：

```text
takagi.ru.monica.steamapp
```

它与 Monica Android / Monica Pass 可以并排安装，Android 应用沙箱会隔离数据库和偏好设置。

Monica Steam 不会自动共享 Monica Pass 密码库，也不能直接管理 Monica Pass 的 Bitwarden、KeePass 或自动填充数据。

## 4. 本地保护

当前实现包含：

- 主密码；
- Android 生物识别；
- 本地加密存储；
- 可选 MDBX 存储；
- Steam 专用 WebDAV / ZIP / `maFile` 备份流程。

这些机制用于降低设备本地数据暴露风险，但无法替代用户对备份文件、系统账户和设备本身的保护。

## 5. 网络优化不会做什么

Monica Steam 的网络优化目前属于**应用内网络层**，不是系统级代理工具。

它不会主动：

- 创建 Android VPN；
- 修改系统代理；
- 修改 Android 系统 DNS；
- 修改 Android 系统 Hosts；
- 为其他应用代理流量；
- 把 Steam 业务流量转发到第三方代理出口。

动态 DNS / DoH 改变的是域名解析结果，也就是“连接哪个目标 IP”；静态 Hosts 则显式固定某些目标 IP。两者都不是 VPN。

## 6. DNS / DoH 与出口 IP

正常情况下：

```text
DNS：
Monica Steam → DNS / DoH → 得到 Steam 服务器 IP

业务流量：
你的公网出口 IP → Steam / CDN
```

因此更换 DNS / DoH 并不会像 VPN / HTTP 代理那样，把 Steam 登录来源改成第三方代理出口。

但这不等于“使用第三方 DNS 对 Steam 风控绝对零影响”。文档只能说明网络机制本身，不替 Valve 对账号风控作保证。

## 7. WebView 边界

当前网络优化主要作用于 Monica Steam 的原生 HTTP 网络栈。

WebView 流量目前不能保证全部经过同一套动态解析/Hosts 链路，因此设置页不会在底层能力不存在时伪装提供 ECH、Gateway、WebView Gateway 等开关。

## 8. 第三方服务

应用可能访问 Steam 网页、移动接口、WebDAV 等外部服务。服务端接口、账号权限、地区策略、TLS 证书、CDN 和风控变化都可能导致功能临时失效。

遇到涉及账号状态的矛盾结果时，优先信任 Steam 官方页面或官方客户端。
