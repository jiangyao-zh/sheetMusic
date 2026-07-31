package sheet

import (
	"errors"
	"io"
	"mime/multipart"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
)

var jwtSecret = []byte(loadJWTSecret())

func loadJWTSecret() string {
	if v := os.Getenv("JWT_SECRET"); v != "" {
		return v
	}
	return "change-me-in-production"
}

type Service interface {
	Login(username, password string) (string, error)
	UploadSheet(file *multipart.FileHeader, userID int) (*Sheet, error)
	ListSheets(keyword string) ([]*Sheet, error)
	UpdateSortOrder(id int, order int) error
	UpdateSheet(id int, title string, bpm int, beatNumerator int, beatDenominator int) error
	DeleteSheet(id int) error
	ListExternal() ([]SheetExternal, error)
}

type serviceImpl struct {
	repo Repository
}

func NewService(repo Repository) Service {
	return &serviceImpl{repo: repo}
}

func (s *serviceImpl) Login(username, password string) (string, error) {
	isDefaultCredential := username == "jiangyiyi" && password == "123456"
	user, err := s.repo.GetUserByUsername(username)
	if err != nil {
		return "", err
	}
	if user == nil {
		if !isDefaultCredential {
			return "", errors.New("invalid username or password")
		}
		passwordHash, hashErr := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
		if hashErr != nil {
			return "", hashErr
		}
		newUser := &User{
			Username:     username,
			PasswordHash: string(passwordHash),
		}
		if createErr := s.repo.CreateUser(newUser); createErr != nil {
			return "", createErr
		}
		user = newUser
	}

	err = bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(password))
	if err != nil {
		if user.PasswordHash != password && !isDefaultCredential {
			return "", errors.New("invalid username or password")
		}
		passwordHash, hashErr := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
		if hashErr != nil {
			return "", hashErr
		}
		if updateErr := s.repo.UpdateUserPasswordHash(user.ID, string(passwordHash)); updateErr != nil {
			return "", updateErr
		}
		user.PasswordHash = string(passwordHash)
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"user_id":  user.ID,
		"username": user.Username,
		"exp":      time.Now().Add(time.Hour * 24).Unix(),
	})
	return token.SignedString(jwtSecret)
}

func (s *serviceImpl) UploadSheet(file *multipart.FileHeader, userID int) (*Sheet, error) {
	ext := strings.ToLower(filepath.Ext(file.Filename))
	if ext != ".jpg" && ext != ".jpeg" && ext != ".png" {
		return nil, errors.New("only jpg/png files are allowed")
	}

	if file.Size > 10*1024*1024 {
		return nil, errors.New("file size exceeds 10MB")
	}

	title := strings.TrimSuffix(file.Filename, filepath.Ext(file.Filename))
	exists, err := s.repo.CheckTitleExists(title)
	if err != nil {
		return nil, err
	}
	if exists {
		return nil, errors.New("file with this name already exists")
	}

	uploadDir := "public/uploads"
	os.MkdirAll(uploadDir, os.ModePerm)

	filename := time.Now().Format("20060102150405") + "_" + file.Filename
	filePath := filepath.Join(uploadDir, filename)
	thumbPath := filepath.Join(uploadDir, "thumb_"+filename)

	src, err := file.Open()
	if err != nil {
		return nil, err
	}
	defer src.Close()

	dst, err := os.Create(filePath)
	if err != nil {
		return nil, err
	}
	defer dst.Close()
	if _, err = io.Copy(dst, src); err != nil {
		return nil, err
	}

	// 缩略图直接使用原图，不压缩，保持清晰度
	thumbPath = filePath

	sheet := &Sheet{
		Title:           title,
		FilePath:        "/" + filePath,
		ThumbPath:       "/" + thumbPath,
		BPM:             80,
		BeatNumerator:   4,
		BeatDenominator: 4,
		UploadUserID:    userID,
	}
	if err := s.repo.CreateSheet(sheet); err != nil {
		os.Remove(filePath)
		os.Remove(thumbPath)
		return nil, err
	}

	return sheet, nil
}

func (s *serviceImpl) ListSheets(keyword string) ([]*Sheet, error) {
	return s.repo.GetSheets(keyword)
}

func (s *serviceImpl) UpdateSortOrder(id int, order int) error {
	return s.repo.UpdateSheetSort(id, order)
}

func (s *serviceImpl) UpdateSheet(id int, title string, bpm int, beatNumerator int, beatDenominator int) error {
	current, err := s.repo.GetSheetByID(id)
	if err != nil {
		return err
	}
	if current == nil {
		return errors.New("sheet not found")
	}
	if current.Title != title {
		exists, checkErr := s.repo.CheckTitleExists(title)
		if checkErr != nil {
			return checkErr
		}
		if exists {
			return errors.New("title already exists")
		}
	}
	return s.repo.UpdateSheet(id, title, bpm, beatNumerator, beatDenominator)
}

func (s *serviceImpl) DeleteSheet(id int) error {
	sheet, err := s.repo.GetSheetByID(id)
	if err != nil {
		return err
	}

	err = s.repo.DeleteSheet(id)
	if err != nil {
		return err
	}

	if sheet.FilePath != "" {
		os.Remove(strings.TrimPrefix(sheet.FilePath, "/"))
	}
	if sheet.ThumbPath != "" {
		os.Remove(strings.TrimPrefix(sheet.ThumbPath, "/"))
	}

	return nil
}

func (s *serviceImpl) ListExternal() ([]SheetExternal, error) {
	sheets, err := s.repo.GetSheets("")
	if err != nil {
		return nil, err
	}
	var res []SheetExternal
	for _, sheet := range sheets {
		res = append(res, SheetExternal{
			ID:              sheet.ID,
			Title:           sheet.Title,
			ThumbUrl:        sheet.FilePath,
			BPM:             sheet.BPM,
			BeatNumerator:   sheet.BeatNumerator,
			BeatDenominator: sheet.BeatDenominator,
			UploadTime:      sheet.CreatedAt,
		})
	}
	return res, nil
}
