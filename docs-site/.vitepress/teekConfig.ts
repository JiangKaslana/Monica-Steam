import { defineTeekConfig } from "vitepress-theme-teek/config";

export const teekConfig = defineTeekConfig({
  teekHome: false,
  vpHome: true,
  sidebarTrigger: true,
  author: {
    name: "Monica Steam contributors",
    link: "https://github.com/JoyinJoester/Monica-Steam",
  },
  footerInfo: {
    copyright: {
      createYear: 2026,
      suffix: "Monica Steam contributors.",
    },
  },
  codeBlock: {
    copiedDone: (TkMessage) => TkMessage.success("复制成功！"),
  },
  toComment: { enabled: false },
  articleShare: { enabled: true },
  themeEnhance: {
    enabled: true,
    position: "top",
    themeColor: {
      defaultColorName: "vp-primary",
    },
  },
});
