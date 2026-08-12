# 常见问题

## Monica Steam 是 Steam 官方客户端吗？

不是。

Monica Steam 是非官方第三方 Android 客户端，与 Valve Corporation 或 Steam 没有隶属、授权或赞助关系。

## 当前稳定吗？

当前仍是公开测试版。

界面、接口适配和功能行为都可能继续调整；Steam 网页与移动接口变化后，也可能出现临时失效。

## 会不会因为使用 Monica Steam 被 Steam 风控？

项目当前明确提示存在 Steam 风控导致账号收到红信或限制的风险。

Steam 风控规则并不完全公开，因此文档不会承诺“绝对安全”。如果无法接受风险，请暂缓使用。

## DoH 会改变我的 Steam 登录公网 IP 吗？

正常情况下不会。

DoH 只负责把域名解析成目标服务器 IP：

```text
steamcommunity.com
        ↓
DNS / DoH
        ↓
Steam 目标 IP
```

真正 Steam 流量仍然是：

```text
你的公网出口 IP → Steam / CDN
```

这和 VPN / HTTP 代理把业务流量转发到第三方出口是不同的机制。

## 静态 Hosts 会改变公网 IP 吗？

不会。

静态 Hosts 固定的是**目标 IP**，不是用户自己的**源公网 IP**。

## 动态 DNS 和静态 Hosts 哪个更适合日常使用？

一般推荐动态 DNS / DoH。

它不需要先扫描，也不会长期固定 Steam / CDN IP，缓存过期以后会自动重新解析。

静态 Hosts 更适合：

- 某些域名长期解析异常；
- 已经扫描到稳定目标节点；
- 希望显式固定某个经过 HTTPS 验证的 IP。

## 为什么静态 Hosts 过一段时间可能需要重扫？

因为它是固定映射。

Steam CDN、运营商线路、Wi‑Fi / 移动网络或跨网路由变化后，之前最优的 IP 可能不再最优，甚至失效。

## 我添加的自定义 DNS / DoH 能同时给静态扫描用吗？

可以。

当前动态 DNS / DoH 与静态 Hosts 共用同一套解析来源配置。

自定义 DNS / DoH 保存后进入统一列表；只要处于启用状态，就可以同时参与动态解析和静态 Hosts 扫描。

## “全部测速”是不是会自动把最快 DNS 设成默认？

测速主要用于比较可用性和 DNS 响应延迟。

DNS 返回得最快，并不保证它返回的 Steam CDN 节点对你实际访问最快，因此不建议只根据一次测速结果频繁切换。

## 动态 DNS 会不会固定某个 IP？

不会长期固定。

动态解析只做短期缓存；缓存过期后会再次解析，使 Steam / CDN 地址能够自然变化。

## 把所有 DNS / DoH 都关了会不会断网？

当前运行时会回到 Android 正常网络路径，避免因为误操作导致 Monica Steam 整体断网。

## 自定义 DoH 会收到我的 Steam 密码或 Cookie 吗？

解析来源只需要处理公开 Steam 域名查询，不需要 Steam 密码、令牌、Cookie 或聊天内容。

但 DNS 服务仍然能看到你向它查询的域名，因此应使用自己信任的解析服务。

## Cloudflare Worker DoH 会代理 Steam 流量吗？

不会。

Worker 只处理 DNS：

```text
Monica → Cloudflare Worker → Google DNS
```

Steam 业务流量仍然是：

```text
Monica → Steam / CDN
```

## 为什么 Cloudflare Worker 教程建议加 TOKEN？

因为公开的 `/dns-query` 很容易被识别并滥用。

随机 TOKEN 不能提供完整鉴权，但能降低被随手扫描到的概率，从而减少别人消耗你的 Workers 免费额度。

## WebView 会走动态 DNS / Hosts 吗？

目前不能保证。

当前网络优化主要作用于 Monica Steam 的原生 HTTP 网络栈。WebView 没有完整接入相同链路，所以不会在底层实现不存在时提供假的 Gateway / ECH 开关。

## 游戏库能离线看吗？

可以查看最近同步的缓存数据，但离线缓存不代表 Steam 当前实时状态。

## 商店最终付款是不是由 Monica Steam 完成？

不是。

Monica Steam 提供原生浏览、愿望单和购物车入口等能力，但最终购买与结算仍由 Steam 官方流程完成。

## 为什么聊天文字正常，但图片不加载？

图片通常来自 Steam UGC / 静态资源 CDN，因此可能是图片域名、CDN 或网络路径单独异常，而不是聊天接口整体失效。

## 可以和 Monica Pass 一起安装吗？

可以。

Monica Steam 使用独立包名和 Android 应用沙箱，不会自动读取 Monica Pass 的密码库。

## Monica Steam 能管理 Monica Pass 的 Bitwarden / KeePass 数据吗？

不能。

Monica Steam 专注 Steam 账号、令牌和相关数据，不包含密码管理器的自动填充、Bitwarden 或 KeePass 工作流。

## 出问题时应该提供什么？

建议提供：

- Android 版本；
- Monica Steam 版本；
- 复现步骤；
- 是否只影响某个 Steam 账号；
- 是否启用了网络优化；
- 经脱敏的相关日志。

不要公开密码、Steam Guard 密钥、Cookie、完整 `maFile`、WebDAV 密码或私人 DoH TOKEN。