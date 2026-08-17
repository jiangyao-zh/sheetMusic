# 小提琴实时音准检测：大跨度换音导致目标音符丢失问题修复方案

## 1. 问题背景

当前 Android 小提琴实时音准检测功能存在一个明显问题：

当演奏者连续演奏：

```text
A4 → E5
```

手机端检测结果可能表现为：

```text
绿 A4
↓
黄 A4
↓
红 A4
↓
C#5
↓
D#5
```

最终没有正确显示：

```text
E5
```

该问题发生在实时收音、YIN 音高检测、PitchTracker、AttackStabilizer、NoteDisplayLock 等处理链路中。

---

# 2. 核心问题判断

目前初步判断：

> 系统可能已经通过 YIN 检测到了接近 E5 的原始频率，但后续 PitchTracker / AttackStabilizer 对大跨度音高跳变进行了过强平滑，导致 A4 → E5 被人为处理成一个缓慢的连续滑音轨迹。

例如：

```text
A4 = 440 Hz
E5 ≈ 659.26 Hz
```

两者相差：

```text
700 cents
```

如果使用类似：

```text
smoothed = previous * 0.7 + current * 0.3
```

则：

```text
440 → 506 → 552 → 584 → 607 → 623 → 634 → ...
```

系统实际上人为生成了一条：

```text
A4
↓
B4
↓
C5
↓
C#5
↓
D5
↓
D#5
↓
E5
```

的频率轨迹。

这条轨迹并不是实际演奏产生的真实连续滑音，而是平滑算法造成的。

---

# 3. 重要原则

## 3.1 平滑不能阻止合法音符跳变

小提琴演奏中，以下变化都是合法的：

```text
A4 → B4
A4 → C5
A4 → E5
D5 → A5
```

因此不能简单使用：

```text
if abs(newPitch - oldPitch) > 80 cents:
    reject
```

或者：

```text
if jump > threshold:
    heavilySmooth()
```

将大跨度音高变化全部当成异常噪声。

---

# 4. 必须先确认问题到底发生在哪一层

在修改算法之前，必须增加 Debug 数据记录。

至少记录以下字段：

```text
timestamp
rawYinHz
yinConfidence
smoothedHz
lockedHz
rawNote
candidateNote
lockedNote
candidateDuration
```

建议输出类似：

```text
Time     RawHz   Conf   SmoothHz   LockedNote   Candidate
----------------------------------------------------------
0ms      440     0.94   440        A4           A4
20ms     450     0.93   443        A4           A4
40ms     520     0.91   466        A4           C5
60ms     610     0.92   510        A4           D5
80ms     658     0.95   554        A4           E5
100ms    659     0.94   586        D#5          E5
120ms    659     0.95   607        D#5          E5
```

## 4.1 判断标准

### 情况 A：YIN 已经检测到 E5

如果：

```text
rawYinHz ≈ 659 Hz
confidence 较高
```

但：

```text
smoothedHz
lockedHz
```

仍然停留在 D#5 或更低。

则确认：

> 问题主要发生在 PitchTracker / AttackStabilizer / NoteDisplayLock。

### 情况 B：YIN 本身没有检测到 E5

如果：

```text
rawYinHz
```

本身就无法稳定达到 E5，则不能直接归因于平滑。

继续检查：

- YIN Window Size
- Hop Size
- Sample Rate
- YIN Threshold
- Confidence
- 音频 Buffer
- 手机麦克风 AGC
- 噪声
- 小提琴泛音
- 基频错误检测
- 半频 / 倍频错误
- 快速换音导致的窗口混合

---

# 5. 当前算法需要重点检查的问题

## 5.1 PitchTracker 过度平滑

如果存在：

```text
newValue = oldValue * 0.7 + measuredValue * 0.3
```

则需要重点修改。

这个算法会让大跨度换音变成渐近逼近：

```text
440 → 659
```

永远需要很多帧才能接近目标。

---

## 5.2 AttackStabilizer 二次平滑

如果存在类似：

```text
locked = locked * 0.72 + smoothed * 0.28
```

需要重点评估甚至取消。

当前实际上可能形成：

```text
YIN
 ↓
第一次低通
 ↓
第二次低通
 ↓
Note Lock
```

多个低通叠加会严重增加延迟。

对于实时小提琴音准检测，这会造成：

```text
真实音符已经改变
        ↓
算法仍然认为旧音符存在
```

---

# 6. 推荐的整体架构

不要继续使用：

```text
YIN
 ↓
强平滑
 ↓
二次平滑
 ↓
音名锁定
 ↓
评分
```

改成：

```text
Microphone
    ↓
PCM Audio
    ↓
YIN
    ↓
Raw Pitch + Confidence
    ↓
Pitch Validation
    ↓
Note State Machine
    ↓
┌───────────────┬────────────────┐
│               │                │
↓               ↓                ↓
UI Pitch     Note Display     Scoring Pitch
```

核心思想：

```text
YIN
```

负责：

> 当前声音是什么频率？

```text
Pitch Tracker
```

负责：

> 当前频率是否稳定？

```text
Note State Machine
```

负责：

> 当前演奏的是哪个音？

```text
UI
```

负责：

> 如何把结果显示出来？

```text
Scoring
```

负责：

> 当前音符是否准确？

不要让 UI 平滑结果直接决定音符切换。

---

# 7. 推荐的 Note State Machine

建立明确的音符状态机。

```text
STABLE
  │
  │ 检测到不同音符
  ↓
CANDIDATE
  │
  ├── 持续满足条件 ──→ NEW NOTE
  │
  └── 检测失败 ─────→ STABLE
```

例如当前：

```text
Locked Note = A4
```

检测到：

```text
E5 confidence = 0.92
```

不要要求经过：

```text
B4
C5
C#5
D5
D#5
```

而应该：

```text
A4
 ↓
E5 Candidate
 ↓
连续稳定 30~50ms
 ↓
E5 Confirmed
 ↓
A4 → E5
```

---

# 8. 大跨度跳变处理规则

不要再把“大跳变”直接当异常。

建议按照音高变化幅度分类。

## 8.1 小变化

```text
< 100 cents
```

可以正常进行轻度平滑。

---

## 8.2 中等变化

```text
100 ~ 300 cents
```

适度平滑，并进行候选音确认。

---

## 8.3 大跨度变化

```text
> 300 cents
```

不要强制平滑。

如果满足：

```text
confidence >= 0.80~0.85
```

并且：

```text
目标音附近稳定持续 30~50ms
```

则允许直接切换。

例如：

```text
A4 → E5
```

应该允许：

```text
A4
 ↓
E5 Candidate
 ↓
E5
```

而不是：

```text
A4
 ↓
C#5
 ↓
D#5
 ↓
E5
```

---

# 9. NoteDisplayLock 修改要求

当前如果使用：

```text
同一音名连续 2 帧才切换
```

不要简单继续增加帧数。

建议从：

```text
Frame Count
```

改成：

```text
Time Based Confirmation
```

例如：

```text
candidateNote = E5

candidateDuration >= 30~50ms
+
confidence >= threshold
+
pitch 位于 E5 附近
```

则确认 E5。

不要要求：

```text
E5 必须连续 N 帧
```

因为不同 Android 设备：

- Audio Buffer
- Sample Rate
- Hop Size
- YIN Window
- CPU 性能

可能导致实际帧时间不同。

---

# 10. Candidate 音符判定

建议增加：

```text
currentNote
candidateNote
candidateStartTime
candidateDuration
```

伪代码：

```text
if rawPitch invalid:
    return

detectedNote = frequencyToNote(rawPitch)

if detectedNote == currentNote:
    clearCandidate()
    updateCurrentPitch()
    return

if detectedNote != candidateNote:
    candidateNote = detectedNote
    candidateStartTime = now
    return

candidateDuration = now - candidateStartTime

if confidence >= CONFIDENCE_THRESHOLD
   and candidateDuration >= NOTE_CONFIRM_DURATION:

    currentNote = candidateNote
    clearCandidate()
```

---

# 11. 高置信度大跳变优化

增加特殊处理：

```text
if pitchJump > LARGE_JUMP_THRESHOLD
   and confidence >= HIGH_CONFIDENCE_THRESHOLD
   and targetNoteStable:

    immediatelySwitchToTargetNote()
```

例如：

```text
A4 = 440Hz
E5 = 659Hz
```

如果 YIN 连续得到：

```text
658
659
660
```

且：

```text
confidence > 0.9
```

应该认为：

> 演奏者已经切换到 E5。

不应该继续让旧 A4 的平滑状态拖住 E5。

---

# 12. UI 平滑和音符识别必须分离

建议：

```text
Raw YIN
   ├────────────→ Note Detection
   │
   ├────────────→ Jump Detection
   │
   ↓
Light Smoothing
   ↓
UI Pitch Indicator
```

也就是说：

## 音符切换

优先使用：

```text
Raw Pitch + Confidence
```

## UI 指针

可以使用：

```text
Lightly Smoothed Pitch
```

## 音准评分

使用：

```text
Validated / Stable Pitch
```

不能使用：

```text
Heavily Smoothed Pitch
```

作为唯一评分依据。

---

# 13. 不要过度平滑

当前类似：

```text
0.7 / 0.3
0.72 / 0.28
```

的连续平滑需要重点检查。

第一阶段建议：

```text
降低平滑强度
```

或者：

```text
取消二次平滑
```

但不要直接确定最终参数。

参数需要结合实际设备测试。

建议初始测试范围：

```text
EMA alpha：0.6 ~ 0.8
```

或者采用：

```text
Median Filter
+
轻度 EMA
```

但最终必须通过真实小提琴录音测试确定。

---

# 14. 小提琴特有问题：滑音

不能简单地认为：

```text
所有大跨度变化都应该立即切换
```

因为小提琴存在：

```text
Glissando / 滑音
```

例如：

```text
A4
 ↓
A#4
 ↓
B4
 ↓
C5
 ↓
C#5
 ↓
D5
 ↓
D#5
 ↓
E5
```

这种情况下不能直接认为：

```text
A4 → E5
```

---

# 15. Jump 和 Glissando 的区分

第一阶段不需要实现复杂模型，但架构必须保留扩展能力。

可以根据：

## Jump

特点：

```text
旧音符
 ↓
短时间内
 ↓
新音符
```

并且：

```text
新音符 confidence 高
```

例如：

```text
A4
E5
E5
E5
```

可以快速切换。

---

## Glissando

特点：

```text
A4
A#4
B4
C5
C#5
D5
D#5
E5
```

频率持续单向变化。

后续可以通过：

```text
pitch velocity
pitch direction
pitch continuity
time duration
```

判断是否属于滑音。

第一阶段不要为了处理滑音而牺牲普通音符跳变。

---

# 16. YIN Window Size 必须检查

如果 YIN Window 太长，快速换音时一个窗口可能同时包含：

```text
A4
+
E5
```

这样 YIN 原始输出本身就可能不稳定。

必须检查：

```text
Sample Rate
Window Size
Hop Size
YIN Threshold
Confidence Threshold
```

例如 44.1kHz：

```text
1024 samples ≈ 23ms
2048 samples ≈ 46ms
4096 samples ≈ 93ms
8192 samples ≈ 186ms
```

窗口越长：

```text
稳定性 ↑
响应速度 ↓
```

窗口越短：

```text
响应速度 ↑
频率稳定性 ↓
```

需要针对小提琴实时演奏进行平衡。

---

# 17. 第一阶段不要修改过多参数

优先按照以下顺序执行。

## P0：增加 Debug

必须能看到：

```text
rawYinHz
confidence
smoothedHz
lockedHz
candidateNote
candidateDuration
```

---

## P1：定位问题

确认：

```text
YIN 是否已经检测到 E5
```

如果 YIN 已经是：

```text
≈659Hz
```

则不要继续修改 YIN。

---

## P2：移除大跳变强制平滑

禁止：

```text
大于某个 cents 就认为是异常
```

禁止：

```text
大跳变必须缓慢逼近
```

---

## P3：降低二次平滑

重点检查：

```text
AttackStabilizer
```

必要时先临时关闭，验证：

```text
A4 → E5
```

是否恢复正常。

---

## P4：实现 Candidate State

增加：

```text
currentNote
candidateNote
candidateStartTime
candidateDuration
```

---

## P5：改成时间确认

初始：

```text
30~50ms
```

进行测试。

---

## P6：增加高置信度大跳变

允许：

```text
A4 → E5
```

直接建立 E5 Candidate。

---

## P7：优化 UI

让：

```text
音符显示
```

与：

```text
音高指针
```

分别处理。

---

# 18. 验收标准

修改后必须至少测试以下场景。

## Case 1：A4 → E5

输入：

```text
A4 → E5
```

预期：

```text
A4 → E5
```

不能出现：

```text
A4 → C#5 → D#5
```

---

## Case 2：A4 → B4

预期：

```text
A4 → B4
```

---

## Case 3：A4 → C5

预期：

```text
A4 → C5
```

---

## Case 4：A4 → E5 快速换音

预期：

```text
A4 → E5
```

E5 应在合理的 30~100ms 范围内出现。

---

## Case 5：A4 → E5 滑音

预期允许观察到：

```text
A4
A#4
B4
C5
...
E5
```

不能强制所有滑音都变成：

```text
A4 → E5
```

---

## Case 6：A4 持续音

预期：

```text
A4
A4
A4
A4
```

不能频繁跳音。

---

## Case 7：小提琴颤音

例如：

```text
A4
A4+
A4
A4-
A4
```

预期：

> 音符仍保持 A4，不应因为频率小幅波动导致音符频繁切换。

---

## Case 8：低置信度噪声

例如：

```text
低音量
背景噪声
琴弓摩擦
呼吸声
```

预期：

> 不应该因为偶发错误频率立即切换音符。

---

# 19. 不允许的修改方式

不要使用以下方式简单解决：

```text
❌ 把所有平滑全部删除
❌ 把 Note Lock 直接删除
❌ 把确认帧数改成 1
❌ 把 confidence threshold 降得很低
❌ 遇到大跳变直接认为是新音
❌ 仅仅把 0.7/0.3 改成其他数字
❌ 通过增加 UI 延迟掩盖问题
```

目标不是：

> 让 E5 更容易显示。

而是：

> 建立一个适合小提琴实时演奏的“音高检测 + 音符识别 + 稳定性判断”机制。

---

# 20. 最终目标架构

最终建议形成：

```text
                    Microphone
                        │
                        ↓
                    PCM Audio
                        │
                        ↓
                       YIN
                        │
             ┌──────────┴──────────┐
             ↓                     ↓
        Raw Frequency          Confidence
             │                     │
             └──────────┬──────────┘
                        ↓
                 Pitch Validation
                        │
                        ↓
               Note State Machine
                        │
             ┌──────────┼──────────┐
             ↓          ↓          ↓
           Jump      Glissando   Stable Note
             │          │          │
             └──────────┼──────────┘
                        ↓
                 Current Note
                        │
             ┌──────────┴──────────┐
             ↓                     ↓
        UI Pitch Display       Pitch Scoring
             │                     │
             ↓                     ↓
        Light Smoothing       Stable Pitch
```

---

# 21. 核心结论

当前：

```text
A4 → 黄A4 → 红A4 → C#5 → D#5 → 没有E5
```

最可能的原因不是简单的：

```text
YIN 没检测到 E5
```

而是：

```text
YIN
 ↓
大跨度跳变
 ↓
强制平滑
 ↓
二次平滑
 ↓
音符锁定
 ↓
E5 被延迟
 ↓
目标音符持续时间不足
 ↓
最终没有锁定 E5
```

核心修复方向：

```text
1. 保留 YIN 原始输出
2. 增加 confidence
3. 不要把大跨度跳变当异常
4. 大跨度高置信度变化允许快速建立 Candidate
5. 使用时间而不是简单帧数确认新音符
6. UI 平滑与音符识别分离
7. 降低或取消二次强平滑
8. 后续再处理 Jump / Glissando 区分
```

最终原则：

> **平滑用于消除测量抖动，而不是阻止合法的音符跳变。**
> 
> **音符识别必须基于原始音高 + 置信度 + 时间稳定性，而不能完全依赖经过多层平滑后的频率。**