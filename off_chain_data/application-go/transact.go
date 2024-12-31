package main

import (
	"fmt"

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
	defer gateway.Close()

	contract := gateway.GetNetwork(channelName).GetContract(chaincodeName)

	smartContract := atb.NewAssetTransferBasic(contract)
	app := newTransactApp(smartContract)
	app.run()
}

type transactApp struct {
	smartContract *atb.AssetTransferBasic
	batchSize     uint
}

func newTransactApp(smartContract *atb.AssetTransferBasic) *transactApp {
	return &transactApp{smartContract, 10}
}

func (t *transactApp) run() {
	for i := 0; i < int(t.batchSize); i++ {
		t.transact()
	}
}

func (t *transactApp) transact() {
	anAsset := atb.NewAsset()

	t.smartContract.CreateAsset(anAsset)
	// TODO print txID to compare easier with block processing
	fmt.Println("Created asset", anAsset.ID)

	// Transfer randomly 1 in 2 assets to a new owner.
	if utils.RandomInt(2) == 0 {
		newOwner := utils.DifferentElement(atb.Owners, anAsset.Owner)
		oldOwner := t.smartContract.TransferAsset(anAsset.ID, newOwner)
		fmt.Printf("Transferred asset %s from %s to %s\n", anAsset.ID, oldOwner, newOwner)
	}

	// Delete randomly 1 in 4 created assets.
	if utils.RandomInt(4) == 0 {
		t.smartContract.DeleteAsset(anAsset.ID)
		fmt.Println("Deleted asset", anAsset.ID)
	}
}
