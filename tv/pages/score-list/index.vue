<template>
  <view class="page">
    <view class="bg-layer"></view>

    <view class="header">
      <view class="status-row">
        <text class="status-left">{{ statusTime }}</text>
        <text class="status-right">在线 · {{ displayImages.length }} 张</text>
      </view>
      <view class="title-row">
        <view class="accent"></view>
        <text class="title">乐谱列表</text>
        <text class="title-tag">TV</text>
      </view>
    </view>

    <view v-if="displayImages.length === 0" class="empty">
      <text>请把乐谱图片放到 static/scores 目录下。</text>
    </view>

    <scroll-view v-else class="list" scroll-y :scroll-into-view="focusAnchorId">
      <view class="grid" :style="{ gridTemplateColumns: `repeat(${cols}, ${itemSize}px)` }">
        <view
          v-for="(item, index) in displayImages"
          :key="item.id"
          :id="`score-card-${index}`"
          class="card"
          :class="{ 'is-selected': index === focusedIndex }"
          :style="{ width: itemSize + 'px' }"
          @click="openByIndex(index)"
          @tap="openByIndex(index)"
        >
          <text class="card-title">{{ item.title || `乐谱 ${index + 1}` }}</text>
          <image class="thumb" :src="item.src" mode="aspectFit" :style="{ width: (itemSize - 12) + 'px', height: (itemSize - 12) + 'px' }" />
          <text class="index-tag">{{ index + 1 }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="purple-rail"></view>

    <view class="manage-block">
      <text class="manage-label">扫码管理</text>
      <image class="qr" :src="qrCodeUrl" mode="aspectFit" />
      <text class="qr-hint">扫码继续浏览</text>
      <text class="qr-url">{{ manageUrl }}</text>
    </view>
  </view>
</template>

<script>
import { getFlatImagesFromStatic } from '@/src/data/flatImages';
import { getMergedSheetList } from '@/src/api/sheetApi';
import { getApiHost } from '@/src/config/api';

const KEY_MAP = {
  13: 'enter',
  19: 'up',
  20: 'down',
  21: 'left',
  22: 'right',
  23: 'enter',
  37: 'left',
  38: 'up',
  39: 'right',
  40: 'down',
  66: 'enter',
  176: 'menu',
  96: 'enter',
  160: 'enter'
};

function resolveKey(evt) {
  if (typeof evt === 'number') {
    return KEY_MAP[evt] || '';
  }
  const byKey = (evt && evt.key) || '';
  if (byKey === 'Enter') return 'enter';
  if (byKey === 'NumpadEnter') return 'enter';
  if (byKey === 'ArrowUp') return 'up';
  if (byKey === 'ArrowDown') return 'down';
  if (byKey === 'ArrowLeft') return 'left';
  if (byKey === 'ArrowRight') return 'right';
  if (byKey === 'Up') return 'up';
  if (byKey === 'Down') return 'down';
  if (byKey === 'Left') return 'left';
  if (byKey === 'Right') return 'right';
  const byCode = (evt && evt.code) || '';
  if (byCode === 'NumpadEnter' || byCode === 'Enter') return 'enter';
  if (byCode === 'ArrowUp') return 'up';
  if (byCode === 'ArrowDown') return 'down';
  if (byCode === 'ArrowLeft') return 'left';
  if (byCode === 'ArrowRight') return 'right';
  const rawCode = evt && (evt.keyCode || evt.which || evt.keycode || (evt.detail && evt.detail.keyCode) || (evt.nativeEvent && evt.nativeEvent.keyCode));
  const code = Number(rawCode) || 0;
  return KEY_MAP[code] || '';
}

export default {
  data() {
    return {
      focusedIndex: 0,
      baseImages: [],
      uploadImages: [],
      orderIds: [],
      sessionId: 'default',
      eventSource: null,
      cols: 5,
      now: Date.now(),
      clockTimer: null,
      screenWidth: 1920,
      appKeyHandler: null,
      listenersBound: false,
      lastRemoteKey: '',
      lastRemoteAt: 0,
      lastRemoteType: ''
    };
  },
  computed: {
    qrCodeUrl() {
      const apiHost = getApiHost();
      return `${apiHost}/public/uploads/qr_music.png`;
    },
    displayImages() {
      const merged = this.baseImages.concat(this.uploadImages);
      const byId = {};
      merged.forEach((x) => {
        byId[x.id] = x;
      });

      const sorted = [];
      this.orderIds.forEach((id) => {
        if (byId[id]) {
          sorted.push(byId[id]);
          delete byId[id];
        }
      });

      const remain = Object.values(byId).sort((a, b) =>
        String(a.title).localeCompare(String(b.title), 'en', { numeric: true, sensitivity: 'base' })
      );
      return sorted.concat(remain);
    },
    manageUrl() {
      if (typeof location === 'undefined') return '';
      return `${location.origin}/#/pages/mobile-manage/index?session=${encodeURIComponent(this.sessionId)}`;
    },
    itemSize() {
      // TV 4K 下 rpx 会过大，改用 px 并限制尺寸区间
      const raw = Math.floor((this.screenWidth - 420) / this.cols);
      return Math.max(96, Math.min(180, raw));
    },
    focusAnchorId() {
      return `score-card-${this.focusedIndex}`;
    },
    statusTime() {
      const date = new Date(this.now);
      const h = String(date.getHours()).padStart(2, '0');
      const m = String(date.getMinutes()).padStart(2, '0');
      const day = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'][date.getDay()];
      return `${h}:${m} ${day}`;
    }
  },
  onShow() {
    this.initLayout();
    this.bindKeys();
  },
  onHide() {
    this.unbindKeys();
  },
  mounted() {
    this.initLayout();
    this.reloadBase();
    this.initSession();
    this.connectControlChannel();
    this.startClock();
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', this.initLayout);
    }
    this.bindKeys();
  },
  onUnload() {
    this.unbindKeys();
  },
  beforeDestroy() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
      this.clockTimer = null;
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', this.initLayout);
    }
    this.unbindKeys();
  },
  methods: {
    bindKeys() {
      if (this.listenersBound) return;
      if (typeof window !== 'undefined') {
        window.addEventListener('keydown', this.onKeyDown);
        window.addEventListener('keyup', this.onKeyDown);
      }
      // #ifdef APP-PLUS
      if (typeof plus !== 'undefined' && plus.key) {
        this.appKeyHandler = (evt) => this.onKeyDown(evt);
        plus.key.addEventListener('keydown', this.appKeyHandler);
        plus.key.addEventListener('keyup', this.appKeyHandler);
      }
      // #endif
      this.listenersBound = true;
    },
    unbindKeys() {
      if (!this.listenersBound) return;
      if (typeof window !== 'undefined') {
        window.removeEventListener('keydown', this.onKeyDown);
        window.removeEventListener('keyup', this.onKeyDown);
      }
      // #ifdef APP-PLUS
      if (typeof plus !== 'undefined' && plus.key && this.appKeyHandler) {
        plus.key.removeEventListener('keydown', this.appKeyHandler);
        plus.key.removeEventListener('keyup', this.appKeyHandler);
      }
      // #endif
      this.listenersBound = false;
    },
    initLayout() {
      // #ifdef H5
      if (typeof window !== 'undefined') {
        this.screenWidth = window.innerWidth || this.screenWidth;
        return;
      }
      // #endif
      try {
        const info = uni.getSystemInfoSync();
        this.screenWidth = info && info.windowWidth ? info.windowWidth : this.screenWidth;
      } catch (e) {
        // ignore
      }
    },
    startClock() {
      this.now = Date.now();
      if (this.clockTimer) clearInterval(this.clockTimer);
      this.clockTimer = setInterval(() => {
        this.now = Date.now();
      }, 30000);
    },
    async reloadBase() {
      const localSheets = getFlatImagesFromStatic();
      this.baseImages = await getMergedSheetList(localSheets);
      if (!this.orderIds.length) {
        this.orderIds = this.baseImages.map((x) => x.id);
      }
    },
    initSession() {
      if (typeof location === 'undefined') return;
      const hash = location.hash || '';
      const queryIndex = hash.indexOf('?');
      if (queryIndex >= 0) {
        const query = new URLSearchParams(hash.slice(queryIndex + 1));
        const session = query.get('session');
        if (session) this.sessionId = session;
      }
      if (!this.sessionId || this.sessionId === 'default') {
        this.sessionId = `tv-${Date.now().toString(36)}`;
      }
    },
    connectControlChannel() {
      if (typeof EventSource === 'undefined' || typeof location === 'undefined') return;
      const host = location.hostname || '127.0.0.1';
      const sseUrl = `http://${host}:9091/events?session=${encodeURIComponent(this.sessionId)}`;
      this.eventSource = new EventSource(sseUrl);
      this.eventSource.addEventListener('action', (evt) => {
        try {
          const payload = JSON.parse(evt.data || '{}');
          this.applyControlAction(payload);
        } catch (e) {
          // ignore bad payload
        }
      });
    },
    applyControlAction(payload) {
      if (!payload || !payload.type) return;
      if (payload.type === 'reorder' && Array.isArray(payload.ids)) {
        this.orderIds = payload.ids.slice();
      } else if (payload.type === 'append_uploads' && Array.isArray(payload.items)) {
        const exists = new Set(this.uploadImages.map((x) => x.id));
        const appended = payload.items.filter((x) => x && x.id && !exists.has(x.id));
        this.uploadImages = this.uploadImages.concat(appended);
      }
      const max = Math.max(0, this.displayImages.length - 1);
      this.focusedIndex = Math.min(this.focusedIndex, max);
    },
    moveFocus(key) {
      const total = this.displayImages.length;
      if (!total) return;
      const cols = this.cols;
      const rows = Math.ceil(total / cols);
      const row = Math.floor(this.focusedIndex / cols);
      const col = this.focusedIndex % cols;
      
      let nextRow = row;
      let nextCol = col;
      if (key === 'left') nextCol = Math.max(0, col - 1);
      if (key === 'right') nextCol = Math.min(cols - 1, col + 1);
      if (key === 'up') nextRow = Math.max(0, row - 1);
      if (key === 'down') nextRow = Math.min(rows - 1, row + 1);
      let next = nextRow * cols + nextCol;
      if (next >= total) next = total - 1;
      this.focusedIndex = next;
    },
    openFocused() {
      const item = this.displayImages[this.focusedIndex];
      if (!item) return;
      this.unbindKeys();
      uni.setStorageSync('sheet_flat_list', JSON.stringify(this.displayImages));
      uni.navigateTo({
        url: `/pages/score-preview/index?index=${this.focusedIndex}`
      });
    },
    openByIndex(index) {
      this.focusedIndex = index;
      this.openFocused();
    },
    onKeyDown(evt) {
      if (evt && evt.repeat) return;
      const key = resolveKey(evt);
      if (!key) return;
      const now = Date.now();
      const evtType = (evt && evt.type) || 'native';
      if (key === this.lastRemoteKey && now - this.lastRemoteAt < 140) return;
      if (evtType === this.lastRemoteType && now - this.lastRemoteAt < 60) return;
      this.lastRemoteKey = key;
      this.lastRemoteAt = now;
      this.lastRemoteType = evtType;
      if (key === 'enter') {
        evt.preventDefault && evt.preventDefault();
        this.openFocused();
        return;
      }
      if (key === 'left' || key === 'right' || key === 'up' || key === 'down') {
        this.moveFocus(key);
      }
      evt.preventDefault && evt.preventDefault();
    }
  }
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 14px 17px;
  position: relative;
  overflow: hidden;
}
.bg-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(135deg, #0b1018 0%, #101827 55%, #0a0f15 100%);
}
.header,
.list,
.purple-rail,
.manage-block,
.empty {
  position: relative;
  z-index: 2;
}
.header {
  margin-bottom: 8px;
  padding: 4px 4px 0;
}
.status-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.status-left,
.status-right {
  color: #b9aee6;
  font-size: 11px;
  letter-spacing: 1px;
}
.status-right { color: #a49acb; }
.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.accent {
  display: none;
}
.title {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #e7edf7;
  letter-spacing: -1px;
}
.title-tag {
  color: #c9d3e3;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 1px;
}
.empty { margin-top: 16px; color: #d8cbff; font-size: 15px; }
.list {
  height: calc(100vh - 175px);
  width: calc(100% - 340px);
}
.grid {
  display: grid;
  gap: 6px;
  justify-content: start;
  align-content: start;
}
.card {
  border: 2px solid transparent;
  border-radius: 10px;
  padding: 3px;
  background: rgba(18, 24, 34, 0.55);
  position: relative;
  box-sizing: border-box;
  outline: none;
  box-shadow: none;
}
.card.is-selected {
  border-color: #f1c64d;
  box-shadow: none;
}
.card:not(.is-selected) {
  border-color: transparent !important;
  box-shadow: none !important;
}
.card:focus,
.card:active,
.card:focus-visible {
  outline: none !important;
  box-shadow: none !important;
}
.card-title {
  display: block;
  font-size: 9px;
  color: #b9aee6;
  margin-bottom: 4px;
  padding: 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: center;
}
.thumb {
  border-radius: 7px;
  background: #0f1115;
  display: block;
  object-fit: cover;
}
.index-tag {
  position: absolute;
  right: 6px;
  bottom: 6px;
  min-width: 6px;
  padding: 0 2px;
  border-radius: 999px;
  background: rgba(12, 11, 25, 0.75);
  color: #d8cbff;
  font-size: 3px;
  line-height: 1.2;
  text-align: center;
}
.purple-rail {
  display: none;
}
.manage-block {
  position: fixed;
  right: 42px;
  bottom: 28px;
  z-index: 99;
  width: 220px;
  border: 3px solid #93a4bc;
  border-radius: 18px;
  padding: 14px;
  background: rgba(20, 25, 35, 0.86);
  transform: scale(0.28);
  transform-origin: right bottom;
}
.manage-label {
  display: block;
  font-size: 22px;
  color: #d4deeb;
  margin-bottom: 8px;
}
.qr {
  width: 192px;
  height: 192px;
  background: #fff;
  border-radius: 10px;
}
.qr-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1f2634;
}
.qr-fallback-text {
  color: #d4deeb;
  font-size: 16px;
}
.qr-hint {
  display: block;
  color: #d4deeb;
  font-size: 18px;
  margin-top: 8px;
  text-align: center;
}
.qr-url {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  color: #9ca7bb;
  text-align: center;
  word-break: break-all;
}
</style>
