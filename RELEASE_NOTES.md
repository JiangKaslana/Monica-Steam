# Monica Steam 首个公开版本

Monica Steam 是一款专注于 Steam 账号管理与日常使用的第三方 Android 客户端。本版本从 Steam 令牌管理出发，加入游戏库、商店、好友聊天、通知和数据备份等功能。

## 与 Monica 的关系

Monica Steam 源自 Monica Android 中的 Steam 功能，并在其设计与部分基础组件之上独立发展。

- 保留 Monica 熟悉的 Material 3 Expressive 设计、配色方案和交互动效
- 复用 Monica 的 Steam 令牌、账号导入、安全锁定及部分设置组件
- 支持 `maFile`、ZIP 和 MDBX 等账号数据来源
- 与 Monica Android 使用独立的软件包、代码仓库和发布周期
- 不会替换或修改 Monica Android
- Monica 继续承担综合账号管理功能，Monica Steam 则专注于 Steam 体验

## 主要功能

### Steam 令牌

- Steam Guard 动态令牌
- 扫码导入与账号管理
- 确认交易等 Steam 移动确认操作
- `maFile` 导入、导出与 ZIP 备份
- MDBX 数据源支持
- 主密码与生物识别保护

### 游戏库

- 查看多个 Steam 账号的游戏库存
- 统计游戏数量、总游玩时间与估算价值
- 显示最近两周游玩时间和家庭共享游戏
- 按游玩状态、时长和全成就筛选
- 查看游戏详情与成就
- 游戏数量、价格分布及游玩热力图
- 离线缓存，上次同步的数据断网后仍可查看

### Steam 商店

- 浏览、搜索和筛选 Steam 游戏
- 根据账号地区与货币显示价格
- 查看多区价格、购买选项、DLC、捆绑包和系统配置要求
- 查看游戏截图、介绍与玩家评价
- 原生购物清单与愿望单
- 通过 Steam 官方页面完成最终结算
- 支持部分活动内容与点数商店浏览

### 好友与聊天

- Steam 好友列表与好友详情
- 私聊和群聊统一显示在会话列表
- 创建群聊、邀请成员及编辑群组信息
- 群主可设置 Steam 群头像
- 支持文字、Steam 表情、贴纸和图片消息
- 消息复制、回应、举报与聊天记录搜索
- 支持置顶会话和消息免打扰

### 通知与确认

- 独立 Steam 通知页面
- 查看通知详情与未读状态
- 处理礼物、交易等支持的 Steam 通知
- 保留 Steam 移动确认功能

### 个性化与数据

- Monica 配色方案及 Monica Plus 配色
- Dock 排序和界面缩放
- WebDAV `maFile` 备份与恢复
- 日志查看、清除和文件分享
- 桌面账号及最近游玩小组件

## 首发版本说明

这是 Monica Steam 的第一个公开版本。部分功能依赖 Steam 的非公开接口和网页服务，Steam 更新接口、登录策略或地区规则后，相关功能可能需要同步适配。

建议首次使用前备份现有 `maFile`。涉及购买和礼物接收时，最终操作仍以 Steam 官方页面显示的内容为准。

## 免责声明

Monica Steam 是非官方第三方客户端，与 Valve Corporation 或 Steam 没有隶属、授权或合作关系。Steam、Steam Guard 及相关商标归 Valve Corporation 所有。
