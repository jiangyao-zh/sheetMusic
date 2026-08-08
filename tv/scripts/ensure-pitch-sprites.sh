#!/usr/bin/env bash
# 确认挺准动画资源存在，供 HBuilderX / 离线打包前检查
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GIF_DIR="$ROOT/static/gif"

required=(
  "spr_stand.png"
  "spr_walking.gif"
  "spr_run.gif"
  "xrk.png"
  "jiepai.png"
)

missing=0
for f in "${required[@]}"; do
  if [[ ! -f "$GIF_DIR/$f" ]]; then
    echo "❌ 缺少动画资源: static/gif/$f"
    missing=1
  else
    echo "✓ static/gif/$f ($(wc -c < "$GIF_DIR/$f") bytes)"
  fi
done

if [[ "$missing" -ne 0 ]]; then
  echo "请将 GIF/PNG 放到 tv/static/gif/ 后再打包。"
  exit 1
fi

echo "✅ static/gif 资源齐全（挺准动画 + 详情页插图 + 节拍器 jiepai），uni-app 打包时会随 static/ 一并打入 APK。"
