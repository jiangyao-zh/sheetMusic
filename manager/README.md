# 乐谱管理系统 (Sheet Music Manager)

基于现有 Manager 框架扩展的完整乐谱管理业务系统，提供 RESTful API 和响应式 Web 前端。

## 环境要求
- Go >= 1.23
- MySQL >= 5.7
- 现代浏览器 (Chrome, Edge, Safari)

## 安装步骤
1. **下载源码**
   进入 `manager` 目录。
2. **安装依赖**
   ```bash
   go mod tidy
   ```
3. **初始化数据库**
   在 MySQL 5.7 中执行 `docs/schema.sql` 脚本，将自动创建 `music` 数据库和相关表，并初始化默认账号 `jiangyiyi` / `123456`。

## 配置说明
应用配置文件通常位于 `.env` 或系统环境变量中：
```ini
APP_NAME=sheet-music-manager
APP_ENV=local
SERVER_PORT=8080
GIN_MODE=debug
DB_DRIVER=mysql
# 替换为您的实际数据库账号密码
DATABASE_DSN="music:iFiKkEPXw7tcncPF@tcp(43.142.45.253:3306)/music?charset=utf8mb4&parseTime=True&loc=Local"
LOG_LEVEL=info
```

## 接口列表 (API 文档)
所有接口（除登录外）均需在 Header 中携带 `Authorization: Bearer <token>`。
返回格式统一为：`{"code": 200, "msg": "success", "data": ...}`

### 1. 登录
- **POST** `/api/auth/login`
- **Body**: `{"username": "jiangyiyi", "password": "123456"}`

### 2. 乐谱管理
- **GET** `/api/sheets` 
  - Query: `?keyword=xxx` (可选，支持搜索)
- **POST** `/api/sheets` (上传)
  - Content-Type: `multipart/form-data`
  - Form: `file` (jpg/png, <=10MB)
- **PUT** `/api/sheets/:id` (重命名)
  - Body: `{"title": "新标题"}`
- **PUT** `/api/sheets/:id/sort` (修改排序)
  - Body: `{"sort_order": 1}`
- **DELETE** `/api/sheets/:id` (删除)

### 3. 外部系统调用
- **GET** `/api/sheets/external`
  - 说明：公开接口，无需鉴权。返回简要的对外暴露数据。

## 部署脚本 (deploy.sh)
项目根目录下提供了基础的部署启动脚本参考。
```bash
#!/bin/bash
# 赋予执行权限：chmod +x deploy.sh
# 运行部署：./deploy.sh

echo "开始部署乐谱管理系统..."
go build -o app_manager main.go

# 确保上传目录存在
mkdir -p public/uploads

echo "重启服务..."
# 结束旧进程
pkill -f app_manager || true

# 后台启动
nohup ./app_manager > app.log 2>&1 &

echo "部署完成！服务已在后台运行。"
```

## 单元测试
项目包含核心业务逻辑的单元测试，覆盖率要求 >=80%。
```bash
# 运行测试并查看覆盖率
cd internal/sheet
go test -v -cover
```
