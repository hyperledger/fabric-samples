package main

import (
	"context"
	"fmt"
	"offChainData/parser"
	"offChainData/processor"
	"offChainData/store"
	"offChainData/utils"
	"os"
	"os/signal"
	"sync"
	"syscall"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

func listen(clientConnection *grpc.ClientConn) {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		panic(err)
	}
	defer func() {
		gateway.Close()
		fmt.Println("Gateway closed.")
	}()

	checkpointFile := utils.EnvOrDefault("CHECKPOINT_FILE", "checkpoint.json")
	checkpointer, err := client.NewFileCheckpointer(checkpointFile)
	if err != nil {
		panic(err)
	}
	defer func() {
		checkpointer.Close()
		fmt.Println("Checkpointer closed.")
	}()

	fmt.Println("Start event listening from block", checkpointer.BlockNumber())
	fmt.Println("Last processed transaction ID within block:", checkpointer.TransactionID())
	if store.SimulatedFailureCount > 0 {
		fmt.Printf("Simulating a write failure every %d transactions\n", store.SimulatedFailureCount)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer func() {
		stop()
		fmt.Println("Context closed.")
	}()

	network := gateway.GetNetwork(channelName)
	blocks, err := network.BlockEvents(
		ctx,
		client.WithCheckpoint(checkpointer),
		client.WithStartBlock(0), // Used only if there is no checkpoint block number
	)
	if err != nil {
		panic(err)
	}

	var wg sync.WaitGroup
	wg.Add(1)

	go func() {
		defer wg.Done()

		for blockProto := range blocks {
			select {
			case <-ctx.Done():
				return
			default:
				blockProcessor := processor.NewBlock(
					parser.ParseBlock(blockProto),
					checkpointer,
					store.ApplyWritesToOffChainStore,
					channelName,
				)
				blockProcessor.Process()
			}
		}
	}()

	wg.Wait()
	fmt.Println("\nReceived 'SIGTERM' signal. Shutting down listener gracefully...")
}
