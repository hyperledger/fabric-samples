package business

import (
	"io/ioutil"
	"log"
	"path/filepath"

	"github.com/hyperledger/fabric-sdk-go/pkg/gateway"
)

var (
	walletPath  = "../identity/user/isabella/wallet"
	userName    = "isabella"
	useContract = "papercontract"
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
		"org2.example.com",
		"users",
		"User1@org2.example.com",
		"msp",
	)
	log.Println("credentialPath: " + credPath)
	certPath := filepath.Join(credPath, "signcerts", "User1@org2.example.com-cert.pem")
	log.Println("certificatePem: " + certPath)
	privateKeyPath := filepath.Join(credPath, "keystore", "priv_sk")
	log.Println("privateKeyPath: " + privateKeyPath)
	// read the certificate pem
	certificate, err := ioutil.ReadFile(filepath.Clean(certPath))
	if err != nil {
		return err
	}
	privateKey, err := ioutil.ReadFile(filepath.Clean(privateKeyPath))
	if err != nil {
		return err
	}

	identity := gateway.NewX509Identity("Org2MSP", string(certificate), string(privateKey))

	err = wallet.Put(userName, identity)
	if err != nil {
		return err
	}
	log.Println("Write wallet info into " + walletPath + " successfully.")
	return err
}
