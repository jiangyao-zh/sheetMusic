package sheet

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

type Handler struct {
	svc Service
}

func NewHandler(svc Service) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) Login(c *gin.Context) {
	var req LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, Response{Code: 400, Msg: "invalid parameters"})
		return
	}

	token, err := h.svc.Login(req.Username, req.Password)
	if err != nil {
		c.JSON(http.StatusUnauthorized, Response{Code: 401, Msg: err.Error()})
		return
	}

	c.JSON(http.StatusOK, Response{Code: 200, Msg: "success", Data: gin.H{"token": token}})
}

func (h *Handler) UploadSheet(c *gin.Context) {
	userID := c.GetInt("user_id")

	form, formErr := c.MultipartForm()
	if formErr == nil && form != nil {
		files := form.File["files"]
		if len(files) > 0 {
			uploaded := make([]*Sheet, 0, len(files))
			failed := make([]gin.H, 0)
			for _, file := range files {
				sheet, err := h.svc.UploadSheet(file, userID)
				if err != nil {
					failed = append(failed, gin.H{
						"name": file.Filename,
						"msg":  err.Error(),
					})
					continue
				}
				uploaded = append(uploaded, sheet)
			}
			if len(uploaded) == 0 {
				c.JSON(http.StatusBadRequest, Response{
					Code: 400,
					Msg:  "all files upload failed",
					Data: gin.H{"failed": failed},
				})
				return
			}
			msg := "success"
			if len(failed) > 0 {
				msg = "partial success"
			}
			c.JSON(http.StatusOK, Response{
				Code: 200,
				Msg:  msg,
				Data: gin.H{
					"uploaded": uploaded,
					"failed":   failed,
				},
			})
			return
		}
	}

	file, err := c.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, Response{Code: 400, Msg: "missing file"})
		return
	}
	sheet, err := h.svc.UploadSheet(file, userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, Response{Code: 500, Msg: err.Error()})
		return
	}
	c.JSON(http.StatusOK, Response{Code: 200, Msg: "success", Data: sheet})
}

func (h *Handler) ListSheets(c *gin.Context) {
	keyword := c.Query("keyword")
	sheets, err := h.svc.ListSheets(keyword)
	if err != nil {
		c.JSON(http.StatusInternalServerError, Response{Code: 500, Msg: err.Error()})
		return
	}
	c.JSON(http.StatusOK, Response{Code: 200, Msg: "success", Data: sheets})
}

func (h *Handler) SortSheet(c *gin.Context) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, Response{Code: 400, Msg: "invalid id"})
		return
	}

	var req SortSheetRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, Response{Code: 400, Msg: "invalid parameters"})
		return
	}
	if req.SortOrder == nil {
		c.JSON(http.StatusBadRequest, Response{Code: 400, Msg: "invalid parameters"})
		return
	}

	if err := h.svc.UpdateSortOrder(id, *req.SortOrder); err != nil {
		c.JSON(http.StatusInternalServerError, Response{Code: 500, Msg: err.Error()})
		return
	}
	c.JSON(http.StatusOK, Response{Code: 200, Msg: "success"})
}

func (h *Handler) RenameSheet(c *gin.Context) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, Response{Code: 400, Msg: "invalid id"})
		return
	}

	var req RenameSheetRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, Response{Code: 400, Msg: "invalid parameters"})
		return
	}

	if err := h.svc.RenameSheet(id, req.Title); err != nil {
		c.JSON(http.StatusInternalServerError, Response{Code: 500, Msg: err.Error()})
		return
	}
	c.JSON(http.StatusOK, Response{Code: 200, Msg: "success"})
}

func (h *Handler) DeleteSheet(c *gin.Context) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, Response{Code: 400, Msg: "invalid id"})
		return
	}

	if err := h.svc.DeleteSheet(id); err != nil {
		c.JSON(http.StatusInternalServerError, Response{Code: 500, Msg: err.Error()})
		return
	}
	c.JSON(http.StatusOK, Response{Code: 200, Msg: "success"})
}

func (h *Handler) ListExternal(c *gin.Context) {
	sheets, err := h.svc.ListExternal()
	if err != nil {
		c.JSON(http.StatusInternalServerError, Response{Code: 500, Msg: err.Error()})
		return
	}
	c.JSON(http.StatusOK, Response{Code: 200, Msg: "success", Data: sheets})
}
