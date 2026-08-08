function disableDefaultFocusHighlight(view) {
  if (!view || typeof plus === 'undefined' || !plus.android) return;
  try {
    plus.android.invoke(view, 'setDefaultFocusHighlightEnabled', false);
  } catch (e) {
    // ignore
  }
}

function walkDisableFocusHighlight(view, depth) {
  if (!view || depth > 10) return;
  disableDefaultFocusHighlight(view);
  try {
    const count = plus.android.invoke(view, 'getChildCount');
    if (typeof count !== 'number') return;
    for (let i = 0; i < count; i += 1) {
      walkDisableFocusHighlight(plus.android.invoke(view, 'getChildAt', i), depth + 1);
    }
  } catch (e) {
    // ignore
  }
}

/** 关闭 Android TV 默认焦点黄框 */
export function disableAndroidTvFocusHighlight() {
  // #ifdef APP-PLUS
  if (typeof plus === 'undefined' || !plus.android) return;
  try {
    const activity = plus.android.runtimeMainActivity();
    if (!activity) return;
    walkDisableFocusHighlight(activity.getWindow().getDecorView(), 0);
  } catch (e) {
    // ignore
  }
  // #endif
}
