#!/bin/bash
set -e

mkdir -p logs
go build -o gin_template main.go
nohup ./gin_template > logs/app.log 2>&1 &
echo "服务已启动，PID: $!"
