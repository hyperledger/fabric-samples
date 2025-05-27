package router

import (
	"assetTransfer/internal/api"
	"github.com/gin-gonic/gin"
)

// SetupRoutes 设置路由
func SetupRoutes(r *gin.Engine) {
	// 定义路由
	r.POST("/submit", api.SubmitTransaction)
	r.POST("/evaluate", api.EvaluateTransaction)
}
