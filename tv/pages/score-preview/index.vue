<template>
  <view class="page">
    <view class="left-section">
      <view class="score-wrap">
        <view v-if="!currentImage" class="empty">未找到图片。</view>
        <view v-else class="canvas">
          <image class="score-image" :src="currentImage" mode="aspectFit" :show-menu-by-longpress="true" :fade-show="false" @load="onImageLoad" @error="onImageError" />
          <view v-if="imageLoadError" class="img-error">
            <text>图片加载失败：{{ currentImage }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="right-section">
      <view class="time-info-block">
        <text class="current-time">{{ currentTimeWithSeconds }}</text>
        <text class="current-date-cn">{{ chineseDateText }}</text>
      </view>
      
      <view class="info-block">
        <text class="title">乐谱详情</text>
        <text class="sub-meta">{{ currentTitle }}</text>
        <text class="page-info">第 {{ pageIndex + 1 }} / {{ totalPages }} 页</text>
      </view>

      <view class="metro-block">
        <view class="metro-header">
          <text class="metro-title">节拍器</text>
          <text class="metro-status" :class="{ running: enabled }">{{ enabled ? '运行中' : '已停止' }}</text>
        </view>
        
        <view class="beat-display">
          <view class="beat-dots">
            <view v-for="n in beatsPerBar" :key="n" class="light" :class="{ active: enabled && currentBeat === n, idle: !enabled || currentBeat !== n }" />
          </view>
        </view>

        <view class="metro-info">
          <view class="info-row">
            <text class="info-label">BPM</text>
            <text class="info-value bpm-value">{{ bpm }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">拍号</text>
            <text class="info-value">{{ beatsPerBar }}/4</text>
          </view>
          <view class="info-row">
            <text class="info-label">当前</text>
            <text class="info-value">第 {{ currentBeatLabel }} 拍</text>
          </view>
        </view>

        <button v-if="isBrowser" class="open-panel-btn" size="mini" @click="togglePanelByMouse">
          {{ panelVisible ? '关闭设置' : '打开设置' }}
        </button>
      </view>
    </view>

    <MetronomePanel
      :visible="panelVisible"
      :active-index="panelIndex"
      :bpm="bpm"
      :beats-per-bar="beatsPerBar"
      :enabled="enabled"
      @toggle="toggleMetronome"
      @bpmDown="adjustBpm(-1)"
      @bpmUp="adjustBpm(1)"
      @beatDown="setBeatsPerBar(beatsPerBar - 1)"
      @beatUp="setBeatsPerBar(beatsPerBar + 1)"
      @close="panelVisible = false"
    />

  </view>
</template>

<script>
import { getFlatImagesFromStatic } from '@/src/data/flatImages';
import { getMergedSheetList } from '@/src/api/sheetApi';
import { getLocalSyncedImages } from '@/src/utils/syncImages';
import MetronomePanel from '@/components/MetronomePanel.vue';

const KEY_MAP = {
  4: 'back', 13: 'enter', 19: 'up', 20: 'down', 21: 'left', 22: 'right',
  23: 'enter', 27: 'back', 37: 'left', 38: 'up', 39: 'right', 40: 'down',
  66: 'enter', 82: 'menu', 96: 'enter', 160: 'enter', 176: 'menu'
};

function resolveKey(evt) {
  if (typeof evt === 'number') return KEY_MAP[evt] || '';
  const byKey = (evt && evt.key) || '';
  if (byKey === 'Enter') return 'enter';
  if (byKey === 'NumpadEnter') return 'enter';
  if (byKey === 'Up') return 'up';
  if (byKey === 'Down') return 'down';
  if (byKey === 'Left') return 'left';
  if (byKey === 'Right') return 'right';
  if (byKey === 'ArrowUp') return 'up';
  if (byKey === 'ArrowDown') return 'down';
  if (byKey === 'ArrowLeft') return 'left';
  if (byKey === 'ArrowRight') return 'right';
  if (byKey === 'Escape') return 'back';
  if (byKey === 'ContextMenu') return 'menu';
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
  components: { MetronomePanel },
  data() {
    return {
      images: [],
      pageIndex: 0,
      panelVisible: false,
      panelIndex: 0,
      bpm: 80,
      beatsPerBar: 4,
      enabled: false,
      currentBeat: 0,
      nativeSoundPool: null,
      nativeSoundId: 0,
      nativeSoundReady: false,
      nativeToneGen: null,
      webAudioContext: null,
      audioMode: '',
      isStartingMetronome: false,
      timer: null,
      beatIndex: 0,
      imageLoadError: false,
      lastToggleAt: 0,
      listenersBound: false,
      appKeyHandler: null,
      panelCloseLockUntil: 0,
      lastNavAt: 0,
      navBlockUntil: 0,
      now: Date.now(),
      appStartTime: 0,
      timeClockTimer: null
    };
  },
  computed: {
    isBrowser() {
      return typeof window !== 'undefined' && typeof document !== 'undefined';
    },
    totalPages() { return this.images.length; },
    currentTitle() {
      const item = this.images[this.pageIndex];
      if (!item) return '乐谱预览';
      return item.title || '乐谱预览';
    },
    currentBeatLabel() {
      return this.currentBeat > 0 ? this.currentBeat : 1;
    },
    currentTimeWithSeconds() {
      const date = new Date(this.now);
      const h = String(date.getHours()).padStart(2, '0');
      const m = String(date.getMinutes()).padStart(2, '0');
      const s = String(date.getSeconds()).padStart(2, '0');
      return `${h}:${m}:${s}`;
    },
    chineseDateText() {
      const date = new Date(this.now);
      const months = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二'];
      const days = ['日', '一', '二', '三', '四', '五', '六'];
      const month = months[date.getMonth()];
      const day = date.getDate();
      const weekday = days[date.getDay()];
      return `${month}月${day}日 星期${weekday}`;
    },
    elapsedTime() {
      const elapsed = Math.floor((this.now - this.appStartTime) / 1000);
      const hours = Math.floor(elapsed / 3600);
      const minutes = Math.floor((elapsed % 3600) / 60);
      const seconds = elapsed % 60;
      if (hours > 0) {
        return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
      }
      return `${minutes}:${String(seconds).padStart(2, '0')}`;
    },
    currentImage() {
      const item = this.images[this.pageIndex];
      let src = item ? item.src : '';
      // 去除URL中的 thumb_ 前缀以访问原图
      src = src.replace(/\/thumb_/g, '/');
      console.log('[详情页] 图片HTTP地址:', src);
      return src;
    }
  },
  async onLoad(query) {
    const raw = uni.getStorageSync('sheet_flat_list');
    if (raw) {
      try { this.images = JSON.parse(raw); } catch (e) { this.images = []; }
    }
    if (!this.images.length) {
      // 优先读取本地同步数据
      const syncedImages = getLocalSyncedImages();
      if (syncedImages && syncedImages.length) {
        this.images = syncedImages;
      } else {
        const localSheets = getFlatImagesFromStatic();
        this.images = await getMergedSheetList(localSheets);
      }
    }
    this.pageIndex = Math.max(0, Math.min(this.images.length - 1, Number((query && query.index) || 0)));
    this.loadCurrentBpm();
    this.initAudio();
    this.loadAppStartTime();
  },
  onBackPress() {
    const now = Date.now();
    if (this.panelVisible) {
      this.closePanelSafely();
      return true;
    }
    // 面板刚关闭后的保护期内，阻止返回
    if (now < this.panelCloseLockUntil) {
      return true;
    }
    if (this.enabled) {
      this.stopMetronome();
      return true;
    }
    return false;
  },
  onShow() {
    this.navBlockUntil = Date.now() + 900;
    this.bindKeys();
    if (!this.audioMode) this.initAudio();
    this.startTimeClock();
    this.setScreenAlwaysOn();
  },
  onHide() {
    this.unbindKeys();
    this.stopTimeClock();
    this.releaseScreenAlwaysOn();
  },
  mounted() {
    this.bindKeys();
  },
  onUnload() {
    this.cleanup();
  },
  beforeDestroy() {
    this.cleanup();
  },
  methods: {
    setScreenAlwaysOn() {
      // #ifdef APP-PLUS
      if (typeof plus !== 'undefined' && plus.device) {
        try {
          plus.device.setWakelock(true);
          console.log('[ScreenAlwaysOn] 已启用屏幕常亮');
        } catch (e) {
          console.warn('[ScreenAlwaysOn] 启用失败:', e);
        }
      }
      // #endif
    },
    releaseScreenAlwaysOn() {
      // #ifdef APP-PLUS
      if (typeof plus !== 'undefined' && plus.device) {
        try {
          plus.device.setWakelock(false);
          console.log('[ScreenAlwaysOn] 已释放屏幕常亮');
        } catch (e) {
          console.warn('[ScreenAlwaysOn] 释放失败:', e);
        }
      }
      // #endif
    },
    bindKeys() {
      this.unbindKeys();
      if (typeof window !== 'undefined') window.addEventListener('keydown', this.onKeyDown);
      // #ifdef APP-PLUS
      if (typeof plus !== 'undefined' && plus.key) {
        this.appKeyHandler = (evt) => this.onKeyDown(evt);
        plus.key.addEventListener('keydown', this.appKeyHandler);
      }
      // #endif
      this.listenersBound = true;
    },
    unbindKeys() {
      if (!this.listenersBound) return;
      if (typeof window !== 'undefined') window.removeEventListener('keydown', this.onKeyDown);
      // #ifdef APP-PLUS
      if (typeof plus !== 'undefined' && plus.key && this.appKeyHandler) {
        plus.key.removeEventListener('keydown', this.appKeyHandler);
      }
      // #endif
      this.listenersBound = false;
    },
    loadAppStartTime() {
      const stored = uni.getStorageSync('app_start_time');
      this.appStartTime = stored || Date.now();
    },
    startTimeClock() {
      this.stopTimeClock();
      this.now = Date.now();
      this.timeClockTimer = setInterval(() => {
        this.now = Date.now();
      }, 1000);
    },
    stopTimeClock() {
      if (this.timeClockTimer) {
        clearInterval(this.timeClockTimer);
        this.timeClockTimer = null;
      }
    },
    cleanup() {
      this.unbindKeys();
      this.stopMetronome();
      this.stopTimeClock();
      // #ifdef APP-PLUS
      if (this.nativeSoundPool) {
        try { this.nativeSoundPool.release(); } catch (e) { /* 忽略 */ }
        this.nativeSoundPool = null;
      }
      if (this.nativeToneGen) {
        try { this.nativeToneGen.release(); } catch (e) { /* 忽略 */ }
        this.nativeToneGen = null;
      }
      // #endif
      if (this.webAudioContext) {
        try { this.webAudioContext.close(); } catch (e) { /* 忽略 */ }
        this.webAudioContext = null;
      }
    },
    initAudio() {
      if (this.audioMode) return;
      // #ifdef APP-PLUS
      this.initNativeAudio();
      return;
      // #endif
      // H5 使用 Web Audio API
      this.initWebAudio();
    },
    initNativeAudio() {
      // #ifdef APP-PLUS
      if (typeof plus === 'undefined' || !plus.android) {
        this.initWebAudio();
        return;
      }
      // 初始化 ToneGenerator（即时可用，确认有效的方案）
      try {
        const ToneGenerator = plus.android.importClass('android.media.ToneGenerator');
        // AudioManager.STREAM_MUSIC = 3, volume = 100
        this.nativeToneGen = new ToneGenerator(3, 100);
        // ToneGenerator 已确认可以发声，立即设为主模式
        this.audioMode = 'tonegen';
      } catch (e) {
        console.log('[节拍器] ToneGenerator 初始化失败:', e.message || e);
      }

      // 后台尝试 SoundPool（加载 mp3 音效，音质更好）
      // 即使失败也不影响 ToneGenerator 正常工作
      try {
        const SoundPool = plus.android.importClass('android.media.SoundPool');
        this.nativeSoundPool = new SoundPool(4, 3, 0);

        let filePath = plus.io.convertLocalFileSystemURL('_www/static/freesound.mp3');
        if (filePath && filePath.indexOf('file://') === 0) {
          filePath = filePath.replace('file://', '');
        }

        if (filePath) {
          this.nativeSoundId = this.nativeSoundPool.load(filePath, 1);
          // SoundPool 加载是异步的，延迟后尝试试播
          // 如果 soundId > 0 才升级模式
          setTimeout(() => {
            if (this.nativeSoundId > 0) {
              this.nativeSoundReady = true;
              this.audioMode = 'soundpool';
            }
          }, 800);
        }
      } catch (e) {
        // SoundPool 失败不影响 ToneGenerator
      }

      if (this.audioMode) return;

      // 原生都失败了，降级到 Web Audio API
      this.initWebAudio();
      // #endif
    },
    initWebAudio() {
      try {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (AC) {
          this.webAudioContext = new AC();
          this.audioMode = 'webaudio';
        }
      } catch (e) {
        console.log('[节拍器] Web Audio API 初始化失败:', e.message || e);
      }
    },
    playNativeTone() {
      // TONE_DTMF_STAR = 10，最高频的 DTMF 音（* 键），极其清脆像敲击硬物
      if (!this.nativeToneGen) return false;
      try { this.nativeToneGen.startTone(10, 25); } catch (e) { /* 忽略 */ }
      return true;
    },
    playClick() {
      const mode = this.audioMode;
      // #ifdef APP-PLUS
      if (mode === 'soundpool' && this.nativeSoundPool && this.nativeSoundReady) {
        // SoundPool.play 返回非0表示成功
        let streamId = 0;
        try { streamId = this.nativeSoundPool.play(this.nativeSoundId, 1.0, 1.0, 1, 0, 1.0); } catch (e) { /* 忽略 */ }
        // 如果 SoundPool 播放失败，降级回 ToneGenerator
        if (!streamId && this.nativeToneGen) {
          this.audioMode = 'tonegen';
          return this.playNativeTone();
        }
        return true;
      }
      if (this.nativeToneGen) {
        return this.playNativeTone();
      }
      // #endif

      // H5: Web Audio API 模拟节拍器"哒"声
      if (this.webAudioContext) {
        try {
          const ctx = this.webAudioContext;
          if (ctx.state === 'suspended') ctx.resume();
          const t = ctx.currentTime;

          // 用两个振荡器叠加模拟木质节拍器的"哒"声
          // 高频短脉冲（攻击感）
          const osc1 = ctx.createOscillator();
          const gain1 = ctx.createGain();
          osc1.connect(gain1);
          gain1.connect(ctx.destination);
          osc1.frequency.value = 1500;
          osc1.type = 'sine';
          gain1.gain.setValueAtTime(0.6, t);
          gain1.gain.exponentialRampToValueAtTime(0.001, t + 0.03);
          osc1.start(t);
          osc1.stop(t + 0.03);

          // 中频共鸣（木质感）
          const osc2 = ctx.createOscillator();
          const gain2 = ctx.createGain();
          osc2.connect(gain2);
          gain2.connect(ctx.destination);
          osc2.frequency.value = 800;
          osc2.type = 'triangle';
          gain2.gain.setValueAtTime(0.3, t);
          gain2.gain.exponentialRampToValueAtTime(0.001, t + 0.06);
          osc2.start(t);
          osc2.stop(t + 0.06);

          return true;
        } catch (e) { /* 忽略 */ }
      }
      return false;
    },
    startMetronome() {
      if (this.enabled || this.timer || this.isStartingMetronome) return;
      if (!this.audioMode) this.initAudio();
      this.isStartingMetronome = true;
      this.playClick();
      setTimeout(() => {
        if (!this.isStartingMetronome) return;
        this.playClick();
      }, 170);
      setTimeout(() => {
        if (!this.isStartingMetronome) return;
        this.enabled = true;
        this.currentBeat = 1;
        this.beatIndex = 1;
        const step = Math.max(120, Math.floor(60000 / this.bpm));
        this.timer = setInterval(() => {
          if (!this.enabled) return;
          this.currentBeat = (this.currentBeat % this.beatsPerBar) + 1;
          this.beatIndex += 1;
          this.playClick();
        }, step);
        this.isStartingMetronome = false;
      }, 360);
    },
    stopMetronome() {
      this.isStartingMetronome = false;
      this.enabled = false;
      this.currentBeat = 0;
      if (this.timer) { clearInterval(this.timer); this.timer = null; }
    },
    toggleMetronome() {
      const now = Date.now();
      if (now - this.lastToggleAt < 220) return;
      this.lastToggleAt = now;
      if (this.enabled) this.stopMetronome(); else this.startMetronome();
    },
    adjustBpm(delta) {
      this.bpm = Math.max(40, Math.min(240, Number(this.bpm) + delta));
      if (this.enabled) {
        this.stopMetronome();
        this.startMetronome();
      }
    },
    setBeatsPerBar(next) {
      this.beatsPerBar = Math.max(2, Math.min(8, Number(next) || 4));
      if (this.currentBeat > this.beatsPerBar) this.currentBeat = 1;
    },
    nextPage() {
      if (!this.totalPages) return;
      this.pageIndex = Math.min(this.totalPages - 1, this.pageIndex + 1);
      this.imageLoadError = false;
      this.loadCurrentBpm();
    },
    prevPage() {
      if (!this.totalPages) return;
      this.pageIndex = Math.max(0, this.pageIndex - 1);
      this.imageLoadError = false;
      this.loadCurrentBpm();
    },
    loadCurrentBpm() {
      const item = this.images[this.pageIndex];
      if (item && typeof item.bpm === 'number' && item.bpm >= 40 && item.bpm <= 240) {
        // BPM 相同时不中断节拍器，保持平滑过渡
        if (this.bpm === item.bpm) return;
        const wasRunning = this.enabled;
        if (wasRunning) this.stopMetronome();
        this.bpm = item.bpm;
        if (wasRunning) {
          this.$nextTick(() => {
            this.startMetronome();
          });
        }
      }
    },
    onImageLoad() { this.imageLoadError = false; },
    onImageError() { this.imageLoadError = true; },
    togglePanelByMouse() {
      if (this.panelVisible) {
        this.closePanelSafely();
      } else {
        this.openPanelSafely();
      }
    },
    openPanelSafely() {
      this.panelVisible = true;
      this.panelIndex = 0;
      this.panelCloseLockUntil = 0;
      this.navBlockUntil = Date.now() + 900;
    },
    closePanelSafely() {
      this.panelVisible = false;
      this.panelCloseLockUntil = Date.now() + 800;
      this.navBlockUntil = Date.now() + 900;
    },
    updatePanelByKey(key) {
      if (key === 'up') { this.panelIndex = Math.max(0, this.panelIndex - 1); return; }
      if (key === 'down') { this.panelIndex = Math.min(2, this.panelIndex + 1); return; }
      if (key === 'enter') {
        if (this.panelIndex === 2) {
          this.toggleMetronome();
          this.closePanelSafely();
        }
        return;
      }
      if (this.panelIndex === 0) { if (key === 'left') this.adjustBpm(-1); if (key === 'right') this.adjustBpm(1); return; }
      if (this.panelIndex === 1) {
        if (key === 'left') this.setBeatsPerBar(this.beatsPerBar - 1);
        if (key === 'right') this.setBeatsPerBar(this.beatsPerBar + 1);
        return;
      }
    },
    onKeyDown(evt) {
      if (evt && evt.repeat) return;
      const key = resolveKey(evt);
      if (!key) return;
      
      if (this.panelVisible) {
        evt && evt.preventDefault && evt.preventDefault();
        evt && evt.stopPropagation && evt.stopPropagation();
      }
      const now = Date.now();
      if (!this.panelVisible && now < this.panelCloseLockUntil) {
        evt.preventDefault && evt.preventDefault();
        return;
      }

      if (this.panelVisible) {
        this.updatePanelByKey(key);
        if (key === 'menu' || key === 'back') this.closePanelSafely();
        return;
      }

      if (key === 'left' || key === 'right') {
        evt.preventDefault && evt.preventDefault();
        if (now < this.navBlockUntil) return;
        if (now - this.lastNavAt < 220) return;
        this.lastNavAt = now;
        if (key === 'left') this.prevPage();
        else this.nextPage();
      }
      else if (key === 'up') { evt.preventDefault && evt.preventDefault(); this.adjustBpm(1); }
      else if (key === 'down') { evt.preventDefault && evt.preventDefault(); this.adjustBpm(-1); }
      else if (key === 'enter') {
        evt.preventDefault && evt.preventDefault();
        this.openPanelSafely();
      }
      else if (key === 'menu') { evt.preventDefault && evt.preventDefault(); this.openPanelSafely(); }
      else if (key === 'back') {
        evt.preventDefault && evt.preventDefault();
        // 如果节拍器正在运行，先停止节拍器
        if (this.enabled) {
          this.stopMetronome();
        } else {
          // 节拍器已停止，退出页面
          uni.navigateBack();
        }
      }
    }
  }
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: row;
  background: #0f1115;
  position: relative;
}

.left-section {
  width: 80%;
  height: 100vh;
  padding: 14px;
  display: flex;
}

.right-section {
  width: 20%;
  height: 100vh;
  padding: 14px 14px 14px 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.score-wrap {
  flex: 1;
  border-radius: 10px;
  background: #141a23;
  padding: 12px;
  display: flex;
}

.canvas {
  flex: 1;
  border-radius: 8px;
  background: #f1f4f8;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.score-image {
  width: 100%;
  height: 100%;
  display: block;
  background: #fff;
}

.empty {
  color: #b7c0d0;
  margin-top: 50px;
  text-align: center;
}

.img-error {
  margin: 11px;
  padding: 8px;
  border-radius: 6px;
  background: #3b1212;
}

.img-error text {
  color: #ffc8c8;
  font-size: 9px;
}

.time-info-block {
  background: linear-gradient(135deg, rgba(20, 26, 35, 0.9), rgba(20, 26, 35, 0.7));
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid rgba(185, 174, 230, 0.2);
}

.current-time {
  font-size: 23px;
  font-weight: 700;
  color: #f1c64d;
  letter-spacing: 1px;
  text-align: center;
}

.current-date-cn {
  font-size: 14px;
  color: #b8c6dc;
  text-align: center;
  margin-top: 2px;
}

.elapsed-time {
  font-size: 10px;
  color: #97a3b6;
  text-align: center;
  margin-top: 4px;
  font-weight: 500;
}

.info-block {
  background: #141a23;
  border-radius: 10px;
  padding: 12px;
  margin-top: 30px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.title {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #f5f7fa;
  margin-bottom: 4px;
}

.sub-meta {
  color: #97a3b6;
  font-size: 12px;
  line-height: 1.4;
}

.page-info {
  color: #b8c6dc;
  font-size: 11px;
  margin-top: 2px;
}

.metro-block {
  background: #141a23;
  border-radius: 10px;
  padding: 12px;
  margin-top: 30px;
  display: flex;
  flex-direction: column;
}

.metro-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.metro-title {
  color: #f5f7fa;
  font-size: 14px;
  font-weight: 700;
}

.metro-status {
  color: #97a3b6;
  font-size: 11px;
}

.metro-status.running {
  color: #42ef94;
}

.beat-display {
  background: #0f141c;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 10px;
}

.beat-dots {
  display: flex;
  justify-content: space-between;
  gap: 6px;
}

.light {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  flex-shrink: 0;
}

.light.idle {
  background: #243140;
}

.light.active {
  background: #42ef94;
  box-shadow: 0 0 10px rgba(66, 239, 148, 0.9);
}

.metro-info {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  margin-top: 6px;
  background: rgba(45, 52, 66, 0.3);
  border-radius: 6px;
}

.info-label {
  color: #97a3b6;
  font-size: 11px;
}

.info-value {
  color: #f5f7fa;
  font-size: 13px;
  font-weight: 700;
}

.bpm-value {
  color: #b9aee6;
}

.open-panel-btn {
  margin-top: 4px;
  background: #3b85ff;
  color: #fff;
  border: none;
  font-size: 11px;
  line-height: 1.4;
  padding: 6px 12px;
  height: auto;
  border-radius: 6px;
}
</style>
