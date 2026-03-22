# TV 端与 Manager 后端 API 整合文档

## 概述

本文档说明如何将 TV 端（UniApp）与 Manager 后端（Go）的乐谱管理接口进行整合，实现从远程服务器获取乐谱数据，替换原有的本地文件读取方式。

## 整合架构

```
┌─────────────────┐         HTTP API          ┌──────────────────┐
│   TV 端 (UniApp) │ ◄────────────────────────► │ Manager 后端 (Go) │
│                 │                            │                  │
│  - 列表页面      │  GET /api/sheets/external  │  - 乐谱管理       │
│  - 详情页面      │                            │  - 文件存储       │
│  - 本地缓存      │                            │  - MySQL 数据库   │
└─────────────────┘                            └──────────────────┘
```

## 新增文件

### 1. API 配置文件
**文件路径**: `/tv/src/config/api.js`

配置 API 基础地址和端点：
```javascript
// API 地址（从 .env 读取）
export const API_HOST = ENV_CONFIG.TV_API_HOST;

// API 端点
export const API_ENDPOINTS = {
  SHEETS_EXTERNAL: '/api/sheets/external',  // 获取乐谱列表（公开接口）
  // ... 其他端点
};
```

**重要**: API 地址配置在根目录 `.env` 文件中，不在代码中硬编码。

### 2. 乐谱 API 工具类
**文件路径**: `/tv/src/api/sheetApi.js`

提供三个核心方法：
- `fetchSheetList()`: 从远程获取乐谱列表
- `fetchSheetDetail(sheetId)`: 获取单个乐谱详情
- `getMergedSheetList(localSheets)`: 合并远程和本地乐谱列表

## 修改的文件

### 1. 列表页面
**文件路径**: `/tv/pages/score-list/index.vue`

**修改内容**:
- 导入 `getMergedSheetList` API
- 修改 `reloadBase()` 方法为异步方法
- 调用 API 获取远程乐谱并与本地乐谱合并

```javascript
import { getMergedSheetList } from '@/src/api/sheetApi';

async reloadBase() {
  const localSheets = getFlatImagesFromStatic();
  this.baseImages = await getMergedSheetList(localSheets);
  if (!this.orderIds.length) {
    this.orderIds = this.baseImages.map((x) => x.id);
  }
}
```

### 2. 详情页面
**文件路径**: `/tv/pages/score-preview/index.vue`

**修改内容**:
- 导入 `getMergedSheetList` API
- 修改 `onLoad()` 方法为异步方法
- 从 API 获取乐谱列表用于详情展示

```javascript
import { getMergedSheetList } from '@/src/api/sheetApi';

async onLoad(query) {
  const raw = uni.getStorageSync('sheet_flat_list');
  if (raw) {
    try { this.images = JSON.parse(raw); } catch (e) { this.images = []; }
  }
  if (!this.images.length) {
    const localSheets = getFlatImagesFromStatic();
    this.images = await getMergedSheetList(localSheets);
  }
  this.pageIndex = Math.max(0, Math.min(this.images.length - 1, Number((query && query.index) || 0)));
  this.initAudio();
}
```

## Manager 后端接口说明

### 获取乐谱列表（公开接口）
**端点**: `GET /api/sheets/external`

**说明**: 无需鉴权的公开接口，返回所有乐谱的简要信息

**响应格式**:
```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "title": "乐谱标题",
      "thumbUrl": "/public/uploads/thumb_20240315120000_example.jpg",
      "uploadTime": "2024-03-15T12:00:00Z"
    }
  ]
}
```

### 数据转换

TV 端将后端返回的数据转换为统一格式：
```javascript
{
  id: 'sheet-1',                                    // 转换后的 ID
  title: '乐谱标题',                                 // 原标题
  src: 'http://43.142.45.253:8080/public/...',     // 缩略图完整 URL
  fullUrl: 'http://43.142.45.253:8080/public/...',  // 原图完整 URL
  uploadTime: '2024-03-15T12:00:00Z',               // 上传时间
  type: 'remote'                                     // 标记为远程乐谱
}
```

## 数据流程

### 列表页加载流程
1. 用户打开列表页
2. 调用 `reloadBase()` 方法
3. 获取本地 `static/scores` 目录下的乐谱
4. 调用 `getMergedSheetList()` 获取远程乐谱
5. 合并远程和本地乐谱（远程优先）
6. 渲染列表展示

### 详情页加载流程
1. 用户从列表页点击某个乐谱
2. 列表页将完整乐谱列表保存到 `uni.storage`
3. 跳转到详情页并传递 `index` 参数
4. 详情页从 `storage` 读取乐谱列表
5. 如果 `storage` 为空，调用 API 重新获取
6. 根据 `index` 显示对应乐谱

## 容错机制

### 1. API 请求失败
- 如果远程 API 请求失败，自动降级到只显示本地乐谱
- 不影响用户正常使用本地乐谱功能

### 2. 网络超时
- 设置 10 秒超时时间
- 超时后返回空数组，使用本地乐谱

### 3. 数据格式异常
- 检查响应格式是否符合预期
- 格式异常时返回空数组，使用本地乐谱

## 配置说明

### 修改 API 地址
编辑根目录 `.env` 文件:
```bash
# 修改 API 地址
TV_API_HOST=http://your-server-ip:8080
```

然后重新生成配置:
```bash
cd tv
npm run config:generate
```

### 配置说明
- TV 端统一使用 `TV_API_HOST` 配置的地址
- 不区分开发和生产环境
- 所有敏感配置都在 `.env` 文件中，不提交到代码仓库

## 测试验证

### 1. 本地开发测试
```bash
# 启动 Manager 后端
cd manager
go run main.go

# 启动 TV 端 H5 开发服务器
cd tv
npm run dev:h5
```

访问 `http://localhost:8080` 查看列表页，验证：
- 远程乐谱是否正常显示
- 本地乐谱是否正常显示
- 点击乐谱能否正常跳转到详情页

### 2. APP 打包测试
```bash
cd tv
# 打包 Android APP
npm run build:app-plus
```

在 Android TV 模拟器或真机上安装测试：
- 验证远程乐谱加载
- 验证网络异常时降级到本地乐谱
- 验证详情页图片显示

## 注意事项

1. **跨域问题**: H5 开发环境可能遇到跨域问题，需要在 Manager 后端配置 CORS
2. **图片路径**: Manager 后端返回的图片路径需要是可公开访问的 URL
3. **网络权限**: APP 需要在 `manifest.json` 中配置网络权限
4. **HTTPS**: 生产环境建议使用 HTTPS 协议

## 后续扩展

可以进一步扩展的功能：
- 实现乐谱上传功能（需要鉴权）
- 实现乐谱删除功能（需要鉴权）
- 实现乐谱排序功能（需要鉴权）
- 添加离线缓存机制
- 添加图片预加载优化

## 相关文件清单

### 新增文件
- `/tv/src/config/api.js` - API 配置
- `/tv/src/api/sheetApi.js` - 乐谱 API 工具类
- `/tv/API_INTEGRATION.md` - 本文档

### 修改文件
- `/tv/pages/score-list/index.vue` - 列表页面
- `/tv/pages/score-preview/index.vue` - 详情页面

### 保留文件（向后兼容）
- `/tv/src/data/flatImages.js` - 本地乐谱读取（作为降级方案）
- `/tv/static/scores/` - 本地乐谱存储目录（已清空，所有乐谱从 API 获取）

### 配置文件（不提交到仓库）
- `/tv/src/config/env.js` - 环境配置（由 `npm run config:generate` 生成）
