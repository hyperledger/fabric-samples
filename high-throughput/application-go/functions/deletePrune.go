/*
Copyright 2020 IBM All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package functions

import (
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

// DeletePrune deletes or prunes a variable
func DeletePrune(contract *client.Contract, function, variableName string) ([]byte, error) {
	result, err := contract.SubmitTransaction(function, variableName)
	if err != nil {
		return result, fmt.Errorf("failed to Submit transaction: %v", err)
	}
	return result, err
}
