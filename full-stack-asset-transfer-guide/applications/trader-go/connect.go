package main

import (
	"crypto/x509"
	"fmt"
	"os"
	"time"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"github.com/hyperledger/fabric-gateway/pkg/hash"
	"github.com/hyperledger/fabric-gateway/pkg/identity"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
)

// newGrpcConnection creates a gRPC connection to the Gateway server.
// If TLS_CERT is set, TLS is used; otherwise an insecure connection is established.
func newGrpcConnection() (*grpc.ClientConn, error) {
	endpoint, err := gatewayEndpoint()
	if err != nil {
		return nil, err
	}

	tlsCert := tlsCertPath()
	if tlsCert != "" {
		certPEM, err := os.ReadFile(tlsCert)
		if err != nil {
			return nil, fmt.Errorf("failed to read TLS certificate: %w", err)
		}

		cert, err := identity.CertificateFromPEM(certPEM)
		if err != nil {
			return nil, fmt.Errorf("failed to parse TLS certificate: %w", err)
		}

		certPool := x509.NewCertPool()
		certPool.AddCert(cert)

		transportCreds := credentials.NewClientTLSFromCert(certPool, "")
		opts := []grpc.DialOption{grpc.WithTransportCredentials(transportCreds)}

		// Override TLS server name if endpoint address doesn't match the certificate
		if alias := hostAlias(); alias != "" {
			opts = append(opts, grpc.WithAuthority(alias))
		}

		return grpc.NewClient(endpoint, opts...)
	}

	return grpc.NewClient(endpoint, grpc.WithTransportCredentials(insecure.NewCredentials()))
}

// newGatewayConnection creates a Fabric Gateway connection using the provided gRPC connection.
func newGatewayConnection(grpcConn *grpc.ClientConn) (*client.Gateway, error) {
	id, err := newIdentity()
	if err != nil {
		return nil, err
	}

	sign, err := newSigner()
	if err != nil {
		return nil, err
	}

	return client.Connect(
		id,
		client.WithSign(sign),
		client.WithHash(hash.SHA256),
		client.WithClientConnection(grpcConn),
		// Default timeouts for different gRPC calls
		client.WithEvaluateTimeout(5*time.Second),
		client.WithEndorseTimeout(15*time.Second),
		client.WithSubmitTimeout(5*time.Second),
		client.WithCommitStatusTimeout(1*time.Minute),
	)
}

// newIdentity creates a client X.509 identity from the certificate file.
func newIdentity() (*identity.X509Identity, error) {
	certPath, err := clientCertPath()
	if err != nil {
		return nil, err
	}

	certPEM, err := os.ReadFile(certPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read certificate: %w", err)
	}

	cert, err := identity.CertificateFromPEM(certPEM)
	if err != nil {
		return nil, fmt.Errorf("failed to parse certificate: %w", err)
	}

	msp, err := mspID()
	if err != nil {
		return nil, err
	}

	return identity.NewX509Identity(msp, cert)
}

// newSigner creates a signing function from the private key file.
func newSigner() (identity.Sign, error) {
	keyPath, err := privateKeyPath()
	if err != nil {
		return nil, err
	}

	keyPEM, err := os.ReadFile(keyPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read private key: %w", err)
	}

	privateKey, err := identity.PrivateKeyFromPEM(keyPEM)
	if err != nil {
		return nil, fmt.Errorf("failed to parse private key: %w", err)
	}

	return identity.NewPrivateKeySign(privateKey)
}
