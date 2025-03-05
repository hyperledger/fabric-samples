package main

import (
	"crypto/x509"
	"fmt"
	"os"
	"path"
	"time"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"github.com/hyperledger/fabric-gateway/pkg/hash"
	"github.com/hyperledger/fabric-gateway/pkg/identity"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
)

const peerName = "peer0.org1.example.com"

var (
	channelName   = envOrDefault("CHANNEL_NAME", "mychannel")
	chaincodeName = envOrDefault("CHAINCODE_NAME", "basic")
	mspID         = envOrDefault("MSP_ID", "Org1MSP")

	// Path to crypto materials.
	cryptoPath = envOrDefault("CRYPTO_PATH", "../../test-network/organizations/peerOrganizations/org1.example.com")

	// Path to user private key directory.
	keyDirectoryPath = envOrDefault("KEY_DIRECTORY_PATH", cryptoPath+"/users/User1@org1.example.com/msp/keystore")

	// Path to user certificate.
	certPath = envOrDefault("CERT_PATH", cryptoPath+"/users/User1@org1.example.com/msp/signcerts/cert.pem")

	// Path to peer tls certificate.
	tlsCertPath = envOrDefault("TLS_CERT_PATH", cryptoPath+"/peers/peer0.org1.example.com/tls/ca.crt")

	// Gateway peer endpoint.
	peerEndpoint = envOrDefault("PEER_ENDPOINT", "dns:///localhost:7051")

	// Gateway peer SSL host name override.
	peerHostAlias = envOrDefault("PEER_HOST_ALIAS", peerName)
)

func newGrpcConnection() *grpc.ClientConn {
	certificatePEM, err := os.ReadFile(tlsCertPath)
	if err != nil {
		panic(fmt.Errorf("failed to read TLS certificate file: %w", err))
	}

	certificate, err := identity.CertificateFromPEM(certificatePEM)
	if err != nil {
		panic(err)
	}

	certPool := x509.NewCertPool()
	certPool.AddCert(certificate)
	transportCredentials := credentials.NewClientTLSFromCert(certPool, peerHostAlias)

	connection, err := grpc.NewClient(peerEndpoint, grpc.WithTransportCredentials(transportCredentials))
	if err != nil {
		panic(fmt.Errorf("failed to create gRPC connection: %w", err))
	}

	return connection
}

func newConnectOptions(clientConnection grpc.ClientConnInterface) (identity.Identity, []client.ConnectOption) {
	return newIdentity(), []client.ConnectOption{
		client.WithSign(newSign()),
		client.WithHash(hash.SHA256),
		client.WithClientConnection(clientConnection),
		client.WithEvaluateTimeout(5 * time.Second),
		client.WithEndorseTimeout(15 * time.Second),
		client.WithSubmitTimeout(5 * time.Second),
		client.WithCommitStatusTimeout(1 * time.Minute),
	}
}

func newIdentity() *identity.X509Identity {
	certificatePEM, err := os.ReadFile(certPath)
	if err != nil {
		panic(fmt.Errorf("failed to read certificate file: %w", err))
	}

	certificate, err := identity.CertificateFromPEM(certificatePEM)
	if err != nil {
		panic(err)
	}

	id, err := identity.NewX509Identity(mspID, certificate)
	if err != nil {
		panic(err)
	}

	return id
}

func newSign() identity.Sign {
	privateKeyPEM, err := readFirstFile(keyDirectoryPath)
	if err != nil {
		panic(fmt.Errorf("failed to read private key file: %w", err))
	}

	privateKey, err := identity.PrivateKeyFromPEM(privateKeyPEM)
	if err != nil {
		panic(err)
	}

	sign, err := identity.NewPrivateKeySign(privateKey)
	if err != nil {
		panic(err)
	}

	return sign
}

func readFirstFile(dirPath string) ([]byte, error) {
	dir, err := os.Open(dirPath)
	if err != nil {
		return nil, err
	}

	fileNames, err := dir.Readdirnames(1)
	if err != nil {
		return nil, err
	}

	return os.ReadFile(path.Join(dirPath, fileNames[0]))
}
