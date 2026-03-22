package config

import (
	"os"

	"github.com/joho/godotenv"
)

type Config struct {
	// App 保存应用元信息，如名称和环境标识。
	App AppConfig `json:"app"`
	// Server 保存HTTP服务配置。
	Server ServerConfig `json:"server"`
	// Database 保存数据库连接模板参数。
	Database DatabaseConfig `json:"database"`
	// Log 保存日志输出级别。
	Log LogConfig `json:"log"`
}

type AppConfig struct {
	// Name 是应用名称，用于日志和健康检查展示。
	Name string `json:"name"`
	// Env 是环境标识，例如 local、test、prod。
	Env string `json:"env"`
}

type ServerConfig struct {
	// Port 是HTTP监听端口。
	Port string `json:"port"`
	// GinMode 控制Gin运行模式。
	GinMode string `json:"gin_mode"`
}

type DatabaseConfig struct {
	// Driver 是数据库类型标识。
	Driver string `json:"driver"`
	// DSN 是数据库连接串模板。
	DSN string `json:"dsn"`
}

type LogConfig struct {
	// Level 是日志级别。
	Level string `json:"level"`
}

// Load 负责从环境变量中加载配置并填充默认值。
func Load() *Config {
	// 优先从项目根目录加载 .env 文件（manager 的上一级目录）
	_ = godotenv.Load("../.env")
	// 如果根目录没有，尝试当前目录的 .env 文件
	_ = godotenv.Load(".env")

	return &Config{
		App: AppConfig{
			Name: getEnv("APP_NAME", "sheet-music-manager"),
			Env:  getEnv("APP_ENV", "production"),
		},
		Server: ServerConfig{
			Port:    getEnv("SERVER_PORT", "8080"),
			GinMode: getEnv("GIN_MODE", "release"),
		},
		Database: DatabaseConfig{
			Driver: getEnv("DB_DRIVER", "mysql"),
			DSN:    getEnv("DATABASE_DSN", "username:password@tcp(host:3306)/database?charset=utf8mb4&parseTime=True&loc=Local"),
		},
		Log: LogConfig{
			Level: getEnv("LOG_LEVEL", "info"),
		},
	}
}

func getEnv(key, fallback string) string {
	// 优先使用环境变量值，确保部署环境可覆盖默认值。
	if value := os.Getenv(key); value != "" {
		return value
	}
	// 回退到代码内置默认值，保证本地开箱即用。
	return fallback
}
