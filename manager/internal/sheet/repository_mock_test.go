package sheet

import (
	"errors"
)

type MockRepository struct {
	Users      map[string]*User
	Sheets     map[int]*Sheet
	SheetTitle map[string]bool
	NextID     int
}

func NewMockRepository() *MockRepository {
	return &MockRepository{
		Users:      make(map[string]*User),
		Sheets:     make(map[int]*Sheet),
		SheetTitle: make(map[string]bool),
		NextID:     1,
	}
}

func (m *MockRepository) GetUserByUsername(username string) (*User, error) {
	if user, ok := m.Users[username]; ok {
		return user, nil
	}
	return nil, nil
}

func (m *MockRepository) CreateUser(user *User) error {
	user.ID = m.NextID
	m.NextID++
	m.Users[user.Username] = user
	return nil
}

func (m *MockRepository) UpdateUserPasswordHash(id int, passwordHash string) error {
	for _, user := range m.Users {
		if user.ID == id {
			user.PasswordHash = passwordHash
			return nil
		}
	}
	return errors.New("not found")
}

func (m *MockRepository) CreateSheet(sheet *Sheet) error {
	sheet.ID = m.NextID
	m.NextID++
	m.Sheets[sheet.ID] = sheet
	m.SheetTitle[sheet.Title] = true
	return nil
}

func (m *MockRepository) GetSheets(keyword string) ([]*Sheet, error) {
	var list []*Sheet
	for _, s := range m.Sheets {
		list = append(list, s)
	}
	return list, nil
}

func (m *MockRepository) GetSheetByID(id int) (*Sheet, error) {
	if s, ok := m.Sheets[id]; ok {
		return s, nil
	}
	return nil, errors.New("not found")
}

func (m *MockRepository) UpdateSheetSort(id int, sortOrder int) error {
	if s, ok := m.Sheets[id]; ok {
		s.SortOrder = sortOrder
		return nil
	}
	return errors.New("not found")
}

func (m *MockRepository) UpdateSheetTitle(id int, title string) error {
	if s, ok := m.Sheets[id]; ok {
		delete(m.SheetTitle, s.Title)
		s.Title = title
		m.SheetTitle[title] = true
		return nil
	}
	return errors.New("not found")
}

func (m *MockRepository) DeleteSheet(id int) error {
	if s, ok := m.Sheets[id]; ok {
		delete(m.SheetTitle, s.Title)
		delete(m.Sheets, id)
		return nil
	}
	return errors.New("not found")
}

func (m *MockRepository) CheckTitleExists(title string) (bool, error) {
	return m.SheetTitle[title], nil
}
