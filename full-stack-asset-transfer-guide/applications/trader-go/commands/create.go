package commands

import (
	"fmt"

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
