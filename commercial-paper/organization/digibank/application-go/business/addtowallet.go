package business

import (
	"log"
	"os"
	"path/filepath"

	"github.com/hyperledger/fabric-sdk-go/pkg/core/config"
	"github.com/hyperledger/fabric-sdk-go/pkg/gateway"
)

var (
	walletPath      = "../identity/user/balaji/wallet"
	userName        = "balaji"
	useContractId   = "papercontract"
	useContractName = "org.papernet.commercialpaper"
)

func AddUser() error {

	wallet, err := gateway.NewFileSystemWallet(walletPath)
	if err != nil {
		return err
	}
	credPath := filepath.Join(
		"..",
		"..",
		"..",
		"..",
		"test-network",
		"organizations",
		"peerOrganizations",
		"org1.example.com",
		"users",
		"User1@org1.example.com",
		"msp",
	)
	log.Println("credentialPath: " + credPath)
	certPath := filepath.Join(credPath, "signcerts", "User1@org1.example.com-cert.pem")
	log.Println("certificatePem: " + certPath)
	privateKeyPath := filepath.Join(credPath, "keystore", "priv_sk")
	log.Println("privateKeyPath: " + privateKeyPath)
	// read the certificate pem
	certificate, err := os.ReadFile(filepath.Clean(certPath))
	if err != nil {
		return err
	}
	privateKey, err := os.ReadFile(filepath.Clean(privateKeyPath))
	if err != nil {
		return err
	}

	identity := gateway.NewX509Identity("Org1MSP", string(certificate), string(privateKey))

	err = wallet.Put(userName, identity)
	if err != nil {
		return err
	}
	log.Println("Write wallet info into " + walletPath + " successfully.")
	return err
}

func getContract() (*gateway.Contract, error) {
	// Set local debugging configuration environment variables.
	os.Setenv("DISCOVERY_AS_LOCALHOST", "true")
	wallet, err := gateway.NewFileSystemWallet(walletPath)
	if err != nil {
		return nil, err
	}
	log.Println("Read wallet info from: " + walletPath)
	ccpPath := filepath.Join(
		"..",
		"gateway",
		"connection-org1.yaml",
	)
	gw, err := gateway.Connect(
		gateway.WithConfig(config.FromFile(filepath.Clean(ccpPath))),
		gateway.WithIdentity(wallet, userName),
	)
	if err != nil {
		return nil, err
	}
	defer gw.Close()
	log.Println("Use network channel: mychannel.")
	network, err := gw.GetNetwork("mychannel")
	if err != nil {
		return nil, err
	}
	log.Println("Use useContractId: " + useContractId + " useContractName: " + useContractName + " smart contract.")

	contract := network.GetContractWithName(useContractId, useContractName)
	log.Println("Submit commercial paper buy transaction.")
	return contract, nil
}
