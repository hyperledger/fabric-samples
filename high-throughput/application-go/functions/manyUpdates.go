/*
Copyright 2020 IBM All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package functions

import (
	"fmt"
	"sync"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

// ManyUpdates allows you to push many cuncurrent updates to a variable
func ManyUpdates(contract *client.Contract, function, variableName, change, sign string) ([]byte, error) {
	var wg sync.WaitGroup

	for i := 0; i < 1000; i++ {
		wg.Add(1)
		go func() ([]byte, error) {
			defer wg.Done()
			result, err := contract.SubmitTransaction(function, variableName, change, sign)
			if err != nil {
				return result, fmt.Errorf("failed to evaluate transaction: %v", err)
			}
			return result, nil
		}()
	}

	wg.Wait()

	result, err := contract.EvaluateTransaction("get", variableName)
	if err != nil {
		return nil, fmt.Errorf("failed to evaluate transaction: %v", err)
	}
	return result, err
}
