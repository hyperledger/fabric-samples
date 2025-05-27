package config

import (
	"gopkg.in/yaml.v3"
	"io/ioutil"
)

type (
	Config struct {
		Server Server `yaml:"server"`
		Log    Log    `yaml:"log"`
	}

	Server struct {
		Mode string `yaml:"mode"`
	}

	Log struct {
		Level string `yaml:"level"`
		Path  string `yaml:"path"`
	}
)

var config Config

func InitConfig(configPath string) error {
	// 读取配置文件
	yamlFile, err := ioutil.ReadFile(configPath)
	if err != nil {
		return err
	}

	// 解析YAML文件到config结构体
	err = yaml.Unmarshal(yamlFile, &config)
	if err != nil {
		return err
	}
	return nil
}

func GetServerMode() string { return config.Server.Mode }

func GetLogLevel() string { return config.Log.Level }
func GetLogPath() string  { return config.Log.Path }
