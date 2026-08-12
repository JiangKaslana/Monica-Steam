# Monica 生态关系

Monica Steam 属于 Monica 生态的一部分，但定位和 Monica Pass 不同。

## Monica Pass

[Monica Pass](https://monica-pass.github.io/Monica/) 是本地优先密码管理器与 Monica 生态入口，重点是：

- 密码记录；
- 2FA；
- 卡券与便签；
- 自动填充；
- 本地加密；
- 密码库工作流。

## Monica Android

Monica Android 是 Monica 系列的完整 Android 工程，也是 Monica Steam 最初 Steam 模块的来源。

Monica Steam 的源码快照记录了从 Monica Android 独立出来的基线，之后作为单独应用演进。

## Monica Steam

Monica Steam 专注 Steam：

- Steam Guard；
- 多账号；
- 登录批准；
- 移动确认；
- 游戏库；
- Steam 商店；
- 好友 / 聊天 / 通知；
- Steam 账号备份；
- Steam 网络优化。

## 三者关系

| 项目 | 主要定位 | 数据边界 |
| --- | --- | --- |
| Monica Pass | 本地优先密码管理器 | 密码库、2FA、便签、自动填充等 |
| Monica Android | Monica Android 完整客户端 / 源项目 | Monica 生态完整 Android 能力 |
| Monica Steam | Steam 专用 Android 客户端 | Steam 账号、令牌、会话、库、社区等 |

## 可以同时安装吗？

可以。

Monica Steam 使用独立应用 ID 与 Android 沙箱，不会自动读取 Monica Pass 数据。

## 为什么文档站风格相似

Monica Steam 文档站有意借鉴 Monica Pass 文档站的一些视觉元素，以保持生态识别度：

- VitePress + Teek；
- 渐变 Hero 标题；
- 悬浮应用图标和柔和光晕；
- 圆角 CTA；
- 悬停功能卡片；
- 可折叠侧边栏；
- 主题增强；
- Material / Monica 风格的色彩层次。

但信息架构、配色与内容针对 Steam 客户端重新设计，而不是直接复制 Monica Pass 的文案和页面结构。

## 项目链接

- [Monica Steam](https://github.com/JoyinJoester/Monica-Steam)
- [Monica Pass 文档站](https://monica-pass.github.io/Monica/)
- [Monica 组织仓库](https://github.com/Monica-Pass)

## 商标说明

Monica Steam 是非官方第三方 Steam 客户端，与 Valve Corporation 没有隶属、授权或赞助关系。Steam、Steam Guard 及相关商标归各自权利人所有。
