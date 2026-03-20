#!/bin/bash
# 部署脚本 deploy.sh
set -e

echo "==> 正在拉取最新代码..."
# git pull origin main

echo "==> 正在编译 Go 后端..."
export GO111MODULE=on
go mod tidy
go build -o bin_app main.go

echo "==> 初始化运行目录..."
mkdir -p public/uploads
mkdir -p web

echo "==> 停止旧服务..."
pkill -f bin_app || echo "未找到运行中的服务"

echo "==> 启动新服务..."
nohup ./bin_app > server.log 2>&1 &

echo "==> 部署完成！"
echo "API 地址: http://127.0.0.1:8080/api"
echo "Web 地址: http://127.0.0.1:8080/web/login.html"