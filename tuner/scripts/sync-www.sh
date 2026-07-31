#!/usr/bin/env bash
# 将 uni-app 构建产物同步到 Android assets
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/dist/build/app"
DEST="$ROOT/android/app/src/main/assets/apps/__UNI__PITCH01/www"

if [[ ! -d "$SRC" ]]; then
  echo "未找到构建产物: $SRC"
  echo "请先执行: cd tuner/app && npm install && npm run build:app"
  exit 1
fi

mkdir -p "$DEST"
rsync -a --delete "$SRC/" "$DEST/"
echo "已同步到 $DEST"
