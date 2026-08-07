# TV 内嵌音准中继（Android）

手机直连 TV IP，协议与 `../tools/control-server.mjs` 的 `/ws/pitch` 对齐。

## 模块

| 模块 | 说明 |
|---|---|
| `uniplugin_pitch_relay` | `PitchRelayServer` + uni `PitchRelayModule` |
| `app` | `RelayHostActivity`：无 DCloud SDK 时的独立中继调试 APK |

## 编译调试 APK

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 18)
cd tv/android
./gradlew :app:assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

装到 TV 后会显示局域网 IP / 会话，手机填该 IP 即可联调。

## 集成到乐谱 uni-app

1. 将 `uniplugin_pitch_relay` 编成 aar，放入 HBuilderX `nativeplugins/PitchRelay/android/`  
   或并入离线打包壳并注册 `com.sheetmusic.pitchrelay.plugin.PitchRelayModule`
2. `manifest.json` 已声明 `nativePlugins.PitchRelay`
3. 前端 `pitchRelay.start()` → TV 页订阅 `127.0.0.1`，界面展示 `lanIp` 给手机

## 协议

```
ws://{tvLanIp}:9091/ws/pitch?session={id}&role=phone|tv
```

phone 发送 `{ type:'pitch', result:{...} }`，同 session 的 tv 接收。
