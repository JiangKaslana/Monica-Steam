# Cloudflare Worker 反代 Google DoH

这是一套**可选、自建、与 Monica Steam 项目本体解耦**的 DoH 中继方案。

目标很简单：利用 Cloudflare Workers 的免费额度，把标准 RFC 8484 DoH 请求透明转发到 Google Public DNS。

```text
Monica Steam
     ↓ DoH
Cloudflare Worker
     ↓ DoH
dns.google
```

Worker 不解析、不修改 DNS 数据包，也不代理 Steam 业务流量。

::: danger 先看风险
Cloudflare Workers 免费额度不是无限资源。公开暴露一个无保护的 DoH 地址，可能被扫描器或第三方当成公共 DNS 使用，快速消耗配额，并增加限流、异常流量或平台风控风险。

**推荐仅个人使用，并配置随机 TOKEN。**
:::

## 当前免费额度

按 Cloudflare 官方 Workers 文档，Free 计划当前包含：

- 每天 100,000 次 Worker 请求；
- 每次调用 10 ms CPU 时间；
- 每次调用最多 50 个外部 subrequest；
- 免费计划的每日请求额度在 UTC 0 点重置；
- 超出每日请求额度后，Worker 可能返回 Error 1027。

这些限制和产品政策未来可能变化，部署前建议再次查看 [Cloudflare Workers Pricing](https://developers.cloudflare.com/workers/platform/pricing/) 与 [Workers Limits](https://developers.cloudflare.com/workers/platform/limits/)。

## Google 上游

Google Public DNS 的标准 RFC 8484 DoH 端点是：

```text
https://dns.google/dns-query
```

Google 官方支持：

- GET：`?dns=<Base64Url DNS message>`；
- POST：请求体直接发送二进制 DNS message，并使用 `application/dns-message`。

详见 [Google Public DNS DoH](https://developers.google.com/speed/public-dns/docs/doh)。

## 为什么只做透明反代

不做：

- ECS 地区伪装；
- 香港 / 日本 / 新加坡强选；
- Cloudflare IP 优选；
- DNS 包拆解重组；
- JSON API 转换；
- Steam CDN 反代；
- VPN / TCP / UDP 隧道。

原因是：越少修改 DNS 请求，越容易保持与标准 DoH 客户端兼容。

最终结构保持为：

```text
客户端原始 DoH 请求
        ↓
Cloudflare Worker 原样转发
        ↓
https://dns.google/dns-query
        ↓
Google 原始 DoH 响应
        ↓
客户端
```

## 全程 Cloudflare 网页版部署

不需要 VPS、Docker、Wrangler、Node.js、本地终端或其他云服务。

进入 Cloudflare 控制台：

```text
Workers & Pages
→ Create application
→ Worker
→ Edit code
```

删除默认代码，然后粘贴下面的 Worker。

## Worker 代码

<details>
<summary>点击展开完整代码</summary>

```javascript
const UPSTREAM = "https://dns.google/dns-query";

export default {
  async fetch(request, env) {
    const incoming = new URL(request.url);

    // TOKEN 可选：
    // 未设置 TOKEN：/dns-query
    // 设置 TOKEN=abc123：/abc123/dns-query
    const token = (env.TOKEN || "").trim();

    const expectedPath = token
      ? `/${token}/dns-query`
      : "/dns-query";

    // 只允许 DoH 路径，避免变成任意 URL 反向代理。
    if (incoming.pathname !== expectedPath) {
      return new Response("Not Found", { status: 404 });
    }

    // RFC 8484 DoH 使用 GET / POST。
    if (request.method !== "GET" && request.method !== "POST") {
      return new Response("Method Not Allowed", {
        status: 405,
        headers: { Allow: "GET, POST" },
      });
    }

    const upstream = new URL(UPSTREAM);

    // GET 的 ?dns= 查询参数原样保留。
    upstream.search = incoming.search;

    // 复制原 Request：方法、Headers、POST Body 都保留。
    const proxyRequest = new Request(upstream.toString(), request);

    return fetch(proxyRequest);
  },
};
```

</details>

## 不使用 TOKEN

如果只是临时测试，可以不设置任何变量。

假设 Worker 地址是：

```text
https://my-doh.example.workers.dev
```

那么 Monica Steam 中添加：

```text
https://my-doh.example.workers.dev/dns-query
```

即可。

::: warning
无 TOKEN 的 `/dns-query` 非常容易被扫描和识别，不建议长期公开使用。
:::

## 使用 TOKEN

推荐在 Cloudflare Worker 设置中添加变量：

```text
TOKEN
```

建议类型选择 **Secret**，值使用足够长的随机字符串，例如：

```text
7fa5b91a8d2c4e67aa834fd570a38e12
```

不要直接使用文档中的示例值。

设置后 DoH 地址变成：

```text
https://my-doh.example.workers.dev/你的TOKEN/dns-query
```

而：

```text
https://my-doh.example.workers.dev/dns-query
```

会返回 404。

### TOKEN 的定位

TOKEN 主要是：

> 隐藏路径 + 简单访问门槛

它不是完整的鉴权系统。

如果完整 URL 被截图、日志、公开仓库或聊天记录泄露，拿到 URL 的人仍然可以使用。

## 自定义域名

可选。

`workers.dev` 域名可以直接用。如果你已经有托管在 Cloudflare 的域名，也可以为 Worker 绑定类似：

```text
doh.example.com
```

最终地址会更简洁：

```text
https://doh.example.com/TOKEN/dns-query
```

## 不建议 Cloudflare IP 优选

不要为了这套简单 DoH Relay 去：

- 直接访问 Cloudflare IP；
- 手动绑定所谓优选 IP；
- 强行指定某个 Cloudflare 国家节点。

对于 Worker，直接使用 Worker 域名或 Custom Domain，让 Cloudflare 自己处理入口网络调度即可。

Google Public DNS 同样通过 `dns.google` 提供正式 DoH 服务，不需要在 Worker 中硬编码 Google 的单个服务器 IP。

## 加入 Monica Steam

进入：

```text
网络优化
→ 解析来源
→ 添加自定义 DoH
```

名称可以写：

```text
Google DoH · Cloudflare Relay
```

地址填写：

```text
https://你的Worker/TOKEN/dns-query
```

保存后，它会进入 Monica Steam 的统一解析来源列表。

因此同一个自定义 DoH 可以：

- 单独启用 / 禁用；
- 单独测速；
- 参加全部测速；
- 用于动态 DNS / DoH；
- 用于静态 Hosts 扫描。

## 它会代理 Steam 流量吗？

不会。

DNS 查询：

```text
Monica → Cloudflare Worker → Google Public DNS
```

真正 Steam 请求：

```text
你的公网出口 → Steam / Steam CDN
```

所以 Worker 不会自动把 Steam 登录来源变成 Cloudflare 的出口 IP。

## 为什么不建议做公共服务

DNS 是高频请求。

如果把完整 DoH 地址公开出去：

```text
任何人
  ↓
你的 Worker
  ↓
Google Public DNS
```

请求数量就不再由自己控制。

即使每个请求的 CPU 消耗很低，也可能因为**请求次数**耗尽 Free 额度。

此外 Google Public DNS 自身也可能返回 `429 Too Many Requests` 等 HTTP 状态码，因此“Cloudflare 还有额度”并不意味着上游一定无限接受请求。

## 推荐方案

个人自用建议：

```text
Cloudflare Workers Free
+
随机 TOKEN
+
固定上游 dns.google/dns-query
+
不公开完整 URL
```

尽量保持代码简单，不增加不必要的 DNS 数据包处理逻辑。
