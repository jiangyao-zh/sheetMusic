# Android 离线打包与真机调试

## 前置条件

1. 安装 [Android Studio](https://developer.android.google.cn/studio)
2. 下载 [DCloud Android 离线 SDK](https://nativesupport.dcloud.net.cn/AppDocs/download/android.html)
3. 在 [DCloud 开发者中心](https://dev.dcloud.net.cn/) 申请离线打包 AppKey  
   （需要：appid、Android 包名、签名证书 SHA1）
4. 准备 Android 签名证书（keystore）

## 工程说明

```
tuner/android/
├── app/                 # 壳工程（集成离线 SDK + 前端 www 资源）
├── uniplugin_pitch/     # 音高检测原生插件（Kotlin）
├── settings.gradle
└── build.gradle
```

离线 SDK 本体体积较大，默认放在 `tuner/android/SDK/`，已被 gitignore，不入库。

## 接入离线 SDK

1. 解压 DCloud Android 离线 SDK
2. 将 `SDK/libs/*.aar`、`SDK/libs/*.jar` 拷贝到 `tuner/android/app/libs/`
3. 参考 SDK 内 `HBuilder-Integrate-AS` 或 `UniPlugin-Hello-AS` 补齐依赖与 `dcloud_control.xml`
4. 在 `AndroidManifest.xml` 中填入离线 AppKey：

```xml
<meta-data
    android:name="dcloud_appkey"
    android:value="你的离线AppKey" />
```

5. 包名默认：`com.sheetmusic.tuner`  
   appid 默认：`__UNI__PITCH01`（需与 `dcloud_control.xml`、前端 `manifest.json` 一致）

## 一键打包脚本

```bash
cd tuner
npm run package:apk
```

该脚本会：构建 uni-app → 同步 www → 跑 YIN 回归 →（若有 SDK）`assembleDebug`。

## 导入前端资源（手动）

```bash
cd tuner/app
npm install
npm run build:app
cd ..
npm run sync:www
```

产物同步到：

```
tuner/android/app/src/main/assets/apps/__UNI__PITCH01/www/
```

并确认 `assets/data/dcloud_control.xml` 中 appid 一致。

## 无离线 SDK 时的真机验证

当前壳工程默认启动 `DebugActivity`，可直接驱动 `AudioRecorder + PitchAnalyzer`，用于验证麦克风与 YIN，无需 DCloud AppKey。

集成离线 SDK 后，将启动 Activity 换成 SDK 的 `PandoraEntry`，即可加载 uni-app 页面与 `PitchDetector` 插件。

## 插件注册

在 `assets/dcloud_uniplugins.json`（或工程等价配置）中注册：

```json
{
  "nativePlugins": [
    {
      "plugins": [
        {
          "type": "module",
          "name": "PitchDetector",
          "class": "com.sheetmusic.pitch.plugin.PitchDetectorModule"
        }
      ]
    }
  ]
}
```

## 权限

必须声明并运行时申请：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

前端在调用 `start()` 前应先走 `uni.authorize` / 原生权限请求。

## 打包 APK

1. Android Studio 打开 `tuner/android`
2. 同步 Gradle，确认 `uniplugin_pitch` 被 `app` 依赖
3. `Build → Generate Signed Bundle / APK`，选择 release 证书
4. 安装到 Android 8.0+ 真机

## 算法单测（不依赖离线 SDK）

```bash
cd tuner/android
./gradlew :uniplugin_pitch:test
```

使用合成正弦波验证 YIN 在小提琴音域（约 196–2637Hz）误差 < 1 cent。

## 常见问题

### 「App离线SDK不支持Kotlin」

官方文档提示的是「不提供 Kotlin 示例与技术支持」。本工程算法层为 Kotlin；若 `UniModule` 反射异常，可将 `PitchDetectorModule` 改为 Java 薄壳，算法类保持 Kotlin。

### H5 能跑、真机无插件

H5 走 `MockPitchProvider`；真机必须打自定义基座/离线包后，`uni.requireNativePlugin('PitchDetector')` 才可用。

### 检测无声或一直 no_signal

检查麦克风权限、设备是否静音、RMS 能量阈值是否过高、是否对着乐器发声。
