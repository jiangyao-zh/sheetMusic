<template>
  <view class="page">
    <text class="title">手机管理台</text>
    <text class="sub">会话: {{ sessionId }}</text>

    <view class="toolbar">
      <button class="btn" type="primary" @click="sendCurrentOrder">同步当前排序到大屏</button>
      <!-- #ifdef H5 -->
      <input class="file" type="file" multiple accept="image/*" @change="onPickFiles" />
      <!-- #endif -->
    </view>

    <scroll-view class="list" scroll-y>
      <view v-for="(item, idx) in items" :key="item.id" class="row">
        <image :src="item.src" class="thumb" mode="aspectFill" />
        <text class="name">{{ idx + 1 }}. {{ item.title }}</text>
        <view class="ops">
          <button size="mini" @click="moveUp(idx)">上移</button>
          <button size="mini" @click="moveDown(idx)">下移</button>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { getFlatImagesFromStatic } from '@/src/data/flatImages';

export default {
  data() {
    return {
      sessionId: 'default',
      items: []
    };
  },
  onLoad(query) {
    this.sessionId = (query && query.session) || 'default';
    this.items = getFlatImagesFromStatic();
  },
  methods: {
    async postAction(payload) {
      const host = (typeof location !== 'undefined' && location.hostname) || '127.0.0.1';
      const url = `http://${host}:9091/action?session=${encodeURIComponent(this.sessionId)}`;
      await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
    },
    async sendCurrentOrder() {
      await this.postAction({
        type: 'reorder',
        ids: this.items.map((x) => x.id)
      });
      uni.showToast({ title: '已同步', icon: 'success' });
    },
    moveUp(index) {
      if (index <= 0) return;
      const arr = this.items.slice();
      const t = arr[index - 1];
      arr[index - 1] = arr[index];
      arr[index] = t;
      this.items = arr;
      this.sendCurrentOrder();
    },
    moveDown(index) {
      if (index >= this.items.length - 1) return;
      const arr = this.items.slice();
      const t = arr[index + 1];
      arr[index + 1] = arr[index];
      arr[index] = t;
      this.items = arr;
      this.sendCurrentOrder();
    },
    async onPickFiles(event) {
      const files = (event.target && event.target.files) || [];
      if (!files.length) return;
      const uploads = [];
      for (let i = 0; i < files.length; i += 1) {
        const file = files[i];
        const dataUrl = await new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result);
          reader.onerror = reject;
          reader.readAsDataURL(file);
        });
        uploads.push({
          id: `upload-${Date.now()}-${i}`,
          title: file.name,
          src: dataUrl,
          type: 'upload'
        });
      }
      this.items = this.items.concat(uploads);
      await this.postAction({ type: 'append_uploads', items: uploads });
      await this.sendCurrentOrder();
      uni.showToast({ title: '上传并同步成功', icon: 'success' });
    }
  }
};
</script>

<style scoped>
.page { min-height: 100vh; background: #0f1115; color: #f3f5f7; padding: 24rpx; }
.title { font-size: 44rpx; font-weight: 700; display: block; }
.sub { color: #98a3b5; font-size: 24rpx; display: block; margin-bottom: 14rpx; }
.toolbar { margin-bottom: 16rpx; display: flex; gap: 10rpx; align-items: center; flex-wrap: wrap; }
.file { color: #d9e1ef; }
.list { height: calc(100vh - 190rpx); }
.row { display: flex; align-items: center; border: 2rpx solid #293042; border-radius: 10rpx; padding: 10rpx; margin-bottom: 10rpx; }
.thumb { width: 120rpx; height: 70rpx; border-radius: 6rpx; background: #1c2230; margin-right: 12rpx; }
.name { flex: 1; font-size: 24rpx; }
.ops { display: flex; gap: 8rpx; }
.btn { margin-right: 8rpx; }
</style>
