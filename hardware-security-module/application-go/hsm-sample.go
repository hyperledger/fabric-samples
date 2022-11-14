//go:build pkcs11
// +build pkcs11

/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/sha256"
	"encoding/json"
	"encoding/pem"
	"errors"
	"os"

	"crypto/x509"
	"fmt"
	"time"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"github.com/hyperledger/fabric-gateway/pkg/identity"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
)

const (
	mspID        = "Org1MSP"
	certPath     = "../crypto-material/hsm/HSMUser/signcerts/cert.pem"
	tlsCertPath  = "../../test-network/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt"
	peerEndpoint = "localhost:7051"
)

var now = time.Now()
var assetId = fmt.Sprintf("asset%d", now.Unix()*1e3+int64(now.Nanosecond())/1e6)

func main() {
	fmt.Println("Running the GO HSM Sample")

	// The gRPC client connection should be shared by all Gateway connections to this endpoint
	clientConnection := newGrpcConnection()
	defer clientConnection.Close()

	hsmSignerFactory, err := identity.NewHSMSignerFactory(findSoftHSMLibrary())
	if err != nil {
		panic(err)
	}
	defer hsmSignerFactory.Dispose()

	certificatePEM, err := os.ReadFile(certPath)
	if err != nil {
		panic(err)
	}

	id := newIdentity(certificatePEM)
	ski := getSKI(certificatePEM)
	hsmSign, hsmSignClose := newHSMSign(hsmSignerFactory, ski)
	defer hsmSignClose()

	// Create a Gateway connection for a specific client identity
	gateway, err := client.Connect(id, client.WithSign(hsmSign), client.WithClientConnection(clientConnection))
	if err != nil {
		panic(err)
	}
	defer gateway.Close()

	exampleTransaction(gateway)
	fmt.Println()
	fmt.Println("Go HSM Sample completed successfully")
	fmt.Println()
}

func exampleTransaction(gateway *client.Gateway) {

    // Override default values for chaincode and channel name as they may differ in testing contexts.
    channelName := "mychannel"
    if cname := os.Getenv("CHANNEL_NAME"); cname != "" {
        channelName = cname
    }

    chaincodeName := "basic"
    if ccname := os.Getenv("CHAINCODE_NAME"); ccname != "" {
        chaincodeName = ccname
    }

    network := gateway.GetNetwork(channelName)
    contract := network.GetContract(chaincodeName)

	fmt.Printf("Submit Transaction: CreateAsset, creates new asset with ID, Color, Size, Owner and AppraisedValue arguments \n")

	_, err := contract.SubmitTransaction("CreateAsset", assetId, "yellow", "5", "Tom", "1300")
	if err != nil {
		panic(fmt.Errorf("failed to submit transaction: %w", err))
	}

	fmt.Printf("*** Transaction committed successfully\n")

	fmt.Printf("Evaluate Transaction: ReadAsset, function returns asset attributes\n")

	evaluateResult, err := contract.EvaluateTransaction("ReadAsset", assetId)
	if err != nil {
		panic(fmt.Errorf("failed to evaluate transaction: %w", err))
	}
	result := formatJSON(evaluateResult)

	fmt.Printf("*** Result:%s\n", result)
}

// newGrpcConnection creates a gRPC connection to the Gateway server.
func newGrpcConnection() *grpc.ClientConn {
	certificate, err := loadCertificate(tlsCertPath)
	if err != nil {
		panic(fmt.Errorf("failed to obtain commit status: %w", err))
	}

	certPool := x509.NewCertPool()
	certPool.AddCert(certificate)
	transportCredentials := credentials.NewClientTLSFromCert(certPool, "peer0.org1.example.com")

	connection, err := grpc.Dial(peerEndpoint, grpc.WithTransportCredentials(transportCredentials))
	if err != nil {
		panic(fmt.Errorf("failed to evaluate transaction: %w", err))
	}

	return connection
}

// newIdentity creates a client identity for this Gateway connection using an X.509 certificate.
func newIdentity(certificatePEM []byte) *identity.X509Identity {
	cert, err := identity.CertificateFromPEM(certificatePEM)
	if err != nil {
		panic(err)
	}
	id, err := identity.NewX509Identity(mspID, cert)
	if err != nil {
		panic(err)
	}

	return id
}

// newHSMSign creates a function that generates a digital signature from a message digest using a private key.
func newHSMSign(h *identity.HSMSignerFactory, certPEM []byte) (identity.Sign, identity.HSMSignClose) {
	opt := identity.HSMSignerOptions{
		Label:      "ForFabric",
		Pin:        "98765432",
		Identifier: string(certPEM),
	}

	sign, close, err := h.NewHSMSigner(opt)
	if err != nil {
		panic(err)
	}

	return sign, close
}

func loadCertificate(filename string) (*x509.Certificate, error) {
	certificatePEM, err := os.ReadFile(filename) //#nosec G304
	if err != nil {
		return nil, err
	}

	return identity.CertificateFromPEM(certificatePEM)
}

func getSKI(certPEM []byte) []byte {
	block, _ := pem.Decode(certPEM)

	x590cert, _ := x509.ParseCertificate(block.Bytes)
	pk := x590cert.PublicKey

	return skiForKey(pk.(*ecdsa.PublicKey))
}

func skiForKey(pk *ecdsa.PublicKey) []byte {
	ski := sha256.Sum256(elliptic.Marshal(pk.Curve, pk.X, pk.Y))
	return ski[:]
}

func findSoftHSMLibrary() string {

	libraryLocations := []string{
		"/usr/lib/softhsm/libsofthsm2.so",
		"/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so",
		"/usr/local/lib/softhsm/libsofthsm2.so",
		"/usr/lib/libacsp-pkcs11.so",
	}
	pkcs11lib := os.Getenv("PKCS11_LIB")
	if pkcs11lib != "" {
		libraryLocations = append(libraryLocations, pkcs11lib)
	}
	for _, libraryLocation := range libraryLocations {
		if _, err := os.Stat(libraryLocation); !errors.Is(err, os.ErrNotExist) {
			return libraryLocation
		}
	}

	panic("No SoftHSM library can be found. The Sample requires SoftHSM to be installed")
}

// Format JSON data
func formatJSON(data []byte) string {
	var prettyJSON bytes.Buffer
	if err := json.Indent(&prettyJSON, data, " ", ""); err != nil {
		panic(fmt.Errorf("failed to parse JSON: %w", err))
	}
	return prettyJSON.String()
}
