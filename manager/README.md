# 乐谱管理系统 (Sheet Music Manager)

本项目提供两类前端使用方式，均共用同一套后端服务与数据库：
- 后台管理端：`/web/login.html`、`/web/sheets.html`
- TV 端对接：通过外部接口 `/api/sheets/external` 拉取公开乐谱信息

## 环境要求
- Go >= 1.23
- MySQL >= 5.7
- 现代浏览器（Chrome / Edge / Safari）

## 快速启动
1. 安装依赖

```bash
cd manager
go mod tidy
```

2. 初始化数据库

```bash
mysql -uroot -p < docs/schema.sql
```

3. 配置环境变量（推荐使用 `.env`）

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

4. 启动服务

```bash
go run main.go
```

## 后台端（Web）用法
- 登录地址：`http://127.0.0.1:8080/web/login.html`
- 列表地址：`http://127.0.0.1:8080/web/sheets.html`
- 支持功能：
  - 单张/批量上传（JPG/PNG，单文件 <= 10MB）
  - 鼠标与手机触摸拖拽排序
  - 重命名（含 BPM 编辑）
  - 删除
  - 查看大图

## TV 端用法
TV 端建议仅调用公开接口，不走后台登录态。

### TV 拉取接口
- `GET /api/sheets/external`
- 返回字段：`id`、`title`、`thumbUrl`、`bpm`、`uploadTime`

### TV 端接入建议
- 轮询或定时刷新接口数据
- `thumbUrl` 可直接作为海报/预览图地址
- 按 `sort_order` 已排序后的顺序展示

### TV 截图预留
- TV 首页截图（待补充）  
  `![TV-首页](docs/images/tv-home.png)`
- TV 列表页截图（待补充）  
  `![TV-列表](docs/images/tv-list.png)`
- TV 播放页截图（待补充）  
  `![TV-播放](docs/images/tv-preview.png)`

## 部署方式
### 方式一：脚本部署

```bash
chmod +x deploy.sh
./deploy.sh
```

### 方式二：手动部署

```bash
go build -o app_manager main.go
mkdir -p public/uploads
nohup ./app_manager > app.log 2>&1 &
```

## API 概览
统一返回：`{"code": number, "msg": string, "data": any}`

- `POST /api/auth/login`
- `POST /api/sheets`（支持 `file` 单传和 `files` 多传）
- `GET /api/sheets`
- `PUT /api/sheets/{id}/sort`
- `PUT /api/sheets/{id}`（更新标题与 BPM）
- `DELETE /api/sheets/{id}`
- `GET /api/sheets/external`

## 安全与防泄露检查
### 1) 不要提交真实密钥
- 禁止在代码、README、脚本中写入真实数据库密码/Token/JWT 密钥。
- 统一通过环境变量注入：
  - `DATABASE_DSN`
  - `JWT_SECRET`

### 2) 提交前扫描

```bash
git grep -nE "(password|passwd|secret|token|DATABASE_DSN|AKIA|BEGIN PRIVATE KEY)"
```

### 3) 自动忽略
- 已通过 `.gitignore` 忽略：
  - `.env` / `.env.*`
  - 上传文件目录 `public/uploads/*`
  - 日志文件 `*.log`

## 测试

```bash
go test ./...
```
