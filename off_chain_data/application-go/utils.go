package main

import (
	"os"
)

func envOrDefault(key, defaultValue string) string {
	result := os.Getenv(key)
	if result == "" {
		return defaultValue
	}
	return result
}
