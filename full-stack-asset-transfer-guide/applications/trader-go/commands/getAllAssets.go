package commands

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

// cmdGetAllAssets queries and prints all assets currently on the ledger.
func cmdGetAllAssets(gw *client.Gateway, _ []string) error {
	network := gw.GetNetwork(channelName())
	contract := network.GetContract(chaincodeName())

	smartContract := NewAssetTransfer(contract)
	assets, err := smartContract.GetAllAssets()
	if err != nil {
		return err
	}

	data, err := json.MarshalIndent(assets, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal assets: %w", err)
	}

	for _, line := range strings.Split(string(data), "\n") {
		fmt.Println(line)
	}
	return nil
}
