# 配置管理指南

本项目使用统一的 `.env` 文件管理所有环境配置，包括 TV 端 API 地址和 Manager 后端数据库配置。

## 快速开始

### 1. 创建配置文件

复制示例配置文件并修改为实际配置：

```bash
# 在项目根目录
cp .env.example .env
```

编辑 `.env` 文件，填入实际配置信息。

### 2. TV 端配置生成

TV 端需要将 `.env` 配置转换为 JavaScript 模块：

```bash
cd tv
npm run config:generate
```

这会生成 `tv/src/config/env.js` 文件，包含 API 地址配置。

**重要提示**：
- `env.js` 文件包含 API 地址等配置信息
- 该文件已加入 `.gitignore`，不会提交到代码仓库
- 每次修改 `.env` 后都需要重新运行 `npm run config:generate`

### 3. Manager 后端配置

Manager 后端会自动从根目录的 `.env` 文件读取配置，无需额外操作。

## 配置项说明

### TV 端配置

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `TV_API_HOST` | API 地址 | `http://your-server-ip:8080` |

### Manager 后端配置

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `APP_NAME` | 应用名称 | `sheet-music-manager` |
| `APP_ENV` | 运行环境 | `production` / `local` |
| `SERVER_PORT` | HTTP 监听端口 | `8080` |
| `GIN_MODE` | Gin 运行模式 | `release` / `debug` |
| `DB_DRIVER` | 数据库驱动 | `mysql` |
| `DATABASE_DSN` | 数据库连接串 | `user:pass@tcp(host:3306)/db?...` |
| `LOG_LEVEL` | 日志级别 | `info` / `debug` / `error` |
| `JWT_SECRET` | JWT 密钥 | 随机字符串 |
| `UPLOAD_DIR` | 文件上传目录 | `public/uploads` |
| `MAX_FILE_SIZE` | 最大文件大小（字节） | `10485760` (10MB) |

## 开发流程

### 本地开发

1. **修改配置**
   ```bash
   # 编辑根目录的 .env 文件
   vim .env
   ```

2. **TV 端开发**
   ```bash
   cd tv
   # 生成配置
   npm run config:generate
   # 启动开发服务器
   npm run dev:h5
   ```

3. **Manager 后端开发**
   ```bash
   cd manager
   # 直接运行，会自动读取 .env
   go run main.go
   ```

### 生产部署

1. **配置生产环境**
   ```bash
   # 在服务器上创建 .env 文件
   vim .env
   ```

2. **部署 TV 端**
   ```bash
   cd tv
   # 生成配置
   npm run config:generate
   # 打包 APP
   npm run build:app-plus
   ```

3. **部署 Manager 后端**
   ```bash
   cd manager
   # 编译
   go build -o app-server main.go
   # 运行（会自动读取 ../.env）
   ./app-server
   ```

## 配置加载机制

### TV 端

TV 端统一使用 `TV_API_HOST` 配置的 API 地址，不区分开发和生产环境。

### Manager 后端

Manager 后端优先级：

1. 系统环境变量
2. 根目录 `.env` 文件
3. 当前目录 `.env` 文件
4. 代码内置默认值

## 文件结构

```
sheetMusic/
├── .env                          # 环境配置（不提交到 Git）
├── .env.example                  # 配置示例（提交到 Git）
├── .gitignore                    # Git 忽略文件
├── CONFIG_GUIDE.md               # 本文档
├── tv/
│   ├── scripts/
│   │   └── generate-config.js    # 配置生成脚本
│   ├── src/
│   │   └── config/
│   │       ├── api.js            # API 配置（使用 env.js）
│   │       └── env.js            # 环境配置（自动生成）
│   └── package.json              # 包含 config:generate 脚本
└── manager/
    └── internal/
        └── config/
            └── config.go         # 配置加载（读取 .env）
```

## 注意事项

### 安全性

1. **不要提交 `.env` 文件到 Git**
   - `.env` 包含敏感信息（数据库密码、JWT 密钥等）
   - 已在 `.gitignore` 中配置忽略
   - 使用 `.env.example` 作为配置模板

2. **生产环境密钥**
   - 修改 `JWT_SECRET` 为随机字符串
   - 使用强密码保护数据库
   - 定期更换敏感配置

### 配置同步

1. **TV 端配置更新**
   ```bash
   # 修改 .env 后，必须重新生成配置
   cd tv
   npm run config:generate
   ```

2. **Manager 后端配置更新**
   ```bash
   # 修改 .env 后，重启服务即可
   cd manager
   ./restart.sh
   ```

### 常见问题

**Q: TV 端修改了 .env 但没有生效？**

A: 需要运行 `npm run config:generate` 重新生成配置文件。

**Q: Manager 后端找不到 .env 文件？**

A: 确保 `.env` 文件在项目根目录（manager 的上一级目录）。

**Q: 如何在不同环境使用不同配置？**

A: 可以创建多个配置文件（如 `.env.dev`, `.env.prod`），部署时复制对应文件为 `.env`。

## 刷新功能

### TV 端列表页刷新

列表页右上角有刷新按钮，点击可重新从 API 获取最新乐谱列表。

**使用场景**：
- Manager 后端上传了新乐谱
- 需要同步最新的乐谱数据
- 网络异常后重新加载

**操作方式**：
- 点击标题栏右侧的"刷新"按钮
- 或在浏览器中刷新页面（会自动重新加载）

## 相关文件

- `/tv/API_INTEGRATION.md` - API 整合文档
- `/.env.example` - 配置示例
- `/.gitignore` - Git 忽略规则
