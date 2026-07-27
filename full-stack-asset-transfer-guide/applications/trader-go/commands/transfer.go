package commands

import (
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

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
