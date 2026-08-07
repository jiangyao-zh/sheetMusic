# PitchRelay

TV 端原生音准 WebSocket 中继。监听 `0.0.0.0:9091`，协议与 `tools/control-server.mjs` 的 `/ws/pitch` 一致。

## 集成

1. 源码在 `tv/android/uniplugin_pitch_relay`
2. 离线打包：将模块编成 aar 放入本目录 `android/`，或把模块并入离线壳工程
3. `manifest.json` → `app-plus.nativePlugins.PitchRelay` 设为开启
4. JS：`uni.requireNativePlugin('PitchRelay')`

## API

```js
const relay = uni.requireNativePlugin('PitchRelay')
relay.start({ port: 9091 }, (st) => console.log(st))
// { running, port, lanIp, error, rooms }
relay.getLanIp((r) => console.log(r.lanIp))
relay.stop()
```
