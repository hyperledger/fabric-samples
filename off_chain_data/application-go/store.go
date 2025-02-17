package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
)

var storeFile = envOrDefault("STORE_FILE", "store.log")
var simulatedFailureCount = getSimulatedFailureCount()
var transactionCount uint = 0 // Used only to simulate failures

// Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
type writer = func(ledgerUpdate) error

// Ledger update made by a specific transaction.
type ledgerUpdate struct {
	BlockNumber   uint64
	TransactionID string
	Writes        []write
}

// Description of a ledger Write that can be applied to an off-chain data store.
type write struct {
	// Channel whose ledger is being updated.
	ChannelName string `json:"channelName"`
	// Namespace within the ledger.
	Namespace string `json:"namespace"`
	// Key name within the ledger namespace.
	Key string `json:"key"`
	// Whether the key and associated value are being deleted.
	IsDelete bool `json:"isDelete"`
	// If `isDelete` is false, the Value written to the key; otherwise ignored.
	Value string `json:"value"`
}

// Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
// This implementation just writes to a file.
func applyWritesToOffChainStore(data ledgerUpdate) error {
	if err := simulateFailureIfRequired(); err != nil {
		return err
	}

	writes := []string{}
	for _, write := range data.Writes {
		marshaled, err := json.Marshal(write)
		if err != nil {
			return err
		}

		writes = append(writes, string(marshaled))
	}

	f, err := os.OpenFile(storeFile, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}

	if _, err := f.Write([]byte(strings.Join(writes, "\n") + "\n")); err != nil {
		f.Close()
		return err
	}

	if err := f.Close(); err != nil {
		return err
	}

	return nil
}

var errExpected = errors.New("expected error: simulated write failure")

func simulateFailureIfRequired() error {
	if simulatedFailureCount > 0 && transactionCount >= simulatedFailureCount {
		transactionCount = 0
		return errExpected
	}

	transactionCount += 1

	return nil
}

func getSimulatedFailureCount() uint {
	valueAsString := envOrDefault("SIMULATED_FAILURE_COUNT", "0")
	result, err := strconv.ParseUint(valueAsString, 10, 0)
	if err != nil {
		panic(fmt.Errorf("invalid SIMULATED_FAILURE_COUNT value: %s", valueAsString))
	}

	return uint(result)
}
