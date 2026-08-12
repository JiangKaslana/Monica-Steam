# 构建与测试

本页面向希望本地编译 Monica Steam、调试网络优化或提交代码的开发者。

## 环境要求

当前仓库 README 给出的构建基线为：

- Android Studio 最新稳定版；
- JDK 17+；
- `compileSdk 35`；
- `targetSdk 34`；
- `minSdk 26`（Android 8.0+）；
- Android Gradle Plugin `8.7.3`；
- Kotlin `2.0.21`；
- Compose BOM `2026.03.00`。

依赖版本最终以仓库中的 `gradle/libs.versions.toml` 和实际 Gradle 配置为准。

## 常用命令

Windows：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

构建 Debug：

```powershell
.\gradlew.bat :app:assembleDebug
```

构建 Release：

```powershell
.\gradlew.bat :app:assembleRelease
```

在 Linux / macOS 下，把 `gradlew.bat` 换成 `./gradlew` 即可。

## Release 签名

Release 签名应通过外部配置提供，例如：

- `keystore.properties`；
- `MONICA_STEAM_RELEASE_*` 环境变量。

::: danger
不要把 keystore、密码、签名配置或私钥提交到 Git 仓库。
:::

## 网络优化相关测试

网络优化已经有独立 CI 覆盖，用于检查：

- 目标域名目录；
- DNS / DoH 配置模型；
- 动态解析运行时；
- 静态扫描行为；
- 集成保护规则；
- Debug APK 构建。

修改网络层时，不应只确认“能编译”，还要验证：

1. 未开启网络优化时仍能回到正常 Android 网络；
2. 自定义 DNS / DoH 的启停状态正确持久化；
3. 静态 Hosts 与动态 DNS 的优先级没有倒置；
4. 私网、保留地址与 Fake-IP 不会被错误应用；
5. 并发解析不会无限扩张；
6. WebView 没有被文档或 UI 误描述成已经完整接入动态解析。

## 文档站本地运行

文档站位于：

```text
docs-site/
```

进入目录后：

```bash
npm install
npm run docs:dev
```

构建：

```bash
npm run docs:build
```

预览生产构建：

```bash
npm run docs:preview
```

构建产物位于：

```text
docs-site/.vitepress/dist/
```

## GitHub Pages

仓库包含独立 GitHub Actions 工作流构建文档站。

默认项目 Pages 路径使用：

```text
/Monica-Steam/
```

因此适用于：

```text
https://<username>.github.io/Monica-Steam/
```

如果部署到 Cloudflare Pages，配置会检测 `CF_PAGES` 环境并自动把站点 base 切换为 `/`。

## Cloudflare Pages

文档站也是标准 VitePress 项目，可直接导入 Cloudflare Pages。

推荐配置：

```text
Root directory: docs-site
Build command: npm run docs:build
Build output directory: .vitepress/dist
```

## 提交前建议

- 先运行 JVM 测试；
- 涉及网络优化时关注对应 CI；
- 文档改动至少跑一次 `npm run docs:build`；
- 不提交 APK、keystore、令牌、私有 DoH TOKEN 或账号测试数据；
- 新增用户可见功能时同步更新文档站与 RELEASE_NOTES。
