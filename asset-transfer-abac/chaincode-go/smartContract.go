/*
SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"log"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	abac "github.com/hyperledger/fabric-samples/asset-transfer-abac/chaincode-go/smart-contract"
)

func main() {
	abacSmartContract, err := contractapi.NewChaincode(&abac.SmartContract{})
	if err != nil {
		log.Panicf("Error creating abac chaincode: %v", err)
	}

	if err := abacSmartContract.Start(); err != nil {
		log.Panicf("Error starting abac chaincode: %v", err)
	}
}
