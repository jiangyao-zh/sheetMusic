# 节拍谱屏 / TempoScore Screen

[中文](#中文) | [English](#english)

## 中文

这是一个给排练和演奏使用的乐谱管理项目，包含后台管理端和 TV 展示端。  
你可以上传乐谱图片、调整顺序、设置节拍（BPM），并同步给 TV 使用。

### 项目结构
- 后台服务与管理端：`manager`
- TV 端应用：`tv`
- 后台文档：`manager/README.md`
- TV 文档：`tv/README.md`

### 快速启动（后台）
```bash
cd manager
go mod tidy
go run main.go
```

访问地址：
- 登录页：`http://127.0.0.1:8080/web/login.html`
- 乐谱页：`http://127.0.0.1:8080/web/sheets.html`

## English

This repository is a sheet music system for rehearsal and performance, including:
- Admin backend/web manager
- TV client display app

You can upload sheet images, reorder them, set BPM, and sync data to TV.

### Project Structure
- Backend & Web manager: `manager`
- TV client app: `tv`
- Backend docs: `manager/README.md`
- TV docs: `tv/README.md`

### Quick Start (Backend)
```bash
cd manager
go mod tidy
go run main.go
```

URLs:
- Login: `http://127.0.0.1:8080/web/login.html`
- Sheets: `http://127.0.0.1:8080/web/sheets.html`
