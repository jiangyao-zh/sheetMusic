package main

import (
	"app/internal/config"
	"app/internal/middleware"
	"app/internal/sheet"
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

// main 是应用启动入口，负责串联配置、日志、依赖注入和HTTP服务启动。
func main() {
	// 第一步：加载运行配置。
	cfg := config.Load()
	// 第二步：初始化统一日志组件。
	logger := initLogger(cfg.Log.Level)
	defer logger.Sync()
	// 第三步：组装引擎并注册路由。
	engine := setupEngine(cfg, logger)

	// 第四步：启动HTTP监听。
	if err := engine.Run(":" + cfg.Server.Port); err != nil {
		logger.Fatal("server start failed", zap.Error(err))
	}
}

// setupEngine 组装应用依赖和路由。
func setupEngine(cfg *config.Config, logger *zap.Logger) *gin.Engine {
	gin.SetMode(cfg.Server.GinMode)
	engine := gin.New()
	engine.Use(middleware.Recovery(logger))
	engine.Use(middleware.Logger(logger))
	engine.Use(middleware.CORS())

	sheet.InitModule(engine, cfg)
	engine.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status": "ok",
			"name":   cfg.App.Name,
			"env":    cfg.App.Env,
		})
	})
	return engine
}

// initLogger 初始化结构化日志。
func initLogger(level string) *zap.Logger {
	// 解析日志级别，不合法时回退到info。
	parsedLevel := zapcore.InfoLevel
	if err := parsedLevel.Set(level); err != nil {
		parsedLevel = zapcore.InfoLevel
	}

	// 定义日志编码输出格式。
	encoderConfig := zap.NewProductionEncoderConfig()
	encoderConfig.TimeKey = "time"
	encoderConfig.MessageKey = "message"
	encoderConfig.LevelKey = "level"
	encoderConfig.EncodeTime = zapcore.ISO8601TimeEncoder

	// 将日志输出到标准输出，便于容器环境采集。
	core := zapcore.NewCore(
		zapcore.NewJSONEncoder(encoderConfig),
		zapcore.Lock(os.Stdout),
		parsedLevel,
	)

	return zap.New(core, zap.AddCaller())
}
