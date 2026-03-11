package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math/big"
	"os"
	"strconv"
	"strings"
	"sync"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

// cmdCreate creates a new asset on the ledger.
// Arguments: <assetId> <ownerName> <color>
func cmdCreate(gw *client.Gateway, args []string) error {
	if len(args) < 3 {
		return fmt.Errorf("arguments: <assetId> <ownerName> <color>")
	}

	network := gw.GetNetwork(channelName())
	contract := network.GetContract(chaincodeName())

	smartContract := NewAssetTransfer(contract)
	return smartContract.CreateAsset(Asset{
		ID:             args[0],
		Owner:          args[1],
		Color:          args[2],
		Size:           1,
		AppraisedValue: 1,
	})
}

// cmdDelete deletes an asset from the ledger.
// Arguments: <assetId>
func cmdDelete(gw *client.Gateway, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("arguments: <assetId>")
	}

	network := gw.GetNetwork(channelName())
	contract := network.GetContract(chaincodeName())

	smartContract := NewAssetTransfer(contract)
	return smartContract.DeleteAsset(args[0])
}

// cmdGetAllAssets queries and prints all assets currently on the ledger.
func cmdGetAllAssets(gw *client.Gateway, _ []string) error {
	network := gw.GetNetwork(channelName())
	contract := network.GetContract(chaincodeName())

	smartContract := NewAssetTransfer(contract)
	assets, err := smartContract.GetAllAssets()
	if err != nil {
		return err
	}

	data, err := json.MarshalIndent(assets, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal assets: %w", err)
	}

	for _, line := range strings.Split(string(data), "\n") {
		fmt.Println(line)
	}
	return nil
}

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

// cmdRead reads and prints a single asset from the ledger.
// Arguments: <assetId>
func cmdRead(gw *client.Gateway, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("arguments: <assetId>")
	}

	network := gw.GetNetwork(channelName())
	contract := network.GetContract(chaincodeName())

	smartContract := NewAssetTransfer(contract)
	asset, err := smartContract.ReadAsset(args[0])
	if err != nil {
		return err
	}

	data, err := json.MarshalIndent(asset, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal asset: %w", err)
	}

	fmt.Println(string(data))
	return nil
}

var (
	colors         = []string{"red", "green", "blue"}
	maxInitialSize = 10
	maxInitialVal  = 1000
)

// cmdTransact runs a batch of concurrent create/update/delete transactions to demonstrate
func cmdTransact(gw *client.Gateway, _ []string) error {
	network := gw.GetNetwork(channelName())
	contract := network.GetContract(chaincodeName())

	smartContract := NewAssetTransfer(contract)
	app := &transactApp{smartContract: smartContract, batchSize: 6}
	return app.run()
}

type transactApp struct {
	smartContract *AssetTransfer
	batchSize     int
}

func (a *transactApp) run() error {
	var wg sync.WaitGroup
	errCh := make(chan error, a.batchSize)

	for i := 0; i < a.batchSize; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := a.transact(); err != nil {
				errCh <- err
			}
		}()
	}

	wg.Wait()
	close(errCh)

	var failures []string
	for err := range errCh {
		failures = append(failures, err.Error())
	}
	if len(failures) > 0 {
		return fmt.Errorf("%d failures:\n- %s", len(failures), strings.Join(failures, "\n- "))
	}
	return nil
}

func (a *transactApp) transact() error {
	asset := a.newAsset()

	if err := a.smartContract.CreateAsset(asset); err != nil {
		return err
	}
	fmt.Printf("Created asset %s\n", asset.ID)

	if randomInt(2) == 0 {
		oldColor := asset.Color
		asset.Color = differentElement(colors, oldColor)
		if err := a.smartContract.UpdateAsset(asset); err != nil {
			return err
		}
		fmt.Printf("Updated color of asset %s from %s to %s\n", asset.ID, oldColor, asset.Color)
	}

	if randomInt(4) == 0 {
		if err := a.smartContract.DeleteAsset(asset.ID); err != nil {
			return err
		}
		fmt.Printf("Deleted asset %s\n", asset.ID)
	}

	return nil
}

func (a *transactApp) newAsset() Asset {
	return Asset{
		ID:             randomHexString(8),
		Color:          randomElement(colors),
		Size:           randomInt(maxInitialSize) + 1,
		AppraisedValue: float64(randomInt(maxInitialVal) + 1),
	}
}

func randomHexString(length int) string {
	b := make([]byte, (length+1)/2)
	if _, err := rand.Read(b); err != nil {
		panic(fmt.Sprintf("failed to generate random bytes: %v", err))
	}
	return hex.EncodeToString(b)[:length]
}

func randomInt(max int) int {
	n, err := rand.Int(rand.Reader, big.NewInt(int64(max)))
	if err != nil {
		panic(fmt.Sprintf("failed to generate random int: %v", err))
	}
	return int(n.Int64())
}

func randomElement(values []string) string {
	return values[randomInt(len(values))]
}

func differentElement(values []string, currentValue string) string {
	var candidates []string
	for _, v := range values {
		if v != currentValue {
			candidates = append(candidates, v)
		}
	}
	return randomElement(candidates)
}

// cmdTransfer transfers ownership of an asset to a new owner in a different organisation.
// Arguments: <assetId> <ownerName> <ownerMspId>
func cmdTransfer(gw *client.Gateway, args []string) error {
	if len(args) < 3 {
		return fmt.Errorf("arguments: <assetId> <ownerName> <ownerMspId>")
	}

	network := gw.GetNetwork(channelName())
	contract := network.GetContract(chaincodeName())

	smartContract := NewAssetTransfer(contract)
	return smartContract.TransferAsset(args[0], args[1], args[2])
}
