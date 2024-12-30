package utils

import (
	"crypto/rand"
	"errors"
	"math/big"
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
func AssertDefined[T any](value T, message string) T {
	if any(value) == any(nil) {
		panic(errors.New(message))
	}

	return value
}

// Wrap a function call with a cache. On first call the wrapped function is invoked to
// obtain a result. Subsequent calls return the cached result.
func Cache[T any](f func() T) func() T {
	value := any(nil)

	return func() T {
		if value == nil {
			value = f()
		}

		return value.(T)
	}
}
