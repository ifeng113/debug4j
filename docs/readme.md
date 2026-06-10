# Debug4j 文档站点 —— 部署与维护指南

## 项目结构

```
docs/
├── .vitepress/
│   ├── config.mjs        # VitePress 配置文件
│   ├── theme/
│   │   ├── index.js      # 主题自定义
│   │   └── style.css     # 样式覆盖
│   ├── dist/             # 构建产物（默认输出目录）
│   └── cache/            # VitePress 缓存（可忽略）
├── guide/                # 指南文档
├── features/             # 功能特性文档
├── technology/           # 技术实现文档
├── api/                  # API 参考文档
├── index.md              # 首页
├── package.json          # 依赖声明（仅 vitepress）
└── readme.md             # 本文档
```

---

## 1. 环境要求

| 依赖 | 版本要求 |
|------|---------|
| Node.js | >= 18.x（推荐 20 LTS） |
| npm / pnpm / yarn | npm >= 9, pnpm >= 8 |

---

## 2. 首次初始化

```bash
# 进入 docs 目录（工作目录固定在此）
cd docs

# 安装依赖（package.json 已声明 vitepress）
npm install

# 或使用 pnpm（更快）
pnpm install
```

> 如果从零搭建新站点，执行 `npm install -D vitepress` 会自动安装最新版。

---

## 3. 本地开发

```bash
# 启动开发服务器（默认 http://localhost:5173）
npx vitepress dev

# 指定端口
npx vitepress dev --port 3000

# 监听所有网络接口（供局域网设备访问）
npx vitepress dev --host 0.0.0.0
```

- 支持热更新（HMR），修改 Markdown 文件后浏览器自动刷新。
- `Ctrl+C` 停止服务。

---

## 4. 生产构建

```bash
# 构建静态站点
npx vitepress build
```

- 输出目录：`.vitepress/dist/`
- 构建产物包含 HTML、JS、CSS、资源文件的完整目录结构，可直接部署至任何静态文件服务器。

---

## 5. 预览构建产物（可选）

```bash
# 本地预览构建结果
npx vitepress preview
```

- 默认地址 `http://localhost:4173`
- 可用于验证构建产物是否正确后再部署。

---

## 6. 部署方式

### 6.1 GitHub Pages（推荐）

在 GitHub 仓库中配置 Actions 自动部署：

**方案 A：从 `docs/` 目录部署**

在 GitHub 仓库 → Settings → Pages → 选择：

```
Source: GitHub Actions
```

创建 `.github/workflows/deploy-docs.yml`：

```yaml
# .github/workflows/deploy-docs.yml
name: Deploy Docs
on:
  push:
    branches: [master, master-dev]
    paths:
      - 'docs/**'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: docs
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: docs/package-lock.json
      - run: npm ci
      - run: npx vitepress build
      - uses: actions/upload-pages-artifact@v3
        with:
          path: docs/.vitepress/dist

  deploy:
    runs-on: ubuntu-latest
    needs: build
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

**方案 B：使用 peaceiris/actions-gh-pages**

```yaml
# .github/workflows/deploy-docs.yml
name: Deploy Docs
on:
  push:
    branches: [master]
    paths:
      - 'docs/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - name: Install & Build
        working-directory: docs
        run: |
          npm ci
          npx vitepress build
      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: docs/.vitepress/dist
          publish_branch: gh-pages
```

### 6.2 Nginx / 静态服务器

构建后将 `.vitepress/dist/` 目录复制到 Web 服务器：

```bash
# 构建
cd docs && npx vitepress build

# 将产物部署到目标服务器
scp -r .vitepress/dist/* user@your-server:/var/www/debug4j-docs/
```

Nginx 配置示例：

```nginx
server {
    listen 80;
    server_name docs.debug4j.dev;
    root /var/www/debug4j-docs;
    index index.html;

    # SPA 路由支持（VitePress 是 MPA，但保留以防子页面刷新 404）
    location / {
        try_files $uri $uri/ =404;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### 6.3 Docker 部署

```dockerfile
# Dockerfile
FROM nginx:alpine
COPY .vitepress/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

```bash
# 构建镜像
docker build -t debug4j-docs .

# 运行
docker run -d --name debug4j-docs -p 8080:80 debug4j-docs
```

### 6.4 Vercel / Netlify / Cloudflare Pages

**Vercel：**
- 导入 Git 仓库 → 框架选择 VitePress
- 根目录：`docs/`
- 构建命令：`npx vitepress build`
- 输出目录：`.vitepress/dist`

**Netlify：**
- `Base directory`: `docs/`
- `Build command`: `npx vitepress build`
- `Publish directory`: `docs/.vitepress/dist`

**Cloudflare Pages：**
- `Framework preset`: VitePress
- `Build command`: `npx vitepress build`
- `Build output directory`: `.vitepress/dist`
- `Root directory`: `docs/`

---

## 7. 环境变量（可选）

在构建时通过环境变量注入运行时配置，需配合 VitePress 的 `defineConfig` 使用：

```bash
# Linux / macOS
NODE_ENV=production BASE_URL=/debug4j/ npx vitepress build

# Windows (CMD)
set NODE_ENV=production && npx vitepress build

# Windows (PowerShell)
$env:NODE_ENV="production"; npx vitepress build
```

---

## 8. 常见问题

### 8.1 构建时出现内存溢出

```bash
# 增加 Node.js 内存限制
NODE_OPTIONS="--max-old-space-size=2048" npx vitepress build
```

### 8.2 死链 / 404 检查

VitePress 1.x 及以上版本会自动检测 Markdown 中的失效内部链接：

```js
// .vitepress/config.mjs
export default defineConfig({
  ignoreDeadLinks: [
    /^http:\/\/localhost/,  // 忽略本地链接
  ],
})
```

### 8.3 自定义 base path

如果站点部署在子路径下（如 `https://user.github.io/debug4j/`）：

```js
// .vitepress/config.mjs
export default defineConfig({
  base: '/debug4j/',
})
```

### 8.4 清除缓存

```bash
# 删除 VitePress 缓存
rm -rf docs/.vitepress/cache
rm -rf docs/node_modules/.vite
```

---

## 9. 快速命令参考

| 命令 | 说明 |
|------|------|
| `npm install` | 安装依赖 |
| `npx vitepress dev` | 启动开发服务器（热更新） |
| `npx vitepress build` | 生产构建，输出到 `.vitepress/dist` |
| `npx vitepress preview` | 预览构建产物 |
| `npx vitepress build --mpa` | 多页应用模式（不使用客户端路由） |

---

## 10. 输出目录说明（`.vitepress/dist/`）

构建完成后产物目录结构如下：

```
.vitepress/dist/
├── index.html                 # 首页
├── 404.html                   # 自定义 404 页
├── guide/introduction.html    # 产品介绍
├── guide/getting-started.html # 快速开始
├── features/proxy-tunnel.html # 代理穿透
├── ...                        # 其他页面
├── assets/                    # 静态资源（JS/CSS/字体）
│   ├── app.xxx.js
│   ├── style.xxx.css
│   └── chunks/
└── hashmap.json               # 资源映射清单
```

所有文件均为纯静态文件，无需服务端运行时，可托管于任何 HTTP 服务器或 CDN。