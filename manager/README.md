# 乐谱管理后台 / Sheet Music Manager Backend

[中文](#中文) | [English](#english)

## 中文

本目录是后台服务与 Web 管理端。  
TV 端独立说明已移动到：`../tv/README.md`。

### 环境要求
- Go >= 1.23
- MySQL >= 5.7
- 现代浏览器（Chrome / Edge / Safari）

### 快速启动
1) 安装依赖

```bash
cd manager
go mod tidy
```

2) 初始化数据库

```bash
mysql -uroot -p < docs/schema.sql
```

3) 配置环境变量（推荐 `.env`）

```ini
APP_NAME=sheet-music-manager
APP_ENV=local
SERVER_PORT=8080
GIN_MODE=release
DB_DRIVER=mysql
DATABASE_DSN="username:password@tcp(host:3306)/music?charset=utf8mb4&parseTime=True&loc=Local"
JWT_SECRET="please-change-this-secret"
LOG_LEVEL=info
```

4) 启动服务

```bash
go run main.go
```

### 后台端（Web）用法
- 登录页：`http://127.0.0.1:8080/web/login.html`
- 乐谱页：`http://127.0.0.1:8080/web/sheets.html`
- 主要功能：
  - 单张/批量上传（JPG/PNG，单文件 <= 10MB）
  - 鼠标与手机触摸拖拽排序
  - 重命名（含 BPM 编辑）
  - 删除
  - 查看大图

### 部署
- 脚本部署

```bash
chmod +x deploy.sh
./deploy.sh
```

- 手动部署

```bash
go build -o app_manager main.go
mkdir -p public/uploads
nohup ./app_manager > app.log 2>&1 &
```

### API 概览
统一返回：`{"code": number, "msg": string, "data": any}`

- `POST /api/auth/login`
- `POST /api/sheets`（支持 `file` 单传和 `files` 多传）
- `GET /api/sheets`
- `PUT /api/sheets/{id}/sort`
- `PUT /api/sheets/{id}`（更新标题与 BPM）
- `DELETE /api/sheets/{id}`
- `GET /api/sheets/external`（给 TV 用）

### 安全建议
1) 不要提交真实密钥，统一使用环境变量：
- `DATABASE_DSN`
- `JWT_SECRET`

2) 提交前扫描：

```bash
git grep -nE "(password|passwd|secret|token|DATABASE_DSN|AKIA|BEGIN PRIVATE KEY)"
```

3) 使用 `.gitignore` 忽略：
- `.env` / `.env.*`
- `public/uploads/*`
- `*.log`

### 测试

```bash
go test ./...
```

## English

This directory contains the backend service and Web admin UI.  
TV documentation has been moved to: `../tv/README.md`.

### Requirements
- Go >= 1.23
- MySQL >= 5.7
- Modern browser (Chrome / Edge / Safari)

### Quick Start
1) Install dependencies

```bash
cd manager
go mod tidy
```

2) Initialize database

```bash
mysql -uroot -p < docs/schema.sql
```

3) Configure environment variables (recommended via `.env`)

```ini
APP_NAME=sheet-music-manager
APP_ENV=local
SERVER_PORT=8080
GIN_MODE=release
DB_DRIVER=mysql
DATABASE_DSN="username:password@tcp(host:3306)/music?charset=utf8mb4&parseTime=True&loc=Local"
JWT_SECRET="please-change-this-secret"
LOG_LEVEL=info
```

4) Run service

```bash
go run main.go
```

### Web Admin Usage
- Login: `http://127.0.0.1:8080/web/login.html`
- Sheets: `http://127.0.0.1:8080/web/sheets.html`
- Features:
  - Single/batch upload (JPG/PNG, <=10MB per file)
  - Mouse & mobile drag sorting
  - Rename (with BPM edit)
  - Delete
  - Full-size preview

### Deployment
- Script:

```bash
chmod +x deploy.sh
./deploy.sh
```

- Manual:

```bash
go build -o app_manager main.go
mkdir -p public/uploads
nohup ./app_manager > app.log 2>&1 &
```

### API Summary
Unified response: `{"code": number, "msg": string, "data": any}`

- `POST /api/auth/login`
- `POST /api/sheets` (`file` for single, `files` for batch)
- `GET /api/sheets`
- `PUT /api/sheets/{id}/sort`
- `PUT /api/sheets/{id}` (update title and BPM)
- `DELETE /api/sheets/{id}`
- `GET /api/sheets/external` (for TV client)

### Security
1) Never commit real secrets. Use environment variables:
- `DATABASE_DSN`
- `JWT_SECRET`

2) Pre-commit scan:

```bash
git grep -nE "(password|passwd|secret|token|DATABASE_DSN|AKIA|BEGIN PRIVATE KEY)"
```

3) Keep ignored:
- `.env` / `.env.*`
- `public/uploads/*`
- `*.log`

### Tests

```bash
go test ./...
```
