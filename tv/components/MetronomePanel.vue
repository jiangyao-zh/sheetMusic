<template>
  <view v-if="visible" class="panel">
    <text class="panel-title">节拍器设置</text>
    <view class="row" :class="{ active: activeIndex === 0 }">
      <text class="label">BPM</text>
      <text class="value">{{ bpm }}</text>
    </view>
    <view class="row" :class="{ active: activeIndex === 1 }">
      <text class="label">拍号分子</text>
      <text class="value">{{ beatsPerBar }}</text>
    </view>
    <view class="row" :class="{ active: activeIndex === 2 }">
      <text class="label">节拍器</text>
      <text class="value">{{ enabled ? "停止" : "启动" }}</text>
    </view>
  </view>
</template>

<script>
export default {
  emits: ["toggle", "bpmDown", "bpmUp", "beatDown", "beatUp", "close"],
  props: {
    visible: { type: Boolean, default: false },
    activeIndex: { type: Number, default: 0 },
    bpm: { type: Number, default: 80 },
    beatsPerBar: { type: Number, default: 4 },
    enabled: { type: Boolean, default: false }
  }
};
</script>

<style scoped>
.panel {
  position: fixed;
  left: 50%;
  top: 50%;
  width: 560px;
  max-width: 92vw;
  max-height: 88vh;
  overflow: auto;
  border-radius: 16px;
  background: rgba(15, 17, 21, 0.92);
  border: 1px solid #2d3442;
  padding: 18px;
  z-index: 999;
  transform: translate(-50%, -50%);
}

.panel-title {
  display: block;
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 10px;
}

.row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(45, 52, 66, 0.4);
}

.row.active {
  outline: 1px solid #4fa3ff;
  background: rgba(79, 163, 255, 0.22);
}

.label {
  font-size: 20px;
  color: #dbe2ee;
}

.value {
  font-size: 20px;
  font-weight: 700;
  color: #f9fbff;
}
</style>
