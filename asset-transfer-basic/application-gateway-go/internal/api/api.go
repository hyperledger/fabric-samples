package api

import (
	"fmt"

	"github.com/gin-gonic/gin"

	ierror "assetTransfer/internal/error"
	"assetTransfer/internal/grpc"
)

func SubmitTransaction(c *gin.Context) {
	funcName, args, err := getCommonParams(c)
	if err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}

	result, err := grpc.Contract.SubmitTransaction(funcName, args...)
	if err != nil {
		c.JSON(500, gin.H{"error": ierror.ErrorHandling(err)})
		return
	}
	c.JSON(200, gin.H{"result": result})
}

func EvaluateTransaction(c *gin.Context) {
	funcName, args, err := getCommonParams(c)
	if err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}

	result, err := grpc.Contract.EvaluateTransaction(funcName, args...)
	if err != nil {
		c.JSON(500, gin.H{"error": ierror.ErrorHandling(err)})
		return
	}
	c.JSON(200, gin.H{"result": result})
}

func getCommonParams(c *gin.Context) (string, []string, error) {
	type RequestBody struct {
		FuncName string   `json:"func"`
		Args     []string `json:"args"`
	}

	var body RequestBody
	if err := c.ShouldBindJSON(&body); err != nil {
		return "", nil, fmt.Errorf("invalid request body: %v", err)
	}

	if body.FuncName == "" {
		return "", nil, fmt.Errorf("func is required")
	}
	if len(body.Args) == 0 {
		return "", nil, fmt.Errorf("args is required")
	}

	return body.FuncName, body.Args, nil
}
