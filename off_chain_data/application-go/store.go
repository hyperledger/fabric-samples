package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
)

var errExpected = errors.New("expected error: simulated write failure")

type offChainStore struct {
	writes, path                            string
	simulatedFailureCount, transactionCount uint
}

func newOffChainStore(path string, simulatedFailureCount uint) *offChainStore {
	return &offChainStore{
		"",
		path,
		uint(simulatedFailureCount),
		0,
	}
}

// Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
// This implementation just writes to a file.
func (ocs *offChainStore) write(data ledgerUpdate) error {
	if err := ocs.simulateFailureIfRequired(); err != nil {
		return err
	}

	ocs.clearLastWrites()

	if err := ocs.marshal(data.Writes); err != nil {
		return err
	}

	if err := ocs.persist(); err != nil {
		return err
	}

	return nil
}

func (ocs *offChainStore) simulateFailureIfRequired() error {
	if ocs.simulatedFailureCount > 0 && ocs.transactionCount >= ocs.simulatedFailureCount {
		ocs.transactionCount = 0
		return errExpected
	}

	ocs.transactionCount += 1

	return nil
}

func (ocs *offChainStore) clearLastWrites() {
	ocs.writes = ""
}

func (ocs *offChainStore) marshal(writes []write) error {
	for _, write := range writes {
		marshaled, err := json.Marshal(write)
		if err != nil {
			return err
		}

		ocs.writes += string(marshaled) + "\n"
	}

	return nil
}

func (ocs *offChainStore) persist() error {
	f, err := os.OpenFile(ocs.path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}

	if _, writeErr := f.Write([]byte(ocs.writes)); writeErr != nil {
		if closeErr := f.Close(); closeErr != nil {
			return fmt.Errorf("write error: %v, close error: %v", writeErr, closeErr)
		}

		return writeErr
	}

	if err := f.Close(); err != nil {
		return err
	}

	return nil
}
