/*
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/hyperledger/fabric-contract-api-go/metadata"
)

func main() {
	hlpNftContract := new(TokenERC721Contract)
	hlpNftContract.Info.Version = "0.0.1"
	hlpNftContract.Info.Description = "ERC-721 fabric port"
	hlpNftContract.Info.License = new(metadata.LicenseMetadata)
	hlpNftContract.Info.License.Name = "Apache-2.0"
	hlpNftContract.Info.Contact = new(metadata.ContactMetadata)
	hlpNftContract.Info.Contact.Name = "Matias Salimbene"

	chaincode, err := contractapi.NewChaincode(hlpNftContract)
	chaincode.Info.Title = "ERC-721 chaincode"
	chaincode.Info.Version = "0.0.1"

	if err != nil {
		panic("Could not create chaincode from TokenERC721Contract." + err.Error())
	}

	err = chaincode.Start()

	if err != nil {
		panic("Failed to start chaincode. " + err.Error())
	}
}
