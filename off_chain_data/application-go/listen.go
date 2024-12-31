package main

import (
	"context"
	"fmt"
	"offChainData/parser"
	"offChainData/processor"
	"offChainData/store"
	"offChainData/utils"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

func listen(clientConnection *grpc.ClientConn) {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		panic(err)
	}
	defer gateway.Close()

	checkpointFile := utils.EnvOrDefault("CHECKPOINT_FILE", "checkpoint.json")
	checkpointer, err := client.NewFileCheckpointer(checkpointFile)
	if err != nil {
		panic(err)
	}
	defer checkpointer.Close()

	fmt.Println("Start event listening from block", checkpointer.BlockNumber())
	fmt.Println("Last processed transaction ID within block:", checkpointer.TransactionID())
	if store.SimulatedFailureCount > 0 {
		fmt.Printf("Simulating a write failure every %d transactions\n", store.SimulatedFailureCount)
	}

	// TODO put into infinite loop like in public docs example
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	network := gateway.GetNetwork(channelName)
	blocks, err := network.BlockEvents(
		ctx,
		client.WithCheckpoint(checkpointer),
		client.WithStartBlock(0), // Used only if there is no checkpoint block number
	)
	if err != nil {
		panic(err)
	}

	for blockProto := range blocks {
		blockProcessor := processor.NewBlock(
			parser.ParseBlock(blockProto),
			checkpointer,
			store.ApplyWritesToOffChainStore,
			channelName,
		)
		blockProcessor.Process()
	}
}
