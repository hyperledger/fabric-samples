package commands

import (
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

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
