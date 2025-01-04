package store

import (
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"offChainData/utils"
	"os"
	"strconv"
	"strings"
)

var storeFile = utils.EnvOrDefault("STORE_FILE", "store.log")
var SimulatedFailureCount = getSimulatedFailureCount()
var transactionCount uint = 0 // Used only to simulate failures

// Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
// This implementation just writes to a file.
func ApplyWritesToOffChainStore(data LedgerUpdate) error {
	funcName := "ApplyWritesToOffChainStore"

	if err := simulateFailureIfRequired(); err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}

	writes := []string{}
	for _, write := range data.Writes {
		marshaled, err := json.Marshal(write)
		if err != nil {
			return fmt.Errorf("in %s: %w", funcName, err)
		}

		writes = append(writes, string(marshaled))
	}

	f, err := os.OpenFile(storeFile, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}

	if _, err := f.Write([]byte(strings.Join(writes, "\n") + "\n")); err != nil {
		f.Close()
		return fmt.Errorf("in %s: %w", funcName, err)
	}

	if err := f.Close(); err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}

	return nil
}

func simulateFailureIfRequired() error {
	if SimulatedFailureCount > 0 && transactionCount >= SimulatedFailureCount {
		transactionCount = 0
		return errors.New("expected error: simulated write failure")
	}

	transactionCount += 1

	return nil
}

func getSimulatedFailureCount() uint {
	valueAsString := utils.EnvOrDefault("SIMULATED_FAILURE_COUNT", "0")
	valueAsFloat, err := strconv.ParseFloat(valueAsString, 64)
	if err != nil {
		panic(err)
	}

	result := math.Floor(valueAsFloat)
	if valueAsFloat < 0 {
		panic(fmt.Errorf("invalid SIMULATED_FAILURE_COUNT value: %s", valueAsString))
	}

	return uint(result)
}
