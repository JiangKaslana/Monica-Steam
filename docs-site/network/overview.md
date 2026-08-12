# 网络优化：动态 DNS / DoH 与静态 Hosts

Monica Steam 当前把 Steam 网络优化明确拆成两套机制：**动态 DNS / DoH** 与 **静态 Hosts 扫描加速**。

它们共享同一套解析来源配置，但运行方式完全不同。

<div class="badge-row">
  <span class="doc-badge">应用内网络层</span>
  <span class="doc-badge">不创建 VPN</span>
  <span class="doc-badge">动态解析</span>
  <span class="doc-badge">静态 Hosts</span>
  <span class="doc-badge">支持自定义 DoH</span>
</div>

## 为什么要区分两套机制

Steam 商店、社区、API、聊天资源或 CDN 在部分网络环境下变慢，并不一定都是“链路彻底不可达”。实际问题可能发生在：

- DNS 解析失败；
- DNS 响应异常或被污染；
- 运营商 DNS 返回不理想的 CDN 节点；
- DNS 超时；
- 某些候选 IP 能解析出来，但 TLS / SNI / 证书验证失败；
- 原来可用的固定 IP 在网络变化后失效。

如果问题主要发生在解析层，那么修复 DNS 就可能恢复直连；如果需要主动挑选并固定一个已经验证的目标节点，则静态 Hosts 更合适。

## 一张图看懂

```text
                     统一解析来源
                         │
          ┌──────────────┴──────────────┐
          │                             │
          ↓                             ↓
   动态 DNS / DoH                静态 Hosts 扫描
          │                             │
   按请求实时解析                 收集候选目标 IP
          │                             │
      短期缓存                 HTTPS / SNI / 证书验证
          │                             │
   缓存过期重新解析                   选择节点
          │                             │
      直连 Steam                  固定应用内 Hosts
```

核心原则是：

> **解析来源统一管理，解析策略彼此独立。**

---

## 动态 DNS / DoH

### 适合什么场景

动态模式适合日常长期使用。

它不要求先执行网络扫描，也不会永久固定某一个 Steam / CDN IP。

工作流程：

```text
Steam 原生网络请求
        ↓
检查是否属于优化范围
        ↓
查询短期缓存
        ↓
缓存有效 ─────────→ 直接使用
        ↓
缓存不存在 / 已过期
        ↓
当前启用的 DNS / DoH 参与解析
        ↓
获得公开有效地址
        ↓
短期缓存
        ↓
连接 Steam
```

缓存到期以后会重新解析，因此能够适应 Steam / CDN 地址变化。

### 缓存与回退

动态解析包含：

- 短期 DNS 缓存；
- 过期缓存回退；
- 手动清除缓存；
- 强制重新解析；
- 同一域名并发请求合并；
- 受控并发，避免解析任务无限扩张。

如果用户把所有解析来源都关掉，运行时会回到 Android 正常网络路径，而不是直接把 Steam 页面整体断网。

### 多解析源

当前解析来源统一列表可以包含：

- Android 系统 DNS；
- DNSPod DoH；
- AliDNS DoH；
- Cloudflare DoH；
- Google Public DNS DoH；
- Quad9 ECS；
- 用户自定义 UDP DNS；
- 用户自定义 HTTPS DoH。

自定义源加入后会直接进入同一列表，可以单独启停、测速和删除。

### “全部测速”与实际动态解析不是一回事

测速可以比较所有可见解析源是否可用、DNS 响应大致有多快；真正参与动态解析的是当前**已启用**的来源。

并且：

> DNS 响应更快，不等于它返回的 Steam CDN 节点实际访问一定更快。

因此不要只看一两毫秒差距就频繁切换解析源。

---

## 静态 Hosts 扫描

静态 Hosts 是另一套思路。

它不是“查询一次 DNS 就直接写 Hosts”，而是先收集候选地址，再验证真实 HTTPS 可用性。

基本流程：

```text
启用的 DNS / DoH 来源
          ↓
解析 Steam 目标域名
          ↓
收集多个候选公开 IP
          ↓
重复 HTTPS 连接测试
          ↓
检查 SNI / TLS / 证书
          ↓
比较成功率与延迟表现
          ↓
应用到 Monica Steam 应用内 Hosts
```

当前自动优选会对核心域名执行高强度重复验证，并对首轮未覆盖域名进行重新解析和更长超时复测。仍无法验证的域名不会伪装成“已全部优化”，而会继续使用正常 DNS 回退。

### 为什么需要重复扫描

静态 Hosts 最大的优点和缺点都来自同一个特点：**固定。**

例如：

```text
steamcommunity.com → 23.x.x.x
```

当运营商、Wi‑Fi / 移动网络、跨网路由或 Steam CDN 后端发生变化时，之前最好的固定节点可能不再合适。

因此遇到这些情况建议重新扫描：

- 换了网络；
- 换了运营商；
- 原来很快、后来明显变慢；
- 固定节点无法建立 HTTPS；
- Steam / CDN 大规模调整后。

---

## 两套机制共享自定义 DNS / DoH

这是当前设计中最方便的一点之一。

假设你在解析来源页面保存：

```text
Cloudflare DoH
Google DoH
自建 DoH A
自定义 DNS B
```

只需要保存一次。

这些来源既可以服务：

```text
动态 DNS / DoH
```

也可以服务：

```text
静态 Hosts 扫描
```

前提是对应来源处于启用状态。

因此不会出现“动态页填一遍，Hosts 页再填一遍”的重复配置。

---

## 两者同时开启时的优先级

如果某个 hostname 已经存在用户主动应用的静态 Hosts：

```text
该 hostname → 静态 Hosts 优先
```

其他没有静态映射的 Steam 域名：

```text
其他 hostname → 动态 DNS / DoH
```

例如：

```text
steamcommunity.com
      ↓
已有静态 Hosts
      ↓
使用固定 IP

api.steampowered.com
      ↓
没有静态 Hosts
      ↓
动态 DNS / DoH
```

所以不需要为了使用某个固定节点就完全关闭动态解析。

---

## 安全过滤

自动与手动 Hosts 会拒绝明显不应该被当作公网 Steam 节点的地址，例如：

- 私网地址；
- 保留地址；
- 文档网段；
- 常见 Fake-IP。

自定义 UDP DNS 也会校验 DNS 事务 ID、查询域名、记录类型和公网地址，避免把明显异常的响应直接应用。

自定义 DoH 只接受标准 HTTPS 端点；解析请求仅包含公开 Steam 域名，不会把 Steam 账号密码、令牌、Cookie 或聊天内容发送给 DNS 服务。

---

## IPv4 / IPv6

系统 DNS 与 DoH 可以提供 IPv4 和 IPv6 结果，并可配置 IPv6 优先。

目前自定义传统 UDP DNS 的实现主要以 IPv4 A 记录为主，因此它与系统 DNS / DoH 的能力并不完全相同。

---

## 覆盖哪些 Steam 域名

网络优化目标不仅包括商店和社区，还覆盖 Monica Steam 实际使用的主要服务类别：

- Steam Store；
- Steam Community；
- Steam Web API；
- 登录与帮助服务；
- Steam Chat；
- 媒体、头像与 UGC；
- Steam 静态资源；
- 常见 Steam CDN 域名族。

不同域名会采用不同扫描预算，核心业务域名会做更重的验证，CDN 类型域名则避免无限拉长扫描时间。

---

## 会改变 Steam 登录出口 IP 吗？

正常情况下不会。

动态 DNS / DoH 的作用是：

```text
域名 → 目标服务器 IP
```

而不是：

```text
用户 → 第三方代理出口 → Steam
```

真正访问 Steam 时，网络仍然是：

```text
你的公网出口 IP → Steam / CDN
```

静态 Hosts 同样只是固定目标地址。

因此这套机制不是 VPN，也不是代理转发。

::: warning
这里只能说明网络机制。Steam 的账号风控规则由 Valve 决定，任何第三方客户端都不应承诺“绝对不会触发风控”。
:::

---

## 当前作用范围

网络优化主要作用于 Monica Steam 的**原生 HTTP 网络栈**。

它不会修改：

- Android 系统 DNS；
- Android 系统 Hosts；
- 系统 VPN；
- 系统代理；
- 其他应用的网络行为。

WebView 流量目前也不能保证完全进入同一解析链路，因此不会提供没有底层实现的假 ECH / Gateway / WebView Gateway 开关。

## 推荐配置

### 普通用户

```text
动态 DNS / DoH：开启
静态 Hosts：关闭
```

选择一两个稳定解析源长期使用即可。

### 某些域名持续异常

先尝试动态 DoH；如果确实需要固定目标节点，再执行静态 Hosts 扫描。

### 喜欢手动优化

可以同时使用动态解析与少量静态 Hosts：只固定经过验证且确实有价值的域名，其余继续动态解析。

## 自建 DoH

如果希望给 Monica Steam 添加一个自己的 Google Public DNS DoH 中继，可以查看：

**[Cloudflare Worker 反代 Google DoH](./cloudflare-google-doh)**
