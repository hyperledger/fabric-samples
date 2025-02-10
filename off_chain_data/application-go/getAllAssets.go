package main

import (
	"encoding/json"
	"fmt"
	atb "offchaindata/contract"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

func getAllAssets(clientConnection grpc.ClientConnInterface) error {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		return err
	}
	defer gateway.Close()

	contract := gateway.GetNetwork(channelName).GetContract(chaincodeName)
	smartContract := atb.NewAssetTransferBasic(contract)
	assets, err := smartContract.GetAllAssets()
	if err != nil {
		return err
	}

	assetsJSONformatted, err := json.MarshalIndent(assets, "", "  ")
	if err != nil {
		return err
	}

	fmt.Println(assetsJSONformatted)

	return nil
}
