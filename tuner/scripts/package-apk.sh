#!/usr/bin/env bash
# 构建前端并尝试打出 debug APK（需要本机 Android SDK）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> 1) 构建 uni-app"
cd "$ROOT/app"
npm install
npm run build:app

echo "==> 2) 同步 www 资源"
"$ROOT/scripts/sync-www.sh"

echo "==> 3) YIN 算法回归"
node "$ROOT/scripts/verify-yin.mjs"

if [[ ! -f "$ROOT/android/local.properties" ]]; then
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    echo "sdk.dir=${ANDROID_HOME}" > "$ROOT/android/local.properties"
  elif [[ -d "${HOME}/Library/Android/sdk" ]]; then
    echo "sdk.dir=${HOME}/Library/Android/sdk" > "$ROOT/android/local.properties"
  else
    echo ""
    echo "未检测到 Android SDK（local.properties / ANDROID_HOME）。"
    echo "算法与前端资源已就绪；请安装 Android Studio 后执行："
    echo "  cd tuner/android && ./gradlew :app:assembleDebug"
    echo "详见 docs/BUILD_ANDROID.md"
    exit 0
  fi
fi

echo "==> 4) Gradle assembleDebug"
cd "$ROOT/android"
./gradlew :app:assembleDebug

APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  mkdir -p "$ROOT/dist"
  cp "$APK" "$ROOT/dist/tuner-debug.apk"
  echo "APK 已输出: $ROOT/dist/tuner-debug.apk"
else
  echo "未找到 APK，请检查 Gradle 日志"
  exit 1
fi
