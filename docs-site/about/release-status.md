# 版本状态

Monica Steam 当前处于**公开测试阶段**。仓库中的 `RELEASE_NOTES.md` 当前记录为第四个公开测试版本。

## 当前重点

近期版本主要围绕这些方向持续完善：

- Steam 链接深度链接与应用内打开；
- 商店、喜加一、CD Key 激活；
- 游戏库详情、个人游戏数据与社区截图；
- 好友添加、好友申请与聊天图片；
- 设置页和底部导航体验；
- Steam WebView 生命周期、错误页和权限边界；
- Steam 网络优化；
- 动态 DNS / DoH；
- 静态 Hosts 扫描；
- 网络回退与运行时稳定性。

## 网络优化近期变化

当前网络优化已经从单一“扫描并应用”流程演进为两套独立机制：

### 动态 DNS / DoH

- 无需先扫描；
- 可启停解析来源；
- 自定义 DNS / DoH；
- 短期缓存；
- 过期缓存回退；
- IPv6 优先；
- 手动刷新；
- 多请求合并；
- 解析源为空时回到 Android 正常网络。

### 静态 Hosts

- DNS / DoH 收集候选 IP；
- 重复 HTTPS 验证；
- SNI / TLS / 证书检查；
- 扫描与应用分离；
- 当前节点会参与重新比速；
- 未覆盖域名继续系统回退；
- 拒绝私网、保留地址、文档网段和常见 Fake-IP。

详情见 [网络优化说明](../network/overview)。

## 已知限制

公开测试版仍可能遇到：

- Steam 接口变化后部分功能失效；
- 商店、聊天、通知受账号地区和登录状态影响；
- 实验性语音在不同 Android 设备上表现不一致；
- WebView 与原生网络栈行为不同；
- Steam 风控风险；
- 部分页面缓存与实时 Steam 状态存在短暂差异。

## 升级建议

升级前建议备份：

- `maFile`；
- Steam 账号 ZIP；
- MDBX 数据（如果使用）；
- 其他重要账号恢复资料。

不要只保留应用内部唯一一份数据。

## 查看完整更新记录

完整公开测试版变更请查看仓库：

[RELEASE_NOTES.md](https://github.com/JoyinJoester/Monica-Steam/blob/main/RELEASE_NOTES.md)

GitHub Releases：

[Monica Steam Releases](https://github.com/JoyinJoester/Monica-Steam/releases)

## 如何判断功能是否“正式”

文档使用以下口径：

- **稳定功能**：在当前测试版中作为正常使用路径维护；
- **实验性功能**：实现存在，但设备、网络或服务端差异仍较大；
- **未实现**：不会为了界面完整而提供没有底层能力的假开关。

例如网络优化当前不会把 ECH、WebView Gateway 等尚未完整落地的能力伪装成可用设置。
