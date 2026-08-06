# 音频算法说明

## 总览

```
麦克风 → AudioRecord → PCM → 预处理 → YIN → 频率 → 音符 → cent/评分 → UI
```

## 1. 采集参数

| 参数 | 值 |
|------|-----|
| 采样率 | 44100 Hz |
| 位深 | PCM 16-bit |
| 声道 | MONO |
| 读缓冲 | 4096 samples（≈93ms，兼顾高音区精度） |
| 回调节流 | 约 15 次/秒（≈66ms，有新帧才回调） |

## 2. 预处理（AudioPreprocessor）

模块提供完整管线：

1. **Remove DC Offset**：减去均值，消除直流偏置
2. **Normalization**：按峰值归一化到 [-1, 1]
3. **Hanning Window**：`w[n] = 0.5 * (1 - cos(2πn/(N-1)))`，降低频谱泄漏

MVP 的 YIN 路径在检测前**仅做去直流**（汉宁窗会拉偏自相关类周期估计）。归一化与汉宁窗保留给后续频谱类扩展。

能量过低（RMS < 阈值）时直接返回 `no_signal`，避免静音误检。

## 3. YIN 基频检测（YinDetector）

参考：de Cheveigné & Kawahara, *YIN, a fundamental frequency estimator for speech and music*, JASA 2002。

### 3.1 Difference Function

\[
d(\tau) = \sum_{j=0}^{W-\tau-1} (x_j - x_{j+\tau})^2
\]

### 3.2 Cumulative Mean Normalized Difference (CMND)

\[
d'(\tau) =
\begin{cases}
1 & \tau = 0 \\
d(\tau) / \left( \frac{1}{\tau}\sum_{j=1}^{\tau} d(j) \right) & \tau > 0
\end{cases}
\]

### 3.3 Absolute Threshold

在小提琴音域对应的 tau 范围内（约 196Hz–2637Hz，即 G3–E7），寻找第一个低于阈值（默认 0.1）的局部极小值；若无，取全局最小。

### 3.4 Parabolic Interpolation

对 `tau-1, tau, tau+1` 三点做抛物线拟合，细化周期：

\[
\tau^* = \tau + \frac{1}{2}\cdot\frac{d'(\tau-1) - d'(\tau+1)}{d'(\tau-1) - 2d'(\tau) + d'(\tau+1)}
\]

### 3.5 Fractional Tau Refinement

高音区周期样本少时，在 \([\tau-0.75,\tau+0.75]\) 内对分数时延做线性插值差分搜索（步长 0.01），进一步压低误差。

### 3.6 频率与置信度

\[
f = \frac{f_s}{\tau^*},\quad
confidence = 1 - d'(\tau)
\]

## 4. 音符转换（NoteConverter）

MIDI 标准（A4 可配置，默认 440Hz）：

\[
midi = 69 + 12 \cdot \log_2(f / a4)
\]

音名由 `round(midi)` 映射到 `C, C#, D, ..., B` 与八度。

## 5. 音准评分（PitchScorer）

\[
cent = 1200 \cdot \log_2(f_{actual} / f_{target})
\]

未设置目标音时，目标取最近半音频率。

| \|cent\| | 评分 |
|----------|------|
| 0–5 | 100 |
| 5–15 | 95 |
| 15–30 | 85 |
| 30–50 | 70 |
| >50 | `max(0, 60 - (|cent|-50)/2)` |

## 6. 性能目标

| 指标 | 目标 |
|------|------|
| 端到端延迟 | < 100ms |
| 刷新率 | 10–20 Hz |
| 合成正弦波精度（196–2637Hz） | < 1 cent |

## 7. 扩展预留

- `setTargetNote(note)`：对接 MusicXML 目标音
- 多音/和弦：需另选算法（如多峰谱、NMF），不在本模块内硬编码
- TV 投屏：手机 `PitchSocketService` 经 `ws://host:9091/ws/pitch` 推送 `PitchResult` JSON；TV 详情页节拍器下方订阅显示（不传 PCM）
