# Android 工程说明

## 模块

| 模块 | 说明 |
|------|------|
| `app` | 壳工程。无离线 SDK 时启动 `DebugActivity` 可真机验证算法；集成 SDK 后改为 `PandoraEntry` 加载 uni-app |
| `uniplugin_pitch` | 音高检测原生插件（AudioRecord + YIN + 评分） |

## 无 SDK 快速验证算法

```bash
# 纯 Node，不依赖 Android SDK
node ../scripts/verify-yin.mjs

# 有 Android SDK 时运行 JUnit
./gradlew :uniplugin_pitch:test
```

## 有 SDK 时打 APK

1. 安装 Android Studio，配置 `local.properties`（可参考 `local.properties.example`）
2. 下载 DCloud 离线 SDK，将 aar/jar 放入 `app/libs/`
3. 申请离线 AppKey，写入 `AndroidManifest.xml` 的 `dcloud_appkey`
4. 构建前端并同步：

```bash
cd ../app && npm install && npm run build:app
../scripts/sync-www.sh
```

5. Android Studio 打开本目录 → Run / Generate Signed APK

详见 [../docs/BUILD_ANDROID.md](../docs/BUILD_ANDROID.md)。
