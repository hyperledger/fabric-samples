package main

import (
	"context"
	"fmt"
	"math/rand/v2"
	"sync"

	atb "offchaindata/contract"

	"github.com/google/uuid"
	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

var owners = []string{"alice", "bob", "charlie"}

func transact(clientConnection grpc.ClientConnInterface) error {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		return err
	}
	defer func() {
		gateway.Close()
		fmt.Println("Gateway closed.")
	}()

	contract := gateway.GetNetwork(channelName).GetContract(chaincodeName)
	smartContract := atb.NewAssetTransferBasic(contract)
	app := newTransactApp(smartContract)
	return app.run()
}

type transactApp struct {
	smartContract *atb.AssetTransferBasic
	batchSize     int
}

func newTransactApp(smartContract *atb.AssetTransferBasic) *transactApp {
	return &transactApp{smartContract, 10}
}

func (t *transactApp) run() error {
	ctx, cancel := context.WithCancelCause(context.Background())
	defer cancel(nil)

	var wg sync.WaitGroup

	for range t.batchSize {
		wg.Add(1)
		go func() {
			defer wg.Done()

			select {
			case <-ctx.Done():
				return
			default:
				if err := t.transact(); err != nil {
					cancel(err)
					return
				}
			}
		}()
	}

	wg.Wait()

	return context.Cause(ctx)
}

func (t *transactApp) transact() error {
	anAsset, err := newAsset()
	if err != nil {
		return err
	}

	if err := t.smartContract.CreateAsset(anAsset); err != nil {
		return err
	}
	fmt.Println("Created asset", anAsset.ID)

	// Transfer randomly 1 in 2 assets to a new owner.
	if rand.N(2) == 0 {
		newOwner := differentElement(owners, anAsset.Owner)
		oldOwner, err := t.smartContract.TransferAsset(anAsset.ID, newOwner)
		if err != nil {
			return err
		}
		fmt.Printf("Transferred asset %s from %s to %s\n", anAsset.ID, oldOwner, newOwner)
	}

	// Delete randomly 1 in 4 created assets.
	if rand.N(4) == 0 {
		if err := t.smartContract.DeleteAsset(anAsset.ID); err != nil {
			return err
		}
		fmt.Println("Deleted asset", anAsset.ID)
	}

	return nil
}

func newAsset() (atb.Asset, error) {
	id, err := uuid.NewRandom()
	if err != nil {
		return atb.Asset{}, err
	}

	return atb.Asset{
		ID:             id.String(),
		Color:          randomElement([]string{"red", "green", "blue"}),
		Size:           uint64(rand.N(10) + 1),
		Owner:          randomElement(owners),
		AppraisedValue: uint64(rand.N(1000) + 1),
	}, nil
}

// Pick a random element from an array.
func randomElement(values []string) string {
	return values[rand.N(len(values))]
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
