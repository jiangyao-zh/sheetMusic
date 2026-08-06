<template>
  <view class="card">
    <view class="hero">
      <text class="hero-label">当前音符</text>
      <text class="hero-note" :style="{ color }">{{ result.note }}</text>
    </view>

    <view class="metrics">
      <view class="metric">
        <text class="metric-label">频率</text>
        <text class="metric-value">{{ formatFrequency(result.frequency) }}</text>
      </view>
      <view class="metric">
        <text class="metric-label">偏差</text>
        <text class="metric-value" :style="{ color }">{{ formatCent(result.cent, result.status) }}</text>
      </view>
    </view>

    <view class="row">
      <text class="label">评分</text>
      <text class="value score">{{ formatScore(result.score, result.status) }}</text>
    </view>
    <view class="row">
      <text class="label">状态</text>
      <text class="value" :style="{ color }">{{ statusText }}</text>
    </view>
    <view v-if="result.status === 'valid'" class="meta">
      <text>置信度 {{ (result.confidence * 100).toFixed(0) }}%</text>
      <text>MIDI {{ result.midi.toFixed(1) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { PitchResult } from '@/types/pitch'
import { formatCent, formatFrequency, formatScore } from '@/utils/format'

defineProps<{
  result: PitchResult
  color: string
  statusText: string
}>()
</script>

<style scoped>
.card {
  width: 100%;
  background: linear-gradient(160deg, #1a1f2b 0%, #141820 100%);
  border: 1px solid #2a3140;
  border-radius: 28rpx;
  padding: 40rpx 40rpx 32rpx;
  box-sizing: border-box;
}
.hero {
  align-items: center;
  display: flex;
  flex-direction: column;
  padding: 12rpx 0 28rpx;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.hero-label {
  color: #8b93a7;
  font-size: 28rpx;
  margin-bottom: 8rpx;
}
.hero-note {
  color: #f3f5f7;
  font-size: 132rpx;
  font-weight: 800;
  letter-spacing: 4rpx;
  line-height: 1.05;
}
.metrics {
  display: flex;
  gap: 20rpx;
  margin: 28rpx 0 8rpx;
}
.metric {
  flex: 1;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20rpx;
  padding: 24rpx 20rpx;
}
.metric-label {
  display: block;
  color: #8b93a7;
  font-size: 26rpx;
  margin-bottom: 10rpx;
}
.metric-value {
  display: block;
  color: #f3f5f7;
  font-size: 48rpx;
  font-weight: 700;
  line-height: 1.2;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 18rpx 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}
.row:last-of-type {
  border-bottom: none;
}
.label {
  color: #8b93a7;
  font-size: 28rpx;
}
.value {
  color: #f3f5f7;
  font-size: 34rpx;
  font-weight: 600;
}
.score {
  font-size: 48rpx;
}
.meta {
  margin-top: 12rpx;
  display: flex;
  justify-content: space-between;
  color: #6b7280;
  font-size: 22rpx;
}
</style>
