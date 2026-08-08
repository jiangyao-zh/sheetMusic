<template>
  <view class="page">
    <view class="hero">
      <text class="title">小提琴音准检测</text>
      <text class="subtitle">单音 · YIN · TV 同步</text>
    </view>

    <ScoreCard :result="result" :color="color" :status-text="label" />

    <PitchMeter :cent="result.cent" :status="result.status" :color="color" />

    <view class="a4-row">
      <text class="a4-label">A4 参考</text>
      <view class="a4-btns">
        <view
          v-for="hz in a4Options"
          :key="hz"
          class="a4-btn"
          :class="{ active: a4 === hz }"
          @click="onSelectA4(hz)"
        >
          <text>{{ hz }}</text>
        </view>
      </view>
    </view>

    <view class="cast-card">
      <view class="cast-head">
        <text class="cast-title">投屏到 TV</text>
        <text class="cast-status" :class="socketStatus">{{ socketLabel }}</text>
      </view>
      <view class="field">
        <text class="field-label">TV IP</text>
        <input
          class="field-input"
          type="text"
          v-model="tvHost"
          placeholder="TV 上显示的局域网 IP"
          @blur="store.saveCastConfig()"
        />
      </view>
      <view class="field">
        <text class="field-label">会话</text>
        <input
          class="field-input"
          type="text"
          v-model="tvSession"
          placeholder="与 TV 一致的 4 位数字"
          @blur="store.saveCastConfig()"
        />
      </view>
      <view class="cast-actions">
        <button class="cast-btn" @click="store.connectTv()">连接 TV</button>
        <button class="cast-btn ghost" @click="store.disconnectTv()">断开</button>
      </view>
      <text v-if="socketDetail" class="cast-detail">{{ socketDetail }}</text>
    </view>

    <view v-if="error" class="error">
      <text>{{ error }}</text>
    </view>

    <view class="actions">
      <button class="btn" :class="{ stop: running }" @click="toggle">
        {{ running ? '停止检测' : '开始检测' }}
      </button>
    </view>

    <text class="hint">
      音准在手机端算完后，经 WebSocket 推送 note/frequency/cent；不传 PCM。端口默认 9091。
    </text>
  </view>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { storeToRefs } from 'pinia'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import PitchMeter from '@/components/PitchMeter.vue'
import ScoreCard from '@/components/ScoreCard.vue'
import { usePitchStore } from '@/stores/pitch'

const store = usePitchStore()
const {
  result,
  running,
  error,
  a4,
  label,
  color,
  tvHost,
  tvSession,
  socketStatus,
  socketDetail,
  socketLabel,
  castEnabled,
} = storeToRefs(store)
const a4Options = [440, 442, 443]

function setKeepScreenOn(on: boolean) {
  try {
    uni.setKeepScreenOn({ keepScreenOn: on })
  } catch {
    // ignore
  }
  // #ifdef APP-PLUS
  try {
    // plus.device.setWakelock：息屏后仍尽量保活（配合原生前台服务）
    // @ts-expect-error plus inject
    if (typeof plus !== 'undefined' && plus.device && plus.device.setWakelock) {
      // @ts-expect-error plus inject
      plus.device.setWakelock(on)
    }
  } catch {
    // ignore
  }
  // #endif
}

function onSelectA4(hz: number) {
  store.setA4(hz)
  if (running.value) {
    store.stop()
    store.start({ a4: hz })
  }
}

function toggle() {
  if (running.value) {
    store.stop()
  } else {
    store.start({ a4: a4.value })
  }
}

watch(
  [running, castEnabled, socketStatus],
  ([isRunning, castOn, sock]) => {
    const need = !!(isRunning || castOn || sock === 'connected' || sock === 'connecting')
    setKeepScreenOn(need)
  },
  { immediate: true },
)

onShow(() => {
  // 页面可见时默认亮屏，避免练琴时自动息屏
  setKeepScreenOn(true)
})

onHide(() => {
  // 若仍在检测/投屏，保持 wakelock；仅停止亮屏策略交给系统，原生 FGS 续跑
  if (!running.value && !castEnabled.value) {
    setKeepScreenOn(false)
  }
})

onUnload(() => {
  store.stop()
  store.disconnectTv()
  setKeepScreenOn(false)
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 40rpx 36rpx 80rpx;
  box-sizing: border-box;
  background: radial-gradient(ellipse at top, #1b2230 0%, #0f1115 55%);
}
.hero {
  margin-bottom: 36rpx;
}
.title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: #f3f5f7;
}
.subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8b93a7;
}
.a4-row {
  margin-top: 28rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.a4-label {
  color: #8b93a7;
  font-size: 26rpx;
}
.a4-btns {
  display: flex;
  gap: 12rpx;
}
.a4-btn {
  min-width: 88rpx;
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: #1a1f2b;
  border: 1px solid #2a3140;
  text-align: center;
  color: #c9d1d9;
  font-size: 24rpx;
}
.a4-btn.active {
  background: #243044;
  border-color: #3d8bfd;
  color: #79b8ff;
}
.cast-card {
  margin-top: 28rpx;
  background: #1a1f2b;
  border: 1px solid #2a3140;
  border-radius: 24rpx;
  padding: 24rpx;
}
.cast-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.cast-title {
  color: #f3f5f7;
  font-size: 28rpx;
  font-weight: 700;
}
.cast-status {
  font-size: 22rpx;
  color: #8b93a7;
}
.cast-status.connected {
  color: #3dd68c;
}
.cast-status.connecting,
.cast-status.error {
  color: #e3b341;
}
.field {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 12rpx;
}
.field-label {
  width: 110rpx;
  color: #8b93a7;
  font-size: 24rpx;
}
.field-input {
  flex: 1;
  background: #0f1115;
  border: 1px solid #2a3140;
  border-radius: 12rpx;
  color: #f3f5f7;
  font-size: 26rpx;
  padding: 12rpx 16rpx;
}
.cast-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}
.cast-btn {
  flex: 1;
  background: #3d8bfd;
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-size: 26rpx;
}
.cast-btn.ghost {
  background: #2a3140;
}
.cast-detail {
  display: block;
  margin-top: 12rpx;
  color: #6b7280;
  font-size: 20rpx;
  word-break: break-all;
}
.error {
  margin-top: 24rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: rgba(255, 123, 114, 0.12);
  color: #ff7b72;
  font-size: 24rpx;
}
.actions {
  margin-top: 48rpx;
}
.btn {
  background: linear-gradient(135deg, #3d8bfd, #2f6fed);
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: 600;
  padding: 8rpx 0;
}
.btn.stop {
  background: linear-gradient(135deg, #6e7681, #484f58);
}
.hint {
  display: block;
  margin-top: 28rpx;
  text-align: center;
  color: #6b7280;
  font-size: 22rpx;
  line-height: 1.5;
}
</style>
