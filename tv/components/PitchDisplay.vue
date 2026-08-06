<template>
  <view class="pitch-block">
    <view class="pitch-header">
      <text class="pitch-title">手机音准</text>
      <text class="pitch-link" :class="statusClass">{{ statusText }}</text>
    </view>

    <view class="pitch-hero">
      <text class="pitch-note" :style="{ color: accent }">{{ noteText }}</text>
    </view>

    <view class="pitch-metrics">
      <view class="metric">
        <text class="metric-label">频率</text>
        <text class="metric-value">{{ freqText }}</text>
      </view>
      <view class="metric">
        <text class="metric-label">偏差</text>
        <text class="metric-value" :style="{ color: accent }">{{ centText }}</text>
      </view>
    </view>

    <view class="pitch-footer">
      <text class="score">{{ scoreText }}</text>
      <text class="hint">{{ hintText }}</text>
    </view>
  </view>
</template>

<script>
const STALE_MS = 800

export default {
  name: 'PitchDisplay',
  props: {
    result: {
      type: Object,
      default: null
    },
    socketStatus: {
      type: String,
      default: 'idle'
    },
    lastMsgAt: {
      type: Number,
      default: 0
    },
    now: {
      type: Number,
      default: 0
    }
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
      if (this.stale) return '信号中断'
      return '已连接'
    },
    active() {
      return this.socketStatus === 'connected' && !this.stale && this.result
    },
    noteText() {
      if (!this.active) return '--'
      return this.result.note || '--'
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
    scoreText() {
      if (!this.active) return '等待手机推送'
      if (this.result.status === 'no_signal') return '未检测到声音'
      if (this.result.status === 'too_low') return '置信度偏低'
      return `${Math.round(Number(this.result.score) || 0)} 分`
    },
    hintText() {
      if (this.socketStatus !== 'connected') return '请在手机填写 TV IP 与会话'
      if (this.stale) return '超过 0.8s 未收到数据'
      return this.result && this.result.status === 'valid' ? '实时同步中' : '检测中'
    },
    accent() {
      if (!this.active || this.result.status !== 'valid') return '#b8c6dc'
      const abs = Math.abs(Number(this.result.cent) || 0)
      if (abs <= 5) return '#42ef94'
      if (abs <= 15) return '#7ee787'
      if (abs <= 30) return '#e3b341'
      return '#ff7b72'
    }
  }
}
</script>

<style scoped>
.pitch-block {
  background: #141a23;
  border-radius: 10px;
  padding: 12px;
  margin-top: 30px;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(66, 239, 148, 0.18);
}

.pitch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.pitch-title {
  color: #f5f7fa;
  font-size: 14px;
  font-weight: 700;
}

.pitch-link {
  font-size: 11px;
  color: #97a3b6;
}

.pitch-link.ok {
  color: #42ef94;
}

.pitch-link.warn {
  color: #e3b341;
}

.pitch-link.bad {
  color: #ff7b72;
}

.pitch-hero {
  align-items: center;
  display: flex;
  justify-content: center;
  padding: 8px 0 6px;
}

.pitch-note {
  font-size: 42px;
  font-weight: 800;
  letter-spacing: 1px;
  line-height: 1;
  color: #f5f7fa;
}

.pitch-metrics {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.metric {
  flex: 1;
  background: rgba(45, 52, 66, 0.35);
  border-radius: 8px;
  padding: 8px;
}

.metric-label {
  display: block;
  color: #97a3b6;
  font-size: 10px;
  margin-bottom: 4px;
}

.metric-value {
  display: block;
  color: #f5f7fa;
  font-size: 14px;
  font-weight: 700;
}

.pitch-footer {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.score {
  color: #f1c64d;
  font-size: 13px;
  font-weight: 700;
}

.hint {
  color: #97a3b6;
  font-size: 10px;
}
</style>
