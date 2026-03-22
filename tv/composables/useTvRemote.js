import { onMounted, onUnmounted } from "vue";

const KEY_MAP = {
  4: "back",
  13: "enter",
  19: "up",
  20: "down",
  21: "left",
  22: "right",
  23: "enter",
  27: "back",
  37: "left",
  38: "up",
  39: "right",
  40: "down",
  66: "enter",
  82: "menu"
};

function normalizeKey(evt) {
  const code = evt?.keyCode ?? evt?.which;
  return KEY_MAP[code] || "";
}

export function useTvRemote(handler) {
  const onKey = (evt) => {
    const key = normalizeKey(evt);
    if (!key) return;
    handler?.(key, evt);
  };

  onMounted(() => {
    // #ifdef APP-PLUS
    if (typeof plus !== "undefined" && plus.key) {
      plus.key.addEventListener("keydown", onKey);
    }
    // #endif

    // #ifdef H5
    window.addEventListener("keydown", onKey);
    // #endif
  });

  onUnmounted(() => {
    // #ifdef APP-PLUS
    if (typeof plus !== "undefined" && plus.key) {
      plus.key.removeEventListener("keydown", onKey);
    }
    // #endif

    // #ifdef H5
    window.removeEventListener("keydown", onKey);
    // #endif
  });
}
