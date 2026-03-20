package sheet

import (
	"github.com/gin-gonic/gin"
)

func RegisterRoutes(r *gin.Engine, handler *Handler) {
	api := r.Group("/api")
	{
		api.POST("/auth/login", handler.Login)
		api.GET("/sheets/external", handler.ListExternal)

		protected := api.Group("")
		protected.Use(AuthMiddleware())
		{
			protected.POST("/sheets", handler.UploadSheet)
			protected.GET("/sheets", handler.ListSheets)
			protected.PUT("/sheets/:id/sort", handler.SortSheet)
			protected.PUT("/sheets/:id", handler.RenameSheet)
			protected.DELETE("/sheets/:id", handler.DeleteSheet)
		}
	}
}
