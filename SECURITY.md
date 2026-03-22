# 安全配置指南

本项目使用 `.env` 文件管理敏感配置信息。为了保护数据安全，请遵循以下指南。

## 重要提醒

⚠️ **`.env` 文件包含敏感信息，已被 `.gitignore` 忽略，不会提交到 Git 仓库**

✅ **代码中不包含任何敏感信息，所有配置都从 `.env` 文件读取**

## 首次配置步骤

### 1. 创建 `.env` 文件

在项目根目录，复制示例文件：

```bash
cp .env.example .env
```

### 2. 编辑 `.env` 文件

使用文本编辑器打开 `.env` 文件，填入实际配置：

```bash
# TV 端 API 地址
TV_API_HOST=http://your-actual-server-ip:8080

# 数据库连接信息
DATABASE_DSN=your_username:your_password@tcp(your_host:3306)/your_database?charset=utf8mb4&parseTime=True&loc=Local

# JWT 密钥（请使用随机字符串）
JWT_SECRET=your-random-secret-key-here
```

### 3. 生成 TV 端配置

```bash
cd tv
npm run config:generate
```

这会生成 `tv/src/config/env.js` 文件。

**重要**：
- `env.js` 包含 API 地址配置，已加入 `.gitignore`
- 不会提交到代码仓库
- 团队成员需要各自运行此命令生成配置

## 敏感信息清单

### 必须修改的配置

| 配置项 | 说明 | 安全要求 |
|--------|------|----------|
| `TV_API_HOST` | TV 端 API 地址 | 填写实际服务器地址 |
| `DATABASE_DSN` | 数据库连接串 | 包含用户名和密码，必须保密 |
| `JWT_SECRET` | JWT 签名密钥 | 使用随机字符串，至少 32 位 |

### 可选修改的配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `APP_NAME` | 应用名称 | `sheet-music-manager` |
| `APP_ENV` | 运行环境 | `production` |
| `SERVER_PORT` | HTTP 端口 | `8080` |
| `GIN_MODE` | Gin 模式 | `release` |
| `LOG_LEVEL` | 日志级别 | `info` |

## 安全最佳实践

### 1. JWT 密钥生成

使用以下命令生成随机密钥：

```bash
# Linux/Mac
openssl rand -base64 32

# 或使用 Node.js
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

### 2. 数据库密码

- 使用强密码（至少 12 位，包含大小写字母、数字、特殊字符）
- 定期更换密码
- 不要在多个环境使用相同密码

### 3. 文件权限

设置 `.env` 文件权限，仅所有者可读写：

```bash
chmod 600 .env
```

### 4. 备份配置

将 `.env` 文件安全备份到：
- 密码管理器（推荐）
- 加密的云存储
- 离线加密存储设备

⚠️ **不要将 `.env` 文件提交到任何版本控制系统**

## 团队协作

### 新成员加入

1. 提供 `.env.example` 文件作为模板
2. 通过安全渠道（加密通讯工具）分享实际配置
3. 指导新成员创建本地 `.env` 文件

### 配置更新

当配置发生变更时：
1. 更新 `.env.example` 文件（不包含实际值）
2. 通知团队成员更新本地 `.env` 文件
3. 提交 `.env.example` 到 Git

## 部署环境

### 开发环境

- 使用测试数据库
- 使用开发用 JWT 密钥
- 可以使用 localhost 地址

### 生产环境

- 使用生产数据库
- 使用强随机 JWT 密钥
- 使用实际服务器地址
- 启用 HTTPS（推荐）

### 环境隔离

建议为不同环境创建不同的配置文件：

```bash
.env.development  # 开发环境
.env.staging      # 测试环境
.env.production   # 生产环境
```

部署时复制对应文件为 `.env`：

```bash
cp .env.production .env
```

## 泄露应对

如果 `.env` 文件不慎泄露：

### 立即行动

1. **更换所有密钥和密码**
   - 数据库密码
   - JWT 密钥
   - API 密钥

2. **检查访问日志**
   - 查看是否有异常访问
   - 检查数据库操作记录

3. **通知团队**
   - 告知所有成员安全事件
   - 要求所有人更新配置

### 预防措施

1. 定期审查 `.gitignore` 文件
2. 使用 `git status` 检查暂存文件
3. 配置 Git hooks 防止意外提交
4. 使用密钥管理服务（如 AWS Secrets Manager）

## 检查清单

部署前请确认：

- [ ] `.env` 文件已创建并填写实际配置
- [ ] JWT 密钥使用随机字符串（至少 32 位）
- [ ] 数据库密码足够强
- [ ] `.env` 文件权限设置为 600
- [ ] `.env` 文件已备份到安全位置
- [ ] `.gitignore` 包含 `.env` 规则
- [ ] 代码中没有硬编码的敏感信息
- [ ] TV 端已运行 `npm run config:generate`

## 常见问题

**Q: 为什么不直接在代码中配置？**

A: 代码会提交到公开仓库，任何人都能看到。使用 `.env` 文件可以将敏感信息与代码分离。

**Q: `.env.example` 和 `.env` 有什么区别？**

A: `.env.example` 是模板文件，包含占位符，可以安全提交到 Git。`.env` 包含实际配置，不能提交。

**Q: 如何在 Docker 中使用 `.env` 文件？**

A: 使用 `docker run --env-file .env` 或在 `docker-compose.yml` 中配置 `env_file: .env`。

**Q: CI/CD 环境如何配置？**

A: 使用 CI/CD 平台的环境变量功能（如 GitHub Secrets、GitLab CI Variables）。

## 相关资源

- [OWASP 配置管理指南](https://owasp.org/www-project-configuration-management/)
- [12-Factor App 配置原则](https://12factor.net/config)
- [密钥管理最佳实践](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html)
