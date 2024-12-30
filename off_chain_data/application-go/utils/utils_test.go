package utils_test

import (
	"offChainData/utils"
	"testing"
)

func Test_cachePrimitiveFunctionResult(t *testing.T) {
	counter := 0
	f := func() int {
		counter++
		return 5
	}

	cachedFunc := utils.Cache(f)
	result1 := cachedFunc()
	result2 := cachedFunc()

	if counter != 1 {
		t.Error("expected counter to be 1, but got", counter)
	}

	if result1 != 5 || result2 != 5 {
		t.Fatal("expected results to be 5, but got", result1, result2)
	}
}

func Test_cacheWrappedPrimitiveFunctionResult(t *testing.T) {
	controlValue := 5
	multiplyControlValueBy := func(n int) int { controlValue *= n; return controlValue }

	cachedFunc := utils.Cache(func() int { return multiplyControlValueBy(5) })
	result1 := cachedFunc()
	result2 := cachedFunc()

	if controlValue != 25 {
		t.Error("expected control value to be 25, but got", controlValue)
	}

	if result1 != 25 || result2 != 25 {
		t.Fatal("expected cached results to be 25, but got", result1, result2)
	}
}

func Test_cacheWrappedDataStructureResult(t *testing.T) {
	type GreetMe struct {
		helloTo string
	}

	controlStruct := &GreetMe{helloTo: "Hello "}
	greet := func(name string) *GreetMe { controlStruct.helloTo += name; return controlStruct }

	cachedFunc := utils.Cache(func() *GreetMe { return greet("John Doe") })
	result1 := cachedFunc().helloTo
	result2 := cachedFunc().helloTo

	if controlStruct.helloTo != "Hello John Doe" {
		t.Error("expected control value to be 'Hello John Doe', but got", controlStruct)
	}

	if result1 != "Hello John Doe" || result2 != "Hello John Doe" {
		t.Fatal("expected cached results to be 'Hello John Doe', but got", result1, result2)
	}
}
