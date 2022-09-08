package business

import (
	"log"
	"os"
	"path/filepath"

	"github.com/hyperledger/fabric-sdk-go/pkg/core/config"
	"github.com/hyperledger/fabric-sdk-go/pkg/gateway"
)

func Issue() error {
	// Set local debugging configuration environment variables.
	os.Setenv("DISCOVERY_AS_LOCALHOST", "true")
	wallet, err := gateway.NewFileSystemWallet(walletPath)
	if err != nil {
		return err
	}
	log.Println("Read wallet info from: " + walletPath)
	ccpPath := filepath.Join(
		"..",
		"gateway",
		"connection-org2.yaml",
	)
	gw, err := gateway.Connect(
		gateway.WithConfig(config.FromFile(filepath.Clean(ccpPath))),
		gateway.WithIdentity(wallet, userName),
	)
	if err != nil {
		return err
	}
	defer gw.Close()
	log.Println("Use network channel: mychannel.")
	network, err := gw.GetNetwork("mychannel")
	if err != nil {
		return err
	}
	log.Println("Use " + useContract + " smart contract.")
	contract := network.GetContract(useContract)
	log.Println("Submit commercial paper issue transaction.")
	issueResponse, err := contract.SubmitTransaction("issue", "MagnetoCorp", "00001", "2020-05-31", "2020-11-30", "5000000")
	if err != nil {
		return err
	}
	log.Println("Process issue transaction response." + string(issueResponse))
	return nil
}
