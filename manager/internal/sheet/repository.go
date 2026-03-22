package sheet

import (
	"errors"

	"gorm.io/gorm"
)

type Repository interface {
	GetUserByUsername(username string) (*User, error)
	CreateUser(user *User) error
	UpdateUserPasswordHash(id int, passwordHash string) error
	CreateSheet(sheet *Sheet) error
	GetSheets(keyword string) ([]*Sheet, error)
	GetSheetByID(id int) (*Sheet, error)
	UpdateSheetSort(id int, sortOrder int) error
	UpdateSheet(id int, title string, bpm int) error
	DeleteSheet(id int) error
	CheckTitleExists(title string) (bool, error)
}

type repositoryImpl struct {
	db *gorm.DB
}

func NewRepository(db *gorm.DB) Repository {
	return &repositoryImpl{db: db}
}

func (r *repositoryImpl) GetUserByUsername(username string) (*User, error) {
	var user User
	err := r.db.Where("username = ?", username).First(&user).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &user, nil
}

func (r *repositoryImpl) CreateUser(user *User) error {
	return r.db.Create(user).Error
}

func (r *repositoryImpl) UpdateUserPasswordHash(id int, passwordHash string) error {
	return r.db.Model(&User{}).Where("id = ?", id).Update("password_hash", passwordHash).Error
}

func (r *repositoryImpl) CreateSheet(sheet *Sheet) error {
	return r.db.Create(sheet).Error
}

func (r *repositoryImpl) GetSheets(keyword string) ([]*Sheet, error) {
	var sheets []*Sheet
	query := r.db.Model(&Sheet{})
	if keyword != "" {
		query = query.Where("MATCH(title) AGAINST(? IN BOOLEAN MODE)", keyword+"*")
	}
	err := query.Order("sort_order ASC, id DESC").Find(&sheets).Error
	return sheets, err
}

func (r *repositoryImpl) GetSheetByID(id int) (*Sheet, error) {
	var sheet Sheet
	err := r.db.First(&sheet, id).Error
	if err != nil {
		return nil, err
	}
	return &sheet, nil
}

func (r *repositoryImpl) UpdateSheetSort(id int, sortOrder int) error {
	return r.db.Model(&Sheet{}).Where("id = ?", id).Update("sort_order", sortOrder).Error
}

func (r *repositoryImpl) UpdateSheet(id int, title string, bpm int) error {
	return r.db.Model(&Sheet{}).Where("id = ?", id).Updates(map[string]interface{}{
		"title": title,
		"bpm":   bpm,
	}).Error
}

func (r *repositoryImpl) DeleteSheet(id int) error {
	return r.db.Delete(&Sheet{}, id).Error
}

func (r *repositoryImpl) CheckTitleExists(title string) (bool, error) {
	var count int64
	err := r.db.Model(&Sheet{}).Where("title = ?", title).Count(&count).Error
	return count > 0, err
}
