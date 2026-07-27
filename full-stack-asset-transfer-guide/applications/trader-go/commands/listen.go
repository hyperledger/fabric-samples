package commands

import (
	"context"
	"fmt"
	"os"
	"strconv"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

const startBlock = uint64(0)

func cmdListen(gw *client.Gateway, _ []string) error {
	network := gw.GetNetwork(channelName())

	checkpointFile := os.Getenv("CHECKPOINT_FILE")
	if checkpointFile == "" {
		checkpointFile = "checkpoint.json"
	}

	simulatedFailureCount, err := getSimulatedFailureCount()
	if err != nil {
		return err
	}

	checkpointer, err := client.NewFileCheckpointer(checkpointFile)
	if err != nil {
		return fmt.Errorf("failed to create checkpointer: %w", err)
	}
	defer checkpointer.Close()

	displayBlock := checkpointer.BlockNumber()
	if displayBlock == 0 {
		displayBlock = startBlock
	}

	fmt.Println("Starting event listening from block", displayBlock)
	fmt.Println("Last processed transaction ID within block:", checkpointer.TransactionID())
	if simulatedFailureCount > 0 {
		fmt.Println("Simulating a write failure every", simulatedFailureCount, "transactions")
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	events, err := network.ChaincodeEvents(ctx, chaincodeName(),
		client.WithStartBlock(startBlock),
		client.WithCheckpoint(checkpointer),
	)
	if err != nil {
		return fmt.Errorf("failed to start chaincode event subscription: %w", err)
	}

	eventCount := 0
	for event := range events {
		if simulatedFailureCount > 0 {
			eventCount++
			if eventCount >= simulatedFailureCount {
				eventCount = 0
				return &ExpectedError{Message: "Simulated write failure"}
			}
		}
		fmt.Printf("Chaincode event: BlockNumber=%d TxID=%s Name=%s Payload=%s\n",
			event.BlockNumber, event.TransactionID, event.EventName, string(event.Payload))
	}

	return nil
}

func getSimulatedFailureCount() (int, error) {
	value := os.Getenv("SIMULATED_FAILURE_COUNT")
	if value == "" {
		return 0, nil
	}
	count, err := strconv.Atoi(value)
	if err != nil || count < 0 {
		return 0, fmt.Errorf("invalid SIMULATED_FAILURE_COUNT value: %s", value)
	}
	return count, nil
}
