package utils_test

import (
	"errors"
	"offChainData/utils"
	"testing"
)

func Test_cachePrimitiveFunctionResult(t *testing.T) {
	counter := 0
	f := func() (int, error) {
		counter++
		return 5, nil
	}

	cachedFunc := utils.Cache(f)
	result1, err := cachedFunc()
	if err != nil {
		t.Fatal("unexpected error:", err)
	}
	result2, err := cachedFunc()
	if err != nil {
		t.Fatal("unexpected error:", err)
	}

	if counter != 1 {
		t.Error("expected counter to be 1, but got", counter)
	}

	if result1 != 5 || result2 != 5 {
		t.Fatal("expected results to be 5, but got", result1, result2)
	}
}

func Test_whenCachedFunctionsErrors_returnError(t *testing.T) {
	errorMsg := "error"
	f := func() (int, error) {
		return 0, errors.New(errorMsg)
	}

	cachedFunc := utils.Cache(f)
	_, err := cachedFunc()
	if err == nil {
		t.Fatal("expected error, but got", err)
	}

	if err.Error() != errorMsg {
		t.Fatal("expected error message to be 'error', but got", err)
	}
}

func Test_cacheWrappedPrimitiveFunctionResult(t *testing.T) {
	controlValue := 5
	multiplyControlValueBy := func(n int) (int, error) { controlValue *= n; return controlValue, nil }

	cachedFunc := utils.Cache(func() (int, error) { return multiplyControlValueBy(5) })
	result1, err := cachedFunc()
	if err != nil {
		t.Fatal("unexpected error:", err)
	}
	result2, err := cachedFunc()
	if err != nil {
		t.Fatal("unexpected error:", err)
	}

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
	greet := func(name string) (*GreetMe, error) { controlStruct.helloTo += name; return controlStruct, nil }

	cachedFunc := utils.Cache(func() (*GreetMe, error) { return greet("John Doe") })
	result1, err := cachedFunc()
	if err != nil {
		t.Fatal("unexpected error:", err)
	}
	result2, err := cachedFunc()
	if err != nil {
		t.Fatal("unexpected error:", err)
	}

	if controlStruct.helloTo != "Hello John Doe" {
		t.Error("expected control value to be 'Hello John Doe', but got", controlStruct)
	}

	if result1.helloTo != "Hello John Doe" || result2.helloTo != "Hello John Doe" {
		t.Fatal("expected cached results to be 'Hello John Doe', but got", result1.helloTo, result2.helloTo)
	}
}
