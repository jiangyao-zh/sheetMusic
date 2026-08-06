# 小提琴智能音准检测 App（MVP）

基于 Android 手机麦克风的小提琴单音音准检测应用。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | uni-app + Vue3 + TypeScript + Pinia + Vite |
| 原生 | Kotlin + AudioRecord |
| 算法 | YIN 基频检测 |
| 打包 | Android 离线 SDK |

## 目录结构

```
tuner/
├── app/                 # uni-app CLI 前端
├── android/             # Android Studio 离线打包工程
│   ├── app/             # 壳工程
│   └── uniplugin_pitch/ # 音高检测原生插件
└── docs/
    ├── ALGORITHM.md     # 音频算法说明
    └── BUILD_ANDROID.md # 离线打包与真机调试
```

## 功能（MVP）

1. 实时录音（44100Hz / PCM16 / MONO / 4096 samples）
2. YIN 算法提取单音基频
3. 频率 → MIDI → 音符名
4. cent 偏差与评分
5. 实时 UI 展示

暂不实现：多音/和弦、乐谱识别、MusicXML、AI 评价、TV 投屏。

## 快速开始

### 前端 H5 联调（Mock 数据）

```bash
cd tuner
npm --prefix app install
npm run dev:h5
```

浏览器打开后点击「开始检测」，会使用 `MockPitchProvider` 模拟音高数据，便于调试 UI。

### 算法回归（不依赖 Android SDK）

```bash
cd tuner
npm run verify:yin
```

### 打包 APK

```bash
cd tuner
npm run package:apk
```

会构建前端、同步 www、跑 YIN 回归；若本机已配置 Android SDK，则继续 `assembleDebug` 输出 `tuner/dist/tuner-debug.apk`。

完整离线 SDK / AppKey 步骤见 [docs/BUILD_ANDROID.md](docs/BUILD_ANDROID.md)。

需要：

- DCloud 离线打包 AppKey（加载 uni 页面时）
- Android 签名证书（正式包）
- Android Studio + 离线 SDK

无 SDK 时仍可用 `android` 工程中的 `DebugActivity` 验证：

1. Android Studio 打开 `tuner/android`，Run 到模拟器/真机  
2. 先点 **「模拟 A4」**：不需麦克风，直接喂合成正弦波，确认算法与 UI  
3. 再点 **「麦克风检测」**：看界面上的 `RMS`；一直 `no_signal` 且 RMS≈0 说明没采到声  

模拟器收音：Extended Controls（`…`）→ Microphone → 打开 *Virtual microphone uses host audio input*。收音不稳时优先用真机。

## 插件 JS 接口

```ts
const plugin = uni.requireNativePlugin('PitchDetector')

plugin.start({ sampleRate: 44100, bufferSize: 4096, a4: 440 }, (data) => {
  // { frequency, confidence, note, midi, cent, score, status }
})

plugin.setTargetNote('A4') // 可选，预留给 MusicXML 目标音
plugin.stop()
```

## 文档

- [算法说明](docs/ALGORITHM.md)
- [Android 打包](docs/BUILD_ANDROID.md)
