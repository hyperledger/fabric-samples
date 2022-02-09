/*
Copyright 2022 IBM All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

const (
	channelName   = "mychannel"
	chaincodeName = "events"
)

var now = time.Now()
var assetID = fmt.Sprintf("asset%d", now.Unix()*1e3+int64(now.Nanosecond())/1e6)

func main() {
	clientConnection := newGrpcConnection()
	defer clientConnection.Close()

	id := newIdentity()
	sign := newSign()

	gateway, err := client.Connect(
		id,
		client.WithSign(sign),
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

	network := gateway.GetNetwork(channelName)
	contract := network.GetContract(chaincodeName)

	// Context used for event listening
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Listen for events emitted by subsequent transactions
	startChaincodeEventListening(ctx, network)

	firstBlockNumber := createAsset(contract)
	updateAsset(contract)
	transferAsset(contract)
	deleteAsset(contract)

	// Replay events from the block containing the first transaction
	replayChaincodeEvents(ctx, network, firstBlockNumber)
}

func startChaincodeEventListening(ctx context.Context, network *client.Network) {
	fmt.Println("\n*** Start chaincode event listening")

	events, err := network.ChaincodeEvents(ctx, chaincodeName)
	if err != nil {
		panic(fmt.Errorf("failed to start chaincode event listening: %w", err))
	}

	go func() {
		for event := range events {
			asset := formatJSON(event.Payload)
			fmt.Printf("\n<-- Chaincode event received: %s - %s\n", event.EventName, asset)
		}
	}()
}

func formatJSON(data []byte) string {
	var result bytes.Buffer
	if err := json.Indent(&result, data, "", "  "); err != nil {
		panic(fmt.Errorf("failed to parse JSON: %w", err))
	}
	return result.String()
}

func createAsset(contract *client.Contract) uint64 {
	fmt.Printf("\n--> Submit transaction: CreateAsset, %s owned by Sam with appraised value 100\n", assetID)

	_, commit, err := contract.SubmitAsync("CreateAsset", client.WithArguments(assetID, "blue", "10", "Sam", "100"))
	if err != nil {
		panic(fmt.Errorf("failed to submit transaction: %w", err))
	}

	status, err := commit.Status()
	if err != nil {
		panic(fmt.Errorf("failed to get transaction commit status: %w", err))
	}

	if !status.Successful {
		panic(fmt.Errorf("failed to commit transaction with status code %v", status.Code))
	}

	fmt.Println("\n*** CreateAsset committed successfully")

	return status.BlockNumber
}

func updateAsset(contract *client.Contract) {
	fmt.Printf("\n--> Submit transaction: UpdateAsset, %s update appraised value to 200\n", assetID)

	_, err := contract.SubmitTransaction("UpdateAsset", assetID, "blue", "10", "Sam", "200")
	if err != nil {
		panic(fmt.Errorf("failed to submit transaction: %w", err))
	}

	fmt.Println("\n*** UpdateAsset committed successfully")
}

func transferAsset(contract *client.Contract) {
	fmt.Printf("\n--> Submit transaction: TransferAsset, %s to Mary\n", assetID)

	_, err := contract.SubmitTransaction("TransferAsset", assetID, "Mary")
	if err != nil {
		panic(fmt.Errorf("failed to submit transaction: %w", err))
	}

	fmt.Println("\n*** TransferAsset committed successfully")
}

func deleteAsset(contract *client.Contract) {
	fmt.Printf("\n--> Submit transaction: DeleteAsset, %s\n", assetID)

	_, err := contract.SubmitTransaction("DeleteAsset", assetID)
	if err != nil {
		panic(fmt.Errorf("failed to submit transaction: %w", err))
	}

	fmt.Println("\n*** DeleteAsset committed successfully")
}

func replayChaincodeEvents(ctx context.Context, network *client.Network, startBlock uint64) {
	fmt.Println("\n*** Start chaincode event replay")

	events, err := network.ChaincodeEvents(ctx, chaincodeName, client.WithStartBlock(startBlock))
	if err != nil {
		panic(fmt.Errorf("failed to start chaincode event listening: %w", err))
	}

	for {
		select {
		case <-time.After(10 * time.Second):
			panic(errors.New("timeout waiting for event replay"))

		case event := <-events:
			asset := formatJSON(event.Payload)
			fmt.Printf("\n<-- Chaincode event replayed: %s - %s\n", event.EventName, asset)

			if event.EventName == "DeleteAsset" {
				// Reached the last submitted transaction so return to stop listening for events
				return
			}
		}
	}
}
