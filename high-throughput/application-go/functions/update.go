/*
Copyright 2020 IBM All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package functions

import (
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

// Update can be used to update or prune the variable
func Update(contract *client.Contract, function, variableName, change, sign string) ([]byte, error) {
	result, err := contract.SubmitTransaction(function, variableName, change, sign)
	if err != nil {
		return result, fmt.Errorf("failed to Submit transaction: %v", err)
	}

	result, err = contract.EvaluateTransaction("get", variableName)
	if err != nil {
		return nil, fmt.Errorf("failed to evaluate transaction: %v", err)
	}
	return result, err
}
