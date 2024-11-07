package main

import (
	"crypto/rand"
	"errors"
	"math/big"
)

func randomElement(values []string) string {
	result := values[randomInt(len(values))]
	return result
}

func randomInt(max int) int {
	result, err := rand.Int(rand.Reader, big.NewInt(int64(max)))
	if err != nil {
		panic(err)
	}

	return int(result.Int64())
}

func differentElement(values []string, currentValue string) string {
	candidateValues := []string{}
	for _, v := range values {
		if v != currentValue {
			candidateValues = append(candidateValues, v)
		}
	}
	return randomElement(candidateValues)
}

func assertDefined[T any](value T, message string) T {
	if any(value) == any(nil) {
		panic(errors.New(message))
	}

	return value
}
