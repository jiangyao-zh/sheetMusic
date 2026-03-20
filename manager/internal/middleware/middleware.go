/*
 * @Author: jiangyao
 * @Date: 2026-03-06 22:05:50
 * @LastEditors: jiangyao
 * @LastEditTime: 2026-03-06 22:44:09
 * @FilePath: /app/internal/middleware/middleware.go
 * @Description:
 *
 * Copyright (c) 2026 by JY, All Rights Reserved.
 */
package middleware

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

// Logger 记录每个请求的核心访问信息。
func Logger(logger *zap.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		// 记录开始时间用于计算耗时。
		start := time.Now()
		// 执行后续处理链。
		c.Next()
		// 请求结束后输出结构化日志，便于问题排查与监控统计。
		logger.Info("http access",
			zap.String("method", c.Request.Method),
			zap.String("path", c.Request.URL.Path),
			zap.Int("status", c.Writer.Status()),
			zap.Duration("latency", time.Since(start)),
			zap.String("client_ip", c.ClientIP()),
		)
	}
}

// Recovery 统一兜底panic，避免服务崩溃。
func Recovery(logger *zap.Logger) gin.HandlerFunc {
	return gin.CustomRecovery(func(c *gin.Context, err interface{}) {
		logger.Error("panic recovered", zap.Any("error", err))
		// 返回统一错误结构，避免把内部细节暴露给调用方。
		c.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{
			"code":    "INTERNAL_ERROR",
			"message": "internal server error",
		})
	})
}

// CORS 处理跨域请求。
func CORS() gin.HandlerFunc {
	return func(c *gin.Context) {
		// 设置允许的跨域来源、方法、请求头。
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Request-ID")
		// 对预检请求直接返回，减少无效业务处理。
		if c.Request.Method == http.MethodOptions {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		// 继续执行后续处理链。
		c.Next()
	}
}
