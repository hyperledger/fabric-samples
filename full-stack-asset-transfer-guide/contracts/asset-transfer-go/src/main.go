package main

/*
Package main starts the Asset Transfer chaincode as a Fabric
Chaincode-as-a-Service (CCaaS).

Before running, ensure the following environment variables are set:

    CORE_CHAINCODE_ID_NAME=${CHAINCODE_ID}
    CORE_CHAINCODE_SERVER_ADDRESS=${CHAINCODE_SERVER_ADDRESS}

For example:

    CORE_CHAINCODE_ID_NAME=${CHAINCODE_ID} \
    CORE_CHAINCODE_SERVER_ADDRESS=${CHAINCODE_SERVER_ADDRESS} \
    go run ./src
*/

import (
	"log"
	"os"

	"github.com/hyperledger/fabric-chaincode-go/v2/shim"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

func main() {
	cc, err := contractapi.NewChaincode(&SmartContract{})
	if err != nil {
		log.Panicf("Error creating asset-transfer chaincode: %v", err)
	}

	ccid := os.Getenv("CORE_CHAINCODE_ID_NAME")
	if ccid == "" {
		log.Fatal("CORE_CHAINCODE_ID_NAME must be set")
	}

	address := os.Getenv("CORE_CHAINCODE_SERVER_ADDRESS")
	if address == "" {
		log.Fatal("CORE_CHAINCODE_SERVER_ADDRESS must be set")
	}

	server := &shim.ChaincodeServer{
		CCID:    ccid,
		Address: address,
		CC:      cc,
		TLSProps: shim.TLSProperties{
			Disabled: true,
		},
	}

	if err := server.Start(); err != nil {
		log.Panicf("Error starting asset-transfer chaincode server: %v", err)
	}
}
