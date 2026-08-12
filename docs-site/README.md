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

## Cloudflare Pages 部署

仓库根目录的 `.github/workflows/deploy-docs.yml` 使用 GitHub Actions 构建并部署文档站到 Cloudflare Pages。

Cloudflare Pages 项目名固定为：

```text
monica-steam
```

因此 Cloudflare 分配的默认站点地址通常为：

```text
https://monica-steam.pages.dev/
```

> 如果 `monica-steam` 这个 Pages 项目名在对应 Cloudflare 账号中已经被占用，需要在 Cloudflare 后台选择另一个可用项目名，并同步修改工作流中的 `CLOUDFLARE_PAGES_PROJECT`。

### 首次配置

先在 Cloudflare Dashboard 的 **Workers & Pages** 中创建一个 Pages 项目，项目名填写：

```text
monica-steam
```

生产分支使用：

```text
main
```

然后在 GitHub 仓库：

```text
Settings → Secrets and variables → Actions
```

添加两个 Repository secrets：

```text
CLOUDFLARE_API_TOKEN
CLOUDFLARE_ACCOUNT_ID
```

其中 API Token 需要具有向目标 Cloudflare Pages 项目部署所需的权限。

完成后，每次 `main` 分支中以下内容发生变化，GitHub Actions 会自动重新构建并部署：

```text
docs-site/**
image/monica_launcher.webp
.github/workflows/deploy-docs.yml
```

Pull Request 不会真正部署，只会执行完整的 VitePress 构建检查。

### 构建参数

Cloudflare Pages 部署时工作流会设置：

```text
CF_PAGES=1
```

因此 VitePress 会自动使用：

```text
base: /
```

不再使用 GitHub Project Pages 的 `/Monica-Steam/` 子路径。

部署命令等价于：

```bash
npx wrangler pages deploy docs-site/.vitepress/dist --project-name=monica-steam --branch=main
```

## 自定义域名

`monica-steam` 是 Cloudflare Pages **项目名**，对应默认的 `monica-steam.pages.dev`。

如果以后有自己的域名，可以在 Cloudflare Pages 项目中的 **Custom domains** 继续绑定例如：

```text
docs.example.com
```

无需修改文档内容。

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
