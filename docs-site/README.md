# Monica Steam Docs Site

Monica Steam 的 VitePress 文档站。

## 技术栈

- VitePress
- vitepress-theme-teek
- Remix Icon

视觉上借鉴 Monica Pass 文档站的部分元素，包括渐变 Hero、浮动应用图标、圆角 CTA、功能卡片、主题增强和折叠侧边栏，但内容与信息架构针对 Monica Steam 重新设计。

## 本地开发

```bash
cd docs-site
npm install
npm run docs:dev
```

构建：

```bash
npm run docs:build
```

预览：

```bash
npm run docs:preview
```

## 静态资源

仓库不重复保存应用图标。GitHub Actions 在构建前会把：

```text
image/monica_launcher.webp
```

复制到：

```text
docs-site/public/monica-steam.webp
```

本地开发时如果首页图标缺失，可手动执行：

```bash
mkdir -p public
cp ../image/monica_launcher.webp public/monica-steam.webp
```

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force public
Copy-Item ..\image\monica_launcher.webp public\monica-steam.webp
```

## GitHub Pages 部署

仓库根目录的 `.github/workflows/deploy-docs.yml` 使用 GitHub Actions 构建并部署文档站到 GitHub Pages。

VitePress 使用项目 Pages 的固定 base：

```text
/Monica-Steam/
```

合并到上游仓库 `JoyinJoester/Monica-Steam` 后，默认站点地址为：

```text
https://joyinjoester.github.io/Monica-Steam/
```

### 首次配置

仓库维护者需要在 GitHub 仓库进入：

```text
Settings → Pages → Build and deployment → Source
```

选择：

```text
GitHub Actions
```

之后每次 `main` 分支中以下内容发生变化，Actions 会自动重新构建并部署：

```text
docs-site/**
image/monica_launcher.webp
.github/workflows/deploy-docs.yml
```

Pull Request 只执行 VitePress 构建检查，不会发布生产站点。

工作流使用 GitHub 官方 Pages Actions：

```text
actions/configure-pages@v5
actions/upload-pages-artifact@v3
actions/deploy-pages@v4
```

无需 Cloudflare Token，也无需额外服务器。

## 文档结构

```text
docs-site/
├── .vitepress/
│   ├── config.mts
│   ├── teekConfig.ts
│   └── theme/
├── guide/
├── network/
├── reference/
├── development/
├── about/
├── index.md
└── package.json
```
