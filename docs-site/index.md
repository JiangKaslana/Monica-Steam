---
layout: home

hero:
  name: "Monica Steam"
  text: "Steam Guard · 游戏库 · 商店 · 社区"
  tagline: "专注 Steam 的独立 Android 客户端。把令牌、移动确认、游戏库、商城、好友聊天、备份与网络优化集中到一个应用中。"
  image:
    src: /monica-steam.webp
    alt: Monica Steam 应用图标
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/quick-start
    - theme: alt
      text: 网络优化
      link: /network/overview
    - theme: alt
      text: GitHub
      link: https://github.com/JoyinJoester/Monica-Steam

features:
  - title: Steam Guard 与移动确认
    details: 管理动态令牌、多账号、登录批准、交易确认与授权设备，并支持 maFile 导入。
    icon: <i class="ri-shield-keyhole-fill"></i>
  - title: 游戏库与统计
    details: 查看游戏库、游玩时间、成就、家庭共享、最近游玩与离线缓存，并提供多维统计。
    icon: <i class="ri-gamepad-fill"></i>
  - title: Steam 商店
    details: 浏览、搜索、愿望单、购物车、多区价格、DLC 与配置要求；最终结算仍走 Steam 官方流程。
    icon: <i class="ri-store-2-fill"></i>
  - title: 好友、聊天与通知
    details: 好友列表、私聊、群聊、图片、贴纸、回应、通知，以及仍处于实验阶段的语音能力。
    icon: <i class="ri-message-3-fill"></i>
  - title: 备份与本地安全
    details: 主密码、生物识别、本地加密存储，以及 Steam 专用 maFile WebDAV 与 ZIP 备份恢复。
    icon: <i class="ri-lock-password-fill"></i>
  - title: Steam 网络优化
    details: 动态 DNS / DoH 与静态 Hosts 两套独立机制，共享解析来源，在不引入系统 VPN 的前提下优化直连访问。
    icon: <i class="ri-global-line"></i>
---

## 从这里开始

<div class="docs-card-grid">
  <a class="docs-card" href="./guide/quick-start">
    <strong>安装与首次使用</strong>
    <span>下载 APK、导入账号、初始化 Steam Guard，并先把重要令牌数据备份好。</span>
  </a>
  <a class="docs-card" href="./guide/risk-and-safety">
    <strong>风险与安全边界</strong>
    <span>公开测试版、Steam 风控、数据备份，以及 Monica Steam 与官方 Steam 的边界。</span>
  </a>
  <a class="docs-card" href="./network/overview">
    <strong>网络优化</strong>
    <span>理解动态 DNS / DoH、静态 Hosts、解析来源共享、缓存与优先级。</span>
  </a>
  <a class="docs-card" href="./reference/faq">
    <strong>常见问题</strong>
    <span>DoH 会不会换出口 IP、什么时候该重扫 Hosts、WebView 是否受网络优化影响等。</span>
  </a>
</div>

::: warning 当前仍是公开测试版
Monica Steam 不是 Steam 官方客户端，部分能力依赖 Steam 网页或非公开移动接口；Steam 侧接口、登录状态与风控策略变化都可能影响功能。**请不要把测试版当作 Steam 令牌或账号资料的唯一备份。**
:::

## 文档站设计

本站使用 VitePress 构建，并借鉴 Monica Pass 文档站的一些视觉语言：渐变 Hero 标题、悬浮应用图标、圆角操作按钮、可交互功能卡片、主题增强与可折叠侧边栏；内容与导航则针对 Monica Steam 重新组织。
