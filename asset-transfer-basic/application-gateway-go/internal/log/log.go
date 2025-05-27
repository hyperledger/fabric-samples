package log

import (
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"os"
	"path/filepath"
)

var logger *zap.Logger

func InitLog(level, path string) error {
	// 配置日志级别
	var logLevel zapcore.Level
	switch level {
	case "debug":
		logLevel = zap.DebugLevel
	case "info":
		logLevel = zap.InfoLevel
	case "warn":
		logLevel = zap.WarnLevel
	case "error":
		logLevel = zap.ErrorLevel
	case "dpanic":
		logLevel = zap.DPanicLevel
	case "panic":
		logLevel = zap.PanicLevel
	case "fatal":
		logLevel = zap.FatalLevel
	default:
		logLevel = zap.InfoLevel
	}

	// 配置日志输出到文件
	fileEncoderConfig := zap.NewProductionEncoderConfig()
	fileEncoderConfig.TimeKey = "timestamp"
	fileEncoderConfig.EncodeTime = zapcore.ISO8601TimeEncoder
	fileEncoder := zapcore.NewJSONEncoder(fileEncoderConfig)

	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return err
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil {
		return err
	}

	fileWriter := zapcore.AddSync(file)
	fileCore := zapcore.NewCore(fileEncoder, fileWriter, logLevel)

	// 创建 logger
	logger = zap.New(fileCore, zap.AddCaller(), zap.AddStacktrace(zap.ErrorLevel))
	return nil
}

func GetLogger() *zap.Logger {
	return logger
}
