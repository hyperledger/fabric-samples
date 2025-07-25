/*
Copyright 2020 IBM All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package functions

import (
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

// Query can be used to read the latest value of a variable
func Query(contract *client.Contract, function, variableName string) ([]byte, error) {
	result, err := contract.EvaluateTransaction(function, variableName)
	if err != nil {
		return nil, fmt.Errorf("failed to evaluate transaction: %v", err)
	}
	return result, err
}
