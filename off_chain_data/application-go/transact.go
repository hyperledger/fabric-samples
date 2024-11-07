package main

import (
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

func transact(clientConnection *grpc.ClientConn) {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		panic((err))
	}
	defer gateway.Close()

	contract := gateway.GetNetwork(channelName).GetContract(chaincodeName)

	smartContract := newAssetTransferBasic(contract)
	app := newTransactApp(smartContract)
	app.run()
}

type transactApp struct {
	smartContract *assetTransferBasic
	batchSize     uint
}

func newTransactApp(smartContract *assetTransferBasic) *transactApp {
	return &transactApp{smartContract, 10}
}

var (
	colors = []string{"red", "green", "blue"}
	owners = []string{"alice", "bob", "charlie"}
)

const (
	maxInitialValue = 1000
	maxInitialSize  = 10
)

func (t *transactApp) run() {
	for i := 0; i < int(t.batchSize); i++ {
		go t.transact()
	}
}

func (t *transactApp) transact() {
	anAsset := NewAsset()

	t.smartContract.createAsset(anAsset)
	fmt.Printf("\nCreated asset %s\n", anAsset.ID)

	// Transfer randomly 1 in 2 assets to a new owner.
	if randomInt(2) == 0 {
		newOwner := differentElement(owners, anAsset.Owner)
		oldOwner := t.smartContract.transferAsset(anAsset.ID, newOwner)
		fmt.Printf("Transferred asset %s from %s to %s\n", anAsset.ID, oldOwner, newOwner)
	}

	// Delete randomly 1 in 4 created assets.
	if randomInt(4) == 0 {
		t.smartContract.deleteAsset(anAsset.ID)
		fmt.Printf("Deleted asset %s\n", anAsset.ID)
	}
}
