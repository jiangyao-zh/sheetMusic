<template>
  <view class="meter">
    <view class="track">
      <view class="center-line" />
      <view
        class="needle"
        :style="{ transform: `translateX(${needleOffset}px)`, background: color }"
      />
    </view>
    <view class="labels">
      <text>-50</text>
      <text>0</text>
      <text>+50</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PitchStatus } from '@/types/pitch'

const props = defineProps<{
  cent: number
  status: PitchStatus
  color: string
}>()

const needleOffset = computed(() => {
  if (props.status === 'idle' || props.status === 'no_signal') return 0
  const clamped = Math.max(-50, Math.min(50, props.cent))
  // 轨道半宽约 140rpx ≈ 70px 视觉；用百分比近似
  return (clamped / 50) * 120
})
</script>

<style scoped>
.meter {
  width: 100%;
  margin: 24rpx 0 8rpx;
}
.track {
  position: relative;
  height: 16rpx;
  border-radius: 999rpx;
  background: #232833;
  overflow: visible;
}
.center-line {
  position: absolute;
  left: 50%;
  top: -10rpx;
  width: 4rpx;
  height: 36rpx;
  background: #5c6578;
  transform: translateX(-50%);
}
.needle {
  position: absolute;
  left: 50%;
  top: -12rpx;
  width: 20rpx;
  height: 40rpx;
  margin-left: -10rpx;
  border-radius: 8rpx;
  transition: transform 0.08s linear, background 0.2s ease;
}
.labels {
  margin-top: 18rpx;
  display: flex;
  justify-content: space-between;
  color: #8b93a7;
  font-size: 22rpx;
}
</style>
