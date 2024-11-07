package main

import (
	"context"
	"fmt"
	"math"
	"strconv"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

var checkpointFile = envOrDefault("CHECKPOINT_FILE", "checkpoint.json")
var simulatedFailureCount = getSimulatedFailureCount()

func listen(clientConnection *grpc.ClientConn) {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		panic(err)
	}
	defer gateway.Close()

	network := gateway.GetNetwork(channelName)

	checkpointer, err := client.NewFileCheckpointer(checkpointFile)
	if err != nil {
		panic(err)
	}
	defer checkpointer.Close()

	fmt.Printf("Start event listening from block %d\n", checkpointer.BlockNumber())
	fmt.Printf("Last processed transaction ID within block: %s\n", checkpointer.TransactionID())
	if simulatedFailureCount > 0 {
		fmt.Printf("Simulating a write failure every %d transactions", simulatedFailureCount)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	blocks, err := network.BlockEvents(
		ctx,
		client.WithStartBlock(0),
		client.WithCheckpoint(checkpointer),
	)
	if err != nil {
		panic(err)
	}

	for blockProto := range blocks {
		checkpointer.CheckpointBlock(blockProto.GetHeader().GetNumber())
	}
}

func getSimulatedFailureCount() uint {
	valueAsString := envOrDefault("SIMULATED_FAILURE_COUNT", "0")
	valueAsFloat, err := strconv.ParseFloat(valueAsString, 64)
	if err != nil {
		panic(err)
	}

	result := math.Floor(valueAsFloat)
	if valueAsFloat < 0 {
		panic(fmt.Errorf("invalid SIMULATED_FAILURE_COUNT value: %s", valueAsString))
	}

	return uint(result)
}
