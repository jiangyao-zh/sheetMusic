<script>
import { disableAndroidTvFocusHighlight } from '@/src/utils/tvFocus';

export default {
  onLaunch() {
    // #ifdef APP-PLUS
    if (typeof plus !== "undefined" && plus.screen && plus.screen.lockOrientation) {
      try {
        plus.screen.lockOrientation("landscape-primary");
      } catch (e) {
        // ignore orientation lock failures on unsupported devices
      }
    }
    disableAndroidTvFocusHighlight();
    // #endif
  },
  onShow() {
    // #ifdef APP-PLUS
    if (typeof plus !== "undefined" && plus.screen && plus.screen.lockOrientation) {
      try {
        plus.screen.lockOrientation("landscape-primary");
      } catch (e) {
        // ignore orientation lock failures on unsupported devices
      }
    }
    disableAndroidTvFocusHighlight();
    // #endif
  },
  onHide() {}
};
</script>

<style>
page {
  background: #0f1115;
  color: #f3f5f7;
  font-family: "Noto Sans SC", "PingFang SC", sans-serif;
}

scroll-view,
view,
.uni-scroll-view,
.uni-scroll-view-content {
  outline: none !important;
  -webkit-tap-highlight-color: transparent;
}
scroll-view:focus,
view:focus,
.uni-scroll-view:focus,
.uni-scroll-view-content:focus {
  outline: none !important;
  border-color: transparent !important;
  box-shadow: none !important;
}
</style>
