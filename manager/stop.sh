#!/bin/bash
set -e

PIDS=$(ps aux | grep gin_template | grep -v grep | awk '{print $2}')
if [ -z "$PIDS" ]; then
  echo "未发现运行中的服务"
  exit 0
fi

for PID in $PIDS; do
  kill -TERM "$PID"
done

echo "服务已停止"
