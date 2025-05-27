package cmd

import (
	"assetTransfer/internal/conf"
	"assetTransfer/internal/grpc"
	"assetTransfer/internal/log"
	"assetTransfer/internal/router"
	"fmt"
	"github.com/gin-gonic/gin"
	"github.com/spf13/cobra"
)

var (
	port       string
	configPath string
)

var rootCmd = &cobra.Command{
	Use:  "ledger-gw",
	RunE: run,
}

func init() {
	rootCmd.PersistentFlags().StringVarP(&port, "port", "p", "8080", "Port to run the server on")
	rootCmd.PersistentFlags().StringVarP(&configPath, "config", "c", "./config.yaml", "Path of the configuration file")

	if err := rootCmd.MarkPersistentFlagRequired("port"); err != nil {
		fmt.Println(err)
	}
	if err := rootCmd.MarkPersistentFlagRequired("config"); err != nil {
		fmt.Println(err)
	}
}

func run(_ *cobra.Command, _ []string) error {
	if err := config.InitConfig(configPath); err != nil {
		return err
	}
	if err := log.InitLog(config.GetLogLevel(), config.GetLogPath()); err != nil {
		return err
	}

	// 初始化网关连接
	grpc.InitGWConnect()
	defer grpc.CloseGWConnect()

	gin.SetMode(config.GetServerMode())
	r := gin.Default()
	router.SetupRoutes(r)
	if err := r.Run(":" + port); err != nil {
		fmt.Println("Failed to start server:", err)
		return err
	}
	return nil
}

func Execute() error {
	if err := rootCmd.Execute(); err != nil {
		return err
	}
	return nil
}
