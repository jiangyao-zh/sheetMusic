<template>
  <view class="page">
    <view class="hero">
      <text class="title">小提琴音准检测</text>
      <text class="subtitle">单音 · YIN · 实时评分</text>
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

    <view v-if="error" class="error">
      <text>{{ error }}</text>
    </view>

    <view class="actions">
      <button class="btn" :class="{ stop: running }" @click="toggle">
        {{ running ? '停止检测' : '开始检测' }}
      </button>
    </view>

    <text class="hint">
      H5 使用模拟音源；真机需离线打包并授权麦克风。
    </text>
  </view>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { onUnload } from '@dcloudio/uni-app'
import PitchMeter from '@/components/PitchMeter.vue'
import ScoreCard from '@/components/ScoreCard.vue'
import { usePitchStore } from '@/stores/pitch'

const store = usePitchStore()
const { result, running, error, a4, label, color } = storeToRefs(store)
const a4Options = [440, 442, 443]

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

onUnload(() => {
  store.stop()
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
