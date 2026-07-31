<template>
  <view class="card">
    <view class="row">
      <text class="label">当前音符</text>
      <text class="value note" :style="{ color }">{{ result.note }}</text>
    </view>
    <view class="row">
      <text class="label">频率</text>
      <text class="value">{{ formatFrequency(result.frequency) }}</text>
    </view>
    <view class="row">
      <text class="label">偏差</text>
      <text class="value" :style="{ color }">{{ formatCent(result.cent, result.status) }}</text>
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
  padding: 36rpx 40rpx;
  box-sizing: border-box;
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
.note {
  font-size: 72rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}
.score {
  font-size: 44rpx;
}
.meta {
  margin-top: 12rpx;
  display: flex;
  justify-content: space-between;
  color: #6b7280;
  font-size: 22rpx;
}
</style>
