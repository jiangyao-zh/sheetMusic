# TV 端乐谱与节拍器 / TV Sheet & Metronome

[中文](#中文) | [English](#english)

## 中文

TV 端用于大屏展示乐谱与节拍器，支持遥控器操作和手机扫码管理。

### 当前功能
- 列表页平铺显示 `static/scores` 下所有图片（不显示目录）
- 缩略图统一尺寸，支持焦点移动与进入预览
- 预览页左右切图，图片按屏幕居中完整显示
- 预览页节拍器红绿灯显示（四拍同一音高）
- 预览页确认键稳定暂停/继续（防重复触发）
- 列表页提供二维码，手机扫码进入管理台
- 手机管理台支持排序、上传并实时同步到大屏

### 与后台（manager）联动
TV 端建议读取后台公开接口，不走后台登录态。

- 接口：`GET /api/sheets/external`
- 字段：`id`、`title`、`thumbUrl`、`bpm`、`uploadTime`
- 建议：按后端排序结果展示，定时刷新或轮询同步

### TV 截图预留
- TV 首页（待补充）  
  `![TV-首页](docs/images/tv-home.png)`
- TV 列表页（待补充）  
  `![TV-列表](docs/images/tv-list.png)`
- TV 预览页（待补充）  
  `![TV-预览](docs/images/tv-preview.png)`

### 目录约定
将乐谱图片放在：
- `static/scores/*.jpg`
- `static/scores/<任意子目录>/*.jpg`

无需执行 `generate:sheets` 即可显示列表。

### 启动方式（H5 联调）
1) 启动 uni-app H5 服务  
2) 另开终端启动控制服务：

```bash
npm run control:server
```

默认端口：`9091`

### 手机扫码管理
1) 打开大屏列表页，显示二维码与管理链接  
2) 手机扫码（或直接访问管理链接）  
3) 在手机管理台可：
- 上移/下移调整顺序（实时同步）
- 选择图片上传（实时追加）

说明：上传图片当前为会话级生效（无数据库持久化）。

### 遥控器 / 键盘
列表页：
- `上/下/左/右`：焦点移动
- `确认`：进入预览

预览页：
- `左/右`：上一张/下一张
- `上/下`：BPM +1 / -1
- `确认`：节拍器开始/暂停
- `菜单`：打开节拍器设置面板
- `返回`：退出预览

## English

The TV app is designed for large-screen sheet display with metronome controls.  
It supports remote navigation and mobile QR management.

### Features
- Flat list of images under `static/scores` (no folder grouping)
- Uniform thumbnails with focus navigation and preview enter
- Left/right image switching in preview mode
- Metronome indicator in preview (4-beat same pitch)
- Stable pause/resume on OK key (anti-repeat)
- QR code on list page for mobile management
- Mobile management supports reorder/upload with real-time sync

### Integration with backend (manager)
Use public API without admin login state:

- Endpoint: `GET /api/sheets/external`
- Fields: `id`, `title`, `thumbUrl`, `bpm`, `uploadTime`
- Recommendation: render backend order and refresh periodically

### TV Screenshot Placeholders
- TV Home (TBD)  
  `![TV-Home](docs/images/tv-home.png)`
- TV List (TBD)  
  `![TV-List](docs/images/tv-list.png)`
- TV Preview (TBD)  
  `![TV-Preview](docs/images/tv-preview.png)`

### Folder Convention
Put score images in:
- `static/scores/*.jpg`
- `static/scores/<any-subfolder>/*.jpg`

No need to run `generate:sheets` for list rendering.

### H5 Dev Run
1) Start uni-app H5 dev service  
2) Start control server in another terminal:

```bash
npm run control:server
```

Default port: `9091`

### Mobile QR Management
1) Open TV list page and get QR + manage link  
2) Scan with mobile (or open the link directly)  
3) In mobile manager:
- Move up/down for reorder (real-time sync)
- Upload images (real-time append)

Note: upload effect is session-level only (not persisted in DB).

### Remote / Keyboard
List page:
- `Up/Down/Left/Right`: move focus
- `OK`: enter preview

Preview page:
- `Left/Right`: previous/next image
- `Up/Down`: BPM +1 / -1
- `OK`: start/pause metronome
- `Menu`: open metronome settings
- `Back`: exit preview
