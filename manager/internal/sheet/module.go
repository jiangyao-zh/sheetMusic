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
	if err := ensureSheetColumns(db); err != nil {
		log.Fatalf("failed to prepare sheet schema: %v", err)
	}

	repo := NewRepository(db)
	svc := NewService(repo)
	handler := NewHandler(svc)

	RegisterRoutes(r, handler)
	r.Static("/public/uploads", "./public/uploads")
	r.Static("/web", "./web")
}

func ensureSheetColumns(db *gorm.DB) error {
	if !db.Migrator().HasColumn(&Sheet{}, "bpm") {
		if err := db.Exec("ALTER TABLE sheets ADD COLUMN bpm INT NOT NULL DEFAULT 80 AFTER thumb_path").Error; err != nil {
			return err
		}
	}
	if !db.Migrator().HasColumn(&Sheet{}, "beat_numerator") {
		if err := db.Exec("ALTER TABLE sheets ADD COLUMN beat_numerator INT NOT NULL DEFAULT 4 AFTER bpm").Error; err != nil {
			return err
		}
	}
	if !db.Migrator().HasColumn(&Sheet{}, "beat_denominator") {
		if err := db.Exec("ALTER TABLE sheets ADD COLUMN beat_denominator INT NOT NULL DEFAULT 4 AFTER beat_numerator").Error; err != nil {
			return err
		}
	}
	return nil
}
