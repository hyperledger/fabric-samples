/*
Copyright 2020 IBM All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"log"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"github.com/hyperledger/fabric-gateway/pkg/hash"
	gatewaypb "github.com/hyperledger/fabric-protos-go-apiv2/gateway"
	"google.golang.org/grpc/status"
)

func main() {

	var function, variableName, change, sign string

	if len(os.Args) <= 2 {
		log.Println("Usage: function variableName")
		log.Fatalf("functions: update delete prune get manyUpdates updatestandard delstandard getstandard manyUpdatesTraditional")
	} else if (os.Args[1] == "update" || os.Args[1] == "manyUpdates" || os.Args[1] == "manyUpdatesTraditional") && len(os.Args) < 5 {
		log.Fatalf("error: provide value and operation")
	} else if len(os.Args) == 3 {
		function = os.Args[1]
		variableName = os.Args[2]
	} else if len(os.Args) == 5 {
		function = os.Args[1]
		variableName = os.Args[2]
		change = os.Args[3]
		sign = os.Args[4]
	}

	clientConnection := newGrpcConnection()
	defer clientConnection.Close()

	gateway, err := client.Connect(
		newIdentity(),
		client.WithSign(newSign()),
		client.WithHash(hash.SHA256),
		client.WithClientConnection(clientConnection),
		client.WithEvaluateTimeout(5*time.Second),
		client.WithEndorseTimeout(15*time.Second),
		client.WithSubmitTimeout(5*time.Second),
		client.WithCommitStatusTimeout(1*time.Minute),
	)
	if err != nil {
		panic(err)
	}
	defer gateway.Close()

	contract := gateway.GetNetwork("mychannel").GetContract("bigdatacc")

	// Handle different functions
	switch function {
	case "update":
		update(contract, variableName, change, sign)
	case "updatestandard":
		updateStandard(contract, variableName, change, sign)
	case "delete":
		delete(contract, variableName)
	case "prune":
		prune(contract, variableName)
	case "delstandard":
		delStandard(contract, variableName)
	case "get":
		get(contract, variableName)
	case "getstandard":
		getStandard(contract, variableName)
	case "manyUpdates":
		manyUpdates(contract, "Update", variableName, change, sign)
		get(contract, variableName)
	case "manyUpdatesTraditional":
		manyUpdates(contract, "UpdateStandard", variableName, change, sign)
		getStandard(contract, variableName)
	default:
		log.Fatalln("Unkown function:", function)
	}
}

func update(contract *client.Contract, variableName, change, sign string) {
	result, err := contract.SubmitTransaction("Update", variableName, change, sign)
	failOnError(err)
	log.Println(string(result))
	get(contract, variableName)
}

func updateStandard(contract *client.Contract, variableName, change, sign string) {
	result, err := contract.SubmitTransaction("UpdateStandard", variableName, change, sign)
	failOnError(err)
	log.Printf("Value of variable %s: %s\n", variableName, result)
}

func delete(contract *client.Contract, variableName string) {
	result, err := contract.SubmitTransaction("Delete", variableName)
	failOnError(err)
	log.Println(string(result))
}

func prune(contract *client.Contract, variableName string) {
	result, err := contract.SubmitTransaction("Prune", variableName)
	failOnError(err)
	log.Println(string(result))
}

func delStandard(contract *client.Contract, variableName string) {
	_, err := contract.SubmitTransaction("DelStandard", variableName)
	failOnError(err)
	log.Printf("Deleted %s.\n", variableName)
}

func get(contract *client.Contract, variableName string) {
	result, err := contract.EvaluateTransaction("Get", variableName)
	failOnError(err)
	log.Printf("Value of variable %s: %s\n", variableName, result)
}

func getStandard(contract *client.Contract, variableName string) {
	result, err := contract.EvaluateTransaction("GetStandard", variableName)
	failOnError(err)
	log.Printf("Value of variable %s: %s\n", variableName, result)
}

func manyUpdates(contract *client.Contract, function, variableName, change, sign string) {
	log.Println("submitting 1000 concurrent updates...")

	var failCount atomic.Uint32
	var wg sync.WaitGroup
	for range 1000 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, err := contract.SubmitTransaction(function, variableName, change, sign)
			if err != nil {
				failCount.Add(1)
			}
		}()
	}

	wg.Wait()

	n := failCount.Load()
	if n > 0 {
		log.Printf("%v submit failures.", n)
	}
}

func failOnError(err error) {
	if err == nil {
		return
	}

	details := status.Convert(err).Details()
	if len(details) > 0 {
		log.Println("Error Details:")

		for _, detail := range details {
			switch detail := detail.(type) {
			case *gatewaypb.ErrorDetail:
				log.Printf("- address: %s\n  mspId: %s\n  message: %s\n", detail.Address, detail.MspId, detail.Message)
			default:
				log.Printf("- %s", detail)
			}
		}
	}

	log.Fatalf("error: %v", err)
}
