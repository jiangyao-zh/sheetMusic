package sheet

import (
	"time"
)

type User struct {
	ID           int       `json:"id" gorm:"primaryKey;autoIncrement"`
	Username     string    `json:"username" gorm:"type:varchar(50);not null;unique"`
	PasswordHash string    `json:"-" gorm:"type:varchar(255);not null"`
	CreatedAt    time.Time `json:"created_at" gorm:"autoCreateTime"`
	UpdatedAt    time.Time `json:"updated_at" gorm:"autoUpdateTime"`
}

type Sheet struct {
	ID           int       `json:"id" gorm:"primaryKey;autoIncrement"`
	Title        string    `json:"title" gorm:"type:varchar(255);not null;index:,class:FULLTEXT"`
	FilePath     string    `json:"file_path" gorm:"type:varchar(500);not null"`
	ThumbPath    string    `json:"thumb_path" gorm:"type:varchar(500);not null"`
	BPM          int       `json:"bpm" gorm:"column:bpm;type:int;not null;default:80"`
	SortOrder    int       `json:"sort_order" gorm:"type:int;not null;default:0;index"`
	UploadUserID int       `json:"upload_user_id" gorm:"type:int;not null"`
	CreatedAt    time.Time `json:"created_at" gorm:"autoCreateTime"`
	UpdatedAt    time.Time `json:"updated_at" gorm:"autoUpdateTime"`
}

type Response struct {
	Code int         `json:"code"`
	Msg  string      `json:"msg"`
	Data interface{} `json:"data"`
}

type LoginRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

type SortSheetRequest struct {
	SortOrder *int `json:"sort_order" binding:"required,gte=0"`
}

type UpdateSheetRequest struct {
	Title string `json:"title" binding:"required"`
	BPM   *int   `json:"bpm" binding:"required,gte=20,lte=300"`
}

type SheetExternal struct {
	ID         int       `json:"id"`
	Title      string    `json:"title"`
	ThumbUrl   string    `json:"thumbUrl"`
	BPM        int       `json:"bpm"`
	UploadTime time.Time `json:"uploadTime"`
}
