<script>
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
</style>
