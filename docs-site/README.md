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

## GitHub Pages

仓库根目录的 `.github/workflows/deploy-docs.yml` 会构建并部署本站。

默认 VitePress `base`：

```text
/Monica-Steam/
```

因此适配项目 Pages：

```text
https://<username>.github.io/Monica-Steam/
```

首次使用时，需要在 GitHub 仓库：

```text
Settings → Pages → Build and deployment → Source
```

选择：

```text
GitHub Actions
```

## Cloudflare Pages

项目也可以直接导入 Cloudflare Pages。

建议：

```text
Root directory: docs-site
Build command: npm run docs:build
Build output directory: .vitepress/dist
```

Cloudflare Pages 构建环境存在 `CF_PAGES` / `CF_PAGES_URL` 时，VitePress 会自动使用 `/` 作为 base，不需要修改源码。

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
