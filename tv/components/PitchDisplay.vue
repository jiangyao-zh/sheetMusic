<template>
  <view class="pitch-block">
    <view class="pitch-header">
      <text class="pitch-title">挺准</text>
      <text class="pitch-link" :class="statusClass">{{ statusText }}</text>
    </view>

    <!-- 外圈/刻度/指针共用同一圆心与半径，保证同心圆弧 -->
    <view class="tuner-gauge">
      <view class="gauge-stage">
        <view class="ring-clip">
          <view class="gauge-ring"></view>
        </view>

        <view
          v-for="tick in ticks"
          :key="'t-' + tick"
          class="gauge-arm"
          :style="armStyle(tick, RADIUS)"
        >
          <view class="tick-mark" :class="{ major: tick % 10 === 0, center: tick === 0 }"></view>
        </view>

        <view
          v-for="mark in scaleMarks"
          :key="'s-' + mark.value"
          class="gauge-arm label-arm"
          :style="armStyle(mark.value, LABEL_RADIUS)"
        >
          <text
            class="scale-label"
            :style="{ transform: `rotate(${-mark.value * DEG_PER_CENT}deg)` }"
          >{{ mark.label }}</text>
        </view>

        <view
          class="gauge-arm needle-arm"
          :class="{ inactive: !active }"
          :style="armStyle(needleCent, NEEDLE_RADIUS)"
        >
          <view class="needle-line" :style="{ backgroundColor: accent }"></view>
        </view>

        <view class="gauge-pivot" :style="{ borderColor: accent }"></view>
      </view>
    </view>

    <view class="pitch-readout">
      <text class="readout-side">{{ freqText }}</text>
      <view class="note-group" :style="{ color: accent }">
        <text class="pitch-note">{{ noteName }}</text>
        <text class="pitch-octave">{{ noteOctave }}</text>
      </view>
      <text class="readout-side readout-cent" :style="{ color: accent }">{{ centText }}</text>
    </view>

    <view class="pitch-footer">
      <view class="activity-row">
        <image
          class="activity-sprite"
          :src="spriteSrc"
          mode="aspectFit"
        />
        <view class="activity-meta">
          <text class="activity-label">{{ activityLabel }}</text>
          <text class="activity-hint">{{ activityHint }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
const STALE_MS = 800
/** 半圆半径（px），适配音准模块高度约 165px（原 248 的 2/3） */
const RADIUS = 70
const LABEL_RADIUS = 84
const NEEDLE_RADIUS = 58
/** -50～+50 cent 映射到 -80°～+80° */
const DEG_PER_CENT = 1.6
/** 推送间隔阈值：更快用跑步，更慢用走路（手机检测约 66ms） */
const RUN_INTERVAL_MS = 140
const WALK_INTERVAL_MS = 320

/**
 * 静态资源路径（必须放在 tv/static/gif，打包进 APK 的 _www/static/gif）
 * HBuilderX / 离线壳同步见 scripts/sync-www.sh、ensure-pitch-sprites.sh
 */
const SPRITE = {
  stand: '/static/gif/spr_stand.png',
  walk: '/static/gif/spr_walking.gif',
  run: '/static/gif/spr_run.gif',
}

export default {
  name: 'PitchDisplay',
  data() {
    return {
      RADIUS,
      LABEL_RADIUS,
      NEEDLE_RADIUS,
      DEG_PER_CENT,
      prevMsgAt: 0,
      intervals: [],
      avgInterval: 9999,
    }
  },
  props: {
    result: {
      type: Object,
      default: null,
    },
    socketStatus: {
      type: String,
      default: 'idle',
    },
    lastMsgAt: {
      type: Number,
      default: 0,
    },
    now: {
      type: Number,
      default: 0,
    },
  },
  watch: {
    lastMsgAt(next) {
      if (!next) return
      if (this.prevMsgAt > 0) {
        const dt = next - this.prevMsgAt
        if (dt > 0 && dt < 2000) {
          this.intervals.push(dt)
          if (this.intervals.length > 10) this.intervals.shift()
          const sum = this.intervals.reduce((a, b) => a + b, 0)
          this.avgInterval = sum / this.intervals.length
        }
      }
      this.prevMsgAt = next
    },
    stale(isStale) {
      if (isStale) {
        this.intervals = []
        this.avgInterval = 9999
      }
    },
  },
  computed: {
    stale() {
      if (!this.lastMsgAt) return true
      return this.now - this.lastMsgAt > STALE_MS
    },
    statusClass() {
      if (this.socketStatus === 'connected' && !this.stale) return 'ok'
      if (this.socketStatus === 'connected' && this.stale) return 'warn'
      if (this.socketStatus === 'connecting') return 'warn'
      if (this.socketStatus === 'error') return 'bad'
      return 'idle'
    },
    statusText() {
      if (this.socketStatus === 'connecting') return '连接中'
      if (this.socketStatus === 'error') return '未连接'
      if (this.socketStatus === 'idle') return '未连接'
      if (this.socketStatus === 'connected' && !this.lastMsgAt) return '等待推送'
      if (this.stale) return '信号中断'
      return '已连接'
    },
    active() {
      return this.socketStatus === 'connected' && !this.stale && this.result &&
        (this.result.status === 'valid' || this.result.status === 'stabilizing')
    },
    ticks() {
      return Array.from({ length: 21 }, (_, i) => -50 + i * 5)
    },
    scaleMarks() {
      return [
        { value: -50, label: '-50' },
        { value: -25, label: '-25' },
        { value: 0, label: '0' },
        { value: 25, label: '+25' },
        { value: 50, label: '+50' },
      ]
    },
    noteText() {
      if (!this.active) return '--'
      return this.result.note || '--'
    },
    noteName() {
      const note = this.noteText
      if (note === '--') return note
      return note.replace(/-?\d+$/, '') || note
    },
    noteOctave() {
      if (!this.active) return ''
      const match = String(this.noteText).match(/(-?\d+)$/)
      return match ? match[1] : ''
    },
    needleCent() {
      if (!this.active) return 0
      if (this.result.status !== 'valid' && this.result.status !== 'stabilizing') return 0
      return Math.max(-50, Math.min(50, Number(this.result.cent) || 0))
    },
    freqText() {
      if (!this.active || !this.result.frequency) return '--'
      return `${Number(this.result.frequency).toFixed(1)} Hz`
    },
    centText() {
      if (!this.active || this.result.status === 'no_signal' || this.result.status === 'idle') return '--'
      const c = Number(this.result.cent) || 0
      const sign = c > 0 ? '+' : ''
      return `${sign}${c.toFixed(0)} cent`
    },
    /**
     * 推送活跃度：
     * stand 无音频/断流；walk 慢速推送；run 快速推送
     */
    activityMode() {
      if (this.socketStatus !== 'connected') return 'stand'
      if (this.stale || !this.result) return 'stand'
      const status = this.result.status
      if (status === 'no_signal' || status === 'idle') return 'stand'
      if (status === 'metronome_suppressed' || status === 'voice_rejected') return 'walk'
      if (status === 'too_low' || status === 'stabilizing') return 'walk'
      if (status === 'valid') {
        if (this.avgInterval <= RUN_INTERVAL_MS) return 'run'
        if (this.avgInterval <= WALK_INTERVAL_MS) return 'walk'
        return 'walk'
      }
      return 'walk'
    },
    spriteSrc() {
      return SPRITE[this.activityMode] || SPRITE.stand
    },
    activityLabel() {
      if (this.activityMode === 'run') return '推送较快'
      if (this.activityMode === 'walk') return '推送较慢'
      return '等待音频'
    },
    activityHint() {
      if (this.socketStatus !== 'connected') return '请在手机填写 TV IP 与会话'
      if (this.stale) return '超过 0.8s 未收到数据'
      if (!this.result) return '未检测到声音'
      if (this.result.status === 'metronome_suppressed') return '过滤节拍器干扰'
      if (this.result.status === 'voice_rejected') return '过滤环境人声'
      if (this.result.status === 'stabilizing') return '保持上一有效音高'
      if (this.result.status === 'no_signal' || this.result.status === 'idle') {
        return '未检测到声音'
      }
      if (this.result.status === 'too_low') return '信号偏弱'
      const n = Math.round(Number(this.result.score) || 0)
      const hz = this.avgInterval < 9000 ? `${Math.round(this.avgInterval)}ms/帧` : ''
      return hz ? `信号强度 ${n} · ${hz}` : `信号强度 ${n}`
    },
    accent() {
      if (!this.result) return '#8b93a7'
      if (this.result.status === 'stabilizing') return '#8b93a7'
      if (this.result.status !== 'valid') return '#8b93a7'
      const abs = Math.abs(Number(this.result.cent) || 0)
      if (abs <= 5) return '#3dd68c'
      if (abs <= 15) return '#7ee787'
      if (abs <= 30) return '#e3b341'
      return '#ff7b72'
    },
  },
  methods: {
    armStyle(cent, height) {
      const angle = Number(cent) * DEG_PER_CENT
      return {
        height: `${height}px`,
        transform: `translateX(-50%) rotate(${angle}deg)`,
      }
    },
  },
}
</script>

<style scoped>
.pitch-block {
  background: #141a23;
  border-radius: 8px;
  /* 底部 padding 更小：高度裁减从底部多余空间开始 */
  padding: 6px 6px 4px;
  margin-top: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  flex: none;
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(66, 239, 148, 0.18);
  box-sizing: border-box;
}

.pitch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0;
  gap: 6px;
  min-width: 0;
  flex-shrink: 0;
}

.pitch-title {
  color: #f5f7fa;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.pitch-link {
  font-size: 9px;
  color: #97a3b6;
  flex-shrink: 1;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pitch-link.ok {
  color: #3dd68c;
}

.pitch-link.warn {
  color: #e3b341;
}

.pitch-link.bad {
  color: #ff7b72;
}

.tuner-gauge {
  display: flex;
  justify-content: center;
  align-items: flex-end;
  padding: 4px 0 0;
  overflow: visible;
  width: 100%;
  flex: 0 0 auto;
}

/* 舞台宽=2R、高=R，圆环为正圆上半部分 → 与刻度同心 */
.gauge-stage {
  position: relative;
  width: 140px;
  height: 70px;
  overflow: visible;
  margin: 0 auto;
}

/* 单独裁切正圆上半部分，避免外圈被压扁 */
.ring-clip {
  position: absolute;
  left: 0;
  top: 0;
  width: 140px;
  height: 70px;
  overflow: hidden;
}

.gauge-ring {
  position: absolute;
  left: 0;
  top: 0;
  width: 140px;
  height: 140px;
  box-sizing: border-box;
  border: 5px solid #242c38;
  border-radius: 50%;
}

.gauge-arm {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 12px;
  transform-origin: 50% 100%;
  pointer-events: none;
}

.tick-mark {
  width: 1px;
  height: 7px;
  margin: 0 auto;
  background: #4d5868;
}

.tick-mark.major {
  width: 2px;
  height: 10px;
  background: #667386;
}

.tick-mark.center {
  width: 4px;
  height: 13px;
  background: #3dd68c;
}

.label-arm {
  z-index: 2;
}

.scale-label {
  display: block;
  width: 24px;
  margin-left: -6px;
  color: #697485;
  font-size: 9px;
  text-align: center;
  line-height: 1;
}

.needle-arm {
  z-index: 3;
  transition: transform 90ms linear;
}

.needle-arm.inactive {
  opacity: 0.35;
}

.needle-line {
  width: 2px;
  height: 100%;
  margin: 0 auto;
  border-radius: 2px;
  box-shadow: 0 0 7px currentColor;
}

.gauge-pivot {
  position: absolute;
  left: 50%;
  bottom: -4px;
  width: 10px;
  height: 10px;
  border: 2px solid;
  border-radius: 50%;
  background: #141a23;
  transform: translateX(-50%);
  z-index: 4;
}

.pitch-readout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: baseline;
  column-gap: 4px;
  margin-top: 4px;
  padding: 0 2px;
  min-width: 0;
  flex-shrink: 0;
}

.note-group {
  display: flex;
  align-items: baseline;
  justify-content: center;
  min-width: 0;
  flex-shrink: 0;
}

.pitch-note {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1;
}

.pitch-octave {
  margin-left: 2px;
  font-size: 13px;
  font-weight: 700;
}

.readout-side {
  color: #97a3b6;
  font-size: 10px;
  white-space: nowrap;
}

.readout-cent {
  text-align: right;
}

.pitch-footer {
  margin-top: 3px;
  flex-shrink: 0;
}

.activity-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  min-width: 0;
}

.activity-sprite {
  width: 68px;
  height: 44px;
  flex-shrink: 0;
  image-rendering: pixelated;
}

.activity-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.activity-label {
  color: #e8eef7;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.15;
}

.activity-hint {
  color: #8b95a7;
  font-size: 9px;
  font-weight: 400;
  line-height: 1.25;
  word-break: break-word;
}
</style>
