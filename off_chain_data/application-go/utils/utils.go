package utils

import (
	"crypto/rand"
	"errors"
	"fmt"
	"math/big"
	"os"
)

// Pick a random element from an array.
func RandomElement(values []string) string {
	result := values[RandomInt(len(values))]
	return result
}

// Generate a random integer in the range 0 to max - 1.
func RandomInt(max int) int {
	result, err := rand.Int(rand.Reader, big.NewInt(int64(max)))
	if err != nil {
		panic(err)
	}

	return int(result.Int64())
}

// Pick a random element from an array, excluding the current value.
func DifferentElement(values []string, currentValue string) string {
	candidateValues := []string{}
	for _, v := range values {
		if v != currentValue {
			candidateValues = append(candidateValues, v)
		}
	}
	return RandomElement(candidateValues)
}

// Return the value if it is defined; otherwise panics with an error message.
func AssertDefined[T any](value T, message string) (T, error) {
	if any(value) == any(nil) {
		var zeroValue T
		return zeroValue, errors.New(message)
	}

	return value, nil
}

// Wrap a function call with a cache. On first call the wrapped function is invoked to
// obtain a result. Subsequent calls return the cached result.
func Cache[T any](f func() (T, error)) func() (T, error) {
	var value T
	var err error
	var cached bool

	return func() (T, error) {
		if !cached {
			value, err = f()
			if err != nil {
				var zeroValue T
				return zeroValue, fmt.Errorf("in Cache: %w", err)
			}
			cached = true
		}
		return value, nil
	}
}

func EnvOrDefault(key, defaultValue string) string {
	result := os.Getenv(key)
	if result == "" {
		return defaultValue
	}
	return result
}
