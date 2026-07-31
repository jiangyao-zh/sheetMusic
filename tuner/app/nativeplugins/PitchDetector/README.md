# PitchDetector 本地原生插件

Android 模块源码位于仓库：

`tuner/android/uniplugin_pitch`

离线打包时将该 module 依赖进壳工程，并在 `dcloud_uniplugins.json` 中注册同名插件 `PitchDetector`。

JS 调用：

```js
const pitch = uni.requireNativePlugin('PitchDetector')
pitch.start({ a4: 440 }, (data) => console.log(data))
pitch.stop()
```
