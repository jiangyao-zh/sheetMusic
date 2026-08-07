#!/usr/bin/env bash
# 将 uni-app 构建产物同步到 Android assets，并强制带上 static/gif
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# HBuilderX / CLI 常见产物路径
CANDIDATES=(
  "$ROOT/unpackage/dist/build/app-plus"
  "$ROOT/unpackage/dist/build/app"
  "$ROOT/dist/build/app-plus"
  "$ROOT/dist/build/app"
)

SRC=""
for d in "${CANDIDATES[@]}"; do
  if [[ -d "$d" ]]; then
    SRC="$d"
    break
  fi
done

APPID="__UNI__C9AA01F"
DEST="$ROOT/android/app/src/main/assets/apps/${APPID}/www"

"$ROOT/scripts/ensure-pitch-sprites.sh"

if [[ -z "$SRC" ]]; then
  echo "未找到 uni-app 构建产物。"
  echo "请先用 HBuilderX「发行 → 原生 App-本地打包」或 CLI 构建 App，"
  echo "产物目录之一应为: unpackage/dist/build/app-plus"
  echo
  echo "仍将 gif 资源预置到 Android assets，便于离线壳集成："
  mkdir -p "$DEST/static/gif"
  rsync -a "$ROOT/static/gif/" "$DEST/static/gif/"
  echo "已预置: $DEST/static/gif"
  ls -la "$DEST/static/gif"
  exit 0
fi

mkdir -p "$DEST"
rsync -a --delete \
  --exclude '.DS_Store' \
  "$SRC/" "$DEST/"

# 强制覆盖/补齐动画（防止构建产物漏拷 static/gif）
mkdir -p "$DEST/static/gif"
rsync -a "$ROOT/static/gif/" "$DEST/static/gif/"

echo "已同步 www → $DEST"
echo "动画资源："
ls -la "$DEST/static/gif"
