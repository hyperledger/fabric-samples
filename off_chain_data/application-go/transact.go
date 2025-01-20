package main

import (
	"crypto/rand"
	"fmt"
	"math/big"
	"sync"

	atb "offchaindata/contract"

	"github.com/google/uuid"
	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

var owners = []string{"alice", "bob", "charlie"}

func transact(clientConnection *grpc.ClientConn) {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		panic((err))
	}
	defer func() {
		gateway.Close()
		fmt.Println("Gateway closed.")
	}()

	contract := gateway.GetNetwork(channelName).GetContract(chaincodeName)

	smartContract := atb.NewAssetTransferBasic(contract)
	app := newTransactApp(smartContract)
	app.run()
}

type transactApp struct {
	smartContract *atb.AssetTransferBasic
	batchSize     int
}

func newTransactApp(smartContract *atb.AssetTransferBasic) *transactApp {
	return &transactApp{smartContract, 10}
}

func (t *transactApp) run() {
	var wg sync.WaitGroup

	for i := 0; i < t.batchSize; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()

			if err := t.transact(); err != nil {
				fmt.Println("\033[31m[ERROR]\033[0m", err)
				return
			}
		}()
	}

	wg.Wait()
}

func (t *transactApp) transact() error {
	funcName := "transact"

	anAsset := newAsset()

	err := t.smartContract.CreateAsset(anAsset)
	if err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}
	fmt.Println("Created asset", anAsset.ID)

	// Transfer randomly 1 in 2 assets to a new owner.
	if randomInt(2) == 0 {
		newOwner := differentElement(owners, anAsset.Owner)
		oldOwner, err := t.smartContract.TransferAsset(anAsset.ID, newOwner)
		if err != nil {
			return fmt.Errorf("in %s: %w", funcName, err)
		}
		fmt.Printf("Transferred asset %s from %s to %s\n", anAsset.ID, oldOwner, newOwner)
	}

	// Delete randomly 1 in 4 created assets.
	if randomInt(4) == 0 {
		err := t.smartContract.DeleteAsset(anAsset.ID)
		if err != nil {
			return fmt.Errorf("in %s: %w", funcName, err)
		}
		fmt.Println("Deleted asset", anAsset.ID)
	}
	return nil
}

func newAsset() atb.Asset {
	id, err := uuid.NewRandom()
	if err != nil {
		panic(err)
	}

	return atb.Asset{
		ID:             id.String(),
		Color:          randomElement([]string{"red", "green", "blue"}),
		Size:           uint64(randomInt(10) + 1),
		Owner:          randomElement(owners),
		AppraisedValue: uint64(randomInt(1000) + 1),
	}
}

// Pick a random element from an array.
func randomElement(values []string) string {
	result := values[randomInt(len(values))]
	return result
}

// Generate a random integer in the range 0 to max - 1.
func randomInt(max int) int {
	result, err := rand.Int(rand.Reader, big.NewInt(int64(max)))
	if err != nil {
		panic(err)
	}

	return int(result.Int64())
}

// Pick a random element from an array, excluding the current value.
func differentElement(values []string, currentValue string) string {
	candidateValues := []string{}
	for _, v := range values {
		if v != currentValue {
			candidateValues = append(candidateValues, v)
		}
	}
	return randomElement(candidateValues)
}
