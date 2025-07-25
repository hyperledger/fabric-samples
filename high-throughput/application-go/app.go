/*
Copyright 2020 IBM All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"log"
	"os"
	"time"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"github.com/hyperledger/fabric-gateway/pkg/hash"
	f "github.com/hyperledger/fabric-samples/high-throughput/application-go/functions"
)

func main() {

	var function, variableName, change, sign string

	if len(os.Args) <= 2 {
		log.Println("Usage: function variableName")
		log.Fatalf("functions: update manyUpdates manyUpdatesTraditional get prune delete")
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
	if function == "update" {
		result, err := f.Update(contract, function, variableName, change, sign)
		if err != nil {
			log.Fatalf("error: %v", err)
		}
		log.Println("Value of variable", string(variableName), ": ", string(result))

	} else if function == "delete" || function == "prune" || function == "delstandard" {
		result, err := f.DeletePrune(contract, function, variableName)
		if err != nil {
			log.Fatalf("error: %v", err)
		}
		log.Println(string(result))
	} else if function == "get" || function == "getstandard" {
		result, err := f.Query(contract, function, variableName)
		if err != nil {
			log.Fatalf("error: %v", err)
		}
		log.Println("Value of variable", string(variableName), ": ", string(result))
	} else if function == "manyUpdates" {
		log.Println("submitting 1000 concurrent updates...")
		result, err := f.ManyUpdates(contract, "update", variableName, change, sign)
		if err != nil {
			log.Fatalf("error: %v", err)
		}
		log.Println("Final value of variable", string(variableName), ": ", string(result))
	} else if function == "manyUpdatesTraditional" {
		log.Println("submitting 1000 concurrent updates...")
		result, err := f.ManyUpdates(contract, "putstandard", variableName, change, sign)
		if err != nil {
			log.Fatalf("error: %v", err)
		}
		log.Println("Final value of variable", string(variableName), ": ", string(result))
	}
}
