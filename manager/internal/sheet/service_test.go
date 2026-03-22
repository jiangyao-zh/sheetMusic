package sheet

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"golang.org/x/crypto/bcrypt"
)

func TestService_Login(t *testing.T) {
	repo := NewMockRepository()
	hash, _ := bcrypt.GenerateFromPassword([]byte("123456"), bcrypt.DefaultCost)
	repo.Users["jiangyiyi"] = &User{ID: 1, Username: "jiangyiyi", PasswordHash: string(hash)}

	svc := NewService(repo)

	token, err := svc.Login("jiangyiyi", "123456")
	assert.NoError(t, err)
	assert.NotEmpty(t, token)

	token, err = svc.Login("jiangyiyi", "wrongpass")
	assert.Error(t, err)
	assert.Empty(t, token)

	token, err = svc.Login("unknown", "123456")
	assert.Error(t, err)
	assert.Empty(t, token)
}

func TestService_LoginPlaintextPasswordMigrate(t *testing.T) {
	repo := NewMockRepository()
	repo.Users["jiangyiyi"] = &User{ID: 1, Username: "jiangyiyi", PasswordHash: "123456"}
	svc := NewService(repo)

	token, err := svc.Login("jiangyiyi", "123456")
	assert.NoError(t, err)
	assert.NotEmpty(t, token)
	assert.NotEqual(t, "123456", repo.Users["jiangyiyi"].PasswordHash)
}

func TestService_LoginAutoCreateDefaultUser(t *testing.T) {
	repo := NewMockRepository()
	svc := NewService(repo)

	token, err := svc.Login("jiangyiyi", "123456")
	assert.NoError(t, err)
	assert.NotEmpty(t, token)
	assert.NotNil(t, repo.Users["jiangyiyi"])
}

func TestService_LoginResetDefaultUserWrongHash(t *testing.T) {
	repo := NewMockRepository()
	hash, _ := bcrypt.GenerateFromPassword([]byte("another-password"), bcrypt.DefaultCost)
	repo.Users["jiangyiyi"] = &User{ID: 1, Username: "jiangyiyi", PasswordHash: string(hash)}
	svc := NewService(repo)

	token, err := svc.Login("jiangyiyi", "123456")
	assert.NoError(t, err)
	assert.NotEmpty(t, token)
	assert.NotEqual(t, string(hash), repo.Users["jiangyiyi"].PasswordHash)
}

func TestService_SheetOperations(t *testing.T) {
	repo := NewMockRepository()
	svc := NewService(repo)

	repo.CreateSheet(&Sheet{Title: "test1", FilePath: "/test1.jpg", ThumbPath: "/thumb1.jpg", BPM: 80, UploadUserID: 1})

	sheets, err := svc.ListSheets("")
	assert.NoError(t, err)
	assert.Len(t, sheets, 1)

	err = svc.UpdateSheet(sheets[0].ID, "new_title", 96)
	assert.NoError(t, err)
	assert.True(t, repo.SheetTitle["new_title"])
	assert.Equal(t, 96, repo.Sheets[sheets[0].ID].BPM)

	repo.CreateSheet(&Sheet{Title: "existing_title", FilePath: "/test2.jpg", ThumbPath: "/thumb2.jpg", BPM: 88, UploadUserID: 1})
	err = svc.UpdateSheet(sheets[0].ID, "existing_title", 88)
	assert.Error(t, err)

	err = svc.UpdateSortOrder(sheets[0].ID, 5)
	assert.NoError(t, err)
	assert.Equal(t, 5, repo.Sheets[sheets[0].ID].SortOrder)

	err = svc.DeleteSheet(sheets[0].ID)
	assert.NoError(t, err)
	assert.Nil(t, repo.Sheets[sheets[0].ID])

	extSheets, err := svc.ListExternal()
	assert.NoError(t, err)
	assert.Len(t, extSheets, 1)
	assert.Equal(t, "existing_title", extSheets[0].Title)
	assert.Equal(t, 88, extSheets[0].BPM)
	assert.Equal(t, "/test2.jpg", extSheets[0].ThumbUrl)
}
