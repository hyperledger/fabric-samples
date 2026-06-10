package commands

import (
	"encoding/json"
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

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
