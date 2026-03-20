package sheet

import (
	"app/internal/config"
	"log"

	"github.com/gin-gonic/gin"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
)

func InitModule(r *gin.Engine, cfg *config.Config) {
	db, err := gorm.Open(mysql.Open(cfg.Database.DSN), &gorm.Config{})
	if err != nil {
		log.Fatalf("failed to connect to database: %v", err)
	}

	repo := NewRepository(db)
	svc := NewService(repo)
	handler := NewHandler(svc)

	RegisterRoutes(r, handler)
	r.Static("/public/uploads", "./public/uploads")
	r.Static("/web", "./web")
}
