import { defineConfig } from "vitepress";
import { teekConfig } from "./teekConfig";

export default defineConfig({
  extends: teekConfig,
  lang: "zh-CN",
  title: "Monica Steam 文档",
  description: "Monica Steam 安装、Steam Guard、游戏库、商店、聊天、备份、安全与网络优化文档。",
  base: "/Monica-Steam/",
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ["meta", { name: "theme-color", content: "#1a9fff" }],
    ["meta", { name: "color-scheme", content: "light dark" }],
    ["link", { rel: "icon", href: "./monica-steam.webp" }],
  ],
  themeConfig: {
    logo: "/monica-steam.webp",
    siteTitle: "Monica Steam",
    nav: [
      { text: "指南", link: "/guide/quick-start" },
      { text: "网络优化", link: "/network/overview" },
      { text: "参考", link: "/reference/faq" },
      { text: "开发", link: "/development/build" },
      {
        text: "项目",
        items: [
          { text: "项目与 Monica 生态", link: "/about/ecosystem" },
          { text: "版本状态", link: "/about/release-status" },
          { text: "赞助与支持", link: "/about/support" },
        ],
      },
    ],
    sidebar: [
      {
        text: "开始使用",
        collapsed: false,
        items: [
          { text: "快速开始", link: "/guide/quick-start" },
          { text: "风险与安全边界", link: "/guide/risk-and-safety" },
        ],
      },
      {
        text: "核心功能",
        collapsed: false,
        items: [
          { text: "账号、Steam Guard 与确认", link: "/guide/account-guard" },
          { text: "游戏库与商店", link: "/guide/library-store" },
          { text: "好友、聊天与通知", link: "/guide/friends-chat" },
          { text: "备份与本地安全", link: "/guide/backup-security" },
        ],
      },
      {
        text: "网络优化",
        collapsed: false,
        items: [
          { text: "动态 DNS / DoH 与静态 Hosts", link: "/network/overview" },
          { text: "Cloudflare Worker 反代 Google DoH", link: "/network/cloudflare-google-doh" },
        ],
      },
      {
        text: "参考",
        collapsed: true,
        items: [
          { text: "设置与使用建议", link: "/reference/settings" },
          { text: "常见问题", link: "/reference/faq" },
        ],
      },
      {
        text: "开发",
        collapsed: true,
        items: [
          { text: "构建与测试", link: "/development/build" },
          { text: "架构与项目边界", link: "/development/architecture" },
        ],
      },
      {
        text: "关于",
        collapsed: true,
        items: [
          { text: "Monica 生态关系", link: "/about/ecosystem" },
          { text: "版本状态", link: "/about/release-status" },
          { text: "赞助与支持", link: "/about/support" },
        ],
      },
    ],
    search: {
      provider: "local",
      options: {
        translations: {
          button: { buttonText: "搜索文档", buttonAriaLabel: "搜索文档" },
          modal: {
            noResultsText: "没有找到相关内容",
            resetButtonTitle: "清除查询",
            footer: { selectText: "选择", navigateText: "切换", closeText: "关闭" },
          },
        },
      },
    },
    outline: { level: [2, 3], label: "本页目录" },
    docFooter: { prev: "上一篇", next: "下一篇" },
    lastUpdated: { text: "最后更新于" },
    editLink: {
      pattern: "https://github.com/JiangKaslana/Monica-Steam/edit/main/docs-site/:path",
      text: "在 GitHub 上编辑此页",
    },
    socialLinks: [
      { icon: "github", link: "https://github.com/JoyinJoester/Monica-Steam" },
    ],
    footer: {
      message: "Monica Steam 是非官方第三方 Steam 客户端，与 Valve Corporation 无隶属、授权或赞助关系。",
      copyright: "Monica Steam contributors · GPL-3.0",
    },
  },
});
