package utils

import (
	"crypto/rand"
	"errors"
	"math/big"
)

func RandomElement(values []string) string {
	result := values[RandomInt(len(values))]
	return result
}

func RandomInt(max int) int {
	result, err := rand.Int(rand.Reader, big.NewInt(int64(max)))
	if err != nil {
		panic(err)
	}

	return int(result.Int64())
}

func DifferentElement(values []string, currentValue string) string {
	candidateValues := []string{}
	for _, v := range values {
		if v != currentValue {
			candidateValues = append(candidateValues, v)
		}
	}
	return RandomElement(candidateValues)
}

func AssertDefined[T any](value T, message string) T {
	if any(value) == any(nil) {
		panic(errors.New(message))
	}

	return value
}
