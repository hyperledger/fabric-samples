package main

import (
	"fmt"
	"sync"

	atb "offChainData/contract"
	"offChainData/utils"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

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

	anAsset := atb.NewAsset()

	err := t.smartContract.CreateAsset(anAsset)
	if err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}
	fmt.Println("Created asset", anAsset.ID)

	// Transfer randomly 1 in 2 assets to a new owner.
	if utils.RandomInt(2) == 0 {
		newOwner := utils.DifferentElement(atb.Owners, anAsset.Owner)
		oldOwner, err := t.smartContract.TransferAsset(anAsset.ID, newOwner)
		if err != nil {
			return fmt.Errorf("in %s: %w", funcName, err)
		}
		fmt.Printf("Transferred asset %s from %s to %s\n", anAsset.ID, oldOwner, newOwner)
	}

	// Delete randomly 1 in 4 created assets.
	if utils.RandomInt(4) == 0 {
		err := t.smartContract.DeleteAsset(anAsset.ID)
		if err != nil {
			return fmt.Errorf("in %s: %w", funcName, err)
		}
		fmt.Println("Deleted asset", anAsset.ID)
	}
	return nil
}
