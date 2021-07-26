/*
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/hyperledger/fabric-contract-api-go/metadata"
)

func main() {
	assetContract := new(AssetContract)
	assetContract.Info.Version = "0.0.1"
	assetContract.Info.Description = "My Smart Contract"
	assetContract.Info.License = new(metadata.LicenseMetadata)
	assetContract.Info.License.Name = "Apache-2.0"
	assetContract.Info.Contact = new(metadata.ContactMetadata)
	assetContract.Info.Contact.Name = "John Doe"

	chaincode, err := contractapi.NewChaincode(assetContract)
	chaincode.Info.Title = "chaincode-go chaincode"
	chaincode.Info.Version = "0.0.1"

	if err != nil {
		panic("Could not create chaincode from AssetContract." + err.Error())
	}

	err = chaincode.Start()

	if err != nil {
		panic("Failed to start chaincode. " + err.Error())
	}
}
