/*
SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"log"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
	asset "github.com/hyperledger/fabric-samples/full-stack-asset-transfer-guide/contracts/asset-transfer-go/smartcontract"
)

func main() {
	assetChaincode, err := contractapi.NewChaincode(&asset.SmartContract{})
	if err != nil {
		log.Panicf("Error creating asset-transfer-go chaincode: %v", err)
	}

	if err := assetChaincode.Start(); err != nil {
		log.Panicf("Error starting asset-transfer-go chaincode: %v", err)
	}
}
